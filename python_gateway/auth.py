import asyncio
from telethon import TelegramClient
from config import settings

async def main():
    print(f"Инициализация сессии: {settings.SESSION_NAME}")
    client = TelegramClient(
        settings.SESSION_NAME,
        settings.API_ID,
        settings.API_HASH
    )

    await client.start()

    if await client.is_user_authorized():
        print("---")
        print("УСПЕХ: Сессия успешно авторизована!")
        print(f"Файл {settings.SESSION_NAME}.session создан.")
        print("Теперь можно закрывать этот скрипт и запускать основной сервис (main.py).")
    else:
        print("Ошибка: не удалось авторизовать сессию.")

if __name__ == "__main__":
    asyncio.run(main())