import json
import os
import sys

import requests
from requests.exceptions import RequestException

DEFAULT_SERVER_URL = "http://localhost:5000"


def square_number(number, server_url=DEFAULT_SERVER_URL, api_key=None):
    """Send a number to the square service and get its square."""
    if api_key is None:
        api_key = os.environ.get("SQUARE_API_KEY")
    url = f"{server_url}/square"
    payload = {"number": number}
    headers = {}
    if api_key:
        headers["X-API-Key"] = api_key
    try:
        response = requests.post(url, json=payload, headers=headers)
        if response.status_code == 200:
            return response.json()
        print(f"Error: Server returned status code {response.status_code}")
        print(f"Response: {response.text}")
        return None
    except RequestException as e:
        print(f"Request failed: {e}")
        return None


def check_server_health(server_url=DEFAULT_SERVER_URL):
    """Check if the server is running and healthy."""
    try:
        response = requests.get(f"{server_url}/health")
        return response.status_code == 200
    except RequestException:
        return False


if __name__ == "__main__":
    server_url = os.environ.get("SQUARE_SERVER_URL", DEFAULT_SERVER_URL)

    if not check_server_health(server_url):
        print(
            "Error: Cannot connect to the square service. Is it running?"
        )
        sys.exit(1)

    if len(sys.argv) > 1:
        try:
            number = int(sys.argv[1])
            result = square_number(number, server_url=server_url)
            if result:
                print(
                    f"The square of {result['number']} is {result['square']}"
                )
        except ValueError:
            print("Error: Please provide an integer value")
    else:
        while True:
            try:
                user_input = input(
                    "\nEnter an integer to square (or 'q' to quit): "
                )
                if user_input.lower() == "q":
                    break
                number = int(user_input)
                result = square_number(number, server_url=server_url)
                if result:
                    print(
                        f"The square of {result['number']} is {result['square']}"
                    )
            except ValueError:
                print("Error: Please enter a valid integer")
            except KeyboardInterrupt:
                print("\nExiting...")
                break
