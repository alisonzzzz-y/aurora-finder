"""Offline tests for the production read-only smoke check."""

import unittest
from unittest.mock import patch

from smoke_production import SmokeCheck


class SmokeCheckTest(unittest.TestCase):
    def test_happy_path_checks_facts_for_the_unique_irish_city_without_calling_ai(self):
        check = SmokeCheck("https://api.example")
        place = {"id": 42, "name": "Dublin", "country": "Ireland"}
        results = {
            "/actuator/health": {"status": 200, "body": {"status": "UP"}},
            "/api/v1/locations?q=Dublin": {"status": 200, "body": [place]},
            "/api/v1/facts/42": {"status": 200, "body": {"nights": []}},
            "/api/v1/aurora-map": {"status": 200, "body": {"status": "AVAILABLE"}},
            "/api/v1/kp-index": {"status": 200, "body": {"records": []}},
            "/api/v1/geomagnetic-storm-forecast": {"status": 200, "body": {"status": "EMPTY"}},
        }
        with patch.object(check, "get_json", side_effect=lambda path: results[path]):
            report = check.run()
        self.assertTrue(report["passed"])
        self.assertEqual("/api/v1/facts/42", report["checks"][2]["path"])
        self.assertIn("AI/model not called", report["scope"])

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

    def test_health_must_be_up(self):
        check = SmokeCheck("https://api.example")
        with patch.object(check, "get_json", return_value={"status": 200, "body": {"status": "DOWN"}}):
            result = check.check("/actuator/health", dict, lambda body: body.get("status") == "UP")
        self.assertEqual("unexpected_response_content", result["error"])

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
