package com.onenotes.app.ui.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.onenotes.app.R
import java.util.concurrent.Executor

/**
 * Invisible activity that asks the user to prove his identity with the fingerprint, the face or
 * the PIN of the device, and reports the result to the caller.
 *
 * It is used in three cases:
 *  - [REASON_APP]  the whole app is locked (see the "app lock" preference);
 *  - [REASON_LOCK] a note is about to be locked;
 *  - [REASON_UNLOCK] a locked note has been opened.
 *
 * The theme is transparent: the dialog of the system is the only thing the user sees, so there
 * is no way for this screen to look "off" compared to the rest of One UI.
 */
class LockActivity : AppCompatActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var reason: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reason = intent.getStringExtra(EXTRA_REASON) ?: REASON_UNLOCK

        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, R.string.lock_not_available, Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, callback())

        val title = intent.getStringExtra(EXTRA_TITLE)
            ?: if (reason == REASON_APP) getString(R.string.lock_app_title) else getString(R.string.lock_title)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(getString(R.string.lock_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()
        biometricPrompt.authenticate(info)
    }

    private fun callback() = object : BiometricPrompt.AuthenticationCallback() {

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            setResult(RESULT_OK, Intent().putExtra(RESULT_REASON, reason))
            finish()
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            // A wrong fingerprint: the system dialog stays open, only a small hint is shown.
            Toast.makeText(this@LockActivity, R.string.lock_failed, Toast.LENGTH_SHORT).show()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    companion object {
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_TITLE = "title"

        const val REASON_APP = "app"
        const val REASON_LOCK = "lock"
        const val REASON_UNLOCK = "unlock"

        const val RESULT_REASON = "result_reason"

        fun intent(context: Context, reason: String, title: String? = null): Intent =
            Intent(context, LockActivity::class.java)
                .putExtra(EXTRA_REASON, reason)
                .putExtra(EXTRA_TITLE, title)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
