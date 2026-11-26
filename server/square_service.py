import hmac
import os

from flask import Flask, jsonify, request

app = Flask(__name__)

# Set SQUARE_API_KEY to require X-API-Key on POST /square (Part 8); unset = no auth.
API_KEY = os.environ.get("SQUARE_API_KEY") or None


@app.route("/square", methods=["POST"])
def square_number():
    if API_KEY is not None:
        api_key = request.headers.get("X-API-Key")
        if api_key is None or not hmac.compare_digest(api_key, API_KEY):
            return jsonify({"error": "Unauthorized"}), 401

    data = request.get_json()
    if data is None or "number" not in data:
        return jsonify({"error": "Missing number parameter"}), 400

    try:
        number = int(data["number"])
    except ValueError:
        return jsonify({"error": "Parameter must be an integer"}), 400

    result = number * number
    return jsonify({"number": number, "square": result})


@app.route("/health", methods=["GET"])
def health_check():
    return jsonify({"status": "healthy"})


if __name__ == "__main__":
    # Dev server only. Never enable the Werkzeug debugger on a public interface:
    # it allows arbitrary code execution. Opt in with FLASK_DEBUG=1 / FLASK_HOST.
    app.run(
        debug=os.environ.get("FLASK_DEBUG") == "1",
        host=os.environ.get("FLASK_HOST", "127.0.0.1"),
        port=int(os.environ.get("FLASK_PORT", "5000")),
    )
