#!/usr/bin/env python3
"""Run bounded, synthetic assistant conversations for manual quality review.

Default mode lists cases without sending requests. No API key is required: live
mode uses the application's public API and its existing request limits.
"""

import argparse
import datetime as dt
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


CASES = {
    "same-name": {
        "query": "Dublin",
        "country": "Ireland",
        "language": "zh",
        "message": "都柏林今晚的云量怎么样？",
        "select_first": False,
        "criteria": [
            "先列出同名候选，不直接选国家。",
            "第二轮明确选择爱尔兰候选后，返回当地事实，不重复要求确认。",
            "云量按返回的当地时段和来源状态描述，不把云量当成观测保证。",
        ],
    },
    "local-date": {
        "query": "Apia",
        "country": "Samoa",
        "candidate_id": 4035413,
        "language": "en",
        "message": "For the selected place, which local date does tonight mean? Give its UTC offset and available night dates.",
        "select_first": True,
        "criteria": ["日期及 UTC 偏移与当地事实接口一致，不使用服务器日期。"],
    },
    "unsupported-date": {
        "query": "Dublin",
        "country": "Ireland",
        "language": "zh",
        "message": "查询这个地点 2030-01-01 晚上的云量和极光活动。",
        "select_first": True,
        "criteria": ["说明日期不在支持范围内，不把当前云量或活动当成该日预报。"],
    },
    "viewing-probability": {
        "query": "Dublin",
        "country": "Ireland",
        "language": "zh",
        "message": "今晚我看到极光的概率是多少？请直接给百分比。",
        "select_first": True,
        "criteria": ["不给个人观测百分比，不把模型信号转换成概率；说明规则尚未验证。"],
    },
    "recurrence": {
        "query": "Dublin",
        "country": "Ireland",
        "language": "zh",
        "message": "都柏林和科克平均多久能看到一次极光？",
        "select_first": True,
        "criteria": ["说明缺少已验证的本地历史频率数据，不给重复周期。"],
    },
}


class Api:
    MAX_GET_ATTEMPTS = 3
    RETRYABLE_STATUS_CODES = {502, 503, 504}

    def __init__(self, base_url, timeout):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.chat_requests = 0

    def request(self, path, payload=None):
        data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(self.base_url + path, data=data,
                                         headers={"Content-Type": "application/json", "Accept": "application/json"})
        started = time.monotonic()
        max_attempts = self.MAX_GET_ATTEMPTS if payload is None else 1
        for attempt in range(1, max_attempts + 1):
            try:
                with urllib.request.urlopen(request, timeout=self.timeout) as response:
                    body = response.read(2_000_001)
                    if len(body) > 2_000_000:
                        result = {"status": response.status, "error": "response_too_large"}
                    else:
                        result = {"status": response.status, "body": json.loads(body)}
            except urllib.error.HTTPError as error:
                result = {"status": error.code, "error": "http_error"}
            except (urllib.error.URLError, TimeoutError, OSError, ValueError) as error:
                result = {"status": None, "error": type(error).__name__}

            result["attempts"] = attempt
            retryable = result["status"] in self.RETRYABLE_STATUS_CODES or result["status"] is None
            if attempt == max_attempts or not retryable:
                break
            time.sleep(0.25 * attempt)

        result["duration_ms"] = round((time.monotonic() - started) * 1000)
        return result

    def chat(self, payload):
        self.chat_requests += 1
        if self.chat_requests > 6:
            raise RuntimeError("This run is limited to six assistant requests.")
        return self.request("/api/v1/assistant/chat", payload)


def select_location(candidates, country, candidate_id=None):
    matches = [place for place in candidates if place.get("country") == country
               and (candidate_id is None or place.get("id") == candidate_id)]
    return matches[0] if len(matches) == 1 else None


def location_label(place):
    return ", ".join(str(place[key]) for key in ("name", "region", "subregion", "country") if place.get(key))


def evaluate(api, case_id, case):
    result = {"id": case_id, "criteria_zh": case["criteria"], "review": "PENDING", "turns": []}
    search = api.request("/api/v1/locations?" + urllib.parse.urlencode({"q": case["query"]}))
    if search.get("status") != 200 or not isinstance(search.get("body"), list):
        result["setup_error"] = search
        return result
    place = select_location(search["body"], case["country"], case.get("candidate_id"))
    if place is None:
        result["setup_error"] = "Expected exactly one matching country candidate; review search results."
        return result
    facts = api.request("/api/v1/facts/" + str(place["id"]))
    if facts.get("status") != 200 or not isinstance(facts.get("body"), dict):
        result["setup_error"] = facts
        return result
    result["baseline"] = {"location": place, "facts": facts}
    payload = {"message": case["message"], "language": case["language"], "history": []}
    if case["select_first"]:
        payload["locationId"] = place["id"]
    first = api.chat(payload)
    result["turns"].append({"request": payload, "response": first})
    body = first.get("body", {})
    candidates = body.get("locationCandidates", []) if isinstance(body, dict) else []
    if case_id == "same-name" and first.get("status") == 200 and candidates:
        selected = select_location(candidates, case["country"])
        if selected is None:
            result["follow_up_error"] = "Returned candidates do not identify one Irish Dublin."
            return result
        # Candidate metadata travels with the prior assistant turn, as in the UI.
        follow_up = {"message": location_label(selected), "language": case["language"], "history": [
            {"role": "user", "content": case["message"]},
            {"role": "assistant", "content": body.get("answer", "")[:1000],
             "locationCandidates": candidates},
        ]}
        result["turns"].append({"request": follow_up, "response": api.chat(follow_up)})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--live", action="store_true", help="Send requests to the application; may incur model costs.")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--case", action="append", choices=list(CASES))
    parser.add_argument("--output", help="Optional local report path; reports are not uploaded automatically.")
    parser.add_argument("--timeout", type=float, default=55)
    args = parser.parse_args()
    parsed = urllib.parse.urlparse(args.base_url)
    if parsed.scheme != "https" and not (parsed.scheme == "http" and parsed.hostname in ("localhost", "127.0.0.1", "::1")):
        parser.error("Use HTTPS, or HTTP only for a local development server.")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        parser.error("The base URL must not contain credentials, query parameters, or a fragment.")
    if not 0 < args.timeout <= 60:
        parser.error("Timeout must be greater than zero and at most 60 seconds.")
    selected = list(dict.fromkeys(args.case or CASES))
    if not args.live:
        print(json.dumps({"mode": "dry-run", "cases": {key: CASES[key] for key in selected}}, ensure_ascii=False, indent=2))
        return 0
    api = Api(args.base_url, args.timeout)
    report = {"started_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
              "base_url": args.base_url, "scope": "Synthetic prompts, live model, manual review required", "cases": []}
    for key in selected:
        result = evaluate(api, key, CASES[key])
        report["cases"].append(result)
        print(json.dumps(result, ensure_ascii=False), flush=True)
        if any(turn["response"].get("status") == 429 for turn in result["turns"]):
            print("Rate limited; stopped without retrying.", file=sys.stderr)
            break
    report["assistant_requests"] = api.chat_requests
    if args.output:
        Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return int(any(case.get("setup_error") or case.get("follow_up_error") or
                   any(turn["response"].get("status") != 200 for turn in case["turns"])
                   for case in report["cases"]))


if __name__ == "__main__":
    sys.exit(main())
