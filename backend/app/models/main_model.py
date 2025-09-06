import os
import pickle
import numpy as np
import pandas as pd
from functools import lru_cache

# Absolute paths relative to this file
MODEL_PATH = os.path.join(os.path.dirname(__file__), "xgboost_model.pkl")
ENCODERS_PATH = os.path.join(os.path.dirname(__file__), "label_encoders.pkl")

# Keep the exact feature order used during training
FEATURE_ORDER = ["Section", "Product Colour", "Brand", "Product Type"]


@lru_cache(maxsize=1)
def _load_model_and_encoders():
    """
    Load model + label encoders once and cache them in-memory.
    This avoids re-loading on every request and is thread-safe for typical Flask usage.
    """
    with open(MODEL_PATH, "rb") as f:
        model = pickle.load(f)

    with open(ENCODERS_PATH, "rb") as f:
        label_encoders = pickle.load(f)  # expected: dict[col_name] -> fitted LabelEncoder

    return model, label_encoders


def _normalize_value(v):
    """
    Normalize raw input values to consistent string form.
    None/NaN -> 'UNK'; strip spaces; lowercase strings.
    """
    if v is None or (isinstance(v, float) and np.isnan(v)):
        return "UNK"
    if isinstance(v, str):
        s = v.strip()
        return s.lower() if s else "UNK"
    # for numbers or other types, convert to string
    return str(v).strip().lower() or "UNK"


def preprocess_new_data(sample_dict, label_encoders):
    """
    Build a single-row DataFrame in the exact FEATURE_ORDER and apply label encoding.
    Unseen categories are mapped to -1 to keep numeric input valid for the model.
    """
    # 1) Build a normalized row with all expected features
    row = {}
    for col in FEATURE_ORDER:
        row[col] = _normalize_value(sample_dict.get(col))

    X = pd.DataFrame([row], columns=FEATURE_ORDER)

    # 2) Apply per-column LabelEncoder if present
    for col in FEATURE_ORDER:
        enc = label_encoders.get(col)
        if enc is None:
            # No encoder for this column -> leave as-is (but must be numeric; fallback to -1)
            # Try to coerce to numeric where possible, else set -1
            try:
                X[col] = pd.to_numeric(X[col], errors="coerce").fillna(-1).astype(int)
            except Exception:
                X[col] = -1
            continue

        # Transform known labels; map unknowns to -1
        def _safe_transform(val):
            # LabelEncoder expects array-like; handle unknowns gracefully
            if val in enc.classes_:
                return int(enc.transform([val])[0])
            return -1

        X[col] = X[col].apply(_safe_transform).astype(int)

    return X


def xgboost_predict(features_dict):
    """
    Predict price for a single item given a feature dict containing:
    - 'Section' (e.g., 'man'/'woman')
    - 'Product Colour' (e.g., 'black', 'blue')
    - 'Brand' (e.g., 'ray-ban')
    - 'Product Type' (e.g., 'shirt', 'pants', 'bracelet')
    Returns a float price.
    """
    model, encoders = _load_model_and_encoders()
    X = preprocess_new_data(features_dict, encoders)

    # XGBoost / Sklearn models accept a (1, n_features) DataFrame
    y_pred = model.predict(X)

    # Convert to plain float (handles ndarray/scalar)
    return float(y_pred[0]) if hasattr(y_pred, "__len__") else float(y_pred)
