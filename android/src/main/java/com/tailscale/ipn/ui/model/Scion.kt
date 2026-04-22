// Copyright (c) Tailscale Inc & AUTHORS
// Copyright (c) 2026 netsys-lab
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.model

import kotlinx.serialization.Serializable

class Scion {
    data class Settings(
        val enabled: Boolean = false,
        val bootstrapUrl: String = "",
        val prefer: Boolean = false,
    )

    @Serializable
    data class StatusResponse(
        val Connected: Boolean = false,
        val LocalIA: String? = null,
        // LastConnectError is the most recent SCION connect-attempt failure
        // message, empty if SCION is connected or has never failed.
        val LastConnectError: String? = null,
        // LastConnectErrorAt is the RFC3339 wall-clock time of LastConnectError.
        val LastConnectErrorAt: String? = null,
    )
}
