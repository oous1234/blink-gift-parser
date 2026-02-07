import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    # Добавляем аннотации типов для всех полей
    API_ID: int = 29957325
    API_HASH: str = "7e69cb42f147746204f52a78ec95cb6b"
    SESSION_NAME: str = "ceawse"
    HOST: str = "0.0.0.0"
    PORT: int = 8082

    class Config:
        env_file = ".env"
        extra = "ignore" # Игнорировать лишние переменные в .env

settings = Settings()