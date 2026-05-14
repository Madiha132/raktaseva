# 🩸 JeevaBindu — Blood Donor Network

**JeevaBindu** is an Android application that connects blood donors with people in urgent need, enabling real-time emergency alerts, donor registration, and health eligibility tracking.

---

## 📱 Features

### 🏠 Home
- Hero banner with live donor & active SOS stats
- Quick-action shortcuts to Emergency, Find Donors, and Health tabs
- Blood compatibility reference card (universal donor/recipient info)

### 📋 Donor Registration
- Register with name, phone, age, blood group, and location
- OTP-based phone verification (simulated popup; production-ready hook)
- Validates age range (18–65) and phone format before saving
- Data saved to Firebase Firestore

### 🚨 Emergency Alert
- Post urgent blood requests with blood group, hospital, units needed, and contact number
- Broadcasts SMS to the emergency contact and **all registered donors**
- Fires a push notification via Firebase Cloud Messaging (FCM)
- Simulated phone lock-screen alert popup appears 5 seconds after posting
- Donors can tap **"I'm On My Way"** to mark an alert as covered

### 👥 Donor Directory
- Browse all registered donors with filter by blood group
- Displays name, location, age, phone, and registration date

### 💉 Health Tracker
- **90-Day Eligibility Checker** — pick your last donation date to instantly see if you're eligible to donate again
- Donation tips (hydration, diet, rest, weight requirements)

---

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Android Views (programmatic, no XML) |
| Database | Firebase Firestore |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| SMS (primary) | Native Android `SmsManager` |
| SMS (fallback) | [Fast2SMS API](https://fast2sms.com) (internet-based, no SIM needed) |

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- Android SDK 26+
- A Firebase project with Firestore and FCM enabled
- (Optional) A [Fast2SMS](https://fast2sms.com) account for the SMS fallback

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-org/jeevabindu.git
   cd jeevabindu
   ```

2. **Connect Firebase**
   - Go to [Firebase Console](https://console.firebase.google.com) → Add project
   - Register your Android app with the package name `com.example.raktaseva`
   - Download `google-services.json` and place it in the `app/` directory
   - Enable **Firestore Database** and **Cloud Messaging** in the Firebase console

3. **Configure SMS fallback** *(optional but recommended)*
   Open `MainActivity.kt` and replace the placeholder with your Fast2SMS API key:
   ```kotlin
   private val FAST2SMS_KEY = "YOUR_FAST2SMS_API_KEY_HERE"
   ```
   Get a free key at [fast2sms.com](https://fast2sms.com) (100 free SMS/day).

4. **Build & run**
   ```bash
   ./gradlew assembleDebug
   ```
   Or press ▶️ **Run** in Android Studio.

---

## 🔑 Required Permissions

Declared in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

`SEND_SMS` is requested at runtime on first launch. If denied, the app automatically falls back to the Fast2SMS API.

---

## 🗄️ Firestore Data Model

### `donors` collection
| Field | Type | Description |
|---|---|---|
| `name` | String | Full name |
| `phone` | String | 10-digit mobile number |
| `bloodGroup` | String | e.g. `A+`, `O-` |
| `location` | String | City / town |
| `age` | Int | Donor age |
| `registeredDate` | String | Date of registration |

### `alerts` collection
| Field | Type | Description |
|---|---|---|
| `alertNumber` | Int | Auto-incremented ID |
| `bloodGroup` | String | Blood group required |
| `hospital` | String | Hospital name |
| `units` | Int | Units needed (1–10) |
| `contact` | String | Emergency contact number |
| `timestamp` | String | Alert creation date |
| `donorComing` | Boolean | `true` once a donor accepts |

---

## 📡 SMS Dispatch Logic

```
sendSms(phone, message)
    │
    ├─ SEND_SMS permission granted?
    │       ├── YES → sendViaNativeSms()  ──── success? → done
    │       │                             └── fail?    → sendViaFast2Sms()
    │       └── NO  → sendViaFast2Sms()  (Fast2SMS REST API over internet)
```

Phone numbers are normalised to E.164 (`+91XXXXXXXXXX`) before dispatch.

---

## 🔔 Notifications

- A **Firestore trigger** (via `FirestoreHelper`) auto-increments alert numbers
- FCM topic `blood_alerts` is subscribed on app launch — all users receive push alerts
- A local notification with high priority fires immediately on the posting device
- A simulated **lock-screen phone overlay** animates in after 5 seconds to demo the donor experience

---

## 🩺 Eligibility Rule

Donors must wait **90 days** between donations. The Health tab calculates:
- Days since last donation
- Exact eligibility date
- Whether the donor is currently eligible (green) or must wait (amber)

---

## 📂 Project Structure

```
app/src/main/java/com/example/raktaseva/
├── MainActivity.kt       # All UI panels & navigation
├── FirestoreHelper.kt    # Firestore read/write abstraction
├── DonorModel.kt         # Data class for donors
└── AlertModel.kt         # Data class for emergency alerts
```

---

## 🤝 Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you'd like to change.

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

> *"Every drop counts. Be someone's hero today."*
