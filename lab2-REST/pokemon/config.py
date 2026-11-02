# app/core/config.py
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    pokeapi_base_url: str
    colorapi_base_url: str
    app_api_key: str

    model_config = SettingsConfigDict(env_file=".env")

settings = Settings()