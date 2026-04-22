// Copyright (c) Tailscale Inc & AUTHORS
// Copyright (c) 2026 netsys-lab
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.viewModel

import androidx.lifecycle.viewModelScope
import com.tailscale.ipn.App
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.localapi.Client
import com.tailscale.ipn.ui.model.Scion
import com.tailscale.ipn.ui.util.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class ScionSettingsViewModel : IpnViewModel() {
    val enabled: StateFlow<Boolean> = MutableStateFlow(false)
    val bootstrapUrl: StateFlow<String> = MutableStateFlow("")
    val prefer: StateFlow<Boolean> = MutableStateFlow(false)
    val scionConnected: StateFlow<Boolean> = MutableStateFlow(false)
    val localIA: StateFlow<String> = MutableStateFlow("")
    val isApplying: StateFlow<Boolean> = MutableStateFlow(false)
    val lastError: StateFlow<String> = MutableStateFlow("")
    val lastConnectError: StateFlow<String> = MutableStateFlow("")
    val bootstrapUrlError: StateFlow<String> = MutableStateFlow("")

    // Tracks the in-flight apply poll so rapid Apply taps, toggle spam,
    // or Activity recreation don't spawn parallel pollers racing on the
    // same StateFlows.
    private var applyJob: Job? = null

    // Tracks the background status-refresh loop that runs while the settings
    // screen is visible, so users see background SCION reconnects/drops
    // without having to re-enter the screen or toggle anything.
    private var statusRefreshJob: Job? = null

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
                lastConnectError.set(if (status.Connected) "" else status.LastConnectError ?: "")
            }
        }
    }

    // Starts a periodic refresh of SCION status while the settings screen
    // is visible. Skips the poll while an Apply is in flight (the apply
    // loop is already polling) and while SCION is disabled. Idempotent.
    fun startPeriodicStatusRefresh(periodMs: Long = 5_000L) {
        if (statusRefreshJob?.isActive == true) return
        statusRefreshJob = viewModelScope.launch {
            while (isActive) {
                if (enabled.value && !isApplying.value) refreshStatus()
                delay(periodMs)
            }
        }
    }

    fun stopPeriodicStatusRefresh() {
        statusRefreshJob?.cancel()
        statusRefreshJob = null
    }

    fun setEnabled(value: Boolean) {
        enabled.set(value)
        saveAndApply()
    }

    fun setBootstrapUrl(value: String) {
        // Persist the trimmed form so saveAndApply doesn't push whitespace
        // into SharedPreferences or the Go envknob.
        val trimmed = value.trim()
        bootstrapUrl.set(trimmed)
        validateBootstrapUrl(trimmed)
        // Don't auto-save - user must press Apply button
    }

    private fun validateBootstrapUrl(url: String) {
        if (url.isEmpty()) {
            bootstrapUrlError.set("") // Empty is valid (uses DNS discovery / defaults)
            return
        }
        if (url.length > 2048) {
            bootstrapUrlError.set(App.get().getString(R.string.scion_invalid_url))
            return
        }
        try {
            val parsed = java.net.URL(url)
            if (parsed.protocol != "http" && parsed.protocol != "https") {
                bootstrapUrlError.set(App.get().getString(R.string.scion_invalid_url_scheme))
                return
            }
            if (parsed.host.isNullOrBlank()) {
                bootstrapUrlError.set(App.get().getString(R.string.scion_invalid_url))
                return
            }
            bootstrapUrlError.set("")
        } catch (e: Exception) {
            bootstrapUrlError.set(App.get().getString(R.string.scion_invalid_url))
        }
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
        lastError.set("")
        lastConnectError.set("")

        // Cancel any in-flight apply; the new settings supersede the old intent.
        // The cancelled job's finally block will clear isApplying before the new
        // launch below sets it back to true.
        applyJob?.cancel()

        if (!settings.enabled) {
            // Disabling - update immediately, no need to poll
            scionConnected.set(false)
            localIA.set("")
            return
        }

        isApplying.set(true)
        applyJob = viewModelScope.launch {
            // Poll status to catch connection result.
            // ReconfigureSCION is async so we poll with a generous window.
            try {
                var connected = false
                for (i in 1..10) {
                    kotlinx.coroutines.delay(2000)
                    val result = pollScionStatus()
                    if (result != null) {
                        scionConnected.set(result.Connected)
                        localIA.set(result.LocalIA ?: "")
                        lastConnectError.set(if (result.Connected) "" else result.LastConnectError ?: "")
                        if (result.Connected) {
                            connected = true
                            break
                        }
                    }
                }
                if (!connected) {
                    // Don't show a hard error -- connection may still be in progress.
                    // The status section already shows "Not connected" via scionConnected=false.
                    lastError.set(App.get().getString(R.string.scion_connect_timeout))
                }
            } finally {
                // Always clear the applying latch, even on cancellation
                // (screen rotation, Activity destroy, or re-entrant Apply).
                isApplying.set(false)
            }
        }
    }

    private suspend fun pollScionStatus(): Scion.StatusResponse? {
        return suspendCancellableCoroutine { cont ->
            Client(viewModelScope).getScionStatus { result ->
                result.onSuccess { cont.resume(it) }
                result.onFailure { cont.resume(null) }
            }
        }
    }
}
