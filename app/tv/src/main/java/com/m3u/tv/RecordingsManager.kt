package com.m3u.tv

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
            val mins = durationSeconds / 60
            val secs = durationSeconds % 60
            return String.format("%02d:%02d", mins, secs)
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

    private val trustAllCert = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val trustAllSslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustAllCert), SecureRandom())
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .sslSocketFactory(trustAllSslContext.socketFactory, trustAllCert)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    fun start(context: Context, recId: String, streamUrl: String, targetHeight: Int = 0): String {
        stop()
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "rec_${recId}.ts")
        try {
            if (!file.exists()) file.createNewFile()
        } catch (_: Exception) {}
        currentFile = file

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                recordStream(streamUrl, file, targetHeight)
            } catch (e: Exception) {
                e.printStackTrace()
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

    private fun resolveUrl(baseUrl: String, pathOrUrl: String): String {
        val resolved = try {
            URL(URL(baseUrl), pathOrUrl).toString()
        } catch (_: Exception) {
            if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
                pathOrUrl
            } else {
                val baseWithoutFile = baseUrl.substringBefore('?').substringBeforeLast('/') + "/"
                baseWithoutFile + pathOrUrl.removePrefix("/")
            }
        }
        return if (!pathOrUrl.contains('?') && baseUrl.contains('?')) {
            val query = baseUrl.substringAfter('?')
            if (resolved.contains('?')) resolved else "$resolved?$query"
        } else {
            resolved
        }
    }

    private data class HlsStreamVariant(
        val url: String,
        val width: Int,
        val height: Int,
        val bandwidth: Long
    )

    private fun selectBestVariantUrl(baseUrl: String, playlistText: String, targetHeight: Int): String {
        val variants = mutableListOf<HlsStreamVariant>()
        val lines = playlistText.lines()
        var lastStreamInf: String? = null
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-STREAM-INF:")) {
                lastStreamInf = trimmed
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                if (lastStreamInf != null) {
                    val resMatch = Regex("""RESOLUTION=(\d+)x(\d+)""").find(lastStreamInf)
                    val w = resMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val h = resMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                    val bwMatch = Regex("""BANDWIDTH=(\d+)""").find(lastStreamInf)
                    val bw = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val resolvedUrl = resolveUrl(baseUrl, trimmed)
                    variants.add(HlsStreamVariant(resolvedUrl, w, h, bw))
                    lastStreamInf = null
                } else {
                    val resolvedUrl = resolveUrl(baseUrl, trimmed)
                    variants.add(HlsStreamVariant(resolvedUrl, 0, 0, 0L))
                }
            }
        }

        if (variants.isEmpty()) return baseUrl

        if (targetHeight > 0) {
            val exact = variants.filter { it.height == targetHeight }
            if (exact.isNotEmpty()) {
                return exact.maxByOrNull { it.bandwidth }?.url ?: exact.first().url
            }
            val closest = variants.filter { it.height > 0 }.minByOrNull { kotlin.math.abs(it.height - targetHeight) }
            if (closest != null) {
                return closest.url
            }
        }

        val highest = variants.maxByOrNull {
            if (it.height > 0) it.height.toLong() * 10_000_000L + it.bandwidth else it.bandwidth
        }
        return highest?.url ?: variants.first().url
    }

    private suspend fun recordStream(streamUrl: String, outputFile: File, targetHeight: Int = 0) {
        val request = Request.Builder()
            .url(streamUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android TV) TeleMate/1.0")
            .header("Accept", "*/*")
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            response.close()
            return
        }

        val body = response.body ?: run {
            response.close()
            return
        }

        val finalUrl = response.request.url.toString()
        val contentType = response.header("Content-Type").orEmpty()
        val peekBytes = try { response.peekBody(1024).string().trimStart() } catch (_: Exception) { "" }

        val isHls = streamUrl.contains(".m3u8", ignoreCase = true) ||
                finalUrl.contains(".m3u8", ignoreCase = true) ||
                contentType.contains("mpegurl", ignoreCase = true) ||
                contentType.contains("application/x-mpegurl", ignoreCase = true) ||
                peekBytes.startsWith("#EXTM3U")

        if (isHls) {
            val initialText = withContext(Dispatchers.IO) { body.string() }
            response.close()
            recordHls(finalUrl, initialText, outputFile, targetHeight)
        } else {
            withContext(Dispatchers.IO) {
                body.byteStream().use { input ->
                    FileOutputStream(outputFile, true).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (currentCoroutineContext().isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            response.close()
        }
    }

    private suspend fun recordHls(initialUrl: String, initialText: String, outputFile: File, targetHeight: Int = 0) {
        var mediaPlaylistUrl = initialUrl
        var playlistText = initialText

        if (!playlistText.contains("#EXT-X-TARGETDURATION")) {
            mediaPlaylistUrl = selectBestVariantUrl(initialUrl, playlistText, targetHeight)
        }

        val downloadedSegments = LinkedHashSet<String>()

        while (currentCoroutineContext().isActive) {
            var pollDelayMs = 2000L
            try {
                val req = Request.Builder()
                    .url(mediaPlaylistUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android TV) TeleMate/1.0")
                    .header("Accept", "*/*")
                    .build()

                val resp = withContext(Dispatchers.IO) {
                    client.newCall(req).execute()
                }

                if (resp.isSuccessful) {
                    val mediaText = withContext(Dispatchers.IO) { resp.body?.string().orEmpty() }
                    resp.close()

                    if (mediaText.contains("#EXT-X-STREAM-INF")) {
                        mediaPlaylistUrl = selectBestVariantUrl(mediaPlaylistUrl, mediaText, targetHeight)
                        continue
                    }

                    val targetMatch = Regex("""#EXT-X-TARGETDURATION:(\d+)""").find(mediaText)
                    if (targetMatch != null) {
                        val durSec = targetMatch.groupValues[1].toLongOrNull() ?: 3L
                        pollDelayMs = (durSec * 1000L / 2).coerceIn(1000L, 3500L)
                    }

                    for (line in mediaText.lines()) {
                        if (!currentCoroutineContext().isActive) break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) continue

                        if (trimmed.startsWith("#EXT-X-MAP:")) {
                            val mapUri = trimmed.substringAfter("URI=\"", "").substringBefore("\"", "")
                            if (mapUri.isNotEmpty()) {
                                val mapUrl = resolveUrl(mediaPlaylistUrl, mapUri)
                                if (downloadedSegments.add(mapUrl)) {
                                    downloadChunk(mapUrl, outputFile)
                                }
                            }
                            continue
                        }

                        if (trimmed.startsWith("#")) continue

                        val segUrl = resolveUrl(mediaPlaylistUrl, trimmed)
                        if (downloadedSegments.add(segUrl)) {
                            downloadChunk(segUrl, outputFile)
                        }
                    }

                    if (downloadedSegments.size > 200) {
                        val it = downloadedSegments.iterator()
                        if (it.hasNext()) {
                            it.next()
                            it.remove()
                        }
                    }
                } else {
                    resp.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(pollDelayMs)
        }
    }

    private suspend fun downloadChunk(url: String, outputFile: File) {
        try {
            val segReq = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android TV) TeleMate/1.0")
                .header("Accept", "*/*")
                .build()

            val segResp = withContext(Dispatchers.IO) {
                client.newCall(segReq).execute()
            }

            if (segResp.isSuccessful) {
                withContext(Dispatchers.IO) {
                    segResp.body?.byteStream()?.use { input ->
                        FileOutputStream(outputFile, true).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
            segResp.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

object RecordingsManager {
    private const val PREFS_NAME = "tivimate_recordings_pref"
    private const val KEY_RECORDINGS = "saved_recordings"

    fun getRecordings(context: Context): List<TvRecording> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECORDINGS, null) ?: return emptyList()
        val list = mutableListOf<TvRecording>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                var streamUrl = obj.getString("streamUrl")
                val localRecFile = File(context.filesDir, "recordings/rec_${id}.ts")
                if (localRecFile.exists() && localRecFile.length() > 0L) {
                    streamUrl = localRecFile.absolutePath
                } else if (!streamUrl.startsWith("/")) {
                    streamUrl = localRecFile.absolutePath
                }
                list.add(
                    TvRecording(
                        id = id,
                        channelId = obj.getInt("channelId"),
                        channelName = obj.getString("channelName"),
                        showTitle = obj.optString("showTitle", "Live Broadcast"),
                        coverUrl = obj.optString("coverUrl", "").takeIf { it != "null" && it.isNotBlank() },
                        streamUrl = streamUrl,
                        durationSeconds = obj.optInt("durationSeconds", 0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveRecording(context: Context, recording: TvRecording) {
        val current = getRecordings(context).toMutableList()
        val sanitizedStreamUrl = if (recording.streamUrl.startsWith("/")) {
            recording.streamUrl
        } else {
            File(context.filesDir, "recordings/rec_${recording.id}.ts").absolutePath
        }
        val sanitizedRecording = recording.copy(streamUrl = sanitizedStreamUrl)
        current.removeAll { it.id == sanitizedRecording.id }
        current.add(0, sanitizedRecording)
        saveRecordingsList(context, current)
    }

    fun deleteRecording(context: Context, id: String) {
        val current = getRecordings(context)
        val toDelete = current.find { it.id == id }
        if (toDelete != null) {
            try {
                val f1 = File(toDelete.streamUrl)
                if (f1.exists()) f1.delete()
            } catch (_: Exception) {}
            try {
                val f2 = File(context.filesDir, "recordings/rec_${toDelete.id}.ts")
                if (f2.exists()) f2.delete()
            } catch (_: Exception) {}
        }
        val updated = current.filterNot { it.id == id }
        saveRecordingsList(context, updated)
    }

    private fun saveRecordingsList(context: Context, list: List<TvRecording>) {
        val array = JSONArray()
        for (rec in list) {
            val obj = JSONObject().apply {
                put("id", rec.id)
                put("channelId", rec.channelId)
                put("channelName", rec.channelName)
                put("showTitle", rec.showTitle)
                put("coverUrl", rec.coverUrl ?: "")
                put("streamUrl", rec.streamUrl)
                put("durationSeconds", rec.durationSeconds)
                put("timestamp", rec.timestamp)
            }
            array.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RECORDINGS, array.toString()).apply()
    }
}
