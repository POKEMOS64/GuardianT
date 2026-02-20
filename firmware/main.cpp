/*
 * Copyright (C) 2026 GuardianT Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// Сначала определяем кривую для uECC
#define uECC_CURVE uECC_secp256r1

// Подключаем библиотеки для ESP8266
#include <ESP8266WiFi.h>
#include <ESP8266HTTPClient.h>
#include <ESP8266WebServer.h>

#include <ArduinoJson.h>
#include <WiFiUdp.h>
#include <EEPROM.h>
#include <uECC.h>
#include <base64.h> // Для кодирования зашифрованных сообщений
#include <AESLib.h> // Библиотека для AES шифрования
#include <vector> // Для буфера сообщений
#include <libb64/cdecode.h> // Для декодирования Base64

// Для OLED дисплея
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

// --- Макросы для отладки ---
// Раскомментируйте для отладки в Serial мониторе
// #define DEBUG_MODE 

#ifdef DEBUG_MODE
  #define DEBUG_PRINT(x) Serial.print(x)
  #define DEBUG_PRINTLN(x) Serial.println(x)
  #define DEBUG_PRINTF(x, ...) Serial.printf(x, ##__VA_ARGS__)
#else
  #define DEBUG_PRINT(x)
  #define DEBUG_PRINTLN(x)
  #define DEBUG_PRINTF(x, ...)
#endif

// --- Настройки дисплея (ESP8266) ---
#define OLED_SDA D2
#define OLED_SCL D1

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET -1

Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

// --- Логотип GardianT (64x64) ---
const unsigned char PROGMEM logo_bmp[] = {
	0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x80, 0x00, 0x00, 0x00, 0x80, 0x00, 0x00, 0x01, 
	0x80, 0x00, 0x00, 0x03, 0xf0, 0x00, 0x00, 0x01, 0x80, 0x00, 0x00, 0x07, 0x78, 0x00, 0x00, 0x01, 
	0x80, 0x00, 0x00, 0x1e, 0x1e, 0x00, 0x00, 0x01, 0x80, 0x00, 0x00, 0x3c, 0x1e, 0x00, 0x00, 0x01, 
	0x80, 0x00, 0x01, 0xe0, 0x81, 0xe0, 0x00, 0x01, 0x80, 0x00, 0x03, 0x81, 0xc0, 0xf0, 0x00, 0x01, 
	0x80, 0x00, 0x1f, 0x03, 0xe0, 0x3e, 0x00, 0x01, 0x80, 0x01, 0xf8, 0x0c, 0x18, 0x07, 0xe0, 0x01, 
	0x80, 0x03, 0xf0, 0x3c, 0x0e, 0x03, 0xf0, 0x01, 0x80, 0x3f, 0xc0, 0xe0, 0x01, 0xc0, 0xff, 0x01, 
	0x83, 0xfc, 0x03, 0x80, 0x00, 0xf0, 0x0f, 0xe1, 0x83, 0xe0, 0x0e, 0x00, 0x00, 0x3c, 0x01, 0xe1, 
	0x83, 0x00, 0x78, 0x01, 0xe0, 0x0f, 0x80, 0x61, 0x83, 0x03, 0xc0, 0x07, 0xf8, 0x01, 0xe0, 0x61, 
	0x83, 0x1e, 0x00, 0x0f, 0xfc, 0x00, 0x1c, 0x61, 0x83, 0x10, 0x00, 0x1f, 0x3e, 0x00, 0x04, 0x61, 
	0x83, 0x10, 0x00, 0x3c, 0x0e, 0x00, 0x04, 0x61, 0x83, 0x10, 0x00, 0x38, 0x0e, 0x00, 0x04, 0x61, 
	0x83, 0x10, 0x00, 0x70, 0x07, 0x80, 0x04, 0x61, 0x83, 0x10, 0x00, 0x70, 0x07, 0x80, 0x04, 0x61, 
	0x83, 0x10, 0x00, 0x70, 0x07, 0x80, 0x04, 0x61, 0x83, 0x10, 0x00, 0xf0, 0x07, 0x80, 0x04, 0x61, 
	0x83, 0x10, 0x07, 0xff, 0xff, 0xf0, 0x04, 0x61, 0x83, 0x10, 0x1f, 0xff, 0xff, 0xfc, 0x04, 0x61, 
	0x83, 0x10, 0x1e, 0x00, 0x00, 0x3c, 0x04, 0x61, 0x83, 0x90, 0x1e, 0x00, 0x00, 0x1c, 0x04, 0xe1, 
	0x81, 0x88, 0x1e, 0x00, 0x00, 0x1c, 0x0c, 0xc1, 0x81, 0x88, 0x1e, 0x00, 0x00, 0x00, 0x08, 0xc1, 
	0x81, 0x88, 0x1e, 0x03, 0xc0, 0x00, 0x08, 0xc1, 0x81, 0x88, 0x1e, 0x06, 0x60, 0x00, 0x08, 0xc1, 
	0x81, 0xcc, 0x1e, 0x04, 0x23, 0xfc, 0x09, 0xc1, 0x80, 0xcc, 0x1e, 0x04, 0x23, 0xfc, 0x19, 0x81, 
	0x80, 0xc4, 0x1e, 0x06, 0x63, 0xfc, 0x11, 0x81, 0x80, 0xc4, 0x1e, 0x02, 0x40, 0x3c, 0x11, 0x81, 
	0x80, 0x64, 0x1e, 0x02, 0x40, 0x1c, 0x13, 0x01, 0x80, 0x66, 0x1e, 0x06, 0x60, 0x1c, 0x33, 0x01, 
	0x80, 0x72, 0x1e, 0x04, 0x20, 0x1c, 0x27, 0x01, 0x80, 0x33, 0x1e, 0x07, 0xe0, 0x1c, 0x66, 0x01, 
	0x80, 0x31, 0x9e, 0x00, 0x00, 0x3c, 0x46, 0x01, 0x80, 0x19, 0x87, 0x00, 0x00, 0x78, 0xcc, 0x01, 
	0x80, 0x18, 0x87, 0xc0, 0x01, 0xf0, 0x8c, 0x01, 0x80, 0x0c, 0x43, 0xe0, 0x07, 0xe1, 0x98, 0x01, 
	0x80, 0x0e, 0x61, 0xf0, 0x07, 0x83, 0x38, 0x01, 0x80, 0x06, 0x20, 0x7c, 0x3e, 0x02, 0x30, 0x01, 
	0x80, 0x03, 0x18, 0x3e, 0x7e, 0x04, 0x60, 0x01, 0x80, 0x03, 0x98, 0x0f, 0xf8, 0x0c, 0xe0, 0x01, 
	0x80, 0x01, 0xc4, 0x07, 0xf0, 0x19, 0xc0, 0x01, 0x80, 0x00, 0xe6, 0x01, 0xc0, 0x33, 0x80, 0x01, 
	0x80, 0x00, 0x73, 0x00, 0x00, 0x67, 0x00, 0x01, 0x80, 0x00, 0x39, 0x80, 0x00, 0xce, 0x00, 0x01, 
	0x80, 0x00, 0x1c, 0xc0, 0x01, 0xce, 0x00, 0x01, 0x80, 0x00, 0x0e, 0x70, 0x03, 0x3c, 0x00, 0x01, 
	0x80, 0x00, 0x07, 0x30, 0x06, 0x38, 0x00, 0x01, 0x80, 0x00, 0x03, 0x9c, 0x1c, 0xf0, 0x00, 0x01, 
	0x80, 0x00, 0x01, 0xc6, 0x31, 0xc0, 0x00, 0x01, 0x80, 0x00, 0x00, 0xe3, 0x23, 0x80, 0x00, 0x01, 
	0x80, 0x00, 0x00, 0x78, 0x8f, 0x00, 0x00, 0x01, 0x80, 0x00, 0x00, 0x1c, 0x0c, 0x00, 0x00, 0x01, 
	0x80, 0x00, 0x00, 0x0f, 0x7c, 0x00, 0x00, 0x01, 0x80, 0x00, 0x00, 0x07, 0xf8, 0x00, 0x00, 0x01, 
	0x80, 0x00, 0x00, 0x01, 0xe0, 0x00, 0x00, 0x01, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01
};

// --- Структуры данных ---
struct EepromData {
  char secretKey[65];
  uint32_t checksum;
};

struct WifiConfig {
  char ssid[33];
  char password[65];
  char serverUrl[129];
  uint32_t checksum;
};

struct PairedDevice {
  uint8_t mac[6];
  uint8_t textKey[32];
  uint8_t fileKey[32];
  char uin[17];
  uint32_t checksum;
};

#define MAX_PAIRED_DEVICES 10

// --- Вспомогательные функции ---
uint32_t calculateChecksum(const char* data, size_t len) {
  uint32_t hash = 5381;
  for(size_t i = 0; i < len; ++i) hash = ((hash << 5) + hash) + data[i]; 
  return hash;
}

uint32_t calculateChecksum(const uint8_t* data, size_t len) {
  uint32_t hash = 5381;
  for(size_t i = 0; i < len; ++i) hash = ((hash << 5) + hash) + data[i]; 
  return hash;
}

int uECC_RNG(uint8_t *dest, unsigned size) {
  for (unsigned i = 0; i < size; ++i) dest[i] = random(256);
  return 1;
}

// --- Пины кнопок (ESP8266) ---
const int PAIRING_SERVER_BUTTON_PIN = D3; // FLASH (GPIO0)
const int PAIRING_CLIENT_BUTTON_PIN = D5; // GPIO14

// --- Константы ---
const char* PAIRING_AP_SSID = "GuardianT_Pairing";
const char* PAIRING_AP_PASSWORD = "GuardianT2026!";
const int PAIRING_UDP_PORT = 4210;
const int DISCOVERY_UDP_PORT = 4211;
const unsigned long PAIRING_TIMEOUT = 60000;
const unsigned long RESPONSE_TIMEOUT = 10000;

const char* PROV_AP_SSID = "GuardianT-Setup";
const char* PROV_AP_PASSWORD = "ert456tre";
const char* PROV_PHONE_IP = "192.168.4.2";
const unsigned long PROV_TIMEOUT = 180000;

#define EEPROM_SIZE 1024
#define ADDR_SECRET_KEY 0
#define ADDR_WIFI_CONFIG (ADDR_SECRET_KEY + sizeof(EepromData))
#define ADDR_PAIRED_DEVICES (ADDR_WIFI_CONFIG + sizeof(WifiConfig))

// --- Глобальные переменные ---
String deviceMacAddress;
String deviceUin;
String deviceSecretKey;
String accessToken;
WifiConfig currentConfig;
uint8_t sharedSecretForAES[32];
WiFiUDP udp;
bool useGateway = false;

ESP8266WebServer server(80);

struct BufferedMessage {
  String fromUin;
  String text;
};
std::vector<BufferedMessage> messageBuffer;

// --- Прототипы ---
void getOrCreateDeviceSecret();
String authenticateWithServer(const String& id, const String& key);
void startPairingAsServer();
void startPairingAsClient(); 
void savePairedDevice(const uint8_t* mac, const uint8_t* sharedSecret, const char* uin);
bool findPairedDevice(const uint8_t* mac, uint8_t* outputSecret, char* outputUin = NULL);
void displayMessage(const String& line1, const String& line2 = "", const String& line3 = "", int delayMs = 2000);
bool loadWifiConfig();
void saveWifiConfig(const char* ssid, const char* password, const char* serverUrl);
void startProvisioningMode();
void handleSendMessage();
void handleGetContacts();
void pollMessages();
void handleGetBufferedMessages();
String decryptMessage(String encryptedBase64, uint8_t* key);
bool findPairedDeviceByUin(const char* uin, uint8_t* outputSecret);
void handleResetPairings();
void connectToWiFi(const char* ssid, const char* password);

// Криптография
const struct uECC_Curve_t * curve = uECC_secp256r1();
const int KEY_SIZE = uECC_curve_public_key_size(curve);
const int PRIVATE_KEY_SIZE = uECC_curve_private_key_size(curve);
const int SHARED_SECRET_SIZE = uECC_curve_private_key_size(curve);
const int UIN_FIELD_SIZE = 16;

int getBatteryPercentage() {
  int raw = analogRead(A0);
  int pct = map(raw, 600, 900, 0, 100); 
  if (pct > 100) pct = 100;
  if (pct < 0) pct = 0;
  return pct;
}

void setup() {
  Serial.begin(115200);
  delay(100);
  ESP.wdtEnable(10000);

  Wire.begin(OLED_SDA, OLED_SCL);
  if(!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) {
    Serial.println(F("SSD1306 allocation failed"));
  }
  
  display.clearDisplay();
  display.drawBitmap(32, 0, logo_bmp, 64, 64, SSD1306_WHITE);
  display.display();
  delay(2000);
  
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0,0);
  display.println("GardianT v1.0 (8266)");
  display.println("Initializing...");
  display.display();

  // Инициализация RNG
  uint32_t seed = 0;
  for(int i = 0; i < 10; i++) {
    seed += analogRead(A0);
    delay(10);
  }
  randomSeed(seed + micros());
  uECC_set_rng(&uECC_RNG);

  pinMode(PAIRING_SERVER_BUTTON_PIN, INPUT_PULLUP);
  pinMode(PAIRING_CLIENT_BUTTON_PIN, INPUT_PULLUP);

  // Factory Reset
  if (digitalRead(PAIRING_SERVER_BUTTON_PIN) == LOW) {
    display.clearDisplay();
    display.setCursor(0,0);
    display.println("Factory Reset?");
    display.println("Hold 3s...");
    display.display();
    delay(3000);
    
    if (digitalRead(PAIRING_SERVER_BUTTON_PIN) == LOW) {
      displayMessage("Wiping EEPROM...", "Please wait", "", 0);
      EEPROM.begin(EEPROM_SIZE);
      for (int i = 0; i < EEPROM_SIZE; i++) EEPROM.write(i, 0);
      EEPROM.commit();
      EEPROM.end();
      displayMessage("Reset Done!", "Rebooting...", "", 2000);
      ESP.restart();
    }
  }

  deviceMacAddress = WiFi.macAddress();
  deviceUin = deviceMacAddress; // По умолчанию UIN = MAC

  display.println("ID: " + deviceUin);
  display.display();

  getOrCreateDeviceSecret();

  if (loadWifiConfig()) {
    connectToWiFi(currentConfig.ssid, currentConfig.password);
  } else {
    startProvisioningMode();
  }

  if (WiFi.status() != WL_CONNECTED) {
    startProvisioningMode();
  }

  accessToken = authenticateWithServer(deviceUin, deviceSecretKey);
  if (accessToken.length() > 0) {
    displayMessage("Auth OK!", "Token received", "", 2000);
  } else {
    displayMessage("Auth FAILED!", "Check server", "", 2000);
  }

  server.on("/send", HTTP_POST, handleSendMessage);
  server.on("/contacts", HTTP_GET, handleGetContacts);
  server.on("/messages", HTTP_GET, handleGetBufferedMessages);
  server.on("/reset_pairing", HTTP_POST, handleResetPairings);
  server.on("/set_server", HTTP_POST, []() {
    if (server.hasArg("url")) {
      String newUrl = server.arg("url");
      saveWifiConfig(currentConfig.ssid, currentConfig.password, newUrl.c_str());
      server.send(200, "text/plain", "Server URL updated. Restarting...");
      delay(1000);
      ESP.restart();
    } else {
      server.send(400, "text/plain", "Missing 'url' parameter");
    }
  });
  server.on("/token", HTTP_GET, []() {
    if (accessToken.length() > 0) {
      server.send(200, "text/plain", accessToken);
    } else {
      server.send(401, "text/plain", "Not authenticated");
    }
  });
  server.on("/", HTTP_GET, []() {
    int bat = getBatteryPercentage();
    server.send(200, "application/json", "{\"status\":\"online\",\"id\":\"" + deviceUin + "\",\"battery\":" + String(bat) + "}");
  });
  server.begin();
  udp.begin(DISCOVERY_UDP_PORT);
  DEBUG_PRINTLN("Local HTTP server started");
}

unsigned long lastPollTime = 0;
const unsigned long POLL_INTERVAL = 5000;

void loop() {
  server.handleClient();
  ESP.wdtFeed();

  int packetSize = udp.parsePacket();
  if (packetSize) {
    char packetBuffer[255];
    int len = udp.read(packetBuffer, 255);
    if (len > 0) packetBuffer[len] = 0;
    
    if (strstr(packetBuffer, "DISCOVER_GARDIANT") != NULL) {
       udp.beginPacket(udp.remoteIP(), udp.remotePort());
       udp.write("GARDIANT_HERE");
       udp.endPacket();
    }
  }

  if (millis() - lastPollTime > POLL_INTERVAL) {
    lastPollTime = millis();
    pollMessages();
  }
  
  static unsigned long lastWifiCheck = 0;
  if (millis() - lastWifiCheck > 30000) {
    lastWifiCheck = millis();
    if (WiFi.status() != WL_CONNECTED) {
      if (strlen(currentConfig.ssid) > 0) {
        WiFi.disconnect();
        WiFi.begin(currentConfig.ssid, currentConfig.password);
      }
    }
  }

  if (digitalRead(PAIRING_SERVER_BUTTON_PIN) == LOW) {
    unsigned long pressStart = millis();
    delay(50);
    if (digitalRead(PAIRING_SERVER_BUTTON_PIN) == LOW) {
      display.clearDisplay();
      display.setCursor(0,0);
      display.println("Button Pressed...");
      display.display();

      while (digitalRead(PAIRING_SERVER_BUTTON_PIN) == LOW) {
        delay(10);
        ESP.wdtFeed();
        
        if (millis() - pressStart > 5000) {
          displayMessage("Resetting", "WiFi Config...", "Release Btn", 0);
          while(digitalRead(PAIRING_SERVER_BUTTON_PIN) == LOW) { delay(10); ESP.wdtFeed(); }
          
          WifiConfig emptyConfig;
          memset(&emptyConfig, 0, sizeof(WifiConfig));
          EEPROM.begin(EEPROM_SIZE);
          EEPROM.put(ADDR_WIFI_CONFIG, emptyConfig);
          EEPROM.commit();
          EEPROM.end();
          
          displayMessage("Config Cleared", "Rebooting...", "", 2000);
          ESP.restart();
          return;
        }
      }
      startPairingAsServer();
    }
  }

  if (digitalRead(PAIRING_CLIENT_BUTTON_PIN) == LOW) {
    delay(50);
    if (digitalRead(PAIRING_CLIENT_BUTTON_PIN) == LOW) {
      while (digitalRead(PAIRING_CLIENT_BUTTON_PIN) == LOW) { delay(10); ESP.wdtFeed(); }
      startPairingAsClient();
    }
  }
  delay(10);
}

void displayMessage(const String& line1, const String& line2, const String& line3, int delayMs) {
  display.clearDisplay();
  display.setCursor(0,0);
  display.println(line1);
  if (line2.length() > 0) display.println(line2);
  if (line3.length() > 0) display.println(line3);
  display.display();
  if (delayMs > 0) delay(delayMs);
}

void connectToWiFi(const char* ssid, const char* password) {
  displayMessage("Connecting WiFi", ssid, "", 0);
  WiFi.mode(WIFI_STA);
  WiFi.setSleepMode(WIFI_NONE_SLEEP);
  WiFi.disconnect();
  delay(300);
  WiFi.begin(ssid, password);
  int retries = 0;
  while (WiFi.status() != WL_CONNECTED && retries < 40) {
    delay(1000);
    display.print(".");
    display.display();
    retries++;
    ESP.wdtFeed();
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    displayMessage("WiFi Connected!", WiFi.localIP().toString(), "", 2000);
  } else {
    displayMessage("WiFi Failed!", "Check network", "", 2000);
  }
}

void getOrCreateDeviceSecret() {
  EEPROM.begin(EEPROM_SIZE);
  EepromData data;
  EEPROM.get(ADDR_SECRET_KEY, data);
  
  if (strlen(data.secretKey) == 64 && data.checksum == calculateChecksum(data.secretKey, 64)) {
    deviceSecretKey = String(data.secretKey);
  } else {
    char hex_key[65];
    for (int i = 0; i < 32; i++) sprintf(hex_key + (i * 2), "%02x", (uint8_t)random(256));
    hex_key[64] = '\0';
    strcpy(data.secretKey, hex_key);
    data.checksum = calculateChecksum(data.secretKey, 64);
    EEPROM.put(ADDR_SECRET_KEY, data);
    EEPROM.commit();
    deviceSecretKey = String(data.secretKey);
  }
  EEPROM.end();
}

bool loadWifiConfig() {
  EEPROM.begin(EEPROM_SIZE);
  EEPROM.get(ADDR_WIFI_CONFIG, currentConfig);
  EEPROM.end();

  uint32_t hash = 5381;
  const uint8_t* p = (const uint8_t*)&currentConfig;
  for (size_t i = 0; i < sizeof(WifiConfig) - sizeof(uint32_t); ++i) hash = ((hash << 5) + hash) + p[i];
  
  return (hash == currentConfig.checksum && strlen(currentConfig.ssid) > 0);
}

void saveWifiConfig(const char* ssid, const char* password, const char* serverUrl) {
  WifiConfig newConfig;
  strncpy(newConfig.ssid, ssid, sizeof(newConfig.ssid) - 1);
  strncpy(newConfig.password, password, sizeof(newConfig.password) - 1);
  strncpy(newConfig.serverUrl, serverUrl, sizeof(newConfig.serverUrl) - 1);

  newConfig.ssid[sizeof(newConfig.ssid) - 1] = '\0';
  newConfig.password[sizeof(newConfig.password) - 1] = '\0';
  newConfig.serverUrl[sizeof(newConfig.serverUrl) - 1] = '\0';

  uint32_t hash = 5381;
  const uint8_t* p = (const uint8_t*)&newConfig;
  for (size_t i = 0; i < sizeof(WifiConfig) - sizeof(uint32_t); ++i) hash = ((hash << 5) + hash) + p[i];
  newConfig.checksum = hash;

  EEPROM.begin(EEPROM_SIZE);
  EEPROM.put(ADDR_WIFI_CONFIG, newConfig);
  EEPROM.commit();
  EEPROM.end();
}

void savePairedDevice(const uint8_t* mac, const uint8_t* sharedSecret, const char* uin) {
  EEPROM.begin(EEPROM_SIZE);
  int slot = -1;
  for (int i = 0; i < MAX_PAIRED_DEVICES; ++i) {
    PairedDevice device;
    int address = ADDR_PAIRED_DEVICES + i * sizeof(PairedDevice);
    EEPROM.get(address, device);
    if (memcmp(device.mac, mac, 6) == 0 || device.mac[0] == 0xFF || device.mac[0] == 0x00) {
      slot = i;
      break;
    }
  }

  if (slot != -1) {
    PairedDevice newDevice;
    memcpy(newDevice.mac, mac, 6);
    
    AESLib aesLib;
    uint8_t iv[16] = {0};
    uint8_t tempInput[32];

    memset(tempInput, 'T', 32);
    aesLib.encrypt(tempInput, 32, newDevice.textKey, (uint8_t*)sharedSecret, 256, iv);

    memset(iv, 0, 16);
    memset(tempInput, 'F', 32);
    aesLib.encrypt(tempInput, 32, newDevice.fileKey, (uint8_t*)sharedSecret, 256, iv);

    strncpy(newDevice.uin, uin, 16);
    newDevice.uin[16] = '\0';
    newDevice.checksum = calculateChecksum(newDevice.textKey, 32);

    int address = ADDR_PAIRED_DEVICES + slot * sizeof(PairedDevice);
    EEPROM.put(address, newDevice);
    EEPROM.commit();
  }
  EEPROM.end();
}

bool findPairedDevice(const uint8_t* mac, uint8_t* outputSecret, char* outputUin) {
  EEPROM.begin(EEPROM_SIZE);
  for (int i = 0; i < MAX_PAIRED_DEVICES; ++i) {
    PairedDevice device;
    int address = ADDR_PAIRED_DEVICES + i * sizeof(PairedDevice);
    EEPROM.get(address, device);
    if (memcmp(device.mac, mac, 6) == 0) {
      if (device.checksum == calculateChecksum(device.textKey, 32)) {
        memcpy(outputSecret, device.textKey, 32);
        if (outputUin != NULL) {
            strncpy(outputUin, device.uin, 16);
            outputUin[16] = '\0';
        }
        EEPROM.end();
        return true;
      }
    }
  }
  EEPROM.end();
  return false;
}

void startProvisioningMode() {
  WiFi.disconnect();
  delay(100);
  WiFi.softAP(PROV_AP_SSID, PROV_AP_PASSWORD);
  displayMessage("Setup Mode", "Connect to AP:", PROV_AP_SSID, 0);

  unsigned long startTime = millis();
  bool configReceived = false;

  while(!configReceived && (millis() - startTime < PROV_TIMEOUT)) {
    ESP.wdtFeed();
    
    if (digitalRead(PAIRING_SERVER_BUTTON_PIN) == LOW) {
      delay(50);
      if (digitalRead(PAIRING_SERVER_BUTTON_PIN) == LOW) {
        while(digitalRead(PAIRING_SERVER_BUTTON_PIN) == LOW) { delay(10); ESP.wdtFeed(); }
        startPairingAsServer();
        return;
      }
    }
    if (digitalRead(PAIRING_CLIENT_BUTTON_PIN) == LOW) {
      delay(50);
      if (digitalRead(PAIRING_CLIENT_BUTTON_PIN) == LOW) {
        while(digitalRead(PAIRING_CLIENT_BUTTON_PIN) == LOW) { delay(10); ESP.wdtFeed(); }
        startPairingAsClient();
        return;
      }
    }

    if (WiFi.softAPgetStationNum() > 0) {
      displayMessage("Client connected", "Requesting config...", "", 0);
      delay(1000); 

      for (int attempt = 1; attempt <= 5; ++attempt) {
        ESP.wdtFeed();
        HTTPClient http;
        WiFiClient client;
        String serverPath = "http://" + String(PROV_PHONE_IP) + ":8080/config";
        
        http.begin(client, serverPath);
        http.setTimeout(5000);
        int httpResponseCode = http.GET();

        if (httpResponseCode == 200) {
          String payload = http.getString();
          JsonDocument doc;
          DeserializationError error = deserializeJson(doc, payload);

          if (!error) {
            const char* newSsid = doc["wifi_ssid"];
            const char* newPass = doc["wifi_pass"];
            const char* newUrl = doc["server_url"];

            saveWifiConfig(newSsid, newPass, newUrl);
            configReceived = true;
            displayMessage("Config received!", "Saving...", "Rebooting...", 2000);
          }
          http.end();
          break;
        } else {
          http.end();
          delay(2000);
        }
      }
      if (configReceived) break;
    }
    delay(1000);
  }

  if (!configReceived) displayMessage("Setup Timeout!", "Rebooting...", "", 3000);
  ESP.restart();
}

void startPairingAsServer() {
  displayMessage("Pairing Mode", "Server", "Creating AP...", 0);
  WiFi.disconnect();
  delay(100);
  WiFi.mode(WIFI_AP);
  delay(100);

  if (!WiFi.softAP(PAIRING_AP_SSID, PAIRING_AP_PASSWORD)) {
    displayMessage("AP Failed!", "Restarting...", "", 2000);
    ESP.restart();
    return;
  }
  IPAddress apIP = WiFi.softAPIP();
  displayMessage("AP Created", apIP.toString(), "Waiting...", 0);

  udp.begin(PAIRING_UDP_PORT);

  const struct uECC_Curve_t * curve = uECC_secp256r1();
  const int publicKeySize = uECC_curve_public_key_size(curve);
  const int privateKeySize = uECC_curve_private_key_size(curve);
  const int sharedSecretSize = privateKeySize;
  
  uint8_t myPublicKey[publicKeySize];
  uint8_t myPrivateKey[privateKeySize];
  uint8_t partnerPublicKey[publicKeySize];
  uint8_t sharedSecret[sharedSecretSize];
  
  if (!uECC_make_key(myPublicKey, myPrivateKey, curve)) {
    displayMessage("Key Gen FAILED!", "Restarting...", "", 3000);
    ESP.restart();
    return;
  }
  
  bool pairingComplete = false;
  unsigned long pairingStartTime = millis();

  while(!pairingComplete && (millis() - pairingStartTime < PAIRING_TIMEOUT)) {
    ESP.wdtFeed();
    int packetSize = udp.parsePacket();
    if (packetSize) {
      if (packetSize >= (publicKeySize + 6 + UIN_FIELD_SIZE)) {
        uint8_t partnerMac[6];
        udp.read(partnerPublicKey, publicKeySize);
        udp.read(partnerMac, 6);
        char partnerUin[UIN_FIELD_SIZE + 1];
        udp.read(partnerUin, UIN_FIELD_SIZE);
        partnerUin[UIN_FIELD_SIZE] = '\0';

        for (int i = 0; i < 5; i++) {
            udp.beginPacket(udp.remoteIP(), udp.remotePort());
            udp.write(myPublicKey, publicKeySize);
            uint8_t myAPMac[6];
            WiFi.softAPmacAddress(myAPMac);
            udp.write(myAPMac, 6);
            char myUinBuf[UIN_FIELD_SIZE];
            memset(myUinBuf, 0, UIN_FIELD_SIZE);
            strncpy(myUinBuf, deviceUin.c_str(), UIN_FIELD_SIZE);
            udp.write(myUinBuf, UIN_FIELD_SIZE);
            udp.endPacket();
            delay(20);
        }

        if (!uECC_shared_secret(partnerPublicKey, myPrivateKey, sharedSecret, curve)) {
          displayMessage("ECDH FAILED!", "Restarting...", "", 3000);
          ESP.restart();
          return;
        }
        
        savePairedDevice(partnerMac, sharedSecret, partnerUin);
        displayMessage("Pairing SUCCESS!", "Secret generated", "Restarting...", 3000);
        pairingComplete = true;
      } else {
        uint8_t dummyBuffer[packetSize];
        udp.read(dummyBuffer, packetSize);
      }
    }
    delay(100);
  }

  if (!pairingComplete) displayMessage("Pairing TIMEOUT!", "Restarting...", "", 3000);
  ESP.restart();
}

void startPairingAsClient() {
  displayMessage("Pairing Mode", "Client", "Scanning...", 0);
  WiFi.disconnect();
  WiFi.mode(WIFI_STA);
  delay(100);

  int n = WiFi.scanNetworks();
  int networkIndex = -1;
  for (int i = 0; i < n; ++i) {
    if (WiFi.SSID(i) == PAIRING_AP_SSID) {
      networkIndex = i;
      break;
    }
  }

  if (networkIndex == -1) {
    displayMessage("AP not found!", "Check server", "Restarting...", 3000);
    ESP.restart();
    return;
  }

  displayMessage("Connecting to", "Pairing AP...", "", 0);
  WiFi.begin(PAIRING_AP_SSID, PAIRING_AP_PASSWORD);
  
  int connectAttempts = 0;
  while (WiFi.status() != WL_CONNECTED && connectAttempts < 20) {
    delay(500);
    connectAttempts++;
    ESP.wdtFeed();
  }
  
  if (WiFi.status() != WL_CONNECTED) {
    displayMessage("Connection FAILED!", "Restarting...", "", 3000);
    ESP.restart();
    return;
  }
  
  displayMessage("Connected!", "Sending key...", "", 1000);

  const struct uECC_Curve_t * curve = uECC_secp256r1();
  const int publicKeySize = uECC_curve_public_key_size(curve);
  const int privateKeySize = uECC_curve_private_key_size(curve);
  const int sharedSecretSize = privateKeySize;
  
  uint8_t myPublicKey[publicKeySize];
  uint8_t myPrivateKey[privateKeySize];
  uint8_t partnerPublicKey[publicKeySize];
  uint8_t sharedSecret[sharedSecretSize];
  
  if (!uECC_make_key(myPublicKey, myPrivateKey, curve)) {
    displayMessage("Key Gen FAILED!", "Restarting...", "", 3000);
    ESP.restart();
    return;
  }

  udp.begin(PAIRING_UDP_PORT);
  IPAddress serverIP = WiFi.gatewayIP();
  unsigned long startTime = millis();
  bool receivedResponse = false;

  while (millis() - startTime < RESPONSE_TIMEOUT && !receivedResponse) {
    ESP.wdtFeed();
    
    udp.beginPacket(serverIP, PAIRING_UDP_PORT);
    udp.write(myPublicKey, publicKeySize);
    uint8_t myMac[6];
    WiFi.macAddress(myMac);
    udp.write(myMac, 6); 
    char myUinBuf[UIN_FIELD_SIZE];
    memset(myUinBuf, 0, UIN_FIELD_SIZE);
    strncpy(myUinBuf, deviceUin.c_str(), UIN_FIELD_SIZE);
    udp.write(myUinBuf, UIN_FIELD_SIZE);
    udp.endPacket();
    
    displayMessage("Key sent", "Waiting...", "", 0);

    unsigned long waitStart = millis();
    while (millis() - waitStart < 2000 && !receivedResponse) {
      int packetSize = udp.parsePacket();
      if (packetSize > 0) {
        if (packetSize >= (publicKeySize + 6 + UIN_FIELD_SIZE)) {
          uint8_t partnerMac[6];
          udp.read(partnerPublicKey, publicKeySize);
          udp.read(partnerMac, 6);
          char partnerUin[UIN_FIELD_SIZE + 1];
          udp.read(partnerUin, UIN_FIELD_SIZE);
          partnerUin[UIN_FIELD_SIZE] = '\0';

          if (!uECC_shared_secret(partnerPublicKey, myPrivateKey, sharedSecret, curve)) {
            displayMessage("ECDH FAILED!", "Restarting...", "", 3000);
            ESP.restart();
            return;
          }
          
          savePairedDevice(partnerMac, sharedSecret, partnerUin);
          receivedResponse = true;
          displayMessage("Pairing SUCCESS!", "Secret generated", "Restarting...", 3000);
        } else {
          uint8_t dummyBuffer[packetSize];
          udp.read(dummyBuffer, packetSize);
        }
      }
      delay(50);
    }
  }

  if (!receivedResponse) displayMessage("No response!", "Check server", "Restarting...", 3000);
  ESP.restart();
}

String authenticateWithServer(const String& id, const String& key) {
  HTTPClient http;
  WiFiClient client;
  String token = "";
  
  if (strlen(currentConfig.serverUrl) == 0) return "";

  if (useGateway) {
    String gatewayIp = WiFi.gatewayIP().toString();
    String proxyUrl = "http://" + gatewayIp + ":8080/proxy/auth?device_id=" + id + "&device_key=" + key;
    http.begin(client, proxyUrl);
  } else {
    String baseUrl = String(currentConfig.serverUrl);
    if (baseUrl.endsWith("/")) baseUrl.remove(baseUrl.length() - 1);
    String serverPath = baseUrl + "/api/auth/device?device_id=" + id + "&device_key=" + key;
    
    displayMessage("Sending request", "to server...", "", 0);
    BearSSL::WiFiClientSecure clientSecure;
    
    if (serverPath.startsWith("https://")) {
      clientSecure.setInsecure();
      clientSecure.setBufferSizes(6144, 1024);
      http.begin(clientSecure, serverPath);
    } else {
      http.begin(client, serverPath);
    }
  }

  http.addHeader("Content-Type", "application/x-www-form-urlencoded");
  http.addHeader("Connection", "close");
  http.setUserAgent("GardianT-Device/1.0");
  if (useGateway) http.addHeader("Host", "gardiant-proxy");
  http.setReuse(false);
  http.setTimeout(15000);

  int httpResponseCode = -1;
  for (int i = 0; i < 3; i++) {
    httpResponseCode = http.POST("");
    if (httpResponseCode > 0) break;
    
    if (i == 2 && !useGateway) {
        useGateway = true;
        return authenticateWithServer(id, key);
    }
    delay(1000);
  }

  if (httpResponseCode > 0) {
    if (httpResponseCode == 200) {
      String payload = http.getString();
      JsonDocument doc;
      DeserializationError error = deserializeJson(doc, payload);
      if (!error) token = doc["access_token"].as<String>();
    } else {
        displayMessage("Server Error!", "Code: " + String(httpResponseCode), "", 2000);
    }
  } else {
    displayMessage("HTTP Error!", "Code: " + String(httpResponseCode), "", 2000);
  }
  
  http.end();
  return token;
}

String encryptMessage(String plainText, uint8_t* key) {
  AESLib aesLib;
  uint8_t iv[16];
  for (int i = 0; i < 16; i++) iv[i] = (uint8_t)random(256);
  
  int textLen = plainText.length();
  int paddedLen = (textLen / 16 + 1) * 16;
  uint8_t *input = new uint8_t[paddedLen];
  memset(input, 0, paddedLen);
  memcpy(input, plainText.c_str(), textLen);
  
  uint8_t padValue = (uint8_t)(paddedLen - textLen);
  for (int i = textLen; i < paddedLen; i++) input[i] = padValue;
  
  uint8_t *output = new uint8_t[paddedLen];
  uint8_t ivWorking[16];
  memcpy(ivWorking, iv, 16);
  
  aesLib.encrypt(input, paddedLen, output, key, 256, ivWorking);
  
  int totalLen = 16 + paddedLen;
  uint8_t *combined = new uint8_t[totalLen];
  memcpy(combined, iv, 16);
  memcpy(combined + 16, output, paddedLen);
  
  String result = base64::encode(combined, totalLen);
  
  delete[] input;
  delete[] output;
  delete[] combined;
  
  return result;
}

void sendToVDS(String targetUin, String encryptedData) {
  if (WiFi.status() != WL_CONNECTED) return;
  
  for (int attempt = 0; attempt < 2; attempt++) {
    HTTPClient http;
    WiFiClient client;
    BearSSL::WiFiClientSecure clientSecure;
    
    String baseUrl = String(currentConfig.serverUrl);
    if (baseUrl.endsWith("/")) baseUrl.remove(baseUrl.length() - 1);
    String url = baseUrl + "/api/chat/send";
    
    if (useGateway) {
      String gatewayIp = WiFi.gatewayIP().toString();
      String proxyUrl = "http://" + gatewayIp + ":8080/proxy/send";
      http.begin(client, proxyUrl);
    } else {
      if (url.startsWith("https://")) {
        clientSecure.setInsecure();
        clientSecure.setBufferSizes(6144, 1024);
        http.begin(clientSecure, url);
      } else {
        http.begin(client, url);
      }
    }
    
    http.addHeader("Content-Type", "application/json");
    http.addHeader("Authorization", "Bearer " + accessToken);
    http.addHeader("Connection", "close");
    http.setUserAgent("GardianT-Device/1.0");
    http.setTimeout(15000);
    
    JsonDocument doc;
    doc["to_device"] = targetUin;
    doc["encrypted_data"] = encryptedData;
    
    String requestBody;
    serializeJson(doc, requestBody);
    
    int httpCode = http.POST(requestBody);
    
    if (httpCode == 401) {
      http.end();
      String newToken = authenticateWithServer(deviceUin, deviceSecretKey);
      if (newToken.length() > 0) {
        accessToken = newToken;
        BufferedMessage bm;
        bm.fromUin = "SYSTEM";
        bm.text = "Token Updated";
        messageBuffer.push_back(bm);
        continue;
      } else {
        break;
      }
    }

    if (httpCode > 0) {
      if (httpCode == 200) {
        displayMessage("Sent to Server", "Success", "", 1000);
      } else {
        displayMessage("Server Error", String(httpCode), "", 2000);
      }
    } else {
      displayMessage("Net Error", "Check WiFi", "", 2000);
    }
    
    http.end();
    break;
  }
}

void handleSendMessage() {
  if (!server.hasArg("to") || !server.hasArg("msg")) {
    server.send(400, "text/plain", "Missing 'to' or 'msg'");
    return;
  }

  String targetMac = server.arg("to");
  String message = server.arg("msg");

  uint8_t mac[6];
  int values[6];
  if (sscanf(targetMac.c_str(), "%x:%x:%x:%x:%x:%x", &values[0], &values[1], &values[2], &values[3], &values[4], &values[5]) == 6) {
    for(int i=0; i<6; i++) mac[i] = (uint8_t)values[i];
  } else {
    server.send(400, "text/plain", "Invalid MAC format");
    return;
  }

  uint8_t secret[32];
  char targetUin[17];
  
  if (findPairedDevice(mac, secret, targetUin)) {
      displayMessage("Encrypting...", "Sending...", "", 0);
      String encrypted = encryptMessage(message, secret);
      sendToVDS(String(targetUin), encrypted);
      server.send(200, "text/plain", "Sent to VDS");
  } else {
      displayMessage("Error", "Not Paired", "", 2000);
      server.send(404, "text/plain", "Device not paired");
  }
}

void handleGetContacts() {
  JsonDocument doc;
  JsonArray contacts = doc.to<JsonArray>();

  EEPROM.begin(EEPROM_SIZE);
  for (int i = 0; i < MAX_PAIRED_DEVICES; ++i) {
    PairedDevice device;
    int address = ADDR_PAIRED_DEVICES + i * sizeof(PairedDevice);
    EEPROM.get(address, device);
    
    if (device.mac[0] != 0xFF && device.mac[0] != 0x00) {
      char macStr[18];
      sprintf(macStr, "%02X:%02X:%02X:%02X:%02X:%02X", 
              device.mac[0], device.mac[1], device.mac[2], 
              device.mac[3], device.mac[4], device.mac[5]);
      
      JsonObject contact = contacts.add<JsonObject>();
      contact["mac"] = String(macStr);

      char fileKeyHex[65];
      for(int k=0; k<32; k++) sprintf(fileKeyHex + k*2, "%02X", device.fileKey[k]);
      contact["file_key"] = String(fileKeyHex);
      
      if (strlen(device.uin) > 0) {
        contact["uin"] = String(device.uin);
        contact["name"] = String(device.uin);
      } else {
        contact["name"] = "Device " + String(macStr).substring(9);
      }
    }
  }
  EEPROM.end();

  String response;
  serializeJson(doc, response);
  server.send(200, "application/json", response);
}

void handleResetPairings() {
  EEPROM.begin(EEPROM_SIZE);
  for (int i = 0; i < MAX_PAIRED_DEVICES * sizeof(PairedDevice); ++i) {
    EEPROM.write(ADDR_PAIRED_DEVICES + i, 0);
  }
  EEPROM.commit();
  EEPROM.end();
  
  server.send(200, "text/plain", "Pairings cleared. Restarting...");
  displayMessage("Keys Cleared", "Restarting...", "", 2000);
  delay(1000);
  ESP.restart();
}

bool findPairedDeviceByUin(const char* uin, uint8_t* outputSecret) {
  EEPROM.begin(EEPROM_SIZE);
  for (int i = 0; i < MAX_PAIRED_DEVICES; ++i) {
    PairedDevice device;
    int address = ADDR_PAIRED_DEVICES + i * sizeof(PairedDevice);
    EEPROM.get(address, device);
    
    if (device.mac[0] != 0xFF && device.mac[0] != 0x00) {
       if (strncmp(device.uin, uin, 16) == 0) {
          if (device.checksum == calculateChecksum(device.textKey, 32)) {
             memcpy(outputSecret, device.textKey, 32);
             EEPROM.end();
             return true;
          }
       }
    }
  }
  EEPROM.end();
  return false;
}

String decryptMessage(String encryptedBase64, uint8_t* key) {
  size_t len = encryptedBase64.length();
  size_t bufferSize = (len * 3 / 4) + 5;
  uint8_t *combined = new uint8_t[bufferSize];
  
  base64_decodestate s;
  base64_init_decodestate(&s);
  int decodedLen = base64_decode_block(encryptedBase64.c_str(), len, (char*)combined, &s);

  if (decodedLen < 16) {
    delete[] combined;
    return "Error: Data too short";
  }

  uint8_t iv[16];
  memcpy(iv, combined, 16);

  int cipherLen = decodedLen - 16;
  uint8_t *ciphertext = new uint8_t[cipherLen];
  memcpy(ciphertext, combined + 16, cipherLen);

  uint8_t *output = new uint8_t[cipherLen];
  AESLib aesLib;
  aesLib.decrypt(ciphertext, cipherLen, output, key, 256, iv);

  uint8_t padValue = output[cipherLen - 1];
  int plainLen = cipherLen;
  if (padValue > 0 && padValue <= 16) plainLen -= padValue;
  
  String result = "";
  for (int i = 0; i < plainLen; i++) result += (char)output[i];

  delete[] combined;
  delete[] ciphertext;
  delete[] output;
  
  return result;
}

void pollMessages() {
  if (WiFi.status() != WL_CONNECTED || accessToken.length() == 0) return;

  for (int attempt = 0; attempt < 2; attempt++) {
    HTTPClient http;
    WiFiClient client;
    BearSSL::WiFiClientSecure clientSecure;
    
    String baseUrl = String(currentConfig.serverUrl);
    if (baseUrl.endsWith("/")) baseUrl.remove(baseUrl.length() - 1);
    String url = baseUrl + "/api/chat/poll";

    if (useGateway) {
      String gatewayIp = WiFi.gatewayIP().toString();
      String proxyUrl = "http://" + gatewayIp + ":8080/proxy/poll";
      http.begin(client, proxyUrl);
    } else {
      if (url.startsWith("https://")) {
        clientSecure.setInsecure();
        clientSecure.setBufferSizes(5120, 512);
        http.begin(clientSecure, url);
      } else {
        http.begin(client, url);
      }
    }
    
    http.addHeader("Authorization", "Bearer " + accessToken);
    http.addHeader("Connection", "close");
    http.setUserAgent("GardianT-Device/1.0");
    http.setTimeout(15000);
    
    int httpCode = http.GET();

    if (httpCode == 401) {
      http.end();
      String newToken = authenticateWithServer(deviceUin, deviceSecretKey);
      if (newToken.length() > 0) {
        accessToken = newToken;
        BufferedMessage bm;
        bm.fromUin = "SYSTEM";
        bm.text = "Token Updated";
        messageBuffer.push_back(bm);
        continue;
      } else {
        break;
      }
    }

    if (httpCode == 200) {
      String payload = http.getString();
      JsonDocument doc;
      DeserializationError error = deserializeJson(doc, payload);
      
      if (!error) {
        JsonArray messages = doc.as<JsonArray>();
        if (messages.size() > 0) {
          for (JsonObject msg : messages) {
            const char* fromUin = msg["from"];
            const char* encryptedData = msg["data"];
            
            uint8_t secret[32];
            if (findPairedDeviceByUin(fromUin, secret)) {
               String decrypted = decryptMessage(String(encryptedData), secret);
               BufferedMessage bm;
               bm.fromUin = String(fromUin);
               bm.text = decrypted;
               messageBuffer.push_back(bm);
               displayMessage("New Message", "Saved to buffer", "", 1000);
            }
          }
        }
      }
    }
    http.end();
    break;
  }
}

void handleGetBufferedMessages() {
  JsonDocument doc;
  JsonArray arr = doc.to<JsonArray>();
  
  for (const auto& msg : messageBuffer) {
    JsonObject obj = arr.add<JsonObject>();
    obj["from"] = msg.fromUin;
    obj["text"] = msg.text;
  }
  
  String response;
  serializeJson(doc, response);
  server.send(200, "application/json", response);
  messageBuffer.clear();
}
