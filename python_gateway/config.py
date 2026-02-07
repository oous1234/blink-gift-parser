import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    API_ID: int
    API_HASH: str
    SESSION_NAME: str = "worker_session"
    HOST: str = "0.0.0.0"
    PORT: int = 8082

    class Config:
        env_file = ".env"

settings = Settings()