
# GuardianT Server

Backend server for the GuardianT secure communication ecosystem. Built with FastAPI.

## Features

*   **Device Authentication:** Secure pairing using unique device keys.
*   **Encrypted Messaging:** Stores and forwards encrypted messages (Zero-Knowledge architecture).
*   **File Transfer:** Secure "Burn-on-read" file storage.
*   **Admin Dashboard:** Basic monitoring of server stats.

## License

This project is licensed under the **GNU Affero General Public License v3 (AGPL-3.0)**.
See the [LICENSE](LICENSE) file for details.

## Installation

### Automatic (Ubuntu/Debian)

1.  Clone this repository to your VPS.
2.  Run the installer:
    ```bash
    sudo bash easy_install.sh
    ```
3.  Follow the prompts to set up your domain and SSL.

### Manual

1.  Install Python 3.10+ and `pip`.
2.  Install dependencies: `pip install -r requirements.txt`.
3.  Set environment variables:
    *   `SECRET_KEY`: A long random string.
    *   `ADMIN_USER` / `ADMIN_PASS`: Credentials for the admin panel.
4.  Run the server:
    ```bash
    uvicorn main:app --host 0.0.0.0 --port 8000
    ```

## Configuration

Edit `main.py` to add your trusted devices in the `TRUSTED_DEVICES` dictionary if you are not using a database adapter yet.

---

# Сервер GuardianT

Бэкенд-сервер для экосистемы защищенной связи GuardianT. Построен на FastAPI.

## Возможности

*   **Аутентификация устройств:** Безопасное сопряжение с использованием уникальных ключей устройств.
*   **Зашифрованные сообщения:** Хранит и пересылает зашифрованные сообщения (Архитектура с нулевым разглашением / Zero-Knowledge).
*   **Передача файлов:** Безопасное хранилище файлов с функцией "сжигания после прочтения" (Burn-on-read).
*   **Панель администратора:** Базовый мониторинг статистики сервера.

## Лицензия

Этот проект лицензирован под **GNU Affero General Public License v3 (AGPL-3.0)**.
Смотрите файл LICENSE для подробностей.

## Установка

### Автоматическая (Ubuntu/Debian)

1.  Клонируйте этот репозиторий на ваш VPS.
2.  Запустите установщик:
    ```bash
    sudo bash easy_install.sh
    ```
3.  Следуйте инструкциям для настройки домена и SSL.

### Ручная

1.  Установите Python 3.10+ и `pip`.
2.  Установите зависимости: `pip install -r requirements.txt`.
3.  Установите переменные окружения:
    *   `SECRET_KEY`: Длинная случайная строка.
    *   `ADMIN_USER` / `ADMIN_PASS`: Учетные данные для админ-панели.
4.  Запустите сервер:
    ```bash
    uvicorn main:app --host 0.0.0.0 --port 8000
    ```

## Конфигурация

Отредактируйте `main.py`, чтобы добавить ваши доверенные устройства в словарь `TRUSTED_DEVICES`, если вы еще не используете адаптер базы данных.
