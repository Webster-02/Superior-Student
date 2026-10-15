package com.superiorstudent.app.feature.studentconnect

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.superiorstudent.app.R
import com.superiorstudent.app.core.session.SessionStore

/**
 * Student Connect uses a separate, recoverable Firebase identity.
 * The university ERP login/session flow remains owned by MainActivity.
 */
class StudentConnectActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView
    private lateinit var authForm: View
    private lateinit var signedInPanel: View
    private lateinit var accountEmail: TextView
    private lateinit var createAccountButton: Button
    private lateinit var signInButton: Button
    private lateinit var resendVerificationButton: Button
    private lateinit var checkVerificationButton: Button
    private lateinit var signOutButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This screen is reachable from the authenticated dashboard only.
        val session = SessionStore(this)
        if (!session.sessionActive || session.username.isBlank()) {
            finish()
            return
        }

        setContentView(R.layout.activity_student_connect)
        auth = FirebaseAuth.getInstance()
        bindViews()
        renderCurrentUser()
        if (auth.currentUser != null) refreshVerificationStatus(silent = true)
    }

    private fun bindViews() {
        emailInput = findViewById(R.id.studentConnectEmailInput)
        passwordInput = findViewById(R.id.studentConnectPasswordInput)
        statusText = findViewById(R.id.studentConnectStatus)
        authForm = findViewById(R.id.studentConnectAuthForm)
        signedInPanel = findViewById(R.id.studentConnectSignedInPanel)
        accountEmail = findViewById(R.id.studentConnectAccountEmail)
        createAccountButton = findViewById(R.id.studentConnectCreateAccountButton)
        signInButton = findViewById(R.id.studentConnectSignInButton)
        resendVerificationButton = findViewById(R.id.studentConnectResendVerificationButton)
        checkVerificationButton = findViewById(R.id.studentConnectCheckVerificationButton)
        signOutButton = findViewById(R.id.studentConnectSignOutButton)

        findViewById<TextView>(R.id.studentConnectBackButton).setOnClickListener { finish() }
        createAccountButton.setOnClickListener { createAccount() }
        signInButton.setOnClickListener { signIn() }
        resendVerificationButton.setOnClickListener { resendVerificationEmail() }
        checkVerificationButton.setOnClickListener { refreshVerificationStatus(silent = false) }
        signOutButton.setOnClickListener {
            auth.signOut()
            passwordInput.text?.clear()
            renderCurrentUser()
            statusText.text = "Signed out of Student Connect. Your university login is unchanged."
        }
    }

    private fun createAccount() {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()
        if (!validateCredentials(email, password)) return

        setBusy(true)
        statusText.text = "Creating your separate Student Connect account…"
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setBusy(false)
                if (!task.isSuccessful) {
                    showAuthError(task.exception, creatingAccount = true)
                    return@addOnCompleteListener
                }

                passwordInput.text?.clear()
                val user = auth.currentUser
                if (user == null) {
                    statusText.text = "Account created, but Firebase did not return a user. Please sign in again."
                    renderCurrentUser()
                    return@addOnCompleteListener
                }

                setBusy(true)
                user.sendEmailVerification().addOnCompleteListener(this) { verificationTask ->
                    setBusy(false)
                    renderCurrentUser()
                    statusText.text = if (verificationTask.isSuccessful) {
                        "Account created. Open the verification email, then return here and tap “I've verified my email”."
                    } else {
                        "Account created, but the verification email could not be sent. Tap “Resend verification email” to retry."
                    }
                }
            }
    }

    private fun signIn() {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()
        if (!validateCredentials(email, password)) return

        setBusy(true)
        statusText.text = "Signing in to Student Connect…"
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setBusy(false)
                passwordInput.text?.clear()
                if (!task.isSuccessful) {
                    showAuthError(task.exception, creatingAccount = false)
                    return@addOnCompleteListener
                }
                renderCurrentUser()
                statusText.text = if (auth.currentUser?.isEmailVerified == true) {
                    "Email verified. Your Student Connect identity is ready."
                } else {
                    "Please verify your email before continuing. You can resend the verification email below."
                }
            }
    }

    private fun validateCredentials(email: String, password: String): Boolean {
        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            statusText.text = "Enter a valid email address that you can access."
            emailInput.requestFocus()
            return false
        }
        if (password.length < 6) {
            statusText.text = "Password must contain at least 6 characters."
            passwordInput.requestFocus()
            return false
        }
        return true
    }

    private fun resendVerificationEmail() {
        val user = auth.currentUser
        if (user == null) {
            renderCurrentUser()
            return
        }

        setBusy(true)
        user.sendEmailVerification().addOnCompleteListener(this) { task ->
            setBusy(false)
            statusText.text = if (task.isSuccessful) {
                "Verification email sent. Check your inbox and spam folder."
            } else {
                "Could not send the email right now. Please wait a moment and try again."
            }
        }
    }

    private fun refreshVerificationStatus(silent: Boolean) {
        val user = auth.currentUser
        if (user == null) {
            renderCurrentUser()
            return
        }

        if (!silent) {
            setBusy(true)
            statusText.text = "Checking email verification…"
        }

        user.reload().addOnCompleteListener(this) { task ->
            if (!silent) setBusy(false)
            renderCurrentUser()
            if (!task.isSuccessful) {
                if (!silent) statusText.text = "Could not refresh verification status. Check your internet and retry."
                return@addOnCompleteListener
            }
            statusText.text = if (auth.currentUser?.isEmailVerified == true) {
                "Email verified. Your Student Connect identity is ready."
            } else {
                "Email not verified yet. Open the email from Firebase, then check again."
            }
        }
    }

    private fun renderCurrentUser() {
        val user = auth.currentUser
        val signedIn = user != null
        authForm.visibility = if (signedIn) View.GONE else View.VISIBLE
        signedInPanel.visibility = if (signedIn) View.VISIBLE else View.GONE
        if (user == null) {
            statusText.text = "Create a separate account or sign in. Do not reuse your university ERP password."
            resendVerificationButton.visibility = View.GONE
            checkVerificationButton.visibility = View.GONE
            return
        }

        accountEmail.text = user.email.orEmpty()
        val verified = user.isEmailVerified
        resendVerificationButton.visibility = if (verified) View.GONE else View.VISIBLE
        checkVerificationButton.visibility = if (verified) View.GONE else View.VISIBLE
        statusText.text = if (verified) {
            "Email verified. Your Student Connect identity is ready."
        } else {
            "Email verification is required before a student profile can be created."
        }
    }

    private fun setBusy(busy: Boolean) {
        emailInput.isEnabled = !busy
        passwordInput.isEnabled = !busy
        createAccountButton.isEnabled = !busy
        signInButton.isEnabled = !busy
        resendVerificationButton.isEnabled = !busy
        checkVerificationButton.isEnabled = !busy
        signOutButton.isEnabled = !busy
    }

    private fun showAuthError(error: Exception?, creatingAccount: Boolean) {
        statusText.text = when (error) {
            is FirebaseAuthUserCollisionException ->
                "An account already exists for this email. Use “Sign in” instead."
            is FirebaseAuthWeakPasswordException ->
                "Choose a stronger password with at least 6 characters."
            is FirebaseAuthInvalidCredentialsException ->
                if (creatingAccount) "Please check the email address and try again."
                else "Email or password is incorrect. Check the details and try again."
            else ->
                "Firebase sign-in failed. Check your internet connection and confirm Email/Password sign-in is enabled in Firebase Console."
        }
    }
}
