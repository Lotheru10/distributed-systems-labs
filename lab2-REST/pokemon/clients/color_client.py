import httpx
from fastapi import HTTPException

import config


class ColorClient:

    async def get_color_name(self, hex_color: str) -> str:
        url = f"{config.settings.colorapi_base_url}{hex_color}"
        async with httpx.AsyncClient() as client:
            response = await client.get(url)

        if response.status_code == 404:
            raise HTTPException(status_code=404, detail="Pokemon not found")

        elif response.status_code >= 500:
            raise HTTPException(status_code=500, detail="Internal server error")

        response.raise_for_status()
        data = response.json()

        return data["name"]["value"]