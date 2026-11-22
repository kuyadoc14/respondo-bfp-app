/*
 * ======================================================================================
 * RESPONDO BFP — SMART EMERGENCY WEARABLE ACCESSORY (PORTRAIT EDITION)
 * Hardware: LilyGO TTGO T-Display (ESP32-D0WDQ6 + ST7789 135x240 IPS Display)
 * Orientation: Portrait (135x240)
 *
 * Physical Buttons:
 *   [LEFT BUTTON  - GPIO 0]  -> WITNESS (Scene Report Alert)
 *   [RIGHT BUTTON - GPIO 35] -> VICTIM (Personal Emergency SOS & Medical Profile)
 *   [RESET BUTTON - EN]      -> POWER MANAGEMENT:
 *                                - Press 1x: Prompts "Press RST again to shut down"
 *                                - Press 2nd time: Powers off into Deep Sleep
 *                                - Press 1x when off: Powers ON device immediately
 *
 * Features:
 *   1. Clean Minimalist Portrait UI (135w x 240h):
 *      - Top bar with Respondo title, BLE status, and mini battery gauge
 *      - Upper section: Stylized 3D Circular SOS button & sync pill
 *      - Bottom section: Sleek, clutter-free "Witness" (Blue) and "Victim" (Red) buttons
 *   2. Mini Battery Telemetry:
 *      - Charging indicator: Lightning logo (⚡) drawn cleanly inside the battery
 *      - Displays "CHG" when connected to power instead of forced 100%
 *      - Real-time voltage & percentage monitoring when on battery
 *      - Low-battery indicator (< 15%)
 *   3. Critical Low Battery Protection:
 *      - Graphic screen: "Device low, please charge" with battery icon & charger graphics
 *      - Safe shutdown into ultra-low-power Deep Sleep
 *   4. Power / Shutdown Control via RST:
 *      - RST press while running triggers shutdown prompt (with 4s countdown)
 *      - 2nd RST press (or holding Victim button) shuts down immediately
 *      - Tapping Witness button or waiting 4s cancels shutdown prompt
 *      - Single RST press when off powers device ON cleanly
 *   5. Real-Time 2-Way Sync with Phone & Firebase:
 *      - Listens for STATUS:DEPLOYED / STATE:DEPLOYED -> Shows Dispatched screen
 *      - Listens for STATUS:RESOLVED / STATE:IDLE -> Returns to Standby screen
 *      - User can tap either button on DEPLOYED screen to dismiss back to Standby
 *      - User can hold either button on VICTIM / BYSTANDER screen to reset back to Standby
 * ======================================================================================
 */

#define LILYGO_LGFX_USE_T_DISPLAY
#include <LilyGo_LovyanGFX.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <BLESecurity.h>
#include <Preferences.h>
#include <esp_sleep.h>

// ── Hardware Configuration ─────────────────────────────────────────────────────────────
LilyGo_T_Display display;

// Accurate Portrait Button Positions (USB & Buttons at Bottom)
#define BUTTON_LEFT      0     // Physical Left Button (GPIO 0 / BOOT) -> WITNESS
#define BUTTON_RIGHT     35    // Physical Right Button (GPIO 35) -> VICTIM
#define PIN_ADC_EN       14    // ADC power gate enable on TTGO T-Display
#define PIN_BAT_ADC      34    // Battery voltage divider (VBAT / 2)

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
#define COLOR_GREEN_BG    0x0265    // Green container background
#define COLOR_BLUE        0x23F9    // Bystander Blue (#2563EB)
#define COLOR_BLUE_BG     0x098B    // Blue container background
#define COLOR_ORANGE      0xFA40    // Respondo Orange (#F97316)
#define COLOR_YELLOW      0xFEA0    // Respondo Yellow (#FACC15)
#define COLOR_WHITE       0xFFFF
#define COLOR_MUTED       0x8410    // Subtle gray text (#888888)

// ── Device State Machine ──────────────────────────────────────────────────────────────
enum DeviceState {
  STATE_UNLINKED,           // BLE advertising, waiting for phone connection
  STATE_NOT_CONNECTED_WARN, // Button clicked but no BLE link
  STATE_IDLE,               // Linked & synced, ready for trigger
  STATE_VICTIM,             // Victim SOS active
  STATE_BYSTANDER,          // Bystander scene report active
  STATE_DEPLOYED,           // Responders en route
  STATE_LOW_BATTERY         // Critical low battery warning before shutdown
};

volatile DeviceState currentState = STATE_UNLINKED;
bool deviceConnected    = false;
bool oldDeviceConnected = false;
bool needScreenRedraw   = true;

// ── Telemetry & Power Tracking ─────────────────────────────────────────────────────────
struct BatteryState {
  float voltage;
  int percentage;
  bool isCharging;
  bool isLow;
};

BatteryState currentBattery = {4.0f, 85, false, false};
unsigned long lastBatteryCheckTime = 0;
int lowBatCounter = 0;

// Button Debounce Timestamps
unsigned long rightPressStartTime = 0;
unsigned long leftPressStartTime  = 0;
int lastRightState = HIGH;
int lastLeftState  = HIGH;

// Warning Screen Timer
unsigned long warningStartTime = 0;

// ── BLE Pointers ──────────────────────────────────────────────────────────────────────
BLEServer         *pServer         = nullptr;
BLECharacteristic *pCharacteristic = nullptr;

// ── Forward Declarations ──────────────────────────────────────────────────────────────
void drawScreen();
void drawTopBar(const char* title, const char* statusPill, uint16_t pillColor);
void drawMiniBattery(int x, int y);
void drawUnlinkedScreen();
void drawNotConnectedWarning();
void drawIdleScreen();
void drawVictimScreen();
void drawBystanderScreen();
void drawDeployedScreen();
void drawLowBatteryScreen();
void drawRstShutdownPrompt(int progress, int secondsLeft);
void powerOffDevice();
BatteryState readBattery();

// ── BLE Server Callbacks ──────────────────────────────────────────────────────────────
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    deviceConnected = true;
    Serial.println("\n[BLE] >>> Connected to Respondo Mobile App! <<<");
    currentState = STATE_IDLE;
    needScreenRedraw = true;
  }

  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
    Serial.println("\n[BLE] >>> Disconnected from Phone! <<<");
    currentState = STATE_UNLINKED;
    needScreenRedraw = true;
  }
};

// ── BLE Characteristic Callbacks (Receives Commands from Android) ─────────────────────
class CharacteristicCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) {
    String value = pChar->getValue();
    if (value.length() > 0) {
      value.trim();
      Serial.printf("[BLE RX] Command received: '%s'\n", value.c_str());

      // Handle status updates from Android app / Firebase
      if (value.equalsIgnoreCase("STATUS:DEPLOYED") || 
          value.equalsIgnoreCase("STATE:DEPLOYED") || 
          value.equalsIgnoreCase("DEPLOYED")) {
        Serial.println("[STATE] Status -> DEPLOYED (Help En Route)");
        currentState = STATE_DEPLOYED;
        needScreenRedraw = true;
      } else if (value.equalsIgnoreCase("STATUS:RESOLVED") || 
                 value.equalsIgnoreCase("STATE:IDLE") || 
                 value.equalsIgnoreCase("STATUS:IDLE") || 
                 value.equalsIgnoreCase("RESOLVED") || 
                 value.equalsIgnoreCase("IDLE")) {
        Serial.println("[STATE] Status -> RESOLVED / IDLE (Standby)");
        currentState = STATE_IDLE;
        needScreenRedraw = true;
      } else if (value.equalsIgnoreCase("STATUS:ACTIVE") || 
                 value.equalsIgnoreCase("STATE:VICTIM") || 
                 value.equalsIgnoreCase("VICTIM")) {
        Serial.println("[STATE] Status -> VICTIM ACTIVE");
        currentState = STATE_VICTIM;
        needScreenRedraw = true;
      } else if (value.equalsIgnoreCase("STATE:BYSTANDER") || 
                 value.equalsIgnoreCase("BYSTANDER")) {
        Serial.println("[STATE] Status -> BYSTANDER ACTIVE");
        currentState = STATE_BYSTANDER;
        needScreenRedraw = true;
      } else if (value.equalsIgnoreCase("PWR:SHUTDOWN")) {
        Serial.println("[BLE RX] Remote shutdown request received.");
        powerOffDevice();
      }
    }
  }
};

// ── Security Callbacks ─────────────────────────────────────────────────────────────────
class SecurityCallbacks : public BLESecurityCallbacks {
  uint32_t onPassKeyRequest() { return 0; }
  void onPassKeyNotify(uint32_t pass_key) {}
  bool onConfirmPIN(uint32_t pass_key) { return true; }
  bool onSecurityRequest() { return true; }
  void onAuthenticationComplete(esp_ble_auth_cmpl_t cmpl) {
    Serial.printf("[BLE SEC] Pair result: %s\n", cmpl.success ? "SUCCESS" : "FAILED");
  }
};

// ── Send BLE Notification to Phone ────────────────────────────────────────────────────
void sendBLEMessage(const char* msg) {
  if (deviceConnected && pCharacteristic != nullptr) {
    pCharacteristic->setValue(msg);
    pCharacteristic->notify();
    Serial.printf("[BLE TX] Sent: %s\n", msg);
  } else {
    Serial.printf("[BLE TX-FAIL] Cannot send '%s': Phone not connected.\n", msg);
  }
}

// ── Battery ADC Reader ────────────────────────────────────────────────────────────────
BatteryState readBattery() {
  BatteryState bs;

  digitalWrite(PIN_ADC_EN, HIGH);
  delayMicroseconds(200);

  uint32_t rawSum = 0;
  for (int i = 0; i < 16; i++) {
    rawSum += analogRead(PIN_BAT_ADC);
    delayMicroseconds(100);
  }
  float rawAvg = (float)rawSum / 16.0f;

  float pinVoltage = (rawAvg / 4095.0f) * 3.3f * 1.135f;
  bs.voltage = pinVoltage * 2.0f;

  // On TTGO T-Display, USB power rail brings VBAT ADC to >= 4.24V
  if (bs.voltage >= 4.24f) {
    bs.isCharging = true;
    bs.percentage = 100;
  } else {
    bs.isCharging = false;
    if (bs.voltage >= 4.18f) bs.percentage = 100;
    else if (bs.voltage <= 3.30f) bs.percentage = 0;
    else {
      bs.percentage = (int)((bs.voltage - 3.30f) / (4.18f - 3.30f) * 100.0f);
    }
  }

  if (bs.percentage > 100) bs.percentage = 100;
  if (bs.percentage < 0)   bs.percentage = 0;
  bs.isLow = (bs.percentage <= 15 && !bs.isCharging);

  return bs;
}

// ── Mini Battery Indicator on Top Bar ─────────────────────────────────────────────────
void drawMiniBattery(int x, int y) {
  int batW = 20;
  int batH = 10;

  if (currentBattery.isCharging) {
    // Battery Outline in Green
    display.drawRoundRect(x, y, batW, batH, 2, COLOR_GREEN);
    display.fillRect(x + batW, y + 3, 2, 4, COLOR_GREEN);

    // Draw Lightning Bolt inside the battery
    int bx = x + 10;
    int by = y + 2;
    display.drawLine(bx + 1, by, bx - 1, by + 3, COLOR_YELLOW);
    display.drawLine(bx + 2, by, bx, by + 3, COLOR_YELLOW);
    display.drawFastHLine(bx - 3, by + 3, 6, COLOR_YELLOW);
    display.drawLine(bx + 1, by + 3, bx - 2, by + 6, COLOR_YELLOW);
    display.drawLine(bx, by + 3, bx - 3, by + 6, COLOR_YELLOW);

    // Indicate "CHG" instead of forced 100%
    display.setTextDatum(textdatum_t::middle_right);
    display.setTextColor(COLOR_GREEN, COLOR_SURFACE);
    display.drawString("CHG", x - 3, y + 5, &fonts::Font0);
  } else {
    uint16_t bodyColor = currentBattery.isLow ? COLOR_RED : (currentBattery.percentage <= 30 ? COLOR_YELLOW : COLOR_GREEN);

    // Battery Outline
    display.drawRoundRect(x, y, batW, batH, 2, bodyColor);
    display.fillRect(x + batW, y + 3, 2, 4, bodyColor);

    // Fill level proportional to percentage
    int fillW = ((batW - 4) * currentBattery.percentage) / 100;
    if (fillW < 1 && currentBattery.percentage > 0) fillW = 1;
    if (fillW > (batW - 4)) fillW = batW - 4;

    if (fillW > 0) {
      display.fillRect(x + 2, y + 2, fillW, batH - 4, bodyColor);
    }

    // Exact % text next to battery
    display.setTextDatum(textdatum_t::middle_right);
    display.setTextColor(bodyColor, COLOR_SURFACE);
    display.drawString(String(currentBattery.percentage) + "%", x - 3, y + 5, &fonts::Font0);
  }
}

// ── Common Top Bar Header (Portrait: 135x24) ──────────────────────────────────────────
void drawTopBar(const char* title, const char* statusPill, uint16_t pillColor) {
  display.fillRect(0, 0, 135, 24, COLOR_SURFACE);
  display.drawFastHLine(0, 24, 135, COLOR_BORDER);

  // App Title
  display.setTextDatum(textdatum_t::middle_left);
  display.setTextColor(COLOR_WHITE, COLOR_SURFACE);
  display.drawString(title, 4, 12, &fonts::Font0);

  // Status Pill
  if (statusPill != nullptr && strlen(statusPill) > 0) {
    display.fillRoundRect(58, 4, 28, 15, 3, pillColor);
    display.setTextDatum(textdatum_t::middle_center);
    display.setTextColor(COLOR_BG, pillColor);
    display.drawString(statusPill, 72, 11, &fonts::Font0);
  }

  // Mini Battery Gauge on the right
  drawMiniBattery(111, 7);
}

// ── Screen 1: BLE Unlinked (Portrait) ─────────────────────────────────────────────────
void drawUnlinkedScreen() {
  display.fillScreen(COLOR_BG);
  drawTopBar("RESPONDO", "PAIR", COLOR_ORANGE);

  // Center Pulse Radar Visual
  int cx = 67;
  int cy = 74;
  display.drawCircle(cx, cy, 32, COLOR_SURFACE);
  display.drawCircle(cx, cy, 24, COLOR_BORDER);
  display.drawCircle(cx, cy, 14, COLOR_ORANGE);
  display.fillCircle(cx, cy, 5, COLOR_ORANGE);

  // Bluetooth symbol
  display.drawFastVLine(cx, cy - 8, 16, COLOR_WHITE);
  display.drawLine(cx, cy - 4, cx + 4, cy, COLOR_WHITE);
  display.drawLine(cx + 4, cy, cx - 4, cy + 6, COLOR_WHITE);
  display.drawLine(cx, cy + 4, cx + 4, cy, COLOR_WHITE);
  display.drawLine(cx + 4, cy, cx - 4, cy - 6, COLOR_WHITE);

  // Instruction Texts
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_BG);
  display.drawString("AWAITING LINK", 67, 118, &fonts::Font2);

  display.setTextColor(COLOR_MUTED, COLOR_BG);
  display.drawString("Open ResponDo App", 67, 138, &fonts::Font0);

  // ── Clean Side-by-Side Witness & Victim Buttons at Bottom ─────────────────────────────
  // Left: Witness Button (Blue)
  display.fillRoundRect(6, 160, 58, 68, 6, COLOR_BLUE_BG);
  display.drawRoundRect(6, 160, 58, 68, 6, COLOR_BLUE);
  display.fillRoundRect(22, 164, 26, 2, 1, COLOR_BLUE);
  display.setTextColor(COLOR_WHITE, COLOR_BLUE_BG);
  display.drawString("Witness", 35, 195, &fonts::Font2);

  // Right: Victim Button (Red)
  display.fillRoundRect(71, 160, 58, 68, 6, COLOR_RED_BG);
  display.drawRoundRect(71, 160, 58, 68, 6, COLOR_RED);
  display.fillRoundRect(87, 164, 26, 2, 1, COLOR_RED);
  display.setTextColor(COLOR_WHITE, COLOR_RED_BG);
  display.drawString("Victim", 100, 195, &fonts::Font2);
}

// ── Screen 2: Not Connected Warning (Portrait) ────────────────────────────────────────
void drawNotConnectedWarning() {
  display.fillScreen(COLOR_RED);

  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_RED);
  display.drawString("PHONE NOT", 67, 40, &fonts::Font2);
  display.drawString("LINKED!", 67, 62, &fonts::Font4);

  display.setTextColor(COLOR_YELLOW, COLOR_RED);
  display.drawString("Connect Bluetooth First:", 67, 100, &fonts::Font0);

  display.fillRoundRect(10, 118, 115, 30, 4, COLOR_RED_BG);
  display.drawRoundRect(10, 118, 115, 30, 4, COLOR_WHITE);
  display.setTextColor(COLOR_WHITE, COLOR_RED_BG);
  display.drawString("\"Respondo Device\"", 67, 133, &fonts::Font0);

  display.setTextDatum(textdatum_t::bottom_center);
  display.setTextColor(COLOR_YELLOW, COLOR_RED);
  display.drawString("Requires Phone Link", 67, 222, &fonts::Font0);
}

// ── Screen 3: Standby Screen (Clean, Modern, Minimalist) ──────────────────────────────
void drawIdleScreen() {
  display.fillScreen(COLOR_BG);
  drawTopBar("RESPONDO", "SYNC", COLOR_GREEN);

  // ── UPPER SECTION: Stylized Circular 3D SOS Button ─────────────────────────────────
  int cx = 67;
  int cy = 76;

  // Outer glowing ring
  display.drawCircle(cx, cy, 37, COLOR_RED_DARK);
  display.drawCircle(cx, cy, 36, COLOR_RED_DARK);

  // Mid pulse ring
  display.drawCircle(cx, cy, 32, display.color565(180, 20, 20));

  // Solid Center Red Button
  display.fillCircle(cx, cy, 27, COLOR_RED);

  // 3D tactile highlight arc
  display.drawArc(cx, cy, 26, 24, 210, 330, display.color565(255, 130, 130));

  // Bold "SOS" text
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_RED);
  display.drawString("SOS", cx, cy, &fonts::Font4);

  // Status badge under circle
  display.fillRoundRect(22, 118, 91, 16, 4, COLOR_SURFACE);
  display.drawRoundRect(22, 118, 91, 16, 4, COLOR_BORDER);
  display.fillCircle(30, 126, 3, COLOR_GREEN);
  display.setTextDatum(textdatum_t::middle_left);
  display.setTextColor(COLOR_GREEN, COLOR_SURFACE);
  display.drawString("SYNC ACTIVE", 37, 126, &fonts::Font0);

  // ── BOTTOM SECTION: Clean & Modern Witness & Victim Buttons ──────────────────────────

  // LEFT BUTTON: WITNESS (Blue)
  display.fillRoundRect(6, 156, 58, 72, 6, COLOR_BLUE_BG);
  display.drawRoundRect(6, 156, 58, 72, 6, COLOR_BLUE);
  display.drawRoundRect(7, 157, 56, 70, 5, display.color565(20, 70, 180));
  display.fillRoundRect(22, 161, 26, 2, 1, COLOR_BLUE);

  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_BLUE_BG);
  display.drawString("Witness", 35, 193, &fonts::Font2);

  // RIGHT BUTTON: VICTIM (Red)
  display.fillRoundRect(71, 156, 58, 72, 6, COLOR_RED_BG);
  display.drawRoundRect(71, 156, 58, 72, 6, COLOR_RED);
  display.drawRoundRect(72, 157, 56, 70, 5, display.color565(180, 20, 20));
  display.fillRoundRect(87, 161, 26, 2, 1, COLOR_RED);

  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_RED_BG);
  display.drawString("Victim", 100, 193, &fonts::Font2);
}

// ── Screen 4: Victim SOS Active (Portrait) ────────────────────────────────────────────
void drawVictimScreen() {
  display.fillScreen(COLOR_BG);

  display.fillRect(0, 0, 135, 24, COLOR_RED);
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_RED);
  display.drawString("! SOS ACTIVE !", 67, 12, &fonts::Font2);

  display.fillRoundRect(5, 28, 125, 206, 6, COLOR_RED_BG);
  display.drawRoundRect(5, 28, 125, 206, 6, COLOR_RED);

  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_RED_BG);
  display.drawString("VICTIM", 67, 50, &fonts::Font4);
  display.drawString("CONFIRMED", 67, 78, &fonts::Font2);

  display.setTextColor(COLOR_YELLOW, COLOR_RED_BG);
  display.drawString("Medical Profile", 67, 112, &fonts::Font2);
  display.drawString("Transmitted", 67, 130, &fonts::Font0);

  display.setTextColor(COLOR_WHITE, COLOR_RED_BG);
  display.drawString("BFP Notified", 67, 160, &fonts::Font2);
  display.drawString("Stay calm & safe", 67, 178, &fonts::Font0);

  display.setTextDatum(textdatum_t::bottom_center);
  display.setTextColor(COLOR_MUTED, COLOR_RED_BG);
  display.drawString("Hold btn to reset", 67, 224, &fonts::Font0);
}

// ── Screen 5: Bystander SOS Active (Portrait) ─────────────────────────────────────────
void drawBystanderScreen() {
  display.fillScreen(COLOR_BG);

  display.fillRect(0, 0, 135, 24, COLOR_BLUE);
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_BLUE);
  display.drawString("! REPORT ACTIVE !", 67, 12, &fonts::Font2);

  display.fillRoundRect(5, 28, 125, 206, 6, COLOR_BLUE_BG);
  display.drawRoundRect(5, 28, 125, 206, 6, COLOR_BLUE);

  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_BLUE_BG);
  display.drawString("WITNESS", 67, 50, &fonts::Font4);
  display.drawString("REPORT SENT", 67, 78, &fonts::Font2);

  display.setTextColor(COLOR_YELLOW, COLOR_BLUE_BG);
  display.drawString("Scene Alert", 67, 112, &fonts::Font2);
  display.drawString("Transmitted", 67, 130, &fonts::Font0);

  display.setTextColor(COLOR_WHITE, COLOR_BLUE_BG);
  display.drawString("BFP Notified", 67, 160, &fonts::Font2);
  display.drawString("Incident logged", 67, 178, &fonts::Font0);

  display.setTextDatum(textdatum_t::bottom_center);
  display.setTextColor(COLOR_MUTED, COLOR_BLUE_BG);
  display.drawString("Hold btn to reset", 67, 224, &fonts::Font0);
}

// ── Screen 6: BFP Responders Dispatched (Portrait) ────────────────────────────────────
void drawDeployedScreen() {
  display.fillScreen(COLOR_BG);

  display.fillRect(0, 0, 135, 24, COLOR_GREEN);
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_BG, COLOR_GREEN);
  display.drawString("BFP DISPATCHED", 67, 12, &fonts::Font2);

  display.fillRoundRect(5, 28, 125, 206, 6, COLOR_GREEN_BG);
  display.drawRoundRect(5, 28, 125, 206, 6, COLOR_GREEN);

  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_GREEN_BG);
  display.drawString("HELP IS ON", 67, 50, &fonts::Font2);
  display.drawString("THE WAY!", 67, 74, &fonts::Font4);

  display.setTextColor(COLOR_YELLOW, COLOR_GREEN_BG);
  display.drawString("Fire Responders", 67, 112, &fonts::Font2);
  display.drawString("En Route", 67, 130, &fonts::Font0);

  display.setTextColor(COLOR_WHITE, COLOR_GREEN_BG);
  display.drawString("Live Tracking", 67, 160, &fonts::Font2);
  display.drawString("Stay in safe location", 67, 178, &fonts::Font0);

  display.setTextDatum(textdatum_t::bottom_center);
  display.setTextColor(COLOR_WHITE, COLOR_GREEN_BG);
  display.drawString("Tap btn to dismiss", 67, 224, &fonts::Font0);
}

// ── Screen 7: Critical Low Battery Shutdown Screen with Graphics ─────────────────────
void drawLowBatteryScreen() {
  display.fillScreen(COLOR_BG);

  display.fillRect(0, 0, 135, 24, COLOR_RED);
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_RED);
  display.drawString("! LOW BATTERY !", 67, 12, &fonts::Font2);

  int bx = 40;
  int by = 50;
  display.drawRoundRect(bx, by, 50, 26, 3, COLOR_RED);
  display.drawRoundRect(bx + 1, by + 1, 48, 24, 2, COLOR_RED);
  display.fillRect(bx + 50, by + 7, 3, 12, COLOR_RED);

  display.fillRect(bx + 4, by + 4, 7, 18, COLOR_RED);
  display.setTextColor(COLOR_YELLOW, COLOR_BG);
  display.drawString("!", bx + 28, by + 13, &fonts::Font2);

  display.drawCircle(67, 104, 14, COLOR_SURFACE);
  display.drawCircle(67, 104, 13, COLOR_BORDER);
  display.setTextColor(COLOR_YELLOW, COLOR_SURFACE);
  display.drawString("+", 67, 104, &fonts::Font2);

  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_BG);
  display.drawString("Device low,", 67, 138, &fonts::Font2);
  display.setTextColor(COLOR_YELLOW, COLOR_BG);
  display.drawString("please charge", 67, 158, &fonts::Font2);

  display.setTextColor(COLOR_MUTED, COLOR_BG);
  display.drawString("Shutting down...", 67, 190, &fonts::Font0);

  display.setTextDatum(textdatum_t::bottom_center);
  display.setTextColor(COLOR_WHITE, COLOR_BG);
  display.drawString("Connect USB to charge", 67, 224, &fonts::Font0);
}

// ── Screen 8: Reset Button Shutdown Prompt Screen ─────────────────────────────────────
void drawRstShutdownPrompt(int progress, int secondsLeft) {
  display.fillScreen(COLOR_BG);

  // Orange Header
  display.fillRect(0, 0, 135, 24, COLOR_ORANGE);
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_BG, COLOR_ORANGE);
  display.drawString("POWER OFF?", 67, 12, &fonts::Font2);

  // Center Power Icon
  int cx = 67;
  int cy = 68;
  display.drawCircle(cx, cy, 26, COLOR_ORANGE);
  display.drawCircle(cx, cy, 25, COLOR_ORANGE);

  // Power symbol
  display.drawArc(cx, cy, 15, 13, 45, 315, COLOR_WHITE);
  display.drawFastVLine(cx, cy - 16, 12, COLOR_WHITE);
  display.drawFastVLine(cx + 1, cy - 16, 12, COLOR_WHITE);

  // Clean, modern instruction texts
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_BG);
  display.drawString("Press RST again", 67, 108, &fonts::Font2);
  display.setTextColor(COLOR_YELLOW, COLOR_BG);
  display.drawString("to shut down", 67, 126, &fonts::Font2);

  display.setTextColor(COLOR_MUTED, COLOR_BG);
  display.drawString("or hold Victim", 67, 148, &fonts::Font0);
  display.drawString("Witness cancels", 67, 162, &fonts::Font0);

  // Animated Progress Countdown Bar
  display.drawRoundRect(15, 184, 105, 10, 3, COLOR_BORDER);
  int barW = (progress * 101) / 100;
  if (barW > 0) {
    display.fillRoundRect(17, 186, barW, 6, 2, COLOR_ORANGE);
  }

  display.setTextColor(COLOR_YELLOW, COLOR_BG);
  display.drawString(String(secondsLeft) + "s remaining", 67, 210, &fonts::Font0);
}

// ── Render Current Screen ─────────────────────────────────────────────────────────────
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
    case STATE_LOW_BATTERY:
      drawLowBatteryScreen();
      break;
    default:
      break;
  }
}

// ── Power Off / Deep Sleep Execution ──────────────────────────────────────────────────
void powerOffDevice() {
  Serial.println("[POWER] Powering off device into ultra-low-power Deep Sleep...");

  // Persist state in NVS so next RST press powers it ON
  Preferences prefs;
  prefs.begin("pwr", false);
  prefs.putBool("is_off", true);
  prefs.putBool("running", false);
  prefs.putUChar("stage", 0);
  prefs.end();

  display.fillScreen(COLOR_BG);
  display.fillRect(0, 0, 135, 24, COLOR_RED);
  display.setTextDatum(textdatum_t::middle_center);
  display.setTextColor(COLOR_WHITE, COLOR_RED);
  display.drawString("SHUTTING DOWN", 67, 12, &fonts::Font2);

  int cx = 67;
  int cy = 76;
  display.drawCircle(cx, cy, 28, COLOR_MUTED);
  display.drawArc(cx, cy, 16, 14, 45, 315, COLOR_MUTED);
  display.drawFastVLine(cx, cy - 16, 12, COLOR_MUTED);

  display.setTextColor(COLOR_WHITE, COLOR_BG);
  display.drawString("Device Off", 67, 126, &fonts::Font2);
  display.setTextColor(COLOR_YELLOW, COLOR_BG);
  display.drawString("Press RST 1x to turn on", 67, 150, &fonts::Font0);
  display.setTextColor(COLOR_MUTED, COLOR_BG);
  display.drawString("Battery protected", 67, 175, &fonts::Font0);
  delay(1200);

  // Turn off display completely to save power
  display.setBrightness(0);
  display.sleep();

  // Configure Wakeup: Single click on BOOT button (GPIO 0) or RIGHT button (GPIO 35) or RST powers it back on!
  esp_sleep_enable_ext0_wakeup(GPIO_NUM_0, 0); // Wake on GPIO 0 LOW (Left Button)
  esp_sleep_enable_ext1_wakeup(1ULL << GPIO_NUM_35, ESP_EXT1_WAKEUP_ALL_LOW); // Wake on GPIO 35 LOW (Right Button)

  Serial.println("[POWER] Deep Sleep active. System OFF.");
  Serial.flush();
  esp_deep_sleep_start();
}

// ── Setup ─────────────────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  delay(80);

  // Initialize Hardware Buttons matching physical portrait placement
  pinMode(BUTTON_RIGHT, INPUT);          // Physical Right Button (GPIO 35, VICTIM)
  pinMode(BUTTON_LEFT,  INPUT_PULLUP);   // Physical Left Button (GPIO 0, BOOT, WITNESS)

  // Initialize Battery ADC power gate and pin
  pinMode(PIN_ADC_EN, OUTPUT);
  digitalWrite(PIN_ADC_EN, HIGH);
  analogReadResolution(12);

  // Initialize LilyGO T-Display in PORTRAIT (Rotation 0 = 135x240, Brightness 220)
  display.begin(0, 220);
  display.fillScreen(COLOR_BG);

  // Check initial battery
  currentBattery = readBattery();

  // Check power state and reset status in NVS
  esp_reset_reason_t reason = esp_reset_reason();
  Serial.printf("[SYSTEM] Boot reset reason: %d\n", reason);

  Preferences prefs;
  prefs.begin("pwr", false);
  bool wasOff = prefs.getBool("is_off", false);
  bool wasRunning = prefs.getBool("running", false);
  uint8_t rstStage = prefs.getUChar("stage", 0);

  if (wasOff) {
    // ── CASE 1: Device was in Deep Sleep (Turned Off) ──
    // Pressing RST (or front button) turns the device ON!
    Serial.println("[POWER] Waking up from OFF state! Booting up...");
    prefs.putBool("is_off", false);
    prefs.putBool("running", true);
    prefs.putUChar("stage", 0);
    prefs.end();

    // Powering On Splash Screen
    display.fillRect(0, 0, 135, 3, COLOR_GREEN);
    display.setTextDatum(textdatum_t::middle_center);
    display.setTextColor(COLOR_WHITE, COLOR_BG);
    display.drawString("RESPONDO", 67, 95, &fonts::Font2);
    display.setTextColor(COLOR_GREEN, COLOR_BG);
    display.drawString("DEVICE ON", 67, 122, &fonts::Font2);
    display.setTextColor(COLOR_MUTED, COLOR_BG);
    display.drawString("Ready for Emergency", 67, 148, &fonts::Font0);
    delay(900);
  } else if (wasRunning) {
    // ── CASE 2: Reset button pressed while device was already running ──
    if (rstStage == 1) {
      // 2nd RST press within window! Confirm Shutdown!
      Serial.println("[POWER] 2nd RST press detected! Executing shutdown now...");
      prefs.putUChar("stage", 0);
      prefs.putBool("running", false);
      prefs.putBool("is_off", true);
      prefs.end();
      powerOffDevice();
      return;
    } else {
      // 1st RST press! Display shutdown prompt with 4s countdown
      Serial.println("[POWER] 1st RST press detected. Prompting for shutdown...");
      prefs.putUChar("stage", 1);
      prefs.end();

      unsigned long promptStart = millis();
      bool cancelled = false;
      unsigned long rightHoldStart = 0;

      while (millis() - promptStart < 4000) {
        // Option to hold Victim (RIGHT) button to confirm shutdown
        if (digitalRead(BUTTON_RIGHT) == LOW) {
          if (rightHoldStart == 0) rightHoldStart = millis();
          if (millis() - rightHoldStart > 1200) {
            Serial.println("[POWER] Victim button held: Shutting down!");
            prefs.begin("pwr", false);
            prefs.putUChar("stage", 0);
            prefs.putBool("running", false);
            prefs.putBool("is_off", true);
            prefs.end();
            powerOffDevice();
            return;
          }
        } else {
          rightHoldStart = 0;
        }

        // If user presses Witness (LEFT) button, cancel immediately!
        if (digitalRead(BUTTON_LEFT) == LOW) {
          Serial.println("[POWER] Shutdown prompt cancelled by Witness button.");
          cancelled = true;
          break;
        }

        int remainingMs = 4000 - (millis() - promptStart);
        int progress = (remainingMs * 100) / 4000;
        int secsLeft = (remainingMs + 999) / 1000;
        drawRstShutdownPrompt(progress, secsLeft);
        delay(50);
      }

      // Reset stage back to 0 so next press starts fresh
      prefs.begin("pwr", false);
      prefs.putUChar("stage", 0);
      prefs.putBool("running", true);
      prefs.end();

      if (cancelled) {
        // Wait until button released to avoid accidental triggers
        while (digitalRead(BUTTON_LEFT) == LOW) {
          delay(10);
        }
      }
    }
  } else {
    // ── CASE 3: Initial power-up or cold boot ──
    prefs.putUChar("stage", 0);
    prefs.putBool("running", true);
    prefs.putBool("is_off", false);
    prefs.end();

    // Portrait Splash Screen
    display.fillRect(0, 0, 135, 3, COLOR_RED);
    display.setTextDatum(textdatum_t::middle_center);
    display.setTextColor(COLOR_WHITE, COLOR_BG);
    display.drawString("RESPONDO", 67, 105, &fonts::Font2);
    display.setTextColor(COLOR_RED, COLOR_BG);
    display.drawString("BFP Emergency", 67, 128, &fonts::Font0);
    display.drawString("Wearable", 67, 142, &fonts::Font0);
    delay(800);
  }

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

  // 5. Configure High-Speed Always-Active Advertisement
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

  pAdvertising->setMinInterval(0x20); // 20ms
  pAdvertising->setMaxInterval(0x40); // 40ms
  pAdvertising->setMinPreferred(0x06); // 7.5ms BLE connection interval
  pAdvertising->setMinPreferred(0x12); // 22.5ms BLE connection interval
  BLEDevice::startAdvertising();

  currentState = STATE_UNLINKED;
  drawScreen();

  Serial.println("[BLE] Ready & Advertising in Portrait mode.");
}

// ── Main Loop ─────────────────────────────────────────────────────────────────────────
void loop() {
  unsigned long now = millis();

  // ── Periodic Battery Reading (Every 3 seconds) ───────────────────────────────────────
  if (now - lastBatteryCheckTime > 3000) {
    lastBatteryCheckTime = now;
    currentBattery = readBattery();

    // Check for critical low battery (<= 3.25V and not charging)
    if (currentBattery.voltage > 0.5f && currentBattery.voltage <= 3.25f && !currentBattery.isCharging) {
      lowBatCounter++;
      if (lowBatCounter >= 3) {
        Serial.println("[BATTERY] CRITICAL LOW! Displaying charge warning and shutting down...");
        currentState = STATE_LOW_BATTERY;
        drawScreen();
        delay(3500);
        powerOffDevice();
      }
    } else {
      lowBatCounter = 0;
    }

    // Refresh top bar battery status on normal screens
    if (currentState == STATE_IDLE || currentState == STATE_UNLINKED) {
      drawTopBar("RESPONDO", (currentState == STATE_IDLE ? "SYNC" : "PAIR"), (currentState == STATE_IDLE ? COLOR_GREEN : COLOR_ORANGE));
    }
  }

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

  // ── Physical Buttons Edge Detection (Accurate Left & Right Buttons) ───────────────────
  int readRight = digitalRead(BUTTON_RIGHT);
  int readLeft  = digitalRead(BUTTON_LEFT);

  // 1. If currently on DEPLOYED screen, tapping either button dismisses back to Standby
  if (currentState == STATE_DEPLOYED) {
    if ((readRight == LOW && lastRightState == HIGH) || (readLeft == LOW && lastLeftState == HIGH)) {
      Serial.println("[BTN] Acknowledged DEPLOYED alert, returning to Standby.");
      currentState = STATE_IDLE;
      needScreenRedraw = true;
      lastRightState = readRight;
      lastLeftState  = readLeft;
      delay(50);
      return;
    }
  }

  // 2. If currently on VICTIM or BYSTANDER screen, holding either button for 1.5s resets back to Standby
  if (currentState == STATE_VICTIM || currentState == STATE_BYSTANDER) {
    if ((readRight == LOW && (now - rightPressStartTime > 1500)) || 
        (readLeft  == LOW && (now - leftPressStartTime  > 1500))) {
      Serial.println("[BTN] Button held: Dismissing active SOS, returning to Standby.");
      currentState = STATE_IDLE;
      needScreenRedraw = true;
      lastRightState = HIGH;
      lastLeftState  = HIGH;
      delay(200);
      return;
    }
  }

  // 3. LEFT BUTTON (GPIO 0 / BOOT) — Pure WITNESS / Bystander SOS Trigger
  if (readLeft == LOW && lastLeftState == HIGH) {
    leftPressStartTime = now;
  } else if (readLeft == HIGH && lastLeftState == LOW) {
    unsigned long pressDuration = now - leftPressStartTime;
    if (pressDuration >= 40 && pressDuration < 1500) { // 40ms debounce
      Serial.println("\n[LEFT BTN] >>> WITNESS SOS PRESSED! <<<");
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
  }

  // 4. RIGHT BUTTON (GPIO 35) — Pure VICTIM SOS Trigger
  if (readRight == LOW && lastRightState == HIGH) {
    rightPressStartTime = now;
  } else if (readRight == HIGH && lastRightState == LOW) {
    unsigned long pressDuration = now - rightPressStartTime;
    if (pressDuration >= 40 && pressDuration < 1500) { // 40ms debounce
      Serial.println("\n[RIGHT BTN] >>> VICTIM SOS PRESSED! <<<");
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
  }

  lastRightState = readRight;
  lastLeftState  = readLeft;

  // ── Screen Rendering ─────────────────────────────────────────────────────────────────
  if (needScreenRedraw) {
    needScreenRedraw = false;
    drawScreen();
  }

  delay(10);
}
