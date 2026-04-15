package com.salat.gmediahud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.salat.gmediahud.entity.CustomSourceType
import com.salat.gmediahud.entity.ExternalTask
import kotlinx.coroutines.flow.update

@android.annotation.SuppressLint("ExportedReceiver")
class BackgroundTaskReceiver : BroadcastReceiver() {

    companion object {
        private const val BASE_PATH = "com.salat.gmediahud"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "$BASE_PATH.SHOW" -> {
                val title = intent.getStringExtra("title") ?: ""
                val subtitle = intent.getStringExtra("subtitle") ?: ""
                val art = intent.getStringExtra("art") ?: ""
                val duration = intent.getIntExtra("duration", 3)
                val params = (intent.getStringExtra("params") ?: "").parseParameters()

                val sourceType = params
                    .getOrDefault("source", CustomSourceType.ONLINE.toString())
                    .toIntOrNull() ?: CustomSourceType.ONLINE
                val progress = params
                    .getOrDefault("progress", "0")
                    .toIntOrNull() ?: 0
                val maxProgress = params
                    .getOrDefault("max_progress", "100")
                    .toIntOrNull() ?: 100

                GlobalState.externalJobTask.tryEmit(
                    ExternalTask(
                        tag = params.getOrDefault("tag", ""),
                        title = title,
                        subtitle = subtitle,
                        art = art,
                        duration = duration.toLong(),
                        inQueue = params.getOrDefault("queue", "1") == "1",
                        withWarning = params.getOrDefault("warning", "0") == "1",
                        withMediaPause = params.getOrDefault("pause", "0") == "1",
                        withToast = params.getOrDefault("toast", "0") == "1",
                        time = System.nanoTime(),
                        sourceType = sourceType,
                        progress = progress,
                        maxProgress = maxProgress
                    )
                )
            }

            "$BASE_PATH.HIDE" -> {
                val tag = intent.getStringExtra("tag") ?: ""
                GlobalState.externalCleanTask.tryEmit(tag)
            }

            "$BASE_PATH.UPDATE_AUDIO_SOURCE" -> {
                val source = intent.getStringExtra("source") ?: ""
                val enableByAudioSource = when (source) {
                    "USB", "BT", "RADIO", "CPAA" -> false
                    else -> true
                }
                GlobalState.enableByAudioSource.update { enableByAudioSource }
            }

            else -> {}
        }
    }

    private fun String.parseParameters(): Map<String, String> {
        // If the input string is empty or contains only whitespace, return an empty map
        if (isBlank()) return emptyMap()

        return split(",")           // Split the string by commas
            .map { it.trim() }      // Remove extra spaces around each parameter
            .mapNotNull { param ->
                // Find the first occurrence of the "=" symbol
                val equalPos = param.indexOf("=")
                // If the separator is missing or the key is empty, skip this fragment
                if (equalPos <= 0) return@mapNotNull null

                // Get the key and value, removing extra spaces
                val key = param.substring(0, equalPos).trim()
                val value = param.substring(equalPos + 1).trim()

                // If the key is not empty, return the pair; otherwise, ignore the element
                if (key.isEmpty()) null else key to value
            }.toMap()  // Convert the list of pairs to a Map
    }
}
