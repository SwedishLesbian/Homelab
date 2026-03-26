package com.homelab.app.ui.screen.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.model.Host
import com.homelab.app.data.model.Session
import com.homelab.app.data.model.SessionStatus
import com.homelab.app.data.repository.HostRepository
import com.homelab.app.data.repository.SessionRepository
import com.homelab.app.ssh.SshManager
import com.homelab.app.ssh.SshSession
import com.homelab.app.ssh.TerminalEmulator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class TerminalState(
    val status: SessionStatus = SessionStatus.CONNECTING,
    val outputLines: List<androidx.compose.ui.text.AnnotatedString> = emptyList(),
    val inputText: String = "",
    val hostName: String = "",
    val errorMessage: String? = null
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sshManager: SshManager,
    private val hostRepository: HostRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private var sshSession: SshSession? = null
    private val emulator = TerminalEmulator()
    private var currentSessionId: String = ""
    private var currentHostId: String = ""

    fun connect(sessionId: String, hostId: String) {
        currentSessionId = sessionId
        currentHostId = hostId
        viewModelScope.launch {
            val hostEntity = hostRepository.getAllHosts().firstOrNull()
                ?.find { it.id == hostId } ?: return@launch

            _state.update { it.copy(status = SessionStatus.CONNECTING, hostName = hostEntity.name) }

            saveSession(sessionId, hostEntity, SessionStatus.CONNECTING)

            val username = hostEntity.sshUsername ?: "root"
            val keyId = hostEntity.sshKeyId

            if (keyId == null) {
                _state.update {
                    it.copy(
                        status = SessionStatus.FAILED,
                        errorMessage = "No SSH key configured. Set one up in host settings."
                    )
                }
                return@launch
            }

            sshManager.connect(
                sessionId = sessionId,
                host = hostEntity,
                username = username,
                keyId = keyId,
                onHostKeyVerification = { true }
            ).onSuccess { session ->
                sshSession = session
                _state.update { it.copy(status = SessionStatus.CONNECTED) }
                saveSession(sessionId, hostEntity, SessionStatus.CONNECTED)
                hostRepository.updateLastConnected(hostId)
                collectOutput(session)
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        status = SessionStatus.FAILED,
                        errorMessage = e.message ?: "Connection failed"
                    )
                }
                saveSession(sessionId, hostEntity, SessionStatus.FAILED)
            }
        }
    }

    private fun collectOutput(session: SshSession) {
        viewModelScope.launch {
            session.outputFlow.collect { chunk ->
                emulator.process(chunk)
                _state.update { it.copy(outputLines = emulator.getCurrentContent()) }
            }
            // Flow ended = disconnected
            _state.update { it.copy(status = SessionStatus.DISCONNECTED) }
        }
    }

    fun sendInput(input: String) {
        viewModelScope.launch {
            sshSession?.sendInput(input)
        }
    }

    fun sendSpecialKey(key: SpecialKey) {
        val sequence = when (key) {
            SpecialKey.CTRL_C -> "\u0003"
            SpecialKey.CTRL_D -> "\u0004"
            SpecialKey.CTRL_Z -> "\u001A"
            SpecialKey.TAB -> "\t"
            SpecialKey.ESCAPE -> "\u001B"
            SpecialKey.UP -> "\u001B[A"
            SpecialKey.DOWN -> "\u001B[B"
            SpecialKey.LEFT -> "\u001B[D"
            SpecialKey.RIGHT -> "\u001B[C"
        }
        viewModelScope.launch { sshSession?.sendInput(sequence) }
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

    private suspend fun saveSession(id: String, host: Host, status: SessionStatus) {
        sessionRepository.saveSession(
            Session(
                id = id, hostId = host.id, hostName = host.name, hostIp = host.ip,
                username = host.sshUsername ?: "root", status = status,
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
