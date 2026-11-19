/*
 * ======================================================================================
 * RESPONDO BFP — SMART EMERGENCY WEARABLE ACCESSORY
 * Hardware: LilyGO TTGO T-Display (ESP32-D0WDQ6 + ST7789 240x135 IPS Display)
 * Physical Buttons:
 *   [TOP BUTTON - GPIO 35]   -> INSTANT VICTIM SOS (Sends User Medical & Profile)
 *   [BOTTOM BUTTON - GPIO 0] -> INSTANT BYSTANDER SOS (Report Incident Scene)
 *
 * Bluetooth Behavior (Like Smartwatch / BT Audio Device):
 *   - Device Name: "Respondo Device"
 *   - Always-Active high-frequency advertising (20ms-40ms beacon).
 *   - Direct pairing & bonding with Android Bluetooth Settings.
 *   - Ultra-low latency BLE connection priority (7.5ms interval).
 *
 * 2-Way Full Synchronization with Respondo Android App:
 *   - STATUS:ACTIVE   -> Screen displays Emergency Alert Active.
 *   - STATUS:DEPLOYED -> Screen turns green: "BFP DISPATCHED - HELP ON THE WAY!".
 *   - STATUS:RESOLVED -> Screen immediately returns to Standby GUI!
 *   - STATUS:IDLE     -> Resets to Standby GUI.
 * ======================================================================================
 */

#define LILYGO_LGFX_USE_T_DISPLAY
#include <LilyGo_LovyanGFX.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <BLESecurity.h>

// ── Hardware Configuration ─────────────────────────────────────────────────────────────
LilyGo_T_Display display;

#define BUTTON_TOP       35    // Top Button (Active LOW, board external pullup)
#define BUTTON_BOTTOM    0     // Bottom BOOT Button (Active LOW, internal pullup)

// ── BLE UUID Configuration (Matches Android App Exactly) ──────────────────────────────
#define DEVICE_NAME         "Respondo Device"
#define SERVICE_UUID        "12345678-1234-1234-1234-123456789abc"
#define CHARACTERISTIC_UUID "87654321-4321-4321-4321-cba987654321"

// ── 16-Bit RGB565 Color Palette (Matching Respondo Android App) ───────────────────────
#define COLOR_BG          0x0842    // Sleek dark charcoal (#0A0A0A)
#define COLOR_SURFACE     0x18E3    // Elevated card background
#define COLOR_BORDER      0x2965    // Card border
#define COLOR_RED         0xC905    // Respondo Fire Engine Red (#CE2029)
#define COLOR_RED_DARK    0x8800    // Dark Red Glow
#define COLOR_RED_BG      0x3000    // Red container background
#define COLOR_GREEN       0x15D0    // Respondo Success Green (#10B981)
#define COLOR_GREEN_BG    0x0284    // Dark Forest Green
#define COLOR_BLUE        0x3C1F    // Respondo Info Blue (#3B82F6)
#define COLOR_BLUE_BG     0x0973    // Dark Navy container
#define COLOR_ORANGE      0xFD20    // Warning Orange (#FF6B00)
#define COLOR_YELLOW      0xFFE0    // Yellow highlight
#define COLOR_WHITE       0xFFFF    // Clean white
#define COLOR_MUTED       0x8410    // Muted secondary text
#define COLOR_DARK_GRAY   0x4208    // Disabled / footer text

// ── System States ─────────────────────────────────────────────────────────────────────
enum WearableState {
  STATE_UNLINKED,          // Phone not connected
  STATE_IDLE,              // Phone connected & Ready on Standby
  STATE_VICTIM,            // Confirmed Victim SOS dispatched
  STATE_BYSTANDER,         // Confirmed Bystander SOS dispatched
  STATE_DEPLOYED,          // BFP Responders En Route
  STATE_NOT_CONNECTED_WARN // Warning when button pressed while unlinked
};

WearableState currentState = STATE_UNLINKED;
unsigned long warningStartTime = 0;
bool needScreenRedraw = true;

// ── BLE Global References ─────────────────────────────────────────────────────────────
BLEServer* pServer = nullptr;
BLECharacteristic* pCharacteristic = nullptr;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// ── Button Debounce States ─────────────────────────────────────────────────────────────
int lastTopState = HIGH;
int lastBottomState = HIGH;
unsigned long lastTopPressTime = 0;
unsigned long lastBottomPressTime = 0;
const unsigned long DEBOUNCE_MS = 50;

// ── Forward Declarations ──────────────────────────────────────────────────────────────
void drawScreen();
void handlePhoneCommand(String cmd);

// ── Transmit BLE Notification to Phone ─────────────────────────────────────────────────
void sendBLEMessage(const char* message) {
  if (!deviceConnected || pCharacteristic == nullptr) {
    Serial.printf("[BLE WARN] Cannot send '%s' — phone not connected!\n", message);
    return;
  }
  pCharacteristic->setValue(message);
  pCharacteristic->notify();
  Serial.printf("[BLE TX -> Phone]: %s\n", message);
}

// ── Handle Incoming Sync Commands from Phone ──────────────────────────────────────────
void handlePhoneCommand(String cmd) {
  Serial.printf("[SYNC FROM PHONE]: %s\n", cmd.c_str());

  if (cmd == "STATUS:RESOLVED" || cmd == "STATUS:IDLE") {
    Serial.println("[SYNC] Incident resolved on phone! Returning to Standby GUI.");
    currentState = deviceConnected ? STATE_IDLE : STATE_UNLINKED;
    needScreenRedraw = true;
  } else if (cmd == "STATUS:DEPLOYED") {
    Serial.println("[SYNC] BFP Responders en route! Updating screen to DEPLOYED.");
    currentState = STATE_DEPLOYED;
    needScreenRedraw = true;
  } else if (cmd == "STATUS:ACTIVE") {
    Serial.println("[SYNC] SOS confirmed active in BFP system.");
    if (currentState == STATE_IDLE || currentState == STATE_UNLINKED) {
      currentState = STATE_VICTIM;
      needScreenRedraw = true;
    }
  } else if (cmd == "STATUS:VICTIM") {
    currentState = STATE_VICTIM;
    needScreenRedraw = true;
  } else if (cmd == "STATUS:BYSTANDER") {
    currentState = STATE_BYSTANDER;
    needScreenRedraw = true;
  }
}

// ── BLE Server Callbacks ──────────────────────────────────────────────────────────────
class ServerCallbacks: public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) override {
    deviceConnected = true;
    Serial.println("\n[BLE] >>> DIRECT GATT CONNECTION ESTABLISHED WITH PHONE! <<<");
    currentState = STATE_IDLE;
    needScreenRedraw = true;
  }

  void onDisconnect(BLEServer* pServer) override {
    deviceConnected = false;
    Serial.println("\n[BLE] Phone disconnected. Resuming high-speed advertising beacon...");
    currentState = STATE_UNLINKED;
    needScreenRedraw = true;
    pServer->startAdvertising(); // Immediately resume advertising like BT headphones
  }
};

// ── BLE Characteristic Callbacks (Receives Writes from Phone) ─────────────────────────
class CharacteristicCallbacks: public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) override {
    String msg = pChar->getValue();
    if (msg.length() > 0) {
      msg.trim();
      handlePhoneCommand(msg);
    }
  }
};

// ── BLE Security Callbacks (Allows Android Settings Pairing) ──────────────────────────
class SecurityCallbacks : public BLESecurityCallbacks {
  uint32_t onPassKeyRequest() override { return 0; }
  void onPassKeyNotify(uint32_t pass_key) {}
  bool onConfirmPIN(uint32_t pass_key) { return true; }
  bool onSecurityRequest() { return true; }
  void onAuthenticationComplete(esp_ble_auth_cmpl_t cmpl) {
    if (cmpl.success) {
      Serial.println("[BLE SEC] Paired & bonded successfully with phone!");
    }
  }
};

// ── UI Drawing Functions (240x135 Landscape) ──────────────────────────────────────────

// Top App Bar
void drawTopBar(const char* title, const char* statusPill, uint16_t pillColor) {
  // Brand red accent stripe across top
  display.fillRect(0, 0, display.width(), 2, COLOR_RED);
  // Header background
  display.fillRect(0, 2, display.width(), 20, COLOR_BG);

  // App Title
  display.setTextDatum(textdatum_t::middle_left);
  display.setTextColor(COLOR_WHITE, COLOR_BG);
  display.drawString(title, 8, 12, &fonts::Font2);

  // Subtitle BFP badge
  display.fillRoundRect(88, 5, 28, 14, 3, COLOR_RED_BG);
  display.drawRoundRect(88, 5, 28, 14, 3, COLOR_RED);
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_RED, COLOR_RED_BG);
  display.drawString("BFP", 102, 12, &fonts::Font0);

  // Status Pill on top right
  display.setTextDatum(textdatum_t::middle_right);
  display.setTextColor(pillColor, COLOR_BG);
  display.drawString(statusPill, display.width() - 8, 12, &fonts::Font2);

  // Separator line
  display.drawFastHLine(0, 22, display.width(), COLOR_BORDER);
}

// Screen 1: Unlinked / Bluetooth Pairing Screen (Matches Android BLE Bottom Sheet)
void drawUnlinkedScreen() {
  display.fillScreen(COLOR_BG);
  drawTopBar("RESPONDO", "* PAIRING", COLOR_ORANGE);

  // Main pairing card
  display.fillRoundRect(6, 26, display.width() - 12, display.height() - 32, 6, COLOR_SURFACE);
  display.drawRoundRect(6, 26, display.width() - 12, display.height() - 32, 6, COLOR_BORDER);

  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_BLUE, COLOR_SURFACE);
  display.drawString("BLUETOOTH PAIRING", display.width() / 2, 42, &fonts::Font2);

  display.setTextColor(COLOR_MUTED, COLOR_SURFACE);
  display.drawString("Connect Phone in Bluetooth Settings:", display.width() / 2, 62, &fonts::Font0);

  // Device Name Badge
  display.fillRoundRect(24, 73, display.width() - 48, 28, 5, COLOR_BG);
  display.drawRoundRect(24, 73, display.width() - 48, 28, 5, COLOR_YELLOW);
  display.setTextColor(COLOR_YELLOW, COLOR_BG);
  display.drawString("\"Respondo Device\"", display.width() / 2, 87, &fonts::Font2);

  display.setTextDatum(textdatum_t::bottom_center);
  display.setTextColor(COLOR_MUTED, COLOR_SURFACE);
  display.drawString("Open Respondo App to link automatically", display.width() / 2, display.height() - 8, &fonts::Font0);
}

// Screen 2: Button Pressed While Not Connected
void drawNotConnectedWarning() {
  display.fillScreen(COLOR_RED);

  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_RED);
  display.drawString("PHONE NOT LINKED!", display.width() / 2, 32, &fonts::Font4);

  display.setTextColor(COLOR_YELLOW, COLOR_RED);
  display.drawString("Connect Bluetooth First:", display.width() / 2, 64, &fonts::Font2);

  display.setTextColor(COLOR_WHITE, COLOR_RED);
  display.drawString("\"Respondo Device\"", display.width() / 2, 88, &fonts::Font2);

  display.setTextDatum(textdatum_t::bottom_center);
  display.drawString("Alert requires phone connection", display.width() / 2, display.height() - 6, &fonts::Font0);
}

// Screen 3: Standby Screen (Mirroring the Respondo Android App Layout)
void drawIdleScreen() {
  display.fillScreen(COLOR_BG);
  drawTopBar("RESPONDO", "* SYNCED", COLOR_GREEN);

  // ── LEFT SIDE: Stylized Circular SOS Button (Matches Android btnSOS) ───────────────
  int cx = 46;
  int cy = 76;

  // Outer glowing ring
  display.drawCircle(cx, cy, 38, COLOR_RED_DARK);
  display.drawCircle(cx, cy, 37, COLOR_RED_DARK);

  // Mid pulse ring
  display.drawCircle(cx, cy, 33, display.color565(180, 20, 20));

  // Solid Center Red Button
  display.fillCircle(cx, cy, 28, COLOR_RED);

  // Bold "SOS" text
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_RED);
  display.drawString("SOS", cx, cy, &fonts::Font4);

  // Subtext under circle
  display.setTextDatum(textdatum_t::bottom_center);
  display.setTextColor(COLOR_MUTED, COLOR_BG);
  display.drawString("DIRECT SYNC", cx, 126, &fonts::Font0);

  // ── RIGHT SIDE: Dual Role Action Cards ─────────────────────────────────────────────

  // TOP CARD: VICTIM SOS (Triggered by Top Button - GPIO 35)
  display.fillRoundRect(94, 26, 140, 48, 6, COLOR_RED_BG);
  display.drawRoundRect(94, 26, 140, 48, 6, COLOR_RED);

  display.setTextDatum(textdatum_t::middle_left);
  display.setTextColor(COLOR_YELLOW, COLOR_RED_BG);
  display.drawString("[TOP BTN]", 98, 38, &fonts::Font0);

  display.setTextDatum(textdatum_t::middle_left);
  display.setTextColor(COLOR_WHITE, COLOR_RED_BG);
  display.drawString("VICTIM SOS", 98, 54, &fonts::Font2);

  display.setTextDatum(textdatum_t::middle_left);
  display.setTextColor(COLOR_MUTED, COLOR_RED_BG);
  display.drawString("Medical & Profile", 98, 67, &fonts::Font0);

  // BOTTOM CARD: BYSTANDER SOS (Triggered by Bottom Button - GPIO 0)
  display.fillRoundRect(94, 78, 140, 48, 6, COLOR_BLUE_BG);
  display.drawRoundRect(94, 78, 140, 48, 6, COLOR_BLUE);

  display.setTextDatum(textdatum_t::middle_left);
  display.setTextColor(COLOR_YELLOW, COLOR_BLUE_BG);
  display.drawString("[BOT BTN]", 98, 90, &fonts::Font0);

  display.setTextDatum(textdatum_t::middle_left);
  display.setTextColor(COLOR_WHITE, COLOR_BLUE_BG);
  display.drawString("BYSTANDER", 98, 106, &fonts::Font2);

  display.setTextDatum(textdatum_t::middle_left);
  display.setTextColor(COLOR_MUTED, COLOR_BLUE_BG);
  display.drawString("Report Scene / Witness", 98, 119, &fonts::Font0);
}

// Screen 4: Victim SOS Active (Mirroring Android "ALERT ACTIVE")
void drawVictimScreen() {
  display.fillScreen(COLOR_BG);

  // Red Emergency Header Banner
  display.fillRect(0, 0, display.width(), 24, COLOR_RED);
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_RED);
  display.drawString("! EMERGENCY ALERT ACTIVE !", display.width() / 2, 12, &fonts::Font2);

  // Card
  display.fillRoundRect(6, 28, display.width() - 12, display.height() - 34, 6, COLOR_RED_BG);
  display.drawRoundRect(6, 28, display.width() - 12, display.height() - 34, 6, COLOR_RED);

  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_RED_BG);
  display.drawString("VICTIM CONFIRMED", display.width() / 2, 48, &fonts::Font4);

  display.setTextColor(COLOR_YELLOW, COLOR_RED_BG);
  display.drawString("Medical Profile Transmitted", display.width() / 2, 76, &fonts::Font2);

  display.setTextColor(COLOR_WHITE, COLOR_RED_BG);
  display.drawString("BFP Station Notified via Phone", display.width() / 2, 98, &fonts::Font2);

  display.setTextDatum(textdatum_t::bottom_center);
  display.setTextColor(COLOR_MUTED, COLOR_RED_BG);
  display.drawString("Hold tight - Responders alerted", display.width() / 2, display.height() - 8, &fonts::Font0);
}

// Screen 5: Bystander SOS Active
void drawBystanderScreen() {
  display.fillScreen(COLOR_BG);

  // Blue Emergency Header Banner
  display.fillRect(0, 0, display.width(), 24, COLOR_BLUE);
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_BLUE);
  display.drawString("! BYSTANDER REPORT ACTIVE !", display.width() / 2, 12, &fonts::Font2);

  // Card
  display.fillRoundRect(6, 28, display.width() - 12, display.height() - 34, 6, COLOR_BLUE_BG);
  display.drawRoundRect(6, 28, display.width() - 12, display.height() - 34, 6, COLOR_BLUE);

  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_BLUE_BG);
  display.drawString("WITNESS REPORT SENT", display.width() / 2, 48, &fonts::Font4);

  display.setTextColor(COLOR_YELLOW, COLOR_BLUE_BG);
  display.drawString("Scene Alert Transmitted", display.width() / 2, 76, &fonts::Font2);

  display.setTextColor(COLOR_WHITE, COLOR_BLUE_BG);
  display.drawString("BFP Station Notified via Phone", display.width() / 2, 98, &fonts::Font2);

  display.setTextDatum(textdatum_t::bottom_center);
  display.setTextColor(COLOR_MUTED, COLOR_BLUE_BG);
  display.drawString("Documenting incident details", display.width() / 2, display.height() - 8, &fonts::Font0);
}

// Screen 6: BFP Responders Dispatched (Mirroring Android "BFP DISPATCHED")
void drawDeployedScreen() {
  display.fillScreen(COLOR_BG);

  // Green Dispatch Header Banner
  display.fillRect(0, 0, display.width(), 24, COLOR_GREEN);
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_BG, COLOR_GREEN);
  display.drawString("🚒 BFP DISPATCHED", display.width() / 2, 12, &fonts::Font2);

  // Card
  display.fillRoundRect(6, 28, display.width() - 12, display.height() - 34, 6, COLOR_GREEN_BG);
  display.drawRoundRect(6, 28, display.width() - 12, display.height() - 34, 6, COLOR_GREEN);

  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_GREEN_BG);
  display.drawString("HELP ON THE WAY!", display.width() / 2, 48, &fonts::Font4);

  display.setTextColor(COLOR_YELLOW, COLOR_GREEN_BG);
  display.drawString("Fire Responders En Route", display.width() / 2, 76, &fonts::Font2);

  display.setTextColor(COLOR_WHITE, COLOR_GREEN_BG);
  display.drawString("Live GPS Tracking Active", display.width() / 2, 98, &fonts::Font2);

  display.setTextDatum(textdatum_t::bottom_center);
  display.setTextColor(COLOR_WHITE, COLOR_GREEN_BG);
  display.drawString("GPS tracked by Fire Department", display.width() / 2, display.height() - 8, &fonts::Font0);
}

void drawScreen() {
  switch (currentState) {
    case STATE_UNLINKED:
      drawUnlinkedScreen();
      break;
    case STATE_NOT_CONNECTED_WARN:
      drawNotConnectedWarning();
      break;
    case STATE_IDLE:
      drawIdleScreen();
      break;
    case STATE_VICTIM:
      drawVictimScreen();
      break;
    case STATE_BYSTANDER:
      drawBystanderScreen();
      break;
    case STATE_DEPLOYED:
      drawDeployedScreen();
      break;
    default:
      break;
  }
}

// ── Setup ─────────────────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  delay(150);

  // Initialize Hardware Buttons
  pinMode(BUTTON_TOP, INPUT);           // Top Button (GPIO 35)
  pinMode(BUTTON_BOTTOM, INPUT_PULLUP); // Bottom Button (GPIO 0, BOOT)

  // Initialize LilyGO T-Display (Rotation 1 = Landscape 240x135, Brightness 220)
  display.begin(1, 220);
  display.fillScreen(COLOR_BG);

  // Splash Screen (Matches Android Splash)
  display.fillRect(0, 0, display.width(), 3, COLOR_RED);
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_BG);
  display.drawString("RESPONDO", display.width() / 2, display.height() / 2 - 12, &fonts::Font4);
  display.setTextColor(COLOR_RED, COLOR_BG);
  display.drawString("Bureau of Fire Protection", display.width() / 2, display.height() / 2 + 14, &fonts::Font2);
  delay(1000);

  // 1. Initialize BLE Device as "Respondo Device"
  BLEDevice::init(DEVICE_NAME);

  // 2. Configure BLE Security & Bonding (Allows Android Settings Pairing)
  BLEDevice::setSecurityCallbacks(new SecurityCallbacks());

  BLESecurity *pSecurity = new BLESecurity();
  pSecurity->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_BOND);
  pSecurity->setCapability(ESP_IO_CAP_NONE);
  pSecurity->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);
  pSecurity->setRespEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);

  // 3. Create BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  // 4. Create BLE Service & Characteristic (Supports Notify & Write for 2-Way Sync)
  BLEService *pService = pServer->createService(SERVICE_UUID);

  pCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID,
                      BLECharacteristic::PROPERTY_READ     |
                      BLECharacteristic::PROPERTY_WRITE    |
                      BLECharacteristic::PROPERTY_WRITE_NR |
                      BLECharacteristic::PROPERTY_NOTIFY
                    );

  pCharacteristic->setCallbacks(new CharacteristicCallbacks());
  pCharacteristic->addDescriptor(new BLE2902());
  pCharacteristic->setValue("READY");
  pService->start();

  // 5. Configure High-Speed Always-Active Advertisement (Like Smartwatch / BT Audio)
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);

  BLEAdvertisementData advData;
  advData.setFlags(0x06); // General Discoverable + BR_EDR_NOT_SUPPORTED
  advData.setName(DEVICE_NAME);
  advData.setAppearance(0x0C00); // Generic Watch (displays wearable icon in Android)
  pAdvertising->setAdvertisementData(advData);

  BLEAdvertisementData scanData;
  scanData.setCompleteServices(BLEUUID(SERVICE_UUID));
  scanData.setName(DEVICE_NAME);
  pAdvertising->setScanResponseData(scanData);

  // Fast 20ms - 40ms advertising intervals for instant phone discovery
  pAdvertising->setMinInterval(0x20); // 20ms
  pAdvertising->setMaxInterval(0x40); // 40ms
  pAdvertising->setMinPreferred(0x06); // 7.5ms BLE connection interval
  pAdvertising->setMinPreferred(0x12); // 22.5ms BLE connection interval
  BLEDevice::startAdvertising();

  Serial.println("[BLE] Ready & Advertising as 'Respondo Device'.");

  // Initial Screen
  currentState = STATE_UNLINKED;
  drawScreen();
}

// ── Main Loop ─────────────────────────────────────────────────────────────────────────
void loop() {
  unsigned long now = millis();

  // ── Auto Re-advertise when Disconnected (Always Active) ──────────────────────────────
  if (!deviceConnected && oldDeviceConnected) {
    delay(200);
    pServer->startAdvertising();
    Serial.println("[BLE] Advertising beacon restarted.");
    oldDeviceConnected = deviceConnected;
  }
  if (deviceConnected && !oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
  }

  // ── Dismiss Temporary Warning Screen ─────────────────────────────────────────────────
  if (currentState == STATE_NOT_CONNECTED_WARN && (now - warningStartTime > 2500)) {
    currentState = STATE_UNLINKED;
    needScreenRedraw = true;
  }

  // ── Button Edge Detection (Direct 0ms Instant Response) ──────────────────────────────
  int readTop = digitalRead(BUTTON_TOP);
  int readBottom = digitalRead(BUTTON_BOTTOM);

  // TOP BUTTON DOWN (GPIO 35) -> VICTIM SOS TRIGGER
  if (readTop == LOW && lastTopState == HIGH && (now - lastTopPressTime > DEBOUNCE_MS)) {
    lastTopPressTime = now;
    Serial.println("\n[TOP BTN] >>> VICTIM SOS PRESSED! <<<");

    if (!deviceConnected) {
      Serial.println("[WARN] Cannot send SOS: Phone not connected!");
      currentState = STATE_NOT_CONNECTED_WARN;
      warningStartTime = now;
      needScreenRedraw = true;
    } else {
      Serial.println("[ACTION] Transmitting 'SOS:VICTIM' to phone...");
      sendBLEMessage("SOS:VICTIM");
      currentState = STATE_VICTIM;
      needScreenRedraw = true;
    }
  }

  // BOTTOM BUTTON DOWN (GPIO 0) -> BYSTANDER SOS TRIGGER
  if (readBottom == LOW && lastBottomState == HIGH && (now - lastBottomPressTime > DEBOUNCE_MS)) {
    lastBottomPressTime = now;
    Serial.println("\n[BOT BTN] >>> BYSTANDER SOS PRESSED! <<<");

    if (!deviceConnected) {
      Serial.println("[WARN] Cannot send SOS: Phone not connected!");
      currentState = STATE_NOT_CONNECTED_WARN;
      warningStartTime = now;
      needScreenRedraw = true;
    } else {
      Serial.println("[ACTION] Transmitting 'SOS:BYSTANDER' to phone...");
      sendBLEMessage("SOS:BYSTANDER");
      currentState = STATE_BYSTANDER;
      needScreenRedraw = true;
    }
  }

  lastTopState = readTop;
  lastBottomState = readBottom;

  // ── Screen Rendering ─────────────────────────────────────────────────────────────────
  if (needScreenRedraw) {
    needScreenRedraw = false;
    drawScreen();
  }

  delay(10);
}
