"""Tests for square_service Flask app."""

import json
import os
import sys
import unittest

_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

import importlib.util


def _import_fresh_square_service():
    """Load square_service.py again so API_KEY picks up current os.environ."""
    sys.modules.pop("square_service", None)
    path = os.path.join(_HERE, "square_service.py")
    spec = importlib.util.spec_from_file_location("square_service", path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class TestSquareService(unittest.TestCase):
    def tearDown(self):
        os.environ.pop("SQUARE_API_KEY", None)
        sys.modules.pop("square_service", None)

    def _client(self):
        svc = _import_fresh_square_service()
        return svc.app.test_client()

    def test_health(self):
        c = self._client()
        r = c.get("/health")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.get_json(), {"status": "healthy"})

    def test_square_ok(self):
        c = self._client()
        r = c.post("/square", json={"number": 7})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.get_json(), {"number": 7, "square": 49})

    def test_square_missing_number(self):
        c = self._client()
        r = c.post("/square", json={})
        self.assertEqual(r.status_code, 400)
        body = r.get_json()
        self.assertEqual(body["error"], "Missing number parameter")

    def test_square_invalid_value(self):
        c = self._client()
        r = c.post("/square", json={"number": "not-an-int"})
        self.assertEqual(r.status_code, 400)
        self.assertEqual(r.get_json()["error"], "Parameter must be an integer")

    def test_square_empty_json_body(self):
        """Empty JSON body yields 400 (invalid / missing parsed JSON)."""
        c = self._client()
        r = c.post(
            "/square",
            data=json.dumps({"number": 1}),
            content_type="application/json",
        )
        self.assertEqual(r.status_code, 200)
        r2 = c.post("/square", data="", content_type="application/json")
        self.assertEqual(r2.status_code, 400)

    def test_square_requires_api_key_when_set(self):
        os.environ["SQUARE_API_KEY"] = "secret"
        c = self._client()
        r = c.post("/square", json={"number": 5})
        self.assertEqual(r.status_code, 401)

        ok = c.post(
            "/square",
            json={"number": 5},
            headers={"X-API-Key": "secret"},
        )
        self.assertEqual(ok.status_code, 200)
        self.assertEqual(ok.get_json()["square"], 25)


if __name__ == "__main__":
    unittest.main()
