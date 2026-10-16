  # CKTScore — Software Requirements Specification (SRS)

**Document ID:** CKTSCORE-SRS-001  
**Version:** 1.0 — Draft for review  
**Date:** 15 October 2026  
**Primary platform:** Native Android application  
**Scope:** Cricket scoring, CricHeroes URL-based score extraction, camera/scoreboard composition, and live streaming

---

## Document Control

| Version | Date | Description |
|---|---|---|
| 1.0 | 15 Oct 2026 | Initial draft covering native Android scoring, URL-based score extraction, overlay composition, cloud video ingest, OBS, and direct viewers. |

## 1. Introduction

### 1.1 Purpose

This Software Requirements Specification (SRS) defines requirements for evolving CKTScore into a single native Android application that can load match data from a CricHeroes URL, manage scoring, display the scoreboard over live phone-camera video, and publish a live stream for OBS production and direct mobile/web viewing.

### 1.2 Scope

The solution will reuse the existing Node.js/Express scoring service and its URL-based extraction flow initially. A native Android app will provide match setup, scoring controls, camera capture, graphics composition, stream controls, and status monitoring. A remote media service will receive and distribute the composed video because remote viewers cannot reliably connect directly to a phone behind mobile-network NAT.

### 1.3 Goals

- Preserve the existing CricHeroes URL-to-scorecard workflow.
- Reuse the current score model, score calculations, manual ball entry, undo, overlay settings, and event animations where practical.
- Provide one Android app for match operation, camera capture, overlay composition, and publishing.
- Support a single phone camera over mobile data or other internet connections.
- Support OBS as an optional production endpoint and direct HLS playback for viewers.
- Improve reliability, persistence, access control, and operational observability before production release.

### 1.4 Definitions and Acronyms

| Term | Definition |
|---|---|
| CKTScore | Existing cricket live-score and OBS overlay project. |
| SRS | Software Requirements Specification. |
| Overlay | Transparent score graphics rendered over video. |
| Ingest | Receiving a live video stream from the Android publisher. |
| RTMPS | RTMP protected by TLS, used for publishing where supported. |
| HLS | HTTP Live Streaming playback format. |
| OBS | Open Broadcaster Software used to compose and broadcast video. |
| CricHeroes extraction | Fetching and parsing match data from a supplied CricHeroes URL. |
| MVP | Minimum viable product. |

### 1.5 References and Source Basis

- User-provided CKTScore project description: Node.js/Express server, `public/index.html` operator panel, `public/overlay.html` OBS overlay, `matchState` in memory, `/api/score`, `/api/auto-configure`, `/api/overlay-settings`, `/api/trigger-event`, `/api/config`, `/api/ball`, `/api/undo`, and CricHeroes scraping service.
- Requirements clarified in conversation: Android only; one phone camera; mobile data/internet; single native app experience; video composition and streaming; support both OBS production and direct/mobile viewing.

> **Source limitation:** The supplied project description does not include the actual source code for `cricheroes-service.js`, the full package manifest, or the HTML/JavaScript implementation. Requirements depending on those details must be validated during discovery.

## 2. Overall Description

### 2.1 Product Perspective

The product consists of four logical subsystems:

1. **Native Android operator app:** UI, match selection, scoring controls, camera/audio capture, graphics composition, publishing, and stream health.
2. **CKTScore API/backend:** URL-based score extraction, match state, score calculations, manual scoring endpoints, overlay configuration, and stream-session APIs.
3. **Media service:** Authenticated video ingest, stream routing, HLS packaging/playback, and a compatible input for OBS. The media server is separate from the scoring API.
4. **Viewer and production clients:** OBS can combine the incoming camera stream with the existing browser overlay; direct viewers can watch HLS with score data displayed in the player.

### 2.2 Product Operating Modes

| Mode | Description |
|---|---|
| Score setup | Operator enters a CricHeroes URL and initializes match data using the existing extraction flow. |
| Score control | Operator records ball-by-ball events, extras, wickets, corrections, and undo actions. |
| Live broadcast | App captures camera/audio, composes score graphics into the outgoing video, encodes it, and publishes it. |
| OBS production | OBS receives the camera/program feed and may use the existing overlay as a browser source. Avoid double-overlaying if the app has already burned graphics into the feed. |
| Direct viewing | Viewer plays the composed stream using HLS; a web/mobile viewer may also receive score events separately if a separate overlay is desired. |

### 2.3 User Classes

| User | Responsibilities |
|---|---|
| Operator / scorer | Set up match, manage score, start/stop stream, monitor network and stream status. |
| Viewer | Watch a live match and scoreboard using a shared viewing link. |
| Administrator | Manage accounts, match permissions, publishing credentials, service configuration, and operational access. |

### 2.4 Assumptions and Dependencies

- An Android phone with a supported camera, microphone, hardware video encoding capability, and a reasonably stable internet uplink is available.
- The existing Node.js service can be deployed at a stable HTTPS address reachable from the phone.
- CricHeroes pages/data remain accessible to the extraction mechanism and their terms and technical controls permit the intended access. This must be verified; scraping is not guaranteed to remain stable.
- A streaming endpoint or media server will be provisioned. The first implementation may use a self-hosted RTMP-compatible media server with HLS output, subject to codec and OBS compatibility testing.
- The first release supports one camera and one active stream per match. Multi-camera production is out of scope.
- Exact target Android API levels, phone models, expected concurrent viewers, retention needs, and streaming destination accounts remain to be agreed.

## 3. System Architecture

### 3.1 Logical Architecture

| Component | Responsibilities | Initial implementation |
|---|---|---|
| Android app | Native UI, match setup, score controls, camera preview/capture, graphics, encoding/publishing, connection status. | Kotlin, Jetpack Compose, CameraX; native graphics/encoding pipeline. |
| CKTScore API | Match state, CricHeroes extraction, score calculations, manual ball updates, undo, overlay settings. | Existing Node.js/Express service, hardened and extended. |
| Database | Persistent matches, score events, users/permissions, stream session metadata. | PostgreSQL recommended for hosted deployment; migration is required because current state is in memory. |
| Media service | Authenticated ingest, stream routing, HLS output, OBS-compatible source. | MediaMTX or equivalent, validated in a proof of concept. |
| Viewer | HLS playback and optional score overlay using API/WebSocket data. | Responsive web viewer first; native viewer may be added if required. |
| OBS | Optional studio composition and output broadcast. | Existing OBS browser overlay retained where applicable. |

### 3.2 Video Flow

1. Android captures camera video and microphone audio.
2. The native composition pipeline draws CKTScore graphics and event animations over video frames.
3. The encoder produces a supported video/audio stream (initial target: H.264 video and AAC audio, subject to device support).
4. The app publishes the composed stream to an authenticated cloud ingest endpoint over a supported protocol such as RTMPS.
5. The media service makes the stream available to OBS and packages it for direct HLS playback.
6. The app independently sends scoring actions to the CKTScore API; score state is not transported in the video API.
7. If OBS uses the already-composed program feed, it must not add a second copy of the same scoreboard. Alternatively, OBS may receive clean camera video and add the existing browser overlay, but this is a distinct production mode.

### 3.3 Score Flow

1. Operator enters the CricHeroes URL in the Android app.
2. App submits the URL to `POST /api/auto-configure`.
3. Backend extracts available match details and updates `matchState`.
4. App retrieves current state from `GET /api/score` and displays the score.
5. Manual scoring actions call `POST /api/ball`; undo calls `POST /api/undo`.
6. The overlay renderer uses the latest accepted score state and applies settings/animations.
7. For production, persisted state and push updates should replace reliance on in-memory state and frequent polling alone.

## 4. Functional Requirements

Priority labels: **Must** = required for the MVP; **Target** = measurable objective to validate.

### 4.1 Authentication and Match Access

- **FR-001 [Must]:** The system shall require an authenticated operator before allowing score-changing actions or creating a stream session.
- **FR-002 [Must]:** The backend shall authorize each operator for the match they attempt to modify.
- **FR-003 [Must]:** The app shall provide a match list and a way to create or resume a match.
- **FR-004 [Must]:** The app shall preserve the existing match metadata, team information, innings, overs, and overlay settings supported by CKTScore.

### 4.2 CricHeroes URL Import

- **FR-010 [Must]:** The app shall allow the operator to enter or paste a CricHeroes match URL.
- **FR-011 [Must]:** The app shall submit the URL to the existing `POST /api/auto-configure` endpoint or its secured successor.
- **FR-012 [Must]:** The app shall show loading, success, and actionable failure states for invalid URLs, inaccessible pages, extraction failures, and incomplete match data.
- **FR-013 [Must]:** The backend shall validate and normalize the submitted URL and restrict outbound requests to approved hosts to reduce server-side request forgery risk.
- **FR-014 [Must]:** The system shall identify the last successful extraction time and indicate when the score data is stale.
- **FR-015 [Must]:** The system shall support manual correction or manual scoring if automatic extraction is unavailable.

### 4.3 Score Management

- **FR-020 [Must]:** The app shall display teams, runs, wickets, overs, innings, and the additional score fields provided by the existing score API.
- **FR-021 [Must]:** The operator shall be able to record supported ball events including runs, wickets, wides, and no-balls, subject to the existing scoring model.
- **FR-022 [Must]:** The system shall recalculate derived statistics such as CRR, RRR, required runs, balls remaining, and strike rates according to the existing logic.
- **FR-023 [Must]:** The operator shall be able to undo the last eligible manual score change.
- **FR-024 [Must]:** Score-changing actions shall use unique request/event identifiers or equivalent idempotency protection to prevent duplicate scoring after retries.
- **FR-025 [Must]:** The system shall record an auditable timestamp and operator identity for score events.
- **FR-026 [Target]:** The app shall visibly distinguish automatic source updates from operator-entered corrections where practical.

### 4.4 Overlay and Graphics

- **FR-030 [Must]:** The app shall render a scoreboard over live camera video without requiring OBS to be running.
- **FR-031 [Must]:** The overlay shall support the key fields and visual settings already exposed by CKTScore, including team scores, wickets, overs, colours, layout, visibility, and supported animations.
- **FR-032 [Must]:** The overlay renderer shall preserve a configurable safe area and readable text at the selected output resolution.
- **FR-033 [Must]:** The system shall avoid displaying duplicate score graphics when the already-composed program feed is used in OBS.
- **FR-034 [Must]:** The app shall show a local preview of the composed output before or during publishing.
- **FR-035 [Must]:** The system shall define behavior for stale score data, such as displaying a stale indicator or preserving the last confirmed score according to configuration.

### 4.5 Camera, Audio, and Encoding

- **FR-040 [Must]:** The app shall provide camera preview and allow selection of a supported rear camera.
- **FR-041 [Must]:** The app shall request and handle camera and microphone permissions explicitly.
- **FR-042 [Must]:** The app shall capture audio from the selected microphone and provide a mute option.
- **FR-043 [Must]:** The app shall compose camera frames and scoreboard graphics into the outgoing video stream.
- **FR-044 [Must]:** The app shall use hardware-accelerated encoding when available and fall back gracefully or report unsupported devices.
- **FR-045 [Must]:** The app shall offer configurable streaming profiles, initially targeting 720p/30 fps and a lower-bandwidth profile such as 480p.
- **FR-046 [Must]:** The app shall detect and communicate camera, microphone, encoder, thermal, and publishing errors.
- **FR-047 [Must]:** The app shall keep the operator informed when background/foreground transitions may affect camera or microphone operation.

### 4.6 Live Publishing and Session Management

- **FR-050 [Must]:** The app shall request a stream session from the backend before publishing.
- **FR-051 [Must]:** The backend shall issue a unique, revocable or expiring publishing credential for each stream session.
- **FR-052 [Must]:** The app shall publish the composed video to a configured compatible media ingest endpoint.
- **FR-053 [Must]:** The app shall display stream states including preparing, connecting, live, reconnecting, failed, and ended.
- **FR-054 [Must]:** The app shall allow the operator to start and stop publishing explicitly.
- **FR-055 [Must]:** The app shall retry transient network failures with bounded exponential backoff and shall not create duplicate sessions on retry.
- **FR-056 [Must]:** The system shall expose stream health information where available, including bitrate, dropped frames, reconnects, and last-seen publisher time.
- **FR-057 [Must]:** The backend shall expire inactive sessions and revoke or invalidate publishing credentials when a stream ends.

### 4.7 Viewer and OBS Integration

- **FR-060 [Must]:** The media service shall expose a configured stream path that OBS can consume using a tested compatible source/protocol.
- **FR-061 [Must]:** The system shall provide an HLS playback URL or viewer page for direct mobile/web viewing.
- **FR-062 [Must]:** The viewer shall show the live video and either the composed scoreboard or a separately rendered score overlay, according to the selected mode.
- **FR-063 [Must]:** The system shall apply an explicit public/private access policy to viewer links.
- **FR-064 [Must]:** The solution shall document whether the published program includes the scoreboard, to prevent accidental double overlay in OBS.
- **FR-065 [Must]:** The system shall display a viewer-facing unavailable/offline state when the stream is not active.

### 4.8 Persistence and Recovery

- **FR-070 [Must]:** The backend shall persist match state and score events so a server restart does not erase the match.
- **FR-071 [Must]:** The system shall recover the latest valid match state after restart from persistent storage.
- **FR-072 [Must]:** The system shall retain sufficient score-event history to support the agreed undo and audit policy.
- **FR-073 [Target]:** The app may queue score actions locally during temporary API loss, but it shall clearly mark them as pending and reconcile them safely when connectivity returns.
- **FR-074 [Must]:** The app shall not silently claim a queued score action has been accepted by the server until acknowledgment is received.
- **FR-075 [Target]:** Local recordings, if enabled, shall be stored according to an explicit retention and storage-space policy.

## 5. External Interface Requirements

### 5.1 Existing API Compatibility

| Existing route | Method | Purpose / expected use |
|---|---|---|
| `/api/auto-configure` | POST | Submit CricHeroes URL and initialize/refresh match data. |
| `/api/score` | GET | Retrieve enriched current match state. |
| `/api/ball` | POST | Record a ball-by-ball scoring action. |
| `/api/undo` | POST | Undo the last eligible state change. |
| `/api/config` | POST | Configure match metadata and teams. |
| `/api/overlay-settings` | POST | Update overlay presentation settings. |
| `/api/trigger-event` | POST | Trigger a supported temporary overlay animation. |
| `/api/reset-reference` | POST | Existing demo/reset endpoint; disable or restrict outside development. |

These routes are based on the supplied project description. Exact payload schemas, status codes, authentication, and response structures must be verified against the actual source code before implementation.

### 5.2 Proposed New API

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/auth/login` | POST | Authenticate operator. |
| `/api/matches` | GET/POST | List/create matches. |
| `/api/matches/{id}` | GET | Fetch a match. |
| `/api/matches/{id}/score-events` | POST | Submit a score event with idempotency key. |
| `/api/matches/{id}/live` | WebSocket | Push score and status changes. |
| `/api/matches/{id}/stream-sessions` | POST | Create stream session and return short-lived publishing details. |
| `/api/stream-sessions/{id}` | GET | Read stream session status. |
| `/api/stream-sessions/{id}/end` | POST | End session and invalidate publishing credentials. |

### 5.3 Mobile Interfaces

- The app shall communicate with the scoring API over HTTPS.
- The app shall publish video using a protocol supported by both the selected Android publishing implementation and the configured media server.
- The app shall use Android camera/microphone APIs and platform-compliant permission and foreground-service behavior.
- The app shall not embed permanent API secrets or administrator credentials.

## 6. Non-Functional Requirements

### 6.1 Performance

- **NFR-001 [Must]:** The app should display the local camera preview with no perceptible control delay under supported device conditions.
- **NFR-002 [Target]:** Score actions should be reflected in the operator UI immediately and acknowledged by the backend within 2 seconds under normal test-network conditions.
- **NFR-003 [Target]:** The initial stream profile should target 720p at 30 fps where supported; the app must expose a lower profile for constrained uplinks.
- **NFR-004 [Must]:** The system shall define and measure end-to-end glass-to-glass and score-update latency during acceptance testing; a final numeric target must be agreed after the media pipeline is selected.
- **NFR-005 [Must]:** Viewer capacity and expected concurrent audience shall be load-tested and documented before public launch.

### 6.2 Reliability and Availability

- **NFR-010 [Must]:** The app shall detect publishing interruptions and communicate the current state rather than presenting a false live indicator.
- **NFR-011 [Must]:** The backend shall persist accepted score changes transactionally.
- **NFR-012 [Must]:** The app shall recover from temporary API or ingest loss without duplicating accepted scoring events.
- **NFR-013 [Must]:** The system shall be field-tested across representative mobile networks, including weak and fluctuating uplinks.

### 6.3 Security and Privacy

- **NFR-020 [Must]:** All public API and publishing endpoints shall use encrypted transport.
- **NFR-021 [Must]:** The backend shall authenticate users and authorize match and stream actions.
- **NFR-022 [Must]:** Publishing credentials shall be scoped, time-limited, and protected from unnecessary exposure in logs.
- **NFR-023 [Must]:** CORS shall be restricted to approved browser origins; CORS shall not be treated as an authentication control.
- **NFR-024 [Must]:** URL extraction shall validate hostnames, redirect destinations, response sizes, timeouts, and content types to mitigate SSRF and resource-exhaustion risks.
- **NFR-025 [Must]:** Logs shall not contain passwords, long-lived secrets, or full publishing tokens.
- **NFR-026 [Must]:** The product shall define recording consent, retention, access control, and deletion behavior before enabling cloud or local recording.

### 6.4 Compatibility and Maintainability

- **NFR-030 [Must]:** The app shall declare and test its supported Android OS range and device capability baseline before release.
- **NFR-031 [Must]:** The app shall use modular boundaries between scoring, source-data import, camera capture, composition, publishing, and viewer functions.
- **NFR-032 [Must]:** The existing Node.js code shall be covered by regression tests before significant refactoring.
- **NFR-033 [Must]:** The build and deployment process shall document environment configuration, database migrations, media-server setup, and rollback procedures.

### 6.5 Observability

- **NFR-040 [Must]:** The backend shall log extraction failures, score update failures, authentication failures, and stream-session lifecycle events.
- **NFR-041 [Must]:** Operational monitoring shall track API health, extraction freshness, active publishers, stream failures, and media-server resource use.
- **NFR-042 [Must]:** The Android app shall provide a diagnostics view that can be shared without exposing secrets.

## 7. Data Requirements

### 7.1 Core Entities

| Entity | Minimum fields |
|---|---|
| Match | `match_id`, `source_url`, tournament, team details, overs, innings, current state, status, created/updated timestamps. |
| Score event | `event_id`, `match_id`, event type, runs, extras, wicket details, event order, `operator_id`, timestamp, idempotency key, correction/undo reference. |
| Stream session | `session_id`, `match_id`, ingest path identifier, status, created/started/ended timestamps, health summary, access policy. |
| User / permissions | `user_id`, authentication identifier, role, permitted match IDs, status. |
| Overlay configuration | `match_id` or `profile_id`, theme, colours, layout, visibility flags, animation preferences. |

### 7.2 Persistence Rules

- Accepted scoring events must be durable before the API reports success.
- The current match state must be recoverable from stored state and/or the ordered score-event history.
- Undo must create an auditable correction or use a defined reversible-event policy; destructive deletion should not silently erase audit history.
- Stream session records must not store reusable plaintext publishing secrets.

## 8. Error Handling and Edge Cases

| Scenario | Required behavior |
|---|---|
| Invalid or unsupported CricHeroes URL | Show validation error; do not modify the active match unless the operator confirms a new match. |
| Source site changes or blocks extraction | Report extraction failure and last successful update; allow manual scoring. |
| Score API unreachable | Keep the last confirmed score visible with a stale/offline indicator; mark queued actions as pending. |
| Duplicate score request | Use idempotency protection and return the original result rather than applying the score twice. |
| Ingest connection lost | Show reconnecting/offline state, reduce bitrate if appropriate, and retry within configured limits. |
| App process killed or phone overheats | Stop or recover gracefully where possible; never falsely report live status. Document Android lifecycle constraints. |
| Microphone permission denied | Allow video-only mode if supported and clearly show audio is unavailable. |
| OBS and direct viewer both enabled | Ensure media routing and overlay mode are configured so graphics are not unintentionally duplicated. |
| Server restart | Restore match data from persistent storage; report stream session interruption and require/recover publishing according to session policy. |

## 9. Acceptance Criteria

1. Given a valid supported CricHeroes URL, when the operator submits it, the app displays the match data returned by the backend and indicates extraction status.
2. Given a configured match, when the operator records a ball event, the score and derived statistics update correctly and are persisted.
3. Given a previously accepted score action, when the operator retries the same request after a timeout, the score is not counted twice.
4. Given camera and microphone permission, when the operator starts a stream, the app previews and publishes camera video with the scoreboard visibly composited into the outgoing feed.
5. Given a live ingest, OBS can receive the configured stream and produce an output without an unintended duplicate overlay.
6. Given an active stream, a remote viewer on a separate internet connection can play the HLS feed and see the expected scoreboard.
7. Given a temporary uplink interruption, the app reports the interruption and attempts recovery without claiming uninterrupted streaming.
8. Given an application/server restart, accepted match data remains available from persistent storage.
9. Given an unauthorized user or invalid stream credential, score mutations and publishing are rejected.
10. Given stale or failed CricHeroes extraction, the app communicates the issue and allows the configured manual scoring fallback.

## 10. Delivery Plan

| Phase | Scope | Exit criteria |
|---|---|---|
| Phase 0 — Discovery | Inspect actual source, verify CricHeroes extraction, define Android/device baseline, choose publishing SDK and media server. | Approved technical spike and verified endpoint schemas. |
| Phase 1 — Backend foundation | Secure API, add persistence, migrations, authentication, score-event idempotency, and tests. | Existing score workflow passes regression tests with persistent state. |
| Phase 2 — Native scoring app | Build Android navigation, URL import, match screen, score controls, undo, and overlay settings. | Operator can run a match from Android without the web control panel. |
| Phase 3 — Camera and composition | Camera/audio capture, overlay renderer, encoder, stream session API, and publisher. | Composed 720p test stream reaches the media server from mobile data. |
| Phase 4 — OBS and direct viewing | OBS input, HLS playback, viewer page, stream access policies. | OBS and remote viewer both receive the intended output. |
| Phase 5 — Hardening | Field testing, network adaptation, lifecycle, thermal, security, monitoring, documentation. | Acceptance criteria pass on agreed devices and networks. |

Indicative planning estimate discussed in conversation: approximately **6–10 weeks** for a focused MVP with an experienced team, subject to source-code review, SDK choice, integration complexity, and field-test results. This is not a committed delivery schedule.

## 11. Risks and Open Questions

| Item | Risk / question | Action |
|---|---|---|
| CricHeroes access | Scraping behavior may change, be blocked, or be restricted by terms. | Validate permitted access and extraction stability; retain manual scoring fallback. |
| Missing implementation details | Actual `cricheroes-service.js` and API schemas were not included in the supplied description. | Inspect repository and create API contract/tests before coding. |
| Video publishing SDK | Camera capture libraries do not automatically provide a complete network publisher. | Run a proof of concept for Android encoder + RTMPS/SRT compatibility. |
| Mobile uplink | 720p streaming may not be sustainable on weak mobile networks. | Add profiles, bitrate adaptation, and field testing. |
| Viewer scale and cost | Audience size and video retention are unspecified. | Define expected concurrent viewers, recording needs, and hosting budget. |
| Direct vs OBS overlay | A composed feed can duplicate graphics if OBS also overlays the scoreboard. | Provide explicit clean-camera and composed-program modes or standardize one mode. |
| Android support | Minimum Android version and target handset range are not specified. | Agree device matrix and test background/camera behavior. |
| Source-data rights | The terms and technical permissions for extracting CricHeroes data are unverified. | Confirm allowed use before production deployment. |

## 12. Out of Scope for Initial MVP

- Multiple simultaneous camera angles.
- Advanced replay, slow motion, graphics packages, and multi-scene production control.
- Automated ball/event recognition from video.
- A full multi-tenant tournament management platform.
- Guaranteed frame-accurate synchronization between independently delivered score events and HLS video.
- A complete migration of the CricHeroes scraper to Kotlin; this remains a later option after dependency review.

## Appendix A — Existing CKTScore Features to Preserve

| Feature from supplied description | Preservation plan |
|---|---|
| CricHeroes URL auto-configuration | Retain `POST /api/auto-configure` and its extraction service initially. |
| In-memory `matchState` | Replace with persistent storage and a recoverable state model. |
| Derived statistics | Reuse and regression-test `recalculateStats` and related helpers. |
| Manual ball updates | Expose through native scoring UI and existing/secured API. |
| Undo stack | Preserve operator undo semantics; improve audit/persistence. |
| Overlay settings | Carry theme, colours, layout, visibility, and opacity behavior into the native renderer. |
| Animations | Preserve supported FOUR, SIX, and milestone events. |
| OBS browser overlay | Retain as an optional browser source, particularly for clean-camera OBS production mode. |
| Polling and stale detection | Retain as a fallback while adding clearer freshness indicators and event-driven updates. |

## Appendix B — Suggested Initial Streaming Profile

| Parameter | Initial target (to validate) |
|---|---|
| Video resolution | 1280 × 720 |
| Frame rate | 30 fps |
| Video codec | H.264, subject to device and destination compatibility |
| Video bitrate | 2.5–4 Mbps normal profile; 0.8–1.5 Mbps fallback |
| Audio codec | AAC where supported by selected publisher/media pipeline |
| Publishing | RTMPS to authenticated ingest; evaluate SRT if network testing warrants it |
| Viewer delivery | HLS initially; WebRTC may be considered later if lower latency is a requirement |
| Network headroom | Target sustained upload capacity comfortably above configured aggregate bitrate; verify in field tests. |

These are starting values for prototyping, not guaranteed performance requirements. Final settings depend on the chosen SDK, media server, handset, network, and destination.
