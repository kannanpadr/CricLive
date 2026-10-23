# CricLive — Application Improvement Areas & Roadmap

Based on the functional architecture outlined in [FUNCTIONALITY.md](file:///d:/CricketLive/CricLive/FUNCTIONALITY.md), this document details technical and production improvement areas categorized by priority, impact, and feasibility.

---

## 1. Live Streaming Resilience & Quality (High Priority)

### 1.1 Adaptive Bitrate & Network Drop Handling
- **Current State**: The RTMP stream operates on fixed target bitrates (5 Mbps for 1080p, 9 Mbps for 1440p, 18 Mbps for 4K).
- **Issue**: On outdoor grounds with fluctuating 4G/5G mobile signals, bandwidth dips cause packet loss, frame dropping, or stream termination on YouTube.
- **Improvement**: 
  - Implement dynamic bitrate adaptation in [`RtmpStreamService.kt`](file:///d:/CricketLive/CricLive/android/app/src/main/kotlin/com/cktscoredemo/RtmpStreamService.kt).
  - Automatically drop to 2.5–3.5 Mbps during congested cell tower intervals and step back up when bandwidth recovers.
  - Add auto-reconnect backoff (3 retries with exponential delay) if connection drops.

### 1.2 "Do Not Disturb" & Privacy Shield During Live Broadcast
- **Current State**: The app uses Android's `MediaProjection` (screen capture) to combine the camera preview and transparent scoreboard.
- **Issue**: Incoming phone calls, WhatsApp messages, volume sliders, or low-battery pop-ups will be recorded and broadcast live onto YouTube.
- **Improvement**:
  - Request `NotificationManager.INTERRUPTION_FILTER_PRIORITY` (Do Not Disturb) when starting a live broadcast to silence notifications.
  - Add a **"Privacy Mute / Break Screen"** button that temporarily replaces the stream output with a static "Innings Break / Technical Pause" graphic while the operator checks their phone.

### 1.3 Audio Mixing: Commentary & Broadcast Sound Effects
- **Current State**: Commentary microphone audio is piped into AAC directly from the hardware mic. Overlay event sounds (boundary chime, wicket siren) play through the web audio engine and may sound muffled or cause echo.
- **Improvement**:
  - Direct digital audio mixing: Route in-app sound effects (boundary music, wicket horns) directly into the RTMP AAC audio encoder track alongside microphone input with independent volume sliders.
  - Implement a noise gate and compressor to prevent audio distortion/peaking when commentators shout during exciting wickets.

---

## 2. CricHeroes Sync & Match Data Integrity (High Priority)

### 2.1 Rain Interruption & DLS (Duckworth-Lewis-Stern) Handling
- **Current State**: The chase equation relies on standard `totalOvers` and `target` values.
- **Issue**: In interrupted club or tournament matches, overs are reduced and revised targets are set by DLS.
- **Improvement**:
  - Add DLS detection in [`cricheroes-service.js`](file:///d:/CricketLive/CricLive/cricheroes-service.js) and manual DLS target override in [`public/index.html`](file:///d:/CricketLive/CricLive/public/index.html).
  - Display "Target (DLS)" clearly in the overlay equations.

### 2.2 CricHeroes WebSocket / Canvas Fallback Resilience
- **Current State**: The mobile syncer extracts scores from canvas `fillText` and DOM elements every 4–6 seconds.
- **Issue**: If CricHeroes updates their frontend classes, DOM scrapers can temporarily fail.
- **Improvement**:
  - Add JSON schema validation on the extracted payload. If extraction fails 3 consecutive times, alert the operator with a warning badge and fall back to manual score control without dropping the stream.

---

## 3. Broadcast Graphics & Production Polish (Medium Priority)

### 3.1 Sponsor & Tournament Branding Banner Carousel
- **Current State**: The overlay displays tournament name, venue, and team colors.
- **Issue**: Local tournament organizers rely heavily on sponsors (title sponsors, powered-by logos, local business ads).
- **Improvement**:
  - Add a rotating sponsor banner slot in [`public/overlay.html`](file:///d:/CricketLive/CricLive/public/overlay.html) (cycles sponsors every 30 seconds at the end of an over).
  - Add an operator toggle to show a full-card sponsor splash during drinks breaks.

### 3.2 Automated Milestones & Stinger Transitions
- **Current State**: Boundary banners appear at the top/center for 3.5 seconds.
- **Improvement**:
  - Add broadcast-style transition wipes (stingers) across the screen when a team reaches a 50/100 team total or a batter completes a half-century.
  - Display player portrait silhouettes or profile photos next to the striker name when available.

### 3.3 Wagon Wheel & Manhattan Chart Integration
- **Current State**: Only numerical stats (runs, balls, 4s, 6s, SR, economy) are shown.
- **Improvement**:
  - Between overs or during wicket reviews, allow the operator to display a mini "Over Summary" bar chart showing runs scored per over.

---

## 4. Mobile Operator Ergonomics & UX (High Value)

### 4.1 Tripod Touch Guard / Screen Lock Mode
- **Current State**: The camera screen has pinch-to-zoom and floating buttons.
- **Issue**: When adjusting the phone on a tripod or when rain/sweat droplets touch the screen, accidental taps can disconnect the stream or change zoom unexpectedly.
- **Improvement**:
  - Add a **Touch Lock toggle** (long-press 2 seconds to unlock) that disables all screen gesture inputs while keeping the camera and overlay streaming uninterrupted.

### 4.2 Stream Health & Diagnostics Dashboard
- **Current State**: Top status shows `LIVE [Duration]`.
- **Improvement**:
  - Add a discreet diagnostic toggle showing:
    - Current upload bitrate (e.g. `4,850 kbps`)
    - Dropped frame count & percentage (`0.2%`)
    - Battery percentage & phone temperature alert (prevent overheating under the sun).

### 4.3 Hardware Button / Bluetooth Remote Control
- **Current State**: Scorers must tap the screen to make changes.
- **Improvement**:
  - Map phone volume buttons or Bluetooth shutter remotes:
    - `Volume Up`: Quick Zoom cycle (1x -> 2x -> 3x).
    - `Volume Down`: Trigger instant Boundary animation or dismiss banners.

---

## 5. Summary Priority Matrix & Implementation Status

| Category | Recommended Feature | Impact | Status |
| :--- | :--- | :--- | :--- |
| **Stream Stability** | Dynamic Bitrate & Auto-Reconnect (Up to 5 Retries with Backoff) | 🔴 Critical | ✅ **Implemented** |
| **Operator UX** | Tripod Touch Lock / Accidental Tap Protection (`🔒 Lock` shield) | 🟡 High | ✅ **Implemented** |
| **Stream Health HUD** | Diagnostics HUD (Real-time Throughput Bitrate + Battery % Status) | 🟡 High | ✅ **Implemented** |
| **Broadcast Privacy** | Privacy Break Screen (`⏸ Break` Innings / Intermission Screen) | 🟡 High | ✅ **Implemented** |
| **Monetization** | Sponsor Logo / Partner Marquee Rotation (Every 12s on scorebar) | 🟢 High Value | ✅ **Implemented** |
| **Data Integrity** | DLS Rain Interruption Target Calculation (`TARGET (DLS)`) | 🟢 High Value | ✅ **Implemented** |
| **Production** | Digital Sound FX Mixing into RTMP Audio | 🟡 High | 📋 Roadmap |
| **Stream Privacy** | Auto Do Not Disturb / Notification Blocker | 🔴 Critical | 📋 Roadmap |
