package com.m3u.tv

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class TvRecording(
    val id: String,
    val channelId: Int,
    val channelName: String,
    val showTitle: String,
    val coverUrl: String?,
    val streamUrl: String,
    val durationSeconds: Int,
    val timestamp: Long
) {
    val formattedDuration: String
        get() {
            val m = durationSeconds / 60
            val s = durationSeconds % 60
            return String.format("%02d:%02d", m, s)
        }

    val formattedDate: String
        get() {
            val sdf = java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(timestamp))
        }
}

object StreamRecorder {
    @Volatile
    private var recordingJob: Job? = null
    @Volatile
    private var currentFile: File? = null

    fun start(context: Context, recId: String, streamUrl: String): String {
        stop()
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rec_${recId}.ts")
        currentFile = file

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (streamUrl.contains(".m3u8", ignoreCase = true)) {
                    recordHls(streamUrl, file)
                } else {
                    recordDirect(streamUrl, file)
                }
            } catch (_: Exception) {
                try {
                    recordDirect(streamUrl, file)
                } catch (_: Exception) {}
            }
        }
        return file.absolutePath
    }

    fun stop(): String? {
        val path = currentFile?.absolutePath
        try {
            recordingJob?.cancel()
        } catch (_: Exception) {}
        recordingJob = null
        currentFile = null
        return path
    }

    private suspend fun recordHls(streamUrl: String, outputFile: File) {
        var mediaUrl = streamUrl
        try {
            val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "TeleMate/1.0 (Android TV)")
                instanceFollowRedirects = true
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            if (text.contains("#EXT-X-STREAM-INF")) {
                val lines = text.lines()
                for (i in lines.indices) {
                    if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                        val nextLine = lines.getOrNull(i + 1)?.trim()
                        if (nextLine != null && !nextLine.startsWith("#") && nextLine.isNotEmpty()) {
                            mediaUrl = URI(streamUrl).resolve(nextLine).toString()
                            break
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val downloadedSegments = mutableSetOf<String>()
        val baseUri = URI(mediaUrl)

        while (currentCoroutineContext().isActive) {
            try {
                val conn = (URL(mediaUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "TeleMate/1.0 (Android TV)")
                    instanceFollowRedirects = true
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val lines = text.lines()
                for (line in lines) {
                    if (!currentCoroutineContext().isActive) break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                    val segUrl = try {
                        baseUri.resolve(trimmed).toString()
                    } catch (_: Exception) {
                        trimmed
                    }

                    if (downloadedSegments.add(segUrl)) {
                        try {
                            val segConn = (URL(segUrl).openConnection() as HttpURLConnection).apply {
                                connectTimeout = 5000
                                readTimeout = 8000
                                setRequestProperty("User-Agent", "TeleMate/1.0 (Android TV)")
                                instanceFollowRedirects = true
                            }
                            if (segConn.responseCode in 200..299) {
                                segConn.inputStream.use { input ->
                                    FileOutputStream(outputFile, true).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
            delay(1500L)
        }
    }

    private suspend fun recordDirect(streamUrl: String, outputFile: File) {
        val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "TeleMate/1.0 (Android TV)")
            instanceFollowRedirects = true
        }
        conn.inputStream.use { input ->
            FileOutputStream(outputFile, true).use { output ->
                val buffer = ByteArray(32 * 1024)
                while (currentCoroutineContext().isActive) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
            }
        }
    }
}

object RecordingsManager {
    private const val PREFS_NAME = "telemate_recordings_prefs"
    private const val KEY_RECORDINGS = "saved_recordings"

    fun getRecordings(context: Context): List<TvRecording> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECORDINGS, null) ?: return emptyList()
        val list = mutableListOf<TvRecording>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TvRecording(
                        id = obj.getString("id"),
                        channelId = obj.getInt("channelId"),
                        channelName = obj.getString("channelName"),
                        showTitle = obj.optString("showTitle", "Live Broadcast"),
                        coverUrl = obj.optString("coverUrl", "").takeIf { it != "null" && it.isNotBlank() },
                        streamUrl = obj.getString("streamUrl"),
                        durationSeconds = obj.optInt("durationSeconds", 0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list.sortedByDescending { it.timestamp }
    }

    fun saveRecording(context: Context, recording: TvRecording) {
        val current = getRecordings(context).toMutableList()
        current.removeAll { it.id == recording.id }
        current.add(0, recording)

        val array = JSONArray()
        for (item in current) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("channelId", item.channelId)
                put("channelName", item.channelName)
                put("showTitle", item.showTitle)
                put("coverUrl", item.coverUrl ?: "")
                put("streamUrl", item.streamUrl)
                put("durationSeconds", item.durationSeconds)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECORDINGS, array.toString())
            .commit()
    }

    fun deleteRecording(context: Context, id: String) {
        val current = getRecordings(context)
        val toDelete = current.find { it.id == id }
        if (toDelete != null) {
            try {
                val file = File(toDelete.streamUrl)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
        val updated = current.filterNot { it.id == id }
        val array = JSONArray()
        for (item in updated) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("channelId", item.channelId)
                put("channelName", item.channelName)
                put("showTitle", item.showTitle)
                put("coverUrl", item.coverUrl ?: "")
                put("streamUrl", item.streamUrl)
                put("durationSeconds", item.durationSeconds)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECORDINGS, array.toString())
            .commit()
    }
}
