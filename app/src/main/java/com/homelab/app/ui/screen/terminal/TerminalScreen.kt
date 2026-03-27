package com.homelab.app.ui.screen.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homelab.app.data.model.SessionStatus
import com.homelab.app.data.security.BiometricHelper
import com.homelab.app.data.security.BiometricResult
import com.homelab.app.ui.theme.HomelabGreen
import com.homelab.app.ui.theme.TerminalBackground
import com.homelab.app.ui.theme.TerminalText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    sessionId: String,
    hostId: String,
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(sessionId, hostId) {
        viewModel.connect(sessionId, hostId)
    }

    // Auto-scroll to bottom on new output
    LaunchedEffect(state.outputLines.size) {
        if (state.outputLines.isNotEmpty()) {
            listState.animateScrollToItem(state.outputLines.size - 1)
        }
    }

    // Auto-focus input when connected
    LaunchedEffect(state.status) {
        if (state.status == SessionStatus.CONNECTED) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                viewModel.disconnect()
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TerminalText)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.hostName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TerminalText
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIndicator(state.status)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = state.status.displayName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = state.status.color()
                    )
                }
            }
        }

        // Terminal output
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(state.outputLines) { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    softWrap = true
                )
            }
            state.errorMessage?.let { error ->
                item {
                    Text(
                        text = error,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Reconnect banner
        AnimatedVisibility(
            visible = state.status == SessionStatus.RECONNECTING,
            enter = fadeIn(), exit = fadeOut()
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Reconnecting\u2026", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Special key toolbar + input — only when connected
        AnimatedVisibility(visible = state.status == SessionStatus.CONNECTED) {
            Column {
                SpecialKeyRow(
                    onKey = viewModel::sendSpecialKey,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = TerminalText
                        ),
                        cursorBrush = SolidColor(HomelabGreen),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Send
                        ),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (inputText.isEmpty()) {
                                Text("Type command\u2026",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = TerminalText.copy(alpha = 0.3f)
                                )
                            }
                            inner()
                        }
                    )
                    IconButton(onClick = {
                        viewModel.sendInput(inputText + "\n")
                        inputText = ""
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = HomelabGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(status: SessionStatus) {
    val color = status.color()
    Surface(
        modifier = Modifier.size(6.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = color
    ) {}
}

@Composable
private fun SessionStatus.color() = when (this) {
    SessionStatus.CONNECTED -> HomelabGreen
    SessionStatus.CONNECTING, SessionStatus.RECONNECTING -> MaterialTheme.colorScheme.primary
    SessionStatus.FAILED -> MaterialTheme.colorScheme.error
    SessionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
}

private fun SessionStatus.displayName() = when (this) {
    SessionStatus.CONNECTING -> "Connecting\u2026"
    SessionStatus.CONNECTED -> "Connected"
    SessionStatus.RECONNECTING -> "Reconnecting\u2026"
    SessionStatus.FAILED -> "Failed"
    SessionStatus.DISCONNECTED -> "Disconnected"
}

@Composable
private fun SpecialKeyRow(onKey: (SpecialKey) -> Unit, modifier: Modifier = Modifier) {
    val keys = listOf(
        "Ctrl+C" to SpecialKey.CTRL_C,
        "Ctrl+D" to SpecialKey.CTRL_D,
        "Ctrl+Z" to SpecialKey.CTRL_Z,
        "Tab" to SpecialKey.TAB,
        "Esc" to SpecialKey.ESCAPE,
        "\u2191" to SpecialKey.UP,
        "\u2193" to SpecialKey.DOWN,
        "\u2190" to SpecialKey.LEFT,
        "\u2192" to SpecialKey.RIGHT
    )
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keys.forEach { (label, key) ->
            OutlinedButton(
                onClick = { onKey(key) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
