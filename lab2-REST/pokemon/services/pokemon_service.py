from clients.pokemon_client import Client
from model import PokemonResponse


class PokemonService:
    def __init__(self):
        self.client = Client()

    async def get_pokemon(self, name: str) -> PokemonResponse:
        data = await self.client.get_pokemon(name)

        return PokemonResponse(
            id=data["id"],
            name=data["name"],
            types=[t["type"]["name"] for t in data["types"]],
            moves=[m["move"]["name"] for m in data["moves"]],
            height=data["height"],
            weight=data["weight"],
            img_url=data["sprites"]["front_default"],
        )