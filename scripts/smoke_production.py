#!/usr/bin/env python3
"""Check the deployed Aurora Finder read-only API without calling the AI."""

import argparse
import datetime as dt
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


class SmokeCheck:
    def __init__(self, base_url, timeout=15):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def get_json(self, path):
        started = time.monotonic()
        request = urllib.request.Request(self.base_url + path, headers={"Accept": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read(2_000_001)
                if len(raw) > 2_000_000:
                    result = {"status": response.status, "error": "response_too_large"}
                else:
                    try:
                        result = {"status": response.status, "body": json.loads(raw)}
                    except (UnicodeDecodeError, json.JSONDecodeError):
                        result = {"status": response.status, "error": "invalid_json"}
        except urllib.error.HTTPError as error:
            result = {"status": error.code, "error": "http_error"}
        except (urllib.error.URLError, TimeoutError, OSError) as error:
            result = {"status": None, "error": type(error).__name__}
        result["duration_ms"] = round(1000 * (time.monotonic() - started))
        return result

    def check(self, path, expected_type, validate=None):
        result = self.get_json(path)
        result["path"] = path
        if result.get("status") == 200:
            body = result.get("body")
            if not isinstance(body, expected_type):
                result["error"] = "unexpected_json_shape"
            elif validate and not validate(body):
                result["error"] = "unexpected_response_content"
        return result

    def run(self):
        checks = [self.check("/actuator/health", dict, lambda body: body.get("status") == "UP")]
        query = urllib.parse.urlencode({"q": "Dublin"})
        locations = self.check("/api/v1/locations?" + query, list)
        checks.append(locations)
        city = None
        if locations.get("status") == 200 and isinstance(locations.get("body"), list):
            matches = [place for place in locations["body"]
                       if isinstance(place, dict) and isinstance(place.get("name"), str)
                       and isinstance(place.get("country"), str)
                       and isinstance(place.get("id"), int)
                       and place["name"].casefold() == "dublin"
                       and place["country"].casefold() == "ireland"]
            if len(matches) == 1:
                city = matches[0]
            else:
                locations["error"] = "expected_one_irish_dublin_candidate"
        if city:
            checks.append(self.check("/api/v1/facts/" + str(city["id"]), dict))
        else:
            checks.append({"path": "/api/v1/facts/{selected Dublin id}", "skipped": True,
                           "error": "could_not_safely_select_irish_dublin"})
        checks.extend([
            self.check("/api/v1/aurora-map", dict),
            self.check("/api/v1/kp-index", dict),
            self.check("/api/v1/geomagnetic-storm-forecast", dict),
        ])
        return {
            "checked_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
            "base_url": self.base_url,
            "scope": "read-only API smoke check; AI/model not called",
            "passed": all(item.get("status") == 200 and not item.get("error") for item in checks),
            "checks": checks,
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="https://aurora-observation-agent.onrender.com")
    parser.add_argument("--timeout", type=float, default=15)
    args = parser.parse_args()
    parsed = urllib.parse.urlparse(args.base_url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        parser.error("Use a plain HTTPS origin without credentials, query, or fragment.")
    if not 0 < args.timeout <= 30:
        parser.error("Timeout must be greater than zero and at most 30 seconds.")
    report = SmokeCheck(args.base_url, args.timeout).run()
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
