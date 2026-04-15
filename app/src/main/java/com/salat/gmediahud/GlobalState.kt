package com.salat.gmediahud

import com.salat.gmediahud.entity.ExternalTask
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

object GlobalState {
    val externalJobTask = MutableSharedFlow<ExternalTask>(
        extraBufferCapacity = 1,
        replay = 0
    )
    val externalCleanTask = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        replay = 0
    )

    val enableByAudioSource = MutableStateFlow(true)
    val canAccessibility = MutableStateFlow(false)
    var lastAccessibilityEventTimestamp = 0L
}
