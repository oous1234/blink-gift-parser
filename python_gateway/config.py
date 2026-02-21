import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    API_ID: int = 29957325
    API_HASH: str = "7e69cb42f147746204f52a78ec95cb6b"
    SESSION_NAME: str = "ceawse"
    HOST: str = "0.0.0.0"
    PORT: int = 8082

    USE_PROXY: bool = False
    PROXY_HOST: str = "185.86.146.3"
    PROXY_PORT: int = 443
    PROXY_SECRET: str = "dd97aa082ad8ca33c6598fe31cf4817dfaa"

    class Config:
        env_file = ".env"
        extra = "ignore"

settings = Settings()