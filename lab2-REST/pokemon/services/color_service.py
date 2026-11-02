from io import BytesIO

import httpx
from PIL import Image
from fastapi import HTTPException

from clients.pokemon_client import Client
from clients.color_client import ColorClient


class PokemonService:

    def __init__(self):
        self.poke_client = Client()
        self.color_client = ColorClient()

    async def get_pokemon_with_color(self, name: str):
        try:
            data = await self.poke_client.get_pokemon(name)
        except Exception as e:
            raise HTTPException(status_code=502, detail="Color service not available")

        img_url = data["sprites"]["front_default"]

        if not img_url:
            raise HTTPException(status_code=404, detail="Pokemon image not found")

        hex_color = await get_dominant_color_hex(img_url)
        color_name = await self.color_client.get_color_name(hex_color)

        return {
            "name": data["name"] if isinstance(data, dict) else data.name,
            "types": [t["type"]["name"] for t in data["types"]] if isinstance(data, dict) else data.types,
            "img_url": img_url,
            "dominant_color": hex_color,
            "color_name": color_name
        }


async def get_dominant_color_hex(image_url: str) -> str:
    async with httpx.AsyncClient() as client:
        response = await client.get(image_url)

    response.raise_for_status()

    img = Image.open(BytesIO(response.content)).convert("RGBA")
    img = img.resize((100, 100))

    pixels = list(img.getdata())

    visible_pixels = [
        (r, g, b)
        for (r, g, b, a) in pixels
        if a > 0
    ]

    if not visible_pixels:
        return "000000"

    dominant = max(set(visible_pixels), key=visible_pixels.count)

    return "{:02x}{:02x}{:02x}".format(*dominant)