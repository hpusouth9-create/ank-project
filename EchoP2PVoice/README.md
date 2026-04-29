# Echo — P2P Voice Calling Android App

A fully serverless peer-to-peer voice calling Android app using **WebRTC** for audio and **Supabase Realtime** for signaling only. Your voice never touches any server.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        SIGNALING PHASE                          │
│                    (Supabase Realtime only)                      │
│                                                                  │
│   Device A (AAA-111)          Device B (BBB-222)                │
│   ─────────────────           ─────────────────                 │
│   1. Subscribe to             1. Subscribe to                   │
│      channel "user:AAA-111"      channel "user:BBB-222"         │
│                                                                  │
│   2. User types "BBB-222"                                       │
│      sendCallRequest ────────────────────────────────────────► │
│                                                                  │
│   3. createOffer() ─── SDP Offer ──────────────────────────────►│
│                  ◄──────────────── SDP Answer ── createAnswer() │
│   4. ICE candidates ◄──────────────────────────► ICE candidates │
│                                                                  │
└──────────────────────────────┬──────────────────────────────────┘
                               │ Supabase exits the picture
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                        P2P AUDIO PHASE                          │
│                  (WebRTC SRTP — direct device to device)        │
│                                                                  │
│   Device A ══════════════════════════════════════ Device B      │
│             Encrypted Opus audio, no server relay               │
└─────────────────────────────────────────────────────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Audio P2P | WebRTC (`stream-webrtc-android`) |
| Signaling | Supabase Realtime Broadcast |
| Dependency Injection | Hilt |
| Architecture | MVVM + StateFlow |
| NAT Traversal | Google public STUN servers |

---

## Project Structure

```
app/src/main/java/com/p2pvoice/
├── P2PVoiceApp.kt                  # Hilt application class
├── MainActivity.kt                 # Entry point, permission handling, navigation
├── AppModule.kt                    # Hilt DI bindings
├── CallForegroundService.kt        # Keeps call alive when app is backgrounded
│
├── signaling/
│   └── SupabaseSignalingManager.kt # Supabase Realtime channels, SDP/ICE relay
│
├── webrtc/
│   └── WebRTCManager.kt           # PeerConnection, audio tracks, ICE, SDP
│
├── utils/
│   └── UserIdManager.kt           # Peer ID generation & persistence
│
└── ui/
    ├── MainViewModel.kt            # Central state management
    ├── theme/
    │   └── Theme.kt                # Color palette, Material3 theme
    └── screens/
        ├── HomeScreen.kt           # Your ID display + dial pad
        ├── IncomingCallScreen.kt   # Accept/reject incoming call
        └── CallScreens.kt         # Outgoing (connecting) + Active call UI
```

---

## Setup Instructions

### Step 1 — Create a Supabase Project

1. Go to [supabase.com](https://supabase.com) and create a free account
2. Click **New Project**
3. Choose a name (e.g. `echo-p2p-voice`) and a strong database password
4. Select the region closest to your users
5. Wait ~2 minutes for the project to spin up

### Step 2 — Enable Realtime

1. In your Supabase dashboard, go to **Database → Replication**
2. Under **Supabase Realtime**, ensure it is **enabled**
3. Go to **Settings → API**
4. Copy your **Project URL** and **anon public key** — you'll need these next

### Step 3 — Configure the Android App

Open `app/build.gradle` and replace the placeholder values:

```groovy
buildConfigField "String", "SUPABASE_URL", "\"https://YOUR_PROJECT_ID.supabase.co\""
buildConfigField "String", "SUPABASE_ANON_KEY", "\"YOUR_ANON_KEY_HERE\""
```

Or better — store them in `local.properties` (never commit secrets to git):

```properties
# local.properties
SUPABASE_URL=https://your-project-id.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

Then in `build.gradle` read from local.properties:
```groovy
def localProps = new Properties()
localProps.load(new FileInputStream(rootProject.file("local.properties")))

buildConfigField "String", "SUPABASE_URL", "\"${localProps['SUPABASE_URL']}\""
buildConfigField "String", "SUPABASE_ANON_KEY", "\"${localProps['SUPABASE_ANON_KEY']}\""
```

### Step 4 — Build and Run

```bash
# Clone and open in Android Studio
# Sync Gradle → Build → Run on device or emulator

# Minimum requirements:
# - Android API 26+ (Android 8.0)
# - Physical device recommended for microphone testing
# - Two devices to test calling
```

---

## How to Make a Call

```
Device A                              Device B
────────                              ────────
1. Open app                           1. Open app
2. See your ID: "ABCD-1234"           2. See your ID: "WXYZ-5678"
3. Share your ID with Device B        
                                      3. Type "ABCD-1234" in the dial box
                                      4. Tap "Start Call"
4. Incoming call screen appears
5. Tap Accept ✓
══════════════ Both now talking P2P ══════════════
```

---

## Privacy & Security

| Concern | Detail |
|---|---|
| **Audio privacy** | Voice travels directly device-to-device via WebRTC SRTP (encrypted). Supabase never sees audio. |
| **Signaling privacy** | SDP and ICE candidates pass through Supabase Realtime briefly. They contain your public IP (via STUN) but no audio. |
| **Data retention** | Supabase Realtime Broadcast is ephemeral — nothing is stored in the database. |
| **Peer ID** | Random 8-char alphanumeric. No name, phone number, or email required. |
| **Encryption** | WebRTC uses DTLS-SRTP for end-to-end audio encryption by default. |

### To further enhance privacy:
- Add end-to-end encryption of SDP payloads before sending to Supabase
- Use ephemeral IDs that expire after each call
- Self-host Supabase on your own server for full data control

---

## Key Dependencies

```groovy
// Supabase (signaling)
implementation platform('io.github.jan-tennert.supabase:bom:1.4.7')
implementation 'io.github.jan-tennert.supabase:realtime-kt'

// WebRTC (P2P audio)
implementation 'io.getstream:stream-webrtc-android:1.1.5'

// Jetpack Compose (UI)
implementation platform('androidx.compose:compose-bom:2023.10.01')

// Hilt (dependency injection)
implementation 'com.google.dagger:hilt-android:2.48'
```

---

## How Peer IDs Work

```kotlin
// Generated on first app launch, persisted in SharedPreferences
// Format: XXXX-XXXX using unambiguous characters (no 0, O, I, 1)
// Characters: ABCDEFGHJKLMNPQRSTUVWXYZ23456789
// Example IDs: "KMPX-73QA", "BH2N-VRZW", "7Q3A-XYMP"

// Entropy: 32^8 = 1,099,511,627,776 possible IDs
// Collision probability with 1M users: ~0.00009% — practically zero
```

---

## Signaling Flow Detail

```
1. CALL REQUEST
   A → Supabase channel "user:B" → B
   {type: "call_request", senderId: "AAA-111", receiverId: "BBB-222"}

2. SDP OFFER (A creates, sends to B)
   {type: "offer", payload: {sdp: "v=0 o=- 46117...", sdpType: "offer"}}

3. SDP ANSWER (B creates, sends to A)
   {type: "answer", payload: {sdp: "v=0 o=- 88234...", sdpType: "answer"}}

4. ICE CANDIDATES (exchanged both ways, multiple messages)
   {type: "ice_candidate", payload: {sdpMid: "0", candidate: "candidate:1..."}}

5. P2P CONNECTION ESTABLISHED — Supabase no longer involved

6. CALL END (either side)
   {type: "call_end"}
```

---

## Limitations & Known Issues

| Issue | Detail |
|---|---|
| **Symmetric NAT** | If both devices are behind strict symmetric NAT (rare), direct P2P may fail. Add a TURN server to fix this. |
| **Background calls** | The foreground service keeps calls alive, but aggressive battery optimization on some devices may kill it. |
| **No push notifications** | If the receiver's app is closed, they won't see the call. Add FCM for background call delivery. |
| **Single active call** | App supports one call at a time — no call waiting or conferencing. |

---

## Adding TURN Server (Optional, for strict NAT)

If calls fail on mobile networks, add a TURN server to `WebRTCManager.kt`:

```kotlin
private val iceServers = listOf(
    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
    // Add TURN for fallback relay (use your own or a service like Metered/Twilio)
    PeerConnection.IceServer.builder("turn:your-turn-server.com:3478")
        .setUsername("your-username")
        .setPassword("your-password")
        .createIceServer()
)
```

Free TURN options:
- **Metered.ca** — free tier available
- **Open Relay** — `openrelay.metered.ca`
- **Self-hosted** — coturn on a $5 VPS

---

## License

MIT — use freely, build something great.
