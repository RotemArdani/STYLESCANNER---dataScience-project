import pandas as pd
import numpy as np
import pickle
import os

MODEL_PATH = os.path.join(os.path.dirname(__file__), 'xgboost_model.pkl')

with open(MODEL_PATH, 'rb') as f:
    loaded_model = pickle.load(f)


ENCODERS_PATH = os.path.join(os.path.dirname(__file__), 'label_encoders.pkl')
# Load the label encoders
with open(ENCODERS_PATH, "rb") as f:
    loaded_label_encoders = pickle.load(f)


def preprocess_new_data(new_data_dict, label_encoders):
    """
    Preprocesses new data using the loaded label encoders.

    Args:
        new_data_dict (dict): A dictionary containing the new data
                              (keys should match the original categorical columns).
        label_encoders (dict): A dictionary containing the fitted label encoders.

    Returns:
        pd.DataFrame: The preprocessed data as a pandas DataFrame.
    """
    FEATURE_ORDER = ['Section', 'Product Colour', 'Brand', 'Product Type']
    new_df = pd.DataFrame([new_data_dict], columns=FEATURE_ORDER)
    for col, encoder in label_encoders.items():
        # Handle unseen labels during inference
        new_df[col] = new_df[col].apply(lambda x: encoder.transform([x])[0] if x in encoder.classes_ else -1) # Or some other strategy for unseen data
    return new_df



# Preprocess the new data
def xgboost_predict(new_sample_data):
    preprocessed_new_data = preprocess_new_data(new_sample_data, loaded_label_encoders)

    predicted_price = loaded_model.predict(preprocessed_new_data)

    print(f"Predicted price for the new product: {predicted_price[0]:.2f}")

    return predicted_price