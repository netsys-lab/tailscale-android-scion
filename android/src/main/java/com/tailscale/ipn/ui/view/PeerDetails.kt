// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.model.IpnState
import com.tailscale.ipn.ui.theme.listItem
import com.tailscale.ipn.ui.theme.short
import com.tailscale.ipn.ui.util.AndroidTVUtil.isAndroidTV
import com.tailscale.ipn.ui.util.Lists
import com.tailscale.ipn.ui.util.itemsWithDividers
import com.tailscale.ipn.ui.viewModel.PeerDetailsViewModel
import com.tailscale.ipn.ui.viewModel.PeerDetailsViewModelFactory
import com.tailscale.ipn.ui.viewModel.PingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerDetails(
    onNavigateBack: () -> Unit,
    nodeId: String,
    pingViewModel: PingViewModel,
    model: PeerDetailsViewModel =
        viewModel(
            factory =
                PeerDetailsViewModelFactory(nodeId, LocalContext.current.filesDir, pingViewModel))
) {
  val isPinging by model.isPinging.collectAsState()
  val peerStatus by model.peerStatus.collectAsState()

  model.netmap.collectAsState().value?.let { netmap ->
    model.node.collectAsState().value?.let { node ->
      Scaffold(
          topBar = {
            Header(
                title = {
                  Column {
                    Text(
                        text = node.displayName,
                        style = MaterialTheme.typography.titleMedium.short,
                        color = MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Box(
                          modifier =
                              Modifier.size(8.dp)
                                  .background(
                                      color = node.connectedColor(netmap),
                                      shape = RoundedCornerShape(percent = 50))) {}
                      Spacer(modifier = Modifier.size(8.dp))
                      Text(
                          text = stringResource(id = node.connectedStrRes(netmap)),
                          style = MaterialTheme.typography.bodyMedium.short,
                          color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                },
                actions = {
                  IconButton(onClick = { model.startPing() }) {
                    Icon(
                        painter = painterResource(R.drawable.timer),
                        contentDescription = "Ping device")
                  }
                },
                onBack = onNavigateBack)
          },
      ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
        ) {
          item(key = "tailscaleAddresses") {
            Lists.MutedHeader(stringResource(R.string.tailscale_addresses))
          }

          itemsWithDividers(node.displayAddresses, key = { it.address }) {
            AddressRow(address = it.address, type = it.typeString)
          }

          item(key = "infoDivider") { Lists.SectionDivider() }

          itemsWithDividers(node.info, key = { "info_${it.titleRes}" }) {
            ValueRow(title = stringResource(id = it.titleRes), value = it.value.getString())
          }

          // SCION Paths section
          peerStatus?.SCIONPaths?.takeIf { it.isNotEmpty() }?.let { rawPaths ->
            // Render active path first, then by latency ascending.
            // Unmeasured (LatencyMs = -1) paths sort last.
            val paths = rawPaths.sortedWith(
                compareByDescending<IpnState.SCIONPathInfo> { it.Active }
                    .thenBy { if (it.hasLatency) it.LatencyMs else Double.MAX_VALUE }
            )
            item(key = "scionDivider") { Lists.SectionDivider() }
            item(key = "scionHeader") {
              Lists.MutedHeader(stringResource(R.string.scion_paths))
            }
            itemsWithDividers(paths, key = { it.Path }) { pathInfo ->
              ScionPathRow(pathInfo)
            }
          }
        }
        if (isPinging) {
          ModalBottomSheet(onDismissRequest = { model.onPingDismissal() }) {
            PingView(model = model.pingViewModel)
          }
        }
      }
    }
  }
}

@Composable
fun AddressRow(address: String, type: String) {
  val localClipboardManager = LocalClipboardManager.current

  // Android TV doesn't have a clipboard, nor any way to use the values, so visible only.
  val modifier =
      if (isAndroidTV()) {
        Modifier.focusable(false)
      } else {
        Modifier.clickable { localClipboardManager.setText(AnnotatedString(address)) }
      }

  ListItem(
      modifier = modifier,
      colors = MaterialTheme.colorScheme.listItem,
      headlineContent = { Text(text = address) },
      supportingContent = { Text(text = type) },
      trailingContent = {
        // TODO: there is some overlap with other uses of clipboard, DRY
        if (!isAndroidTV()) {
          Icon(painter = painterResource(id = R.drawable.clipboard), null)
        }
      })
}

@Composable
fun ValueRow(title: String, value: String) {
  ListItem(
      colors = MaterialTheme.colorScheme.listItem,
      headlineContent = { Text(text = title) },
      supportingContent = { Text(text = value) })
}

@Composable
fun ScionPathRow(pathInfo: IpnState.SCIONPathInfo) {
  // A path whose last successful probe is older than this threshold is
  // visually demoted — the dot goes muted and "stale" appears in the
  // supporting text. Helps operators spot dying paths at a glance.
  val staleThresholdSeconds = 30.0
  val isStale = pathInfo.LastPongSecondsAgo > staleThresholdSeconds
  ListItem(
      colors = MaterialTheme.colorScheme.listItem,
      headlineContent = {
        Text(text = pathInfo.Path, style = MaterialTheme.typography.bodySmall, maxLines = 2)
      },
      supportingContent = {
        val latencyStr =
            if (pathInfo.hasLatency) stringResource(R.string.scion_latency_ms, pathInfo.LatencyMs)
            else stringResource(R.string.scion_latency_unknown)
        val hopsStr =
            if (pathInfo.Hops > 0) stringResource(R.string.scion_hops, pathInfo.Hops)
            else stringResource(R.string.scion_hops_same_as)
        val statusStr =
            if (pathInfo.Healthy) stringResource(R.string.scion_path_healthy)
            else stringResource(R.string.scion_path_unhealthy)
        val base = "$latencyStr · $hopsStr · $statusStr"
        val text =
            if (isStale) "$base · ${stringResource(R.string.scion_path_stale)}" else base
        Text(text)
      },
      leadingContent = {
        val dotColor = when {
          isStale -> MaterialTheme.colorScheme.outline
          pathInfo.Active -> MaterialTheme.colorScheme.primary
          else -> MaterialTheme.colorScheme.outlineVariant
        }
        Box(
            modifier =
                Modifier.size(8.dp)
                    .background(color = dotColor, shape = RoundedCornerShape(percent = 50)))
      })
}
