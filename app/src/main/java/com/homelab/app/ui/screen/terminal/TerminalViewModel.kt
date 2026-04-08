package com.homelab.app.ui.screen.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.model.Host
import com.homelab.app.data.model.Session
import com.homelab.app.data.model.SessionStatus
import com.homelab.app.data.repository.HostRepository
import com.homelab.app.data.repository.SshKeyRepository
import com.homelab.app.data.repository.SessionRepository
import com.homelab.app.ssh.AuthMethod
import com.homelab.app.ssh.AuthParams
import com.homelab.app.ssh.AuthParamsStore
import com.homelab.app.ssh.SshManager
import com.homelab.app.ssh.SshSession
import com.homelab.app.ssh.TerminalEmulator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

private const val MAX_RECONNECT_ATTEMPTS = 5
private val RECONNECT_DELAYS_MS = listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L)

data class TerminalState(
    val status: SessionStatus = SessionStatus.CONNECTING,
    val outputLines: List<androidx.compose.ui.text.AnnotatedString> = emptyList(),
    val hostName: String = "",
    val errorMessage: String? = null,
    val keyDeployed: Boolean = false
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sshManager: SshManager,
    private val hostRepository: HostRepository,
    private val sessionRepository: SessionRepository,
    private val authParamsStore: AuthParamsStore,
    private val sshKeyRepository: SshKeyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private var sshSession: SshSession? = null
    private val emulator = TerminalEmulator()
    private var currentSessionId: String = ""
    private var cachedHost: Host? = null
    private var cachedAuthParams: AuthParams? = null

    fun connect(sessionId: String, hostId: String) {
        currentSessionId = sessionId
        viewModelScope.launch {
            val host = hostRepository.getAllHosts().firstOrNull()?.find { it.id == hostId }
                ?: return@launch
            cachedHost = host

            // Look up auth params from the store (set by HomeViewModel's bottom sheet)
            val authParams = authParamsStore.get(sessionId)
            if (authParams == null) {
                // Fallback: use host's stored config
                cachedAuthParams = AuthParams(
                    username = host.sshUsername ?: "root",
                    authMethod = if (host.sshKeyId != null) AuthMethod.SSH_KEY else AuthMethod.TAILSCALE_SSH,
                    keyId = host.sshKeyId
                )
            } else {
                cachedAuthParams = authParams
                authParamsStore.remove(sessionId)
            }

            _state.update { it.copy(hostName = host.name) }
            doConnect(host, attempt = 0)
        }
    }

    private suspend fun doConnect(host: Host, attempt: Int) {
        val authParams = cachedAuthParams ?: return

        _state.update {
            it.copy(
                status = if (attempt == 0) SessionStatus.CONNECTING else SessionStatus.RECONNECTING,
                errorMessage = null
            )
        }
        saveSession(host, if (attempt == 0) SessionStatus.CONNECTING else SessionStatus.RECONNECTING)

        sshManager.connect(
            sessionId = currentSessionId,
            host = host,
            authParams = authParams
        ).onSuccess { session ->
            sshSession = session
            _state.update { it.copy(status = SessionStatus.CONNECTED) }
            saveSession(host, SessionStatus.CONNECTED)
            hostRepository.updateLastConnected(host.id)

            // Deploy public key if requested
            if (authParams.deployKeyId != null) {
                deployPublicKey(session, authParams.deployKeyId)
            }

            collectOutputAndReconnect(host, session)
        }.onFailure { e ->
            if (attempt < MAX_RECONNECT_ATTEMPTS) {
                val delayMs = RECONNECT_DELAYS_MS[attempt.coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)]
                _state.update {
                    it.copy(
                        status = SessionStatus.RECONNECTING,
                        errorMessage = "Retrying in ${delayMs / 1000}s... (${e.message})"
                    )
                }
                delay(delayMs)
                doConnect(host, attempt + 1)
            } else {
                _state.update {
                    it.copy(
                        status = SessionStatus.FAILED,
                        errorMessage = e.message ?: "Connection failed"
                    )
                }
                saveSession(host, SessionStatus.FAILED)
            }
        }
    }

    private fun deployPublicKey(session: SshSession, keyId: String) {
        viewModelScope.launch {
            sshKeyRepository.getAllKeys().firstOrNull()?.find { it.id == keyId }?.let { key ->
                val pubKey = key.publicKey
                val cmd = "mkdir -p ~/.ssh && echo '$pubKey' >> ~/.ssh/authorized_keys && chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys\n"
                session.sendInput(cmd)

                // Update host to use this key for future connections
                cachedHost?.let { host ->
                    hostRepository.updateSshConfig(host.id, cachedAuthParams?.username, keyId)
                }
                _state.update { it.copy(keyDeployed = true) }
            }
        }
    }

    private fun collectOutputAndReconnect(host: Host, session: SshSession) {
        viewModelScope.launch {
            session.outputFlow.collect { chunk ->
                emulator.process(chunk)
                _state.update { it.copy(outputLines = emulator.getCurrentContent()) }
            }
            // Flow ended means the connection dropped
            if (_state.value.status == SessionStatus.CONNECTED) {
                doConnect(host, attempt = 0)
            }
        }
    }

    fun sendInput(input: String) {
        viewModelScope.launch { sshSession?.sendInput(input) }
    }

    fun sendSpecialKey(key: SpecialKey) {
        val seq = when (key) {
            SpecialKey.CTRL_C -> "\u0003"
            SpecialKey.CTRL_D -> "\u0004"
            SpecialKey.CTRL_Z -> "\u001A"
            SpecialKey.TAB    -> "\t"
            SpecialKey.ESCAPE -> "\u001B"
            SpecialKey.UP     -> "\u001B[A"
            SpecialKey.DOWN   -> "\u001B[B"
            SpecialKey.LEFT   -> "\u001B[D"
            SpecialKey.RIGHT  -> "\u001B[C"
        }
        viewModelScope.launch { sshSession?.sendInput(seq) }
    }

    fun onTerminalResize(cols: Int, rows: Int) {
        viewModelScope.launch { sshSession?.resize(cols, rows) }
    }

    fun disconnect() {
        sshManager.disconnectSession(currentSessionId)
        viewModelScope.launch {
            sessionRepository.updateStatus(currentSessionId, SessionStatus.DISCONNECTED)
        }
    }

    private suspend fun saveSession(host: Host, status: SessionStatus) {
        sessionRepository.saveSession(
            Session(
                id = currentSessionId,
                hostId = host.id,
                hostName = host.name,
                hostIp = host.ip,
                username = cachedAuthParams?.username ?: "root",
                status = status,
                lastActive = Instant.now()
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        sshManager.disconnectSession(currentSessionId)
    }
}

enum class SpecialKey { CTRL_C, CTRL_D, CTRL_Z, TAB, ESCAPE, UP, DOWN, LEFT, RIGHT }
