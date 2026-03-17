# SCION Integration Architecture (Android)

## Component Overview

The Android client adds a SCION settings UI and configuration bridge on top of the Go backend from [netsys-lab/tailscale-scion](https://github.com/netsys-lab/tailscale-scion).

```
┌─────────────────────────────────┐
│  Android UI (Kotlin/Compose)    │
│  ScionSettingsView, PeerDetails │
├─────────────────────────────────┤
│  Go Bridge (libtailscale)       │
│  ConfigureSCION()               │
├─────────────────────────────────┤
│  magicsock (tailscale-scion)    │
│  ReconfigureSCION(), pconnSCION │
└─────────────────────────────────┘
```

### Key Files

| File | Role |
|------|------|
| `App.kt` | Reads SCION SharedPreferences on startup, sets env vars, exposes `configureSCION()` |
| `libtailscale/scion.go` | Go bridge: `ConfigureSCION(enabled, bootstrapURL, prefer)` → `magicsock.ReconfigureSCION()` |
| `ScionSettingsViewModel.kt` | UI state management, load/save settings, poll status, apply with feedback |
| `ScionSettingsView.kt` | Settings screen: enable toggle, prefer toggle, bootstrap URL, apply button |
| `Scion.kt` | Data models: `Settings`, `StatusResponse` |
| `IpnState.kt` | `SCIONPathInfo` model (path, active, healthy, latencyMs, expiresAt, MTU) |
| `PeerDetails.kt` | Composable rendering SCION path list per peer |
| `PeerDetailsViewModel.kt` | Polls status API every 5s for live path data |
| `Client.kt` | LocalAPI HTTP client, includes `getScionStatus()` |
| `build-tags.sh` | Appends `ts_omit_scion` build tag when `TS_OMIT_SCION=1` |

## Data Flow

### Settings Apply

```
User toggles SCION in UI
  → ScionSettingsViewModel.saveAndApply()
    → App.saveScionSettings()
      → writes SharedPreferences (scion_enabled, scion_bootstrap_url, scion_prefer)
      → calls app.configureSCION() on Dispatchers.IO
        → Go: ConfigureSCION() waits a.ready.Wait()
          → magicsock.ReconfigureSCION(SCIONConfig{...})
            → sets envknobs (TS_SCION_EMBEDDED=1, TS_SCION_FORCE_BOOTSTRAP=1, ...)
            → closes existing connection
            → retrySCIONConnect() async (bootstrap → embedded daemon)
  → UI polls /v0/scion-status every 2s × 5 attempts (10s total)
    → shows "Connecting..." / "Connected (IA: ...)" / error
```

### Path Display

```
PeerDetailsViewModel polls /localapi/v0/status every 5s
  → JSON includes SCIONPaths[] per peer
    → PeerDetails.kt renders path rows:
       active/inactive indicator, healthy/unhealthy, latency (ms)
```

## Key Design Decisions

- **SharedPreferences persistence.** SCION settings stored in `"unencrypted"` prefs file with keys `scion_enabled`, `scion_bootstrap_url`, `scion_prefer`. Restored on app restart; env vars set before Go backend starts.

- **Runtime reconfiguration.** `ReconfigureSCION()` allows changing SCION settings without restarting the app. It forces `TS_SCION_EMBEDDED=1` + `TS_SCION_FORCE_BOOTSTRAP=1`, so Android always uses bootstrap → embedded daemon (never external sciond).

- **Startup flow.** If SCION is enabled in prefs, env vars are set before the Go backend launches → `initSCIONLocked()` runs the connection flow automatically.

- **Apply flow.** `ReconfigureSCION()` closes existing SCION connection → `retrySCIONConnect()` runs asynchronously → on success, rediscovers paths for all peers and triggers STUN.

- **Side-by-side repos.** `go.mod` uses `replace tailscale.com => ../tailscale-scion`, requiring both repos to be cloned as siblings. CI does this automatically.

- **Build without SCION.** Set `TS_OMIT_SCION=1` before building to exclude SCION dependencies via the `ts_omit_scion` build tag.

## Cross-Reference

The SCION transport implementation (connection management, path selection, send/receive, bootstrap) lives in [netsys-lab/tailscale-scion](https://github.com/netsys-lab/tailscale-scion). See its [docs/architecture.md](https://github.com/netsys-lab/tailscale-scion/blob/scion-dev/docs/architecture.md) for protocol-level details.
