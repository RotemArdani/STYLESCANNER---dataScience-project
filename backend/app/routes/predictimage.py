import os
import base64
from flask import Blueprint, request, jsonify
from app.models.brand import pred_Brand
from app.models.color_and_type import get_colerand_type
from app.models.man_wman import pred_section
from app.models.main_model import xgboost_predict
import pickle
import pandas as pd

routes_bp = Blueprint("routes", __name__)

@routes_bp.route("/predictimage", methods=["POST"])
def predict_image():
    try:
        data = request.get_json()

        print("[DEBUG] Received data:", data)
        print(f"Keys received: {list(data.keys())}")
        print(f"Image (first 100 chars): {data.get('image', '')[:100]}")
        print(f"Country: {data.get('country')}, City: {data.get('locality')}")

        image_base64 = data.get("image")
        if not image_base64:
            return jsonify({"error": "No image provided"}), 400

        BASE_DIR = os.path.dirname(os.path.abspath(__file__))
        save_dir = os.path.join(BASE_DIR, "..", "content")
        os.makedirs(save_dir, exist_ok=True)
        image_path = os.path.join(save_dir, "uploaded_image.jpg")

        with open(image_path, "wb") as f:
            f.write(base64.b64decode(image_base64))

        print(f"[DEBUG] Image saved to {image_path}")

        features = {}

        # Section (MAN/WOMAN)
        features["Section"] = pred_section(image_path)

        # Brand
        features["Brand"] = pred_Brand(image_path)

        # Color + Type
        ct_results = get_colerand_type(image_path)
        print(f"ct_results: {ct_results}")
        if ct_results:
            features["Product Type"] = ct_results[0]["ITEM_TYPE"]
            features["Product Colour"] = ct_results[0]["ITEM_COLOR"]

        print("[DEBUG] Extracted features:", features)

        predicted_price = xgboost_predict(features) 

        return jsonify({
            "predicted_price": float(predicted_price[0])
        })
    except Exception as e:
        print(f"Server error: {str(e)}")
        return jsonify({"error": str(e)}), 500
