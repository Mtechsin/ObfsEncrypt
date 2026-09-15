# Bug Reporting & Diagnostics Plan

Obfs Encrypt is **100% offline**. There is no crash SDK, no analytics, and no automatic upload.
A good bug report must therefore be **built in-app**, **privacy-safe**, and **copyable** by the user
into GitHub, email, or chat.

This document is the engineering plan and the contract for what the diagnostic pipeline is
allowed to capture.

---

## Goals

1. Users can export a complete diagnostic report in one tap (Settings → Support).
2. Developers get enough context to reproduce without a second round-trip.
3. **Never** capture passwords, keyfile bytes, Keystore material, file contents, or full user paths.
4. Work on API 24–35, debug and release, with and without storage permission.
5. Survive process death: the last crash is persisted and included on the next launch.

## Non-goals

- No network telemetry (would break the offline security promise).
- No Play Vitals / Firebase Crashlytics (requires network + third-party SDKs).
- No automatic issue creation.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Catch sites (crypto, worker, UI, storage)                  │
│       │                                                     │
│       ▼                                                     │
│  AppLogger  ──► logcat (tag: ObfsLog)                       │
│       │       ──► in-memory ring buffer (last 400 events)   │
│       │                                                     │
│  CrashHandler ──► files/crash_last.txt (survives restart)   │
│       │                                                     │
│       ▼                                                     │
│  DiagnosticsCollector                                       │
│    • app/build/device/settings snapshot                     │
│    • sanitized recent history (ops only, redacted names)    │
│    • ring buffer + last crash                               │
│    • privacy scrubber                                       │
│       │                                                     │
│       ▼                                                     │
│  BugReportExporter                                          │
│    • Share sheet (ACTION_SEND text/plain)                   │
│    • Copy to clipboard                                      │
│    • Save to app files (optional)                           │
└─────────────────────────────────────────────────────────────┘
```

### Components

| Component | Path | Responsibility |
|-----------|------|----------------|
| `AppLogger` | `diagnostics/AppLogger.kt` | Structured levels, tags, ring buffer, logcat |
| `CrashHandler` | `diagnostics/CrashHandler.kt` | `UncaughtExceptionHandler`, persist stack + breadcrumbs |
| `DiagnosticsCollector` | `diagnostics/DiagnosticsCollector.kt` | Assemble sanitized markdown report |
| `BugReportExporter` | `diagnostics/BugReportExporter.kt` | Share / copy / save |

Wired in `ObfsApp.onCreate()` before any work starts.

---

## Privacy contract (hard rules)

These rules are enforced in `DiagnosticsCollector.sanitize*` and must not be bypassed.

| Data | Treatment |
|------|-----------|
| Passwords, keyfile bytes, salts, nonces, Keystore keys | **Never logged.** Call sites must not pass them. |
| File contents | **Never logged.** |
| Full file paths | Replaced with `<path>` + extension only. |
| File basenames | Redacted: first character + `…` + extension (e.g. `t….pdf`). |
| Display names / emails | Not collected. |
| Absolute external storage roots | Collapsed to `/…/` form. |
| History `errorMessage` | Kept (exception messages rarely contain secrets); paths inside messages scrubbed. |
| Device model, API, locale, ABI | **Kept** — needed for triage. |
| App version / build type / flags | **Kept.** |
| Feature toggles (theme, app lock, biometrics, shred, integrity) | **Kept as booleans only.** |

If a future field is added, it must land in this table first.

---

## What a good report contains

The exported markdown looks like:

```markdown
# Obfs Encrypt Bug Report

## Summary
<!-- user fills: what happened, expected, steps -->

## App
- versionName / versionCode / buildType / debuggable

## Device
- manufacturer / model / API / ABI(s) / locale / low-ram

## Settings (non-secret)
- theme, language, app lock, biometric, shred, integrity, storage permission, output folder set

## Recent operations (sanitized)
- last N history rows: op, method, success, size bucket, redacted name, error class

## Last crash (if any)
- time, thread, exception, sanitized stack (top 25 frames)

## Recent log events
- timestamped AppLogger ring buffer
```

Target size: **under ~50 KB** so it pastes into a GitHub issue body.

---

## Instrumentation policy

### Always log (`AppLogger.e` / `.w`)

- Uncaught exceptions (via `CrashHandler`)
- Encryption / decryption failures (`EncryptionHelper`, `ParallelEncryptionHelper`)
- WorkManager worker failures (`EncryptionWorker`)
- Keystore load/init failures (`SecureKeyStore`)
- Permission / SAF failures that abort an operation
- History parse failures

### Log at debug only

- Navigation hops, dialog compose, permission dialogs (existing `Log.d` can stay for debug builds)

### Never log

- Password / keyfile / ciphertext bytes
- Full URI strings from the picker
- User-visible file names in free text (use `sanitizeFileName`)

### Silent `catch (_: Exception)`

Allowed only for **best-effort cleanup** (temp file delete, stream close).
Any catch that hides a user-visible failure must call `AppLogger.w/e` with tag + class name.

---

## User-facing flow

1. **Settings → Support → Report a bug**  
   Opens a bottom sheet / dialog explaining what is included and what is excluded.
2. User taps **Share report** or **Copy report**.
3. Optional fields: user pastes a short description into the report header before sharing
   (compose `Summary` section in the collector, or leave the HTML comment for them).
4. **Help → How to report a bug** FAQ explains the same path for users who land in Help first.

No email address is hard-coded; the share sheet lets the user pick GitHub, email, notes, etc.

---

## GitHub issue intake

| File | Purpose |
|------|---------|
| `.github/ISSUE_TEMPLATE/bug_report.yml` | Structured bug form; asks for the pasted diagnostics blob |
| `.github/ISSUE_TEMPLATE/feature_request.yml` | Separate from bugs |
| `.github/ISSUE_TEMPLATE/config.yml` | Disables blank issues; links security policy |

The bug form **requires** the diagnostics block (or an explicit "could not export" note).

---

## Developer triage checklist

1. Reproduce with the same versionCode if possible.
2. Look for `Last crash` first — stack often identifies the module immediately.
3. Check `Recent operations` for method (STANDARD/FAST/STRONG), file size bucket, success flags.
4. Check settings flags: app lock, biometric, shred, integrity, custom output folder.
5. If the ring buffer is empty, the crash happened before `ObfsApp.onCreate` finished —
   inspect `crash_last.txt` only path and ANR/low-memory clues on the device form.

---

## Implementation phases

| Phase | Scope | Status |
|-------|--------|--------|
| P0 | `AppLogger`, `CrashHandler`, collector, exporter, ObfsApp wiring | Done |
| P0 | Settings Support section + Help FAQ + strings (en/ar) | Done |
| P0 | Instrument crypto + worker critical catches | Done |
| P1 | GitHub issue templates | Done |
| P2 | Persist last 200 log lines to disk (optional, disk-privacy tradeoff) | Open |
| P3 | On-device "reproduce steps" wizard that times operations | Open |

---

## Maintenance rules

- New catch sites in `crypto/`, `services/`, `security/` must use `AppLogger`.
- New settings that affect crypto behavior must be added as a boolean/category line in the collector.
- Privacy table changes require a one-line note in the PR description.
- Do not add network libraries to "improve" reporting.
