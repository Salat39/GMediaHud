package com.salat.gmediahud.components

import android.graphics.Bitmap
import androidx.core.graphics.get

fun hasBitmapChanged(oldBitmap: Bitmap?, newBitmap: Bitmap?): Boolean {
    // If both references point to the same object, no changes occurred
    if (oldBitmap === newBitmap) return false

    // If either bitmap is null, consider it a change
    if (oldBitmap == null || newBitmap == null) return true

    // Check basic parameters
    if (oldBitmap.width != newBitmap.width ||
        oldBitmap.height != newBitmap.height ||
        oldBitmap.config != newBitmap.config
    ) {
        return true
    }

    // Select key points for comparison
    val positions = listOf(
        Pair(0, 0),                                             // top-left corner
        Pair(oldBitmap.width - 1, 0),                           // top-right corner
        Pair(0, oldBitmap.height - 1),                          // bottom-left corner
        Pair(oldBitmap.width - 1, oldBitmap.height - 1),        // bottom-right corner
        Pair(oldBitmap.width / 2, oldBitmap.height / 2)         // center
    )

    // Compare selected pixels
    for ((x, y) in positions) {
        if (oldBitmap[x, y] != newBitmap[x, y]) {
            return true
        }
    }

    return false
}
