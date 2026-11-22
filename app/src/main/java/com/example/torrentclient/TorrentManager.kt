package com.example.torrentclient

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.TorrentFinishedAlert
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class TorrentItem(
    val infoHash: String,
    val name: String,
    val progress: Float,
    val downloadRateBytes: Int,
    val uploadRateBytes: Int,
    val numPeers: Int,
    val state: String,
    val isPaused: Boolean
)

object TorrentManager {
    private val session = SessionManager()
    private val handles = ConcurrentHashMap<String, TorrentHandle>()
    private val pausedHashes = ConcurrentHashMap.newKeySet<String>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var appContext: Context? = null

    private val _torrents = MutableStateFlow<List<TorrentItem>>(emptyList())
    val torrents: StateFlow<List<TorrentItem>> = _torrents.asStateFlow()

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun getDownloadDirectory(): File {
        return if (hasAllFilesAccess()) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).apply {
                if (!exists()) mkdirs()
            }
        } else {
            val ctx = appContext
            ctx?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(ctx?.filesDir, "downloads").apply { mkdirs() }
        }
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        if (session.isRunning) return

        session.addListener(object : AlertListener {
            override fun types(): IntArray? = null

            override fun alert(alert: Alert<*>) {
                when (alert.type()) {
                    AlertType.ADD_TORRENT -> {
                        val addAlert = alert as AddTorrentAlert
                        val handle = addAlert.handle()
                        if (handle != null && handle.isValid) {
                            handles[handle.infoHash().toHex()] = handle
                        }
                    }
                    AlertType.TORRENT_FINISHED -> {
                        val finishedAlert = alert as TorrentFinishedAlert
                        val handle = finishedAlert.handle()
                        val ctx = appContext
                        if (handle != null && handle.isValid && ctx != null) {
                            val torrentName = handle.name() ?: return
                            val savedTarget = File(getDownloadDirectory(), torrentName)
                            MediaScannerConnection.scanFile(
                                ctx,
                                arrayOf(savedTarget.absolutePath),
                                null,
                                null
                            )
                        }
                    }
                    else -> Unit
                }
            }
        })

        session.start()
        startPolling()
    }

    fun stop() {
        pollingJob?.cancel()
        handles.clear()
        pausedHashes.clear()
        if (session.isRunning) {
            session.stop()
        }
    }

    fun addMagnet(magnetUri: String) {
        val dir = getDownloadDirectory()
        val trimmed = magnetUri.trim()
        if (!trimmed.startsWith("magnet:?")) return

        scope.launch {
            try {
                val params = AddTorrentParams.parseMagnetUri(trimmed)
                params.savePath(dir.absolutePath)
                session.swig().async_add_torrent(params.swig())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addTorrentBytes(bytes: ByteArray) {
        val dir = getDownloadDirectory()
        scope.launch {
            try {
                val torrentInfo = TorrentInfo(bytes)
                session.download(torrentInfo, dir)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun togglePause(infoHash: String) {
        val handle = handles[infoHash] ?: return
        if (!handle.isValid) return

        if (pausedHashes.contains(infoHash)) {
            handle.resume()
            pausedHashes.remove(infoHash)
        } else {
            handle.pause()
            pausedHashes.add(infoHash)
        }
    }

    fun removeTorrent(infoHash: String) {
        val handle = handles.remove(infoHash) ?: return
        pausedHashes.remove(infoHash)
        if (handle.isValid) {
            session.remove(handle)
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                val list = handles.entries.mapNotNull { (hash, handle) ->
                    if (!handle.isValid) return@mapNotNull null
                    val status = handle.status()
                    val isPaused = pausedHashes.contains(hash)
                    TorrentItem(
                        infoHash = hash,
                        name = status.name().orEmpty().ifBlank { "Fetching metadata..." },
                        progress = status.progress(),
                        downloadRateBytes = status.downloadRate(),
                        uploadRateBytes = status.uploadRate(),
                        numPeers = status.numPeers(),
                        state = if (isPaused) "PAUSED" else status.state().name,
                        isPaused = isPaused
                    )
                }
                _torrents.value = list
                delay(1000L)
            }
        }
    }
}
