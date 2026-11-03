# Dual SIM Bands — final implementation specification

Version: 1.0 (planning document)
Status: implementation subject to repository audit and Pixel 10a capability tests
Product name: **Dual SIM Bands**

Display name (user decision): **Dual SIM Bands** since 2026-10-22 (briefly "Dual SIM Bands for 5G" on 2026-10-21). The project folder and Gradle project are named DualSIMBands since 2026-10-22.

## 1. Purpose and scope
Extend the existing carrier-display-name Android app into a dual-SIM cellular network observer and carrier-display-name manager. Preserve its existing functions and data. Primary target: Google Pixel 10a, non-root Android, actual installed API level to be verified. Existing Shizuku integration remains optional for supported privileged actions. No new package/applicationId, signing identity, or destructive migration.

### Product goals
1. Edit and restore per-SIM carrier display names reliably, with truthful result reporting.
2. Display independently for each subscription: network registration, displayed technology, observed LTE/NR state, radio channels, frequency, signal metrics, and available cell identity.
3. Distinguish 5G availability indicators from verified NR connection, and detected cells from verified carrier aggregation components.
4. Show data while Wi-Fi is active when Android permits; show provenance and age.
5. Optional ongoing notification and CSV history, without making persistent monitoring a prerequisite for basic app use.

## 2. Existing app preservation and migration
- Audit current settings, Shizuku calls, saved carrier-name defaults, apply/restore paths, subscription selection, lifecycle, and failure messages.
- Keep applicationId and existing signing certificate for upgrade; changing launcher label to Dual SIM Bands is allowed. Do not change package namespace merely for rebranding.
  - Superseded on 2026-10-21 by the user: applicationId and namespace are now `io.github.fortuneoflab.dualsimbands`; settings from the earlier application ID are intentionally not migrated.
- Determine existing versionName/versionCode from repository, then increment versionCode; do not assume 0.1.3 is the actual installed version.
- Persist a per-SIM *desired override state* and original/restorable display value or reliable platform default restoration strategy. Do not assume setting a string to empty restores default. Verify by reading back effective state; distinguish write success from UI display propagation.
- A disabled override must restore the original/default display using a tested, supported method. If unsupported, clearly explain and retain a recoverable state; never claim success.
- Subscription IDs can change after reboot, SIM swap, or eSIM reprovisioning. Use a privacy-preserving stable mapping if available; otherwise require explicit remapping rather than applying a label to the wrong SIM. Do not store ICCID/IMSI unencrypted or include them in logs.
- Shizuku unavailable/denied: read-only features continue where possible. No reboot auto-restore promise until the actual Shizuku startup behavior and user consent are verified. Deferred retry only when the service is available and Android allows it; no polling loop that drains battery.
- Verify existing save error and unchecked-override-not-restored bugs against actual code and device logs.

## 3. Data and terminology
For each SIM/subscription track:
- subscriptionId (runtime only), slot index, carrier/operator label, MCC/MNC if available, default-data/default-voice status, mobile data state, registration state, timestamp, and source.
- **Display technology**: TelephonyDisplayInfo and the platform's display override, if available.
- **Observed radio**: voice/data RAT from ServiceState/TelephonyManager; LTE/NR serving and registered CellInfo where accessible.
- **NR status**: verified NR active, NR availability indicated, NR not confirmed, unknown/unsupported. Do not map an API's `NOT_RESTRICTED` to `CONNECTED`.
- **CA status**: verified active PCC/SCC only when an authorized data source explicitly supports it; otherwise LTE-CA indication or unknown, with no unverified PCC/SCC claim.
- Provenance enum: VERIFIED, INDICATED, DERIVED, UNKNOWN, UNSUPPORTED; separately track timestamp and STALE. `DERIVED` for exact standard frequency arithmetic is not the same as speculative radio state.

### State model examples
- Display 5G; data RAT LTE; NR status unknown => `5G indicated · LTE observed · NR unconfirmed`.
- Display 4G+; data RAT LTE; CA composition inaccessible => `LTE-CA indicated · CA component carriers unconfirmed`.
- Verified NR secondary link (only if privileged source demonstrably available) => `NR active (NSA)`; standalone NR registration if confirmed => `NR SA`.
- Speedtest performance is not evidence for or against NR activity. No conclusion about congestion or CA composition based solely on throughput.

## 4. LTE channel and frequency
For every observed LTE cell display, when available:
- serving/registered/neighbor/unknown role; Band; EARFCN; downlink center MHz; uplink center MHz where derivable; bandwidth MHz; PCI; TAC; CI; RSRP/RSRQ/RSSI/RSSNR; observation time and source.
- Use Android CellIdentityLte fields, CellInfoLte and CellSignalStrengthLte, and platform constants for unavailable values; do not treat INT_MAX or missing values as valid measurements.
- Calculate downlink: `F_DL = F_DL_low + 0.1*(N_DL - N_Offs-DL)` MHz using a maintained, validated 3GPP TS 36.101 band table.
- Calculate FDD uplink only if band duplex configuration and channel mapping support it. For TDD label shared frequency and time-division duplexing; do not fabricate an independent uplink channel.
- An EARFCN can correspond to overlapping bands; prefer the reported band, and mark any frequency-only band inference as ambiguous when appropriate.
- `CellIdentityLte.getBandwidth()` is not guaranteed to exist or be populated on all supported devices. Preserve unknown bandwidth.
- Include automated tests for B1 EARFCN 100 = 2120.0 MHz downlink, B21 EARFCN 6525 = 1503.4 MHz downlink, boundary values, TDD bands, overlapping bands, and unknown/unavailable EARFCN.

## 5. NR channel and frequency
For observed NR cells, show NR-ARFCN, NR Band (if reported or unambiguously derivable), center frequency MHz, bandwidth when actually available, PCI, TAC/NCI where permitted, SS-RSRP/SS-RSRQ/SS-SINR, CSI metrics where supported.
- Use the NR global raster from 3GPP TS 38.104, with frequency-range-dependent steps and offsets; validate against authoritative table fixtures. Avoid a single fixed conversion step for all NR-ARFCNs.
- Do not equate NR cell detection with an active NSA secondary connection. CellInfoNr may be absent on NSA even when NR is used; state `not observable`, not `disconnected`.
- NR band and channel bandwidth can be unavailable/ambiguous; no guessing from displayed `5G` icon.

## 6. LTE carrier aggregation
- Separate `observed cells` from `verified serving component carriers`. The former may include neighbor cells and repeated B42 frequencies.
- Attempt access to physical channel and CA details only after checking Android public API access, required permissions, Android version, and whether the data is genuinely per-subscription.
- Do not assert PhysicalChannelConfig is generally available to third-party apps. Shizuku does not automatically grant privileged telephony permissions or system UID.
- If only 4G+ display is known, show `LTE-CA indicated; PCC/SCC unknown`. Never sum bandwidths of merely detected cells.
- If verified PCC/SCC is available, list role, EARFCN, band, frequency, bandwidth and active state, with verified aggregate bandwidth.

## 7. Dual SIM and Wi-Fi
- Per-subscription TelephonyManager created with `createForSubscriptionId`; update on active subscription and default-data changes; unregister callbacks when no longer needed.
- Do not merge cells from different subscriptions into one CA grouping. If callback/API cannot attribute cells reliably, label subscription unknown and do not assign them.
- Show default-data SIM and separate non-data SIM. When Wi-Fi is default network, retain available cellular registration and measurements, and indicate that cellular data may be idle.
- Handle radio off, airplane mode, missing permissions, no service, eSIM changes, stale callback data, and SIM removal.

## 8. UI / UX
Tabs or bottom navigation: **Radio**, **SIM Labels**, **History**, **Settings**.
- Radio: one card per SIM; carrier, slot, data SIM marker, displayed 5G/4G+, actual RAT, NR status, frequency and signal summary. Expand to cell table with band, EARFCN/NR-ARFCN, MHz, bandwidth, signal metrics, source, age and role. Do not claim `primary band` without verified serving-cell attribution.
- SIM Labels: existing workflow; per-SIM override toggle, custom text, apply, restore/default, actual readback, Shizuku status, actionable errors.
- History: opt-in session logs and CSV export using Android document picker/share mechanism; avoid exposing subscriber IDs.
- Settings: monitor toggle, notification toggle, polling preference, history retention, permissions/status, diagnostic export, app/version details.
- UI must be readable in Japanese, support dark mode, and distinguish `未確認` from `未接続` and `取得不可`.
- No status-bar icon spoofing. Notification is an app notification, not a replacement for system 5G icon.

## 9. Notification and background work
- Opt-in persistent notification showing default-data SIM, displayed/observed RAT, confirmed band/frequency if known, and NR status.
- Expanded notification may show signal, multiple verified carriers, and timestamp. Avoid claiming the status is live if it is stale.
- Event-driven callbacks first; optional foreground refresh ~1s only if supported and efficient. Background 5–10s is a *target*, not guaranteed: obey Doze, OS throttling, foreground service restrictions, and battery optimization.
- Before adding foreground services, identify valid foreground-service type and Android permission policy for the actual target SDK; request only legally supported permissions. If continuous background monitoring cannot be implemented compliantly, degrade to event-driven / while-open updates.
- Android 13+ notification runtime permission where applicable. No hidden background work when the user turns monitoring off.

## 10. Logging, privacy, permissions
- Default logging OFF. Store timestamp, subscription alias, RAT/display/NR state, band, channel, MHz, signal, verified CA components, data provenance and age.
- Opt-in separate capture for cell IDs/TAC/location-adjacent fields; configurable retention and delete-all.
- Export CSV with schema version, UTF-8 BOM if needed for Excel, clear units and `unknown`/`unsupported` values.
- Request only permissions actually required by implemented APIs: READ_PHONE_STATE and location permissions may be relevant to CellInfo; confirm behavior against OS version, app-ops, location services, and permission denial. POST_NOTIFICATIONS only if notifications enabled. Shizuku permission only for the existing privileged workflow.
- Do not request READ_PRIVILEGED_PHONE_STATE as if a normal third-party app can obtain it.

## 11. Implementation architecture (adapt to actual repository)
- `SubscriptionRepository`: subscription discovery and identity mapping.
- `CarrierLabelRepository`: overrides, original/default restoration, persistence.
- `ShizukuGateway`: explicit privileged operations and failure semantics.
- `RadioRepository`: per-subscription callbacks and timestamped observations.
- `FrequencyCalculator`: versioned 3GPP band/raster tables and pure unit-tested math.
- `CaAnalyzer`: verified versus merely indicated CA and component role.
- `RadioStateReducer`: provenance-aware displayed/observed radio state.
- `NotificationController`: opt-in presentation and lifecycle.
- `HistoryRepository`: opt-in local logs, retention and CSV export.
Use existing language, UI toolkit, DI and persistence framework unless there is a compelling documented reason to migrate.

## 12. Phases and acceptance criteria
### Phase 0 — audit (no code edits)
Inventory modules, build targets, target SDK, actual existing app version, signing, Shizuku flows, label restore bug, dual SIM logic, and feasible Android API/permission matrix. Report findings, risks, tests, and file-level plan. Stop for approval.
### Phase 1 — radio observation and calculators
Build per-SIM read-only model and LTE/NR channel conversion. Unit tests pass; absent permissions and sentinel values handled; no existing label regression.
### Phase 2 — radio UI and verified state
Display technology versus observed RAT, NR indication versus connection, observed cell roles and freshness; confirm Wi-Fi and dual-SIM behavior. No false NR or CA claims.
### Phase 3 — labels and persistence hardening
Fix verified existing errors, readback, toggle-off restore, Shizuku loss/recovery, subscription changes. Regression tests pass.
### Phase 4 — notifications, logging and CA enhancements
Implement only supported permission paths, opt-in service/logging and CSV. Device-specific enhancements behind capability checks.
### Phase 5 — validation and packaging
Run tests, build APK, verify update install preserving data with same applicationId/signing. If original signing key unavailable, state that in-place upgrade is impossible and do not silently change the ID or erase user data.

## 13. Required tests
- B1 EARFCN 100 -> 2120.0 MHz; B21 EARFCN 6525 -> 1503.4 MHz; NR global raster boundaries and TDD/FDD mappings.
- Two SIMs with different subscription IDs and default data switch, including SIM removal and changed IDs.
- Wi-Fi active with registered cellular data, no service, airplane mode, missing location/phone permissions.
- Status bar 5G while observed LTE, then 4G+ during throughput, then 5G after throughput; NR must remain `unconfirmed` unless independently verified.
- Multiple detected B19/B21/B42 cells must not be presented as confirmed 4CC CA without authoritative role data.
- Override ON -> OFF restores original/default or reports explicit unsupported failure; reboot and Shizuku stopped/restarted.
- Callback lifecycle, no leak, power use, opt-in notification and logging, CSV correctness, upgrade/install signing.

## 14. Deliverables
- Phase 0 report: actual findings, API/permission feasibility table (public, restricted, Shizuku-possible, unavailable), open questions and recommended implementation order.
- Incremental source commits and tests with per-phase summaries.
- User-facing permissions explanation, feature limitations and troubleshooting.
- Build output APK path, exact variant, signing identity, and whether it is install-compatible with the installed app; never assert actual device validation without logs/results.
