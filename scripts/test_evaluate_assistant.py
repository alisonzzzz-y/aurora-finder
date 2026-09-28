"""Offline checks for the live evaluation runner. No network or model calls."""

import unittest
from unittest.mock import patch

from evaluate_assistant import Api, CASES, evaluate, select_location


class EvaluationRunnerTest(unittest.TestCase):
    def test_verified_candidate_id_distinguishes_a_city_from_a_nearby_mountain(self):
        city = {"id": 4035413, "country": "Samoa", "name": "Apia"}
        mountain = {"id": 4034942, "country": "Samoa", "name": "Mount Apia"}
        self.assertEqual(city, select_location([mountain, city], "Samoa", 4035413))
        self.assertIsNone(select_location([mountain], "Samoa", 4035413))

    def test_never_selects_between_multiple_country_matches(self):
        self.assertIsNone(select_location([
            {"id": 1, "country": "Ireland"}, {"id": 2, "country": "Ireland"}
        ], "Ireland"))

    def test_six_request_budget_prevents_a_seventh_network_call(self):
        api = Api("http://localhost:8080", 1)
        with patch.object(api, "request", return_value={"status": 200}) as request:
            for _ in range(6):
                api.chat({"message": "synthetic"})
            with self.assertRaises(RuntimeError):
                api.chat({"message": "synthetic"})
            self.assertEqual(6, request.call_count)

    def test_failed_facts_baseline_does_not_spend_a_model_request(self):
        api = Api("http://localhost:8080", 1)
        with patch.object(api, "request", side_effect=[
            {"status": 200, "body": [{"id": 1, "country": "Ireland"}]},
            {"status": 503, "error": "http_error"},
        ]):
            result = evaluate(api, "viewing-probability", CASES["viewing-probability"])
        self.assertIn("setup_error", result)
        self.assertEqual(0, api.chat_requests)

    def test_follow_up_keeps_candidates_and_does_not_preselect_a_location(self):
        api = Api("http://localhost:8080", 1)
        place = {"id": 1, "name": "Dublin", "country": "Ireland"}
        candidates = [place, {"id": 2, "name": "Dublin", "country": "United States"}]
        with patch.object(api, "request", side_effect=[
            {"status": 200, "body": candidates},
            {"status": 200, "body": {"sourceStatus": "PARTIAL"}},
            {"status": 200, "body": {"answer": "Which Dublin?", "locationCandidates": candidates}},
            {"status": 200, "body": {"answer": "Local facts."}},
        ]):
            result = evaluate(api, "same-name", CASES["same-name"])
        self.assertEqual(2, api.chat_requests)
        follow_up = result["turns"][1]["request"]
        self.assertNotIn("locationId", follow_up)
        self.assertEqual("Dublin, Ireland", follow_up["message"])
        self.assertEqual(candidates, follow_up["history"][1]["locationCandidates"])
        self.assertEqual("PENDING", result["review"])


if __name__ == "__main__":
    unittest.main()
