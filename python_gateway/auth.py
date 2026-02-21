import asyncio
import os
from telethon import TelegramClient, errors
from config import settings

async def main():
    session_file = f"{settings.SESSION_NAME}.session"

    client = TelegramClient(
        settings.SESSION_NAME,
        settings.API_ID,
        settings.API_HASH,
        system_version="4.16.30-x64",
        device_model="Desktop"
    )

    try:
        await client.connect()

        phone = "89210619588"
        print(f"Отправка кода на {phone}...")

        try:
            await client.send_code_request(phone)
            print("Код отправлен! ПРОВЕРЬ ПРИЛОЖЕНИЕ TELEGRAM.")

            code = input("Введите код из Telegram: ").strip()

            try:
                await client.sign_in(phone, code)
            except errors.SessionPasswordNeededError:
                password = input("Введите ваш 2FA пароль (облачный пароль): ")
                await client.sign_in(password=password)

        except errors.FloodWaitError as e:
            print(f"Ошибка флуда: нужно подождать {e.seconds} секунд.")
            return

        if await client.is_user_authorized():
            me = await client.get_me()
            print(f"\nУСПЕХ! Сессия создана для: {me.first_name}")
            print("Теперь можешь запускать main.py")

    except Exception as e:
        print(f"Ошибка: {e}")
    finally:
        await client.disconnect()

if __name__ == "__main__":
    asyncio.run(main())