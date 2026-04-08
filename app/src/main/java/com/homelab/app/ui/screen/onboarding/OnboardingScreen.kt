package com.homelab.app.ui.screen.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private const val TAILSCALE_OAUTH_URL = "https://login.tailscale.com/admin/settings/oauth"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OnboardingScreen(
    onAuthenticated: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current
    var secretVisible by remember { mutableStateOf(false) }

    // Register autofill nodes so password managers (Bitwarden, 1Password, etc.) can
    // recognise Client ID as "username" and Client Secret as "password" and save/fill them.
    val clientIdNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Username),
            onFill = { viewModel.onClientIdChanged(it) }
        )
    }
    val clientSecretNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.Password),
            onFill = { viewModel.onClientSecretChanged(it) }
        )
    }
    autofillTree += clientIdNode
    autofillTree += clientSecretNode

    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onAuthenticated()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Homelab",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "SSH for your Tailscale network",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(48.dp))

        Text(
            "Create an OAuth client in your Tailscale admin console with scope devices:read, then paste the credentials below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { uriHandler.openUri(TAILSCALE_OAUTH_URL) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open Tailscale Admin Console")
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.clientIdInput,
            onValueChange = viewModel::onClientIdChanged,
            label = { Text("OAuth Client ID") },
            placeholder = { Text("k123456CNTRL...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { clientIdNode.boundingBox = it.boundsInWindow() }
                .onFocusChanged { if (it.isFocused) autofill?.requestAutofillForNode(clientIdNode) }
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.clientSecretInput,
            onValueChange = viewModel::onClientSecretChanged,
            label = { Text("OAuth Client Secret") },
            placeholder = { Text("tskey-client-...") },
            singleLine = true,
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            visualTransformation = if (secretVisible) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { secretVisible = !secretVisible }) {
                    Text(
                        if (secretVisible) "Hide" else "Show",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { viewModel.connect() }),
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { clientSecretNode.boundingBox = it.boundsInWindow() }
                .onFocusChanged { if (it.isFocused) autofill?.requestAutofillForNode(clientSecretNode) }
        )

        Spacer(Modifier.height(20.dp))

        if (state.isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Button(
                onClick = viewModel::connect,
                enabled = state.clientIdInput.isNotBlank() && state.clientSecretInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Connect", fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(32.dp))
        Text(
            "Your SSH keys never leave your device.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}
