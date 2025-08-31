from tqdm import tqdm
import torch
import torch.nn as nn
import torch.optim as optim
from torch.optim import lr_scheduler
import torchvision.models as models
from PIL import Image
from torchvision import transforms
import os


model_ft = models.resnet50(weights='IMAGENET1K_V1') ##here just run
num_ftrs = model_ft.fc.in_features
model_ft.fc = nn.Linear(num_ftrs, 2)

MODEL_PATH = os.path.join(os.path.dirname(__file__), 'man_wman.mdl')

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
        int: The predicted class label.
    """
    image = Image.open(img_path).convert('RGB')  # Ensure image is in RGB format
    input_image = transform(image).unsqueeze(0)  # Apply transform and add batch dimension
    input_image = input_image.to(device)  # Move image to the appropriate device
    model = model.to(device) # Ensure the model is on the correct device

    model.eval()  # Set the model to evaluation mode

    with torch.no_grad():  # Disable gradient calculation
        output = model(input_image)
        probabilities = torch.nn.functional.softmax(output, dim=1)
        _, predicted_class = torch.max(probabilities, 1)

    return predicted_class.item()

# Define the device
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# Define the transform
transform = transforms.Compose([
    transforms.Resize(256),
    transforms.CenterCrop(224),
    transforms.ToTensor(),
    transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225])
])


def pred_section(sample_image_path):
  
# sample_image_path = "/content/8483.jpg" # Replace with your image path

  predicted_label = predict_image_label(sample_image_path, model_ft, transform, device)

  # Assuming you have a list of class names
  # class_names = [...] # Replace with your list of class names

  # print(f"The predicted class index is: {predicted_label}")
  # if 'class_names' in locals() and predicted_label < len(class_names):
  #   print(f"The predicted class name is: {class_names[predicted_label]}")
  # else:
  #   print("Class names are not available or the predicted label is out of bounds.")

  # print(f"The predicted class index is: {predicted_label}")
  return ["MAN","WOMAN"][predicted_label]