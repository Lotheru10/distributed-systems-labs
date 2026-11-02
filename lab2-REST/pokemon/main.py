from fastapi import FastAPI

from routers.page_router import page_router
from routers.pokemon_router import pokemon_router
from routers.color_router import router as color_router

app = FastAPI()
app.include_router(pokemon_router)
app.include_router(page_router)
app.include_router(color_router)
