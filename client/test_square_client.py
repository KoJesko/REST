"""Tests for square_client (mocked HTTP)."""

import os
import sys
import unittest
from unittest.mock import MagicMock, patch

import requests.exceptions

_HERE = os.path.dirname(os.path.abspath(__file__))
if _HERE not in sys.path:
    sys.path.insert(0, _HERE)

import square_client


class TestSquareClient(unittest.TestCase):
    def tearDown(self):
        os.environ.pop("SQUARE_API_KEY", None)

    @patch.object(square_client, "requests")
    def test_square_success(self, mock_req):
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {"number": 3, "square": 9}
        mock_req.post.return_value = mock_resp

        result = square_client.square_number(3, server_url="http://example.com")
        mock_req.post.assert_called_once()
        args, kwargs = mock_req.post.call_args
        self.assertEqual(args[0], "http://example.com/square")
        self.assertEqual(kwargs["json"], {"number": 3})
        self.assertEqual(result, {"number": 3, "square": 9})

    @patch.object(square_client, "requests")
    def test_square_sends_api_key_from_env(self, mock_req):
        os.environ["SQUARE_API_KEY"] = "k"
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {"number": 1, "square": 1}
        mock_req.post.return_value = mock_resp

        square_client.square_number(1, server_url="http://x")
        _, kwargs = mock_req.post.call_args
        self.assertEqual(kwargs["headers"], {"X-API-Key": "k"})

    @patch.object(square_client, "requests")
    def test_square_explicit_api_key_override(self, mock_req):
        mock_resp = MagicMock(status_code=200)
        mock_resp.json.return_value = {"number": 2, "square": 4}
        mock_req.post.return_value = mock_resp

        square_client.square_number(2, api_key="override")
        _, kwargs = mock_req.post.call_args
        self.assertEqual(kwargs["headers"], {"X-API-Key": "override"})

    @patch.object(square_client, "requests")
    def test_square_error_status_returns_none(self, mock_req):
        mock_resp = MagicMock(status_code=400, text='{"error":"bad"}')
        mock_req.post.return_value = mock_resp

        with patch("builtins.print"):
            result = square_client.square_number(1)
        self.assertIsNone(result)

    @patch.object(square_client, "requests")
    def test_health_true(self, mock_req):
        mock_resp = MagicMock(status_code=200)
        mock_req.get.return_value = mock_resp
        self.assertTrue(square_client.check_server_health("http://h"))

    @patch.object(square_client, "requests")
    def test_health_false_on_connection_error(self, mock_req):
        mock_req.get.side_effect = requests.exceptions.ConnectionError("refused")
        self.assertFalse(square_client.check_server_health("http://h"))


if __name__ == "__main__":
    unittest.main()
