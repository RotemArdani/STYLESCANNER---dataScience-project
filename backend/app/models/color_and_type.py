import os
import json
import re
from openai import OpenAI
import base64

# Initialize client
client = OpenAI(
    api_key=os.getenv("DASHSCOPE_API_KEY"),
    base_url="https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
)

# Allowed item types (preserve original case)
ALLOWED_ITEM_TYPES = [
    'T-Shirts & Vests', 'Shorts', 'Polo Shirts', 'Accessories',
    'Swimwear', 'Underwear & Socks', 'Tops', 'Shirts', 'Pyjamas',
    'Shoes, Boots & Trainers', 'Jeans', 'Hoodies & Sweatshirts',
    'Shoes', 'Trousers & Chinos', 'New In', 'Dresses',
    'Jumpers & Cardigans', 'Swimwear & Beachwear', 'Maternity',
    'Lingerie & Nightwear', 'Joggers', 'Skirts', 'Trousers & Leggings',
    'Co-ords', 'Face + Body', 'Suits & Separates', 'Socks & Tights',
    'Sportswear', 'Petite', 'Curve & Plus Size'
]

# Allowed colors (preserve original case)
ALLOWED_COLORS = [
    'SAGE', 'Caribou', 'MARINE', 'camo print', 'PINK', 'green', 'BLACK', 'Blue', 'GREY', 'TREKKING GREEN', 'WHITE', 'COCONUT MILK',
    'BLUE', 'WASHED BLACK', 'CREAM', 'GREY MARL', 'Khaki', 'WASHED KHAKI', 'WASHED LILAC', 'WASHED BLUE', 'BROWN', 'KHAKI',
    'RED', 'white', 'Cloud Dancer', 'Blue Denim/ opt 3', 'Sky Captain Pack:Fla', 'Agave Green', 'Sky Captain',
    'Glacier Grey', 'Black', 'Grey denim SQ732', 'Coriander', 'WASHED BROWN', 'ECRU', 'light blue', 'brown', 'CLEAR / BLUE',
    'SILVER', 'GOLD', 'ASPHALT', 'DESERT TAUPE', 'CHARCOAL', 'SKY CAPTAIN', 'JET BLACK', 'NAVY', 'GREEN/WHITE', 'BURGUNDY',
    'BROWN STRIPE', 'OFF WHITE', 'MULTI', 'BRIGHT WHITE', 'DRIFTWOOD', 'White', 'Moonbeam', 'FALCON', 'DUSTY OLIVE', 'COBBLESTONE',
    'Navy Blazer', 'TOFU', 'Seneca Rock', 'Sea Salt', 'Black Denim', 'Mid blue SQ060', 'Black Pack:Magical', 'Crockery', 'Elmwood',
    'Lemon Sherbert', 'STONE', 'GREEN', 'SURF THE WEB', 'VOLCANIC GLASS', 'ADOBE ROSE', 'BEIGE', 'PELICAN', 'HUNTER GREEN',
    'BDRIFTWOOD', 'BELGIAN BLOCK', 'Evergreen', 'Oxford Tan', 'Turkish Sea', 'Cashmeree Blue', 'Vintage Indigo', 'String',
    'Blue Denim', 'OATMEAL', 'blue', 'Olive Night', 'MOUNTAIN VIEW', 'Blue Denim/ opt 2', 'VETIVER', 'SEAL BROWN', 'orange',
    'MOLE', 'White L/Grey Melange', 'SCARAB', 'BRACKEN', 'DARK GREEN', 'SYRAH', 'Clear', 'DARKEST SPRUCE', 'Orange', 'DEEP DEPTHS',
    'ULTIMATE GREY', 'turquoise', 'PUSSYWILLOW', 'FOREST FOG', 'MEDIEVAL BLUE', 'MINERAL GREY', 'TRAVERTINE', 'multi',
    'POTTING SOIL', 'FOREVER BLUE', 'MEDITERRANEA', 'TOBACCO BROWN', 'GREY MARL & WHITE', 'DARK SHADOW', 'PINE GREEN', 'WHITE/GREY',
    'Green', 'Beige', 'Navy', 'LIGHT WASH BLUE', 'COMFREY', 'ODYSSEY GRAY', 'brown and black', 'silver', 'KOMBU GREEN',
    'belgian block', 'DARK SLATE', 'coconut milk', 'green and blue', 'LIGHT PINK', 'Silver', 'gold', 'TAN', 'DUNE', 'STRAWBERRY CREAM',
    'LIGHT GREEN', 'Egret', 'MID BLUE', 'olive strata', 'khaki', 'BIRCH', 'Burgundy', 'Chambray Blue', 'Tibetan Red', 'Neon Green',
    'PALE BLUE', 'Neon Yellow', 'RUST', 'Black Denim J7712-00', 'Gold Colour J3108-00', 'Asphalt J2197-00', 'Marshmallow',
    'Black Denim SQ737', 'Jet Black J546400', 'Washed Black', 'Cork', 'White Denim', 'Light Blue Denim', 'PALE MAUVE', 'Shadow',
    'Major Brown', 'Bright White', 'LIGHT BLUE', 'White Sand', 'Snow White', 'Ocean Cavern', 'Arabian Spice', 'Cedar',
    'Brown Stone', 'Mid Blue', 'Iceberg Green', 'Cream', 'FOREST NIGHT', 'Purple', 'Emerald', 'Blue Zircon', 'Sapphire',
    'PORT ROYALE', 'Multi', 'Light Green', 'Light Sapphire', 'Pearl', 'Silver Lining', 'PURPLE', 'Moonstruck', 'GREEN CHECK',
    'GREY CHECK', 'MOCHA', 'Cobalt Blue', 'Coconut Milk', 'White Jade', 'KHAKI - LIGHT', 'PRALINE', 'FOLKSTONE GRAY', 'JAVA', 'MARRON',
    'Off White', 'TORNADO', 'GARDEN TOPIARY', 'CHARCOAL MARL', 'NAVY BLAZER', 'STONE BLUE', 'RIO RED', 'ULTIMATE GRAY',
    'DARK KHAKI', 'GREIGE', 'RED/BLUE', 'LIGHT STONE', 'DARK BROWN', 'FORES FOG', 'SEA SPRAY', 'ODYSSEY GREY', 'WHITE/BLUE',
    'WHITE/PINK', 'DARK STONE', 'LIGHT BROWN', 'Sugar Swizzle', 'Antique White', 'BLACK/GUNMETAL', 'LIGHT GREY MELANGE',
    'OVERLAND TREK', 'MID WASH BLUE', 'BISON', 'EVENING BLUE', 'Black Print:TIDFO', 'YELLOW', 'Beetle', 'Dark Blue Denim',
    'BREEN', 'PALE KHAKI', 'grey marl', 'SERENITY', 'CINDER', 'NEW SAGE', 'ELEPHANT SKIN', 'CHOCOLATE CHIP', 'DARK BLUE',
    'Turbulence', 'Flint Stone', 'Mallard Green', 'BLACK/WHITE/GREY', 'OATMEAL MARL', 'Stone', 'NAUTICAL BLUE', 'Grey Denim',
    'Anthracite', 'Chocolate Brown', 'Brown', 'red', 'Tan', 'OFF WHITE AND GREEN', 'navy', 'Grey', 'Blue Stripe', 'Yellow',
    'Grey Marl', 'TAPIOCA', 'grey', 'Dark blue', 'MULTI STRIPE', 'Off-white', 'Pink & Red', 'Mole', 'Ecru', 'MULTI CHECK',
    'MULTI STRIPES', 'Brown Paisley', 'Off white', 'Burnt Orange', 'Red', 'Lemon', 'Gingham', 'Blush Pink', 'BLACK PATTERN',
    'BRIGHT RED', 'MAGENTA', 'IVORY', 'Gold', 'Blanc de Blanc', 'MUSTARD YELLOW', 'PINK STRIPE', 'ORANGE', 'Chocolate', 'cream',
    'Black Beauty', 'Zebra Print', 'Ivory', 'Polka Dot', 'WHITE PATTERN', 'RED PATTERN', 'OLIVE', 'Khaki and white',
    'Oyster white', 'Off black', 'Light Blue', 'Red and white', 'Milky white', 'FRESH BUTTERMILK', 'CHOCOLATE', 'OAT',
    'GREEN/BROWN', 'TEXAS RUST', 'BLUE STRIPE', 'stone', 'MILKY ORANGE', 'BROWN GRAD', 'CLEAR TORT', 'Parrot Green',
    'Blue stripe', 'MID BEIGE', 'Yellow Gingham', 'No Colour', 'Berry Thirsty', "Wat 'Bout Wine?", 'Splash N Spice', 'Navy Ink',
    'Iron Grey', 'NOC', 'LIGHT YELLOW', 'PEARL GOLD', 'PEARL', 'MATCHA', 'Turquoise', 'Mono', 'Dark brown', 'Pink', 'Light Pink',
    'PALE PINK', 'LIGHT BEIGE', 'Off-black', 'CHOCOLATE BROWN', 'BUTTERMILK YELLOW', 'Grey melange', 'Light blue', 'SAGE GREEN',
    'NO COLOUR', 'Blue and white', 'Pink noon', 'Lilac', 'Arabesque wood', 'Brown Pattern', 'Pearl Blush', 'Pale Mint',
    'dark red', 'Tile print', 'TOBACCO', 'GOLD GREEN', 'SKY GINGHAM', 'Gold pu', 'WHITE PU', 'BLACK SPOT', 'BUTTERMILK',
    'Crystal Pink Anja', 'MOCHA BROWN', 'Vintage White', 'Rouge Dahlia', 'MONO STRIPES', 'Tortoiseshell', 'GOLD PU',
    'NATURAL', 'Black & white spot', 'White Polka Dot', 'Sage', 'MGREEN', 'Beau Blue', 'Molten', 'Zebra', 'Bonbon', 'WALNUT',
    'Windsor Wine', 'BEIGE MARL', 'white broderie', 'zebra print', 'MINT', 'Natural', 'SKY BLUE', 'DUSKY PINK', 'straw yellow',
    'BROWN PATTERN', 'BLUE PATTERN', 'Vibrant Green', 'MIDNIGHT/BGT BLUE', 'NAVY FLORAL', 'SUBTLE GREEN', 'Oatmeal',
    'PINK/RED', 'Laurel Wreath', 'SILVER MINK', 'White Cherry', 'LILAC', 'Rust', 'PASTEL PINK', 'PINK - LIGHT', 'BABY PINK',
    'TURQUOISE', 'Pale Gold', 'BRONZE MIX', 'GOLD TONE', 'CLEAR', 'LINEN', 'MONO', 'LEOPARD PRINT', 'NAVY STRIPE', 'ICE MARL',
    'LEMON', 'CREAM STRIPE', 'LEMON DITSY PRINT', 'TAUPE', 'BLUE AND GOLD MIX', 'Khaki Green', 'Merlot', 'NEUTRAL',
    'BANDANNA PRINT', 'pink', 'Baby Pink', 'Matcha Bubble Tea', 'Buttermilk', 'Buttermilk Yellow', 'Black tie dye', 'FRESH BLUE',
    'Midnight Navy', 'Vanilla Ice/Black', 'Blush', 'STRIPE', 'BURGUNDY STRIPE', 'FIERY RED', 'WHITE FLORAL', 'PALE', 'TEAL',
    'RED STRIPE', 'PEACH', 'lemon yellow', 'BUTTERMILK STRIPE', 'WHITE & GOLD', 'AQUA', 'MINT FLOCK', 'BRIGHT GREEN', 'Tan Leo',
    'Roseate Spoonbill', 'White/black stripe', 'Dark grey', 'YELLOW POLKA', 'tortoiseshell', 'WASHED CREAM', 'Grey Heather',
    'Red/white stripe', "Butta'd Down", 'Butta Match', 'POWDER WHITE', 'SILVER TONE', 'DUSTY PINK', 'BLACK & WHITE STRIPE', 'CORNFLOWER',
    'MONO STRIPE', 'CHERRY RED', 'SEASIDE BLUE', 'LIGHT LEOPARD', 'GOLD & BLUE', 'SPRING GREEN STRIPE', 'SANDSTONE', 'Golden Sand',
    'torte', 'FAUX PEARL', 'GREY STRIPE', 'GREEN AND WHITE', 'DITSY FLORAL', 'Little Boy Blue', 'Brilliant White', 'Tigers Eye',
    'Persimmon Orange', 'TORTOISESHELL', 'COLBALT', 'CLOUD DANCER', 'PINK LADY', 'Birch Zebra', 'CHOCOLATE TORTE', 'COCONUT NAVY',
    'NAVY AND WHITE', 'Story or Post', 'Verified', 'Vanilla Ice', 'Medium Brown', 'light', 'Sand Mond', 'Fig Peach', 'STRIPE TBC',
    'BABY BLUE', 'Salmon Syrup', 'Honey', 'Honey Nut', 'Red Pattern', 'MID GREEN', 'BRIGHT PINK', 'Pink Stripe', 'yellow check',
    'white smoke', 'TIBETAN RED', 'PINK CHECK FLOWER', 'Light grey', 'GREY MELANGE'
]

# Create mapping: lowercase -> original
ITEM_TYPE_MAP =ALLOWED_ITEM_TYPES# {t.lower(): t for t in ALLOWED_ITEM_TYPES}
COLOR_MAP =ALLOWED_COLORS# {c.lower(): c for c in ALLOWED_COLORS}


def analyze_image_for_items_and_colors(image_path, image_base64):
    """
    Analyzes a local image and returns a list of detected items with their colors.
    Both ITEM_TYPE and ITEM_COLOR are mapped to your allowed lists, preserving original casing.
    """
    if not os.path.exists(image_path):
        raise FileNotFoundError(f"Image not found: {image_path}")
    if not os.getenv("DASHSCOPE_API_KEY"):
        raise EnvironmentError("DASHSCOPE_API_KEY not set.")

    def encode_image(path):
        with open(path, "rb") as img_file:
            return base64.b64encode(img_file.read()).decode('utf-8')

    base64_image = encode_image(image_path)

    prompt = f"""
    Analyze the image and list all visible apparel items with their primary color.
    
    **Allowed Item Types**: {ALLOWED_ITEM_TYPES}
    **Allowed Colors**: {ALLOWED_COLORS}

    For each detected item:
    - Match the clothing item to the **closest category** from the allowed item types.
      Examples:
        - 't-shirt' → 'T-Shirts & Vests'
        - 'sneakers' → 'Shoes, Boots & Trainers'
        - 'hoodie' → 'Hoodies & Sweatshirts'
        - 'dress' → 'Dresses'
    - Choose the **dominant color** and map it to the closest match in the allowed colors list.
      Examples:
        - 'navy' → 'NAVY'
        - 'khaki' → 'WASHED KHAKI'

    Return **only** a JSON list in this format:
    [
      {{"ITEM_TYPE": "T-Shirts & Vests", "ITEM_COLOR": "NAVY"}},
      ...
    ]

    Do not include any other text.
    """

    try:
        completion = client.chat.completions.create(
            model="qwen-vl-plus",
            messages=[
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt},
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": f"image/jpeg;base64,{base64_image}"
                            }
                        }
                    ]
                }
            ],
            max_tokens=1024,
        )

        raw_response = completion.choices[0].message.content.strip()

        # Clean JSON from markdown
        raw_response = re.sub(r"```json\s*", "", raw_response)
        raw_response = re.sub(r"```\s*", "", raw_response)
        raw_response = raw_response.strip()
        print(f"raw_response:{raw_response}")


        # Parse
        try:
            data = json.loads(raw_response)
            print(f"data:{data}")
        except json.JSONDecodeError:
            match = re.search(r'\[\s*\{.*\}\s*\]', raw_response, re.DOTALL)
            if match:
                data = json.loads(match.group(0))
            else:
                print("  Failed to extract JSON:", raw_response)
                return []

        result = []
        for item in data:
            raw_type = str(item.get("ITEM_TYPE", "")).strip()
            raw_color = str(item.get("ITEM_COLOR", "")).strip()

            # --- Match ITEM_TYPE ---
            matched_type = None
            if raw_type.lower() in ITEM_TYPE_MAP:
                matched_type = ITEM_TYPE_MAP[raw_type.lower()]
            else:
                # Fallback keywords
                fallback_map = {
                    "t-shirt": "T-Shirts & Vests",
                    "shirt": "Shirts",
                    "top": "Tops",
                    "hoodie": "Hoodies & Sweatshirts",
                    "sweater": "Jumpers & Cardigans",
                    "pants": "Trousers & Chinos",
                    "jeans": "Jeans",
                    "dress": "Dresses",
                    "skirt": "Skirts",
                    "shorts": "Shorts",
                    "socks": "Socks & Tights",
                    "underwear": "Underwear & Socks",
                    "pajamas": "Pyjamas",
                    "shoes": "Shoes, Boots & Trainers",
                    "boots": "Shoes, Boots & Trainers",
                    "trainers": "Shoes, Boots & Trainers",
                    "joggers": "Joggers",
                    "swimsuit": "Swimwear",
                    "beachwear": "Swimwear & Beachwear",
                    "hat": "Accessories",
                    "bag": "Accessories",
                    "belt": "Accessories",
                    "watch": "Accessories",
                }
                for key, val in fallback_map.items():
                    if key in raw_type.lower():
                        matched_type = val
                        break

            if not matched_type:
                continue  # Skip unknown types

            # --- Match ITEM_COLOR ---
            matched_color = None
            if raw_color.lower() in COLOR_MAP:
                matched_color = COLOR_MAP[raw_color.lower()]
            else:
                # Try partial match
                for allowed_lower, original in COLOR_MAP.items():
                    if raw_color.lower() in allowed_lower or allowed_lower in raw_color.lower():
                        matched_color = original
                        break
                if not matched_color:
                    matched_color = "MULTI"  # Fallback

            result.append({
                "ITEM_TYPE": matched_type,
                "ITEM_COLOR": matched_color
            })

        return result
    except Exception as e:
        print(f" Error: {e}")
        return []


def get_colerand_type(imgpath, image_base64):
    result = analyze_image_for_items_and_colors(imgpath, image_base64)  # Replace with your path
    print(json.dumps(result, indent=2))
    return result