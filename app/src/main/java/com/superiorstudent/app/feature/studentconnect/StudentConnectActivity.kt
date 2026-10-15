package com.superiorstudent.app.feature.studentconnect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import com.superiorstudent.app.feature.profile.data.ProfileCache
import com.superiorstudent.app.feature.studentconnect.data.StudentProfile
import com.superiorstudent.app.feature.studentconnect.data.StudentProfileRepository

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
    private lateinit var profileSetupPanel: View
    private lateinit var profileNameInput: EditText
    private lateinit var createProfileButton: Button
    private lateinit var profileCard: View
    private lateinit var profileDisplayName: TextView
    private lateinit var profilePublicId: TextView
    private lateinit var copyPublicIdButton: Button
    private lateinit var studentSearchPanel: View
    private lateinit var studentSearchInput: EditText
    private lateinit var studentSearchButton: Button
    private lateinit var studentSearchResultCard: View
    private lateinit var studentSearchResultName: TextView
    private lateinit var studentSearchResultId: TextView

    private val profileRepository by lazy { StudentProfileRepository() }
    private var profileLoadInProgressFor: String? = null
    private var profileLoadedFor: String? = null

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
        profileSetupPanel = findViewById(R.id.studentConnectProfileSetup)
        profileNameInput = findViewById(R.id.studentConnectDisplayNameInput)
        createProfileButton = findViewById(R.id.studentConnectCreateProfileButton)
        profileCard = findViewById(R.id.studentConnectProfileCard)
        profileDisplayName = findViewById(R.id.studentConnectProfileDisplayName)
        profilePublicId = findViewById(R.id.studentConnectProfilePublicId)
        copyPublicIdButton = findViewById(R.id.studentConnectCopyPublicIdButton)
        studentSearchPanel = findViewById(R.id.studentConnectSearchPanel)
        studentSearchInput = findViewById(R.id.studentConnectSearchInput)
        studentSearchButton = findViewById(R.id.studentConnectSearchButton)
        studentSearchResultCard = findViewById(R.id.studentConnectSearchResultCard)
        studentSearchResultName = findViewById(R.id.studentConnectSearchResultName)
        studentSearchResultId = findViewById(R.id.studentConnectSearchResultId)

        findViewById<TextView>(R.id.studentConnectBackButton).setOnClickListener { finish() }
        createAccountButton.setOnClickListener { createAccount() }
        signInButton.setOnClickListener { signIn() }
        resendVerificationButton.setOnClickListener { resendVerificationEmail() }
        checkVerificationButton.setOnClickListener { refreshVerificationStatus(silent = false) }
        createProfileButton.setOnClickListener { createStudentProfile() }
        copyPublicIdButton.setOnClickListener { copyPublicId() }
        studentSearchButton.setOnClickListener { searchStudentByPublicId() }
        signOutButton.setOnClickListener {
            auth.signOut()
            passwordInput.text?.clear()
            profileLoadInProgressFor = null
            profileLoadedFor = null
            profileSetupPanel.visibility = View.GONE
            profileCard.visibility = View.GONE
            studentSearchPanel.visibility = View.GONE
            studentSearchResultCard.visibility = View.GONE
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
            if (auth.currentUser?.isEmailVerified == true) {
                profileLoadedFor = null
                loadProfileIfNeeded(auth.currentUser!!.uid)
            }
        }
    }

    private fun renderCurrentUser() {
        val user = auth.currentUser
        val signedIn = user != null
        authForm.visibility = if (signedIn) View.GONE else View.VISIBLE
        signedInPanel.visibility = if (signedIn) View.VISIBLE else View.GONE

        if (user == null) {
            profileSetupPanel.visibility = View.GONE
            profileCard.visibility = View.GONE
            studentSearchPanel.visibility = View.GONE
            studentSearchResultCard.visibility = View.GONE
            profileLoadInProgressFor = null
            profileLoadedFor = null
            statusText.text = "Create a separate account or sign in. Do not reuse your university ERP password."
            resendVerificationButton.visibility = View.GONE
            checkVerificationButton.visibility = View.GONE
            return
        }

        accountEmail.text = user.email.orEmpty()
        val verified = user.isEmailVerified
        resendVerificationButton.visibility = if (verified) View.GONE else View.VISIBLE
        checkVerificationButton.visibility = if (verified) View.GONE else View.VISIBLE
        if (verified) {
            statusText.text = "Email verified. Checking your student profile…"
            loadProfileIfNeeded(user.uid)
        } else {
            profileSetupPanel.visibility = View.GONE
            profileCard.visibility = View.GONE
            statusText.text = "Email verification is required before a student profile can be created."
        }
    }

    private fun loadProfileIfNeeded(uid: String) {
        if (profileLoadInProgressFor == uid || profileLoadedFor == uid) return
        profileLoadInProgressFor = uid
        profileSetupPanel.visibility = View.GONE
        profileCard.visibility = View.GONE
        statusText.text = "Checking your student profile…"

        val user = auth.currentUser
        if (user == null || user.uid != uid) {
            profileLoadInProgressFor = null
            return
        }

        // Refresh the ID token so Firestore rules see the latest email_verified claim.
        user.getIdToken(true).addOnCompleteListener(this) { tokenTask ->
            if (auth.currentUser?.uid != uid) return@addOnCompleteListener
            if (!tokenTask.isSuccessful) {
                profileLoadInProgressFor = null
                profileLoadedFor = uid
                statusText.text = "Could not verify your Firebase session. Reopen Student Connect and try again."
                return@addOnCompleteListener
            }

            profileRepository.getOwnProfile(uid) { profile, error ->
                if (auth.currentUser?.uid != uid) return@getOwnProfile
                profileLoadInProgressFor = null
                profileLoadedFor = uid

                if (error != null) {
                    profileLoadedFor = null
                    profileSetupPanel.visibility = View.GONE
                    profileCard.visibility = View.GONE
                    studentSearchPanel.visibility = View.GONE
                    studentSearchResultCard.visibility = View.GONE
                    statusText.text = "Could not load your profile. Check Firestore and its published security rules, then reopen Student Connect."
                    return@getOwnProfile
                }

                if (profile == null) {
                    profileCard.visibility = View.GONE
                    studentSearchPanel.visibility = View.GONE
                    studentSearchResultCard.visibility = View.GONE
                    profileSetupPanel.visibility = View.VISIBLE
                    if (profileNameInput.text.isNullOrBlank()) {
                        val cachedName = try { ProfileCache(this).studentName.trim() } catch (_: Exception) { "" }
                        if (cachedName.length in 2..60) profileNameInput.setText(cachedName)
                    }
                    statusText.text = "Email verified. Create your student profile to reserve your unique ID."
                } else {
                    showProfile(profile)
                    statusText.text = "Your student profile is ready. You can share your public ID with classmates."
                }
            }
        }
    }

    private fun createStudentProfile() {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified) {
            statusText.text = "Sign in and verify your email before creating a student profile."
            return
        }

        val displayName = profileNameInput.text?.toString()?.trim().orEmpty()
        if (displayName.replace(Regex("""\s+"""), " ").length !in 2..60) {
            statusText.text = "Enter a display name between 2 and 60 characters."
            profileNameInput.requestFocus()
            return
        }

        setBusy(true)
        statusText.text = "Confirming email verification and reserving your unique ID…"
        user.reload().addOnCompleteListener(this) { reloadTask ->
            if (auth.currentUser?.uid != user.uid) {
                setBusy(false)
                return@addOnCompleteListener
            }
            if (!reloadTask.isSuccessful || auth.currentUser?.isEmailVerified != true) {
                setBusy(false)
                statusText.text = "Firebase could not confirm the verified email. Please check verification and retry."
                return@addOnCompleteListener
            }

            auth.currentUser?.getIdToken(true)?.addOnCompleteListener(this) { tokenTask ->
                if (auth.currentUser?.uid != user.uid) {
                    setBusy(false)
                    return@addOnCompleteListener
                }
                if (!tokenTask.isSuccessful) {
                    setBusy(false)
                    statusText.text = "Could not refresh your secure Firebase session. Please retry."
                    return@addOnCompleteListener
                }

                profileRepository.createProfile(user.uid, displayName) { profile, error ->
                    setBusy(false)
                    if (auth.currentUser?.uid != user.uid) return@createProfile
                    if (error != null || profile == null) {
                        profileLoadedFor = null
                        statusText.text = when {
                            error is IllegalArgumentException -> error.message.orEmpty()
                            error?.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ->
                                "Firestore denied the request. Confirm the included security rules are published and your email is verified."
                            else ->
                                "Could not create your profile. Check internet access and the Firestore setup, then try again."
                        }
                        return@createProfile
                    }

                    profileLoadedFor = user.uid
                    showProfile(profile)
                    statusText.text = "Student profile created. Your unique public ID is ready to share."
                }
            }
        }
    }

    private fun showProfile(profile: StudentProfile) {
        profileSetupPanel.visibility = View.GONE
        profileCard.visibility = View.VISIBLE
        studentSearchPanel.visibility = View.VISIBLE
        profileDisplayName.text = profile.displayName
        profilePublicId.text = profile.publicId
    }

    private fun searchStudentByPublicId() {
        val currentUser = auth.currentUser
        if (currentUser == null || !currentUser.isEmailVerified) {
            statusText.text = "Sign in with your verified Student Connect account first."
            return
        }

        val publicId = StudentPublicIdFormat.normalize(studentSearchInput.text?.toString().orEmpty())
        if (!StudentPublicIdFormat.isValid(publicId)) {
            studentSearchResultCard.visibility = View.GONE
            statusText.text = "Enter the complete student ID, for example UNI-ABCDEFG."
            studentSearchInput.requestFocus()
            return
        }

        val ownId = StudentPublicIdFormat.normalize(profilePublicId.text?.toString().orEmpty())
        if (publicId == ownId) {
            studentSearchResultCard.visibility = View.GONE
            statusText.text = "That is your own student ID. Try a classmate's ID."
            return
        }

        studentSearchButton.isEnabled = false
        studentSearchResultCard.visibility = View.GONE
        statusText.text = "Searching for student…"

        profileRepository.findByPublicId(publicId, currentUser.uid) { profile, error ->
            if (auth.currentUser?.uid != currentUser.uid) return@findByPublicId
            studentSearchButton.isEnabled = true

            when {
                error is IllegalArgumentException -> {
                    studentSearchResultCard.visibility = View.GONE
                    statusText.text = error.message.orEmpty()
                }
                error != null -> {
                    studentSearchResultCard.visibility = View.GONE
                    statusText.text = if (error.message.orEmpty().contains("PERMISSION_DENIED", ignoreCase = true)) {
                        "Firestore rules blocked this search. Publish the updated firestore.rules file and try again."
                    } else {
                        "Could not complete the student search. Check your internet and Firestore rules, then retry."
                    }
                }
                profile == null -> {
                    studentSearchResultCard.visibility = View.GONE
                    statusText.text = "No student found with ID $publicId. Check the ID and try again."
                }
                else -> {
                    studentSearchResultName.text = profile.displayName
                    studentSearchResultId.text = profile.publicId
                    studentSearchResultCard.visibility = View.VISIBLE
                    statusText.text = "Student found. Only their public name and ID are shown."
                }
            }
        }
    }


    private fun copyPublicId() {
        val id = profilePublicId.text?.toString().orEmpty()
        if (id.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Student public ID", id))
        android.widget.Toast.makeText(this, "Student ID copied", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun setBusy(busy: Boolean) {
        emailInput.isEnabled = !busy
        passwordInput.isEnabled = !busy
        createAccountButton.isEnabled = !busy
        signInButton.isEnabled = !busy
        resendVerificationButton.isEnabled = !busy
        checkVerificationButton.isEnabled = !busy
        signOutButton.isEnabled = !busy
        createProfileButton.isEnabled = !busy
        copyPublicIdButton.isEnabled = !busy
        studentSearchInput.isEnabled = !busy
        studentSearchButton.isEnabled = !busy
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
