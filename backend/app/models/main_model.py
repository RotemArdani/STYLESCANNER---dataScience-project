import os
import pickle
from dotenv import load_dotenv

load_dotenv()

def load_main_model():
    model_path = os.getenv("MODEL_PATH")
    if not model_path:
        raise Exception("MODEL_PATH is not set in .env file.")

    with open(model_path, "rb") as f:
        model = pickle.load(f)

    return model

def make_prediction(input_data):
    try:
        model = load_main_model()

        prediction = model.predict(input_data)

        min_range = prediction.min().item()
        max_range = prediction.max().item()

        return min_range, max_range
    except Exception as e:
        raise Exception(f"Error in prediction: {e}")

# def make_prediction(output):
#     try:
#         min_range = 50
#         max_range = 100
#         return min_range, max_range
#     except Exception as e:
#         raise Exception(f"Error in prediction: {e}")
