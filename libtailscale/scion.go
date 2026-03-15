// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package libtailscale

import (
	"log"

	"tailscale.com/wgengine/magicsock"
)

// ConfigureSCION updates SCION configuration at runtime.
// Called from the Android UI when the user changes SCION settings.
// This MUST be called from a background thread (Dispatchers.IO) to avoid ANR.
func (a *App) ConfigureSCION(enabled bool, bootstrapURL string, prefer bool) {
	a.ready.Wait()

	if a.backend == nil {
		log.Printf("ConfigureSCION: backend not ready")
		return
	}

	mc := a.backend.MagicConn()
	if mc == nil {
		log.Printf("ConfigureSCION: magicsock not ready")
		return
	}

	mc.ReconfigureSCION(magicsock.SCIONConfig{
		Enabled:      enabled,
		BootstrapURL: bootstrapURL,
		Prefer:       prefer,
	})
	log.Printf("ConfigureSCION: enabled=%v bootstrapURL=%q prefer=%v", enabled, bootstrapURL, prefer)
}
