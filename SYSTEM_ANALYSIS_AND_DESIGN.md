# RESPONDO BFP — Systems Analysis and Design (SAD) Specification

**System Name:** RESPONDO BFP Emergency Response & Dispatch Management System  
**Document Type:** Systems Analysis and Design (SAD) Deliverable  
**Version:** 2.0  
**Scope:** End-to-End System (Hardware Wearable Device, Android Mobile App, Web Dispatch Dashboard, Firebase Cloud Infrastructure)

---

## Table of Contents
1. [Use Case Diagram & System Specifications](#1-use-case-diagram)
2. [Data Dictionary](#2-data-dictionary)
3. [Component Diagram](#3-component-diagram)
4. [Deployment Diagram](#4-deployment-diagram)
5. [Database Diagram (Entity-Relationship Diagram - ERD)](#5-database-diagram-erd)
6. [Data Flow Diagrams (DFD Level 0 & Level 1)](#6-data-flow-diagrams-dfd)

---

## 1. Use Case Diagram

The Use Case Diagram defines the operational interactions between external actors (Citizens, Bystanders, Wearable Hardware, BFP Dispatchers, and Automated Cloud Processes) and the Respondo BFP software platform.

```mermaid
flowchart LR
    %% Actors
    subgraph Actors [External Actors]
        Citizen["🧑 Citizen / Victim"]
        Bystander["👁️ Bystander"]
        Wearable["⌚ ESP32 Wearable Device"]
        Dispatcher["🚒 BFP Dispatcher / Admin"]
        CloudFunc["⚡ Cloud Functions (System)"]
    end

    %% System Boundary
    subgraph SystemBoundary ["RESPONDO BFP Emergency Response System"]
        UC1(["Trigger Personal SOS (Victim Mode)"])
        UC2(["Report Scene Emergency (Bystander Mode)"])
        UC3(["Trigger SOS via Hardware Button"])
        UC4(["Trigger SOS via Phone Shake"])
        UC5(["Manage Personal & Medical Profile"])
        UC6(["Accept Data Privacy Consent (RA 10173)"])
        UC7(["Access Offline First Aid & CPR Guides"])
        UC8(["Monitor Real-Time Emergency Map"])
        UC9(["Verify & Triage Incidents"])
        UC10(["Deploy Emergency Responders"])
        UC11(["Submit Incident & Patient Report"])
        UC12(["Mark Incident Resolved"])
        UC13(["Auto-Detect Spam & Rate Limit"])
        UC14(["Push Dispatch Notifications"])
    end

    %% Citizen Interactions
    Citizen --> UC1
    Citizen --> UC4
    Citizen --> UC5
    Citizen --> UC6
    Citizen --> UC7

    %% Bystander Interactions
    Bystander --> UC2
    Bystander --> UC7

    %% Wearable Interactions
    Wearable --> UC3
    UC3 -.->|<<includes>>| UC1
    UC3 -.->|<<includes>>| UC2

    %% Relationship inclusions/extensions
    UC1 -.->|<<includes>>| UC5
    UC4 -.->|<<includes>>| UC1
    UC10 -.->|<<includes>>| UC9
    UC12 -.->|<<extends>>| UC11

    %% Dispatcher Interactions
    Dispatcher --> UC8
    Dispatcher --> UC9
    Dispatcher --> UC10
    Dispatcher --> UC11
    Dispatcher --> UC12

    %% System Actor Interactions
    CloudFunc --> UC13
    CloudFunc --> UC14
    UC1 -.->|<<triggers>>| UC13
    UC1 -.->|<<triggers>>| UC14
```

### Use Case Specifications

| Use Case ID | Name | Primary Actor | Description | Preconditions | Postconditions |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **UC-01** | Trigger Personal SOS | Citizen / Victim | Dispatches instant panic signal with GPS coordinates and victim medical profile. | Location enabled on smartphone. | Active emergency document created in Firestore; dispatchers alerted. |
| **UC-02** | Report Scene Emergency | Bystander | Eyewitness reports fire, vehicular accident, or medical hazard without attaching personal medical history. | Network or cellular connection active. | Scene incident created with `reporterRole: "bystander"`. |
| **UC-03** | Trigger via Wearable | ESP32 Wearable | User clicks physical Right Button (Victim) or Left Button (Bystander) on wrist accessory. | Wearable paired over Bluetooth Low Energy (BLE). | Android service reads BLE packet and initiates mobile SOS pipeline. |
| **UC-04** | Trigger via Shake | Citizen | User vigorously shakes phone in emergency situations where screen access is restricted. | Accelerometer enabled; shake threshold reached ($> 2.7\text{G}$). | Emergency countdown triggered, followed by automated dispatch. |
| **UC-05** | Manage Medical Profile | Citizen | Registers blood type, allergies, pre-existing conditions, and emergency contacts. | User authenticated or in local profile view. | Saved locally and synchronized to `/user_profiles/{uid}` in cloud. |
| **UC-08** | Monitor Emergency Map | BFP Dispatcher | Live GIS dashboard displaying emergency alerts with dynamic visual indicators and sirens. | BFP admin credentials verified. | Dispatcher views real-time markers with hazard type and caller information. |
| **UC-10** | Deploy Responders | BFP Dispatcher | Assigns fire trucks or paramedics; draws route polyline from BFP station to incident GPS. | Alert is in `active` state. | Alert transitions to `deployed`; wearable screen switches to "Responders En Route". |
| **UC-12** | Resolve Emergency | BFP Dispatcher | Closes emergency after scene is neutralized or victims transported. | Alert in `deployed` state. | Alert status set to `resolved`; wearable returns to standby. |
| **UC-13** | Spam Abuse Limiting | Cloud Functions | Evaluates incoming frequency per device. Automatically flags duplicate alerts $> 3$ in 60 seconds. | Incident created in Firestore. | Alert marked `flagged_spam` if limit exceeded; prevents dispatcher alarm fatigue. |

---

## 2. Data Dictionary

The full, exhaustive field-level data dictionary covering all database entities, client storage, hardware registers, and communication payloads is maintained in [DATA_DICTIONARY.md](file:///c:/Users/Alvin%20Jan/project/sad/respondo-bfp-app/DATA_DICTIONARY.md). Below is the comprehensive structural summary across all system components.

### 2.1 Multi-Tier Data Storage Summary

```mermaid
flowchart TD
    subgraph Data Stores Across System
        FS[("Google Cloud Firestore\n• sos_alerts\n• user_profiles\n• admin_users\n• admin_tokens\n• first_aid\n• bfp_stations")]
        AND_PREF[("Android SharedPreferences\n• bfp_user_profile.xml\n• bfp_prefs.xml\n• admin_auth_security.xml\n• video_cache.xml")]
        WEB_STORE[("Web Client Storage\n• sessionStorage (bfp_lockout)\n• localStorage (pwa_user_profile, bfp_user_id)")]
        ESP_NVS[("ESP32 Wearable NVS Flash\n• Preferences namespace 'pwr'\n• is_off, running, stage")]
    end
```

### 2.2 Core Firestore Collections Specification

| Entity / Collection | Document ID | Key Fields | Cardinality / Relationships | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **`sos_alerts`** | Auto-ID (20 chars) | `userId`, `latitude`, `longitude`, `status`, `timestamp`, `accidentType`, `reporterRole`, `reporterProfile`, `patients`, `deployedAt`, `resolvedAt` | $1 : N$ with `patients`, $1 : 1$ with embedded `reporterProfile` | Primary emergency dispatch record. Enforces immutability on core telemetry. |
| **`user_profiles`** | User `uid` | `fullName`, `phone`, `bloodType`, `allergies`, `medicalConditions`, `emergencyContact`, `privacyConsentAccepted`, `updatedAt` | $1 : 1$ with Firebase Auth Account | Pre-filled medical triage identity and legal compliance record under RA 10173. |
| **`admin_users`** | Admin `uid` | `authorized` (`bool`), `role` (`string`), `stationId` (`string`) | $1 : 1$ with BFP Personnel Account | Role-Based Access Control (RBAC) whitelist for BFP dispatchers and officers. |
| **`admin_tokens`** | Admin `uid` | `fcmToken`, `updatedAt`, `createdAt` | $1 : 1$ with Admin Device | Registered Firebase Cloud Messaging push endpoints for dispatch sirens. |
| **`first_aid`** | Alphanumeric ID | `title`, `category`, `description`, `iconEmoji`, `steps` (array), `photoUrls` (array), `videoUrl`, `storageVideoUrl` | Standalone reference repository | Medical emergency protocols available offline on mobile and online on web. |
| **`bfp_stations`** | Station ID | `name`, `latitude`, `longitude`, `address`, `contactNumber` | $1 : N$ with Dispatched Alerts | Coordinates of fire stations used for routing and distance calculation. |

---

## 3. Component Diagram

The Component Diagram details the structural modules, dependencies, and interfaces connecting the Android Native Application, ESP32 Firmware, Web Dispatch Console, and Google Cloud Backend.

```mermaid
graph TB
    subgraph ESP32_Firmware ["⌚ Edge Hardware Component (LilyGO ESP32 Wearable)"]
        BTN_CTRL["[Button Controller]\nGPIO 0 & 35 Interrupts"]
        PWR_MGMT["[Power & Deep Sleep Manager]\nNVS Flash / RST Logic"]
        BAT_MON["[ADC Battery Monitor]\n12-bit Voltage Sampling"]
        GFX_UI["[ST7789 Display Engine]\nLovyanGFX Portrait GUI"]
        BLE_SERVER["[BLE GATT Server Component]\nService & Characteristic UUIDs"]
        
        BTN_CTRL --> BLE_SERVER
        PWR_MGMT --> GFX_UI
        BAT_MON --> BLE_SERVER
        BLE_SERVER --> GFX_UI
    end

    subgraph Android_App ["📱 Mobile Client Component (Android Native App)"]
        BLE_SVC["[BLESOSService & BLEManager]\nBluetooth LE Background Service"]
        SHAKE_DET["[ShakeGestureDetector]\nAccelerometer Sensor Engine"]
        LOC_SVC["[FusedLocationProviderClient]\nHigh-Accuracy GPS Service"]
        USER_MGR["[UserProfileManager]\nLocal & Cloud Sync Module"]
        FIRST_AID_REPO["[FirstAidRepository]\nCache & Firestore Listener"]
        VOICE_ASST["[VoiceAssistantDialog]\nClaude / Gemini Speech Processor"]
        FCM_CLIENT["[MyFirebaseMessagingService]\nPush Notification Receiver"]
        MAP_MARKER["[AlertMarkerHelper & DensityMap]\nIncident Visualizer"]
        
        BLE_SVC <-->|BLE GATT Stream| BLE_SERVER
        SHAKE_DET --> BLE_SVC
        LOC_SVC --> BLE_SVC
        USER_MGR --> BLE_SVC
    end

    subgraph Web_Console ["💻 Web Dispatch Console (public/index.html & sos.html)"]
        WEB_MAP["[Leaflet GIS Engine]\nOpenStreetMap & Marker Cluster"]
        AUDIO_CTRL["[Emergency Audio Synthesizer]\nSiren & Tone Player"]
        AUTH_GUARD["[Session Security Guard]\nBrute-Force & Lockout Manager"]
        DISPATCH_MGR["[Dispatch Action Controller]\nStatus Deploy / Resolve Handler"]
        PWA_SOS["[Web SOS Client (sos.html)]\nGeolocation & Quick Alert"]
        
        AUTH_GUARD --> DISPATCH_MGR
        DISPATCH_MGR --> WEB_MAP
        WEB_MAP --> AUDIO_CTRL
    end

    subgraph Firebase_Cloud ["☁️ Cloud Backend Infrastructure"]
        AUTH_SVC["[Firebase Authentication]\nEmail / Password & Anonymous Auth"]
        FIRESTORE["[Cloud Firestore NoSQL]\nReal-Time Database Collections"]
        CLOUD_FUNC["[Firebase Cloud Functions]\nonSosAlertCreated Trigger"]
        FCM_SVC["[Firebase Cloud Messaging Gateway]\nGlobal Push Broadcast"]
        STORAGE_SVC["[Firebase Storage]\nFirst Aid Videos & Incident Media"]
        
        BLE_SVC -->|HTTPS / gRPC| FIRESTORE
        PWA_SOS -->|HTTPS / gRPC| FIRESTORE
        DISPATCH_MGR <-->|Realtime Snapshot Listener| FIRESTORE
        AUTH_GUARD <--> AUTH_SVC
        USER_MGR <--> AUTH_SVC
        FIRESTORE -->|Document onCreate| CLOUD_FUNC
        CLOUD_FUNC -->|Send Alert Payload| FCM_SVC
        FCM_SVC -->|Push Alert| FCM_CLIENT
        FIRST_AID_REPO <--> STORAGE_SVC
    end
```

---

## 4. Deployment Diagram

The Deployment Diagram models the physical runtime nodes, execution environments, hardware specifications, and network protocols connecting edge devices, client terminals, and cloud infrastructure.

```mermaid
graph TB
    subgraph Node_ESP32 ["«device: Hardware Node»\nLilyGO TTGO T-Display ESP32"]
        subgraph Exec_ESP32 ["«execution environment»\nFreeRTOS / Arduino Core"]
            Artifact_Firmware["«artifact»\nrespondo_wearable.bin"]
            Artifact_NVS["«artifact»\nNVS Preferences Flash Partition"]
        end
        HW_Display["ST7789 135x240 IPS Screen"]
        HW_Buttons["Tactile Buttons (GPIO 0 & 35)"]
        HW_ADC["Battery Voltage Divider (GPIO 34)"]
    end

    subgraph Node_Android ["«device: Mobile Node»\nCitizen / Officer Android Smartphone"]
        subgraph Exec_Android ["«execution environment»\nAndroid OS 8.0+ (API 26+) / ART VM"]
            Artifact_APK["«artifact»\nrespondo-bfp.apk"]
            Artifact_Prefs["«artifact»\nSharedPreferences XML Files"]
            Artifact_Cache["«artifact»\nLocal Video Cache Directory"]
        end
        HW_BLE["Bluetooth 4.2 / 5.0 Radio"]
        HW_GPS["GPS / GLONASS Receiver"]
        HW_Accel["3-Axis Accelerometer Sensor"]
    end

    subgraph Node_PC ["«device: Desktop Workstation»\nBFP Dispatch Command Center"]
        subgraph Exec_Browser ["«execution environment»\nChromium / Modern Web Browser"]
            Artifact_Web["«artifact»\nindex.html / theme.css / runtime-config.js"]
            Artifact_Session["«artifact»\nDOM sessionStorage / localStorage"]
        end
        HW_Audio["Emergency Siren Sound Output"]
        HW_Screen["Dual-Screen GIS Monitor"]
    end

    subgraph Node_Cloud ["«cloud provider: Google Cloud Platform»\nFirebase Multi-Region Cloud Infrastructure"]
        subgraph Exec_Functions ["«execution environment»\nNode.js 18 / 20 Cloud Functions"]
            Artifact_Fn["«artifact»\nfunctions/index.js"]
        end
        subgraph Exec_Firestore ["«database service»\nGoogle Cloud Firestore"]
            Artifact_DB["«database»\nsos_alerts, user_profiles, admin_tokens, bfp_stations"]
        end
        subgraph Exec_FCM ["«messaging gateway»\nFirebase Cloud Messaging (FCM)"]
            Artifact_Push["«service»\nFCM Push Routing Service"]
        end
        subgraph Exec_Hosting ["«content delivery network»\nFirebase Hosting"]
            Artifact_Static["«files»\npublic/ Web Distribution"]
        end
    end

    %% Network Links & Protocols
    Node_ESP32 <-->|Bluetooth Low Energy 4.2/5.0\nService UUID: 12345678-...\nGATT Notify / Write| Node_Android
    Node_Android <-->|HTTPS / WSS / gRPC\nTLS 1.3 encrypted| Node_Cloud
    Node_PC <-->|HTTPS / WSS\nFirestore Realtime Protocol| Node_Cloud
    Exec_Functions -->|Admin SDK IPC| Exec_Firestore
    Exec_Functions -->|FCM Push API| Exec_FCM
    Exec_FCM -.->|Downstream Push Notification| Node_Android
    Exec_Hosting -.->|HTTP/2 Static Asset Delivery| Node_PC
```

---

## 5. Database Diagram (ERD)

The Entity-Relationship Diagram outlines all collections, embedded complex structures, primary keys, foreign keys, cardinality, and field attributes.

```mermaid
erDiagram
    ADMIN_USERS ||--o{ ADMIN_TOKENS : registers
    ADMIN_USERS ||--o{ SOS_ALERTS : deploys_and_resolves
    USER_PROFILES ||--o{ SOS_ALERTS : initiates
    SOS_ALERTS ||--|{ PATIENTS : contains
    SOS_ALERTS ||--o| REPORTER_PROFILE : embeds
    BFP_STATIONS ||--o{ SOS_ALERTS : routes_to
    FIRST_AID ||--o{ SOS_ALERTS : provides_guidance

    SOS_ALERTS {
        string alertId PK "Auto-generated Alphanumeric ID"
        string userId FK "Reporter Auth UID or Device ID"
        number latitude "Incident GPS Latitude (-90 to 90)"
        number longitude "Incident GPS Longitude (-180 to 180)"
        string status "active | deployed | resolved | flagged_spam"
        timestamp timestamp "Server Timestamp (Immutable)"
        string source "android_app | esp32_wearable | web_sos"
        string accidentType "Fire | Medical | Vehicle | Hazard | Other"
        string reporterRole "victim | bystander"
        string reporterName "Full name of reporter"
        string reporterPhone "Contact phone number"
        string incidentNotes "Transcription / caller notes"
        string description "Incident description"
        number countOfVictims "Total reported casualties"
        string modeOfInjury "Mechanism of injury"
        string natureOfInjury "Pathology / diagnosis"
        boolean reportSubmitted "Accident sheet completion flag"
        timestamp deployedAt "Unit dispatch timestamp"
        timestamp resolvedAt "Incident resolution timestamp"
        string resolvedReason "Resolution notes"
        timestamp flaggedAt "Spam flag timestamp"
        string spamReason "Abuse filter explanation"
    }

    REPORTER_PROFILE {
        string fullName "Legal citizen name"
        string age "Citizen age"
        string birthdate "Date of birth (YYYY-MM-DD)"
        string address "Home address"
        string phone "Phone number"
        string bloodType "ABO/Rh Blood Group"
        string allergies "Drug and food allergies"
        string medicalConditions "Chronic illnesses / comorbidities"
        string emergencyContact "Contact phone number"
        string emergencyContactName "Contact person name"
    }

    PATIENTS {
        string name "Patient / Casualty name"
        string age "Patient age"
        string birthdate "Birthdate"
        string address "Residential address"
        string emergencyContact "Guardian contact"
        string allergies "Known allergies"
        string condition "Physical condition & injuries"
        string levelOfConsciousness "AVPU (Alert, Voice, Pain, Unresponsive)"
        boolean seizure "Convulsive episode indicator"
    }

    USER_PROFILES {
        string uid PK "Firebase Auth UID"
        string fullName "Citizen full name"
        string age "Age"
        string birthdate "Birth date"
        string address "Address"
        string phone "Contact number"
        string emergencyContact "Emergency contact phone"
        string emergencyContactName "Emergency contact name"
        string bloodType "Blood type"
        string allergies "Allergies"
        string medicalConditions "Medical history"
        boolean privacyConsentAccepted "RA 10173 Consent Flag"
        string privacyConsentDate "Consent timestamp"
        string deviceToken "FCM Push Registration Token"
        timestamp updatedAt "Last update timestamp"
        timestamp createdAt "Creation timestamp"
    }

    ADMIN_USERS {
        string uid PK "Firebase Auth UID"
        boolean authorized "Admin access granted flag"
        string role "dispatcher | commander | paramedic"
        string stationId FK "References BFP_STATIONS"
    }

    ADMIN_TOKENS {
        string uid PK "Firebase Auth UID"
        string fcmToken "FCM Registration Token"
        timestamp updatedAt "Token update timestamp"
        timestamp createdAt "Token registration timestamp"
    }

    BFP_STATIONS {
        string stationId PK "Station Identifier"
        string name "BFP Station Name"
        number latitude "Depot GPS Latitude"
        number longitude "Depot GPS Longitude"
        string address "Physical station address"
        string contactNumber "Hotline phone number"
    }

    FIRST_AID {
        string guideId PK "Guide Slug / Alphanumeric ID"
        string title "Guide Title (e.g. Burns, CPR)"
        string category "Emergency Care | Life Support"
        string description "Narrative condition summary"
        string iconEmoji "Visual UI Emoji icon"
        array steps "Ordered list of first aid steps"
        array photoUrls "List of instructional image URLs"
        string videoUrl "Online video tutorial link"
        string storageVideoUrl "Firebase Storage local video cache"
    }
```

---

## 6. Data Flow Diagrams (DFD)

### 6.1 DFD Level 0 — System Context Diagram
The Level 0 Context Diagram establishes the global boundary of the RESPONDO BFP system, displaying data inflows and outflows between external entities and the central emergency system.

```mermaid
flowchart TD
    Citizen["🧑 Citizen / Victim / Bystander"]
    Wearable["⌚ ESP32 Wearable Device"]
    Dispatcher["🚒 BFP Dispatcher"]
    CloudFCM["📡 Firebase Cloud Messaging"]

    System(["0.0\nRESPONDO BFP\nEmergency Response &\nDispatch Management System"])

    %% Citizen Inflows/Outflows
    Citizen -->|1. Emergency SOS Trigger & GPS Fix| System
    Citizen -->|2. Medical Profile & RA 10173 Consent| System
    Citizen -->|3. Supplementary Accident Casualty Report| System
    System -->|4. Dispatch Status Sync: Deployed / En Route| Citizen
    System -->|5. First Aid Guides & Instructional Video| Citizen

    %% Wearable Inflows/Outflows
    Wearable -->|6. Physical Button Panic Signal: VICTIM / BYSTANDER| System
    Wearable -->|7. Battery Telemetry: Voltage & Percentage| System
    System -->|8. Visual State Feedback: DEPLOYED / RESOLVED / IDLE| Wearable

    %% Dispatcher Inflows/Outflows
    System -->|9. Real-Time Emergency Telemetry & Audio Alert| Dispatcher
    System -->|10. Patient Health & Consciousness Records| Dispatcher
    Dispatcher -->|11. Triage Actions & Responder Deployment| System
    Dispatcher -->|12. Incident Resolution & Closure Notes| System

    %% Push Gateway Inflows/Outflows
    System -->|13. Push Notification Dispatch Payload| CloudFCM
    CloudFCM -->|14. High-Priority Heads-Up Alert| Dispatcher
```

---

### 6.2 DFD Level 1 — Subsystem Decomposition Diagram
The Level 1 Diagram decomposes the central system into six distinct operational processes, showing internal data transformations and interactions with Firestore data stores.

```mermaid
flowchart TD
    %% External Entities
    Citizen["🧑 Citizen / Bystander"]
    Wearable["⌚ ESP32 Wearable"]
    Dispatcher["🚒 BFP Dispatcher"]
    FCM_GW["📡 FCM Push Service"]

    %% Processes
    P1(["1.0\nPanic Signal &\nTelemetry Intake"])
    P2(["2.0\nValidation & Spam\nAbuse Filter"])
    P3(["3.0\nIncident Triage &\nGIS Route Mapping"])
    P4(["4.0\nResponder Deployment\n& Status Broadcast"])
    P5(["5.0\nCasualty Reporting &\nIncident Resolution"])
    P6(["6.0\nProfile & First Aid\nKnowledge Mgmt"])

    %% Data Stores
    D1[("D1: sos_alerts")]
    D2[("D2: user_profiles")]
    D3[("D3: admin_users")]
    D4[("D4: bfp_stations")]
    D5[("D5: first_aid")]
    D6[("D6: admin_tokens")]

    %% Process 1.0 Flows
    Wearable -->|BLE Button Trigger: VICTIM / BYSTANDER| P1
    Citizen -->|Mobile SOS / Shake / Web SOS + GPS| P1
    D2 -.->|Read victim medical profile| P1
    P1 -->|Generate draft emergency document| P2

    %% Process 2.0 Flows
    P2 -->|Rate limit check <= 3 per 60s| P2
    P2 -->|Write verified active alert| D1
    P2 -->|Write flagged_spam record if violated| D1
    P2 -->|Dispatch trigger payload| FCM_GW
    D6 -.->|Read admin registration tokens| FCM_GW

    %% Process 3.0 Flows
    D1 -.->|Real-time snapshot stream| P3
    D4 -.->|Read BFP station coordinates| P3
    D3 -.->|Verify dispatcher authentication| P3
    P3 -->|Render map marker, hazard color & route polyline| Dispatcher

    %% Process 4.0 Flows
    Dispatcher -->|Action: Deploy Emergency Unit| P4
    P4 -->|Update status: 'deployed', deployedAt: serverTimestamp| D1
    P4 -->|Broadcast STATUS:DEPLOYED over BLE| Wearable
    P4 -->|Sync 'Responders En Route' status to citizen UI| Citizen

    %% Process 5.0 Flows
    Dispatcher -->|Submit casualty status & AVPU vitals| P5
    Citizen -->|Submit supplementary accident sheet| P5
    P5 -->|Update patients[] array & reportSubmitted: true| D1
    Dispatcher -->|Action: Resolve incident + enter notes| P5
    P5 -->|Update status: 'resolved', resolvedAt: serverTimestamp| D1
    P5 -->|Broadcast STATUS:RESOLVED over BLE| Wearable

    %% Process 6.0 Flows
    Citizen -->|Edit medical conditions, blood type, allergies| P6
    P6 -->|Save citizen profile & privacy consent| D2
    Citizen -->|Query CPR & emergency guides| P6
    D5 -.->|Read first aid steps & cached video URLs| P6
    P6 -->|Deliver first aid instructions| Citizen
```

---

## 7. Verification and Traceability Matrix

| Requirement / Component | Use Case | Data Dictionary Store | Component | Deployment Node | ERD Entity | DFD Process |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Wearable SOS Trigger** | UC-03 | `esp32_wearable` NVS & BLE | `BLE_SERVER` & `BLE_SVC` | `Node_ESP32` | `SOS_ALERTS` | Process 1.0 |
| **Shake to Emergency** | UC-04 | `bfp_prefs.xml` (`pref_shake`) | `SHAKE_DET` | `Node_Android` | `SOS_ALERTS` | Process 1.0 |
| **Victim Medical Profile**| UC-05, UC-06 | `user_profiles`, `bfp_user_profile`| `USER_MGR` | `Node_Android` | `USER_PROFILES`, `REPORTER_PROFILE` | Process 6.0 |
| **Spam Rate Limiter** | UC-13 | `sos_alerts.status: flagged_spam` | `CLOUD_FUNC` | `Node_Cloud` | `SOS_ALERTS` | Process 2.0 |
| **FCM Audio Siren** | UC-14 | `admin_tokens` | `FCM_SVC` & `AUDIO_CTRL` | `Node_PC` / `Node_Android`| `ADMIN_TOKENS` | Process 2.0 |
| **GIS Map & Station Route**| UC-08, UC-10 | `bfp_stations`, `sos_alerts` | `WEB_MAP`, `MAP_MARKER` | `Node_PC` | `BFP_STATIONS` | Process 3.0 |
| **Casualty Triage Sheet** | UC-11 | `sos_alerts.patients[]` | `DISPATCH_MGR` | `Node_Android` / `Node_PC`| `PATIENTS` | Process 5.0 |
| **Offline First Aid** | UC-07 | `first_aid`, `video_cache` | `FIRST_AID_REPO` | `Node_Android` | `FIRST_AID` | Process 6.0 |
