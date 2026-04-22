// Copyright (c) Tailscale Inc & AUTHORS
// Copyright (c) 2026 netsys-lab
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.util.Lists
import com.tailscale.ipn.ui.viewModel.ScionSettingsViewModel

@Composable
fun ScionSettingsView(
    onBack: () -> Unit,
    viewModel: ScionSettingsViewModel = viewModel()
) {
    val enabled by viewModel.enabled.collectAsState()
    val bootstrapUrl by viewModel.bootstrapUrl.collectAsState()
    val prefer by viewModel.prefer.collectAsState()
    val connected by viewModel.scionConnected.collectAsState()
    val localIA by viewModel.localIA.collectAsState()
    val isApplying by viewModel.isApplying.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    val lastConnectError by viewModel.lastConnectError.collectAsState()
    val bootstrapUrlError by viewModel.bootstrapUrlError.collectAsState()

    Scaffold(
        topBar = {
            Header(titleRes = R.string.scion_settings, onBack = onBack)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Lists.MutedHeader(stringResource(R.string.scion_status))
            Setting.Text(
                title = stringResource(R.string.scion_connection),
                subtitle = when {
                    isApplying -> stringResource(R.string.scion_connecting)
                    connected -> stringResource(R.string.scion_connected_ia, localIA)
                    lastConnectError.isNotEmpty() -> stringResource(
                        R.string.scion_last_connect_error, lastConnectError
                    )
                    lastError.isNotEmpty() -> lastError
                    else -> stringResource(R.string.scion_not_connected)
                }
            )

            Lists.SectionDivider()

            Lists.MutedHeader(stringResource(R.string.scion_configuration))

            Setting.Switch(
                titleRes = R.string.scion_enable,
                isOn = enabled,
                onToggle = { viewModel.setEnabled(it) }
            )

            Lists.ItemDivider()

            Setting.Switch(
                titleRes = R.string.scion_prefer,
                isOn = prefer,
                enabled = enabled,
                onToggle = { viewModel.setPrefer(it) }
            )

            Lists.ItemDivider()

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.scion_bootstrap_url),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = bootstrapUrl,
                    onValueChange = { viewModel.setBootstrapUrl(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.scion_bootstrap_url_hint)) },
                    singleLine = true,
                    enabled = enabled,
                    isError = bootstrapUrlError.isNotEmpty(),
                    supportingText = if (bootstrapUrlError.isNotEmpty()) {
                        { Text(bootstrapUrlError, color = MaterialTheme.colorScheme.error) }
                    } else null,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.scion_bootstrap_url_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.applySettings() },
                    enabled = enabled && !isApplying && bootstrapUrlError.isEmpty(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isApplying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.scion_apply))
                    }
                }
            }
        }
    }
}
