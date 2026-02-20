# GuardianT Firmware (ESP8266)

Firmware for the GuardianT Hardware Key. Based on ESP8266, it handles secure key storage, encryption, and device-to-device pairing.

## 🛠 Hardware Requirements

*   **Microcontroller:** ESP8266 (NodeMCU v3 / Wemos D1 Mini)
*   **Display:** OLED SSD1306 (128x64) I2C
*   **Buttons:**
    *   **Pairing Server:** Connected to `D3` (GPIO0 / Flash Button)
    *   **Pairing Client:** Connected to `D5` (GPIO14)

## 🚀 Setup & Registration

### 1. Getting Credentials (UID & Key)
To register your device on your self-hosted server, you need its unique ID and generated secret key.

1.  Flash the firmware to your ESP8266.
2.  Keep the device connected via USB.
3.  Open the **Serial Monitor** (in Arduino IDE or PlatformIO) and set baud rate to **115200**.
4.  Reset the device (press RST button).
5.  Look for the following lines in the boot logs:
    ```text
    --- GardianT Device Setup ---
    Device UIN: AA:BB:CC:DD:EE:FF
    Device Secret Key: 8f4b2e... (long hex string) ...
    ```
6.  Copy these values.
7.  Open `server/main.py` on your server and add them to `TRUSTED_DEVICES`:
    ```python
    TRUSTED_DEVICES = {
        "AA:BB:CC:DD:EE:FF": "8f4b2e...",
    }
    ```

### 2. Device Pairing (Key Exchange)
To enable secure direct communication between two GuardianT Keys (without the server seeing the content), they must exchange keys physically.

1.  **Device A (Server Role):**
    *   Hold the **Flash Button (D3)** for 1 second.
    *   Screen shows: `Pairing Mode: Server`.
    *   It creates a hidden Wi-Fi AP.

2.  **Device B (Client Role):**
    *   Hold the **Button on D5** for 1 second.
    *   Screen shows: `Pairing Mode: Client`.
    *   It scans for Device A.

3.  **Process:**
    *   Devices connect automatically.
    *   They perform an **ECDH (Elliptic-curve Diffie–Hellman)** handshake.
    *   A unique shared secret is generated and saved to EEPROM.
    *   Both screens show: `Pairing SUCCESS!`.

---

# Прошивка GuardianT (ESP8266)

Прошивка для аппаратного ключа GuardianT. Основана на ESP8266, обеспечивает безопасное хранение ключей, шифрование и сопряжение устройств.

## 🛠 Требования к железу

*   **Микроконтроллер:** ESP8266 (NodeMCU v3 / Wemos D1 Mini)
*   **Дисплей:** OLED SSD1306 (128x64) I2C
*   **Кнопки:**
    *   **Сервер сопряжения:** Подключена к `D3` (GPIO0 / Кнопка Flash)
    *   **Клиент сопряжения:** Подключена к `D5` (GPIO14)

## 🚀 Настройка и Регистрация

### 1. Получение учетных данных (UID и Ключ)
Чтобы зарегистрировать устройство на вашем сервере, вам нужно узнать его ID и секретный ключ.

1.  Прошейте ESP8266.
2.  Оставьте устройство подключенным по USB.
3.  Откройте **Монитор порта** (Serial Monitor) на скорости **115200**.
4.  Нажмите кнопку сброса (RST) на плате.
5.  Найдите в логах загрузки следующие строки:
    ```text
    --- GardianT Device Setup ---
    Device UIN: AA:BB:CC:DD:EE:FF
    Device Secret Key: 8f4b2e... (длинная hex строка) ...
    ```
6.  Скопируйте эти значения.
7.  Откройте файл `server/main.py` на вашем сервере и добавьте их в словарь `TRUSTED_DEVICES`:
    ```python
    TRUSTED_DEVICES = {
        "AA:BB:CC:DD:EE:FF": "8f4b2e...",
    }
    ```

### 2. Сопряжение устройств (Обмен ключами)
Чтобы два ключа GuardianT могли общаться напрямую (так, чтобы сервер не мог расшифровать переписку), они должны выполнить физическое сопряжение.

1.  **Устройство А (Роль Сервера):**
    *   Удерживайте кнопку **Flash (D3)** 1 секунду.
    *   На экране: `Pairing Mode: Server`.
    *   Устройство создаст скрытую точку доступа Wi-Fi.

2.  **Устройство Б (Роль Клиента):**
    *   Удерживайте кнопку на **D5** 1 секунду.
    *   На экране: `Pairing Mode: Client`.
    *   Устройство начнет искать Устройство А.

3.  **Процесс:**
    *   Устройства соединятся автоматически.
    *   Выполняется протокол **ECDH** (обмен ключами Диффи-Хеллмана на эллиптических кривых).
    *   Генерируется общий секрет, который сохраняется в энергонезависимую память.
    *   На обоих экранах появится: `Pairing SUCCESS!`.