import os
from pydantic_settings import BaseSettings
from typing import List

class Settings(BaseSettings):
    API_ID: int = 29957325
    API_HASH: str = "7e69cb42f147746204f52a78ec95cb6b"

    # Путь к папке с сессиями
    SESSIONS_DIR: str = "sessions"

    HOST: str = "0.0.0.0"
    PORT: int = 8082

    # Настройки воркера
    MAX_CONCURRENT_PER_SESSION: int = 3
    DEFAULT_FLOOD_SLEEP_THRESHOLD: int = 60

    class Config:
        env_file = ".env"
        extra = "ignore"

settings = Settings()

if not os.path.exists(settings.SESSIONS_DIR):
    os.makedirs(settings.SESSIONS_DIR)