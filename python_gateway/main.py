import uvicorn
from fastapi import FastAPI, Query, HTTPException
from contextlib import asynccontextmanager
from tg_client import tg_session
from service import TelegramService
from schemas import InventoryResponse, GiftMetadataResponse
from config import settings

@asynccontextmanager
async def lifespan(app: FastAPI):
    await tg_session.connect()
    yield
    await tg_session.disconnect()

app = FastAPI(title="MTProto Gift Worker", lifespan=lifespan)

@app.get("/api/v1/inventory/live", response_model=InventoryResponse)
async def get_inventory_live(
    user_id: str,
    offset: str = Query(""),
    limit: int = Query(100, ge=1, le=1000)
):
    try:
        return await TelegramService.get_user_inventory(user_id, offset, limit)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/v1/metadata/fast", response_model=GiftMetadataResponse)
async def get_metadata_fast(slug: str):
    try:
        return await TelegramService.get_gift_metadata(slug)
    except Exception as e:
        raise HTTPException(status_code=404, detail=f"Metadata not found or error: {str(e)}")

if __name__ == "__main__":
    uvicorn.run(app, host=settings.HOST, port=settings.PORT)