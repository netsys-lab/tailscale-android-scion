// Copyright (c) Tailscale Inc & AUTHORS
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
    )
}
