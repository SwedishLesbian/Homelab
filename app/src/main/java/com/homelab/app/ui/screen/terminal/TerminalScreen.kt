package com.homelab.app.ui.screen.terminal

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.homelab.app.data.model.SessionStatus
import com.homelab.app.ui.theme.TerminalBackground
import com.homelab.app.ui.theme.TerminalText
import com.homelab.app.ui.theme.HomelabGreen

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

    LaunchedEffect(state.outputLines.size) {
        if (state.outputLines.isNotEmpty()) {
            listState.animateScrollToItem(state.outputLines.size - 1)
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
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                viewModel.disconnect()
                onBack()
            }) {
                Icon(Icons.Default.ArrowBack, "Back", tint = TerminalText)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.hostName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TerminalText
                )
                Text(
                    text = state.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (state.status) {
                        SessionStatus.CONNECTED -> HomelabGreen
                        SessionStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> TerminalText.copy(alpha = 0.6f)
                    }
                )
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
                    lineHeight = 18.sp,
                    color = TerminalText
                )
            }

            state.errorMessage?.let { error ->
                item {
                    Text(
                        text = error,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Keyboard toolbar
        if (state.status == SessionStatus.CONNECTED) {
            SpecialKeyRow(
                onKey = viewModel::sendSpecialKey,
                modifier = Modifier.fillMaxWidth()
            )

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
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
                    singleLine = true
                )
                IconButton(onClick = {
                    viewModel.sendInput(inputText + "\n")
                    inputText = ""
                }) {
                    Icon(Icons.Default.Close, "Send", tint = HomelabGreen)
                }
            }
        }
    }
}

@Composable
private fun SpecialKeyRow(onKey: (SpecialKey) -> Unit, modifier: Modifier = Modifier) {
    val keys = listOf(
        "Ctrl+C" to SpecialKey.CTRL_C,
        "Ctrl+D" to SpecialKey.CTRL_D,
        "Ctrl+Z" to SpecialKey.CTRL_Z,
        "Tab" to SpecialKey.TAB,
        "Esc" to SpecialKey.ESCAPE,
        "↑" to SpecialKey.UP,
        "↓" to SpecialKey.DOWN,
        "←" to SpecialKey.LEFT,
        "→" to SpecialKey.RIGHT
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
