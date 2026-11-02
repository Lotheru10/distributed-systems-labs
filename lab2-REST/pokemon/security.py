from fastapi import Header, HTTPException
import config

async def verify_api_key(x_api_key: str = Header(...)):
    if x_api_key != config.settings.app_api_key:
        raise HTTPException(status_code=401, detail="Invalid API key")