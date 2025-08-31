from tqdm import tqdm
import torch
import torch.nn as nn
import torch.optim as optim
from torch.optim import lr_scheduler
import torchvision.models as models
from PIL import Image
from torchvision import transforms
from sklearn.preprocessing import LabelEncoder
import pandas as pd # Import pandas to potentially load the dataframe with original labels
import os

model_ft = models.resnet50(weights='IMAGENET1K_V1') ##here just run
num_ftrs = model_ft.fc.in_features
model_ft.fc = nn.Linear(num_ftrs, 5)


MODEL_PATH = os.path.join(os.path.dirname(__file__), 'Brand300825.mdl')

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

with open(MODEL_PATH, 'rb') as f:
    model_ft.load_state_dict(torch.load(f, map_location=device))

def predict_image_label(img_path, model, transform, device):
    """
    Predicts the class label for a given image path using the provided model.

    Args:
        img_path (str): The path to the image file.
        model (torch.nn.Module): The trained model.
        transform (torchvision.transforms.Compose): The image transformations to apply.
        device (torch.device): The device to perform inference on (e.g., 'cuda' or 'cpu').

    Returns:
        str: The predicted class name.
    """
    image = Image.open(img_path).convert('RGB')  # Ensure image is in RGB format
    input_image = transform(image).unsqueeze(0)  # Apply transform and add batch dimension
    input_image = input_image.to(device)  # Move image to the appropriate device
    model = model.to(device) # Ensure the model is on the correct device

    model.eval()  # Set the model to evaluation mode

    with torch.no_grad():  # Disable gradient calculation
        output = model(input_image)
        probabilities = torch.nn.functional.softmax(output, dim=1)
        _, predicted_class_index = torch.max(probabilities, 1)

    predicted_label = ['ASOS DESIGN', 'Reclaimed Vintage', 'Collusion', 'New Look','ASOS Curve'][max(0,predicted_class_index-1)]

    return predicted_label

# Define the device
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# Define the transform
transform = transforms.Compose([
    transforms.Resize(256),
    transforms.CenterCrop(224),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
])

# Load the dataframe to fit the LabelEncoder
# Assuming 'df' with 'Brand' column is available from previous cells
# If not, you might need to load it here
if 'df' not in locals():
    print("DataFrame 'df' not found. Please ensure it's loaded in a previous cell.")
    # You might need to add code here to load the dataframe if it's not already loaded
    # For example:
    # df = pd.read_csv('/content/drive/MyDrive/final project/ASOS_Product_Data_V4.csv')
    # df = df.loc[df["Product Price"]<150]
    # items = df.Brand.value_counts().to_dict().items()
    # n = 85
    # df = df[df.Brand.isin([key for key, val in items if val > n])]


# Assuming 'df' is loaded, fit the LabelEncoder


# Example image path'
def pred_Brand(sample_image_path):
  # sample_image_path = "/content/8.jpg" # Replace with your image path
  predicted_brand = predict_image_label(sample_image_path, model_ft, transform, device)

  return predicted_brand