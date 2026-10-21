package com.superiorstudent.app.feature.studentconnect

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.ListenerRegistration
import com.superiorstudent.app.R
import com.superiorstudent.app.core.session.SessionStore
import com.superiorstudent.app.ui.AppThemePreferences
import com.superiorstudent.app.ui.U2UDesignTokens
import com.superiorstudent.app.ui.U2UTextRole
import com.superiorstudent.app.feature.profile.data.ProfileCache
import com.superiorstudent.app.feature.market.ui.MarketRequestsActivity
import com.superiorstudent.app.feature.studentconnect.data.FriendRequest
import com.superiorstudent.app.feature.studentconnect.data.FriendRequestStatus
import com.superiorstudent.app.feature.studentconnect.data.StudentProfile
import com.superiorstudent.app.feature.studentconnect.data.FriendRequestRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentConnectionFilter
import com.superiorstudent.app.feature.studentconnect.data.StudentProfileRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentPresence
import com.superiorstudent.app.feature.studentconnect.data.StudentPresenceFormatter
import com.superiorstudent.app.feature.studentconnect.data.StudentPresenceRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentConnectPresenceTracker
import com.superiorstudent.app.feature.studentconnect.data.StudentChatRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentChatSummary
import com.superiorstudent.app.feature.studentconnect.data.StudentConnectLocalCache
import com.superiorstudent.app.feature.studentconnect.data.StudentNotificationTokenRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentNotification
import com.superiorstudent.app.feature.studentconnect.data.StudentNotificationRepository
import com.superiorstudent.app.feature.studentconnect.data.StudentNotificationNavigation
import com.superiorstudent.app.feature.studentconnect.data.StudentNotificationTarget
import com.superiorstudent.app.feature.studentconnect.data.StudentPublicIdFormat
import com.superiorstudent.app.feature.studentconnect.notifications.StudentConnectNotificationHelper

/**
 * U2U uses a separate, recoverable Firebase identity.
 * The university ERP login/session flow remains owned by MainActivity.
 */
class StudentConnectActivity : AppCompatActivity() {

    private enum class StudentConnectScreen { HOME, SEARCH, CHATS, REQUESTS, CONNECTIONS, NOTIFICATIONS, PROFILE }
    private var currentScreen = StudentConnectScreen.HOME
    private val screenBackStack = StudentConnectBackStack(StudentConnectScreen.HOME)
    private var heroFloatAnimator: ObjectAnimator? = null
    private var heroTiltAnimator: ObjectAnimator? = null
    private var heroOrbAnimator: ObjectAnimator? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView
    private lateinit var studentConnectScrollView: ScrollView
    private lateinit var friendRequestsSwipeRefresh: SwipeRefreshLayout
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
    private lateinit var chatListTitleText: TextView
    private lateinit var chatListContainer: LinearLayout
    private lateinit var chatListEmptyText: TextView
    private lateinit var chatFilters: View
    private lateinit var chatFilterAll: TextView
    private lateinit var chatFilterUnread: TextView
    private lateinit var seeAllChatsButton: View
    private lateinit var requestsIncomingTab: TextView
    private lateinit var requestsSentTab: TextView
    private lateinit var incomingRequestsHeading: TextView
    private lateinit var outgoingRequestsHeading: TextView
    private lateinit var profileAvatar: TextView
    private var filterUnreadChatsOnly = false
    private var showOutgoingRequestsOnly = false
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
    private lateinit var homeDashboard: View
    private lateinit var homeGreetingText: TextView
    private lateinit var homeHeroLogo: View
    private lateinit var homeHeroOrb: View
    private lateinit var chatSection: View
    private lateinit var connectionsSection: View
    private lateinit var bottomNavigation: View
    private lateinit var securityNoteText: View
    private lateinit var headerNotificationsButton: View
    private lateinit var headerNotificationBadge: TextView
    private lateinit var homeRequestsSubtitle: TextView
    private lateinit var homeConnectionsSubtitle: TextView
    private lateinit var navItems: List<View>
    private lateinit var navIcons: List<ImageView>
    private lateinit var navLabels: List<TextView>

    private val profileRepository by lazy { StudentProfileRepository() }
    private val friendRequestRepository by lazy { FriendRequestRepository() }
    private val studentChatRepository by lazy { StudentChatRepository() }
    private val localCache by lazy { StudentConnectLocalCache.get(applicationContext) }
    private val localChatClearState by lazy { StudentChatLocalClearState(applicationContext) }
    private val notificationTokenRepository by lazy { StudentNotificationTokenRepository() }
    private val studentNotificationRepository by lazy { StudentNotificationRepository() }
    private var notificationListener: ListenerRegistration? = null
    private var notificationsListeningFor: String? = null
    private var cachedStudentNotifications: List<StudentNotification> = emptyList()
    private var chatListListener: ListenerRegistration? = null
    private var chatListListeningFor: String? = null
    private var chatListSubscriptionGeneration = 0
    private var chatListLiveSnapshotReceivedFor: String? = null
    private var cachedChatSummaries: List<StudentChatSummary> = emptyList()
    private val notificationHighlightHandler = Handler(Looper.getMainLooper())
    private var highlightedNotificationRequestId: String? = null
    private var highlightedNotificationTarget: StudentNotificationTarget? = null
    private var notificationHighlightClearRunnable: Runnable? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && ::statusText.isInitialized) {
            statusText.text = "Notifications are disabled. Enable them in Android Settings to receive U2U alerts."
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
    private var profileCacheLookupFor: String? = null
    private var activityStarted = false
    private val presenceOwnerKey by lazy { "StudentConnectActivity:" + System.identityHashCode(this) }
    private val presenceRepository by lazy { StudentPresenceRepository() }
    private val connectionPresenceListeners = mutableMapOf<String, ListenerRegistration>()
    private val connectionPresenceCache = mutableMapOf<String, StudentPresence>()
    private val connectionPresenceViews = mutableMapOf<String, TextView>()
    private val presenceUiHandler = Handler(Looper.getMainLooper())
    private val presenceUiRefreshRunnable = object : Runnable {
        override fun run() {
            if (!activityStarted || currentScreen != StudentConnectScreen.CONNECTIONS) return
            refreshConnectionPresenceLabels()
            presenceUiHandler.postDelayed(this, 15_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
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
        applySharedDesignTokens()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleU2UBack()
            }
        })
        applyStudentConnectWindowInsets()
        arrangeProfileSection()
        renderCurrentUser()
        // The saved profile renders first; one background ID-token refresh confirms it without user.reload().
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        if (::homeDashboard.isInitialized && currentScreen == StudentConnectScreen.HOME) startDashboardAnimations()
        syncOwnPresence()
        syncVisibleConnectionPresence()
    }

    override fun onStop() {
        activityStarted = false
        StudentConnectPresenceTracker.stop(presenceOwnerKey)
        stopConnectionPresenceListeners()
        presenceUiHandler.removeCallbacks(presenceUiRefreshRunnable)
        stopDashboardAnimations()
        super.onStop()
    }

    override fun onDestroy() {
        StudentConnectPresenceTracker.stop(presenceOwnerKey)
        stopConnectionPresenceListeners()
        presenceUiHandler.removeCallbacksAndMessages(null)
        stopDashboardAnimations()
        stopNotificationsListener()
        stopChatListListener()
        clearNotificationHighlight()
        connectionPresenceViews.clear()
        connectionPresenceCache.clear()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (!::auth.isInitialized) return
        syncOwnPresence()
        syncVisibleConnectionPresence()

        val user = auth.currentUser ?: return
        if (user.isEmailVerified && currentStudentProfile?.uid == user.uid) {
            // Keep the existing listener and cached rows alive while returning from a chat.
            refreshFriendRequests(showLoadingMessage = false)
            startChatListListener(user.uid)
        }
    }

    private fun bindViews() {
        emailInput = findViewById(R.id.studentConnectEmailInput)
        passwordInput = findViewById(R.id.studentConnectPasswordInput)
        statusText = findViewById(R.id.studentConnectStatus)
        val statusCard = findViewById<View>(R.id.studentConnectStatusCard)
        statusCard.visibility = View.GONE
        statusText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                val message = s?.toString()?.trim().orEmpty()
                val backgroundActivity = message.isBlank() ||
                    message == "Checking U2U status…" ||
                    message.startsWith("Checking your student profile") ||
                    message.startsWith("Email verified. Checking your student profile") ||
                    message.endsWith("…") || message.endsWith("...")
                statusCard.visibility = if (backgroundActivity) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        studentConnectScrollView = findViewById(R.id.studentConnectScrollView)
        friendRequestsSwipeRefresh = studentConnectScrollView.parent as SwipeRefreshLayout
        friendRequestsSwipeRefresh.setColorSchemeResources(R.color.sc_purple, R.color.sc_lavender)
        friendRequestsSwipeRefresh.isEnabled = false
        friendRequestsSwipeRefresh.setOnChildScrollUpCallback { _, _ -> studentConnectScrollView.scrollY > 0 }
        friendRequestsSwipeRefresh.setOnRefreshListener {
            if (currentScreen == StudentConnectScreen.REQUESTS) refreshFriendRequests(showLoadingMessage = false)
            else friendRequestsSwipeRefresh.isRefreshing = false
        }
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
        profileAvatar = findViewById(R.id.studentConnectProfileAvatar)
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
        refreshFriendRequestsButton.visibility = View.GONE
        chatListTitleText = findViewById(R.id.studentConnectChatListTitle)
        chatListContainer = findViewById(R.id.studentConnectChatListContainer)
        chatListEmptyText = findViewById(R.id.studentConnectChatListEmpty)
        chatFilters = findViewById(R.id.studentConnectChatFilters)
        chatFilterAll = findViewById(R.id.studentConnectChatFilterAll)
        chatFilterUnread = findViewById(R.id.studentConnectChatFilterUnread)
        seeAllChatsButton = findViewById(R.id.studentConnectSeeAllChats)
        requestsIncomingTab = findViewById(R.id.studentConnectRequestsIncomingTab)
        requestsSentTab = findViewById(R.id.studentConnectRequestsSentTab)
        incomingRequestsHeading = findViewById(R.id.studentConnectIncomingRequestsHeading)
        outgoingRequestsHeading = findViewById(R.id.studentConnectOutgoingRequestsHeading)
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
        homeDashboard = findViewById(R.id.studentConnectHomeDashboard)
        homeGreetingText = findViewById(R.id.studentConnectHomeGreeting)
        homeHeroLogo = findViewById(R.id.studentConnectHeroLogo)
        homeHeroLogo.cameraDistance = dp(9000).toFloat()
        homeHeroOrb = findViewById(R.id.studentConnectHeroOrb)
        chatSection = findViewById(R.id.studentConnectChatSection)
        connectionsSection = findViewById(R.id.studentConnectConnectionsSection)
        bottomNavigation = findViewById(R.id.studentConnectBottomNavigation)
        securityNoteText = findViewById(R.id.studentConnectSecurityNote)
        headerNotificationsButton = findViewById(R.id.studentConnectHeaderNotificationsButton)
        headerNotificationBadge = findViewById(R.id.studentConnectHeaderNotificationBadge)
        homeRequestsSubtitle = findViewById(R.id.studentConnectHomeRequestsSubtitle)
        homeConnectionsSubtitle = findViewById(R.id.studentConnectHomeConnectionsSubtitle)
        navItems = listOf(findViewById(R.id.studentConnectNavHome), findViewById(R.id.studentConnectNavSearch), findViewById(R.id.studentConnectNavChats), findViewById(R.id.studentConnectNavRequests), findViewById(R.id.studentConnectNavProfile))
        navIcons = listOf(findViewById(R.id.studentConnectNavHomeIcon), findViewById(R.id.studentConnectNavSearchIcon), findViewById(R.id.studentConnectNavChatsIcon), findViewById(R.id.studentConnectNavRequestsIcon), findViewById(R.id.studentConnectNavProfileIcon))
        navLabels = listOf(findViewById(R.id.studentConnectNavHomeLabel), findViewById(R.id.studentConnectNavSearchLabel), findViewById(R.id.studentConnectNavChatsLabel), findViewById(R.id.studentConnectNavRequestsLabel), findViewById(R.id.studentConnectNavProfileLabel))

        findViewById<TextView>(R.id.studentConnectBackButton).setOnClickListener { handleU2UBack() }
        chatFilterAll.setOnClickListener {
            filterUnreadChatsOnly = false
            updateChatFilterTabs()
            auth.currentUser?.uid?.let { uid -> renderChatList(uid, cachedChatSummaries) }
        }
        chatFilterUnread.setOnClickListener {
            filterUnreadChatsOnly = true
            updateChatFilterTabs()
            auth.currentUser?.uid?.let { uid -> renderChatList(uid, cachedChatSummaries) }
        }
        requestsIncomingTab.setOnClickListener {
            showOutgoingRequestsOnly = false
            updateRequestTabs()
        }
        requestsSentTab.setOnClickListener {
            showOutgoingRequestsOnly = true
            updateRequestTabs()
        }
        headerNotificationsButton.setOnClickListener { navigateToStudentConnectScreen(StudentConnectScreen.NOTIFICATIONS) }
        findViewById<View>(R.id.studentConnectHomeFindCard).setOnClickListener { navigateToStudentConnectScreen(StudentConnectScreen.SEARCH) }
        findViewById<View>(R.id.studentConnectHeroFindButton).setOnClickListener { navigateToStudentConnectScreen(StudentConnectScreen.SEARCH) }
        findViewById<View>(R.id.studentConnectHomeRequestsCard).setOnClickListener { navigateToStudentConnectScreen(StudentConnectScreen.REQUESTS) }
        findViewById<View>(R.id.studentConnectHomeConnectionsCard).setOnClickListener { navigateToStudentConnectScreen(StudentConnectScreen.CONNECTIONS) }
        findViewById<View>(R.id.studentConnectHomeProfileCard).setOnClickListener { navigateToStudentConnectScreen(StudentConnectScreen.PROFILE) }
        findViewById<View>(R.id.studentConnectHomeMarketCard).setOnClickListener {
            startActivity(Intent(this, MarketRequestsActivity::class.java))
        }
        findViewById<View>(R.id.studentConnectHomeNearbySendButton).setOnClickListener {
            openNearbyShare("send")
        }
        findViewById<View>(R.id.studentConnectHomeNearbyReceiveButton).setOnClickListener {
            openNearbyShare("receive")
        }
        findViewById<View>(R.id.studentConnectSeeAllChats).setOnClickListener { navigateToStudentConnectScreen(StudentConnectScreen.CHATS) }
        navItems[0].setOnClickListener { navigateToStudentConnectScreen(StudentConnectScreen.HOME) }
        navItems[1].setOnClickListener { navigateToStudentConnectScreen(StudentConnectScreen.SEARCH) }
        navItems[2].setOnClickListener { navigateToStudentConnectScreen(StudentConnectScreen.CHATS) }
        navItems[3].setOnClickListener { navigateToStudentConnectScreen(StudentConnectScreen.REQUESTS) }
        navItems[4].setOnClickListener { navigateToStudentConnectScreen(StudentConnectScreen.PROFILE) }
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

    private fun openNearbyShare(mode: String) {
        val intent = android.content.Intent(
            this@StudentConnectActivity,
            com.superiorstudent.app.feature.transfer.P2pNearbyTransferActivity::class.java
        ).putExtra(
            "pachattar.u2u.nearby.initial_mode",
            mode
        )
        startActivity(intent)
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
        StudentConnectPresenceTracker.stop(presenceOwnerKey, immediate = true)
        stopConnectionPresenceListeners()
        connectionPresenceCache.clear()
        connectionPresenceViews.clear()
        auth.currentUser?.uid?.let(localCache::clearAccount)
        screenBackStack.clear()
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
        statusText.text = "Signed out of U2U. Your university login is unchanged."
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
        statusText.text = "Creating your separate U2U account…"
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
        statusText.text = "Signing in to U2U…"
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
                    "Email verified. Your U2U identity is ready."
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
                "Email verified. Your U2U identity is ready."
            } else {
                "Email not verified yet. Open the email from Firebase, then check again."
            }
            if (auth.currentUser?.isEmailVerified == true) {
                profileLoadedFor = null
                loadProfileIfNeeded(auth.currentUser!!.uid)
            }
        }
    }

    /**
     * Keeps the entire U2U experience on its scoped navy/purple brand.
     * This affects presentation only; ERP and Firebase sign-in/session logic are unchanged.
     */
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun applySharedDesignTokens() {
        val root = findViewById<View>(R.id.studentConnectRoot) ?: return
        U2UDesignTokens.applyTypographyTree(root)
        applyStudentConnectAuthTypography(root)
        U2UDesignTokens.applyHomeTileRules(root)
        U2UDesignTokens.applySurfaceDepth(root)
        applyHomeDashboardPolish(root)
        applyFindStudentsPolish(root)
        U2UDesignTokens.applyInteractionFeedback(root)
    }

    private fun applyStudentConnectAuthTypography(root: View) {
        val header = root.findViewById<LinearLayout>(R.id.studentConnectBrandHeader) ?: return
        if (header.orientation != LinearLayout.VERTICAL) return

        root.findViewById<TextView>(R.id.studentConnectBrandTitle)?.apply {
            textSize = 30f
            letterSpacing = -0.027f
            typeface = ResourcesCompat.getFont(this@StudentConnectActivity, R.font.poppins_medium)
        }
        root.findViewById<TextView>(R.id.studentConnectBrandTagline)?.apply {
            textSize = 14f
            letterSpacing = 0f
            typeface = ResourcesCompat.getFont(this@StudentConnectActivity, R.font.poppins_regular)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        listOf(
            root.findViewById<Button>(R.id.studentConnectCreateAccountButton),
            root.findViewById<Button>(R.id.studentConnectSignInButton)
        ).forEach { button ->
            button?.apply {
                textSize = 15f
                typeface = ResourcesCompat.getFont(this@StudentConnectActivity, R.font.poppins_medium)
            }
        }
    }

    /** Applies reference-matched visual refinements only to existing Home dashboard controls. */
    private fun applyHomeDashboardPolish(root: View) {
        fun styleText(id: Int, sizeSp: Float, medium: Boolean) {
            root.findViewById<TextView>(id)?.apply {
                setTextSize(sizeSp)
                includeFontPadding = false
                typeface = ResourcesCompat.getFont(
                    this@StudentConnectActivity,
                    if (medium) R.font.poppins_medium else R.font.poppins_regular
                )
                letterSpacing = if (medium) -0.01f else 0f
                setLineSpacing(0f, 1f)
            }
        }
        styleText(R.id.studentConnectHomeHeroSubtitle, 13f, false)
        styleText(R.id.studentConnectHomeNearbyShareTitle, 15f, true)
        styleText(R.id.studentConnectHomeNearbyShareDescription, 12f, false)
        styleText(R.id.studentConnectHomeSpaceTitle, 16f, true)
        styleText(R.id.studentConnectHomeSpaceSubtitle, 12f, false)
        listOf(
            R.id.studentConnectHomeFindCard,
            R.id.studentConnectHomeRequestsCard,
            R.id.studentConnectHomeConnectionsCard,
            R.id.studentConnectHomeProfileCard
        ).forEach { id ->
            root.findViewById<MaterialCardView>(id)?.apply {
                radius = dp(22).toFloat()
                strokeWidth = 0
            }
        }
        listOf(
            R.id.studentConnectHeroFindButton,
            R.id.studentConnectHomeNearbySendButton,
            R.id.studentConnectHomeNearbyReceiveButton
        ).forEach { id ->
            root.findViewById<TextView>(id)?.apply {
                setTextSize(if (id == R.id.studentConnectHeroFindButton) 12.5f else 15f)
                typeface = ResourcesCompat.getFont(this@StudentConnectActivity, R.font.poppins_medium)
                includeFontPadding = false
            }
        }
    }

    /** Applies reference-aligned typography and icon treatment to the existing Find Students screen. */
    private fun applyFindStudentsPolish(root: View) {
        fun styleText(id: Int, sizeSp: Float, medium: Boolean, letterSpacing: Float = 0f) {
            root.findViewById<TextView>(id)?.apply {
                setTextSize(sizeSp)
                includeFontPadding = false
                typeface = ResourcesCompat.getFont(
                    this@StudentConnectActivity,
                    if (medium) R.font.poppins_medium else R.font.poppins_regular
                )
                this.letterSpacing = letterSpacing
            }
        }

        styleText(R.id.studentConnectSearchHeadline, 22f, true, -0.02f)
        styleText(R.id.studentConnectSearchDescription, 13f, false)
        styleText(R.id.studentConnectSearchButton, 15f, true)
        styleText(R.id.studentConnectSearchResultLabel, 12f, false)
        styleText(R.id.studentConnectSearchResultName, 15f, true)
        styleText(R.id.studentConnectSearchResultId, 13f, true)
        styleText(R.id.studentConnectSearchPrivacyText, 12f, false)

        root.findViewById<EditText>(R.id.studentConnectSearchInput)?.apply {
            setTextSize(14f)
            includeFontPadding = false
            typeface = ResourcesCompat.getFont(this@StudentConnectActivity, R.font.poppins_regular)
        }

        if (currentScreen == StudentConnectScreen.SEARCH) {
            root.findViewById<TextView>(R.id.studentConnectBrandTitle)?.apply {
                setTextSize(20f)
                letterSpacing = -0.02f
            }
            root.findViewById<TextView>(R.id.studentConnectBrandTagline)?.apply {
                setTextSize(12f)
            }
            root.findViewById<TextView>(R.id.studentConnectBackButton)?.apply {
                text = ""
                val arrow = ContextCompat.getDrawable(this@StudentConnectActivity, R.drawable.ic_pt_arrow_left)
                    ?.mutate()
                    ?.apply { setTint(ContextCompat.getColor(this@StudentConnectActivity, R.color.sc_text_primary)) }
                setCompoundDrawablesWithIntrinsicBounds(arrow, null, null, null)
                gravity = android.view.Gravity.CENTER
            }
        } else {
            root.findViewById<TextView>(R.id.studentConnectBackButton)?.setCompoundDrawables(null, null, null, null)
        }
    }

    private fun applyStudentConnectWindowInsets() {
        val root = findViewById<View>(R.id.studentConnectRoot)
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ContextCompat.getColor(this, R.color.sc_navy)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.sc_navy)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@StudentConnectActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@StudentConnectActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(
                baseLeft + bars.left, baseTop + bars.top, baseRight + bars.right,
                baseBottom + maxOf(bars.bottom, ime.bottom)
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun arrangeProfileSection() {
        val content = studentConnectScrollView.getChildAt(0) as? LinearLayout ?: return
        content.removeView(profileCard)
        content.removeView(signedInPanel)
        content.removeView(securityNoteText)
        content.addView(profileCard)
        content.addView(signedInPanel)
        content.addView(securityNoteText)
    }

    private fun hideStudentConnectWorkspace() {
        stopDashboardAnimations()
        homeDashboard.visibility = View.GONE
        chatSection.visibility = View.GONE
        connectionsSection.visibility = View.GONE
        studentSearchPanel.visibility = View.GONE
        notificationsPanel.visibility = View.GONE
        friendRequestsPanel.visibility = View.GONE
        profileCard.visibility = View.GONE
        bottomNavigation.visibility = View.GONE
        securityNoteText.visibility = if (auth.currentUser == null || auth.currentUser?.isEmailVerified != true) View.VISIBLE else View.GONE
    }

    private fun navigateToStudentConnectScreen(screen: StudentConnectScreen) {
        if (currentStudentProfile == null || auth.currentUser?.isEmailVerified != true) return
        if (screen == StudentConnectScreen.HOME) {
            screenBackStack.clear()
            if (currentScreen != screen) showStudentConnectScreen(screen)
            return
        }
        if (currentScreen == screen) return
        screenBackStack.navigate(currentScreen, screen)
        showStudentConnectScreen(screen)
    }

    private fun handleU2UBack() {
        val canUseWorkspace = currentStudentProfile != null && auth.currentUser?.isEmailVerified == true
        if (!canUseWorkspace || currentScreen == StudentConnectScreen.HOME) {
            screenBackStack.clear()
            finish()
            return
        }
        showStudentConnectScreen(screenBackStack.back(currentScreen))
    }

    /** Chat is a child of Chats, and Chats is a child of U2U Home. */
    private fun prepareChatReturnDestination() {
        screenBackStack.reset(StudentConnectScreen.HOME, StudentConnectScreen.CHATS)
        if (currentScreen != StudentConnectScreen.CHATS) {
            showStudentConnectScreen(StudentConnectScreen.CHATS)
        }
    }

    private fun launchStudentChat(otherUid: String, otherDisplayName: String) {
        prepareChatReturnDestination()
        startActivity(
            Intent(this, StudentChatActivity::class.java)
                .putExtra(StudentChatActivity.EXTRA_OTHER_UID, otherUid)
                .putExtra(StudentChatActivity.EXTRA_OTHER_DISPLAY_NAME, otherDisplayName)
        )
    }

    private fun showStudentConnectScreen(screen: StudentConnectScreen) {
        if (currentStudentProfile == null || auth.currentUser?.isEmailVerified != true) return
        currentScreen = screen
        friendRequestsSwipeRefresh.isEnabled = screen == StudentConnectScreen.REQUESTS
        if (screen != StudentConnectScreen.REQUESTS) friendRequestsSwipeRefresh.isRefreshing = false
        if (screen == StudentConnectScreen.HOME) filterUnreadChatsOnly = false
        applyStudentConnectBranding()
        homeDashboard.visibility = if (screen == StudentConnectScreen.HOME) View.VISIBLE else View.GONE
        chatSection.visibility = if (screen == StudentConnectScreen.HOME || screen == StudentConnectScreen.CHATS) View.VISIBLE else View.GONE
        studentSearchPanel.visibility = if (screen == StudentConnectScreen.SEARCH) View.VISIBLE else View.GONE
        connectionsSection.visibility = if (screen == StudentConnectScreen.CONNECTIONS) View.VISIBLE else View.GONE
        friendRequestsPanel.visibility = if (screen == StudentConnectScreen.REQUESTS) View.VISIBLE else View.GONE
        notificationsPanel.visibility = if (screen == StudentConnectScreen.NOTIFICATIONS) View.VISIBLE else View.GONE
        profileCard.visibility = if (screen == StudentConnectScreen.PROFILE) View.VISIBLE else View.GONE
        signedInPanel.visibility = if (screen == StudentConnectScreen.PROFILE) View.VISIBLE else View.GONE
        profileSetupPanel.visibility = View.GONE
        bottomNavigation.visibility = View.VISIBLE
        securityNoteText.visibility = if (screen == StudentConnectScreen.PROFILE) View.VISIBLE else View.GONE
        chatFilters.visibility = if (screen == StudentConnectScreen.CHATS) View.VISIBLE else View.GONE
        chatListTitleText.visibility = if (screen == StudentConnectScreen.CHATS) View.GONE else View.VISIBLE
        seeAllChatsButton.visibility = if (screen == StudentConnectScreen.HOME) View.VISIBLE else View.GONE

        val selectedIndex = when (screen) {
            StudentConnectScreen.HOME -> 0
            StudentConnectScreen.SEARCH -> 1
            StudentConnectScreen.CHATS -> 2
            StudentConnectScreen.REQUESTS -> 3
            StudentConnectScreen.PROFILE -> 4
            else -> -1
        }
        navItems.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            item.setBackgroundResource(if (selected) R.drawable.bg_student_connect_nav_selected else android.R.color.transparent)
            navIcons[index].imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, if (selected) R.color.sc_button_primary_text else R.color.sc_nav_icon)
            )
            navLabels[index].setTextColor(ContextCompat.getColor(this, if (selected) R.color.sc_button_primary_text else R.color.sc_nav_icon))
            U2UDesignTokens.applyTextRole(navLabels[index], U2UTextRole.CAPTION, mediumOverride = selected)
        }

        updateChatFilterTabs()
        updateRequestTabs()
        if (screen == StudentConnectScreen.CHATS || screen == StudentConnectScreen.HOME) {
            auth.currentUser?.uid?.let { uid -> renderChatList(uid, cachedChatSummaries) }
        }
        studentConnectScrollView.scrollTo(0, 0)
        syncVisibleConnectionPresence()
        if (screen == StudentConnectScreen.HOME) {
            animateDashboardEntrance()
            startDashboardAnimations()
        } else {
            stopDashboardAnimations()
        }
        applySharedDesignTokens()
    }

    private fun animateDashboardEntrance() {
        homeDashboard.animate().cancel()
        homeDashboard.alpha = 0f
        homeDashboard.translationY = dp(12).toFloat()
        homeDashboard.animate().alpha(1f).translationY(0f).setDuration(300L)
            .setInterpolator(AccelerateDecelerateInterpolator()).start()
        listOf(R.id.studentConnectHomeFindCard, R.id.studentConnectHomeRequestsCard,
            R.id.studentConnectHomeConnectionsCard, R.id.studentConnectHomeProfileCard).forEachIndexed { index, id ->
            val card = findViewById<View>(id)
            card.animate().cancel()
            card.alpha = 0f
            card.translationY = dp(10).toFloat()
            card.animate().alpha(1f).translationY(0f).setStartDelay(index * 55L).setDuration(240L)
                .setInterpolator(AccelerateDecelerateInterpolator()).start()
        }
    }

    private fun startDashboardAnimations() {
        stopDashboardAnimations()
        if (!::homeDashboard.isInitialized || homeDashboard.visibility != View.VISIBLE) return
        // The overlapping ring motif is static in the supplied reference.
    }

    private fun stopDashboardAnimations() {
        heroFloatAnimator?.cancel()
        heroTiltAnimator?.cancel()
        heroOrbAnimator?.cancel()
        heroFloatAnimator = null
        heroTiltAnimator = null
        heroOrbAnimator = null
        if (::homeHeroLogo.isInitialized) {
            homeHeroLogo.translationY = 0f
            homeHeroLogo.rotationY = 12f
        }
        if (::homeHeroOrb.isInitialized) homeHeroOrb.alpha = 0.72f
    }

    private fun applyStudentConnectBranding() {
        studentConnectScrollView.setBackgroundResource(R.drawable.bg_student_connect_auth)
        val user = auth.currentUser
        val hasStudentProfile = currentStudentProfile != null && user?.isEmailVerified == true
        val backButton = findViewById<TextView>(R.id.studentConnectBackButton)
        val brandLogo = findViewById<ImageView>(R.id.studentConnectBrandLogo)
        val brandTitle = findViewById<TextView>(R.id.studentConnectBrandTitle)
        val brandTagline = findViewById<TextView>(R.id.studentConnectBrandTagline)
        val brandHeader = findViewById<LinearLayout>(R.id.studentConnectBrandHeader)
        val notificationContainer = headerNotificationsButton.parent as? View

        val screenTitle = when {
            !hasStudentProfile -> "Join U2U"
            currentScreen == StudentConnectScreen.HOME -> "U2U"
            currentScreen == StudentConnectScreen.SEARCH -> "Find students"
            currentScreen == StudentConnectScreen.CHATS -> "Chats"
            currentScreen == StudentConnectScreen.REQUESTS -> "Friend requests"
            currentScreen == StudentConnectScreen.CONNECTIONS -> "My connections"
            currentScreen == StudentConnectScreen.PROFILE -> "Your profile"
            currentScreen == StudentConnectScreen.NOTIFICATIONS -> "Notifications"
            else -> "U2U"
        }
        val screenSubtitle = when {
            !hasStudentProfile -> "Same campus. Real people.\nNew opportunities."
            currentScreen == StudentConnectScreen.HOME -> "Chat and share with classmates"
            currentScreen == StudentConnectScreen.SEARCH -> "Search by public ID"
            currentScreen == StudentConnectScreen.CHATS -> "Your connections"
            currentScreen == StudentConnectScreen.REQUESTS -> "Manage who can chat with you"
            currentScreen == StudentConnectScreen.CONNECTIONS -> "Your campus circle"
            currentScreen == StudentConnectScreen.PROFILE -> "Visible to your connections"
            currentScreen == StudentConnectScreen.NOTIFICATIONS -> "Requests and updates"
            else -> "Chat and share with classmates"
        }

        brandHeader.orientation = if (hasStudentProfile) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        (brandHeader.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.topMargin = dp(if (hasStudentProfile) 2 else 30)
            brandHeader.layoutParams = params
        }
        backButton.apply {
            text = "‹"
            setTextColor(ContextCompat.getColor(this@StudentConnectActivity, R.color.sc_lavender))
            visibility = if (!hasStudentProfile || currentScreen == StudentConnectScreen.HOME) View.GONE else View.VISIBLE
        }
        brandLogo.apply {
            visibility = if (!hasStudentProfile || currentScreen == StudentConnectScreen.HOME) View.VISIBLE else View.GONE
            layoutParams = layoutParams.apply {
                width = dp(if (hasStudentProfile) 40 else 64)
                height = dp(if (hasStudentProfile) 40 else 64)
            }
            setImageResource(if (hasStudentProfile) R.drawable.ic_student_connect_brand else R.drawable.ic_student_connect_auth_mark)
            setBackgroundResource(if (hasStudentProfile) android.R.color.transparent else R.drawable.bg_student_connect_auth_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        val brandTextBlock = brandTitle.parent as? LinearLayout
        (brandTextBlock?.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.width = if (hasStudentProfile) 0 else LinearLayout.LayoutParams.MATCH_PARENT
            params.height = LinearLayout.LayoutParams.WRAP_CONTENT
            params.weight = if (hasStudentProfile) 1f else 0f
            params.marginStart = dp(if (hasStudentProfile) 12 else 0)
            params.topMargin = dp(if (hasStudentProfile) 0 else 18)
            brandTextBlock.layoutParams = params
        }
        brandTitle.layoutParams = brandTitle.layoutParams.apply {
            height = if (hasStudentProfile) dp(40) else LinearLayout.LayoutParams.WRAP_CONTENT
        }
        brandTitle.apply {
            text = screenTitle
            setTextColor(ContextCompat.getColor(this@StudentConnectActivity, R.color.sc_text_primary))
            textSize = if (!hasStudentProfile) 30f else if (currentScreen == StudentConnectScreen.HOME) 20f else 18f
            letterSpacing = if (!hasStudentProfile) -0.027f else 0f
            typeface = ResourcesCompat.getFont(
                this@StudentConnectActivity,
                if (!hasStudentProfile) R.font.poppins_medium else R.font.poppins_medium
            )
            maxLines = 1
        }
        brandTagline.apply {
            text = screenSubtitle
            setTextColor(ContextCompat.getColor(this@StudentConnectActivity, R.color.sc_text_muted))
            visibility = View.VISIBLE
            textSize = if (!hasStudentProfile) 14f else 11f
            letterSpacing = 0f
            maxLines = 2
            setLineSpacing(if (!hasStudentProfile) dp(3).toFloat() else dp(1).toFloat(), 1f)
            (layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                params.topMargin = dp(if (!hasStudentProfile) 6 else 3)
                layoutParams = params
            }
        }
        findViewById<TextView>(R.id.studentConnectBrandPromise).visibility = View.GONE
        notificationContainer?.visibility =
            if (hasStudentProfile && currentScreen == StudentConnectScreen.HOME) View.VISIBLE else View.GONE

        findViewById<MaterialCardView>(R.id.studentConnectStatusCard).apply {
            setCardBackgroundColor(ContextCompat.getColor(this@StudentConnectActivity, R.color.sc_card))
            setStrokeColor(ContextCompat.getColor(this@StudentConnectActivity, R.color.sc_border))
        }
        statusText.setTextColor(ContextCompat.getColor(this, R.color.sc_text_muted))
        findViewById<View>(R.id.studentConnectPasswordSafetyNote).visibility =
            if (user == null) View.VISIBLE else View.GONE
        updateChatFilterTabs()
        updateRequestTabs()
        applySharedDesignTokens()
    }

    private fun updateChatFilterTabs() {
        if (!::chatFilterAll.isInitialized || !::chatFilterUnread.isInitialized) return
        chatFilterAll.setBackgroundResource(
            if (!filterUnreadChatsOnly) R.drawable.bg_student_connect_segment_selected else android.R.color.transparent
        )
        chatFilterUnread.setBackgroundResource(
            if (filterUnreadChatsOnly) R.drawable.bg_student_connect_segment_selected else android.R.color.transparent
        )
        chatFilterAll.setTextColor(ContextCompat.getColor(
            this, if (!filterUnreadChatsOnly) R.color.sc_segment_ink else R.color.sc_text_primary
        ))
        chatFilterUnread.setTextColor(ContextCompat.getColor(
            this, if (filterUnreadChatsOnly) R.color.sc_segment_ink else R.color.sc_text_primary
        ))
    }

    private fun updateRequestTabs() {
        if (!::requestsIncomingTab.isInitialized || !::requestsSentTab.isInitialized) return
        requestsIncomingTab.setBackgroundResource(
            if (!showOutgoingRequestsOnly) R.drawable.bg_student_connect_segment_selected else android.R.color.transparent
        )
        requestsSentTab.setBackgroundResource(
            if (showOutgoingRequestsOnly) R.drawable.bg_student_connect_segment_selected else android.R.color.transparent
        )
        requestsIncomingTab.setTextColor(ContextCompat.getColor(
            this, if (!showOutgoingRequestsOnly) R.color.sc_segment_ink else R.color.sc_text_primary
        ))
        requestsSentTab.setTextColor(ContextCompat.getColor(
            this, if (showOutgoingRequestsOnly) R.color.sc_segment_ink else R.color.sc_text_primary
        ))
        incomingRequestsHeading.visibility = if (showOutgoingRequestsOnly) View.GONE else View.VISIBLE
        incomingRequestsContainer.visibility = if (showOutgoingRequestsOnly) View.GONE else View.VISIBLE
        incomingRequestsEmptyText.visibility =
            if (!showOutgoingRequestsOnly && incomingRequestsContainer.childCount == 0) View.VISIBLE else View.GONE
        outgoingRequestsHeading.visibility = if (showOutgoingRequestsOnly) View.VISIBLE else View.GONE
        outgoingRequestsContainer.visibility = if (showOutgoingRequestsOnly) View.VISIBLE else View.GONE
        outgoingRequestsEmptyText.visibility =
            if (showOutgoingRequestsOnly && outgoingRequestsContainer.childCount == 0) View.VISIBLE else View.GONE
    }

    private fun renderCurrentUser() {
        val user = auth.currentUser
        val signedIn = user != null
        authForm.visibility = if (signedIn) View.GONE else View.VISIBLE
        signedInPanel.visibility = if (signedIn) View.VISIBLE else View.GONE
        applyStudentConnectBranding()

        if (user == null) {
            hideStudentConnectWorkspace()
            profileSetupPanel.visibility = View.GONE
            profileCard.visibility = View.GONE
            studentSearchPanel.visibility = View.GONE
            studentSearchResultCard.visibility = View.GONE
            stopChatListListener()
            chatListContainer.removeAllViews()
            chatListEmptyText.text = "Sign in to see your recent chats."
            chatListEmptyText.visibility = View.VISIBLE
            chatListTitleText.text = "Recent chats"
            profileLoadInProgressFor = null
            profileLoadedFor = null
            stopNotificationsListener()
            clearNotificationHighlight()
            notificationsPanel.visibility = View.GONE
            notificationsContainer.removeAllViews()
            cachedStudentNotifications = emptyList()
            currentStudentProfile = null
            screenBackStack.clear()
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
            statusText.text = ""
            if (currentStudentProfile?.uid == user.uid) {
                loadProfileIfNeeded(user.uid)
            } else if (profileCacheLookupFor != user.uid) {
                profileCacheLookupFor = user.uid
                localCache.loadProfile(user.uid) { savedProfile ->
                    if (auth.currentUser?.uid != user.uid) return@loadProfile
                    profileCacheLookupFor = null
                    if (savedProfile?.uid == user.uid) showProfile(savedProfile, cachedOnly = true)
                    loadProfileIfNeeded(user.uid)
                }
            }
        } else {
            hideStudentConnectWorkspace()
            profileSetupPanel.visibility = View.GONE
            profileCard.visibility = View.GONE
            statusText.text = "Email verification is required before a student profile can be created."
        }
    }

    private fun loadProfileIfNeeded(uid: String) {
        if (profileLoadInProgressFor == uid || profileLoadedFor == uid) return
        profileLoadInProgressFor = uid
        if (currentStudentProfile?.uid != uid) {
            profileSetupPanel.visibility = View.GONE
            profileCard.visibility = View.GONE
        }
        statusText.text = ""

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
                statusText.text = if (currentStudentProfile?.uid == uid) {
                    "Showing saved U2U data. Could not verify the session right now."
                } else {
                    "Could not verify your Firebase session. Check your connection and try again."
                }
                return@addOnCompleteListener
            }

            profileRepository.getOwnProfile(uid) { profile, error ->
                if (auth.currentUser?.uid != uid) return@getOwnProfile
                profileLoadInProgressFor = null
                profileLoadedFor = uid

                if (error != null) {
                    if (currentStudentProfile?.uid == uid) {
                        statusText.text = "Showing saved U2U data. Could not sync the profile right now."
                    } else {
                        profileLoadedFor = null
                        profileSetupPanel.visibility = View.GONE
                        profileCard.visibility = View.GONE
                        studentSearchPanel.visibility = View.GONE
                        studentSearchResultCard.visibility = View.GONE
                        statusText.text = "Could not load your profile. Check your connection and try again."
                    }
                    return@getOwnProfile
                }

                if (profile == null) {
                    currentStudentProfile = null
                    localCache.clearAccount(uid)
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
                    statusText.text = "Student profile ready"
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
                    statusText.text = "Student profile created"
                }
            }
        }
    }

    private fun showProfile(profile: StudentProfile, cachedOnly: Boolean = false) {
        currentStudentProfile = profile
        if (!cachedOnly) localCache.saveProfile(profile)
        syncOwnPresence()
        notificationsPanel.visibility = View.VISIBLE
        startNotificationsListener(profile.uid)
        startChatListListener(profile.uid)
        if (!cachedOnly) {
            requestStudentConnectNotificationPermissionIfNeeded()
            registerStudentNotificationToken(profile.uid)
        }
        profileSetupPanel.visibility = View.GONE
        profileCard.visibility = View.VISIBLE
        studentSearchPanel.visibility = View.VISIBLE
        friendRequestsPanel.visibility = View.VISIBLE
        profileDisplayName.text = profile.displayName
        profileAvatar.text = profile.displayName.trim()
            .split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.take(1).uppercase(Locale.getDefault()) }
            .ifBlank { profile.displayName.take(2).uppercase(Locale.getDefault()) }
        profilePublicId.text = profile.publicId
        homeGreetingText.text = "Hi, " + profile.displayName.substringBefore(" ").ifBlank { profile.displayName } + "!"
        applyStudentConnectBranding()
        screenBackStack.clear()
        showStudentConnectScreen(StudentConnectScreen.HOME)
        if (!cachedOnly) refreshFriendRequests(showLoadingMessage = false)
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

    private fun startChatListListener(uid: String, forceRefresh: Boolean = false) {
        if (!forceRefresh && chatListListeningFor == uid && chatListListener != null) return
        stopChatListListener()
        chatListListeningFor = uid
        val generation = ++chatListSubscriptionGeneration
        chatListLiveSnapshotReceivedFor = null
        chatListTitleText.text = "Recent chats"
        chatListEmptyText.text = "Loading recent chats…"
        chatListEmptyText.visibility = View.VISIBLE

        localCache.loadChatSummaries(uid) { savedSummaries ->
            if (generation != chatListSubscriptionGeneration ||
                auth.currentUser?.uid != uid || currentStudentProfile?.uid != uid ||
                chatListLiveSnapshotReceivedFor == uid
            ) return@loadChatSummaries
            cachedChatSummaries = savedSummaries
            renderChatList(uid, savedSummaries)
            if (savedSummaries.isNotEmpty()) chatListTitleText.text = "Recent chats · saved"
        }

        chatListListener = studentChatRepository.listenConversationSummaries(uid) { summaries, error ->
            if (generation != chatListSubscriptionGeneration ||
                auth.currentUser?.uid != uid || currentStudentProfile?.uid != uid
            ) return@listenConversationSummaries
            if (error != null || summaries == null) {
                if (cachedChatSummaries.isNotEmpty()) {
                    renderChatList(uid, cachedChatSummaries)
                    chatListTitleText.text = "Recent chats · saved"
                } else {
                    chatListEmptyText.text = if (error?.message.orEmpty().contains("PERMISSION_DENIED", ignoreCase = true)) {
                        "Could not load recent chats. Confirm the current Firestore chat rules are published."
                    } else {
                        "Could not load recent chats. Check your internet connection and return to try again."
                    }
                    chatListEmptyText.visibility = View.VISIBLE
                }
                return@listenConversationSummaries
            }

            chatListLiveSnapshotReceivedFor = uid
            cachedChatSummaries = summaries
            localCache.saveChatSummaries(uid, summaries)
            chatListTitleText.text = "Recent chats"
            renderChatList(uid, summaries)
        }
    }

    private fun stopChatListListener() {
        chatListSubscriptionGeneration++
        chatListListener?.remove()
        chatListListener = null
        chatListListeningFor = null
        chatListLiveSnapshotReceivedFor = null
        cachedChatSummaries = emptyList()
    }

    private fun renderChatList(uid: String, summaries: List<StudentChatSummary>) {
        chatListContainer.removeAllViews()
        val unreadCount = summaries.sumOf { it.unreadCount }
        chatListTitleText.text = if (unreadCount > 0) "Recent chats ($unreadCount unread)" else "Recent chats"
        val visibleSummaries = if (currentScreen == StudentConnectScreen.CHATS && filterUnreadChatsOnly) {
            summaries.filter { it.unreadCount > 0 }
        } else summaries
        chatListEmptyText.text = when {
            summaries.isEmpty() -> "No chats yet. Open Message from My Connections to start a conversation."
            currentScreen == StudentConnectScreen.CHATS && filterUnreadChatsOnly && visibleSummaries.isEmpty() ->
                "No unread chats. You're all caught up."
            else -> ""
        }
        chatListEmptyText.visibility = if (visibleSummaries.isEmpty()) View.VISIBLE else View.GONE
        if (currentScreen == StudentConnectScreen.CHATS) updateChatFilterTabs()

        visibleSummaries.forEach { summary ->
            val row = layoutInflater.inflate(R.layout.item_student_chat_summary, chatListContainer, false)
            row.findViewById<TextView>(R.id.studentChatSummaryName).text = summary.otherDisplayName
            row.findViewById<TextView>(R.id.studentChatSummaryAvatar).text = summary.otherDisplayName
                .trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.take(1).uppercase(Locale.getDefault()) }
            val clearBeforeMillis = localChatClearState.cutoffMillis(uid, summary.conversationId)
            val lastMessageMillis = summary.lastMessageAt?.toDate()?.time
            val isLocallyCleared = StudentChatLocalClearPolicy.isSummaryHidden(
                lastMessageMillis, clearBeforeMillis
            )
            val preview = if (isLocallyCleared) {
                "Chat cleared for you"
            } else {
                summary.lastMessageText?.let { message ->
                    val prefix = if (summary.lastMessageSenderUid == uid) "You: " else ""
                    (prefix + message).take(92).let { if ((prefix + message).length > 92) "$it…" else it }
                } ?: "No messages yet. Say hello!"
            }
            row.findViewById<TextView>(R.id.studentChatSummaryPreview).text = preview
            row.findViewById<TextView>(R.id.studentChatSummaryTime).text =
                if (isLocallyCleared) "Cleared" else formatChatSummaryTime(summary.lastMessageAt ?: summary.updatedAt)
            val unreadBadge = row.findViewById<TextView>(R.id.studentChatSummaryUnreadBadge)
            if (summary.unreadCount > 0) {
                unreadBadge.text = if (summary.unreadCount > 99) "99+" else summary.unreadCount.toString()
                unreadBadge.visibility = View.VISIBLE
            } else {
                unreadBadge.visibility = View.GONE
            }
            row.setOnClickListener {
                val user = auth.currentUser
                if (user == null || !user.isEmailVerified || user.uid != uid) {
                    statusText.text = "Sign in with your verified U2U account before opening a chat."
                    return@setOnClickListener
                }
                launchStudentChat(summary.otherUid, summary.otherDisplayName)
            }
            chatListContainer.addView(row)
        }
        applySharedDesignTokens()
    }

    private fun formatChatSummaryTime(timestamp: com.google.firebase.Timestamp?): String {
        if (timestamp == null) return "New chat"
        val elapsed = (System.currentTimeMillis() - timestamp.toDate().time).coerceAtLeast(0L)
        return when {
            elapsed < 60_000L -> "Just now"
            elapsed < 3_600_000L -> "${elapsed / 60_000L} min ago"
            elapsed < 86_400_000L -> "${elapsed / 3_600_000L} hr ago"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp.toDate().time))
        }
    }

    private fun renderNotifications(notifications: List<StudentNotification>) {
        notificationsContainer.removeAllViews()
        val unreadCount = notifications.count { !it.isRead }
        headerNotificationBadge.text = when {
            unreadCount > 99 -> "99+"
            unreadCount > 0 -> unreadCount.toString()
            else -> ""
        }
        headerNotificationBadge.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
        notificationsTitleText.text = if (unreadCount == 0) "Notifications" else "Notifications ($unreadCount unread)"
        notificationsEmptyText.text = "No notifications yet. Friend request updates will appear here."
        notificationsEmptyText.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
        markAllNotificationsReadButton.visibility = if (unreadCount > 0) View.VISIBLE else View.GONE
        markAllNotificationsReadButton.isEnabled = true

        notifications.forEach { notification ->
            val row = layoutInflater.inflate(R.layout.item_student_connect_notification, notificationsContainer, false)
            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener { openNotification(notification) }
            row.findViewById<TextView>(R.id.studentConnectNotificationItemTitle).text = notification.title
            val notificationIcon = row.findViewById<ImageView>(R.id.studentConnectNotificationItemIcon)
            val iconDetails = when {
                notification.type.contains("message", ignoreCase = true) ->
                    R.drawable.ic_sc_chat to R.color.sc_sky
                notification.type.contains("accepted", ignoreCase = true) ->
                    R.drawable.ic_sc_requests to R.color.sc_mint
                else -> R.drawable.ic_sc_requests to R.color.sc_peach
            }
            notificationIcon.setImageResource(iconDetails.first)
            notificationIcon.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(ContextCompat.getColor(this@StudentConnectActivity, iconDetails.second))
            }
            notificationIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.sc_text_primary)
            )
            row.findViewById<TextView>(R.id.studentConnectNotificationItemBody).text = notification.body
            val readState = row.findViewById<TextView>(R.id.studentConnectNotificationItemReadState)
            readState.text = if (notification.isRead) "READ" else "UNREAD"
            readState.setTextColor(ContextCompat.getColor(
                this, if (notification.isRead) R.color.sc_text_muted else R.color.sc_purple
            ))
            row.findViewById<TextView>(R.id.studentConnectNotificationItemTime).text =
                formatNotificationTime(notification.createdAt)
            val markReadButton = row.findViewById<Button>(R.id.studentConnectNotificationItemMarkReadButton)
            markReadButton.visibility = if (notification.isRead) View.GONE else View.VISIBLE
            markReadButton.setOnClickListener {
                markReadButton.isEnabled = false
                markNotificationAsRead(notification) readResult@{ error ->
                    if (auth.currentUser?.uid != notification.recipientUid) return@readResult
                    if (error != null) {
                        markReadButton.isEnabled = true
                        statusText.text = "Could not mark notification as read. Check the latest firestore.rules."
                    }
                }
            }
            notificationsContainer.addView(row)
        }
        applySharedDesignTokens()
    }

    private fun markNotificationAsRead(
        notification: StudentNotification,
        callback: (Exception?) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified || user.uid != notification.recipientUid) {
            callback(IllegalStateException("Sign in to the account that received this notification."))
            return
        }
        if (notification.isRead) {
            callback(null)
            return
        }
        studentNotificationRepository.markAsRead(user.uid, notification.id) { error ->
            if (auth.currentUser?.uid != user.uid) return@markAsRead
            callback(error)
        }
    }

    private fun openNotification(notification: StudentNotification) {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified || user.uid != notification.recipientUid) {
            statusText.text = "Sign in to the account that received this notification."
            return
        }

        markNotificationAsRead(notification) readResult@{ error ->
            if (auth.currentUser?.uid != user.uid) return@readResult
            if (error != null) {
                statusText.text = "Could not mark notification as read. Check the latest firestore.rules."
            }
        }

        val initialTarget = StudentNotificationNavigation.targetForType(notification.type)
        if (initialTarget == null) {
            statusText.text = "This notification type cannot open a related section."
            return
        }
        statusText.text = "Opening related request…"
        friendRequestRepository.loadRequests(user.uid) { requests, error ->
            if (auth.currentUser?.uid != user.uid) return@loadRequests
            if (error != null || requests == null) {
                statusText.text = "Could not load the related request. Check your connection and tap Refresh to retry."
                scrollToNotificationSection(initialTarget)
                return@loadRequests
            }

            cachedFriendRequests = requests
            val relatedRequest = requests.firstOrNull { it.id == notification.requestId }
            val target = if (
                notification.type == "friend_request" &&
                relatedRequest?.status == FriendRequestStatus.ACCEPTED &&
                relatedRequest.receiverUid == user.uid
            ) {
                StudentNotificationTarget.CONNECTION
            } else {
                initialTarget
            }

            val validTargetRequest = when (target) {
                StudentNotificationTarget.INCOMING_REQUEST -> requests.firstOrNull {
                    it.id == notification.requestId &&
                        it.receiverUid == user.uid &&
                        it.status != FriendRequestStatus.ACCEPTED &&
                        it.status != FriendRequestStatus.REMOVED
                }
                StudentNotificationTarget.CONNECTION -> requests.firstOrNull {
                    it.id == notification.requestId &&
                        it.senderUid == user.uid &&
                        it.status == FriendRequestStatus.ACCEPTED
                }
            }

            if (validTargetRequest == null) {
                clearNotificationHighlight()
                renderFriendRequestLists(user.uid, requests)
                scrollToNotificationSection(target)
                statusText.text = "This request has changed since the notification was created. Check the section for its latest status."
                return@loadRequests
            }

            highlightNotificationRequest(user.uid, validTargetRequest.id, target, requests)
            statusText.text = when (target) {
                StudentNotificationTarget.INCOMING_REQUEST -> "Opened the related incoming request."
                StudentNotificationTarget.CONNECTION -> "Opened the related connection."
            }
        }
    }

    private fun highlightNotificationRequest(
        uid: String,
        requestId: String,
        target: StudentNotificationTarget,
        requests: List<FriendRequest>
    ) {
        notificationHighlightClearRunnable?.let(notificationHighlightHandler::removeCallbacks)
        notificationHighlightClearRunnable = null
        highlightedNotificationRequestId = requestId
        highlightedNotificationTarget = target

        cachedFriendRequests = requests
        renderFriendRequestLists(uid, requests)
        navigateToStudentConnectScreen(if (target == StudentNotificationTarget.INCOMING_REQUEST) StudentConnectScreen.REQUESTS else StudentConnectScreen.CONNECTIONS)
        val targetContainer = when (target) {
            StudentNotificationTarget.INCOMING_REQUEST -> incomingRequestsContainer
            StudentNotificationTarget.CONNECTION -> connectionsContainer
        }
        val matchingRow = (0 until targetContainer.childCount)
            .map { targetContainer.getChildAt(it) }
            .firstOrNull { it.tag == requestId }
        val scrollTarget = matchingRow ?: when (target) {
            StudentNotificationTarget.INCOMING_REQUEST -> findViewById(R.id.studentConnectIncomingRequestsHeading)
            StudentNotificationTarget.CONNECTION -> connectionsTitleText
        }
        scrollToNotificationTarget(scrollTarget)

        val clearHighlight = Runnable {
            if (highlightedNotificationRequestId == requestId && highlightedNotificationTarget == target) {
                highlightedNotificationRequestId = null
                highlightedNotificationTarget = null
                notificationHighlightClearRunnable = null
                if (auth.currentUser?.uid == uid) renderFriendRequestLists(uid, cachedFriendRequests)
            }
        }
        notificationHighlightClearRunnable = clearHighlight
        notificationHighlightHandler.postDelayed(clearHighlight, 2_800L)
    }

    private fun clearNotificationHighlight() {
        notificationHighlightClearRunnable?.let(notificationHighlightHandler::removeCallbacks)
        notificationHighlightClearRunnable = null
        highlightedNotificationRequestId = null
        highlightedNotificationTarget = null
    }

    private fun scrollToNotificationSection(target: StudentNotificationTarget) {
        navigateToStudentConnectScreen(if (target == StudentNotificationTarget.INCOMING_REQUEST) StudentConnectScreen.REQUESTS else StudentConnectScreen.CONNECTIONS)
        val sectionView = when (target) {
            StudentNotificationTarget.INCOMING_REQUEST -> findViewById(R.id.studentConnectIncomingRequestsHeading)
            StudentNotificationTarget.CONNECTION -> connectionsTitleText
        }
        scrollToNotificationTarget(sectionView)
    }

    private fun scrollToNotificationTarget(target: View) {
        studentConnectScrollView.post {
            val targetLocation = IntArray(2)
            val scrollLocation = IntArray(2)
            target.getLocationOnScreen(targetLocation)
            studentConnectScrollView.getLocationOnScreen(scrollLocation)
            val topPadding = (18 * resources.displayMetrics.density).toInt()
            val targetY = studentConnectScrollView.scrollY +
                targetLocation[1] - scrollLocation[1] - topPadding
            studentConnectScrollView.smoothScrollTo(0, targetY.coerceAtLeast(0))
        }
    }

    private fun formatNotificationTime(timestamp: com.google.firebase.Timestamp?): String {
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

    private fun syncOwnPresence() {
        val user = if (::auth.isInitialized) auth.currentUser else null
        val profile = currentStudentProfile
        if (activityStarted && user != null && user.isEmailVerified && profile?.uid == user.uid) {
            StudentConnectPresenceTracker.start(presenceOwnerKey, user.uid)
        } else {
            StudentConnectPresenceTracker.stop(presenceOwnerKey)
        }
    }

    private fun syncVisibleConnectionPresence() {
        val user = if (::auth.isInitialized) auth.currentUser else null
        if (!activityStarted || currentScreen != StudentConnectScreen.CONNECTIONS ||
            user == null || !user.isEmailVerified || currentStudentProfile?.uid != user.uid
        ) {
            stopConnectionPresenceListeners()
            return
        }
        val visibleConnections = cachedFriendRequests
            .filter { it.status == FriendRequestStatus.ACCEPTED }
            .filter { request ->
                val isSender = request.senderUid == user.uid
                val name = if (isSender) request.receiverName else request.senderName
                val publicId = if (isSender) request.receiverPublicId else request.senderPublicId
                StudentConnectionFilter.matches(connectionsQuery, name, publicId)
            }
        syncConnectionPresenceListeners(user.uid, visibleConnections)
    }

    private fun syncConnectionPresenceListeners(uid: String, connections: List<FriendRequest>) {
        if (!activityStarted || currentScreen != StudentConnectScreen.CONNECTIONS ||
            auth.currentUser?.uid != uid || auth.currentUser?.isEmailVerified != true
        ) {
            stopConnectionPresenceListeners()
            return
        }
        val targetUids = connections.mapNotNull { request ->
            val peerUid = if (request.senderUid == uid) request.receiverUid
            else if (request.receiverUid == uid) request.senderUid
            else null
            peerUid?.takeIf { it.isNotBlank() && it != uid }
        }.toSet()

        (connectionPresenceListeners.keys - targetUids).forEach { removedUid ->
            connectionPresenceListeners.remove(removedUid)?.remove()
            connectionPresenceCache.remove(removedUid)
        }
        targetUids.forEach { peerUid ->
            if (connectionPresenceListeners.containsKey(peerUid)) return@forEach
            val listener = presenceRepository.listenPresence(uid, peerUid) { presence, error ->
                if (!activityStarted || currentScreen != StudentConnectScreen.CONNECTIONS ||
                    auth.currentUser?.uid != uid
                ) return@listenPresence
                if (error != null) {
                    Log.w("StudentConnectPresence", "Could not load connection presence.", error)
                    return@listenPresence
                }
                if (presence == null) connectionPresenceCache.remove(peerUid)
                else connectionPresenceCache[peerUid] = presence
                refreshConnectionPresenceLabels()
            }
            if (listener != null) connectionPresenceListeners[peerUid] = listener
        }
        presenceUiHandler.removeCallbacks(presenceUiRefreshRunnable)
        if (targetUids.isNotEmpty()) presenceUiHandler.postDelayed(presenceUiRefreshRunnable, 15_000L)
        refreshConnectionPresenceLabels()
    }

    private fun stopConnectionPresenceListeners() {
        connectionPresenceListeners.values.forEach { it.remove() }
        connectionPresenceListeners.clear()
        presenceUiHandler.removeCallbacks(presenceUiRefreshRunnable)
    }

    private fun refreshConnectionPresenceLabels() {
        val now = System.currentTimeMillis()
        connectionPresenceViews.forEach { (peerUid, statusView) ->
            val label = StudentPresenceFormatter.label(connectionPresenceCache[peerUid], now)
            statusView.text = if (label == "Offline") "Connected · Offline" else "Connected · $label"
            statusView.setTextColor(ContextCompat.getColor(
                this, if (label == "Online") R.color.sc_online else R.color.sc_text_muted
            ))
        }
    }

    private fun refreshFriendRequests(showLoadingMessage: Boolean = true) {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified || currentStudentProfile?.uid != user.uid) {
            friendRequestsSwipeRefresh.isRefreshing = false
            friendRequestsPanel.visibility = View.GONE
            return
        }

        if (friendRequestsLoadingFor == user.uid) {
            friendRequestsRefreshQueued = true
            return
        }

        friendRequestsPanel.visibility = if (currentScreen == StudentConnectScreen.REQUESTS) View.VISIBLE else View.GONE
        friendRequestsLoadingFor = user.uid
        friendRequestsRefreshQueued = false
        val loadSequence = ++friendRequestsLoadSequence
        refreshFriendRequestsButton.isEnabled = false
        if (showLoadingMessage) statusText.text = "Loading friend requests…"

        friendRequestRepository.loadRequests(user.uid) { requests, error ->
            if (loadSequence != friendRequestsLoadSequence) return@loadRequests

            friendRequestsLoadingFor = null
            refreshFriendRequestsButton.isEnabled = true
            friendRequestsSwipeRefresh.isRefreshing = false
            if (auth.currentUser?.uid != user.uid) return@loadRequests

            if (error != null || requests == null) {
                cachedFriendRequests = emptyList()
                incomingRequestsContainer.removeAllViews()
                outgoingRequestsContainer.removeAllViews()
                connectionsContainer.removeAllViews()
                incomingRequestsEmptyText.text = "Could not load requests. Pull down to try again."
                outgoingRequestsEmptyText.text = "Could not load requests. Pull down to try again."
                incomingRequestsEmptyText.visibility = View.VISIBLE
                outgoingRequestsEmptyText.visibility = View.VISIBLE
                statusText.text = if (error?.message.orEmpty().contains("PERMISSION_DENIED", ignoreCase = true)) {
                    "Firestore rules blocked loading friend requests. Confirm the published rules allow request participants to read their requests."
                } else {
                    "Could not load friend requests. Check your connection and pull down to retry."
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
        connectionPresenceViews.clear()
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
        syncConnectionPresenceListeners(uid, if (currentScreen == StudentConnectScreen.CONNECTIONS) connections else emptyList())
        refreshConnectionPresenceLabels()

        connectionsTitleText.text = "My Connections (" + allConnections.size + ")"
        homeConnectionsSubtitle.text = if (allConnections.isEmpty()) "Start your network" else allConnections.size.toString() + " connected"
        val pendingIncomingCount = incoming.count { it.status == FriendRequestStatus.PENDING }
        homeRequestsSubtitle.text = if (pendingIncomingCount == 0) "Manage requests" else pendingIncomingCount.toString() + " new request" + (if (pendingIncomingCount == 1) "" else "s")
        connectionsEmptyText.text = when {
            allConnections.isEmpty() -> "No connections yet. Accept a friend request to connect with classmates."
            connections.isEmpty() -> "No connections match your search."
            else -> ""
        }
        connectionsEmptyText.visibility = if (connections.isEmpty()) View.VISIBLE else View.GONE

        incomingRequestsEmptyText.text = "No incoming friend requests yet."
        outgoingRequestsEmptyText.text = "You haven't sent any friend requests yet."
        requestsIncomingTab.text = "Incoming " + incoming.size
        requestsSentTab.text = "Sent " + outgoing.size
        incomingRequestsEmptyText.visibility = if (incoming.isEmpty()) View.VISIBLE else View.GONE
        outgoingRequestsEmptyText.visibility = if (outgoing.isEmpty()) View.VISIBLE else View.GONE
        updateRequestTabs()
        applySharedDesignTokens()
    }

    private fun addFriendRequestRow(
        container: LinearLayout,
        request: FriendRequest,
        incomingRequest: Boolean
    ) {
        val row = layoutInflater.inflate(R.layout.item_student_connect_friend_request, container, false)
        row.tag = request.id
        val shouldHighlight = request.id == highlightedNotificationRequestId &&
            ((container === incomingRequestsContainer &&
                highlightedNotificationTarget == StudentNotificationTarget.INCOMING_REQUEST) ||
                (container === connectionsContainer &&
                    highlightedNotificationTarget == StudentNotificationTarget.CONNECTION))
        (row as? MaterialCardView)?.apply {
            setStrokeColor(ContextCompat.getColor(
                this@StudentConnectActivity,
                if (shouldHighlight) R.color.sc_purple else R.color.sc_border
            ))
            strokeWidth = (resources.displayMetrics.density *
                if (shouldHighlight) 2 else 1).toInt().coerceAtLeast(1)
        }
        val nameText = row.findViewById<TextView>(R.id.studentConnectFriendRequestName)
        val publicIdText = row.findViewById<TextView>(R.id.studentConnectFriendRequestId)
        val requestStatusText = row.findViewById<TextView>(R.id.studentConnectFriendRequestStatus)
        val acceptButton = row.findViewById<Button>(R.id.studentConnectFriendRequestAcceptButton)
        val rejectButton = row.findViewById<Button>(R.id.studentConnectFriendRequestRejectButton)
        val cancelButton = row.findViewById<Button>(R.id.studentConnectFriendRequestCancelButton)
        val resendButton = row.findViewById<Button>(R.id.studentConnectFriendRequestResendButton)
        val copyIdButton = row.findViewById<Button>(R.id.studentConnectFriendRequestCopyIdButton)
        val removeConnectionButton = row.findViewById<Button>(R.id.studentConnectFriendRequestRemoveButton)
        val messageButton = row.findViewById<Button>(R.id.studentConnectFriendRequestMessageButton)
        val actionButtons = listOf(
            acceptButton, rejectButton, cancelButton, resendButton,
            copyIdButton, removeConnectionButton, messageButton
        )
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
        if (container === connectionsContainer && request.status == FriendRequestStatus.ACCEPTED) {
            val currentUid = auth.currentUser?.uid
            val peerUid = if (request.senderUid == currentUid) request.receiverUid else request.senderUid
            if (peerUid.isNotBlank()) connectionPresenceViews[peerUid] = requestStatusText
        }

        when {
            request.status == FriendRequestStatus.ACCEPTED -> {
                if (container === connectionsContainer) {
                    messageButton.visibility = View.VISIBLE
                    messageButton.setOnClickListener { openChatWithConnection(request) }
                }
                removeConnectionButton.visibility = View.VISIBLE
                removeConnectionButton.setOnClickListener {
                    AlertDialog.Builder(this, R.style.ThemeOverlay_StudentConnect_Dialog)
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

    private fun openChatWithConnection(request: FriendRequest) {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified || currentStudentProfile?.uid != user.uid) {
            statusText.text = "Sign in with your verified U2U account before opening a chat."
            return
        }
        if (request.status != FriendRequestStatus.ACCEPTED ||
            (request.senderUid != user.uid && request.receiverUid != user.uid)
        ) {
            statusText.text = "Chat is available only for an accepted connection."
            return
        }

        val otherUid = if (request.senderUid == user.uid) request.receiverUid else request.senderUid
        val otherName = if (request.senderUid == user.uid) request.receiverName else request.senderName
        if (otherUid.isBlank() || otherName.trim().length !in 2..60) {
            statusText.text = "The connected student's profile is incomplete. Refresh and try again."
            return
        }

        launchStudentChat(otherUid, otherName)
    }

    private fun updateFriendRequestStatus(
        request: FriendRequest,
        nextStatus: String,
        actionLabel: String,
        actionButtons: List<Button>
    ) {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified) {
            statusText.text = "Sign in with your verified U2U account before managing requests."
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
            statusText.text = "Sign in with your verified U2U account first."
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
            statusText.text = "Sign in with your verified U2U account first."
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
