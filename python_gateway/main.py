import uvicorn
import logging
import time
from fastapi import FastAPI, Query
from contextlib import asynccontextmanager

from session_manager import farm
from service import TelegramService
from schemas import InventoryResponse, GiftMetadataResponse, SearchResponse
from config import settings

# Настройка логирования
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Инициализация и завершение работы фермы аккаунтов."""
    logger.info("Starting Telegram Session Farm...")
    await farm.init_farm()

    if not farm.sessions:
        logger.critical("FARM STARTUP FAILED: No authorized sessions available in sessions/ directory!")
    else:
        logger.info(f"Farm initialized with {len(farm.sessions)} active sessions.")

    yield

    logger.info("Shutting down Farm...")
    await farm.close_all()
    logger.info("All sessions disconnected.")

app = FastAPI(
    title="BlinkGift MTProto Gateway",
    version="2.0.0",
    lifespan=lifespan
)

@app.get("/api/v1/health")
async def health_check():
    """Статус всех сессий в пуле."""
    return {
        "status": "online" if farm.sessions else "offline",
        "total_sessions": len(farm.sessions),
        "available_sessions": len([s for s in farm.sessions if s.is_available]),
        "pool": [
            {
                "session": s.session_name,
                "is_available": s.is_available,
                "current_load": s.busy_count,
                "flood_wait_sec": max(0, int(s.flood_until - time.time()))
            } for s in farm.sessions
        ]
    }

@app.get("/api/v1/inventory/live", response_model=InventoryResponse)
async def get_inventory_live(
    user_id: str,
    offset: str = Query(""),
    limit: int = Query(100, ge=1, le=100)
):
    """Запрос живого инвентаря пользователя."""
    return await TelegramService.get_user_inventory(user_id, offset, limit)

@app.get("/api/v1/metadata/fast", response_model=GiftMetadataResponse)
async def get_metadata_fast(slug: str):
    """Запрос детальных метаданных подарка по его slug."""
    return await TelegramService.get_gift_metadata(slug)

@app.get("/api/v1/search", response_model=SearchResponse)
async def search_entities(
    q: str = Query(..., min_length=1),
    limit: int = Query(20, ge=1, le=50)
):
    """Поиск пользователей/каналов с их подарками."""
    return await TelegramService.search_entities(q, limit)

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=True
    )