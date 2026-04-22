// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.viewModel

import androidx.lifecycle.viewModelScope
import com.tailscale.ipn.App
import com.tailscale.ipn.ui.localapi.Client
import com.tailscale.ipn.ui.notifier.Notifier
import com.tailscale.ipn.ui.util.LoadingIndicator
import com.tailscale.ipn.ui.util.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsNav(
    val onNavigateToBugReport: () -> Unit,
    val onNavigateToAbout: () -> Unit,
    val onNavigateToDNSSettings: () -> Unit,
    val onNavigateToSplitTunneling: () -> Unit,
    val onNavigateToTailnetLock: () -> Unit,
    val onNavigateToSubnetRouting: () -> Unit,
    val onNavigateToMDMSettings: () -> Unit,
    val onNavigateToManagedBy: () -> Unit,
    val onNavigateToUserSwitcher: () -> Unit,
    val onNavigateToPermissions: () -> Unit,
    val onNavigateToScionSettings: () -> Unit,
    val onNavigateBackHome: () -> Unit,
    val onBackToSettings: () -> Unit,
)

class SettingsViewModel : IpnViewModel() {
  // Display name for the logged in user
  val isAdmin: StateFlow<Boolean> = MutableStateFlow(false)
  // True if tailnet lock is enabled.  nil if not yet known.
  val tailNetLockEnabled: StateFlow<Boolean?> = MutableStateFlow(null)
  // True if tailscaleDNS is enabled. nil if not yet known.
  val corpDNSEnabled: StateFlow<Boolean?> = MutableStateFlow(null)
  // True if SCION is enabled.
  val scionEnabled: StateFlow<Boolean> = MutableStateFlow(false)
  // True if SCION is currently connected (only meaningful when enabled).
  val scionConnected: StateFlow<Boolean> = MutableStateFlow(false)
  // Local SCION ISD-AS when connected; empty otherwise.
  val scionLocalIA: StateFlow<String> = MutableStateFlow("")

  init {
    viewModelScope.launch {
      Notifier.netmap.collect { netmap -> isAdmin.set(netmap?.SelfNode?.isAdmin ?: false) }
    }

    Client(viewModelScope).tailnetLockStatus { result ->
      result.onSuccess { status -> tailNetLockEnabled.set(status.Enabled) }

      LoadingIndicator.stop()
    }

    viewModelScope.launch {
      Notifier.prefs.collect {
        it?.let { corpDNSEnabled.set(it.CorpDNS) } ?: run { corpDNSEnabled.set(null) }
      }
    }

    refreshScionEnabled()
    refreshScionStatus()
  }

  /** Re-read SCION enabled state from SharedPreferences. Called on init and
   *  when returning from the SCION settings screen via LaunchedEffect. */
  fun refreshScionEnabled() {
    scionEnabled.set(App.get().getScionSettings().enabled)
  }

  /** Fetch current SCION connection state from the local API. Cheap: a
   *  single GET /v0/scion-status. Called on init and when returning to
   *  this screen so the row subtitle reflects actual connectivity, not
   *  just the enabled toggle. */
  fun refreshScionStatus() {
    if (!App.get().getScionSettings().enabled) {
      scionConnected.set(false)
      scionLocalIA.set("")
      return
    }
    Client(viewModelScope).getScionStatus { result ->
      result.onSuccess { status ->
        scionConnected.set(status.Connected)
        scionLocalIA.set(status.LocalIA ?: "")
      }
    }
  }
}
