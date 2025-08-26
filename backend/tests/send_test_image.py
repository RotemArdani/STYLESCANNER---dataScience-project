import argparse
import base64
import json
import os
import sys
import requests

DEFAULT_URL = "http://127.0.0.1:5000/predictimage"

def encode_image_to_base64(path: str) -> str:
    if not os.path.isfile(path):
        raise FileNotFoundError(f"Image file not found: {path}")
    with open(path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("utf-8") # str
    return b64

def strip_data_url_prefix(b64_str: str) -> str:
    if b64_str.startswith("data:image"):
        return b64_str.split(",", 1)[-1]
    return b64_str

def main():
    parser = argparse.ArgumentParser(description="Send a Base64 image to the Flask /predictimage endpoint.")
    parser.add_argument("--url", default=DEFAULT_URL, help=f"Endpoint URL (default: {DEFAULT_URL})")
    parser.add_argument("--file", required=True, help="Path to image file (e.g., tests/images/shirt.jpg)")
    parser.add_argument("--country", default="Israel")
    parser.add_argument("--locality", default="Tel Aviv")
    parser.add_argument("--save", default="tests/last_response.json", help="Path to save JSON response")
    args = parser.parse_args()

    try:
        image_b64 = encode_image_to_base64(args.file)
        image_b64 = strip_data_url_prefix(image_b64)

        payload = {
            "image": image_b64,
            "country": args.country,
            "locality": args.locality
        }

        print(f"[INFO] Sending POST to {args.url}")
        resp = requests.post(args.url, json=payload, timeout=60)

        print(f"[INFO] Status code: {resp.status_code}")
        try:
            data = resp.json()
            print("[INFO] Response JSON (pretty):")
            print(json.dumps(data, ensure_ascii=False, indent=2))
        except json.JSONDecodeError:
            print("[WARN] Response is not JSON. Raw text follows:")
            print(resp.text)
            data = {"raw_text": resp.text}

        os.makedirs(os.path.dirname(args.save), exist_ok=True)
        with open(args.save, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"[INFO] Saved response to {args.save}")

        if resp.status_code != 200:
            print("[ERROR] Non-200 response code", file=sys.stderr)
            sys.exit(1)

    except Exception as e:
        print(f"[ERROR] {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
