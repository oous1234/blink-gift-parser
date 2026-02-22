import uvicorn
from fastapi import FastAPI, Query, HTTPException
from contextlib import asynccontextmanager
from tg_client import tg_session
from service import TelegramService
from schemas import InventoryResponse, GiftMetadataResponse, SearchResponse
from config import settings

@asynccontextmanager
async def lifespan(app: FastAPI):
    await tg_session.connect()
    yield
    await tg_session.disconnect()

app = FastAPI(title="MTProto Deep Discovery Worker", lifespan=lifespan)

@app.get("/api/v1/whales/deep-scan", response_model=SearchResponse)
async def deep_scan_whales(
    limit: int = Query(100, ge=1, le=1000)
):
    """
    ЗАПУСКАЕТ ГЛУБОКИЙ СКАН ТЕЛЕГРАМА.
    Проходится по всем буквам и сочетаниям букв, собирает китов.
    ВНИМАНИЕ: Запрос может выполняться 1-3 минуты.
    """
    return await TelegramService.deep_scan_whales(limit)

@app.get("/api/v1/search", response_model=SearchResponse)
async def search_entities(
    q: str = Query(..., min_length=2),
    limit: int = Query(20, ge=1, le=50)
):
    return await TelegramService.search_entities(q, limit)

@app.get("/api/v1/whales", response_model=SearchResponse)
async def get_fast_whales(
    limit: int = Query(20, ge=1, le=100)
):
    """Быстрый поиск китов по крипто-тегам"""
    return await TelegramService.discover_whales(limit)

@app.get("/api/v1/inventory/live", response_model=InventoryResponse)
async def get_inventory_live(
    user_id: str,
    offset: str = Query(""),
    limit: int = Query(100)
):
    return await TelegramService.get_user_inventory(user_id, offset, limit)

@app.get("/api/v1/metadata/fast", response_model=GiftMetadataResponse)
async def get_metadata_fast(slug: str):
    return await TelegramService.get_gift_metadata(slug)

if __name__ == "__main__":
    uvicorn.run(app, host=settings.HOST, port=settings.PORT)