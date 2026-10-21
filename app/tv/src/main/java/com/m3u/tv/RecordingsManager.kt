package com.m3u.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
        val current = getRecordings(context).filterNot { it.id == id }
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
}
