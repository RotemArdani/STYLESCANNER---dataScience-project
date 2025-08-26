import os
import logging
from clarifai_grpc.channel.clarifai_channel import ClarifaiChannel
from clarifai_grpc.grpc.api import resources_pb2, service_pb2_grpc
from clarifai_grpc.grpc.api import service_pb2, resources_pb2
import base64



logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# load and connect to clarify - Apparel Detection model
CLARIFAI_API_KEY = os.environ.get("CLARIFAI_API_KEY")
USER_ID = "clarifai"
APP_ID =  "main"
MODEL_ID = "apparel-detection"
MODEL_VERSION_ID = "1ed35c3d176f45d69d2aa7971e6ab9fe"  


if not CLARIFAI_API_KEY:
    raise ValueError("CLARIFAI_API_KEY not set in environment variables")

try:
    channel = ClarifaiChannel.get_grpc_channel()
    stub = service_pb2_grpc.V2Stub(channel)

    metadata = (("authorization", f"Key {CLARIFAI_API_KEY}"),)
    
    logger.info("Connected to Clarifai Apparel Detection model successfully")
except Exception as e:
    logger.exception("Error connecting to Clarifai API")
    raise e



def predict_apparel_base64(image_base64: str) -> dict:
    try:
        if not image_base64:
            raise ValueError("No image provided for prediction")
        
        image_b64_bytes = base64.b64decode(image_base64)  

        request = service_pb2.PostModelOutputsRequest(
            user_app_id=resources_pb2.UserAppIDSet(
                user_id=USER_ID,
                app_id=APP_ID
            ),
            model_id=MODEL_ID,
            inputs=[
                resources_pb2.Input(
                    data=resources_pb2.Data(
                        image=resources_pb2.Image(base64=image_b64_bytes)
                    )
                )
            ]        
        )

        response = stub.PostModelOutputs(request, metadata=metadata)
     
        print(f"response: {response}")

        outputs = response.outputs
        if not outputs:
            logger.warning("No outputs returned by Clarifai API")
            return {}

        items = []

        for region in outputs[0].data.regions:
            bbox = region.region_info.bounding_box
            concepts_list = region.data.concepts

            concepts_dict = {c.name: c.value for c in concepts_list}

            items.append({
                "bounding_box": {
                    "top_row": bbox.top_row,
                    "left_col": bbox.left_col,
                    "bottom_row": bbox.bottom_row,
                    "right_col": bbox.right_col,
                },
                "concepts": concepts_dict
            })

        print(f"clarify info recieved {items}")

        return {"items": items}

    except Exception as e:
        logger.exception("Error during Clarifai prediction")
        return {"error": str(e)}
