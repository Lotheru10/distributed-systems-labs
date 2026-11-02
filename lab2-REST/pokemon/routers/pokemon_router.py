from fastapi import APIRouter, Depends

from model import PokemonResponse
from security import verify_api_key
from services.pokemon_service import PokemonService

pokemon_router = APIRouter(prefix="/pokemon", tags=["pokemon"])
service = PokemonService()

@pokemon_router.get("/{name}", response_model=PokemonResponse)
async def get_pokemon(name: str):
    return await service.get_pokemon(name)