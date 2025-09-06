import os
import base64
import logging
from flask import Blueprint, request, jsonify
from app.models.brand import pred_Brand
from app.models.color_and_type import get_colerand_type
from app.models.man_wman import pred_section
from app.models.main_model import xgboost_predict

logger = logging.getLogger(__name__)
routes_bp = Blueprint("routes", __name__)

@routes_bp.route("/predictimage", methods=["POST"])
def predict_image():
    try:
        # Parse request
        data = request.get_json(silent=True) or {}
        image_base64 = data.get("image")
        country = data.get("country")
        city = data.get("locality")
        logger.info("Request received | country=%s | city=%s", country, city)

        # Validate image (base64)
        if not image_base64 or not isinstance(image_base64, str) or len(image_base64) < 50:
            logger.warning("Invalid or missing base64 image")
            return jsonify({"code": "INVALID_IMAGE", "error": "Invalid or missing 'image' (base64)"}), 400

        # Support data URL
        if image_base64.startswith("data:"):
            parts = image_base64.split(",", 1)
            if len(parts) != 2:
                logger.warning("Malformed data URL in 'image'")
                return jsonify({"code": "BAD_DATA_URL", "error": "Malformed data URL in 'image'"}), 400
            image_base64 = parts[1]

        # Persist image
        base_dir = os.path.dirname(os.path.abspath(__file__))
        save_dir = os.path.join(base_dir, "..", "content")
        os.makedirs(save_dir, exist_ok=True)
        image_path = os.path.join(save_dir, "uploaded_image.jpg")
        try:
            with open(image_path, "wb") as f:
                f.write(base64.b64decode(image_base64))
        except Exception:
            logger.exception("Failed to decode/save image base64")
            return jsonify({"code": "SAVE_FAILED", "error": "Failed to decode/save image base64"}), 400

        # Section & brand (with fallbacks)
        try:
            section = pred_section(image_path)
            brand = pred_Brand(image_path)
            if not isinstance(section, str) or not section.strip():
                section = "Unknown"
            if not isinstance(brand, str) or not brand.strip():
                brand = "Unknown"
            features_base = {"Section": section, "Brand": brand}
            logger.info("Base features | Section=%s | Brand=%s", section, brand)
        except Exception as e:
            logger.exception("Feature extraction failed")
            return jsonify({"code": "FEATURES_FAILED", "error": f"Feature extraction failed: {e}"}), 500

        # Detect clothing items (type/color)
        try:
            ct_results = get_colerand_type(image_path)  # [{"ITEM_TYPE": "...", "ITEM_COLOR": "..."}]
            logger.info("Detected items count=%d", len(ct_results or []))
        except Exception as e:
            logger.exception("Color/Type detection failed")
            return jsonify({"code": "COLOR_TYPE_FAILED", "error": f"Color/Type detection failed: {e}"}), 500

        # Stop early if nothing detected (no pricing)
        if not ct_results:
            logger.info("No clothing item detected in image")
            return jsonify({
                "code": "NO_CLOTHING_DETECTED",
                "error": "No clothing item detected. Please upload a clear clothing image."
            }), 400

        # Predict per item
        items = []
        total_price = 0.0
        for idx, item in enumerate(ct_results, start=1):
            item_type = item.get("ITEM_TYPE")
            item_color = item.get("ITEM_COLOR")
            if not item_type or not item_color:
                logger.warning("Skipping item with missing fields | item=%s", item)
                continue

            features = {**features_base, "Product Type": item_type, "Product Colour": item_color}
            try:
                price = float(xgboost_predict(features))
                logger.info(
                    "Item #%d | Section=%s | Brand=%s | Type=%s | Color=%s | Price=%.2f",
                    idx, features_base["Section"], features_base["Brand"], item_type, item_color, price
                )
            except Exception:
                logger.exception("Price prediction failed for item #%d", idx)
                price = 0.0

            items.append({
                "type": item_type,
                "color": item_color,
                "brand": features_base["Brand"],
                "section": features_base["Section"],
                "price": price
            })
            total_price += price

        # Price range
        margin_pct = float(os.getenv("PRICE_MARGIN_PCT", "0.15"))
        min_range = max(0.0, total_price)
        max_range = total_price * (1.0 + margin_pct)

        logger.info(
            "Summary | items=%d | total=%.2f | min=%.2f | max=%.2f | margin=%.0f%%",
            len(items), total_price, min_range, max_range, margin_pct * 100
        )

        # Response
        return jsonify({
            "items": items,
            "total_price": float(total_price),
            "min_range": float(min_range),
            "max_range": float(max_range),
            "note": "prices are estimates",
            "currency": "USD"
        }), 200

    except Exception:
        logger.exception("Unhandled server error")
        return jsonify({"code": "INTERNAL_ERROR", "error": "Internal server error"}), 500
