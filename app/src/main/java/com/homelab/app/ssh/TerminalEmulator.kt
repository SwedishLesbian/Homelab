package com.homelab.app.ssh

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Minimal ANSI escape code parser for terminal output.
 * Converts raw terminal output (with ANSI codes) into AnnotatedString for Compose rendering.
 */
class TerminalEmulator {
    val lines = mutableListOf<AnnotatedString>()
    private val scrollbackLimit = 10_000
    private val currentLine = StringBuilder()
    private var currentStyle = TerminalStyle()

    fun process(input: String) {
        var i = 0
        while (i < input.length) {
            when {
                input[i] == '\r' -> { i++ }
                input[i] == '\n' -> {
                    commitLine()
                    i++
                }
                input[i] == '\u001B' && i + 1 < input.length && input[i + 1] == '[' -> {
                    val end = input.indexOf('m', i + 2)
                    if (end != -1) {
                        val codes = input.substring(i + 2, end).split(";")
                        applyAnsiCodes(codes)
                        i = end + 1
                    } else {
                        i++
                    }
                }
                input[i] == '\u001B' -> {
                    // Skip other escape sequences
                    i += 2
                }
                else -> {
                    currentLine.append(input[i])
                    i++
                }
            }
        }
    }

    private fun commitLine() {
        lines.add(buildLineAnnotatedString())
        if (lines.size > scrollbackLimit) lines.removeFirst()
        currentLine.clear()
    }

    private fun buildLineAnnotatedString(): AnnotatedString = buildAnnotatedString {
        withStyle(currentStyle.toSpanStyle()) {
            append(currentLine.toString())
        }
    }

    private fun applyAnsiCodes(codes: List<String>) {
        codes.forEach { code ->
            when (code.trim()) {
                "0", "" -> currentStyle = TerminalStyle()
                "1" -> currentStyle = currentStyle.copy(bold = true)
                "3" -> currentStyle = currentStyle.copy(italic = true)
                "30" -> currentStyle = currentStyle.copy(foreground = Color.Black)
                "31" -> currentStyle = currentStyle.copy(foreground = Color(0xFFFF5555))
                "32" -> currentStyle = currentStyle.copy(foreground = Color(0xFF55FF55))
                "33" -> currentStyle = currentStyle.copy(foreground = Color(0xFFFFFF55))
                "34" -> currentStyle = currentStyle.copy(foreground = Color(0xFF5555FF))
                "35" -> currentStyle = currentStyle.copy(foreground = Color(0xFFFF55FF))
                "36" -> currentStyle = currentStyle.copy(foreground = Color(0xFF55FFFF))
                "37" -> currentStyle = currentStyle.copy(foreground = Color.White)
            }
        }
    }

    fun getCurrentContent(): List<AnnotatedString> = lines.toList()
}

data class TerminalStyle(
    val foreground: Color = Color.White,
    val background: Color = Color.Transparent,
    val bold: Boolean = false,
    val italic: Boolean = false
) {
    fun toSpanStyle() = SpanStyle(
        color = foreground,
        background = background,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
}
