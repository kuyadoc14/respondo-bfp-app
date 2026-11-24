import os
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import FancyBboxPatch, Ellipse, Rectangle, Polygon, ArrowStyle
import numpy as np
import zipfile
import xml.etree.ElementTree as ET
from io import BytesIO

# Colors
C_DARK_BLUE = '#1E3A8A'
C_MED_BLUE  = '#3B82F6'
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

def draw_use_case():
    fig, ax = plt.subplots(figsize=(16, 11), dpi=200)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    # Title
    ax.text(50, 97, "RESPONDO BFP — Use Case Diagram", ha='center', va='center',
            fontsize=20, fontweight='bold', color=C_DARK_BLUE)
    ax.text(50, 94, "Emergency Response & Dispatch Management System", ha='center', va='center',
            fontsize=12, color=C_SLATE, style='italic')

    # System boundary box
    sys_box = FancyBboxPatch((28, 5), 52, 85, boxstyle="round,pad=1.5",
                             facecolor=C_LIGHT_SLT, edgecolor=C_MED_BLUE, linewidth=2.5, linestyle='--')
    ax.add_patch(sys_box)
    ax.text(54, 88, "«system» RESPONDO BFP Emergency Response Platform", ha='center', va='center',
            fontsize=13, fontweight='bold', color=C_DARK_BLUE)

    # Actor drawing helper
    def draw_actor(x, y, name, role="Actor"):
        # Head
        head = plt.Circle((x, y + 4.5), 1.8, facecolor=C_LIGHT_BLUE, edgecolor=C_DARK_BLUE, linewidth=2, zorder=5)
        ax.add_patch(head)
        # Body
        ax.plot([x, x], [y + 2.7, y - 1], color=C_DARK_BLUE, linewidth=2.5, zorder=5)
        # Arms
        ax.plot([x - 2.5, x + 2.5], [y + 1.5, y + 1.5], color=C_DARK_BLUE, linewidth=2.5, zorder=5)
        # Legs
        ax.plot([x, x - 2], [y - 1, y - 4.5], color=C_DARK_BLUE, linewidth=2.5, zorder=5)
        ax.plot([x, x + 2], [y - 1, y - 4.5], color=C_DARK_BLUE, linewidth=2.5, zorder=5)
        # Name
        ax.text(x, y - 6.5, name, ha='center', va='top', fontsize=10.5, fontweight='bold', color=C_TEXT)
        ax.text(x, y - 8.5, f"({role})", ha='center', va='top', fontsize=8.5, color=C_SLATE)

    # Human Actors Left
    draw_actor(10, 78, "Citizen / Victim", "Primary Caller")
    draw_actor(10, 48, "Bystander", "Eyewitness")
    draw_actor(10, 18, "ESP32 Wearable", "IoT Hardware Device")

    # Dispatcher Actor Right
    draw_actor(90, 68, "BFP Dispatcher", "BFP Command Officer")
    draw_actor(90, 28, "Cloud Functions", "Automated Backend")

    # Use Cases (Ovals)
    use_cases = [
        # (id, text, x, y, width, height)
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
        ax.text(cx, cy, text, ha='center', va='center', fontsize=9, fontweight='semibold', color=C_DARK_BLUE, zorder=4)
        uc_centers[uid] = (cx, cy, w, h)

    # Actor connection lines
    def connect(x1, y1, uid, style='-', color='#64748B', lw=1.5):
        cx, cy, w, h = uc_centers[uid]
        dx = cx - x1
        dy = cy - y1
        dist = np.sqrt(dx*dx + dy*dy)
        # trim near ellipse
        t_edge = 0.5 * w / max(abs(dx/dist), 0.1) if dist > 0 else 0
        x2 = cx - (dx/dist) * (w*0.45)
        y2 = cy - (dy/dist) * (h*0.45)
        ax.plot([x1, x2], [y1, y2], linestyle=style, color=color, linewidth=lw, zorder=2)

    # Connect Citizen
    connect(12, 78, "UC1")
    connect(12, 78, "UC4")
    connect(12, 78, "UC5")
    connect(12, 78, "UC6")

    # Connect Bystander
    connect(12, 48, "UC2")
    connect(12, 48, "UC6")

    # Connect Wearable
    connect(12, 18, "UC3")

    # Connect Dispatcher
    connect(88, 68, "UC8")
    connect(88, 68, "UC9")
    connect(88, 68, "UC10")
    connect(88, 68, "UC7")

    # Connect Cloud Functions
    connect(88, 28, "UC11")
    connect(88, 28, "UC12")

    # Inclusions & Extensions (dashed with arrows)
    def arrow_rel(u1, u2, label):
        x1, y1, w1, h1 = uc_centers[u1]
        x2, y2, w2, h2 = uc_centers[u2]
        ax.annotate('', xy=(x2, y2 + h2*0.45), xytext=(x1, y1 - h1*0.45),
                    arrowprops=dict(arrowstyle="->", color=C_AMBER, lw=1.5, ls="--"))
        mx, my = (x1+x2)/2, (y1+y2)/2
        ax.text(mx, my, f"«{label}»", fontsize=8, color=C_AMBER, fontweight='bold', ha='center', va='center',
                bbox=dict(boxstyle="square,pad=0.2", fc="white", ec="none"))

    arrow_rel("UC3", "UC1", "includes")
    arrow_rel("UC4", "UC1", "includes")
    arrow_rel("UC1", "UC5", "includes")
    arrow_rel("UC9", "UC8", "includes")

    plt.tight_layout()
    plt.savefig('diagrams/use_case_diagram.png', dpi=200, bbox_inches='tight')
    plt.close()
    print("Generated diagrams/use_case_diagram.png")

def draw_component():
    fig, ax = plt.subplots(figsize=(16, 12), dpi=200)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97, "RESPONDO BFP — UML Component Diagram", ha='center', va='center',
            fontsize=20, fontweight='bold', color=C_DARK_BLUE)
    ax.text(50, 94.5, "Structural Modules, Dependencies, and Subsystem Interfaces", ha='center', va='center',
            fontsize=12, color=C_SLATE, style='italic')

    # Draw package container
    def draw_package(x, y, w, h, title, icon_color):
        tab = FancyBboxPatch((x, y + h - 3), w*0.35, 3, boxstyle="round,pad=0.3",
                             facecolor=icon_color, edgecolor=C_BORDER, linewidth=1.5)
        ax.add_patch(tab)
        box = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.5",
                             facecolor=C_LIGHT_SLT, edgecolor=icon_color, linewidth=2)
        ax.add_patch(box)
        ax.text(x + 2, y + h - 2, title, fontsize=11, fontweight='bold', color=C_DARK_BLUE, va='center')

    # Draw UML component box
    def draw_comp(x, y, w, h, name, subtext=""):
        cbox = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.3",
                              facecolor="#FFFFFF", edgecolor=C_SLATE, linewidth=1.5)
        ax.add_patch(cbox)
        # Small UML component tabs on left
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
    draw_package(3, 50, 44, 42, "⌚ ESP32 Wearable Firmware Subsystem", C_AMBER)
    draw_comp(6, 78, 17, 7, "Button Controller", "GPIO 0 & 35 ISR")
    draw_comp(27, 78, 17, 7, "Power & Sleep Manager", "NVS Flash / RST Logic")
    draw_comp(6, 65, 17, 7, "ADC Battery Monitor", "12-bit Voltage Divider")
    draw_comp(27, 65, 17, 7, "ST7789 Display GUI", "LovyanGFX Portrait")
    draw_comp(16, 53, 20, 7, "BLE GATT Server", "Service & Char UUIDs")

    # 2. Android Mobile Client
    draw_package(3, 4, 44, 43, "📱 Android Mobile Client Subsystem", C_GREEN)
    draw_comp(6, 36, 18, 6.5, "BLESOSService", "Background BLE Manager")
    draw_comp(26, 36, 18, 6.5, "ShakeGestureDetector", "3-Axis Accelerometer")
    draw_comp(6, 25, 18, 6.5, "LocationEngine", "FusedLocationProvider")
    draw_comp(26, 25, 18, 6.5, "UserProfileManager", "Local & Cloud Health Sync")
    draw_comp(6, 14, 18, 6.5, "FirstAidRepository", "Offline Guide Caching")
    draw_comp(26, 14, 18, 6.5, "FCMClientService", "Push Siren Receiver")

    # 3. Web Dispatch Console
    draw_package(53, 50, 44, 42, "💻 Web Dispatch Console (public/)", C_MED_BLUE)
    draw_comp(56, 78, 18, 7, "Leaflet GIS Engine", "Interactive OpenStreetMap")
    draw_comp(76, 78, 18, 7, "Emergency Audio", "HTML5 Audio Synthesizer")
    draw_comp(56, 65, 18, 7, "SessionSecurityGuard", "Brute-Force & Lockout")
    draw_comp(76, 65, 18, 7, "DispatchController", "Triage & Unit Routing")
    draw_comp(66, 53, 19, 7, "Web SOS Portal", "sos.html Geolocation")

    # 4. Cloud Backend
    draw_package(53, 4, 44, 43, "☁️ Google Cloud & Firebase Backend", C_RED)
    draw_comp(56, 36, 18, 6.5, "Cloud Firestore", "NoSQL Realtime DB")
    draw_comp(76, 36, 18, 6.5, "Cloud Functions", "onSosAlertCreated Trigger")
    draw_comp(56, 25, 18, 6.5, "Firebase Auth", "Role-Based Security")
    draw_comp(76, 25, 18, 6.5, "FCM Gateway", "High-Priority Notification")
    draw_comp(66, 14, 19, 6.5, "Firebase Storage", "Video & Document Assets")

    # Connectors across packages
    # BLE connection between ESP32 and Android
    ax.annotate("", xy=(16, 43), xytext=(16, 53),
                arrowprops=dict(arrowstyle="<->", color=C_AMBER, lw=2.5))
    ax.text(18, 48, "Bluetooth Low Energy (GATT)\nUUID: 12345678-...", fontsize=8.5, color=C_AMBER, fontweight='bold')

    # Android to Firestore
    ax.annotate("", xy=(56, 39), xytext=(24, 39),
                arrowprops=dict(arrowstyle="->", color=C_GREEN, lw=2.5))
    ax.text(32, 41, "HTTPS / gRPC (Firestore Sync)", fontsize=8.5, color=C_GREEN, fontweight='bold')

    # Web Console to Firestore
    ax.annotate("", xy=(65, 43), xytext=(65, 53),
                arrowprops=dict(arrowstyle="<->", color=C_MED_BLUE, lw=2.5))
    ax.text(66.5, 48, "Real-Time Snapshot Stream", fontsize=8.5, color=C_MED_BLUE, fontweight='bold')

    # Cloud Functions to FCM
    ax.annotate("", xy=(85, 31.5), xytext=(85, 36),
                arrowprops=dict(arrowstyle="->", color=C_RED, lw=2))

    plt.tight_layout()
    plt.savefig('diagrams/component_diagram.png', dpi=200, bbox_inches='tight')
    plt.close()
    print("Generated diagrams/component_diagram.png")

def draw_deployment():
    fig, ax = plt.subplots(figsize=(16, 12), dpi=200)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97, "RESPONDO BFP — UML Deployment Diagram", ha='center', va='center',
            fontsize=20, fontweight='bold', color=C_DARK_BLUE)
    ax.text(50, 94.5, "Physical Runtime Nodes, Execution Artifacts, and Network Protocols", ha='center', va='center',
            fontsize=12, color=C_SLATE, style='italic')

    # 3D Node Drawing
    def draw_node_3d(x, y, w, h, title, stereotype, color):
        d = 2.5
        # Top face
        poly_top = Polygon([(x, y+h), (x+d, y+h+d), (x+w+d, y+h+d), (x+w, y+h)],
                           facecolor='#E2E8F0', edgecolor=C_SLATE, lw=1.5)
        # Right face
        poly_rt  = Polygon([(x+w, y), (x+w+d, y+d), (x+w+d, y+h+d), (x+w, y+h)],
                           facecolor='#CBD5E1', edgecolor=C_SLATE, lw=1.5)
        # Front face
        rect_fr  = Rectangle((x, y), w, h, facecolor='#FFFFFF', edgecolor=C_SLATE, lw=1.8)
        ax.add_patch(poly_top)
        ax.add_patch(poly_rt)
        ax.add_patch(rect_fr)

        # Header bar
        hbar = Rectangle((x, y+h-4), w, 4, facecolor=color, edgecolor='none')
        ax.add_patch(hbar)
        ax.text(x+w/2, y+h-1.5, f"«{stereotype}»", ha='center', va='center', fontsize=8, color='#FFFFFF')
        ax.text(x+w/2, y+h-3, title, ha='center', va='center', fontsize=9.5, fontweight='bold', color='#FFFFFF')

    # Artifact box
    def draw_artifact(x, y, w, h, name, desc=""):
        abox = FancyBboxPatch((x, y), w, h, boxstyle="square,pad=0.2",
                              facecolor=C_LIGHT_SLT, edgecolor=C_BORDER, lw=1.2)
        ax.add_patch(abox)
        ax.text(x+1, y+h-1.5, "«artifact»", fontsize=7.5, color=C_SLATE, style='italic')
        ax.text(x+1, y+h-3.2, name, fontsize=8.5, fontweight='bold', color=C_TEXT)
        if desc:
            ax.text(x+1, y+1.2, desc, fontsize=7.5, color=C_SLATE)

    # Node 1: ESP32 Hardware
    draw_node_3d(4, 52, 40, 38, "LilyGO TTGO T-Display ESP32", "device: Edge Hardware", C_AMBER)
    ax.text(6, 84, "Execution Env: FreeRTOS / Arduino Core", fontsize=8.5, fontweight='semibold', color=C_DARK_BLUE)
    draw_artifact(6, 73, 36, 9, "respondo_wearable.bin", "Compiled firmware & ST7789 GUI driver")
    draw_artifact(6, 61, 36, 9, "NVS Preferences Partition", "pwr namespace: is_off, running, stage")
    ax.text(6, 54, "• Tactile Buttons (GPIO 0 & 35)   • Battery ADC (GPIO 34)", fontsize=8, color=C_SLATE)

    # Node 2: Android Phone
    draw_node_3d(4, 5, 40, 42, "Citizen / Officer Smartphone", "device: Mobile Node", C_GREEN)
    ax.text(6, 41, "Execution Env: Android OS 8.0+ / ART Runtime", fontsize=8.5, fontweight='semibold', color=C_DARK_BLUE)
    draw_artifact(6, 30, 36, 9, "respondo-bfp.apk", "Native Java Mobile App & BLE Services")
    draw_artifact(6, 18, 36, 9, "SharedPreferences XML Files", "bfp_user_profile, bfp_prefs, video_cache")
    ax.text(6, 8, "• BLE 4.2/5.0 Radio   • Fused GPS Sensor   • Accelerometer", fontsize=8, color=C_SLATE)

    # Node 3: BFP Dispatch PC
    draw_node_3d(55, 52, 41, 38, "BFP Command Workstation", "device: Desktop PC Node", C_MED_BLUE)
    ax.text(57, 84, "Execution Env: Chromium / Modern Browser", fontsize=8.5, fontweight='semibold', color=C_DARK_BLUE)
    draw_artifact(57, 73, 37, 9, "index.html & theme.css", "Web Dispatch Console & Leaflet GIS")
    draw_artifact(57, 61, 37, 9, "DOM Web Storage", "sessionStorage (lockout) / localStorage")
    ax.text(57, 54, "• Dual Monitor GIS Display   • Siren Audio Synthesizer", fontsize=8, color=C_SLATE)

    # Node 4: Google Cloud Platform
    draw_node_3d(55, 5, 41, 42, "Firebase Multi-Region Cloud", "cloud: Google Cloud Platform", C_RED)
    ax.text(57, 41, "Execution Env: Serverless Managed Infrastructure", fontsize=8.5, fontweight='semibold', color=C_DARK_BLUE)
    draw_artifact(57, 30, 37, 9, "functions/index.js", "Node.js Cloud Function: onSosAlertCreated")
    draw_artifact(57, 18, 37, 9, "Google Cloud Firestore", "sos_alerts, user_profiles, admin_tokens, bfp_stations")
    ax.text(57, 8, "• FCM Push Broadcast Gateway   • Firebase Hosting CDN", fontsize=8, color=C_SLATE)

    # Protocol Connectors
    # BLE between Node 1 & 2
    ax.annotate("", xy=(24, 48), xytext=(24, 52),
                arrowprops=dict(arrowstyle="<->", color=C_AMBER, lw=3))
    ax.text(25.5, 49.5, "Bluetooth Low Energy 4.2 / 5.0 (GATT)", fontsize=8.5, fontweight='bold', color=C_AMBER)

    # Mobile to Cloud
    ax.annotate("", xy=(55, 26), xytext=(45, 26),
                arrowprops=dict(arrowstyle="<->", color=C_GREEN, lw=3))
    ax.text(46, 28, "HTTPS / WSS (TLS 1.3)", fontsize=8.5, fontweight='bold', color=C_GREEN, ha='center')

    # PC to Cloud
    ax.annotate("", xy=(75, 48), xytext=(75, 52),
                arrowprops=dict(arrowstyle="<->", color=C_MED_BLUE, lw=3))
    ax.text(76.5, 49.5, "HTTPS / Firestore Realtime gRPC", fontsize=8.5, fontweight='bold', color=C_MED_BLUE)

    plt.tight_layout()
    plt.savefig('diagrams/deployment_diagram.png', dpi=200, bbox_inches='tight')
    plt.close()
    print("Generated diagrams/deployment_diagram.png")

def draw_erd():
    fig, ax = plt.subplots(figsize=(16, 12), dpi=200)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97.5, "RESPONDO BFP — Database Diagram (ERD)", ha='center', va='center',
            fontsize=20, fontweight='bold', color=C_DARK_BLUE)
    ax.text(50, 95, "Entity-Relationship Model (Firestore Collections, Embedded Maps & Arrays)", ha='center', va='center',
            fontsize=12, color=C_SLATE, style='italic')

    def draw_entity(x, y, w, h, title, fields, color=C_DARK_BLUE):
        # Header
        hdr = FancyBboxPatch((x, y + h - 4.5), w, 4.5, boxstyle="round,pad=0.2",
                             facecolor=color, edgecolor=C_SLATE, lw=1.5)
        ax.add_patch(hdr)
        ax.text(x + w/2, y + h - 2.2, title, ha='center', va='center',
                fontsize=10, fontweight='bold', color='#FFFFFF')
        # Body
        body = FancyBboxPatch((x, y), w, h - 4.5, boxstyle="round,pad=0.2",
                              facecolor='#FFFFFF', edgecolor=C_SLATE, lw=1.5)
        ax.add_patch(body)
        # Fields
        for i, (pkfk, f_name, f_type) in enumerate(fields):
            curr_y = y + h - 6.5 - (i * 2.1)
            if curr_y < y + 1:
                break
            tag_color = C_RED if "PK" in pkfk else (C_MED_BLUE if "FK" in pkfk else C_SLATE)
            ax.text(x + 1, curr_y, pkfk, fontsize=7.5, fontweight='bold', color=tag_color)
            ax.text(x + 4.5, curr_y, f_name, fontsize=8, fontweight='semibold', color=C_TEXT)
            ax.text(x + w - 1, curr_y, f_type, fontsize=7.5, color=C_SLATE, ha='right', style='italic')

    # 1. SOS_ALERTS (Center)
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

    # 2. REPORTER_PROFILE (Left of SOS)
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

    # 3. PATIENTS (Left Lower)
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

    # 4. USER_PROFILES (Top Right)
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

    # 5. ADMIN_USERS (Right Middle)
    admin_fields = [
        ("PK", "uid", "Auth UID"),
        ("  ", "authorized", "Boolean"),
        ("  ", "role", "String"),
        ("FK", "stationId", "References Station"),
    ]
    draw_entity(71, 38, 25, 16, "ADMIN_USERS (Collection)", admin_fields, C_DARK_BLUE)

    # 6. ADMIN_TOKENS (Right Lower)
    token_fields = [
        ("PK", "uid", "Auth UID"),
        ("  ", "fcmToken", "String"),
        ("  ", "updatedAt", "ServerTimestamp"),
    ]
    draw_entity(71, 20, 25, 14, "ADMIN_TOKENS (Collection)", token_fields, C_DARK_BLUE)

    # 7. BFP_STATIONS (Bottom Left)
    station_fields = [
        ("PK", "stationId", "String"),
        ("  ", "name", "String"),
        ("  ", "latitude", "Number"),
        ("  ", "longitude", "Number"),
        ("  ", "address", "String"),
        ("  ", "contactNumber", "String"),
    ]
    draw_entity(36, 17, 28, 20, "BFP_STATIONS (Collection)", station_fields, C_SLATE)

    # Connectors with Crow's Foot notations
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
    plt.savefig('diagrams/database_erd.png', dpi=200, bbox_inches='tight')
    plt.close()
    print("Generated diagrams/database_erd.png")

def draw_dfd_0():
    fig, ax = plt.subplots(figsize=(16, 11), dpi=200)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97, "RESPONDO BFP — Data Flow Diagram (DFD Level 0)", ha='center', va='center',
            fontsize=20, fontweight='bold', color=C_DARK_BLUE)
    ax.text(50, 94, "Context Diagram: Global External Entities & System Inflows/Outflows", ha='center', va='center',
            fontsize=12, color=C_SLATE, style='italic')

    # Central Process Circle
    central = plt.Circle((50, 50), 16, facecolor=C_LIGHT_BLUE, edgecolor=C_DARK_BLUE, lw=3, zorder=3)
    ax.add_patch(central)
    ax.text(50, 53, "0.0", ha='center', va='center', fontsize=16, fontweight='bold', color=C_RED, zorder=4)
    ax.text(50, 48, "RESPONDO BFP\nEmergency Response\n& Dispatch System", ha='center', va='center',
            fontsize=11, fontweight='bold', color=C_DARK_BLUE, zorder=4)

    # External Entities (Rectangles)
    def draw_entity_rect(x, y, w, h, name, icon):
        box = FancyBboxPatch((x, y), w, h, boxstyle="square,pad=0.5",
                             facecolor='#FFFFFF', edgecolor=C_SLATE, lw=2.2, zorder=3)
        ax.add_patch(box)
        ax.text(x + w/2, y + h/2 + 2, icon, ha='center', va='center', fontsize=18)
        ax.text(x + w/2, y + h/2 - 3, name, ha='center', va='center', fontsize=10, fontweight='bold', color=C_TEXT)

    draw_entity_rect(4, 60, 22, 16, "Citizen / Victim", "🧑")
    draw_entity_rect(4, 24, 22, 16, "ESP32 Wearable", "⌚")
    draw_entity_rect(74, 60, 22, 16, "BFP Dispatcher", "🚒")
    draw_entity_rect(74, 24, 22, 16, "FCM Push Gateway", "📡")

    # Directed Flow Lines with text
    def flow_curve(p1, p2, text, rad=0.15, color=C_MED_BLUE):
        ax.annotate("", xy=p2, xytext=p1,
                    arrowprops=dict(arrowstyle="->", connectionstyle=f"arc3,rad={rad}", color=color, lw=2))
        mx = (p1[0] + p2[0])/2
        my = (p1[1] + p2[1])/2 + (rad * 15)
        ax.text(mx, my, text, ha='center', va='center', fontsize=8, fontweight='semibold', color=color,
                bbox=dict(boxstyle="square,pad=0.2", fc="white", ec="none"))

    # Citizen <-> System
    flow_curve((26, 72), (38, 62), "1. SOS Trigger & GPS Fix", 0.1, C_RED)
    flow_curve((26, 66), (36, 56), "2. Medical Profile & RA 10173 Consent", 0.1, C_GREEN)
    flow_curve((38, 48), (26, 60), "3. Dispatch Status Sync ('Deployed')", 0.1, C_MED_BLUE)

    # Wearable <-> System
    flow_curve((26, 36), (38, 42), "4. Hardware Button Panic Signal", 0.1, C_RED)
    flow_curve((26, 30), (36, 38), "5. Battery Voltage & Telemetry", 0.1, C_AMBER)
    flow_curve((42, 35), (26, 24), "6. Status Sync ('Responders En Route')", -0.15, C_DARK_BLUE)

    # System <-> Dispatcher
    flow_curve((62, 60), (74, 72), "7. Real-Time Telemetry & Siren Alarm", 0.1, C_RED)
    flow_curve((64, 52), (74, 64), "8. Patient Vitals & Injury Data", 0.1, C_AMBER)
    flow_curve((74, 58), (62, 46), "9. Unit Deployment & Route Polyline", -0.15, C_GREEN)
    flow_curve((74, 54), (60, 40), "10. Incident Resolution Notes", -0.2, C_SLATE)

    # System <-> FCM
    flow_curve((60, 36), (74, 30), "11. Dispatch Alert Payload", 0.1, C_RED)

    plt.tight_layout()
    plt.savefig('diagrams/dfd_level_0.png', dpi=200, bbox_inches='tight')
    plt.close()
    print("Generated diagrams/dfd_level_0.png")

def draw_dfd_1():
    fig, ax = plt.subplots(figsize=(16, 12), dpi=200)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    ax.text(50, 97.5, "RESPONDO BFP — Data Flow Diagram (DFD Level 1)", ha='center', va='center',
            fontsize=20, fontweight='bold', color=C_DARK_BLUE)
    ax.text(50, 95, "Subsystem Functional Decomposition, Transformations & Firestore Data Stores", ha='center', va='center',
            fontsize=12, color=C_SLATE, style='italic')

    # External Entities
    def draw_ent(x, y, w, h, name):
        box = FancyBboxPatch((x, y), w, h, boxstyle="square,pad=0.2",
                             facecolor='#FFFFFF', edgecolor=C_SLATE, lw=2)
        ax.add_patch(box)
        ax.text(x+w/2, y+h/2, name, ha='center', va='center', fontsize=9.5, fontweight='bold', color=C_TEXT)

    draw_ent(3, 80, 16, 8, "Citizen / Victim")
    draw_ent(3, 62, 16, 8, "ESP32 Wearable")
    draw_ent(81, 70, 16, 8, "BFP Dispatcher")
    draw_ent(81, 20, 16, 8, "FCM Gateway")

    # Processes (Circles)
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

    # Data Stores (Open-ended double lines)
    def draw_store(x, y, w, h, num, name):
        box = Rectangle((x, y), w, h, facecolor='#FFFFFF', edgecolor=C_MED_BLUE, lw=1.5)
        ax.add_patch(box)
        # Vertical divider
        ax.plot([x + 6, x + 6], [y, y + h], color=C_MED_BLUE, lw=1.5)
        ax.text(x + 3, y + h/2, num, ha='center', va='center', fontsize=8, fontweight='bold', color=C_MED_BLUE)
        ax.text(x + 8, y + h/2, name, ha='left', va='center', fontsize=8, fontweight='bold', color=C_TEXT)

    draw_store(38, 65, 24, 4.5, "D1", "sos_alerts")
    draw_store(15, 42, 22, 4.5, "D2", "user_profiles")
    draw_store(72, 45, 22, 4.5, "D3", "admin_users")
    draw_store(72, 38, 22, 4.5, "D4", "bfp_stations")
    draw_store(15, 12, 22, 4.5, "D5", "first_aid")
    draw_store(72, 31, 22, 4.5, "D6", "admin_tokens")

    # Directed Arrows
    def arrow(p1, p2, text="", color=C_SLATE, offset=(0, 1.5)):
        ax.annotate("", xy=p2, xytext=p1,
                    arrowprops=dict(arrowstyle="->", color=color, lw=1.8))
        if text:
            mx, my = (p1[0]+p2[0])/2 + offset[0], (p1[1]+p2[1])/2 + offset[1]
            ax.text(mx, my, text, fontsize=7.5, fontweight='semibold', color=color, ha='center',
                    bbox=dict(boxstyle="square,pad=0.2", fc="white", ec="none"))

    # Flows
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
    plt.savefig('diagrams/dfd_level_1.png', dpi=200, bbox_inches='tight')
    plt.close()
    print("Generated diagrams/dfd_level_1.png")

if __name__ == '__main__':
    print("Generating all diagrams...")
    draw_use_case()
    draw_component()
    draw_deployment()
    draw_erd()
    draw_dfd_0()
    draw_dfd_1()
    print("All diagrams generated successfully!")
