"""Offline tests for the production read-only smoke check."""

import unittest
from unittest.mock import patch

from smoke_production import DEFAULT_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS, SmokeCheck


class SmokeCheckTest(unittest.TestCase):
    def test_timeout_allows_for_free_instance_wake_up(self):
        self.assertGreaterEqual(DEFAULT_TIMEOUT_SECONDS, 50)
        self.assertLessEqual(DEFAULT_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)

    def test_happy_path_checks_facts_for_the_unique_irish_city_without_calling_ai(self):
        check = SmokeCheck("https://api.example")
        place = {"id": 42, "name": "Dublin", "country": "Ireland"}
        results = {
            "/actuator/health": {"status": 200, "body": {"status": "UP"}},
            "/api/v1/locations?q=Dublin": {"status": 200, "body": [place]},
            "/api/v1/facts/42": {"status": 200, "body": {"outlook": {
                "location": {"id": 42}, "nights": [{}, {}, {}]
            }}},
            "/api/v1/aurora-map": {"status": 200, "body": {
                "status": "CURRENT", "points": [{}],
                "observationTime": "2026-09-29T09:03:00Z",
                "forecastTime": "2026-09-29T10:31:00Z"
            }},
            "/api/v1/kp-index": {"status": 200, "body": {"records": [{}]}},
            "/api/v1/geomagnetic-storm-forecast": {"status": 200, "body": {"days": [{}]}},
        }
        with patch.object(check, "get_json", side_effect=lambda path: results[path]):
            report = check.run()
        self.assertTrue(report["passed"])
        self.assertEqual("/api/v1/facts/42", report["checks"][2]["path"])
        self.assertIn("AI/model not called", report["scope"])
        self.assertNotIn("body", report["checks"][0])
        self.assertEqual(1, report["checks"][1]["summary"]["irishDublinMatches"])
        self.assertEqual(3, report["checks"][2]["summary"]["nightCount"])

    def test_ambiguous_irish_city_fails_safely_without_facts_request(self):
        check = SmokeCheck("https://api.example")
        places = [
            {"id": 1, "name": "Dublin", "country": "Ireland"},
            {"id": 2, "name": "Dublin", "country": "Ireland"},
        ]
        seen = []

        def get_json(path):
            seen.append(path)
            if path == "/actuator/health":
                return {"status": 200, "body": {"status": "UP"}}
            if path.startswith("/api/v1/locations?"):
                return {"status": 200, "body": places}
            return {"status": 200, "body": {}}

        with patch.object(check, "get_json", side_effect=get_json):
            report = check.run()
        self.assertFalse(report["passed"])
        self.assertNotIn("/api/v1/facts/1", seen)
        self.assertEqual("expected_one_irish_dublin_candidate", report["checks"][1]["error"])
        self.assertTrue(report["checks"][2]["skipped"])

    def test_missing_location_id_is_not_selected_or_used_in_a_request(self):
        check = SmokeCheck("https://api.example")
        seen = []

        def get_json(path):
            seen.append(path)
            if path == "/actuator/health":
                return {"status": 200, "body": {"status": "UP"}}
            if path.startswith("/api/v1/locations?"):
                return {"status": 200, "body": [{"name": "Dublin", "country": "Ireland"}]}
            return {"status": 200, "body": {}}

        with patch.object(check, "get_json", side_effect=get_json):
            report = check.run()
        self.assertFalse(report["passed"])
        self.assertFalse(any(path.startswith("/api/v1/facts/") for path in seen))

    def test_health_must_be_up(self):
        check = SmokeCheck("https://api.example")
        with patch.object(check, "get_json", return_value={"status": 200, "body": {"status": "DOWN"}}):
            result = check.check("/actuator/health", dict, lambda body: body.get("status") == "UP")
        self.assertEqual("unexpected_response_content", result["error"])

    def test_success_status_with_empty_data_fails_content_validation(self):
        self.assertFalse(SmokeCheck.validate_aurora_map({"status": "CURRENT", "points": []}))
        self.assertFalse(SmokeCheck.validate_kp_index({"records": []}))
        self.assertFalse(SmokeCheck.validate_storm_forecast({"days": []}))
        self.assertFalse(SmokeCheck.validate_facts(
            {"outlook": {"location": {"id": 42}, "nights": [{}, {}]}}, 42
        ))

    def test_fact_validation_requires_the_requested_location_and_three_nights(self):
        body = {"outlook": {"location": {"id": 42}, "nights": [{}, {}, {}]}}
        self.assertTrue(SmokeCheck.validate_facts(body, 42))
        self.assertFalse(SmokeCheck.validate_facts(body, 43))

    def test_fact_summary_shows_compared_source_windows_when_no_overlap(self):
        body = {
            "outlook": {"nights": [{}]},
            "auroraActivity": {"status": "CURRENT", "scopeStartUtc": "2026-09-29T09:03:00Z",
                               "scopeEndUtc": "2026-09-29T10:31:00Z"},
            "cloudForecast": {"status": "CURRENT", "scopeStartUtc": "2026-09-29T11:00:00Z",
                              "scopeEndUtc": "2026-09-30T11:00:00Z"},
            "solarDarkness": {"status": "CURRENT"},
            "coverage": {"status": "NO_OVERLAP", "cloudPointsWithValuesInsideShortRange": 0},
        }
        summary = SmokeCheck.summarize("/api/v1/facts/42", body)
        self.assertEqual("NO_OVERLAP", summary["coverageStatus"])
        self.assertEqual("2026-09-29T09:03:00Z", summary["auroraWindowUtc"]["start"])
        self.assertEqual("2026-09-29T11:00:00Z", summary["cloudForecastWindowUtc"]["start"])
        self.assertEqual(0, summary["overlappingCloudPointCount"])

    def test_invalid_json_is_reported_as_failure(self):
        check = SmokeCheck("https://api.example")
        response = unittest.mock.MagicMock()
        response.status = 200
        response.read.return_value = b"not-json"
        response.__enter__.return_value = response
        with patch("smoke_production.urllib.request.urlopen", return_value=response):
            result = check.get_json("/health")
        self.assertEqual("invalid_json", result["error"])
        self.assertEqual(200, result["status"])
        self.assertGreaterEqual(result["duration_ms"], 0)


if __name__ == "__main__":
    unittest.main()
