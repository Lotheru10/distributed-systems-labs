from pathlib import Path

from fastapi import APIRouter, Depends
from starlette.responses import HTMLResponse

from security import verify_api_key

page_router = APIRouter()

@page_router.get("/", response_class=HTMLResponse)
async def home():
    path = Path(__file__).resolve().parent.parent / "templates" / "index.html"
    return path.read_text(encoding="utf-8")