import asyncio
import os
import qrcode
import logging
from telethon import TelegramClient, errors
from config import settings

# Настройка логирования для чистого вывода
logging.basicConfig(level=logging.INFO, format='%(message)s')
logger = logging.getLogger(__name__)

async def add_session_via_qr():
    print("\n" + "="*50)
    print("BLINKGIFT: ДОБАВЛЕНИЕ АККАУНТА ЧЕРЕЗ QR-КОД")
    print("="*50 + "\n")

    # 1. Запрашиваем имя сессии
    session_name = input("Введите уникальное имя для этой сессии (например, acc_01): ").strip()
    if not session_name:
        print("❌ Ошибка: Имя не может быть пустым.")
        return

    # Путь к файлу сессии в нашей папке sessions/
    if not os.path.exists(settings.SESSIONS_DIR):
        os.makedirs(settings.SESSIONS_DIR)
        
    session_path = os.path.join(settings.SESSIONS_DIR, session_name)

    # 2. Инициализируем клиент
    client = TelegramClient(
        session_path, 
        settings.API_ID, 
        settings.API_HASH,
        system_version="4.16.30-x64"
    )

    await client.connect()

    # 3. Проверяем, не авторизован ли он уже (вдруг файл уже был)
    if await client.is_user_authorized():
        me = await client.get_me()
        print(f"✅ Сессия '{session_name}' уже авторизована для: {me.first_name}")
        await client.disconnect()
        return

    # 4. Запуск процесса QR-авторизации
    print("⏳ Запрашиваю QR-код у Telegram...")
    qr_login = await client.qr_login()

    def display_qr(url):
        qr = qrcode.QRCode()
        qr.add_data(url)
        # Печатаем QR в консоли
        qr.print_ascii(invert=True)

    display_qr(qr_login.url)

    print("\n" + "!"*50)
    print("ИНСТРУКЦИЯ:")
    print("1. Открой Telegram на телефоне.")
    print("2. Перейди в Настройки -> Устройства -> Подключить устройство.")
    print("3. Отсканируй QR-код выше.")
    print("!"*50 + "\n")

    # 5. Ожидание сканирования
    try:
        # Ждем, пока пользователь отсканирует код
        await qr_login.wait(timeout=120)
    except errors.SessionPasswordNeededError:
        # Если включена двухфакторная аутентификация (2FA)
        print("\n🔑 QR-код принят! Требуется облачный пароль (2FA).")
        password = input("Введите ваш пароль двухэтапной аутентификации: ")
        try:
            await client.sign_in(password=password)
        except Exception as e:
            print(f"❌ Ошибка при вводе пароля: {e}")
            return
    except asyncio.TimeoutError:
        print("⚠️ Время ожидания (2 мин) истекло. Попробуйте снова.")
        return
    except Exception as e:
        print(f"❌ Произошла ошибка: {e}")
        return

    # 6. Финализация
    if await client.is_user_authorized():
        me = await client.get_me()
        print(f"\n✨ УСПЕХ! Аккаунт добавлен в ферму.")
        print(f"Имя: {me.first_name}")
        print(f"Username: @{me.username if me.username else 'отсутствует'}")
        print(f"Файл сессии: {session_path}.session")
    else:
        print("❌ Не удалось авторизовать аккаунт.")

    await client.disconnect()

if __name__ == "__main__":
    try:
        asyncio.run(add_session_via_qr())
    except KeyboardInterrupt:
        print("\nПрервано пользователем.")