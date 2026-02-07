import logging
from telethon import TelegramClient
from config import settings

logger = logging.getLogger(__name__)

class TelegramSession:
    def __init__(self):
        self.client = TelegramClient(
            settings.SESSION_NAME,
            settings.API_ID,
            settings.API_HASH
        )

    async def connect(self):
        await self.client.connect()
        if not await self.client.is_user_authorized():
            logger.error("Session not authorized. Run manual auth script first.")
            raise RuntimeError("Telegram authorization required")

    async def disconnect(self):
        await self.client.disconnect()

tg_session = TelegramSession()