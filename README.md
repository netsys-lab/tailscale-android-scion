# Tailscale (SCION) for Android

Tailscale Android client with SCION path-aware networking.

## What This Is

A fork of [tailscale/tailscale-android](https://github.com/tailscale/tailscale-android) that adds SCION settings, live path display, and an embedded SCION daemon. Uses [netsys-lab/tailscale-scion](https://github.com/netsys-lab/tailscale-scion) as the Go backend.

> **This project is not affiliated with or endorsed by Tailscale Inc.**

## Install

Download the latest signed APK from [GitHub Releases](https://github.com/netsys-lab/tailscale-android-scion/releases) and sideload it (enable "Install unknown apps" in Android settings).

The app uses a separate application ID (`io.github.netsyslab.tailscale.scion`) and can be installed alongside the official Tailscale app.

## Usage

1. Open the app and log in to your Tailscale network.
2. Go to **Settings → SCION Settings**.
3. Toggle **Enable SCION** on.
4. Enter your **Bootstrap Server URL** (e.g., `http://bootstrap.example.com:8041`).
5. Tap **Apply**.

The status will show "Connecting..." and then "Connected (IA: ...)" on success, or an error message on failure.

To see SCION paths for a peer, tap the peer in the main list -- the peer details screen shows active paths with latency and health status, updated every 5 seconds.

## Building from Source

### Prerequisites

- Go (latest stable)
- Android SDK with command-line tools
- Android NDK (23.1.7779620 as configured in `build.gradle`)
- Java 17

### Setup

Clone both repos side-by-side (the `go.mod` has `replace tailscale.com => ../tailscale-scion`):

```bash
git clone https://github.com/netsys-lab/tailscale-android-scion.git
git clone https://github.com/netsys-lab/tailscale-scion.git
cd tailscale-android-scion
```

### Build (Linux / macOS)

```bash
make apk            # build debug APK
make install         # install on connected device
make run             # install and launch
```

The Makefile uses `./tool/go` (Tailscale Go toolchain) and builds for all architectures. The `go.mod` replace directive ensures the SCION backend is used automatically.

### Build without SCION

```bash
export TS_OMIT_SCION=1
make apk
```

## Architecture

See [docs/architecture.md](docs/architecture.md) for component overview, data flow, and design decisions.

## License

BSD-3-Clause. Based on [tailscale/tailscale-android](https://github.com/tailscale/tailscale-android).
SCION networking provided by [scionproto/scion](https://github.com/scionproto/scion) (Apache-2.0).

This project is not affiliated with or endorsed by Tailscale Inc.
WireGuard is a registered trademark of Jason A. Donenfeld.
