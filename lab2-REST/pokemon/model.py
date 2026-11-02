from pydantic import BaseModel
from typing import List

class PokemonResponse(BaseModel):
    id: int
    name: str
    types: List[str]
    moves: List[str]
    weight: float
    height: float
    img_url: str

class PokemonHeightWeight(BaseModel):
    id: int
    name: str
    height: float
    weight: float

class TypeResponse(BaseModel):
    id: int
    name: str
    pokemons: List[PokemonHeightWeight]
    img_url: str

