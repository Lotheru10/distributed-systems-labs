import httpx
from fastapi import FastAPI, HTTPException
import config



class Client:
    async def get_pokemon(self, name: str):
        url = config.settings.pokeapi_base_url+"pokemon/"+name.lower().strip()
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(url)

        if response.status_code == 404:
            raise HTTPException(status_code=404, detail="Pokemon not found")
        elif response.status_code >= 500:
            raise HTTPException(status_code=500, detail="Internal server error")

        response.raise_for_status()
        return response.json()