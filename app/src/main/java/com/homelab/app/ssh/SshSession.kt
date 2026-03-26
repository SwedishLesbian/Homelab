package com.homelab.app.ssh

import com.homelab.app.data.model.Host
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.Session.Shell
import java.io.InputStream
import java.io.OutputStream

class SshSession(
    val sessionId: String,
    private val client: SSHClient,
    val host: Host
) {
    private var sshSession: Session? = null
    private var shell: Shell? = null
    private var outputStream: OutputStream? = null
    private val outputChannel = Channel<String>(Channel.UNLIMITED)

    val outputFlow: Flow<String> = outputChannel.receiveAsFlow()

    fun open() {
        sshSession = client.startSession()
        sshSession?.allocatePTY("xterm-256color", 220, 50, 0, 0)
        shell = sshSession?.startShell()
        outputStream = shell?.outputStream
        startReadingOutput(shell?.inputStream)
    }

    private fun startReadingOutput(inputStream: InputStream?) {
        inputStream ?: return
        Thread {
            try {
                val buffer = ByteArray(4096)
                while (true) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    outputChannel.trySend(text)
                }
            } catch (e: Exception) {
                outputChannel.trySend("\r\n[Connection closed]\r\n")
            } finally {
                outputChannel.close()
            }
        }.also { it.isDaemon = true; it.start() }
    }

    suspend fun sendInput(input: String) = withContext(Dispatchers.IO) {
        outputStream?.write(input.toByteArray(Charsets.UTF_8))
        outputStream?.flush()
    }

    suspend fun resize(cols: Int, rows: Int) = withContext(Dispatchers.IO) {
        runCatching { shell?.changeWindowDimensions(cols, rows, 0, 0) }
    }

    val isConnected: Boolean get() = client.isConnected

    fun close() {
        runCatching { shell?.close() }
        runCatching { sshSession?.close() }
        runCatching { client.disconnect() }
    }
}
