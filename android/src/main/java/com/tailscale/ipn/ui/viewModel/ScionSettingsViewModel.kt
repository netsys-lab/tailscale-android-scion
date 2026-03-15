// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.viewModel

import androidx.lifecycle.viewModelScope
import com.tailscale.ipn.App
import com.tailscale.ipn.ui.localapi.Client
import com.tailscale.ipn.ui.model.Scion
import com.tailscale.ipn.ui.util.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScionSettingsViewModel : IpnViewModel() {
    val enabled: StateFlow<Boolean> = MutableStateFlow(false)
    val bootstrapUrl: StateFlow<String> = MutableStateFlow("")
    val prefer: StateFlow<Boolean> = MutableStateFlow(false)
    val scionConnected: StateFlow<Boolean> = MutableStateFlow(false)
    val localIA: StateFlow<String> = MutableStateFlow("")

    init {
        loadSettings()
        refreshStatus()
    }

    private fun loadSettings() {
        val settings = App.get().getScionSettings()
        enabled.set(settings.enabled)
        bootstrapUrl.set(settings.bootstrapUrl)
        prefer.set(settings.prefer)
    }

    fun refreshStatus() {
        Client(viewModelScope).getScionStatus { result ->
            result.onSuccess { status ->
                scionConnected.set(status.Connected)
                localIA.set(status.LocalIA ?: "")
            }
        }
    }

    fun setEnabled(value: Boolean) {
        enabled.set(value)
        saveAndApply()
    }

    fun setBootstrapUrl(value: String) {
        bootstrapUrl.set(value)
        // Don't auto-save - user must press Apply button
    }

    fun setPrefer(value: Boolean) {
        prefer.set(value)
        saveAndApply()
    }

    fun applySettings() {
        saveAndApply()
    }

    private fun saveAndApply() {
        val settings = Scion.Settings(
            enabled = enabled.value,
            bootstrapUrl = bootstrapUrl.value,
            prefer = prefer.value,
        )
        App.get().saveScionSettings(settings)
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            refreshStatus()
        }
    }
}
