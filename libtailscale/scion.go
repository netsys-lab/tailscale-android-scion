// Copyright (c) Tailscale Inc & AUTHORS
// Copyright (c) 2026 netsys-lab
// SPDX-License-Identifier: BSD-3-Clause

package libtailscale

import (
	"fmt"
	"log"

	"tailscale.com/wgengine/magicsock"
)

// ConfigureSCION updates SCION configuration at runtime.
// Called from the Android UI when the user changes SCION settings.
// This MUST be called from a background thread (Dispatchers.IO) to avoid ANR.
//
// ReconfigureSCION is asynchronous (it launches retrySCIONConnect in a
// goroutine), so a nil return means the config was dispatched, not that
// the connection succeeded. The caller should poll /v0/scion-status.
func (a *App) ConfigureSCION(enabled bool, bootstrapURL string, prefer bool) error {
	a.ready.Wait()

	if a.backend == nil {
		return fmt.Errorf("ConfigureSCION: backend not ready")
	}

	mc := a.backend.MagicConn()
	if mc == nil {
		return fmt.Errorf("ConfigureSCION: magicsock not ready")
	}

	mc.ReconfigureSCION(magicsock.SCIONConfig{
		Enabled:      enabled,
		BootstrapURL: bootstrapURL,
		Prefer:       prefer,
	})
	log.Printf("ConfigureSCION: enabled=%v bootstrapURL=%q prefer=%v", enabled, bootstrapURL, prefer)
	return nil
}
