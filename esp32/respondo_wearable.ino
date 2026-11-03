/*
 * ======================================================================================
 * RESPONDO BFP — ESP32 EMERGENCY WEARABLE ACCESSORY
 * Firmware: respondo_wearable.ino
 *
 * Device Name: "Respondo Wearable"
 * Description: Wearable panic accessory with Android Bluetooth Settings Pairing & Bonding.
 *
 * How to Pair with your Android Phone:
 *   1. Power on the ESP32. It will advertise as "Respondo Wearable".
 *   2. On your Android phone, go to:
 *      Settings -> Connected Devices / Bluetooth -> "Pair new device"
 *   3. Tap "Respondo Wearable" and confirm the "Pair" prompt.
 *   4. Once paired, open the Respondo BFP app. The app directly connects to your
 *      paired wearable and the status pill turns green.
 *
 * Button Emergency Sequence:
 *   1. FIRST PRESS (SOS):
 *      - User presses the button -> Sends "SOS_TRIGGER" to the phone.
 *      - Phone dispatches GPS alert to BFP and announces instructions via TTS.
 *      - Wearable enters 20-second "Awaiting Role" mode (heartbeat LED).
 *
 *   2. ROLE CONFIRMATION (Second Action):
 *      - LONG-PRESS (Hold >= 1.5 seconds): Confirms user IS THE VICTIM.
 *        -> Wearable sends "ROLE_VICTIM".
 *        -> Phone automatically transmits victim profile & medical details to BFP.
 *        -> Wearable gives 3 beeps + solid LED confirmation.
 *      - DOUBLE-PRESS (2 rapid clicks): Confirms user IS A BYSTANDER / WITNESS.
 *        -> Wearable sends "ROLE_BYSTANDER".
 *        -> Phone opens accident report sheet for scene documentation.
 *        -> Wearable gives 2 beeps + double blink confirmation.
 * ======================================================================================
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <BLESecurity.h>

// ── Hardware Pin Configuration ─────────────────────────────────────────────────────────
// Adjust pins to match your physical hardware board
#define BUTTON_PIN      4     // Push button (Active LOW with internal PULLUP). (Or GPIO 0 for BOOT)
#define LED_PIN         2     // Built-in status LED (GPIO 2 on most ESP32 boards)
#define BUZZER_PIN      18    // Passive/Active Buzzer pin (Set to -1 if no buzzer is connected)

// ── BLE UUID Configuration (Must match Android app exactly) ───────────────────────────
#define DEVICE_NAME         "Respondo Wearable"
#define SERVICE_UUID        "12345678-1234-1234-1234-123456789abc"
#define CHARACTERISTIC_UUID "87654321-4321-4321-4321-cba987654321"

// ── Timing Parameters (milliseconds) ──────────────────────────────────────────────────
#define DEBOUNCE_DELAY      50      // Debounce delay
#define LONG_PRESS_TIME     1500    // Hold >= 1.5s to confirm VICTIM role
#define DOUBLE_CLICK_TIME   450     // Max interval between 2 clicks for BYSTANDER role
#define ROLE_WINDOW_TIME    20000   // 20-second window to confirm role after initial SOS

// ── System States ─────────────────────────────────────────────────────────────────────
enum WearableState {
  STATE_IDLE,             // Normal standby, waiting for SOS press
  STATE_AWAITING_ROLE,    // Initial SOS sent; waiting for Long-Press (Victim) or Double-Press (Bystander)
  STATE_SOS_ACTIVE        // Emergency confirmed and active
};

WearableState currentState = STATE_IDLE;
unsigned long roleWindowStart = 0;

// ── BLE Global References ─────────────────────────────────────────────────────────────
BLEServer* pServer = nullptr;
BLECharacteristic* pCharacteristic = nullptr;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// ── Button Debounce & Detection Variables ─────────────────────────────────────────────
int lastButtonState = HIGH;
int buttonState = HIGH;
unsigned long lastDebounceTime = 0;
unsigned long pressStartTime = 0;
bool isPressing = false;
bool longPressTriggered = false;

int clickCount = 0;
unsigned long lastReleaseTime = 0;

// ── Beep & LED Feedback Helpers ───────────────────────────────────────────────────────
void playTone(int frequency, int durationMs) {
  if (BUZZER_PIN < 0) return;
  #if defined(ESP_ARDUINO_VERSION_MAJOR) && (ESP_ARDUINO_VERSION_MAJOR >= 3)
    tone(BUZZER_PIN, frequency, durationMs);
    delay(durationMs);
    noTone(BUZZER_PIN);
  #else
    // Universally compatible square-wave generation (works across all ESP32 core versions)
    long halfPeriodUs = 1000000L / (frequency * 2);
    long cycles = ((long)durationMs * 1000L) / (halfPeriodUs * 2);
    for (long i = 0; i < cycles; i++) {
      digitalWrite(BUZZER_PIN, HIGH);
      delayMicroseconds(halfPeriodUs);
      digitalWrite(BUZZER_PIN, LOW);
      delayMicroseconds(halfPeriodUs);
    }
  #endif
}

void feedbackSOSSent() {
  for (int i = 0; i < 4; i++) {
    digitalWrite(LED_PIN, HIGH);
    playTone(2800, 80);
    digitalWrite(LED_PIN, LOW);
    delay(60);
  }
}

void feedbackVictimConfirmed() {
  digitalWrite(LED_PIN, HIGH);
  playTone(2200, 150);
  delay(80);
  playTone(2600, 150);
  delay(80);
  playTone(3200, 300);
  delay(1000);
  digitalWrite(LED_PIN, LOW);
}

void feedbackBystanderConfirmed() {
  for (int i = 0; i < 2; i++) {
    digitalWrite(LED_PIN, HIGH);
    playTone(1800, 120);
    digitalWrite(LED_PIN, LOW);
    delay(100);
  }
}

// ── BLE Security Callbacks (Enables Phone Settings Pairing) ───────────────────────────
class SecurityCallbacks : public BLESecurityCallbacks {
  uint32_t onPassKeyRequest() override {
    Serial.println("[BLE SEC] Passkey requested");
    return 0;
  }

  void onPassKeyNotify(uint32_t pass_key) override {
    Serial.printf("[BLE SEC] Passkey notification: %d\n", pass_key);
  }

  bool onConfirmPIN(uint32_t pass_key) override {
    Serial.printf("[BLE SEC] Confirm PIN: %d\n", pass_key);
    return true;
  }

  bool onSecurityRequest() override {
    Serial.println("[BLE SEC] Security pairing request received from phone");
    return true;
  }

  void onAuthenticationComplete(esp_ble_auth_cmpl_t cmpl) override {
    if (cmpl.success) {
      Serial.println("\n[BLE SEC] >>> PAIRED & BONDED WITH PHONE SUCCESSFULLY! <<<");
      digitalWrite(LED_PIN, HIGH);
      playTone(2400, 150);
      delay(150);
      playTone(3000, 200);
      digitalWrite(LED_PIN, LOW);
    } else {
      Serial.printf("[BLE SEC] Pairing failed, reason code: 0x%x\n", cmpl.fail_reason);
    }
  }
};

// ── BLE Server Callbacks ──────────────────────────────────────────────────────────────
class ServerCallbacks: public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {
    deviceConnected = true;
    Serial.println("[BLE] Direct connection established with phone!");
    digitalWrite(LED_PIN, HIGH);
    delay(200);
    digitalWrite(LED_PIN, LOW);
  }

  void onDisconnect(BLEServer* pServer) override {
    deviceConnected = false;
    Serial.println("[BLE] Phone disconnected. Resuming advertising for reconnection...");
  }
};

// ── Transmit BLE Command to Phone ─────────────────────────────────────────────────────
void sendBLEMessage(const char* message) {
  if (!deviceConnected) {
    Serial.printf("[BLE WARN] Cannot send '%s' — phone is not directly connected!\n", message);
    return;
  }
  pCharacteristic->setValue(message);
  pCharacteristic->notify();
  Serial.printf("[BLE TX] Transmitted notification: '%s'\n", message);
}

// ── Setup ─────────────────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n==================================================");
  Serial.println("  RESPONDO BFP — ESP32 WEARABLE ACCESSORY");
  Serial.println("  Device Name: Respondo Wearable");
  Serial.println("  Mode: Phone Settings Bluetooth Pairing & Bonding");
  Serial.println("==================================================");

  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  if (BUZZER_PIN >= 0) {
    pinMode(BUZZER_PIN, OUTPUT);
  }

  // 1. Initialize BLE Device
  BLEDevice::init(DEVICE_NAME);

  // 2. Configure BLE Security & Bonding for Android Bluetooth Settings
  BLEDevice::setSecurityCallbacks(new SecurityCallbacks());

  BLESecurity *pSecurity = new BLESecurity();
  pSecurity->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_BOND); // Request bonding on phone
  pSecurity->setCapability(ESP_IO_CAP_NONE);                 // "Just Works" (Standard phone pairing prompt)
  pSecurity->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
  pSecurity->setRespEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);

  // 3. Create BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  // 4. Create BLE Service & Characteristic
  BLEService *pService = pServer->createService(SERVICE_UUID);

  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ   |
                      BLECharacteristic::PROPERTY_WRITE  |
                      BLECharacteristic::PROPERTY_NOTIFY
                    );

  pCharacteristic->addDescriptor(new BLE2902());
  pCharacteristic->setValue("READY");
  pService->start();

  // 5. Configure Discoverable Advertisement Data
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);

  BLEAdvertisementData advData;
  advData.setFlags(0x06); // General Discoverable + BR/EDR not supported
  advData.setName(DEVICE_NAME);
  advData.setCompleteServices(BLEUUID(SERVICE_UUID));
  pAdvertising->setAdvertisementData(advData);

  BLEAdvertisementData scanData;
  scanData.setName(DEVICE_NAME);
  pAdvertising->setScanResponseData(scanData);

  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();

  Serial.println("[BLE] Ready & Advertising as 'Respondo Wearable'.");
  Serial.println("[INFO] Open Phone Settings -> Bluetooth -> Tap 'Respondo Wearable' to pair.");

  // Ready beep
  playTone(2000, 100);
}

// ── Action Handlers ───────────────────────────────────────────────────────────────────
void handleFirstSOSPress() {
  Serial.println("\n[EVENT] >>> FIRST PRESS: EMERGENCY SOS TRIGGERED <<<");
  sendBLEMessage("SOS_TRIGGER");
  feedbackSOSSent();

  currentState = STATE_AWAITING_ROLE;
  roleWindowStart = millis();
  Serial.println("[STATE] Awaiting role confirmation: HOLD for VICTIM, DOUBLE-CLICK for BYSTANDER");
}

void handleConfirmVictim() {
  Serial.println("\n[EVENT] >>> LONG PRESS DETECTED: USER CONFIRMED AS VICTIM <<<");
  sendBLEMessage("ROLE_VICTIM");
  feedbackVictimConfirmed();

  currentState = STATE_SOS_ACTIVE;
  clickCount = 0;
}

void handleConfirmBystander() {
  Serial.println("\n[EVENT] >>> DOUBLE CLICK DETECTED: USER CONFIRMED AS BYSTANDER <<<");
  sendBLEMessage("ROLE_BYSTANDER");
  feedbackBystanderConfirmed();

  currentState = STATE_SOS_ACTIVE;
  clickCount = 0;
}

// ── Main Loop ─────────────────────────────────────────────────────────────────────────
void loop() {
  // ── Handle BLE Reconnection ──────────────────────────────────────────────────────────
  if (!deviceConnected && oldDeviceConnected) {
    delay(500);
    pServer->startAdvertising();
    Serial.println("[BLE] Direct advertising restarted.");
    oldDeviceConnected = deviceConnected;
  }
  if (deviceConnected && !oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
  }

  // ── Read Button with Debounce ────────────────────────────────────────────────────────
  int reading = digitalRead(BUTTON_PIN);
  unsigned long now = millis();

  if (reading != lastButtonState) {
    lastDebounceTime = now;
  }

  if ((now - lastDebounceTime) > DEBOUNCE_DELAY) {
    if (reading != buttonState) {
      buttonState = reading;

      // Button Pressed (Active LOW)
      if (buttonState == LOW) {
        pressStartTime = now;
        isPressing = true;
        longPressTriggered = false;
      }
      // Button Released
      else {
        isPressing = false;
        unsigned long pressDuration = now - pressStartTime;

        if (!longPressTriggered && pressDuration < LONG_PRESS_TIME) {
          clickCount++;
          lastReleaseTime = now;
        }
      }
    }
  }

  lastButtonState = reading;

  // ── LONG-PRESS (Hold >= 1.5s): Confirms VICTIM ──────────────────────────────────────
  if (isPressing && !longPressTriggered && (now - pressStartTime >= LONG_PRESS_TIME)) {
    longPressTriggered = true;
    clickCount = 0;

    if (currentState == STATE_IDLE) {
      handleFirstSOSPress();
      delay(300);
      handleConfirmVictim();
    } else {
      handleConfirmVictim();
    }
  }

  // ── SINGLE vs DOUBLE CLICK ──────────────────────────────────────────────────────────
  if (clickCount > 0 && !isPressing && (now - lastReleaseTime > DOUBLE_CLICK_TIME)) {
    if (clickCount == 1) {
      if (currentState == STATE_IDLE) {
        handleFirstSOSPress();
      } else if (currentState == STATE_AWAITING_ROLE) {
        Serial.println("[INFO] Single press in awaiting mode. Hold >= 1.5s for Victim or 2x for Bystander.");
      }
    } else if (clickCount >= 2) {
      if (currentState == STATE_AWAITING_ROLE || currentState == STATE_SOS_ACTIVE) {
        handleConfirmBystander();
      } else {
        handleFirstSOSPress();
        delay(300);
        handleConfirmBystander();
      }
    }
    clickCount = 0;
  }

  // ── LED Status Indication ───────────────────────────────────────────────────────────
  if (currentState == STATE_AWAITING_ROLE) {
    int pulsePhase = (now / 200) % 2;
    digitalWrite(LED_PIN, pulsePhase == 0 ? HIGH : LOW);

    if (now - roleWindowStart > ROLE_WINDOW_TIME) {
      Serial.println("[STATE] Role confirmation window expired. Entering SOS_ACTIVE.");
      currentState = STATE_SOS_ACTIVE;
      digitalWrite(LED_PIN, LOW);
    }
  } else if (currentState == STATE_SOS_ACTIVE) {
    int slowBlink = (now / 1000) % 2;
    digitalWrite(LED_PIN, slowBlink == 0 ? HIGH : LOW);
  } else {
    digitalWrite(LED_PIN, LOW);
  }

  delay(10);
}
