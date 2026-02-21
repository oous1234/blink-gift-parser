import asyncio
from telethon import TelegramClient, errors
from config import settings
import qrcode
import os

async def main():
    # Перед запуском убедись, что старый битый файл сессии удален,
    # чтобы начать процесс чисто
    session_file = f"{settings.SESSION_NAME}.session"

    client = TelegramClient(settings.SESSION_NAME, settings.API_ID, settings.API_HASH)
    await client.connect()

    if not await client.is_user_authorized():
        print("Запрос QR-кода...")
        qr_login = await client.qr_login()

        # Генерация QR в консоли
        qr = qrcode.QRCode()
        qr.add_data(qr_login.url)
        qr.print_ascii(invert=True)

        print("\n1. Открой Telegram на телефоне.")
        print("2. Настройки -> Устройства -> Подключить устройство.")
        print("3. Отсканируй код выше.")

        try:
            # Ждем сканирования QR-кода
            await qr_login.wait(timeout=60)
        except errors.SessionPasswordNeededError:
            # ТВОЙ СЛУЧАЙ: QR отсканирован, но нужен пароль 2FA
            print("\n[!] QR-код принят. Требуется облачный пароль (2FA).")
            password = input("Введите ваш пароль двухэтапной аутентификации: ")
            try:
                await client.sign_in(password=password)
            except Exception as e:
                print(f"Ошибка при вводе пароля: {e}")
                return
        except asyncio.TimeoutError:
            print("Время ожидания истекло. Попробуй снова.")
            return
        except Exception as e:
            print(f"Произошла ошибка: {e}")
            return

    if await client.is_user_authorized():
        me = await client.get_me()
        print(f"\n--- УСПЕХ! ---")
        print(f"Сессия '{settings.SESSION_NAME}.session' создана.")
        print(f"Вошли как: {me.first_name} (@{me.username if me.username else 'ID: ' + str(me.id)})")
        print("Теперь можешь закрывать этот скрипт и запускать основной сервис.")

    await client.disconnect()

if __name__ == "__main__":
    asyncio.run(main())