package com.nova.app.feature.private_space.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat

sealed class BiometricAvailability {
    data object Ready : BiometricAvailability()
    data object NoHardware : BiometricAvailability()
    data object NoneEnrolled : BiometricAvailability()
    data object TemporarilyUnavailable : BiometricAvailability()
}

/**
 * Thin wrapper around androidx.biometric.BiometricPrompt. NOVA never touches raw biometric
 * data — the OS performs the match and only reports success, failure, or error back to the app.
 *
 * Allows biometric OR device PIN/pattern/password as a fallback, which is what
 * `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` authenticators means below — this covers devices/users
 * without enrolled biometrics while still requiring real device authentication either way.
 */
class NovaBiometricAuth(private val activity: FragmentActivity) {

    private val allowedAuthenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun availability(): BiometricAvailability {
        val manager = BiometricManager.from(activity)
        return when (manager.canAuthenticate(allowedAuthenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Ready
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.NoHardware
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NoneEnrolled
            else -> BiometricAvailability.TemporarilyUnavailable
        }
    }

    fun authenticate(
        title: String = "Unlock NOVA Private",
        subtitle: String = "Confirm it's you to open your secure space",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    onFailed()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        prompt.authenticate(promptInfo)
    }
}
