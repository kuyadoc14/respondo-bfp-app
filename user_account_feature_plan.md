# 🔐 Optional User Account & Bystander Mode — Feature Plan

## Context

Currently, when a user presses SOS:
- A random `userId` is generated locally (`pwa_user_<timestamp>` or `app_user_<timestamp>`)
- The Accident Report Sheet pops up immediately, asking for patient details (name, age, address, etc.)
- **No user identity is saved** — the user must manually fill in everything each time

### Problem
If the **user themselves** is the victim (alone, panicked, injured), they can't fill in their own details. A pre-saved profile would auto-populate their info so BFP dispatchers immediately know **who** they are.

---

## 🏗️ Architecture Design

### New Firestore Collection: `user_profiles/{uid}`

```json
{
  "fullName": "Juan Dela Cruz",
  "age": 28,
  "birthdate": "1998-03-15",
  "address": "123 Rizal St, Barangay Centro, Lipa City",
  "phone": "+63 917 123 4567",
  "emergencyContact": "+63 918 987 6543",
  "emergencyContactName": "Maria Dela Cruz",
  "allergies": "Penicillin",
  "bloodType": "O+",
  "medicalConditions": "Asthma",
  "createdAt": "<server_timestamp>",
  "updatedAt": "<server_timestamp>"
}
```

### Firestore Rules Addition

```
match /user_profiles/{uid} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
```

---

## 🧠 Core UX Flow — "Are you the victim or a bystander?"

When SOS is pressed and the alert is sent, **before the Accident Report Sheet**, show an **intermediary question**:

```
┌─────────────────────────────────────────────┐
│                                             │
│   🔥 SOS Alert Sent Successfully            │
│                                             │
│   Are you involved in this emergency?       │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │  🙋 I AM the victim                  │  │
│  │  Auto-fill my saved profile as P1     │  │
│  └───────────────────────────────────────┘  │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │  👥 I am a BYSTANDER                 │  │
│  │  I'll fill in victim details manually │  │
│  └───────────────────────────────────────┘  │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │  ⏭️ Skip Report                      │  │
│  │  Just send the alert                  │  │
│  └───────────────────────────────────────┘  │
│                                             │
└─────────────────────────────────────────────┘
```

### Behavior per choice:

| Choice | What Happens |
|--------|-------------|
| **"I am the victim"** | Opens AccidentReportSheet with Patient 1 **auto-filled** from saved profile. User can still edit. Their profile data is also attached to the SOS alert as `reporterProfile`. |
| **"I am a bystander"** | Opens AccidentReportSheet normally (blank). A toggle/checkbox appears: **"Include my contact info for BFP follow-up?"** — if checked, their name & phone are saved to the alert as `reporterName` / `reporterPhone`, but NOT as a patient. |
| **"Skip Report"** | Dismisses the sheet. Report can be filled later via the "Report" button. |

---

## 📱 Changes Required

### 1. Android App (Java)

| File | Change |
|------|--------|
| **`ProfileFragment.java`** | Add user profile form (name, age, birthdate, address, phone, emergency contact, allergies, blood type, medical conditions). Save/load from Firestore `user_profiles` collection. Add Firebase Auth sign-up/sign-in for civilian users (email+password or phone auth). |
| **`SosFragment.java`** | After SOS is sent, show the role selection dialog (victim/bystander/skip) before the AccidentReportSheet. |
| **`AccidentReportSheet.java`** | Accept optional pre-fill data. If "I am the victim" was selected and user is logged in, auto-populate Patient 1 fields from the saved profile. If "bystander" was selected and user opted in, attach reporter info to the alert. |
| **New: `RoleSelectionDialog.java`** | A dialog that asks "Are you the victim or a bystander?" after SOS is sent. |
| **New: `layout/dialog_role_selection.xml`** | Layout for the role selection dialog. |
| **New: `layout/fragment_profile_user.xml`** | User profile editing layout within the Profile tab. |
| **`activity_main.xml` / `MainActivity.java`** | Profile tab now shows user account section at the top (sign in/out, profile info). |
| **`firestore.rules`** | Add `user_profiles` collection rules. |

### 2. PWA / SOS Page (`sos.html`)

| Change | Description |
|--------|-------------|
| **Add optional login UI** | Small "Sign In" link/button on the SOS page. Uses Firebase Auth (email/password). Not required — the SOS button works without login. |
| **Profile auto-fill** | If logged in, the user's saved profile is fetched. When SOS is pressed, shows the same role selection (victim/bystander/skip). |
| **Reporter info on alert** | If user is logged in and selects "bystander" + opts in, their name/phone is attached to the `sos_alerts` document. |

### 3. Admin Dashboard (`index.html`)

| Change | Description |
|--------|-------------|
| **Show reporter info** | In the table's "Caller / Device" column, show reporter name instead of raw userId when available. |
| **Report modal** | Show "Reporter Profile" section if `reporterProfile` data exists on the alert — name, phone, blood type, allergies, etc. |

---

## 📊 Updated Alert Document Structure

```json
{
  "userId": "abc123",
  "latitude": 14.1152,
  "longitude": 122.9577,
  "timestamp": "<server>",
  "status": "active",
  "source": "android",
  "deviceToken": "",

  // NEW — Reporter identity (if logged in)
  "reporterName": "Juan Dela Cruz",
  "reporterPhone": "+63 917 123 4567",
  "reporterRole": "victim" | "bystander",
  
  // NEW — Full profile snapshot (if victim + logged in)
  "reporterProfile": {
    "fullName": "Juan Dela Cruz",
    "age": 28,
    "bloodType": "O+",
    "allergies": "Penicillin",
    "medicalConditions": "Asthma",
    "emergencyContact": "+63 918 987 6543",
    "emergencyContactName": "Maria Dela Cruz"
  },

  // Existing report data
  "accidentType": "Medical",
  "countOfVictims": 1,
  "patients": [...]
}
```

---

## 🔒 Privacy Considerations

- Login is **100% optional** — SOS always works without an account
- User can **choose not to share** their details when acting as a bystander (opt-in checkbox)
- Profile data is only readable by the user themselves (`request.auth.uid == uid`)
- Profile snapshot on the alert is only readable by admin (existing rules)
- Users can delete their profile at any time

---

## 📋 Implementation Order

1. **Firestore rules** — Add `user_profiles` collection
2. **Android: Profile tab** — User sign-up/sign-in + profile form  
3. **Android: Role Selection Dialog** — Victim/Bystander/Skip prompt
4. **Android: AccidentReportSheet** — Auto-fill support  
5. **Android: SosFragment** — Integrate role dialog + attach reporter info
6. **PWA: sos.html** — Optional login + role selection + profile auto-fill
7. **Admin: index.html** — Display reporter info in table + modal

> [!IMPORTANT]
> This plan covers both Android (Java) and Web (PWA + Admin). Should I proceed with implementation? If so, which parts should I start with?
