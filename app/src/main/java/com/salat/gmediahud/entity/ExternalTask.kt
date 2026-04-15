package com.salat.gmediahud.entity

data class ExternalTask(
    val tag: String,
    val title: String,
    val subtitle: String,
    val art: String,
    val duration: Long,
    val inQueue: Boolean,
    val withWarning: Boolean,
    val withMediaPause: Boolean,
    val withToast: Boolean,
    val time: Long,
    val sourceType: Int,
    val progress: Int,
    val maxProgress: Int
)
