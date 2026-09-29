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


DEFAULT_TIMEOUT_SECONDS = 65
MAX_TIMEOUT_SECONDS = 90


class SmokeCheck:
    def __init__(self, base_url, timeout=DEFAULT_TIMEOUT_SECONDS):
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

    @staticmethod
    def summarize(path, body):
        if path == "/actuator/health":
            return {"serviceStatus": body.get("status")}
        if path.startswith("/api/v1/locations?"):
            places = [place for place in body if isinstance(place, dict)]
            irish_dublin = [place for place in places
                            if isinstance(place.get("name"), str) and place["name"].casefold() == "dublin"
                            and isinstance(place.get("country"), str) and place["country"].casefold() == "ireland"]
            return {
                "candidateCount": len(body),
                "irishDublinMatches": len(irish_dublin),
                "irishDublin": [{key: place.get(key) for key in ("name", "region", "subregion", "country", "timezone")}
                                for place in irish_dublin],
            }
        if path.startswith("/api/v1/facts/"):
            outlook = body.get("outlook") or {}
            aurora = body.get("auroraActivity") or {}
            clouds = body.get("cloudForecast") or {}
            darkness = body.get("solarDarkness") or {}
            coverage = body.get("coverage") or {}
            return {
                "location": outlook.get("location"),
                "ruleStatus": (outlook.get("ruleStatus")),
                "sourceStatus": body.get("sourceStatus"),
                "nightCount": len(outlook.get("nights") or []),
                "auroraStatus": aurora.get("status"),
                "auroraWindowUtc": {"start": aurora.get("scopeStartUtc"), "end": aurora.get("scopeEndUtc")},
                "cloudStatus": clouds.get("status"),
                "cloudForecastWindowUtc": {"start": clouds.get("scopeStartUtc"), "end": clouds.get("scopeEndUtc")},
                "darknessStatus": darkness.get("status"),
                "coverageStatus": coverage.get("status"),
                "overlappingCloudPointCount": coverage.get("cloudPointsWithValuesInsideShortRange"),
            }
        if path == "/api/v1/aurora-map":
            return {"status": body.get("status"), "pointCount": len(body.get("points") or []),
                    "observationTime": body.get("observationTime"), "forecastTime": body.get("forecastTime")}
        if path == "/api/v1/kp-index":
            return {"recordCount": len(body.get("records") or []), "retrievedAt": body.get("retrievedAt")}
        if path == "/api/v1/geomagnetic-storm-forecast":
            return {"dayCount": len(body.get("days") or []), "issuedAt": body.get("issuedAt"),
                    "retrievedAt": body.get("retrievedAt")}
        return {"topLevelKeys": sorted(body.keys()) if isinstance(body, dict) else None}

    @staticmethod
    def validate_facts(body, expected_location_id):
        outlook = body.get("outlook")
        if not isinstance(outlook, dict):
            return False
        location = outlook.get("location")
        nights = outlook.get("nights")
        return (
            isinstance(location, dict)
            and location.get("id") == expected_location_id
            and isinstance(nights, list)
            and len(nights) == 3
            and all(isinstance(night, dict) for night in nights)
        )

    @staticmethod
    def validate_aurora_map(body):
        return (
            body.get("status") == "CURRENT"
            and isinstance(body.get("points"), list)
            and len(body["points"]) > 0
            and isinstance(body.get("observationTime"), str)
            and isinstance(body.get("forecastTime"), str)
        )

    @staticmethod
    def validate_kp_index(body):
        records = body.get("records")
        return isinstance(records, list) and len(records) > 0 and all(
            isinstance(record, dict) for record in records
        )

    @staticmethod
    def validate_storm_forecast(body):
        days = body.get("days")
        return isinstance(days, list) and len(days) > 0 and all(
            isinstance(day, dict) for day in days
        )

    def check(self, path, expected_type, validate=None, keep_body=False):
        result = dict(self.get_json(path))
        result["path"] = path
        if result.get("status") == 200:
            body = result.pop("body", None)
            if not isinstance(body, expected_type):
                result["error"] = "unexpected_json_shape"
            else:
                result["summary"] = self.summarize(path, body)
                if validate and not validate(body):
                    result["error"] = "unexpected_response_content"
                if keep_body:
                    result["_body"] = body
        return result

    def run(self):
        checks = [self.check("/actuator/health", dict, lambda body: body.get("status") == "UP")]
        query = urllib.parse.urlencode({"q": "Dublin"})
        locations = self.check("/api/v1/locations?" + query, list, keep_body=True)
        checks.append(locations)
        city = None
        if locations.get("status") == 200 and isinstance(locations.get("_body"), list):
            matches = [place for place in locations["_body"]
                       if isinstance(place, dict) and isinstance(place.get("name"), str)
                       and isinstance(place.get("country"), str)
                       and isinstance(place.get("id"), int)
                       and place["name"].casefold() == "dublin"
                       and place["country"].casefold() == "ireland"]
            if len(matches) == 1:
                city = matches[0]
            else:
                locations["error"] = "expected_one_irish_dublin_candidate"
        locations.pop("_body", None)
        if city:
            checks.append(self.check(
                "/api/v1/facts/" + str(city["id"]),
                dict,
                lambda body: self.validate_facts(body, city["id"]),
            ))
        else:
            checks.append({"path": "/api/v1/facts/{selected Dublin id}", "skipped": True,
                           "error": "could_not_safely_select_irish_dublin"})
        checks.extend([
            self.check("/api/v1/aurora-map", dict, self.validate_aurora_map),
            self.check("/api/v1/kp-index", dict, self.validate_kp_index),
            self.check(
                "/api/v1/geomagnetic-storm-forecast",
                dict,
                self.validate_storm_forecast,
            ),
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
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    args = parser.parse_args()
    parsed = urllib.parse.urlparse(args.base_url)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment:
        parser.error("Use a plain HTTPS origin without credentials, query, or fragment.")
    if not 0 < args.timeout <= MAX_TIMEOUT_SECONDS:
        parser.error(f"Timeout must be greater than zero and at most {MAX_TIMEOUT_SECONDS} seconds.")
    report = SmokeCheck(args.base_url, args.timeout).run()
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
