import os
import torch
from dotenv import load_dotenv
from torchvision import transforms
from PIL import Image

load_dotenv()

def load_cnn_model():
    try:
        model_path = os.getenv("CNN_MODEL_PATH")
        if not model_path:
            raise Exception("CNN_MODEL_PATH is not set in .env file.")

        print(f"[DEBUG] Loading CNN model from: {model_path}")
        model = torch.load(model_path, map_location=torch.device('cpu'))
        model.eval()
        return model
    except Exception as e:
        print(f"[DEBUG] Failed to load CNN model: {e}")
        raise Exception(f"Error loading CNN model: {e}")

# Temporary function to process
def process_with_cnn(image_decoded):
    return {
        'Brand': 'Under Armour',
        'Category': 'Dress',
        'Color': 'Green',
        'Size': 'S',
        'Material': 'Denim'
    }

# Real function - dont delete !
# def process_with_cnn(image_decoded):
#     try:
#         model = load_cnn_model()
#
#         transform = transforms.Compose([
#             transforms.Resize((224, 224)),
#             transforms.ToTensor(),
#             transforms.Normalize(mean=[0.485, 0.456, 0.406],
#                                  std=[0.229, 0.224, 0.225])
#         ])
#
#         img_tensor = transform(image).unsqueeze(0)
#
#         with torch.no_grad():
#             output = model(img_tensor)
#
#         return output
#     except Exception as e:
#         print(f"[DEBUG] Error inside process_with_cnn: {e}")
#         raise Exception(f"Error processing image with CNN: {e}")



# Topaz's Checks
# def process_with_cnn(image):
#     try:
#         dummy_output = torch.tensor([[0.7, 0.3]])
#         return dummy_output
#     except Exception as e:
#         raise Exception(f"Error processing image with CNN: {e}")