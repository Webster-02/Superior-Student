package com.superiorstudent.app.feature.studentconnect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.ListenerRegistration
import com.superiorstudent.app.R
import com.superiorstudent.app.core.session.SessionStore
import com.superiorstudent.app.feature.profile.data.ProfileCache
import com.superiorstudent.app.feature.studentconnect.data.FriendRequest
import com.superiorstudent.app.feature.studentconnect.data.FriendRequestStatus
import com.superiorstudent.app.feature.studentconnect.data.StudentProfile
import com.superiorstudent.app.feature.studentconnect.data.FriendRequestRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentConnectionFilter
import com.superiorstudent.app.feature.studentconnect.data.StudentProfileRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentNotificationTokenRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentNotification
import com.superiorstudent.app.feature.studentconnect.data.StudentNotificationRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentPublicIdFormat
import com.superiorstudent.app.feature.studentconnect.notifications.StudentConnectNotificationHelper

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
    private lateinit var sendFriendRequestButton: Button
    private lateinit var friendRequestsPanel: View
    private lateinit var refreshFriendRequestsButton: Button
    private lateinit var connectionsContainer: LinearLayout
    private lateinit var connectionsSearchInput: EditText
    private lateinit var connectionsTitleText: TextView
    private lateinit var connectionsEmptyText: TextView
    private lateinit var incomingRequestsContainer: LinearLayout
    private lateinit var outgoingRequestsContainer: LinearLayout
    private lateinit var incomingRequestsEmptyText: TextView
    private lateinit var outgoingRequestsEmptyText: TextView
    private lateinit var notificationsPanel: LinearLayout
    private lateinit var notificationsTitleText: TextView
    private lateinit var notificationsContainer: LinearLayout
    private lateinit var notificationsEmptyText: TextView
    private lateinit var markAllNotificationsReadButton: Button

    private val profileRepository by lazy { StudentProfileRepository() }
    private val friendRequestRepository by lazy { FriendRequestRepository() }
    private val notificationTokenRepository by lazy { StudentNotificationTokenRepository() }
    private val studentNotificationRepository by lazy { StudentNotificationRepository() }
    private var notificationListener: ListenerRegistration? = null
    private var notificationsListeningFor: String? = null
    private var cachedStudentNotifications: List<StudentNotification> = emptyList()
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && ::statusText.isInitialized) {
            statusText.text = "Notifications are disabled. Enable them in Android Settings to receive Student Connect alerts."
        }
    }
    private var currentStudentProfile: StudentProfile? = null
    private var searchedStudentProfile: StudentProfile? = null
    private var cachedFriendRequests: List<FriendRequest> = emptyList()
    private var connectionsQuery: String = ""
    private var friendRequestsLoadingFor: String? = null
    private var friendRequestsLoadSequence = 0
    private var friendRequestsRefreshQueued = false
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

    override fun onDestroy() {
        stopNotificationsListener()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (!::auth.isInitialized) return

        val user = auth.currentUser ?: return
        if (user.isEmailVerified && currentStudentProfile?.uid == user.uid) {
            // Refresh requests/connections when the user returns to this screen.
            refreshFriendRequests(showLoadingMessage = false)
        }
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
        sendFriendRequestButton = findViewById(R.id.studentConnectSendFriendRequestButton)
        friendRequestsPanel = findViewById(R.id.studentConnectFriendRequestsPanel)
        refreshFriendRequestsButton = findViewById(R.id.studentConnectRefreshFriendRequestsButton)
        connectionsContainer = findViewById(R.id.studentConnectConnectionsContainer)
        connectionsSearchInput = findViewById(R.id.studentConnectConnectionsSearchInput)
        connectionsTitleText = findViewById(R.id.studentConnectConnectionsTitle)
        connectionsEmptyText = findViewById(R.id.studentConnectConnectionsEmpty)
        incomingRequestsContainer = findViewById(R.id.studentConnectIncomingRequestsContainer)
        outgoingRequestsContainer = findViewById(R.id.studentConnectOutgoingRequestsContainer)
        incomingRequestsEmptyText = findViewById(R.id.studentConnectIncomingRequestsEmpty)
        outgoingRequestsEmptyText = findViewById(R.id.studentConnectOutgoingRequestsEmpty)
        notificationsPanel = findViewById(R.id.studentConnectNotificationsPanel)
        notificationsTitleText = findViewById(R.id.studentConnectNotificationsTitle)
        notificationsContainer = findViewById(R.id.studentConnectNotificationsContainer)
        notificationsEmptyText = findViewById(R.id.studentConnectNotificationsEmpty)
        markAllNotificationsReadButton = findViewById(R.id.studentConnectMarkAllNotificationsReadButton)

        findViewById<TextView>(R.id.studentConnectBackButton).setOnClickListener { finish() }
        createAccountButton.setOnClickListener { createAccount() }
        signInButton.setOnClickListener { signIn() }
        resendVerificationButton.setOnClickListener { resendVerificationEmail() }
        checkVerificationButton.setOnClickListener { refreshVerificationStatus(silent = false) }
        createProfileButton.setOnClickListener { createStudentProfile() }
        copyPublicIdButton.setOnClickListener { copyPublicId() }
        studentSearchButton.setOnClickListener { searchStudentByPublicId() }
        sendFriendRequestButton.setOnClickListener { sendFriendRequest() }
        refreshFriendRequestsButton.setOnClickListener { refreshFriendRequests(showLoadingMessage = true) }
        markAllNotificationsReadButton.setOnClickListener { markAllNotificationsRead() }
        connectionsSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                connectionsQuery = s?.toString().orEmpty()
                val user = auth.currentUser
                if (user != null && user.isEmailVerified && currentStudentProfile?.uid == user.uid) {
                    renderFriendRequestLists(user.uid, cachedFriendRequests)
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        signOutButton.setOnClickListener { signOutStudentConnect() }
    }

    private fun signOutStudentConnect() {
        val user = auth.currentUser
        val preferences = getSharedPreferences(
            StudentConnectNotificationHelper.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val registeredUid = preferences.getString(StudentConnectNotificationHelper.KEY_REGISTERED_UID, null)

        if (user == null || !user.isEmailVerified || registeredUid != user.uid) {
            finishStudentConnectSignOut()
            return
        }

        signOutButton.isEnabled = false
        statusText.text = "Disabling notifications on this device…"
        notificationTokenRepository.deleteToken(user.uid) { error ->
            if (auth.currentUser?.uid != user.uid) return@deleteToken
            if (error != null) {
                signOutButton.isEnabled = true
                statusText.text = "Could not unregister this device from notifications. Check your internet connection and try signing out again."
                return@deleteToken
            }

            preferences.edit()
                .remove(StudentConnectNotificationHelper.KEY_REGISTERED_UID)
                .apply()
            finishStudentConnectSignOut()
        }
    }

    private fun finishStudentConnectSignOut() {
        auth.signOut()
        passwordInput.text?.clear()
        profileLoadInProgressFor = null
        profileLoadedFor = null
        profileSetupPanel.visibility = View.GONE
        profileCard.visibility = View.GONE
        studentSearchPanel.visibility = View.GONE
        studentSearchResultCard.visibility = View.GONE
        currentStudentProfile = null
        searchedStudentProfile = null
        sendFriendRequestButton.visibility = View.GONE
        friendRequestsPanel.visibility = View.GONE
        clearFriendRequestLists()
        friendRequestsLoadSequence++
        friendRequestsLoadingFor = null
        friendRequestsRefreshQueued = false
        refreshFriendRequestsButton.isEnabled = true
        signOutButton.isEnabled = true
        renderCurrentUser()
        statusText.text = "Signed out of Student Connect. Your university login is unchanged."
    }

    private fun requestStudentConnectNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val preferences = getSharedPreferences(
            StudentConnectNotificationHelper.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            preferences.edit()
                .putBoolean(StudentConnectNotificationHelper.KEY_PERMISSION_REQUESTED, true)
                .apply()
            return
        }
        if (preferences.getBoolean(StudentConnectNotificationHelper.KEY_PERMISSION_REQUESTED, false)) return

        preferences.edit()
            .putBoolean(StudentConnectNotificationHelper.KEY_PERMISSION_REQUESTED, true)
            .apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun registerStudentNotificationToken(uid: String) {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener(this) { token ->
                val user = auth.currentUser
                if (user == null || user.uid != uid || !user.isEmailVerified) return@addOnSuccessListener

                notificationTokenRepository.saveToken(uid, token) { error ->
                    if (auth.currentUser?.uid != uid) return@saveToken
                    if (error != null) {
                        Log.w("StudentConnectPush", "Could not register this device for notifications.", error)
                        return@saveToken
                    }
                    getSharedPreferences(
                        StudentConnectNotificationHelper.PREFERENCES_NAME,
                        Context.MODE_PRIVATE
                    ).edit()
                        .putString(StudentConnectNotificationHelper.KEY_REGISTERED_UID, uid)
                        .apply()
                }
            }
            .addOnFailureListener(this) { error ->
                Log.w("StudentConnectPush", "Could not retrieve this device's notification token.", error)
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
            stopNotificationsListener()
            notificationsPanel.visibility = View.GONE
            notificationsContainer.removeAllViews()
            cachedStudentNotifications = emptyList()
            currentStudentProfile = null
            searchedStudentProfile = null
            sendFriendRequestButton.visibility = View.GONE
            friendRequestsPanel.visibility = View.GONE
            clearFriendRequestLists()
            friendRequestsLoadSequence++
            friendRequestsLoadingFor = null
            friendRequestsRefreshQueued = false
            refreshFriendRequestsButton.isEnabled = true
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
            friendRequestsPanel.visibility = View.GONE
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
                    currentStudentProfile = null
                    searchedStudentProfile = null
                    sendFriendRequestButton.visibility = View.GONE
                    friendRequestsPanel.visibility = View.GONE
                    clearFriendRequestLists()
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
        currentStudentProfile = profile
        notificationsPanel.visibility = View.VISIBLE
        startNotificationsListener(profile.uid)
        requestStudentConnectNotificationPermissionIfNeeded()
        registerStudentNotificationToken(profile.uid)
        profileSetupPanel.visibility = View.GONE
        profileCard.visibility = View.VISIBLE
        studentSearchPanel.visibility = View.VISIBLE
        friendRequestsPanel.visibility = View.VISIBLE
        profileDisplayName.text = profile.displayName
        profilePublicId.text = profile.publicId
        refreshFriendRequests(showLoadingMessage = false)
    }

    private fun startNotificationsListener(uid: String) {
        if (notificationsListeningFor == uid && notificationListener != null) return
        stopNotificationsListener()
        notificationsPanel.visibility = View.VISIBLE
        notificationsListeningFor = uid
        notificationListener = studentNotificationRepository.listen(uid) { notifications, error ->
            if (auth.currentUser?.uid != uid || currentStudentProfile?.uid != uid) return@listen
            if (error != null || notifications == null) {
                cachedStudentNotifications = emptyList()
                notificationsContainer.removeAllViews()
                notificationsTitleText.text = "Notifications"
                notificationsEmptyText.text = if (error?.message.orEmpty().contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "Publish the updated firestore.rules file to enable the notification inbox."
                } else {
                    "Could not load notifications. Check your connection and try again."
                }
                notificationsEmptyText.visibility = View.VISIBLE
                markAllNotificationsReadButton.visibility = View.GONE
                return@listen
            }
            cachedStudentNotifications = notifications
            renderNotifications(notifications)
        }
    }

    private fun stopNotificationsListener() {
        notificationListener?.remove()
        notificationListener = null
        notificationsListeningFor = null
    }

    private fun renderNotifications(notifications: List<StudentNotification>) {
        notificationsContainer.removeAllViews()
        val unreadCount = notifications.count { !it.isRead }
        notificationsTitleText.text = if (unreadCount == 0) "Notifications" else "Notifications ($unreadCount unread)"
        notificationsEmptyText.text = "No notifications yet. Friend request updates will appear here."
        notificationsEmptyText.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
        markAllNotificationsReadButton.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
        markAllNotificationsReadButton.isEnabled = true

        notifications.forEach { notification ->
            val row = layoutInflater.inflate(R.layout.item_student_connect_notification, notificationsContainer, false)
            row.findViewById<TextView>(R.id.studentConnectNotificationItemTitle).text = notification.title
            row.findViewById<TextView>(R.id.studentConnectNotificationItemBody).text = notification.body
            val readState = row.findViewById<TextView>(R.id.studentConnectNotificationItemReadState)
            readState.text = if (notification.isRead) "READ" else "UNREAD"
            readState.setTextColor(androidx.core.content.ContextCompat.getColor(
                this, if (notification.isRead) R.color.ink_500 else R.color.brand_primary
            ))
            row.findViewById<TextView>(R.id.studentConnectNotificationItemTime).text = formatNotificationTime(notification.createdAt)
            val markReadButton = row.findViewById<Button>(R.id.studentConnectNotificationItemMarkReadButton)
            markReadButton.visibility = if (notification.isRead) View.GONE else View.VISIBLE
            markReadButton.setOnClickListener {
                val user = auth.currentUser
                if (user == null || user.uid != notification.recipientUid) return@setOnClickListener
                markReadButton.isEnabled = false
                studentNotificationRepository.markAsRead(user.uid, notification.id) { error ->
                    if (auth.currentUser?.uid != user.uid) return@markAsRead
                    if (error != null) {
                        markReadButton.isEnabled = true
                        statusText.text = "Could not mark notification as read. Check the latest firestore.rules."
                    }
                }
            }
            notificationsContainer.addView(row)
        }
    }

    private fun formatNotificationTime(timestamp: com.google.firebase.firestore.Timestamp?): String {
        if (timestamp == null) return "Just now"
        val elapsed = (System.currentTimeMillis() - timestamp.toDate().time).coerceAtLeast(0L)
        return when {
            elapsed < 60_000L -> "Just now"
            elapsed < 3_600_000L -> "${elapsed / 60_000L} min ago"
            elapsed < 86_400_000L -> "${elapsed / 3_600_000L} hr ago"
            else -> SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(timestamp.toDate().time))
        }
    }

    private fun markAllNotificationsRead() {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified) return
        markAllNotificationsReadButton.isEnabled = false
        studentNotificationRepository.markAllAsRead(user.uid, cachedStudentNotifications) { error ->
            if (auth.currentUser?.uid != user.uid) return@markAllAsRead
            markAllNotificationsReadButton.isEnabled = true
            if (error != null) {
                statusText.text = "Could not mark notifications as read. Check the latest firestore.rules."
            } else {
                statusText.text = "All notifications marked as read."
            }
        }
    }

    private fun refreshFriendRequests(showLoadingMessage: Boolean = true) {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified || currentStudentProfile?.uid != user.uid) {
            friendRequestsPanel.visibility = View.GONE
            return
        }

        if (friendRequestsLoadingFor == user.uid) {
            friendRequestsRefreshQueued = true
            return
        }

        friendRequestsPanel.visibility = View.VISIBLE
        friendRequestsLoadingFor = user.uid
        friendRequestsRefreshQueued = false
        val loadSequence = ++friendRequestsLoadSequence
        refreshFriendRequestsButton.isEnabled = false
        if (showLoadingMessage) statusText.text = "Loading friend requests…"

        friendRequestRepository.loadRequests(user.uid) { requests, error ->
            if (loadSequence != friendRequestsLoadSequence) return@loadRequests

            friendRequestsLoadingFor = null
            refreshFriendRequestsButton.isEnabled = true
            if (auth.currentUser?.uid != user.uid) return@loadRequests

            if (error != null || requests == null) {
                cachedFriendRequests = emptyList()
                incomingRequestsContainer.removeAllViews()
                outgoingRequestsContainer.removeAllViews()
                connectionsContainer.removeAllViews()
                incomingRequestsEmptyText.text = "Could not load requests. Tap Refresh to try again."
                outgoingRequestsEmptyText.text = "Could not load requests. Tap Refresh to try again."
                incomingRequestsEmptyText.visibility = View.VISIBLE
                outgoingRequestsEmptyText.visibility = View.VISIBLE
                statusText.text = if (error?.message.orEmpty().contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "Firestore rules blocked loading friend requests. Confirm the published rules allow request participants to read their requests."
                } else {
                    "Could not load friend requests. Check your internet connection and tap Refresh to retry."
                }
            } else {
                cachedFriendRequests = requests
                renderFriendRequestLists(user.uid, requests)
                if (showLoadingMessage) statusText.text = "Friend requests refreshed."
            }

            if (friendRequestsRefreshQueued && auth.currentUser?.uid == user.uid) {
                friendRequestsRefreshQueued = false
                refreshFriendRequests(showLoadingMessage = false)
            }
        }
    }

    private fun renderFriendRequestLists(uid: String, requests: List<FriendRequest>) {
        connectionsContainer.removeAllViews()
        incomingRequestsContainer.removeAllViews()
        outgoingRequestsContainer.removeAllViews()

        val allConnections = requests
            .filter { it.status == FriendRequestStatus.ACCEPTED }
            .sortedBy {
                if (it.senderUid == uid) it.receiverName.lowercase(java.util.Locale.ROOT)
                else it.senderName.lowercase(java.util.Locale.ROOT)
            }
        val connections = allConnections.filter { request ->
            val isSender = request.senderUid == uid
            val otherName = if (isSender) request.receiverName else request.senderName
            val otherPublicId = if (isSender) request.receiverPublicId else request.senderPublicId
            StudentConnectionFilter.matches(connectionsQuery, otherName, otherPublicId)
        }

        val incoming = requests
            .filter { it.receiverUid == uid && it.status != FriendRequestStatus.ACCEPTED && it.status != FriendRequestStatus.REMOVED }
            .sortedWith(compareBy<FriendRequest> { if (it.status == FriendRequestStatus.PENDING) 0 else 1 }
                .thenBy { it.senderName.lowercase(java.util.Locale.ROOT) })
        val outgoing = requests
            .filter { it.senderUid == uid && it.status != FriendRequestStatus.ACCEPTED && it.status != FriendRequestStatus.REMOVED }
            .sortedWith(compareBy<FriendRequest> { if (it.status == FriendRequestStatus.PENDING) 0 else 1 }
                .thenBy { it.receiverName.lowercase(java.util.Locale.ROOT) })

        connections.forEach {
            val currentUserIsReceiver = it.receiverUid == uid
            addFriendRequestRow(connectionsContainer, it, incomingRequest = currentUserIsReceiver)
        }
        incoming.forEach { addFriendRequestRow(incomingRequestsContainer, it, incomingRequest = true) }
        outgoing.forEach { addFriendRequestRow(outgoingRequestsContainer, it, incomingRequest = false) }

        connectionsTitleText.text = "My Connections (" + allConnections.size + ")"
        connectionsEmptyText.text = when {
            allConnections.isEmpty() -> "No connections yet. Accept a friend request to connect with classmates."
            connections.isEmpty() -> "No connections match your search."
            else -> ""
        }
        connectionsEmptyText.visibility = if (connections.isEmpty()) View.VISIBLE else View.GONE

        incomingRequestsEmptyText.text = "No incoming friend requests yet."
        outgoingRequestsEmptyText.text = "You haven't sent any friend requests yet."
        incomingRequestsEmptyText.visibility = if (incoming.isEmpty()) View.VISIBLE else View.GONE
        outgoingRequestsEmptyText.visibility = if (outgoing.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun addFriendRequestRow(
        container: LinearLayout,
        request: FriendRequest,
        incomingRequest: Boolean
    ) {
        val row = layoutInflater.inflate(R.layout.item_student_connect_friend_request, container, false)
        val nameText = row.findViewById<TextView>(R.id.studentConnectFriendRequestName)
        val publicIdText = row.findViewById<TextView>(R.id.studentConnectFriendRequestId)
        val requestStatusText = row.findViewById<TextView>(R.id.studentConnectFriendRequestStatus)
        val acceptButton = row.findViewById<Button>(R.id.studentConnectFriendRequestAcceptButton)
        val rejectButton = row.findViewById<Button>(R.id.studentConnectFriendRequestRejectButton)
        val cancelButton = row.findViewById<Button>(R.id.studentConnectFriendRequestCancelButton)
        val resendButton = row.findViewById<Button>(R.id.studentConnectFriendRequestResendButton)
        val copyIdButton = row.findViewById<Button>(R.id.studentConnectFriendRequestCopyIdButton)
        val removeConnectionButton = row.findViewById<Button>(R.id.studentConnectFriendRequestRemoveButton)
        val actionButtons = listOf(acceptButton, rejectButton, cancelButton, resendButton, copyIdButton, removeConnectionButton)
        actionButtons.forEach {
            it.visibility = View.GONE
            it.isEnabled = true
        }

        if (incomingRequest) {
            nameText.text = request.senderName
            publicIdText.text = request.senderPublicId
        } else {
            nameText.text = request.receiverName
            publicIdText.text = request.receiverPublicId
        }
        copyIdButton.visibility = View.VISIBLE
        copyIdButton.setOnClickListener {
            copyStudentId(publicIdText.text?.toString().orEmpty())
        }

        requestStatusText.text = when (request.status) {
            FriendRequestStatus.PENDING -> if (incomingRequest) "Waiting for your response" else "Waiting for their response"
            FriendRequestStatus.ACCEPTED -> "Connected"
            FriendRequestStatus.REMOVED -> "Connection removed"
            FriendRequestStatus.REJECTED -> if (incomingRequest) "You rejected this request" else "Request declined. You can resend it."
            FriendRequestStatus.CANCELLED -> if (incomingRequest) "Sender cancelled this request" else "You cancelled this request. You can send it again."
            else -> "Unknown request status"
        }

        when {
            request.status == FriendRequestStatus.ACCEPTED -> {
                removeConnectionButton.visibility = View.VISIBLE
                removeConnectionButton.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Remove connection?")
                        .setMessage("Remove ${nameText.text} from My Connections? You can send a new request later.")
                        .setNegativeButton("Keep connection", null)
                        .setPositiveButton("Remove") { _, _ ->
                            updateFriendRequestStatus(
                                request,
                                FriendRequestStatus.REMOVED,
                                "Remove connection",
                                actionButtons
                            )
                        }
                        .show()
                }
            }
            incomingRequest && request.status == FriendRequestStatus.PENDING -> {
                acceptButton.visibility = View.VISIBLE
                rejectButton.visibility = View.VISIBLE
                acceptButton.setOnClickListener {
                    updateFriendRequestStatus(request, FriendRequestStatus.ACCEPTED, "Accept", actionButtons)
                }
                rejectButton.setOnClickListener {
                    updateFriendRequestStatus(request, FriendRequestStatus.REJECTED, "Reject", actionButtons)
                }
            }
            !incomingRequest && request.status == FriendRequestStatus.PENDING -> {
                cancelButton.visibility = View.VISIBLE
                cancelButton.setOnClickListener {
                    updateFriendRequestStatus(request, FriendRequestStatus.CANCELLED, "Cancel", actionButtons)
                }
            }
            !incomingRequest && (request.status == FriendRequestStatus.REJECTED ||
                request.status == FriendRequestStatus.CANCELLED) -> {
                resendButton.visibility = View.VISIBLE
                resendButton.setOnClickListener {
                    updateFriendRequestStatus(request, FriendRequestStatus.PENDING, "Resend", actionButtons)
                }
            }
        }

        container.addView(row)
    }

    private fun updateFriendRequestStatus(
        request: FriendRequest,
        nextStatus: String,
        actionLabel: String,
        actionButtons: List<Button>
    ) {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified) {
            statusText.text = "Sign in with your verified Student Connect account before managing requests."
            return
        }

        actionButtons.forEach { it.isEnabled = false }
        statusText.text = if (nextStatus == FriendRequestStatus.REMOVED) {
            "Removing connection…"
        } else {
            "$actionLabel friend request…"
        }
        friendRequestRepository.updateStatus(request, user.uid, nextStatus) { error ->
            if (auth.currentUser?.uid != user.uid) return@updateStatus

            if (error != null) {
                actionButtons.forEach { it.isEnabled = true }
                statusText.text = if (error.message.orEmpty().contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "Firestore rules blocked this action. Confirm the published friendRequests update rules and try again."
                } else {
                    if (nextStatus == FriendRequestStatus.REMOVED) {
                        "Could not remove this connection. Tap Refresh and try again."
                    } else {
                        "Could not $actionLabel this friend request. Tap Refresh and try again."
                    }
                }
                return@updateStatus
            }

            statusText.text = when (nextStatus) {
                FriendRequestStatus.ACCEPTED -> "Friend request accepted. You are now connected."
                FriendRequestStatus.REJECTED -> "Friend request rejected."
                FriendRequestStatus.CANCELLED -> "Sent friend request cancelled."
                FriendRequestStatus.PENDING -> "Request sent again. It is now pending."
                FriendRequestStatus.REMOVED -> "Connection removed. You can send a new request later."
                else -> "Friend request updated."
            }
            refreshFriendRequests(showLoadingMessage = false)
        }
    }

    private fun clearFriendRequestLists() {
        connectionsContainer.removeAllViews()
        incomingRequestsContainer.removeAllViews()
        outgoingRequestsContainer.removeAllViews()
        cachedFriendRequests = emptyList()
        connectionsQuery = ""
        connectionsSearchInput.setText("")
        connectionsTitleText.text = "My Connections (0)"
        connectionsEmptyText.visibility = View.GONE
        incomingRequestsEmptyText.visibility = View.GONE
        outgoingRequestsEmptyText.visibility = View.GONE
    }

    private fun searchStudentByPublicId() {
        val currentUser = auth.currentUser
        if (currentUser == null || !currentUser.isEmailVerified) {
            statusText.text = "Sign in with your verified Student Connect account first."
            return
        }

        searchedStudentProfile = null
        sendFriendRequestButton.visibility = View.GONE
        sendFriendRequestButton.isEnabled = true
        sendFriendRequestButton.text = "Send friend request"
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
                    searchedStudentProfile = profile
                    studentSearchResultName.text = profile.displayName
                    studentSearchResultId.text = profile.publicId
                    sendFriendRequestButton.text = "Send friend request"
                    sendFriendRequestButton.isEnabled = true
                    sendFriendRequestButton.visibility = View.VISIBLE
                    studentSearchResultCard.visibility = View.VISIBLE
                    statusText.text = "Student found. Only their public name and ID are shown."
                }
            }
        }
    }


    private fun sendFriendRequest() {
        val sender = auth.currentUser
        if (sender == null || !sender.isEmailVerified) {
            statusText.text = "Sign in with your verified Student Connect account first."
            return
        }

        val senderProfile = currentStudentProfile
        val receiverProfile = searchedStudentProfile
        if (senderProfile == null || senderProfile.uid != sender.uid || receiverProfile == null) {
            statusText.text = "Search for a classmate again before sending a friend request."
            sendFriendRequestButton.visibility = View.GONE
            return
        }

        if (senderProfile.uid == receiverProfile.uid) {
            statusText.text = "You cannot send a friend request to yourself."
            sendFriendRequestButton.visibility = View.GONE
            return
        }

        sendFriendRequestButton.isEnabled = false
        statusText.text = "Sending friend request…"
        friendRequestRepository.sendRequest(senderProfile, receiverProfile) { requestStatus, error ->
            if (auth.currentUser?.uid != sender.uid) return@sendRequest

            if (error != null) {
                sendFriendRequestButton.isEnabled = true
                statusText.text = if (
                    error.message.orEmpty().contains("PERMISSION_DENIED", ignoreCase = true)
                ) {
                    "Firestore blocked this request. Confirm the latest firestore.rules are published in Firebase Console, then retry."
                } else {
                    "Could not send the friend request. Check your internet connection and try again."
                }
                return@sendRequest
            }

            when (requestStatus) {
                FriendRequestStatus.PENDING -> {
                    sendFriendRequestButton.text = "Request pending"
                    sendFriendRequestButton.isEnabled = false
                    statusText.text = "Friend request sent or already pending. It appears in Sent Requests below."
                    refreshFriendRequests(showLoadingMessage = false)
                }
                FriendRequestStatus.ACCEPTED -> {
                    sendFriendRequestButton.text = "Already connected"
                    sendFriendRequestButton.isEnabled = false
                    statusText.text = "You are already connected with this student."
                    refreshFriendRequests(showLoadingMessage = false)
                }
                else -> {
                    sendFriendRequestButton.isEnabled = true
                    statusText.text = "Could not confirm the friend request status. Please try again."
                }
            }
        }
    }

    private fun copyPublicId() {
        copyStudentId(profilePublicId.text?.toString().orEmpty())
    }

    private fun copyStudentId(id: String) {
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
