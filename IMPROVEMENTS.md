# CricLive — Improvement Areas & Technical Roadmap

This document provides a comprehensive, multi-tiered analysis of potential enhancements for **CricLive**. Recommendations are categorized into **Critical**, **Major**, **Minor**, and **Trivial** tiers to guide development, production stability, and broadcast polish.

---

## 1. 🔴 Critical Improvements (Stability, Fail-safes & Production Uptime)

Critical improvements address fatal crashes, live stream drops during outdoor broadcasts, memory leaks, and Android OS-level background interruptions.

### 1.1 Dynamic Adaptive Bitrate (ABR) on Network Drop
- **Current State**: Stream bitrate is fixed at 5 Mbps (1080p), 9 Mbps (1440p), or 18 Mbps (4K). While auto-reconnect handles complete drops, sudden bandwidth drops under congested 4G/5G towers cause packet buffering on YouTube.
- **Root Cause**: Outdoor grounds often experience fluctuating cell tower throughput.
- **Proposed Solution**:
  - Implement dynamic bandwidth adaptation using RootEncoder's on-the-fly bitrate stepping (`setVideoBitrateOnFly(bitrate)`).
  - Continuously monitor socket queue delay in `ConnectChecker`. If delay exceeds 800ms, step down bitrate by 30% (e.g. from 5,000 Kbps to 3,500 Kbps).
  - Step back up gradually after 15 seconds of stable transmission.

### 1.2 Android 14+ Foreground Service & MediaProjection Expiry Guard
- **Current State**: `RtmpStreamService` uses `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`.
- **Root Cause**: On Android 14 (API 34), `MediaProjection` permissions granted via `createScreenCaptureIntent()` are single-use and can expire if the service is restarted without a fresh prompt or if the OS enforces strict background execution limits.
- **Proposed Solution**:
  - Cache the `Intent` and `resultCode` safely during the active match session.
  - Implement a `ServiceConnection` binder lifecycle to prevent Android OS process kills when the phone enters low memory conditions under heavy camera heat.
  - Add `FLAG_INCLUDE_STOPPED_PACKAGES` and foreground notification channel with high importance fallback if stream is disrupted.

### 1.3 Thermal Throttle & Sun Exposure Protection
- **Current State**: Streaming 1080p/60 or 4K with CameraX and hardware MediaProjection outdoors under direct sunlight generates high heat, leading to thermal throttling (camera frame rate dropping to 15 FPS or phone auto-shutdown).
- **Proposed Solution**:
  - Register an `Intent.ACTION_POWER_CONNECTED` and `Intent.ACTION_BATTERY_CHANGED` listener with battery temperature check (`EXTRA_TEMPERATURE`).
  - If phone battery temperature exceeds 44°C:
    1. Display an on-screen thermal warning badge (`🔥 Device Overheating: 44.5°C`).
    2. Automatically lower screen brightness to minimum (stream capture continues at full brightness).
    3. Option to auto-step stream resolution down to 1080p 30fps to cool the SoC.

### 1.4 Persistent Browser Scraping Memory Leak Prevention
- **Current State**: In [`cricheroes-service.js`](file:///d:/CricketLive/CricLive/cricheroes-service.js), Playwright maintains a persistent Chromium tab with canvas hooks running for hours during a match.
- **Root Cause**: Extended canvas scraping in Chromium accumulates DOM nodes, listener closures, and context objects over a 4-hour match.
- **Proposed Solution**:
  - Schedule an automated garbage-collection cycle: close and spawn a fresh incognito browser context every 45 minutes or 30 overs.
  - Keep the last known state cached in memory during the 2-second browser reload so the broadcast overlay never experiences a flicker.

---

## 2. 🟡 Major Improvements (Core Broadcast Features & Production Value)

Major improvements deliver significant production capabilities, audio enhancements, multi-platform streaming, and offline integrity.

### 2.1 Direct Digital Audio Mixing (Mic Commentary + Sound Effects)
- **Current State**: Live stream audio records directly from the phone microphone. Event sounds (boundary horn, wicket siren) play through web audio on the speaker, causing ambient feedback or echo.
- **Proposed Solution**:
  - Implement a dual-channel digital audio mixer in Android:
    - **Channel A**: Hardware microphone with noise suppressor (`AudioEffect.EFFECT_TYPE_NS`) and acoustic echo canceller (`AcousticEchoCanceler`).
    - **Channel B**: Direct digital PCM sound effect channel mixing mp3/wav stingers (crowd cheering, boundary music, wicket siren) directly into the RTMP AAC encoder at normalized -6dB.
  - Independent volume sliders in the camera UI for Commentary and Match Sound FX.

### 2.2 Dual Recording: Simultaneous Local MP4 Capture
- **Current State**: Video is pushed directly to YouTube Live via RTMP. If the cellular connection drops in an area without cell coverage, footage during the drop is lost on YouTube.
- **Proposed Solution**:
  - Enable simultaneous local MP4 recording directly to phone storage (`Environment.DIRECTORY_MOVIES/CricLive/`) using RootEncoder's `record(path)`.
  - Ensures a pristine 1080p/4K master recording with baked-in live scores is saved locally on the device regardless of internet drops, allowing immediate post-match highlight editing.

### 2.3 SRT (Secure Reliable Transport) & RIST Protocol Support
- **Current State**: The application streams via RTMP (`rtmp://`).
- **Proposed Solution**:
  - Add SRT (`srt://`) protocol support. SRT uses ARQ (Automatic Repeat reQuest) packet recovery and UDP, making it drastically superior to RTMP over lossy 4G/5G mobile connections with high jitter.
  - Supported natively by YouTube Live, OBS, and vMix.

### 2.4 Multi-Destination Streaming (YouTube + Facebook Live)
- **Current State**: Streams to a single RTMP destination (YouTube).
- **Proposed Solution**:
  - Allow operators to input a secondary RTMP stream URL and key (e.g. Facebook Live or Twitch).
  - Implement dual-socket RTMP dispatch so ground matches can broadcast to both YouTube and the club's Facebook page simultaneously without duplicating encoder workload.

### 2.5 Scraper JSON Schema Validation & Fallback Alerting
- **Current State**: Extracts scores from canvas `fillText` and DOM elements every 4 seconds.
- **Proposed Solution**:
  - Enforce strict JSON schema validation on the scraped payload.
  - If CricHeroes alters frontend classes or canvas layout:
    1. If extraction fails 3 consecutive times, alert the operator with a non-intrusive badge (`⚠️ Reader Syncer Stalled`).
    2. Automatically fall back to manual score adjustment mode without terminating the live video stream.

### 2.6 Dynamic Run-Rate & Manhattan Over-by-Over Charts
- **Current State**: Only numerical current over bubbles are shown.
- **Proposed Solution**:
  - Add an operator toggle for an "Over Summary" bar chart (Manhattan graph) between overs or during drinks breaks, displaying runs scored per over for both teams.

---

## 3. 🟢 Minor Improvements (UI/UX Polish & Operational Convenience)

Minor improvements refine operator workflow, visual presentation, and match ergonomics.

### 3.1 Broadcast Stinger Transitions & Celebration Wipes
- **Current State**: Boundary banners appear as floating rounded cards for 3.5 seconds.
- **Proposed Solution**:
  - Implement high-energy TV broadcast stinger wipes (full-screen angled bar sweep with sound effect) when:
    - Batter completes a half-century (50) or century (100).
    - Team score reaches milestones (50, 100, 150, 200).
    - Maiden over is completed.

### 3.2 Player Profile Photos / Avatar Badges
- **Current State**: Striker and bowler are represented by text names only.
- **Proposed Solution**:
  - Display circular player portrait thumbnails or jersey numbers next to batter and bowler names when available from the CricHeroes scorecard.

### 3.3 Hardware Volume Buttons as Physical Shutter & Zoom Remotes
- **Current State**: Operators must tap the screen to cycle camera zoom or trigger boundaries.
- **Proposed Solution**:
  - Intercept hardware key events in `MainActivity`:
    - `Volume Up`: Cycle zoom presets (1x -> 2x -> 3x -> 1x).
    - `Volume Down`: Trigger instant boundary animation or dismiss banners.
  - Compatible with cheap Bluetooth shutter selfie remotes clamped to the tripod handle.

### 3.4 Automatic "Do Not Disturb" (DND) Mode During Streaming
- **Current State**: Incoming WhatsApp calls, SMS banners, or notification heads-up can pop up over the screen while streaming.
- **Proposed Solution**:
  - Request `NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`.
  - Automatically activate `INTERRUPTION_FILTER_PRIORITY` or `INTERRUPTION_FILTER_NONE` upon starting the stream and restore normal phone notifications when the stream stops.

### 3.5 Detailed Bowler Spell Tracker
- **Current State**: Displays bowler's total match figures (`overs`, `maidens`, `runs`, `wickets`, `eco`).
- **Proposed Solution**:
  - Add spell-specific tracking (e.g. *"Current Spell: 2.0-0-8-1"* alongside overall figures *"Match: 4.0-0-26-2"*).

---

## 4. ⚪ Trivial Improvements (Micro-interactions & Code Hygiene)

Trivial improvements consist of small aesthetic touches, feedback sounds, and minor cleanup items.

### 4.1 Haptic Feedback on Camera & UI Toggles
- Provide gentle vibration (`VibrationEffect.EFFECT_CLICK`) when:
  - Tapping zoom preset buttons (`1x`, `2x`, `4x`).
  - Engaging / Disengaging Tripod Touch Guard (`🔒 Lock`).
  - Tapping `⏸ Break Screen`.

### 4.2 Score Number Ticker Micro-Animation
- Add CSS flip/roll animation or smooth counter increment when runs update (e.g. `128` smoothly rolls to `132` upon hitting a four).

### 4.3 Automatic Clipboard URL Sanitization
- When pasting CricHeroes URLs into the control panel or mobile sync dialog, auto-strip extraneous tracking parameters (e.g. `?utm_source=whatsapp&ref=share`).

### 4.4 Tooltips on Control Panel Buttons
- Add clean micro-tooltips over control panel icon buttons to assist first-time match scorers.

### 4.5 Clean Up Unused Deprecations in Kotlin Code
- Replace `Preview.Builder.setTargetAspectRatio(AspectRatio.RATIO_16_9)` with `ResolutionSelector.Builder` in CameraX 1.3+ to eliminate compile-time warnings.

---

## 5. Comprehensive Prioritization & Implementation Matrix

| Tier | Improvement Area | Key Benefit | Impact | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Critical** | Dynamic Adaptive Bitrate (ABR) | On-the-fly encoder step down prevents buffering on congested 4G/5G | 🔴 Crucial | ✅ **Implemented** |
| **Critical** | Stream Auto-Reconnect with Exponential Backoff | Recovers gracefully from mobile tower drops (up to 5 retries) | 🔴 Crucial | ✅ **Implemented** |
| **Critical** | Device Thermal Protection & Warning HUD | Real-time battery temperature monitor + alert badge prevents shutdowns | 🔴 Crucial | ✅ **Implemented** |
| **Critical** | Android 14+ Foreground Life-cycle Guard | Eliminates OS process kills during long matches | 🔴 Crucial | ✅ **Implemented** |
| **Critical** | Scraper Context Memory Recycle | Prevents backend browser crashes on 4+ hour games | 🔴 Crucial | ✅ **Implemented** |
| **Major** | Tripod Touch Guard / Screen Lock Shield | Locks all gestures, preventing accidental tripod/sweat touches | 🟡 High | ✅ **Implemented** |
| **Major** | Privacy Shield / Broadcast Break Screen | One-tap intermission screen prevents personal phone leaks | 🟡 High | ✅ **Implemented** |
| **Major** | DLS Rain Interruption Target Calculation | Dedicated DLS target calculation and `TARGET (DLS)` display | 🟡 High | ✅ **Implemented** |
| **Major** | Commercial Partner Marquee Carousel | Rotating sponsor badge on scorebar every 12 seconds | 🟡 High | ✅ **Implemented** |
| **Major** | Direct Digital Audio Mixing (Mic + FX) | Pristine broadcast sound without room echo | 🟡 High | 📋 Planned |
| **Major** | Concurrent Local Master MP4 Recording | Protects match footage if cellular drops | 🟡 High | 📋 Planned |
| **Major** | SRT Protocol Streaming Ingest | Ultra-stable transmission over lossy mobile networks | 🟡 High | 📋 Planned |
| **Major** | Multi-Destination Streaming (YT + FB) | Expands audience to Facebook & YouTube | 🟡 High | 📋 Planned |
| **Major** | Manhattan & Run-Rate Charts | Television-style interval analysis | 🟡 High | 📋 Planned |
| **Minor** | Hardware Volume / Bluetooth Shutter Remote | Tripod-friendly zoom & break toggle without touching phone | 🟢 Medium | ✅ **Implemented** |
| **Minor** | Auto "Do Not Disturb" (DND) Guidance | Alerts operator to silence notifications before going live | 🟢 Medium | ✅ **Implemented** |
| **Minor** | Broadcast Stinger Celebration Wipes | Adds high-energy TV graphics to 50s and 100s | 🟢 Medium | 📋 Planned |
| **Minor** | Player Avatar / Portrait Badges | Personalizes batter and bowler statistics | 🟢 Medium | 📋 Planned |
| **Minor** | Detailed Bowler Spell Tracker | Differentiates current spell from match totals | 🟢 Medium | 📋 Planned |
| **Trivial** | Haptic Feedback on Preset Taps | Physical confirmation for outdoor operators | ⚪ Polish | ✅ **Implemented** |
| **Trivial** | Score Ticker Micro-Roll Animation | Premium visual feel on boundary scoring | ⚪ Polish | 📋 Planned |
| **Trivial** | Clipboard Auto-Sanitizer | Saves operator time when pasting match URLs | ⚪ Polish | 📋 Planned |
| **Trivial** | Control Panel Micro-Tooltips | Better onboarding for non-technical scorers | ⚪ Polish | 📋 Planned |
