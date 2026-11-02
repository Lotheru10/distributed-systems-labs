from fastapi import APIRouter, Depends

from security import verify_api_key
from services.color_service import PokemonService

router = APIRouter(prefix="/pokemon")

service = PokemonService()

@router.get("/{name}/color", dependencies=[Depends(verify_api_key)])
async def get_pokemon_color(name: str):
    return await service.get_pokemon_with_color(name)