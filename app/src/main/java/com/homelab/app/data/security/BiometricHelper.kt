package com.homelab.app.data.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class BiometricResult {
    object Success : BiometricResult()
    data class Failure(val message: String) : BiometricResult()
    object Cancelled : BiometricResult()
}

@Singleton
class BiometricHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isAvailable(): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Launches a biometric prompt and suspends until the user authenticates or cancels.
     * Must be called from a FragmentActivity context.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Authenticate",
        subtitle: String = "Confirm your identity to unlock the SSH key",
        negativeButtonText: String = "Cancel"
    ): BiometricResult = suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(BiometricResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (cont.isActive) {
                    cont.resume(
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) BiometricResult.Cancelled
                        else BiometricResult.Failure(errString.toString())
                    )
                }
            }

            override fun onAuthenticationFailed() {
                // Single attempt failed — prompt stays open; do nothing here
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        cont.invokeOnCancellation { prompt.cancelAuthentication() }
        prompt.authenticate(info)
    }
}
