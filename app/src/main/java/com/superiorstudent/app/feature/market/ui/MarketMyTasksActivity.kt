package com.superiorstudent.app.feature.market.ui

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.tasks.Tasks
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.feature.market.data.MarketAppliedTask
import com.superiorstudent.app.feature.market.data.MarketBudgetType
import com.superiorstudent.app.feature.market.data.MarketConversation
import com.superiorstudent.app.feature.market.data.MarketProposal
import com.superiorstudent.app.feature.market.data.MarketProposalStatus
import com.superiorstudent.app.feature.market.data.MarketRepository
import com.superiorstudent.app.feature.market.data.MarketTask
import com.superiorstudent.app.feature.market.data.MarketTaskStatus
import com.superiorstudent.app.ui.AppThemePreferences

/** Screen 15: the signed-in user's posted tasks and proposals. */
class MarketMyTasksActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var taskList: LinearLayout
    private lateinit var postedTab: TextView
    private lateinit var appliedTab: TextView
    private val repository by lazy { MarketRepository() }
    private var postedTasks: List<MarketTask> = emptyList()
    private var appliedTasks: List<MarketAppliedTask> = emptyList()
    private var conversations: List<MarketConversation> = emptyList()
    private var loading = false
    private var loadError: String? = null
    private var activeTab = MarketMyTasksTab.APPLIED

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        activeTab = savedInstanceState?.getString(STATE_TAB)
            ?.let { runCatching { MarketMyTasksTab.valueOf(it) }.getOrNull() }
            ?: MarketMyTasksTab.APPLIED

        root = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.market_bg))
            clipToPadding = false
        }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        setContentView(root)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(6)
        })
        content.addView(buildTabs(), LinearLayout.LayoutParams(-1, dp(54)).apply {
            topMargin = dp(6)
            marginStart = dp(16)
            marginEnd = dp(16)
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(12)
        })
        taskList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(24))
        }
        scroll.addView(taskList, FrameLayout.LayoutParams(-1, -2))

        renderTasks()
        loadTasks()
        applyWindowInsets()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TAB, activeTab.name)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::taskList.isInitialized) loadTasks()
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = FrameLayout(this).apply {
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            contentDescription = "Back"
            addView(MarketProposalsIconView(this@MarketMyTasksActivity,
                MarketProposalsIcon.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))

        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headings.addView(makeText("My tasks", 20f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.02f
        })
        headings.addView(makeText("Posted and applied", 13f, false,
            color(R.color.market_ink2)))
        row.addView(headings, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun buildTabs(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = rounded(color(R.color.market_card), 20f, color(R.color.market_line))
        }

        postedTab = buildTab("Posted 0",
            MarketMyTasksTab.POSTED)
        appliedTab = buildTab("Applied 0",
            MarketMyTasksTab.APPLIED)
        outer.addView(postedTab, LinearLayout.LayoutParams(0, -1, 1f))
        outer.addView(appliedTab, LinearLayout.LayoutParams(0, -1, 1f))
        return outer
    }

    private fun buildTab(label: String, tab: MarketMyTasksTab): TextView =
        makeText(label, 13f, true, color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            isClickable = true
            isFocusable = true
            setOnClickListener {
                activeTab = tab
                renderTasks()
            }
        }

    private fun renderTasks() {
        if (!::taskList.isInitialized) return
        updateTabs()
        taskList.removeAllViews()
        if (loading) {
            showListMessage("Loading your tasks…")
            return
        }
        loadError?.let {
            showListMessage(it + "  Tap to retry.", retry = true)
            return
        }
        val items = when (activeTab) {
            MarketMyTasksTab.POSTED -> postedTasks.map(::postedItem)
            MarketMyTasksTab.APPLIED -> appliedTasks.map(::appliedItem)
        }
        if (items.isEmpty()) {
            showListMessage(if (activeTab == MarketMyTasksTab.POSTED)
                "You haven’t posted any tasks yet." else "You haven’t sent any proposals yet.")
            return
        }
        items.forEachIndexed { index, item ->
            taskList.addView(buildTaskCard(item), LinearLayout.LayoutParams(-1, -2).apply {
                if (index > 0) topMargin = dp(10)
            })
        }
    }

    private fun updateTabs() {
        listOf(
            Triple(MarketMyTasksTab.POSTED, postedTab, "Posted ${postedTasks.size}"),
            Triple(MarketMyTasksTab.APPLIED, appliedTab, "Applied ${appliedTasks.size}")
        ).forEach { (tab, view, label) ->
            view.text = label
            val selected = activeTab == tab
            view.background = if (selected) {
                rounded(color(R.color.market_segment), 15f)
            } else {
                GradientDrawable().apply { setColor(android.graphics.Color.TRANSPARENT) }
            }
            view.setTextColor(color(if (selected) R.color.market_segment_ink else R.color.market_ink2))
        }
    }

    private fun buildTaskCard(item: MarketMyTaskItem): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(color(R.color.market_card), 20f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            setOnClickListener { onTaskSelected(item) }
            contentDescription = item.category + ", " + item.title + ", " + item.status
        }

        val pills = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        pills.addView(makePill(item.category, categoryBackground(item.categoryTone),
            color(R.color.market_ink)), LinearLayout.LayoutParams(-2, -2))
        // Flexible spacer keeps the status chip at the far right, like the reference.
        pills.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        pills.addView(makePill(item.status, statusBackground(item.statusTone),
            statusForeground(item.statusTone)), LinearLayout.LayoutParams(-2, -2))
        card.addView(pills)

        card.addView(makeText(item.title, 14f, true, color(R.color.market_ink)).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(dp(1).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        card.addView(makeText(item.detailLine, 12.5f, false,
            color(R.color.market_ink2)).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        return card
    }

    private fun makePill(label: String, fill: Int, foreground: Int): TextView =
        makeText(label, 11f, true, foreground).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = rounded(fill, 99f)
        }

    private fun categoryBackground(tone: MarketMyTaskTone): Int = color(when (tone) {
        MarketMyTaskTone.PEACH -> R.color.market_peach
        MarketMyTaskTone.MINT -> R.color.market_mint
        MarketMyTaskTone.LAVENDER -> R.color.market_lav
        MarketMyTaskTone.SKY -> R.color.market_sky
        MarketMyTaskTone.LIME -> R.color.market_match
        MarketMyTaskTone.SUCCESS -> R.color.market_mint
        MarketMyTaskTone.WARNING -> R.color.warn_bg
    })

    private fun statusBackground(tone: MarketMyTaskTone): Int = color(when (tone) {
        MarketMyTaskTone.PEACH -> R.color.market_peach
        MarketMyTaskTone.MINT -> R.color.market_mint
        MarketMyTaskTone.LAVENDER -> R.color.market_lav
        MarketMyTaskTone.SKY -> R.color.market_sky
        MarketMyTaskTone.LIME -> R.color.market_match
        MarketMyTaskTone.SUCCESS -> R.color.market_mint
        MarketMyTaskTone.WARNING -> R.color.warn_bg
    })

    private fun statusForeground(tone: MarketMyTaskTone): Int = color(when (tone) {
        MarketMyTaskTone.PEACH,
        MarketMyTaskTone.MINT,
        MarketMyTaskTone.LAVENDER,
        MarketMyTaskTone.SKY -> R.color.market_ink
        MarketMyTaskTone.LIME -> R.color.market_match_ink
        MarketMyTaskTone.SUCCESS -> R.color.ok
        MarketMyTaskTone.WARNING -> R.color.warn
    })

    private fun onTaskSelected(item: MarketMyTaskItem) {
        when (item.action) {
            MarketMyTaskAction.OPEN_POSTED_DETAIL ->
                startActivity(Intent(this, MarketTaskPosterDetailActivity::class.java).apply {
                    putExtra(MarketTaskPosterDetailActivity.EXTRA_TASK_ID, item.id)
                })
            MarketMyTaskAction.OPEN_CHAT_PREVIEW -> {
                val conversationId = item.conversationId
                if (conversationId.isNullOrBlank()) {
                    AlertDialog.Builder(this)
                        .setTitle(item.title)
                        .setMessage("${item.status}\n\nThe associated Market conversation is unavailable right now.")
                        .setPositiveButton("Got it", null)
                        .show()
                } else {
                    startActivity(Intent(this, MarketTaskChatActivity::class.java).apply {
                        putExtra(MarketTaskChatActivity.EXTRA_CONVERSATION_ID, conversationId)
                        putExtra(MarketTaskChatActivity.EXTRA_HELPER_NAME, item.helperName)
                        putExtra(MarketTaskChatActivity.EXTRA_TASK_TITLE, item.title)
                        putExtra(MarketTaskChatActivity.EXTRA_PRICE_PKR, item.pricePkr)
                    })
                }
            }
            MarketMyTaskAction.RATE_TASK -> {
                val targetUid = item.counterpartUid.orEmpty()
                if (targetUid.isBlank()) {
                    showTaskInfo(item, "The other task participant is unavailable for rating.")
                } else {
                    try {
                        repository.loadMyTaskRating(item.id).addOnCompleteListener { result ->
                            if (isFinishing || isDestroyed) return@addOnCompleteListener
                            if (!result.isSuccessful) {
                                showTaskInfo(item, result.exception?.localizedMessage
                                    ?: "Your rating status could not be checked.")
                            } else if (result.result != null) {
                                showTaskInfo(item, "You have already rated this completed task.")
                            } else {
                                startActivity(Intent(this, MarketTaskRatingActivity::class.java).apply {
                                    putExtra(MarketTaskRatingActivity.EXTRA_TASK_ID, item.id)
                                    putExtra(MarketTaskRatingActivity.EXTRA_TARGET_UID, targetUid)
                                    putExtra(MarketTaskRatingActivity.EXTRA_HELPER_NAME, item.helperName)
                                    putExtra(MarketTaskRatingActivity.EXTRA_TASK_TITLE, item.title)
                                })
                            }
                        }
                    } catch (error: Exception) {
                        showTaskInfo(item, error.localizedMessage ?: "Rating is unavailable.")
                    }
                }
            }
            MarketMyTaskAction.WITHDRAW_PROPOSAL -> AlertDialog.Builder(this)
                .setTitle("Withdraw proposal?")
                .setMessage("Your proposal will be removed from this task's active proposals.")
                .setNegativeButton("Keep proposal", null)
                .setPositiveButton("Withdraw") { _, _ ->
                    try {
                        repository.withdrawProposal(item.id).addOnCompleteListener { result ->
                            if (isFinishing || isDestroyed) return@addOnCompleteListener
                            if (result.isSuccessful) loadTasks()
                            else showTaskInfo(item, result.exception?.localizedMessage
                                ?: "Your proposal could not be withdrawn.")
                        }
                    } catch (error: Exception) {
                        showTaskInfo(item, error.localizedMessage ?: "Your proposal could not be withdrawn.")
                    }
                }
                .show()
            MarketMyTaskAction.PREVIEW_INFO -> AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage(item.status + "\n\n" + item.detailLine)
                .setPositiveButton("Got it", null)
                .show()
        }
    }

    private fun showTaskInfo(item: MarketMyTaskItem, message: String) {
        AlertDialog.Builder(this).setTitle(item.title).setMessage(message)
            .setPositiveButton("Got it", null).show()
    }

    private fun loadTasks(reconcileExpired: Boolean = true) {
        if (isFinishing || isDestroyed || loading) return
        loading = true
        loadError = null
        conversations = emptyList()
        renderTasks()
        val posted: com.google.android.gms.tasks.Task<List<MarketTask>>
        val applied: com.google.android.gms.tasks.Task<List<MarketAppliedTask>>
        val chats = try {
            repository.loadMyMarketConversations()
        } catch (_: Exception) {
            null
        }
        try {
            posted = repository.loadMyTasks()
            applied = repository.loadMyAppliedTasks()
        } catch (error: Exception) {
            loading = false
            loadError = error.localizedMessage ?: "Your Market activity could not be loaded."
            renderTasks()
            return
        }
        chats?.addOnCompleteListener { chatResult ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            conversations = if (chatResult.isSuccessful) chatResult.result.orEmpty() else emptyList()
            renderTasks()
        }
        posted.addOnCompleteListener { postedResult ->
            applied.addOnCompleteListener { appliedResult ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener
                if (!postedResult.isSuccessful || !appliedResult.isSuccessful) {
                    loading = false
                    loadError = (postedResult.exception ?: appliedResult.exception)
                        ?.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Your Market activity could not be loaded."
                    renderTasks()
                    return@addOnCompleteListener
                }

                val loadedTasks = postedResult.result.orEmpty()
                val now = System.currentTimeMillis()
                val expiredOpenTasks = if (reconcileExpired) loadedTasks.filter { task ->
                    task.status == MarketTaskStatus.OPEN &&
                        task.expiresAt.toDate().time < now
                } else emptyList()
                if (expiredOpenTasks.isNotEmpty()) {
                    val expiryWrites = expiredOpenTasks.mapNotNull { task ->
                        runCatching { repository.expireTask(task.id) }.getOrNull()
                    }
                    if (expiryWrites.size != expiredOpenTasks.size) {
                        loading = false
                        loadError = "Expired tasks could not be closed. Tap to retry."
                        renderTasks()
                        return@addOnCompleteListener
                    }
                    Tasks.whenAllComplete(expiryWrites).addOnCompleteListener expiryComplete@{
                        if (isFinishing || isDestroyed) return@expiryComplete
                        if (expiryWrites.any { !it.isSuccessful }) {
                            loading = false
                            loadError = "Expired tasks could not be closed. Tap to retry."
                            renderTasks()
                        } else {
                            loading = false
                            loadTasks(reconcileExpired = false)
                        }
                    }
                    return@addOnCompleteListener
                }

                postedTasks = loadedTasks
                appliedTasks = appliedResult.result.orEmpty()
                loading = false
                renderTasks()
            }
        }
    }

    private fun showListMessage(message: String, retry: Boolean = false) {
        taskList.addView(makeText(message, 13f, false, color(R.color.market_ink2)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(28), dp(16), dp(28))
            if (retry) {
                isClickable = true
                isFocusable = true
                setOnClickListener { loadTasks() }
            }
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun postedItem(task: MarketTask) = MarketMyTaskItem(
        id = task.id,
        category = task.category,
        categoryTone = categoryTone(task.category),
        status = task.status.wireValue.replace('_', ' ').replaceFirstChar(Char::uppercase),
        statusTone = taskStatusTone(task.status),
        title = task.title,
        detailLine = "${budgetLabel(task.budgetType, task.budgetAmount)} · ${task.proposalCount} proposals",
        action = MarketMyTaskAction.OPEN_POSTED_DETAIL
    )

    private fun appliedItem(entry: MarketAppliedTask): MarketMyTaskItem {
        val proposal = entry.proposal
        val task = entry.task
        val status = proposal.status.wireValue.replace('_', ' ').replaceFirstChar(Char::uppercase)
        val title = task?.title ?: if (task == null) "Task · ${proposal.taskId.takeLast(6)}" else "Task"
        val detail = if (task == null) {
            val unavailable = "Task details aren’t available · Your proposal: ${priceLabel(proposal.price)}"
            if (proposal.status in listOf(MarketProposalStatus.SENT, MarketProposalStatus.SHORTLISTED))
                "$unavailable · Tap to withdraw" else unavailable
        } else {
            val proposalSummary = "Your price ${priceLabel(proposal.price)} · ${proposal.deliveryDays} day${if (proposal.deliveryDays == 1) "" else "s"} · ${task.category}"
            if (proposal.status in listOf(MarketProposalStatus.SENT, MarketProposalStatus.SHORTLISTED))
                "$proposalSummary · Tap to withdraw"
            else proposalSummary
        }
        return MarketMyTaskItem(
            id = proposal.taskId,
            category = task?.category ?: "Task",
            categoryTone = categoryTone(task?.category.orEmpty()),
            status = status,
            statusTone = proposalStatusTone(proposal.status),
            title = title,
            detailLine = detail,
            action = if (proposal.status == MarketProposalStatus.ACCEPTED)
                if (task?.status == MarketTaskStatus.COMPLETED) MarketMyTaskAction.RATE_TASK
                else MarketMyTaskAction.OPEN_CHAT_PREVIEW
            else if (proposal.status in listOf(MarketProposalStatus.SENT, MarketProposalStatus.SHORTLISTED))
                MarketMyTaskAction.WITHDRAW_PROPOSAL else MarketMyTaskAction.PREVIEW_INFO,
            pricePkr = proposal.price.toInt(),
            helperName = task?.posterName.orEmpty(),
            counterpartUid = task?.posterId,
            conversationId = if (proposal.status == MarketProposalStatus.ACCEPTED) {
                conversations.firstOrNull { it.kind == com.superiorstudent.app.feature.market.data.MarketListingKind.TASK &&
                    it.taskId == proposal.taskId }?.id
            } else null
        )
    }

    private fun categoryTone(category: String): MarketMyTaskTone = when (category.lowercase()) {
        "design", "typing" -> MarketMyTaskTone.PEACH
        "coding", "programming" -> MarketMyTaskTone.MINT
        "notes", "writing" -> MarketMyTaskTone.LAVENDER
        else -> MarketMyTaskTone.SKY
    }

    private fun taskStatusTone(status: MarketTaskStatus): MarketMyTaskTone = when (status) {
        MarketTaskStatus.OPEN -> MarketMyTaskTone.SUCCESS
        MarketTaskStatus.ASSIGNED -> MarketMyTaskTone.LIME
        MarketTaskStatus.COMPLETED -> MarketMyTaskTone.MINT
        MarketTaskStatus.CANCELLED, MarketTaskStatus.EXPIRED -> MarketMyTaskTone.WARNING
    }

    private fun proposalStatusTone(status: MarketProposalStatus): MarketMyTaskTone = when (status) {
        MarketProposalStatus.ACCEPTED -> MarketMyTaskTone.SUCCESS
        MarketProposalStatus.SHORTLISTED -> MarketMyTaskTone.LIME
        MarketProposalStatus.SENT -> MarketMyTaskTone.SKY
        MarketProposalStatus.DECLINED, MarketProposalStatus.WITHDRAWN -> MarketMyTaskTone.WARNING
    }

    private fun budgetLabel(type: MarketBudgetType, amount: Double?): String = when (type) {
        MarketBudgetType.FREE -> "Free"
        MarketBudgetType.NEGOTIABLE -> amount?.let { "From ${priceLabel(it)} · negotiable" } ?: "Negotiable"
        MarketBudgetType.FIXED -> amount?.let(::priceLabel) ?: "Fixed budget"
    }

    private fun priceLabel(value: Double): String = "Rs. " + java.text.NumberFormat
        .getIntegerInstance(java.util.Locale.US).format(value.toLong())

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketMyTasksActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketMyTasksActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@MarketMyTasksActivity,
                if (medium) R.font.poppins_medium else R.font.poppins)
                ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            setTextColor(textColor)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun color(id: Int): Int = getColor(id)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val STATE_TAB = "market.my_tasks.active_tab"
    }
}
