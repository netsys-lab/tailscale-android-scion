// Copyright (c) Tailscale Inc & AUTHORS
// Copyright (c) 2026 netsys-lab
// SPDX-License-Identifier: BSD-3-Clause

package libtailscale

import (
	"fmt"
	"log"
	"net/url"
	"sync"
	"time"

	"tailscale.com/wgengine/magicsock"
)

// configureSCIONReadyTimeout bounds how long ConfigureSCION will wait for
// backend startup to complete before reporting an error. A startup panic or
// engine init failure can leave a.ready never signalled; without the bound
// the caller's IO thread blocks forever.
const configureSCIONReadyTimeout = 30 * time.Second

// SCION-config memo. The App is a process-singleton so package-level state
// is safe and keeps the ConfigureSCION logic colocated with the other SCION
// code without polluting the App struct in backend.go with a magicsock
// import. ReconfigureSCION forces a SCION close / reconnect cycle, so
// short-circuiting no-op dispatches avoids spurious disconnects from
// identical back-to-back settings writes (toggle off → on, duplicate
// Apply taps, config-equal reloads).
var (
	lastSCIONConfigMu  sync.Mutex
	lastSCIONConfig    magicsock.SCIONConfig
	hasLastSCIONConfig bool
)

// redactBootstrapURL returns a logcat-safe representation of a SCION
// bootstrap URL: scheme + host only. Path and query are dropped because
// they may contain organisation-identifying discovery info.
func redactBootstrapURL(raw string) string {
	if raw == "" {
		return ""
	}
	u, err := url.Parse(raw)
	if err != nil || u.Host == "" {
		return "(redacted)"
	}
	return u.Scheme + "://" + u.Host
}

// ConfigureSCION updates SCION configuration at runtime.
// Called from the Android UI when the user changes SCION settings.
// This MUST be called from a Kotlin background thread (Dispatchers.IO) to
// avoid ANR — the Go side does not enforce the caller's thread.
//
// ReconfigureSCION is asynchronous (it launches retrySCIONConnect in a
// goroutine), so a nil return means the config was dispatched, not that
// the connection succeeded. The caller should poll /v0/scion-status.
func (a *App) ConfigureSCION(enabled bool, bootstrapURL string, prefer bool) error {
	// Wait for the backend to be ready, but don't hang indefinitely if
	// startup failed.
	done := make(chan struct{})
	go func() {
		a.ready.Wait()
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(configureSCIONReadyTimeout):
		return fmt.Errorf("ConfigureSCION: backend startup timed out after %s", configureSCIONReadyTimeout)
	}

	if a.backend == nil {
		return fmt.Errorf("ConfigureSCION: backend not ready")
	}

	mc := a.backend.MagicConn()
	if mc == nil {
		return fmt.Errorf("ConfigureSCION: magicsock not ready")
	}

	cfg := magicsock.SCIONConfig{
		Enabled:      enabled,
		BootstrapURL: bootstrapURL,
		Prefer:       prefer,
	}

	// Skip the dispatch if the config is identical to what was last sent.
	// This avoids a spurious SCION close/reconnect cycle when a user toggle
	// ends up back at the same state (e.g. off → on with the same URL).
	lastSCIONConfigMu.Lock()
	unchanged := hasLastSCIONConfig && lastSCIONConfig == cfg
	lastSCIONConfig = cfg
	hasLastSCIONConfig = true
	lastSCIONConfigMu.Unlock()
	if unchanged {
		log.Printf("ConfigureSCION: noop (config unchanged)")
		return nil
	}

	mc.ReconfigureSCION(cfg)
	log.Printf("ConfigureSCION: enabled=%v bootstrapURL=%s prefer=%v", enabled, redactBootstrapURL(bootstrapURL), prefer)
	return nil
}
