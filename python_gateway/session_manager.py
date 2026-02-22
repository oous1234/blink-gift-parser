import os
import glob
import logging
import random
import asyncio
from typing import List, Optional
from tg_client import SessionClient
from config import settings
from telethon import errors

logger = logging.getLogger(__name__)

class SessionManager:
    def __init__(self):
        self.sessions: List[SessionClient] = []

    async def init_farm(self):
        """Загружает все сессии из папки sessions/"""
        session_files = glob.glob(f"{settings.SESSIONS_DIR}/*.session")
        if not session_files:
            logger.error("No .session files found in sessions/ directory!")
            return

        for auth_file in session_files:
            # Убираем расширение .session, так как Telethon добавит его сам
            clean_path = auth_file.replace(".session", "")
            client_wrapper = SessionClient(clean_path)
            await client_wrapper.connect()
            if client_wrapper.is_authorized:
                self.sessions.append(client_wrapper)

        logger.info(f"Farm initialized with {len(self.sessions)} active sessions.")

    def get_best_client(self) -> SessionClient:
        """
        Выбирает наименее нагруженный и доступный клиент.
        Использует приоритет: доступность -> минимальный busy_count.
        """
        available = [s for s in self.sessions if s.is_available]

        if not available:
            self.sessions.sort(key=lambda x: x.flood_until)
            best = self.sessions[0]
            wait_time = max(0, int(best.flood_until - time.time()))
            raise errors.FloodWaitError(seconds=wait_time or 5)

        available.sort(key=lambda x: x.busy_count)
        return available[0]

    async def exec_with_retry(self, func, *args, **kwargs):
        """
        Обертка для выполнения запросов с автоматическим ретраем на другой сессии.
        """
        max_retries = len(self.sessions)
        last_error = None

        for attempt in range(max_retries):
            client_wrapper = None
            try:
                client_wrapper = self.get_best_client()
                client_wrapper.busy_count += 1

                return await func(client_wrapper.client, *args, **kwargs)

            except errors.FloodWaitError as e:
                if client_wrapper:
                    client_wrapper.set_flood(e.seconds)
                last_error = e
                continue # Пробуем следующую сессию

            except Exception as e:
                logger.error(f"Execution error: {e}")
                last_error = e
                continue

            finally:
                if client_wrapper:
                    client_wrapper.busy_count -= 1

        raise last_error or RuntimeError("All sessions failed")

    async def close_all(self):
        for s in self.sessions:
            await s.disconnect()

farm = SessionManager()