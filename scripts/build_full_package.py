import os
import zipfile
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import FancyBboxPatch, Ellipse, Rectangle, Polygon
import numpy as np
from PIL import Image

# Colors
C_DARK_BLUE = '#1E3A8A'
C_MED_BLUE  = '#2563EB'
C_LIGHT_BLUE= '#EFF6FF'
C_RED       = '#DC2626'
C_LIGHT_RED = '#FEF2F2'
C_AMBER     = '#D97706'
C_LIGHT_AMB = '#FFFBEB'
C_GREEN     = '#059669'
C_LIGHT_GRN = '#ECFDF5'
C_SLATE     = '#334155'
C_LIGHT_SLT = '#F8FAFC'
C_BORDER    = '#CBD5E1'
C_TEXT      = '#0F172A'

os.makedirs('diagrams', exist_ok=True)

# ── 1. GENERATE CLEAN DIAGRAM IMAGES (NO EMOJI GLYPHS) ─────────────────────────

def draw_use_case():
    fig, ax = plt.subplots(figsize=(16, 11), dpi=220)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97, "RESPONDO BFP — Use Case Diagram", ha='center', va='center',
            fontsize=20, fontweight='bold', color=C_DARK_BLUE)
    ax.text(50, 94, "Emergency Response & Dispatch Management System", ha='center', va='center',
            fontsize=12, color=C_SLATE, style='italic')

    # System boundary
    sys_box = FancyBboxPatch((28, 5), 52, 85, boxstyle="round,pad=1.5",
                             facecolor=C_LIGHT_SLT, edgecolor=C_MED_BLUE, linewidth=2.5, linestyle='--')
    ax.add_patch(sys_box)
    ax.text(54, 88, "<<system>> RESPONDO BFP Emergency Response Platform", ha='center', va='center',
            fontsize=13, fontweight='bold', color=C_DARK_BLUE)

    def draw_actor(x, y, name, role="Actor"):
        head = plt.Circle((x, y + 4.5), 1.8, facecolor=C_LIGHT_BLUE, edgecolor=C_DARK_BLUE, linewidth=2, zorder=5)
        ax.add_patch(head)
        ax.plot([x, x], [y + 2.7, y - 1], color=C_DARK_BLUE, linewidth=2.5, zorder=5)
        ax.plot([x - 2.5, x + 2.5], [y + 1.5, y + 1.5], color=C_DARK_BLUE, linewidth=2.5, zorder=5)
        ax.plot([x, x - 2], [y - 1, y - 4.5], color=C_DARK_BLUE, linewidth=2.5, zorder=5)
        ax.plot([x, x + 2], [y - 1, y - 4.5], color=C_DARK_BLUE, linewidth=2.5, zorder=5)
        ax.text(x, y - 6.5, name, ha='center', va='top', fontsize=10.5, fontweight='bold', color=C_TEXT)
        ax.text(x, y - 8.5, f"({role})", ha='center', va='top', fontsize=8.5, color=C_SLATE)

    draw_actor(10, 78, "Citizen / Victim", "Primary Caller")
    draw_actor(10, 48, "Bystander", "Eyewitness")
    draw_actor(10, 18, "ESP32 Wearable", "IoT Hardware Device")

    draw_actor(90, 68, "BFP Dispatcher", "BFP Command Officer")
    draw_actor(90, 28, "Cloud Functions", "Automated Backend")

    use_cases = [
        ("UC1", "Trigger Personal SOS\n(Victim Mode)", 42, 80, 16, 6.5),
        ("UC2", "Report Scene Emergency\n(Bystander Mode)", 42, 69, 17, 6.5),
        ("UC3", "Hardware Button Trigger\n(Wearable Right/Left)", 42, 58, 17, 6.5),
        ("UC4", "Shake-to-SOS Detection\n(Accelerometer G-Force)", 42, 47, 17, 6.5),
        ("UC5", "Manage Medical Profile\n& RA 10173 Consent", 42, 36, 17, 6.5),
        ("UC6", "Access First Aid & CPR\nGuides (Offline Video)", 42, 25, 17, 6.5),
        ("UC7", "Submit Casualty Report\n& AVPU Vital Signs", 42, 14, 17, 6.5),

        ("UC8", "Monitor Live GIS Map\n& Audio Dispatch Siren", 66, 75, 17, 6.5),
        ("UC9", "Triage & Deploy Units\n(Draw Station Route)", 66, 63, 17, 6.5),
        ("UC10", "Mark Incident Resolved\n& Archive Operation", 66, 51, 17, 6.5),
        ("UC11", "Auto-Detect Spam Alert\n(Frequency Abuse Filter)", 66, 38, 17, 6.5),
        ("UC12", "Broadcast FCM Push\nNotifications", 66, 25, 17, 6.5),
    ]

    uc_centers = {}
    for uid, text, cx, cy, w, h in use_cases:
        ellipse = Ellipse((cx, cy), w, h, facecolor='#FFFFFF', edgecolor=C_MED_BLUE, linewidth=2, zorder=3)
        ax.add_patch(ellipse)
        ax.text(cx, cy, text, ha='center', va='center', fontsize=9, fontweight='bold', color=C_DARK_BLUE, zorder=4)
        uc_centers[uid] = (cx, cy, w, h)

    def connect(x1, y1, uid, style='-', color='#64748B', lw=1.5):
        cx, cy, w, h = uc_centers[uid]
        dx = cx - x1
        dy = cy - y1
        dist = np.sqrt(dx*dx + dy*dy)
        x2 = cx - (dx/dist) * (w*0.45)
        y2 = cy - (dy/dist) * (h*0.45)
        ax.plot([x1, x2], [y1, y2], linestyle=style, color=color, linewidth=lw, zorder=2)

    connect(12, 78, "UC1")
    connect(12, 78, "UC4")
    connect(12, 78, "UC5")
    connect(12, 78, "UC6")

    connect(12, 48, "UC2")
    connect(12, 48, "UC6")

    connect(12, 18, "UC3")

    connect(88, 68, "UC8")
    connect(88, 68, "UC9")
    connect(88, 68, "UC10")
    connect(88, 68, "UC7")

    connect(88, 28, "UC11")
    connect(88, 28, "UC12")

    def arrow_rel(u1, u2, label):
        x1, y1, w1, h1 = uc_centers[u1]
        x2, y2, w2, h2 = uc_centers[u2]
        ax.annotate('', xy=(x2, y2 + h2*0.45), xytext=(x1, y1 - h1*0.45),
                    arrowprops=dict(arrowstyle="->", color=C_AMBER, lw=1.5, ls="--"))
        mx, my = (x1+x2)/2, (y1+y2)/2
        ax.text(mx, my, f"<<{label}>>", fontsize=8, color=C_AMBER, fontweight='bold', ha='center', va='center',
                bbox=dict(boxstyle="square,pad=0.2", fc="white", ec="none"))

    arrow_rel("UC3", "UC1", "includes")
    arrow_rel("UC4", "UC1", "includes")
    arrow_rel("UC1", "UC5", "includes")
    arrow_rel("UC9", "UC8", "includes")

    plt.tight_layout()
    plt.savefig('diagrams/use_case_diagram.png', dpi=220, bbox_inches='tight')
    plt.close()
    print("[OK] diagrams/use_case_diagram.png")

def draw_component():
    fig, ax = plt.subplots(figsize=(16, 12), dpi=220)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97, "RESPONDO BFP — UML Component Diagram", ha='center', va='center',
            fontsize=20, fontweight='bold', color=C_DARK_BLUE)
    ax.text(50, 94.5, "Structural Modules, Dependencies, and Subsystem Interfaces", ha='center', va='center',
            fontsize=12, color=C_SLATE, style='italic')

    def draw_package(x, y, w, h, title, icon_color):
        tab = FancyBboxPatch((x, y + h - 3), w*0.45, 3, boxstyle="round,pad=0.3",
                             facecolor=icon_color, edgecolor=C_BORDER, linewidth=1.5)
        ax.add_patch(tab)
        box = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.5",
                             facecolor=C_LIGHT_SLT, edgecolor=icon_color, linewidth=2)
        ax.add_patch(box)
        ax.text(x + 2, y + h - 2, title, fontsize=11, fontweight='bold', color=C_DARK_BLUE, va='center')

    def draw_comp(x, y, w, h, name, subtext=""):
        cbox = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.3",
                              facecolor="#FFFFFF", edgecolor=C_SLATE, linewidth=1.5)
        ax.add_patch(cbox)
        t1 = Rectangle((x - 1.2, y + h*0.65), 2.2, h*0.2, facecolor="#FFFFFF", edgecolor=C_SLATE, lw=1.2)
        t2 = Rectangle((x - 1.2, y + h*0.2), 2.2, h*0.2, facecolor="#FFFFFF", edgecolor=C_SLATE, lw=1.2)
        ax.add_patch(t1)
        ax.add_patch(t2)
        ax.text(x + w/2 + 0.5, y + h/2 + (1 if subtext else 0), name, ha='center', va='center',
                fontsize=9.5, fontweight='bold', color=C_TEXT)
        if subtext:
            ax.text(x + w/2 + 0.5, y + h/2 - 1.5, subtext, ha='center', va='center',
                    fontsize=8, color=C_SLATE, style='italic')

    # 1. ESP32 Hardware
    draw_package(3, 50, 44, 42, "[HARDWARE] ESP32 Wearable Subsystem", C_AMBER)
    draw_comp(6, 78, 17, 7, "Button Controller", "GPIO 0 & 35 ISR")
    draw_comp(27, 78, 17, 7, "Power & Sleep Mgr", "NVS Flash / RST Logic")
    draw_comp(6, 65, 17, 7, "ADC Battery Monitor", "12-bit Voltage Divider")
    draw_comp(27, 65, 17, 7, "ST7789 Display GUI", "LovyanGFX Portrait")
    draw_comp(16, 53, 20, 7, "BLE GATT Server", "Service & Char UUIDs")

    # 2. Android Mobile Client
    draw_package(3, 4, 44, 43, "[MOBILE] Android Client Subsystem", C_GREEN)
    draw_comp(6, 36, 18, 6.5, "BLESOSService", "Background BLE Manager")
    draw_comp(26, 36, 18, 6.5, "ShakeGestureDetector", "3-Axis Accelerometer")
    draw_comp(6, 25, 18, 6.5, "LocationEngine", "FusedLocationProvider")
    draw_comp(26, 25, 18, 6.5, "UserProfileManager", "Local & Cloud Health Sync")
    draw_comp(6, 14, 18, 6.5, "FirstAidRepository", "Offline Guide Caching")
    draw_comp(26, 14, 18, 6.5, "FCMClientService", "Push Siren Receiver")

    # 3. Web Dispatch Console
    draw_package(53, 50, 44, 42, "[WEB] Dispatch Console Subsystem", C_MED_BLUE)
    draw_comp(56, 78, 18, 7, "Leaflet GIS Engine", "Interactive OpenStreetMap")
    draw_comp(76, 78, 18, 7, "Emergency Audio", "HTML5 Audio Synthesizer")
    draw_comp(56, 65, 18, 7, "SessionSecurityGuard", "Brute-Force & Lockout")
    draw_comp(76, 65, 18, 7, "DispatchController", "Triage & Unit Routing")
    draw_comp(66, 53, 19, 7, "Web SOS Portal", "sos.html Geolocation")

    # 4. Cloud Backend
    draw_package(53, 4, 44, 43, "[CLOUD] Firebase Infrastructure", C_RED)
    draw_comp(56, 36, 18, 6.5, "Cloud Firestore", "NoSQL Realtime DB")
    draw_comp(76, 36, 18, 6.5, "Cloud Functions", "onSosAlertCreated Trigger")
    draw_comp(56, 25, 18, 6.5, "Firebase Auth", "Role-Based Security")
    draw_comp(76, 25, 18, 6.5, "FCM Gateway", "High-Priority Notification")
    draw_comp(66, 14, 19, 6.5, "Firebase Storage", "Video & Document Assets")

    # Connectors
    ax.annotate("", xy=(16, 43), xytext=(16, 53),
                arrowprops=dict(arrowstyle="<->", color=C_AMBER, lw=2.5))
    ax.text(18, 48, "Bluetooth Low Energy (GATT)\nUUID: 12345678-...", fontsize=8.5, color=C_AMBER, fontweight='bold')

    ax.annotate("", xy=(56, 39), xytext=(24, 39),
                arrowprops=dict(arrowstyle="->", color=C_GREEN, lw=2.5))
    ax.text(32, 41, "HTTPS / gRPC (Firestore Sync)", fontsize=8.5, color=C_GREEN, fontweight='bold')

    ax.annotate("", xy=(65, 43), xytext=(65, 53),
                arrowprops=dict(arrowstyle="<->", color=C_MED_BLUE, lw=2.5))
    ax.text(66.5, 48, "Real-Time Snapshot Stream", fontsize=8.5, color=C_MED_BLUE, fontweight='bold')

    ax.annotate("", xy=(85, 31.5), xytext=(85, 36),
                arrowprops=dict(arrowstyle="->", color=C_RED, lw=2))

    plt.tight_layout()
    plt.savefig('diagrams/component_diagram.png', dpi=220, bbox_inches='tight')
    plt.close()
    print("[OK] diagrams/component_diagram.png")

def draw_deployment():
    fig, ax = plt.subplots(figsize=(16, 12), dpi=220)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97, "RESPONDO BFP — UML Deployment Diagram", ha='center', va='center',
            fontsize=20, fontweight='bold', color=C_DARK_BLUE)
    ax.text(50, 94.5, "Physical Runtime Nodes, Execution Artifacts, and Network Protocols", ha='center', va='center',
            fontsize=12, color=C_SLATE, style='italic')

    def draw_node_3d(x, y, w, h, title, stereotype, color):
        d = 2.5
        poly_top = Polygon([(x, y+h), (x+d, y+h+d), (x+w+d, y+h+d), (x+w, y+h)],
                           facecolor='#E2E8F0', edgecolor=C_SLATE, lw=1.5)
        poly_rt  = Polygon([(x+w, y), (x+w+d, y+d), (x+w+d, y+h+d), (x+w, y+h)],
                           facecolor='#CBD5E1', edgecolor=C_SLATE, lw=1.5)
        rect_fr  = Rectangle((x, y), w, h, facecolor='#FFFFFF', edgecolor=C_SLATE, lw=1.8)
        ax.add_patch(poly_top)
        ax.add_patch(poly_rt)
        ax.add_patch(rect_fr)

        hbar = Rectangle((x, y+h-4), w, 4, facecolor=color, edgecolor='none')
        ax.add_patch(hbar)
        ax.text(x+w/2, y+h-1.5, f"<<{stereotype}>>", ha='center', va='center', fontsize=8, color='#FFFFFF')
        ax.text(x+w/2, y+h-3, title, ha='center', va='center', fontsize=9.5, fontweight='bold', color='#FFFFFF')

    def draw_artifact(x, y, w, h, name, desc=""):
        abox = FancyBboxPatch((x, y), w, h, boxstyle="square,pad=0.2",
                              facecolor=C_LIGHT_SLT, edgecolor=C_BORDER, lw=1.2)
        ax.add_patch(abox)
        ax.text(x+1, y+h-1.5, "<<artifact>>", fontsize=7.5, color=C_SLATE, style='italic')
        ax.text(x+1, y+h-3.2, name, fontsize=8.5, fontweight='bold', color=C_TEXT)
        if desc:
            ax.text(x+1, y+1.2, desc, fontsize=7.5, color=C_SLATE)

    # Node 1: ESP32 Hardware
    draw_node_3d(4, 52, 40, 38, "LilyGO TTGO T-Display ESP32", "device: Edge Hardware", C_AMBER)
    ax.text(6, 84, "Execution Env: FreeRTOS / Arduino Core", fontsize=8.5, fontweight='bold', color=C_DARK_BLUE)
    draw_artifact(6, 73, 36, 9, "respondo_wearable.bin", "Compiled firmware & ST7789 GUI driver")
    draw_artifact(6, 61, 36, 9, "NVS Preferences Partition", "pwr namespace: is_off, running, stage")
    ax.text(6, 54, "- Tactile Buttons (GPIO 0 & 35)   - Battery ADC (GPIO 34)", fontsize=8, color=C_SLATE)

    # Node 2: Android Phone
    draw_node_3d(4, 5, 40, 42, "Citizen / Officer Smartphone", "device: Mobile Node", C_GREEN)
    ax.text(6, 41, "Execution Env: Android OS 8.0+ / ART Runtime", fontsize=8.5, fontweight='bold', color=C_DARK_BLUE)
    draw_artifact(6, 30, 36, 9, "respondo-bfp.apk", "Native Java Mobile App & BLE Services")
    draw_artifact(6, 18, 36, 9, "SharedPreferences XML Files", "bfp_user_profile, bfp_prefs, video_cache")
    ax.text(6, 8, "- BLE 4.2/5.0 Radio   - Fused GPS Sensor   - Accelerometer", fontsize=8, color=C_SLATE)

    # Node 3: BFP Dispatch PC
    draw_node_3d(55, 52, 41, 38, "BFP Command Workstation", "device: Desktop PC Node", C_MED_BLUE)
    ax.text(57, 84, "Execution Env: Chromium / Modern Browser", fontsize=8.5, fontweight='bold', color=C_DARK_BLUE)
    draw_artifact(57, 73, 37, 9, "index.html & theme.css", "Web Dispatch Console & Leaflet GIS")
    draw_artifact(57, 61, 37, 9, "DOM Web Storage", "sessionStorage (lockout) / localStorage")
    ax.text(57, 54, "- Dual Monitor GIS Display   - Siren Audio Synthesizer", fontsize=8, color=C_SLATE)

    # Node 4: Google Cloud Platform
    draw_node_3d(55, 5, 41, 42, "Firebase Multi-Region Cloud", "cloud: Google Cloud Platform", C_RED)
    ax.text(57, 41, "Execution Env: Serverless Managed Infrastructure", fontsize=8.5, fontweight='bold', color=C_DARK_BLUE)
    draw_artifact(57, 30, 37, 9, "functions/index.js", "Node.js Cloud Function: onSosAlertCreated")
    draw_artifact(57, 18, 37, 9, "Google Cloud Firestore", "sos_alerts, user_profiles, admin_tokens, bfp_stations")
    ax.text(57, 8, "- FCM Push Broadcast Gateway   - Firebase Hosting CDN", fontsize=8, color=C_SLATE)

    # Protocol Connectors
    ax.annotate("", xy=(24, 48), xytext=(24, 52),
                arrowprops=dict(arrowstyle="<->", color=C_AMBER, lw=3))
    ax.text(25.5, 49.5, "Bluetooth Low Energy 4.2 / 5.0 (GATT)", fontsize=8.5, fontweight='bold', color=C_AMBER)

    ax.annotate("", xy=(55, 26), xytext=(45, 26),
                arrowprops=dict(arrowstyle="<->", color=C_GREEN, lw=3))
    ax.text(46, 28, "HTTPS / WSS (TLS 1.3)", fontsize=8.5, fontweight='bold', color=C_GREEN, ha='center')

    ax.annotate("", xy=(75, 48), xytext=(75, 52),
                arrowprops=dict(arrowstyle="<->", color=C_MED_BLUE, lw=3))
    ax.text(76.5, 49.5, "HTTPS / Firestore Realtime gRPC", fontsize=8.5, fontweight='bold', color=C_MED_BLUE)

    plt.tight_layout()
    plt.savefig('diagrams/deployment_diagram.png', dpi=220, bbox_inches='tight')
    plt.close()
    print("[OK] diagrams/deployment_diagram.png")

def draw_erd():
    fig, ax = plt.subplots(figsize=(16, 12), dpi=220)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97.5, "RESPONDO BFP — Database Diagram (ERD)", ha='center', va='center',
            fontsize=20, fontweight='bold', color=C_DARK_BLUE)
    ax.text(50, 95, "Entity-Relationship Model (Firestore Collections, Embedded Maps & Arrays)", ha='center', va='center',
            fontsize=12, color=C_SLATE, style='italic')

    def draw_entity(x, y, w, h, title, fields, color=C_DARK_BLUE):
        hdr = FancyBboxPatch((x, y + h - 4.5), w, 4.5, boxstyle="round,pad=0.2",
                             facecolor=color, edgecolor=C_SLATE, lw=1.5)
        ax.add_patch(hdr)
        ax.text(x + w/2, y + h - 2.2, title, ha='center', va='center',
                fontsize=10, fontweight='bold', color='#FFFFFF')
        body = FancyBboxPatch((x, y), w, h - 4.5, boxstyle="round,pad=0.2",
                              facecolor='#FFFFFF', edgecolor=C_SLATE, lw=1.5)
        ax.add_patch(body)
        for i, (pkfk, f_name, f_type) in enumerate(fields):
            curr_y = y + h - 6.5 - (i * 2.1)
            if curr_y < y + 1:
                break
            tag_color = C_RED if "PK" in pkfk else (C_MED_BLUE if "FK" in pkfk else C_SLATE)
            ax.text(x + 1, curr_y, pkfk, fontsize=7.5, fontweight='bold', color=tag_color)
            ax.text(x + 4.5, curr_y, f_name, fontsize=8, fontweight='bold', color=C_TEXT)
            ax.text(x + w - 1, curr_y, f_type, fontsize=7.5, color=C_SLATE, ha='right', style='italic')

    sos_fields = [
        ("PK", "alertId", "String (20)"),
        ("FK", "userId", "String (128)"),
        ("  ", "latitude", "Number (Float)"),
        ("  ", "longitude", "Number (Float)"),
        ("  ", "status", "String (Enum)"),
        ("  ", "timestamp", "ServerTimestamp"),
        ("  ", "accidentType", "String (Enum)"),
        ("  ", "reporterRole", "victim|bystander"),
        ("  ", "reporterProfile", "Embedded Map"),
        ("  ", "patients", "Array<Map>"),
        ("  ", "countOfVictims", "Number (Int)"),
        ("  ", "modeOfInjury", "String"),
        ("  ", "deployedAt", "Timestamp"),
        ("  ", "resolvedAt", "Timestamp"),
    ]
    draw_entity(36, 42, 28, 38, "SOS_ALERTS (Collection)", sos_fields, C_RED)

    rep_fields = [
        ("  ", "fullName", "String"),
        ("  ", "age", "String"),
        ("  ", "birthdate", "String"),
        ("  ", "phone", "String"),
        ("  ", "bloodType", "String"),
        ("  ", "allergies", "String"),
        ("  ", "medicalConditions", "String"),
        ("  ", "emergencyContact", "String"),
        ("  ", "emergencyContactName", "String"),
    ]
    draw_entity(4, 54, 25, 26, "REPORTER_PROFILE (Map)", rep_fields, C_AMBER)

    pat_fields = [
        ("  ", "name", "String"),
        ("  ", "age", "Number / String"),
        ("  ", "birthdate", "String"),
        ("  ", "emergencyContact", "String"),
        ("  ", "condition", "String"),
        ("  ", "levelOfConsciousness", "AVPU Scale"),
        ("  ", "seizure", "Boolean"),
    ]
    draw_entity(4, 22, 25, 23, "PATIENTS (Array of Maps)", pat_fields, C_AMBER)

    user_fields = [
        ("PK", "uid", "Auth UID"),
        ("  ", "fullName", "String"),
        ("  ", "phone", "String"),
        ("  ", "bloodType", "String"),
        ("  ", "allergies", "String"),
        ("  ", "medicalConditions", "String"),
        ("  ", "privacyConsentAccepted", "Boolean"),
        ("  ", "privacyConsentDate", "String"),
        ("  ", "deviceToken", "String"),
        ("  ", "updatedAt", "ServerTimestamp"),
    ]
    draw_entity(71, 58, 25, 28, "USER_PROFILES (Collection)", user_fields, C_GREEN)

    admin_fields = [
        ("PK", "uid", "Auth UID"),
        ("  ", "authorized", "Boolean"),
        ("  ", "role", "String"),
        ("FK", "stationId", "References Station"),
    ]
    draw_entity(71, 38, 25, 16, "ADMIN_USERS (Collection)", admin_fields, C_DARK_BLUE)

    token_fields = [
        ("PK", "uid", "Auth UID"),
        ("  ", "fcmToken", "String"),
        ("  ", "updatedAt", "ServerTimestamp"),
    ]
    draw_entity(71, 20, 25, 14, "ADMIN_TOKENS (Collection)", token_fields, C_DARK_BLUE)

    station_fields = [
        ("PK", "stationId", "String"),
        ("  ", "name", "String"),
        ("  ", "latitude", "Number"),
        ("  ", "longitude", "Number"),
        ("  ", "address", "String"),
        ("  ", "contactNumber", "String"),
    ]
    draw_entity(36, 17, 28, 20, "BFP_STATIONS (Collection)", station_fields, C_SLATE)

    def draw_rel(x1, y1, x2, y2, label, card1="1", card2="N"):
        ax.plot([x1, x2], [y1, y2], color=C_SLATE, lw=1.8, zorder=2)
        mx, my = (x1+x2)/2, (y1+y2)/2
        ax.text(mx, my + 1, label, fontsize=8, fontweight='bold', color=C_MED_BLUE, ha='center',
                bbox=dict(boxstyle="square,pad=0.2", fc="white", ec="none"))
        ax.text(x1 + (x2-x1)*0.1, y1 + (y2-y1)*0.1 - 1.5, card1, fontsize=8, color=C_TEXT, fontweight='bold')
        ax.text(x2 - (x2-x1)*0.1, y2 - (y2-y1)*0.1 - 1.5, card2, fontsize=8, color=C_TEXT, fontweight='bold')

    draw_rel(29, 66, 36, 66, "embeds", "1", "1")
    draw_rel(29, 35, 36, 50, "contains", "1", "N")
    draw_rel(71, 70, 64, 60, "initiates", "1", "N")
    draw_rel(71, 44, 64, 52, "manages", "1", "N")
    draw_rel(83.5, 38, 83.5, 34, "registers", "1", "N")
    draw_rel(50, 37, 50, 42, "routes_to", "1", "N")

    plt.tight_layout()
    plt.savefig('diagrams/database_erd.png', dpi=220, bbox_inches='tight')
    plt.close()
    print("[OK] diagrams/database_erd.png")

def draw_dfd_0():
    fig, ax = plt.subplots(figsize=(16, 11), dpi=220)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97, "RESPONDO BFP — Data Flow Diagram (DFD Level 0)", ha='center', va='center',
            fontsize=20, fontweight='bold', color=C_DARK_BLUE)
    ax.text(50, 94, "Context Diagram: Global External Entities & System Inflows/Outflows", ha='center', va='center',
            fontsize=12, color=C_SLATE, style='italic')

    central = plt.Circle((50, 50), 16, facecolor=C_LIGHT_BLUE, edgecolor=C_DARK_BLUE, lw=3, zorder=3)
    ax.add_patch(central)
    ax.text(50, 53, "0.0", ha='center', va='center', fontsize=16, fontweight='bold', color=C_RED, zorder=4)
    ax.text(50, 48, "RESPONDO BFP\nEmergency Response\n& Dispatch System", ha='center', va='center',
            fontsize=11, fontweight='bold', color=C_DARK_BLUE, zorder=4)

    def draw_entity_rect(x, y, w, h, name, tag=""):
        box = FancyBboxPatch((x, y), w, h, boxstyle="square,pad=0.5",
                             facecolor='#FFFFFF', edgecolor=C_SLATE, lw=2.2, zorder=3)
        ax.add_patch(box)
        ax.text(x + w/2, y + h/2 + 2, f"[{tag}]", ha='center', va='center', fontsize=10, fontweight='bold', color=C_MED_BLUE)
        ax.text(x + w/2, y + h/2 - 2, name, ha='center', va='center', fontsize=10, fontweight='bold', color=C_TEXT)

    draw_entity_rect(4, 60, 22, 16, "Citizen / Victim", "CITIZEN")
    draw_entity_rect(4, 24, 22, 16, "ESP32 Wearable", "DEVICE")
    draw_entity_rect(74, 60, 22, 16, "BFP Dispatcher", "OFFICER")
    draw_entity_rect(74, 24, 22, 16, "FCM Push Gateway", "GATEWAY")

    def flow_curve(p1, p2, text, rad=0.15, color=C_MED_BLUE):
        ax.annotate("", xy=p2, xytext=p1,
                    arrowprops=dict(arrowstyle="->", connectionstyle=f"arc3,rad={rad}", color=color, lw=2))
        mx = (p1[0] + p2[0])/2
        my = (p1[1] + p2[1])/2 + (rad * 15)
        ax.text(mx, my, text, ha='center', va='center', fontsize=8, fontweight='bold', color=color,
                bbox=dict(boxstyle="square,pad=0.2", fc="white", ec="none"))

    flow_curve((26, 72), (38, 62), "1. SOS Trigger & GPS Fix", 0.1, C_RED)
    flow_curve((26, 66), (36, 56), "2. Medical Profile & RA 10173 Consent", 0.1, C_GREEN)
    flow_curve((38, 48), (26, 60), "3. Dispatch Status Sync ('Deployed')", 0.1, C_MED_BLUE)

    flow_curve((26, 36), (38, 42), "4. Hardware Button Panic Signal", 0.1, C_RED)
    flow_curve((26, 30), (36, 38), "5. Battery Voltage & Telemetry", 0.1, C_AMBER)
    flow_curve((42, 35), (26, 24), "6. Status Sync ('Responders En Route')", -0.15, C_DARK_BLUE)

    flow_curve((62, 60), (74, 72), "7. Real-Time Telemetry & Siren Alarm", 0.1, C_RED)
    flow_curve((64, 52), (74, 64), "8. Patient Vitals & Injury Data", 0.1, C_AMBER)
    flow_curve((74, 58), (62, 46), "9. Unit Deployment & Route Polyline", -0.15, C_GREEN)
    flow_curve((74, 54), (60, 40), "10. Incident Resolution Notes", -0.2, C_SLATE)

    flow_curve((60, 36), (74, 30), "11. Dispatch Alert Payload", 0.1, C_RED)

    plt.tight_layout()
    plt.savefig('diagrams/dfd_level_0.png', dpi=220, bbox_inches='tight')
    plt.close()
    print("[OK] diagrams/dfd_level_0.png")

def draw_dfd_1():
    fig, ax = plt.subplots(figsize=(16, 12), dpi=220)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97.5, "RESPONDO BFP — Data Flow Diagram (DFD Level 1)", ha='center', va='center',
            fontsize=20, fontweight='bold', color=C_DARK_BLUE)
    ax.text(50, 95, "Subsystem Functional Decomposition, Transformations & Firestore Data Stores", ha='center', va='center',
            fontsize=12, color=C_SLATE, style='italic')

    def draw_ent(x, y, w, h, name):
        box = FancyBboxPatch((x, y), w, h, boxstyle="square,pad=0.2",
                             facecolor='#FFFFFF', edgecolor=C_SLATE, lw=2)
        ax.add_patch(box)
        ax.text(x+w/2, y+h/2, name, ha='center', va='center', fontsize=9.5, fontweight='bold', color=C_TEXT)

    draw_ent(3, 80, 16, 8, "Citizen / Victim")
    draw_ent(3, 62, 16, 8, "ESP32 Wearable")
    draw_ent(81, 70, 16, 8, "BFP Dispatcher")
    draw_ent(81, 20, 16, 8, "FCM Gateway")

    def draw_proc(x, y, r, num, name):
        circle = plt.Circle((x, y), r, facecolor=C_LIGHT_BLUE, edgecolor=C_DARK_BLUE, lw=2, zorder=3)
        ax.add_patch(circle)
        ax.text(x, y + 2, num, ha='center', va='center', fontsize=10, fontweight='bold', color=C_RED, zorder=4)
        ax.text(x, y - 1.5, name, ha='center', va='center', fontsize=7.5, fontweight='bold', color=C_DARK_BLUE, zorder=4)

    draw_proc(32, 80, 6.5, "1.0", "Panic Signal &\nTelemetry Intake")
    draw_proc(55, 80, 6.5, "2.0", "Validation & Spam\nAbuse Filter")
    draw_proc(65, 55, 6.5, "3.0", "Incident Triage &\nGIS Route Mapping")
    draw_proc(40, 50, 6.5, "4.0", "Responder Deploy\n& Status Broadcast")
    draw_proc(40, 25, 6.5, "5.0", "Casualty Report &\nIncident Resolution")
    draw_proc(15, 25, 6.5, "6.0", "Profile & First Aid\nKnowledge Mgmt")

    def draw_store(x, y, w, h, num, name):
        box = Rectangle((x, y), w, h, facecolor='#FFFFFF', edgecolor=C_MED_BLUE, lw=1.5)
        ax.add_patch(box)
        ax.plot([x + 6, x + 6], [y, y + h], color=C_MED_BLUE, lw=1.5)
        ax.text(x + 3, y + h/2, num, ha='center', va='center', fontsize=8, fontweight='bold', color=C_MED_BLUE)
        ax.text(x + 8, y + h/2, name, ha='left', va='center', fontsize=8, fontweight='bold', color=C_TEXT)

    draw_store(38, 65, 24, 4.5, "D1", "sos_alerts")
    draw_store(15, 42, 22, 4.5, "D2", "user_profiles")
    draw_store(72, 45, 22, 4.5, "D3", "admin_users")
    draw_store(72, 38, 22, 4.5, "D4", "bfp_stations")
    draw_store(15, 12, 22, 4.5, "D5", "first_aid")
    draw_store(72, 31, 22, 4.5, "D6", "admin_tokens")

    def arrow(p1, p2, text="", color=C_SLATE, offset=(0, 1.5)):
        ax.annotate("", xy=p2, xytext=p1,
                    arrowprops=dict(arrowstyle="->", color=color, lw=1.8))
        if text:
            mx, my = (p1[0]+p2[0])/2 + offset[0], (p1[1]+p2[1])/2 + offset[1]
            ax.text(mx, my, text, fontsize=7.5, fontweight='bold', color=color, ha='center',
                    bbox=dict(boxstyle="square,pad=0.2", fc="white", ec="none"))

    arrow((19, 83), (25.5, 83), "Mobile SOS / Shake", C_RED)
    arrow((19, 66), (28, 75), "BLE Panic Signal", C_RED)
    arrow((38.5, 80), (48.5, 80), "Draft Alert", C_SLATE)
    arrow((55, 73.5), (55, 69.5), "Write Active Alert", C_GREEN)
    arrow((61.5, 80), (81, 24), "Push Payload", C_RED)

    arrow((50, 65), (60, 58), "Snapshot Stream", C_MED_BLUE)
    arrow((81, 74), (71.5, 57), "Triage Action", C_DARK_BLUE)
    arrow((71.5, 53), (46.5, 50), "Deploy Team", C_AMBER)
    arrow((40, 56.5), (40, 65), "status: deployed", C_GREEN)

    arrow((81, 70), (46.5, 27), "Resolve Notes", C_SLATE)
    arrow((40, 31.5), (40, 65), "status: resolved", C_GREEN)

    arrow((11, 80), (15, 31.5), "Profile Edit", C_MED_BLUE)
    arrow((15, 25), (18, 42), "Sync Profile", C_GREEN)

    plt.tight_layout()
    plt.savefig('diagrams/dfd_level_1.png', dpi=220, bbox_inches='tight')
    plt.close()
    print("[OK] diagrams/dfd_level_1.png")

# ── 2. COMPILE FULL DOCX DOCUMENT ──────────────────────────────────────────────

def create_docx(output_path="RESPONDO_BFP_SYSTEM_DOCUMENTATION.docx"):
    print(f"Compiling complete Word DOCX document: {output_path}...")

    # Load diagrams to embed
    diagram_files = [
        ("use_case_diagram.png", "Figure 1: System Use Case Diagram"),
        ("component_diagram.png", "Figure 2: UML Component Diagram"),
        ("deployment_diagram.png", "Figure 3: UML Deployment Diagram"),
        ("database_erd.png", "Figure 4: Entity-Relationship Diagram (ERD)"),
        ("dfd_level_0.png", "Figure 5: Data Flow Diagram Level 0 (Context Diagram)"),
        ("dfd_level_1.png", "Figure 6: Data Flow Diagram Level 1 (Decomposition Diagram)"),
    ]

    # OpenXML template structures
    content_types_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

    rels_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    doc_rels_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rIdImg1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image1.png"/>
  <Relationship Id="rIdImg2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image2.png"/>
  <Relationship Id="rIdImg3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image3.png"/>
  <Relationship Id="rIdImg4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image4.png"/>
  <Relationship Id="rIdImg5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image5.png"/>
  <Relationship Id="rIdImg6" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image6.png"/>
</Relationships>"""

    styles_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
        <w:sz w:val="22"/>
        <w:color w:val="0F172A"/>
      </w:rPr>
    </w:rPrDefault>
    <w:pPrDefault>
      <w:pPr>
        <w:spacing w:after="160" w:line="276" w:lineRule="auto"/>
      </w:pPr>
    </w:pPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:styleId="Title">
    <w:name w:val="Title"/>
    <w:pPr>
      <w:spacing w:before="240" w:after="120"/>
      <w:jc w:val="center"/>
    </w:pPr>
    <w:rPr>
      <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
      <w:b/>
      <w:sz w:val="52"/>
      <w:color w:val="1E3A8A"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:pPr>
      <w:spacing w:before="360" w:after="120"/>
    </w:pPr>
    <w:rPr>
      <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
      <w:b/>
      <w:sz w:val="36"/>
      <w:color w:val="1E3A8A"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:pPr>
      <w:spacing w:before="240" w:after="80"/>
    </w:pPr>
    <w:rPr>
      <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
      <w:b/>
      <w:sz w:val="28"/>
      <w:color w:val="2563EB"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Caption">
    <w:name w:val="caption"/>
    <w:pPr>
      <w:spacing w:before="80" w:after="200"/>
      <w:jc w:val="center"/>
    </w:pPr>
    <w:rPr>
      <w:i/>
      <w:sz w:val="18"/>
      <w:color w:val="64748B"/>
    </w:rPr>
  </w:style>
</w:styles>"""

    # Helper functions to build Word XML
    def p(text="", bold=False, italic=False, size=22, color="0F172A", style=None, align=None):
        style_xml = f'<w:pStyle w:val="{style}"/>' if style else ""
        align_xml = f'<w:jc w:val="{align}"/>' if align else ""
        p_pr = f"<w:pPr>{style_xml}{align_xml}</w:pPr>" if (style_xml or align_xml) else ""
        b_xml = "<w:b/>" if bold else ""
        i_xml = "<w:i/>" if italic else ""
        r_pr = f"<w:rPr>{b_xml}{i_xml}<w:sz w:val='{size}'/><w:color w:val='{color}'/></w:rPr>"
        # escape XML
        safe_text = str(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return f"<w:p>{p_pr}<w:r>{r_pr}<w:t xml:space='preserve'>{safe_text}</w:t></w:r></w:p>"

    def img_p(rel_id, cx=5800000, cy=4000000, desc="Diagram"):
        return f"""<w:p>
  <w:pPr><w:jc w:val="center"/></w:pPr>
  <w:r>
    <w:drawing>
      <wp:inline distT="0" distB="0" distL="0" distR="0" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing">
        <wp:extent cx="{cx}" cy="{cy}"/>
        <wp:effectExtent l="0" t="0" r="0" b="0"/>
        <wp:docPr id="1" name="{desc}"/>
        <wp:cNvGraphicFramePr/>
        <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
          <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
            <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
              <pic:nvPicPr>
                <pic:cNvPr id="0" name="{desc}"/>
                <pic:cNvPicPr/>
              </pic:nvPicPr>
              <pic:blipFill>
                <a:blip xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:embed="{rel_id}"/>
                <a:stretch><a:fillRect/></a:stretch>
              </pic:blipFill>
              <pic:spPr>
                <a:xfrm><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm>
                <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
              </pic:spPr>
            </pic:pic>
          </a:graphicData>
        </a:graphic>
      </wp:inline>
    </w:drawing>
  </w:r>
</w:p>"""

    def table_xml(headers, rows):
        xml = ['<w:tbl><w:tblPr><w:tblW w:w="5000" w:type="pct"/><w:tblBorders><w:top w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="CBD5E1"/><w:insideH w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/><w:insideV w:val="none"/></w:tblBorders></w:tblPr>']
        # Header Row
        xml.append('<w:tr><w:trPr><w:tblHeader/></w:trPr>')
        for h in headers:
            xml.append(f'<w:tc><w:tcPr><w:shd w:val="clear" w:color="auto" w:fill="1E3A8A"/><w:tcMar><w:top w:w="120" w:type="dxa"/><w:bottom w:w="120" w:type="dxa"/><w:left w:w="150" w:type="dxa"/><w:right w:w="150" w:type="dxa"/></w:tcMar></w:tcPr>{p(h, bold=True, size=20, color="FFFFFF")}</w:tc>')
        xml.append('</w:tr>')
        # Body rows
        for row in rows:
            xml.append('<w:tr>')
            for cell in row:
                xml.append(f'<w:tc><w:tcPr><w:tcMar><w:top w:w="80" w:type="dxa"/><w:bottom w:w="80" w:type="dxa"/><w:left w:w="150" w:type="dxa"/><w:right w:w="150" w:type="dxa"/></w:tcMar></w:tcPr>{p(cell, size=19)}</w:tc>')
            xml.append('</w:tr>')
        xml.append('</w:tbl>')
        return "".join(xml)

    # Assemble Document XML Body
    body_parts = []

    # Title & Metadata
    body_parts.append(p("RESPONDO BFP", bold=True, size=52, color="1E3A8A", align="center"))
    body_parts.append(p("Emergency Response & Dispatch Management System", italic=True, size=26, color="475569", align="center"))
    body_parts.append(p("Systems Analysis and Design (SAD) — Complete Specification", bold=True, size=24, color="2563EB", align="center"))
    body_parts.append(p("System Version: 2.1 | Scope: Web, Android Mobile, ESP32 IoT Wearable, Cloud", size=18, color="64748B", align="center"))
    body_parts.append(p(""))

    # Section 1: Use Case Diagram
    body_parts.append(p("1. Use Case Diagram", bold=True, size=34, color="1E3A8A", style="Heading1"))
    body_parts.append(p("The Use Case Diagram defines the behavioral interactions between human actors (Citizen/Victim, Bystander, BFP Dispatcher), physical IoT hardware (ESP32 Wearable), and automated backend cloud services."))
    body_parts.append(img_p("rIdImg1", cx=6000000, cy=4125000, desc="Use Case Diagram"))
    body_parts.append(p("Figure 1: RESPONDO BFP — System Use Case Diagram", style="Caption"))
    body_parts.append(p(""))

    # Section 2: Component Diagram
    body_parts.append(p("2. UML Component Diagram", bold=True, size=34, color="1E3A8A", style="Heading1"))
    body_parts.append(p("The Component Diagram illustrates the internal structural packages, runtime modules, sensor engines, and communication interfaces linking the edge hardware, native Android application, web console, and Google Cloud platform."))
    body_parts.append(img_p("rIdImg2", cx=6000000, cy=4500000, desc="Component Diagram"))
    body_parts.append(p("Figure 2: RESPONDO BFP — UML Component Diagram", style="Caption"))
    body_parts.append(p(""))

    # Section 3: Deployment Diagram
    body_parts.append(p("3. UML Deployment Diagram", bold=True, size=34, color="1E3A8A", style="Heading1"))
    body_parts.append(p("The Deployment Diagram depicts the physical hardware devices, virtual execution environments, software artifacts (.apk, .bin, cloud functions), and networking protocols (BLE 4.2/5.0 GATT, HTTPS/TLS 1.3, FCM Push)."))
    body_parts.append(img_p("rIdImg3", cx=6000000, cy=4500000, desc="Deployment Diagram"))
    body_parts.append(p("Figure 3: RESPONDO BFP — UML Deployment Diagram", style="Caption"))
    body_parts.append(p(""))

    # Section 4: Database Diagram (ERD)
    body_parts.append(p("4. Database Diagram (Entity-Relationship Diagram)", bold=True, size=34, color="1E3A8A", style="Heading1"))
    body_parts.append(p("The Database Diagram defines all Google Cloud Firestore collections, embedded medical profiles, patient casualty arrays, administrator access control lists, and fire station location entities."))
    body_parts.append(img_p("rIdImg4", cx=6000000, cy=4500000, desc="Database ERD"))
    body_parts.append(p("Figure 4: RESPONDO BFP — Entity-Relationship Diagram (ERD)", style="Caption"))
    body_parts.append(p(""))

    # Section 5: Data Flow Diagrams
    body_parts.append(p("5. Data Flow Diagrams (DFD)", bold=True, size=34, color="1E3A8A", style="Heading1"))
    body_parts.append(p("5.1 DFD Level 0 — Context Diagram", bold=True, size=26, color="2563EB", style="Heading2"))
    body_parts.append(p("The Level 0 Context Diagram establishes the global boundary of the system, showing primary external data inflows and dispatch feedback loops."))
    body_parts.append(img_p("rIdImg5", cx=6000000, cy=4125000, desc="DFD Level 0"))
    body_parts.append(p("Figure 5: RESPONDO BFP — DFD Level 0 Context Diagram", style="Caption"))
    body_parts.append(p(""))

    body_parts.append(p("5.2 DFD Level 1 — Subsystem Decomposition Diagram", bold=True, size=26, color="2563EB", style="Heading2"))
    body_parts.append(p("The Level 1 Decomposition Diagram breaks the emergency pipeline into six distinct processes (Panic Intake, Abuse Filtering, GIS Triage, Responder Deployment, Casualty Reporting, and Knowledge Management) interacting with data stores D1 through D6."))
    body_parts.append(img_p("rIdImg6", cx=6000000, cy=4500000, desc="DFD Level 1"))
    body_parts.append(p("Figure 6: RESPONDO BFP — DFD Level 1 Subsystem Decomposition Diagram", style="Caption"))
    body_parts.append(p(""))

    # Section 6: Data Dictionary
    body_parts.append(p("6. Comprehensive System Data Dictionary", bold=True, size=34, color="1E3A8A", style="Heading1"))
    body_parts.append(p("The complete data catalog covering Firestore database collections, edge hardware storage, mobile app preferences, and web storage."))

    # Table 1: sos_alerts
    body_parts.append(p("6.1 Primary Collection: sos_alerts", bold=True, size=24, color="2563EB", style="Heading2"))
    headers_sos = ["Field Name", "Data Type", "Required", "Constraints & Allowed Values", "Description"]
    rows_sos = [
        ["userId", "String", "Yes (Immutable)", "Max 128 chars", "Reporting citizen / device ID"],
        ["latitude", "Number", "Yes (Immutable)", "-90.0 to 90.0", "GPS incident latitude"],
        ["longitude", "Number", "Yes (Immutable)", "-180.0 to 180.0", "GPS incident longitude"],
        ["status", "String", "Yes", "active, deployed, resolved, flagged_spam", "Operational dispatch state"],
        ["timestamp", "Timestamp", "Yes (Immutable)", "ServerTimestamp", "Creation timestamp"],
        ["source", "String", "No", "android_app, esp32_wearable, web_sos", "Hardware / client origin"],
        ["accidentType", "String", "No", "Fire, Medical, Vehicle, Hazard, Other", "Emergency hazard category"],
        ["reporterRole", "String", "No", "victim, bystander", "Reporter involvement role"],
        ["reporterProfile", "Map", "No", "Map (name, phone, bloodType, allergies)", "Embedded victim medical data"],
        ["patients", "Array<Map>", "No", "Max 50 patient entries", "Casualty details & AVPU scale"],
        ["countOfVictims", "Number", "No", "Integer >= 0", "Total casualty count"],
        ["deployedAt", "Timestamp", "No", "ServerTimestamp", "BFP deployment timestamp"],
        ["resolvedAt", "Timestamp", "No", "ServerTimestamp", "Resolution timestamp"],
        ["resolvedReason", "String", "No", "Max 500 chars", "Responder resolution notes"],
        ["spamReason", "String", "No", "Max 200 chars", "Abuse filter explanation"],
    ]
    body_parts.append(table_xml(headers_sos, rows_sos))
    body_parts.append(p(""))

    # Table 2: user_profiles
    body_parts.append(p("6.2 Collection: user_profiles", bold=True, size=24, color="2563EB", style="Heading2"))
    headers_usr = ["Field Name", "Data Type", "Required", "Description"]
    rows_usr = [
        ["fullName", "String", "No", "Legal full name of citizen"],
        ["phone", "String", "No", "Primary contact telephone number"],
        ["bloodType", "String", "No", "ABO/Rh Blood Group (O+, A+, B+, etc.)"],
        ["allergies", "String", "No", "Known pharmacological & food allergies"],
        ["medicalConditions", "String", "No", "Pre-existing chronic medical illnesses"],
        ["emergencyContact", "String", "No", "Emergency next-of-kin telephone"],
        ["privacyConsentAccepted", "Boolean", "Yes", "Explicit consent under RA 10173"],
        ["deviceToken", "String", "No", "FCM push registration token"],
    ]
    body_parts.append(table_xml(headers_usr, rows_usr))
    body_parts.append(p(""))

    # Table 3: Hardware & Web Client Storage
    body_parts.append(p("6.3 Edge Hardware & Client Storage Data Stores", bold=True, size=24, color="2563EB", style="Heading2"))
    headers_stg = ["Subsystem", "Storage Mechanism", "Keys / Variables", "Description"]
    rows_stg = [
        ["ESP32 Wearable", "NVS Flash (namespace 'pwr')", "is_off, running, stage", "Power state machine across deep-sleep & reboots"],
        ["ESP32 Wearable", "BLE GATT Service", "UUID: 12345678-..., TRIGGER:VICTIM", "Wireless panic triggers & battery telemetry"],
        ["Android Mobile", "SharedPreferences (bfp_prefs)", "pref_ble_mac, activeAlertId, pref_shake", "Wearable bonding & accelerometer sensitivity"],
        ["Android Mobile", "SharedPreferences (admin_auth)", "failed_attempts, lockout_timestamp", "Brute-force password defense on mobile"],
        ["Web Dispatch", "sessionStorage", "bfp_failed_login_attempts, bfp_lockout_until", "Browser session brute-force lockout guard"],
        ["Web Citizen SOS", "localStorage", "pwa_user_profile, bfp_user_id", "Offline web profile and persistent citizen UID"],
    ]
    body_parts.append(table_xml(headers_stg, rows_stg))
    body_parts.append(p(""))

    # End document body
    document_xml = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
  <w:body>
    {"".join(body_parts)}
    <w:sectPr>
      <w:pgSz w:w="12240" w:h="15840"/>
      <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="720" w:footer="720" w:gutter="0"/>
    </w:sectPr>
  </w:body>
</w:document>"""

    # Build ZIP / DOCX package
    with zipfile.ZipFile(output_path, 'w', compression=zipfile.ZIP_DEFLATED) as docx:
        docx.writestr('[Content_Types].xml', content_types_xml)
        docx.writestr('_rels/.rels', rels_xml)
        docx.writestr('word/_rels/document.xml.rels', doc_rels_xml)
        docx.writestr('word/styles.xml', styles_xml)
        docx.writestr('word/document.xml', document_xml)

        # Write images into word/media/
        for idx, (filename, _) in enumerate(diagram_files, start=1):
            filepath = os.path.join('diagrams', filename)
            if os.path.exists(filepath):
                with open(filepath, 'rb') as f:
                    docx.writestr(f'word/media/image{idx}.png', f.read())
                print(f"  -> Embedded word/media/image{idx}.png ({filename})")
            else:
                print(f"  [!] Missing file {filepath}")

    print(f"[DONE] Successfully generated {output_path} ({os.path.getsize(output_path)} bytes)!")

if __name__ == '__main__':
    print("=== Step 1: Generating Diagram PNG Images ===")
    draw_use_case()
    draw_component()
    draw_deployment()
    draw_erd()
    draw_dfd_0()
    draw_dfd_1()

    print("\n=== Step 2: Assembling Word Document (.docx) ===")
    create_docx("RESPONDO_BFP_SYSTEM_DOCUMENTATION.docx")
