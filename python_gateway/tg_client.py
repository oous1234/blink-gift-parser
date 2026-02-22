import logging
import asyncio
import time
from telethon import TelegramClient, errors
from config import settings

logger = logging.getLogger(__name__)

class SessionClient:
    """Обертка над TelegramClient для контроля состояния конкретной сессии."""

    def __init__(self, session_path: str):
        self.session_name = session_path.split("/")[-1].replace(".session", "")
        self.client = TelegramClient(
            session_path,
            settings.API_ID,
            settings.API_HASH,
            flood_sleep_threshold=settings.DEFAULT_FLOOD_SLEEP_THRESHOLD
        )
        self.busy_count = 0
        self.flood_until = 0
        self.is_authorized = False

    async def connect(self):
        try:
            await self.client.connect()
            self.is_authorized = await self.client.is_user_authorized()
            if not self.is_authorized:
                logger.warning(f"Session {self.session_name} is NOT authorized!")
            else:
                logger.info(f"Session {self.session_name} connected and ready.")
        except Exception as e:
            logger.error(f"Failed to connect session {self.session_name}: {e}")

    @property
    def is_available(self) -> bool:
        """Проверка доступности: авторизован, нет FloodWait и не перегружен."""
        return (
            self.is_authorized and
            time.time() > self.flood_until and
            self.busy_count < settings.MAX_CONCURRENT_PER_SESSION
        )

    def set_flood(self, seconds: int):
        logger.warning(f"Session {self.session_name} got FloodWait for {seconds}s")
        self.flood_until = time.time() + seconds

    async def disconnect(self):
        await self.client.disconnect()