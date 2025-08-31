import os
from flask import Flask
from dotenv import load_dotenv
from flask_cors import CORS

load_dotenv()

from app.routes.predictimage import routes_bp  

app = Flask(__name__)

app.register_blueprint(routes_bp)

app.config["DEBUG"] = os.getenv("FLASK_DEBUG", "False").lower() == "true"
# model_path = os.getenv("MODEL_PATH")

if os.getenv("ENABLE_CORS", "False").lower() == "true":
    CORS(app)

@app.route("/", methods=["GET"])
def home():
    return {"message": "Server is running!"}

if __name__ == "__main__":
    port = int(os.getenv("FLASK_APP_PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=app.config["DEBUG"])
