from flask import Blueprint, request, jsonify
from app.models.clarifai_api import predict_apparel_base64

routes_bp = Blueprint('routes', __name__)

@routes_bp.route("/predictimage", methods=["POST"])
def predict_image():
    try:
        data = request.get_json()
        print("[DEBUG] Received data:", data)

        print("/predictimage endpoint hit")
        print(f"Keys received: {list(data.keys())}")
        print(f"Image (first 100 chars): {data.get('image', '')[:100]}")
        print(f"Country: {data.get('country')}, City: {data.get('locality')}")

        image_base64 = data.get("image")

        if not image_base64:
            return jsonify({"error": "No image provided"}), 400
        
        prediction = predict_apparel_base64(image_base64)

        # return jsonify(prediction)

        # TODO - need to see what main_model gets
        # min_range, max_range = make_prediction(prediction)
        # return jsonify({
        #     "min_range": int(min_range),
        #     "max_range": int(max_range)
        # })

        return jsonify({
            "min_range": int(5),
            "max_range": int(11)
        })
    except Exception as e:
        print(f"Server error: {str(e)}")
        return jsonify({"error": str(e)}), 500
