# app/models/brand.py
import os
import torch
import torch.nn as nn
import torchvision.models as models
from PIL import Image
from torchvision import transforms

# Device selection (CPU by default; uses CUDA if available)
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# Load base architecture and replace head to match your 5-brand classifier
model_ft = models.resnet50(weights="IMAGENET1K_V1")
num_ftrs = model_ft.fc.in_features
model_ft.fc = nn.Linear(num_ftrs, 5)

# Load trained weights (state_dict) from disk
MODEL_PATH = os.path.join(os.path.dirname(__file__), "Brand300825.mdl")
state_dict = torch.load(MODEL_PATH, map_location=device)
model_ft.load_state_dict(state_dict)
model_ft.to(device)
model_ft.eval()  # set to eval once

# Deterministic transform consistent with training
transform = transforms.Compose([
    transforms.Resize(256),
    transforms.CenterCrop(224),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406],
                         [0.229, 0.224, 0.225]),
])

# Map model outputs to brand names (keep order aligned with training)
BRAND_LABELS = [
    "ASOS DESIGN",
    "Reclaimed Vintage",
    "Collusion",
    "New Look",
    "ASOS Curve",
]


def _predict_image_label(img_path: str) -> str:
    """
    Run a single forward pass and return the predicted class label.
    """
    # Ensure RGB and build a 1xCxHxW tensor
    image = Image.open(img_path).convert("RGB")
    x = transform(image).unsqueeze(0).to(device)

    with torch.no_grad():
        logits = model_ft(x)
        probs = torch.softmax(logits, dim=1)
        pred_idx = int(probs.argmax(dim=1).item())  # 0..4

    # Safe indexing into labels list
    return BRAND_LABELS[pred_idx]


def pred_Brand(sample_image_path: str) -> str:
    """
    Public API used by routes: returns predicted brand string for the image path.
    """
    return _predict_image_label(sample_image_path)
