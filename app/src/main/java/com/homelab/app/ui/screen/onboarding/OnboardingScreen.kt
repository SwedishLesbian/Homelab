package com.homelab.app.ui.screen.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun OnboardingScreen(
    onAuthenticated: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current

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

        Spacer(Modifier.height(56.dp))

        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            },
            label = "onboarding_step"
        ) { step ->
            when (step) {
                OnboardingStep.CLIENT_ID -> ClientIdStep(
                    clientId = state.clientIdInput,
                    error = state.error,
                    onClientIdChanged = viewModel::onClientIdChanged,
                    onContinue = viewModel::saveClientId,
                    onOpenTailscale = { uriHandler.openUri(TAILSCALE_OAUTH_URL) }
                )
                OnboardingStep.CLIENT_SECRET -> ClientSecretStep(
                    clientSecret = state.clientSecretInput,
                    isLoading = state.isLoading,
                    error = state.error,
                    onClientSecretChanged = viewModel::onClientSecretChanged,
                    onConnect = viewModel::connect,
                    onBack = viewModel::goBackToClientId
                )
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

@Composable
private fun ClientIdStep(
    clientId: String,
    error: String?,
    onClientIdChanged: (String) -> Unit,
    onContinue: () -> Unit,
    onOpenTailscale: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Step 1 of 2 — Client ID",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Create an OAuth client in your Tailscale admin console (scope: devices:read), then paste the client ID below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onOpenTailscale,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.OpenInBrowser, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open Tailscale Admin Console")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = clientId,
            onValueChange = onClientIdChanged,
            label = { Text("OAuth Client ID") },
            placeholder = { Text("k123456CNTRL...") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password, // prevents autocorrect
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onContinue() }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onContinue,
            enabled = clientId.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Continue", fontSize = 16.sp)
        }
    }
}

@Composable
private fun ClientSecretStep(
    clientSecret: String,
    isLoading: Boolean,
    error: String?,
    onClientSecretChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit
) {
    var secretVisible by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Step 2 of 2 — Client Secret",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Paste the client secret shown in the Tailscale admin console when you created the OAuth client.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = clientSecret,
            onValueChange = onClientSecretChanged,
            label = { Text("OAuth Client Secret") },
            placeholder = { Text("tskey-client-...") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            visualTransformation = if (secretVisible) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { secretVisible = !secretVisible }) {
                    Text(if (secretVisible) "Hide" else "Show",
                        style = MaterialTheme.typography.labelSmall)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onConnect() }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Button(
                onClick = onConnect,
                enabled = clientSecret.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Connect", fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Change client ID")
        }
    }
}
