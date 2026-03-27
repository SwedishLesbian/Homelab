package com.homelab.app.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CLIPBOARD_CLEAR_DELAY_MS = 30_000L

/**
 * Copies [text] to the system clipboard then schedules a clear after 30 seconds.
 * Satisfies PRD security requirement: "Clipboard auto-clear (30s)".
 */
fun Context.copyAndScheduleClear(
    text: String,
    label: String = "Homelab",
    scope: CoroutineScope
) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    scope.launch {
        delay(CLIPBOARD_CLEAR_DELAY_MS)
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}
