# RESPONDO BFP — Complete System Data Dictionary

**System Name:** RESPONDO BFP Emergency Response & Dispatch Management System  
**Version:** 2.1 (Full System Specification)  
**Target Architecture:** Multi-Platform IoT & Cloud System
* **Edge Hardware:** LilyGO TTGO T-Display ESP32 Wearable (C++ / FreeRTOS / BLE GATT / NVS)
* **Mobile Client:** Android Native Application (Java / Android SDK / BLE Manager / FusedLocation / SharedPreferences)
* **Cloud Infrastructure:** Google Cloud Firestore (NoSQL), Cloud Functions (Node.js), Firebase Auth, Firebase Cloud Messaging (FCM)
* **Dispatch Console & Web SOS:** Web Application (HTML5 / ES6 / Leaflet GIS / Session & Local Storage)

---

## 1. Cloud Firestore Database Collections

### 1.1 Collection: `sos_alerts`
The central operational collection storing active and historical emergency dispatches. Every document represents an emergency incident report created by citizens or wearable triggers and monitored by the BFP dispatch console.

* **Path:** `/sos_alerts/{alertId}`
* **Document ID:** Auto-generated Firestore ID (alphanumeric, 20 characters).
* **Security & Constraints:** Max 25 fields per document. Strict immutable fields (`userId`, `latitude`, `longitude`, `timestamp`). Admin-only write access to `status`, `deployedAt`, `resolvedAt`, and `resolvedReason`.

| Field Name | Data Type | Requirement | Constraints & Allowed Values | Description & System Usage |
| :--- | :--- | :--- | :--- | :--- |
| `userId` | `String` | **Required** | Non-empty; max 128 chars. **Immutable**. | Unique identifier of reporting client (Firebase Auth UID, device installation ID, or anonymous citizen ID). |
| `latitude` | `Number` | **Required** | Floating point between `-90.0` and `90.0`. **Immutable**. | Geographic latitude coordinate of the incident location. |
| `longitude` | `Number` | **Required** | Floating point between `-180.0` and `180.0`. **Immutable**. | Geographic longitude coordinate of the incident location. |
| `status` | `String` | **Required** | `active`, `deployed`, `resolved`, `flagged_spam`. Initial create must be `active`. | Operational status of the dispatch life-cycle. Transitions controlled by administrators. |
| `timestamp` | `Timestamp` | **Required** | Firestore Server Timestamp (`request.time`). **Immutable**. | Exact timestamp when the emergency alert was lodged into the database. |
| `deviceToken` | `String` | Optional | Max 512 chars. | Firebase Cloud Messaging (FCM) registration token of reporting device for return push sync. |
| `source` | `String` | Optional | `android_app`, `esp32_wearable`, `web_sos`, `voice_assistant`. | Hardware or client origin of the alert. |
| `incidentNotes` | `String` | Optional | Max 2,000 chars. | Voice transcription, caller notes, or details dictated via AI speech-to-text. |
| `description` | `String` | Optional | Max 2,000 chars. | Descriptive summary of the emergency event. |
| `accidentType` | `String` | Optional | `Fire`, `Medical`, `Vehicle`, `Hazard`, `Other`. | Classification of the primary hazard or emergency type. |
| `reporterRole` | `String` | Optional | `victim`, `bystander`. | Indicates whether reporter is directly involved or an eyewitness. |
| `reporterName` | `String` | Optional | Max 150 chars. | Full name of reporting individual. |
| `reporterPhone` | `String` | Optional | Max 30 chars. | Contact phone number of reporter for dispatcher call-back. |
| `reporterProfile` | `Map` | Optional | Key-value mapping. | Embedded snapshot of victim's personal/medical identity at the moment of trigger (see Section 1.1.1). |
| `countOfVictims` | `Number` | Optional | Integer `>= 0`. | Total number of individuals injured or in need of rescue. |
| `patients` | `Array<Map>` | Optional | Max 50 patient entries. | Detailed field casualty/patient records updated by responders or citizen (see Section 1.1.2). |
| `modeOfInjury` | `String` | Optional | Max 200 chars (e.g. *Collision, Fall, Burn, Explosion*). | Trauma mechanism or cause of bodily harm. |
| `natureOfInjury` | `String` | Optional | Max 200 chars (e.g. *Fracture, Asphyxiation, Laceration*). | Medical pathology or bodily damage observed. |
| `reportSubmitted` | `Boolean` | Optional | `true` or `false`. | Flag indicating whether the supplementary accident report sheet has been submitted. |
| `lastConditionUpdate` | `Timestamp` | Optional | Firestore Timestamp. | Server timestamp of the latest field condition update. |
| `deployedAt` | `Timestamp` | Optional | Firestore Timestamp. | Time when BFP dispatch deployed emergency teams to the scene. |
| `resolvedAt` | `Timestamp` | Optional | Firestore Timestamp. | Time when emergency incident was officially marked resolved. |
| `resolvedReason` | `String` | Optional | Max 500 chars. | Summary explanation provided by responder upon resolution. |
| `flaggedAt` | `Timestamp` | Optional | Firestore Timestamp. | Time when Cloud Functions auto-flagged the alert as spam. |
| `spamReason` | `String` | Optional | Max 200 chars (e.g. *Rate limit exceeded*). | Abuse mitigation rationale recorded by backend. |

#### 1.1.1 Embedded Map: `sos_alerts.reporterProfile`

| Key | Data Type | Format / Example | Description |
| :--- | :--- | :--- | :--- |
| `fullName` | `String` | `"Juan Dela Cruz"` | Legal name of account holder. |
| `age` | `String` | `"28"` | Age in years. |
| `birthdate` | `String` | `"YYYY-MM-DD"` | Date of birth. |
| `address` | `String` | `"Brgy. Central, Quezon City"` | Home residential address. |
| `phone` | `String` | `"+639171234567"` | Primary mobile phone number. |
| `bloodType` | `String` | `"O+"`, `"A+"`, `"B-"`, `"AB+"` | ABO/Rh blood classification for emergency transfusion. |
| `allergies` | `String` | `"Penicillin, Peanuts"` | Drug and food allergies critical for triage. |
| `medicalConditions` | `String` | `"Asthma, Hypertension"` | Pre-existing chronic diseases or physical conditions. |
| `emergencyContactName` | `String` | `"Maria Dela Cruz"` | Name of next-of-kin or primary emergency contact. |
| `emergencyContact` | `String` | `"+639189876543"` | Phone number of designated emergency contact. |

#### 1.1.2 Embedded Array of Objects: `sos_alerts.patients[]`

| Key | Data Type | Allowed Values / Format | Description |
| :--- | :--- | :--- | :--- |
| `name` | `String` | Plain text | Full name or placeholder identification. |
| `age` | `String` / `Number`| Integer or string | Age of victim. |
| `birthdate` | `String` | `"YYYY-MM-DD"` | Date of birth. |
| `address` | `String` | Plain text | Address or residential location. |
| `emergencyContact` | `String` | Name & Phone string | Contact information for the victim's guardian. |
| `allergies` | `String` | Plain text | Known pharmacological or environmental allergies. |
| `condition` | `String` | Plain text | Current physical condition and vital status. |
| `levelOfConsciousness` | `String` | `Alert`, `Voice`, `Pain`, `Unresponsive` (AVPU scale) | Neurological triage consciousness assessment. |
| `seizure` | `Boolean` | `true`, `false` | Indicator whether victim has exhibited convulsive/seizure episodes. |

---

### 1.2 Collection: `user_profiles`
Stores citizen registration data, pre-filled medical profiles, and legal privacy disclosures.

* **Path:** `/user_profiles/{uid}`
* **Document ID:** Firebase Auth User UID (`request.auth.uid`).
* **Security & Constraints:** Owner read/write only. Maximum 20 fields per document.

| Field Name | Data Type | Requirement | Constraints & Allowed Values | Description & System Usage |
| :--- | :--- | :--- | :--- | :--- |
| `fullName` | `String` | Optional | Max 150 chars | Legal full name of citizen. |
| `age` | `String` | Optional | Numeric string | Citizen age. |
| `birthdate` | `String` | Optional | `"YYYY-MM-DD"` | Date of birth. |
| `address` | `String` | Optional | Max 300 chars | Residential address. |
| `phone` | `String` | Optional | International or standard phone format | Primary contact number. |
| `emergencyContact` | `String` | Optional | Phone number format | Secondary phone number for emergency escalation. |
| `emergencyContactName` | `String`| Optional | Max 150 chars | Name of emergency contact person. |
| `allergies` | `String` | Optional | Max 500 chars | Known drug or substance allergies. |
| `bloodType` | `String` | Optional | `A+`, `A-`, `B+`, `B-`, `AB+`, `AB-`, `O+`, `O-`, `Unknown` | Medical blood type for emergency records. |
| `medicalConditions` | `String` | Optional | Max 1,000 chars | Medical history, comorbidities, or disability notes. |
| `privacyConsentAccepted` | `Boolean`| **Required** | `true` or `false` | Philippine Data Privacy Act (RA 10173) explicit consent flag. |
| `privacyConsentDate` | `String` | Optional | ISO-8601 or date format | Timestamp when data privacy disclosure was accepted. |
| `updatedAt` | `Timestamp` | Optional | Server Timestamp | Last profile modification timestamp. |
| `createdAt` | `Timestamp` | Optional | Server Timestamp | Account profile creation timestamp. |
| `deviceToken` | `String` | Optional | Max 512 chars | FCM token for direct device push notifications. |

---

### 1.3 Collection: `admin_users`
Security authorization table implementing Role-Based Access Control (RBAC) for official Bureau of Fire Protection officers and dispatch station operators.

* **Path:** `/admin_users/{uid}`
* **Document ID:** Firebase Auth User UID (`request.auth.uid`).

| Field Name | Data Type | Requirement | Constraints | Description |
| :--- | :--- | :--- | :--- | :--- |
| `authorized` | `Boolean` | **Required** | `true` | Grants BFP administrative dispatch privileges and triage controls. |
| `stationId` | `String` | Optional | References `bfp_stations` | Designated BFP headquarters or fire station jurisdiction. |
| `role` | `String` | Optional | `dispatcher`, `commander`, `paramedic` | Operational responsibilities of the personnel. |

---

### 1.4 Collection: `admin_tokens`
Stores active Firebase Cloud Messaging (FCM) device registration tokens for authenticated BFP dispatch consoles and officer devices.

* **Path:** `/admin_tokens/{uid}`
* **Document ID:** Firebase Auth User UID (`request.auth.uid`).

| Field Name | Data Type | Requirement | Constraints | Description |
| :--- | :--- | :--- | :--- | :--- |
| `fcmToken` | `String` | **Required** | Non-empty FCM registration token | Push endpoint token for dispatch audio and alerts. |
| `updatedAt` | `Timestamp` | **Required** | Server Timestamp | Most recent token refresh timestamp. |
| `createdAt` | `Timestamp` | Optional | Server Timestamp | Initial device token registration timestamp. |

---

### 1.5 Collection: `first_aid`
Emergency first responder instructions, CPR protocols, and multimedia survival guides available offline in the mobile app and online on the web console.

* **Path:** `/first_aid/{guideId}`

| Field Name | Data Type | Requirement | Constraints / Examples | Description |
| :--- | :--- | :--- | :--- | :--- |
| `title` | `String` | **Required** | E.g. `"Burns"`, `"CPR"`, `"Bleeding"` | Title of the emergency guide. |
| `category` | `String` | **Required** | E.g. `"Emergency Care"`, `"Life Support"` | High-level triage category. |
| `description` | `String` | **Required** | Narrative string | Summary of condition and symptoms. |
| `iconEmoji` | `String` | Optional | Emoji symbol (e.g. `"🔥"`, `"❤️"`, `"🫁"`) | Visual identification icon for UI cards. |
| `steps` | `Array<String>` | **Required** | Ordered list of instructions | Step-by-step procedural action list. |
| `photoUrls` | `Array<String>` | Optional | List of HTTPS image URLs | Diagrams or photographic demonstrations. |
| `videoUrl` | `String` | Optional | HTTPS URL | External tutorial video link. |
| `storageVideoUrl`| `String` | Optional | Firebase Storage `gs://` or HTTPS URL | Local cached instructional video for offline playback. |

---

### 1.6 Collection: `bfp_stations`
Geospatial location and dispatch information for Bureau of Fire Protection stations.

* **Path:** `/bfp_stations/{stationId}`

| Field Name | Data Type | Requirement | Constraints | Description |
| :--- | :--- | :--- | :--- | :--- |
| `name` | `String` | **Required** | E.g. `"BFP Central Fire Station"` | Official facility or district name. |
| `latitude` | `Number` | **Required** | `-90.0` to `90.0` | Geospatial latitude of the fire station depot. |
| `longitude` | `Number` | **Required** | `-180.0` to `180.0` | Geospatial longitude of the fire station depot. |
| `address` | `String` | Optional | Street / City address | Physical location description. |
| `contactNumber` | `String` | Optional | Hotline or telephone | Landline hotline for radio dispatch coordination. |

---

## 2. Web Application Client Storage

### 2.1 Web Dispatch Console (`public/index.html`) — Session Storage (`sessionStorage`)
Stores operational session parameters and brute-force authentication protection data.

| Key Name | Data Type | Scope / Lifecycle | Description & Value Rules |
| :--- | :--- | :--- | :--- |
| `bfp_failed_login_attempts` | `String (Integer)` | Tab Session | Counts consecutive failed login password attempts (e.g. `"3"`). When $\ge 5$, triggers timed lockout. |
| `bfp_lockout_until` | `String (Epoch ms)` | Tab Session | Epoch timestamp in milliseconds indicating when the brute-force login lock expires. |

### 2.2 Web Citizen SOS Portal (`public/sos.html`) — Local Storage (`localStorage`)
Stores offline citizen identity and persistent client identification for web-initiated emergency alerts.

| Key Name | Data Type | Scope / Lifecycle | Description & Value Rules |
| :--- | :--- | :--- | :--- |
| `bfp_user_id` | `String (UUID)` | Persistent across sessions | Anonymous client device identifier generated on first visit (`anon_<timestamp>_<random>`) to maintain user audit trail. |
| `pwa_user_profile` | `String (JSON)` | Persistent across sessions | Serialized JSON representation of user medical & contact data (`name`, `phone`, `emergencyContact`, `bloodType`, `allergies`). |

---

## 3. Android Native Mobile Client Storage (SharedPreferences)

### 3.1 Preference File: `bfp_user_profile.xml`
Managed by `UserProfileManager.java`. Stores citizen identity, emergency health data, and statutory consent.

| Key Name | Data Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `full_name` | `String` | `""` | Legal name of device owner. |
| `age` | `String` | `""` | Age in years. |
| `birthdate` | `String` | `""` | Date of birth in `YYYY-MM-DD`. |
| `address` | `String` | `""` | Residential address. |
| `phone` | `String` | `""` | Contact phone number. |
| `emergency_contact_name` | `String` | `""` | Name of primary next-of-kin. |
| `emergency_contact` | `String` | `""` | Phone number of emergency contact. |
| `allergies` | `String` | `""` | Comma-delimited list of medical/food allergies. |
| `blood_type` | `String` | `""` | Blood group (`O+`, `A+`, `B+`, etc.). |
| `medical_conditions` | `String` | `""` | Chronic conditions or physical disabilities. |
| `privacy_consent_accepted`| `Boolean`| `false`| Consent flag under RA 10173 (Data Privacy Act). |
| `privacy_consent_date` | `String` | `""` | Date string when consent was provided. |

### 3.2 Preference File: `bfp_prefs.xml`
Managed by `BLEManager.java`, `BLESOSService.java`, and `SosFragment.java`. Controls hardware wearable connectivity, shake detection, and active alert state.

| Key Name | Data Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `pref_ble_mac` | `String` | `""` | Bluetooth MAC address of bonded Respondo ESP32 wearable. |
| `pref_ble_name` | `String` | `"Respondo Device"` | Broadcast name of bonded wearable device. |
| `activeAlertId` | `String` | `""` | Firestore document ID of an ongoing in-progress SOS alert. |
| `userId` | `String` | `""` | Locally cached citizen UID. |
| `pref_shake_enabled` | `Boolean` | `true` | Enables or disables accelerometer shake-to-SOS trigger. |
| `pref_shake_sensitivity`| `Float` | `2.7f` | Acceleration threshold (G-force) required to trip shake trigger. |

### 3.3 Preference File: `admin_auth_security.xml`
Managed by `AdminLoginActivity.java`. Protects mobile admin login from brute-force dictionary attacks.

| Key Name | Data Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `failed_attempts` | `Integer` | `0` | Number of consecutive failed BFP admin password attempts. |
| `lockout_timestamp` | `Long` | `0L` | Millisecond epoch timestamp when temporary lockout ends. |

### 3.4 Preference File: `video_cache.xml`
Managed by `VideoCache.java`. Maps remote Firebase Storage instructional video URLs to on-device offline MP4 files.

| Key Name | Data Type | Value Format | Description |
| :--- | :--- | :--- | :--- |
| `<videoUrl>` | `String` | Absolute file path | Maps remote video URL string to local storage cache path (e.g. `/data/user/0/com.bfp.alert/cache/video_cpr.mp4`). |

---

## 4. Edge Device Hardware & Telemetry Data Dictionary (ESP32 Wearable)

### 4.1 Non-Volatile Storage (NVS Flash Preferences: `"pwr"`)
Configured in `esp32/respondo_wearable.ino`. Preserves operational power state across hardware reboots, watchdog timeouts, and deep-sleep cycles.

| Key Name | Type | Value Range | Description |
| :--- | :--- | :--- | :--- |
| `is_off` | `Boolean` | `true`, `false` | Indicates whether device entered intentional deep sleep shutdown. |
| `running` | `Boolean` | `true`, `false` | State flag used to differentiate between cold boot and soft reset. |
| `stage` | `UInt8` | `0`, `1` | Reset button power-cycle state machine (`0`: idle running, `1`: armed for 2nd press shutdown). |

### 4.2 Hardware Pinout & Peripheral Mapping
LilyGO TTGO T-Display (ESP32-D0WDQ6 + ST7789 135x240 IPS Display).

| Pin / Constant | Peripheral / Signal | Direction | Logic Level / Resolution | Function & Usage |
| :--- | :--- | :--- | :--- | :--- |
| `BUTTON_LEFT` (`GPIO 0`) | BOOT / Left Button | Input (Pull-up) | Active LOW | Triggers **Witness / Bystander** incident report; cancels shutdown prompt. |
| `BUTTON_RIGHT` (`GPIO 35`) | Right Push Button | Input (No pull-up)| Active LOW | Triggers **Victim Personal SOS**; confirms shutdown prompt. |
| `PIN_ADC_EN` (`GPIO 14`) | Power Gate Enable | Output | Active HIGH | Powers on battery voltage divider circuit before sampling. |
| `PIN_BAT_ADC` (`GPIO 34`) | Analog-to-Digital | Input | 12-bit ADC (`0`–`4095`) | Samples battery voltage ($V_{BAT} / 2$). Computes voltage & percentage. |
| `TFT_MOSI` (`GPIO 19`) | ST7789 SPI Data | Output | High Speed SPI | Display pixel data transfer. |
| `TFT_SCLK` (`GPIO 18`) | ST7789 SPI Clock | Output | High Speed SPI | Display clock sync. |
| `TFT_CS` (`GPIO 5`) | ST7789 Chip Select | Output | Active LOW | Display slave select. |
| `TFT_DC` (`GPIO 16`) | ST7789 Data/Command | Output | Digital | Data vs register command selector. |
| `TFT_BL` (`GPIO 4`) | Backlight LED | Output | PWM | Display backlight illumination control. |

### 4.3 Bluetooth Low Energy (BLE) Protocol Specification
- **Advertised Name:** `Respondo Device`
- **GATT Service UUID:** `12345678-1234-1234-1234-123456789abc`
- **GATT Characteristic UUID:** `87654321-4321-4321-4321-cba987654321` (Properties: Read, Write, Notify)

| Message Packet | Emitter | Consumer | Payload Structure / Encoding | Operational Effect |
| :--- | :--- | :--- | :--- | :--- |
| `TRIGGER:VICTIM` | Wearable | Android App | UTF-8 String | Dispatches high-priority Personal SOS attaching victim medical profile and GPS. |
| `TRIGGER:BYSTANDER` | Wearable | Android App | UTF-8 String | Dispatches Eyewitness incident report with GPS coordinates. |
| `BATTERY:<v>,<pct>` | Wearable | Android App | String (e.g. `BATTERY:4.12,96`) | Periodically updates battery telemetry indicator on phone UI. |
| `STATE:IDLE` | Android App | Wearable | UTF-8 String | Signals successful BLE pairing; displays circular SOS standby screen. |
| `STATUS:DEPLOYED` | Android App | Wearable | UTF-8 String | Alert status updated in cloud; wearable beeps and displays Amber "Responders En Route". |
| `STATUS:RESOLVED` | Android App | Wearable | UTF-8 String | Incident resolved; resets wearable to Standby SOS screen. |

---

## 5. Cloud Messaging (FCM) Notification Payload Specification

Dispatched by Cloud Functions (`functions/index.js`) upon document creation in `/sos_alerts/{alertId}`.

```json
{
  "notification": {
    "title": "EMERGENCY ALERT: [Fire | Medical | Vehicle | Hazard | Other]",
    "body": "Reported by [Reporter Name] ([Role]) at [Coordinates]"
  },
  "data": {
    "alertId": "string (Firestore Document ID)",
    "latitude": "string (Float representation)",
    "longitude": "string (Float representation)",
    "status": "active",
    "accidentType": "string",
    "reporterRole": "victim | bystander",
    "source": "android_app | esp32_wearable | web_sos",
    "click_action": "FLUTTER_NOTIFICATION_CLICK"
  }
}
```
