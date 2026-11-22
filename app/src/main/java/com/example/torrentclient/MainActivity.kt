package com.example.torrentclient

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serviceIntent = Intent(this, TorrentService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        handleMagnetIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TorrentScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleMagnetIntent(intent)
    }

    private fun handleMagnetIntent(intent: Intent?) {
        val uriString = intent?.dataString ?: return
        if (uriString.startsWith("magnet:?")) {
            TorrentManager.addMagnet(uriString)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val torrents by TorrentManager.torrents.collectAsState()

    var magnetInput by remember { mutableStateOf("") }
    var hasAllFilesAccess by remember {
        mutableStateOf(TorrentManager.hasAllFilesAccess())
    }

    // Re-check storage permission automatically when returning from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAllFilesAccess = TorrentManager.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Request notification permission
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // File picker for .torrent files
    val torrentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                TorrentManager.addTorrentBytes(stream.readBytes())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Android Torrent Client") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasAllFilesAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                StoragePermissionBanner(
                    onGrantClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            )
                        }
                    }
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Saving to: /storage/emulated/0/Download",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = magnetInput,
                onValueChange = { magnetInput = it },
                label = { Text("Paste Magnet Link (magnet:?xt=...)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (magnetInput.isNotBlank()) {
                            TorrentManager.addMagnet(magnetInput)
                            magnetInput = ""
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add Magnet")
                }

                OutlinedButton(
                    onClick = {
                        torrentPickerLauncher.launch(arrayOf("application/x-bittorrent", "*/*"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open .torrent")
                }
            }

            HorizontalDivider()

            if (torrents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No active torrents. Add a magnet link or .torrent file above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(torrents, key = { it.infoHash }) { item ->
                        TorrentCard(
                            item = item,
                            onTogglePause = { TorrentManager.togglePause(item.infoHash) },
                            onRemove = { TorrentManager.removeTorrent(item.infoHash) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StoragePermissionBanner(onGrantClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Allow All Files Access",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Required to save torrents directly into your public Downloads folder for Samsung My Files.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onGrantClick) {
                Text("Grant")
            }
        }
    }
}

@Composable
fun TorrentCard(
    item: TorrentItem,
    onTogglePause: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            LinearProgressIndicator(
                progress = { item.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val percent = String.format(Locale.US, "%.1f%%", item.progress * 100f)
                    Text(
                        text = "$percent • ${item.state}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "↓ ${formatSpeed(item.downloadRateBytes)}  ↑ ${formatSpeed(item.uploadRateBytes)}  •  Peers: ${item.numPeers}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onTogglePause) {
                        Icon(
                            imageVector = if (item.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (item.isPaused) "Resume" else "Pause"
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove"
                        )
                    }
                }
            }
        }
    }
}

fun formatSpeed(bytesPerSec: Int): String {
    return when {
        bytesPerSec >= 1_048_576 -> String.format(Locale.US, "%.2f MB/s", bytesPerSec / 1_048_576f)
        bytesPerSec >= 1024 -> String.format(Locale.US, "%.1f KB/s", bytesPerSec / 1024f)
        else -> "$bytesPerSec B/s"
    }
}
