package com.superiorstudent.app.feature.market.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import android.text.InputType
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.feature.market.data.MarketBudgetType
import com.superiorstudent.app.feature.market.data.MarketProposal
import com.superiorstudent.app.feature.market.data.MarketProposalStatus
import com.superiorstudent.app.feature.market.data.MarketRepository
import com.superiorstudent.app.feature.market.data.MarketTask
import com.superiorstudent.app.feature.market.data.MarketTaskEdit
import com.superiorstudent.app.feature.market.data.MarketTaskStatus
import com.superiorstudent.app.ui.AppThemePreferences
import kotlin.math.min

/** Screen 06: live task detail in the poster/owner view. */
class MarketTaskPosterDetailActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private val repository by lazy { MarketRepository() }
    private var task: MarketTask? = null
    private var proposals: List<MarketProposal> = emptyList()
    private var loading = true
    private var loadError: String? = null
    private var resumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply {
            setBackgroundColor(color(R.color.market_bg))
            clipToPadding = false
        }
        window.statusBarColor = color(R.color.market_bg)
        window.navigationBarColor = color(R.color.market_bg)
        setContentView(root)
        renderPage()
        loadTask()
        applyWindowInsets()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
    }

    override fun onResume() {
        super.onResume()
        if (resumedOnce) loadTask() else resumedOnce = true
    }

    private fun renderPage() {
        root.removeAllViews()
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(8)
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(8)
        })

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(24))
        }
        scroll.addView(details, FrameLayout.LayoutParams(-1, -2))

        if (task == null) {
            details.addView(makeText(
                loadError ?: if (loading) "Loading your task…" else "Task unavailable.",
                14f, false, color(R.color.market_ink2)
            ).apply { gravity = Gravity.CENTER; setPadding(dp(12), dp(36), dp(12), dp(36)) },
                LinearLayout.LayoutParams(-1, -2))
            content.addView(makeBackButton(), LinearLayout.LayoutParams(-1, dp(52)).apply {
                marginStart = dp(16); marginEnd = dp(16); bottomMargin = dp(16)
            })
            return
        }

        details.addView(buildCategoryTags())
        details.addView(makeText(task?.title.orEmpty(), 22f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.02f
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        details.addView(buildAuthorRow(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(18)
            bottomMargin = dp(18)
        })
        details.addView(buildMetricCards(), LinearLayout.LayoutParams(-1, -2))

        details.addView(makeText("Details", 16f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        details.addView(makeText(task?.description.orEmpty(), 13f, false,
            color(R.color.market_ink2)).apply { setLineSpacing(dp(4).toFloat(), 1f) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        details.addView(buildSkillTags(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
        })
        details.addView(buildProposalsSummary(), LinearLayout.LayoutParams(-1, dp(84)).apply {
            topMargin = dp(28)
        })

        content.addView(buildFooter(), LinearLayout.LayoutParams(-1, -2))
    }

    private fun loadTask() {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        if (taskId.isBlank()) {
            loading = false
            loadError = "This task link is missing its task ID. Open a task from My Tasks."
            renderPage()
            return
        }
        loading = true
        loadError = null
        repository.loadOwnedTask(taskId).addOnCompleteListener { taskResult ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            if (!taskResult.isSuccessful || taskResult.result == null) {
                loading = false
                task = null
                proposals = emptyList()
                loadError = taskResult.exception?.localizedMessage
                    ?: "This task could not be loaded."
                renderPage()
                return@addOnCompleteListener
            }
            task = taskResult.result
            repository.loadTaskProposals(taskId).addOnCompleteListener { proposalResult ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener
                loading = false
                if (proposalResult.isSuccessful) {
                    proposals = proposalResult.result.orEmpty()
                } else {
                    proposals = emptyList()
                    loadError = proposalResult.exception?.localizedMessage
                        ?: "Task proposals could not be loaded."
                }
                renderPage()
            }
        }
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = FrameLayout(this).apply {
            background = rounded(color(R.color.market_card), 18f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            contentDescription = "Back"
            addView(TaskDetailIconView(this@MarketTaskPosterDetailActivity, TaskDetailIcon.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(48), dp(48)))

        row.addView(makeText("Your task", 22f, true, color(R.color.market_ink)),
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(14) })

        val taskStatus = task?.status
        val status = makeText(taskStatus?.wireValue.orEmpty().replace('_', ' ')
            .replaceFirstChar { it.uppercase() }, 13f, true, statusTextColor()).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = rounded(statusBackgroundColor(), 99f)
        }
        row.addView(status, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(14) })

        val edit = FrameLayout(this).apply {
            background = rounded(color(R.color.market_card), 18f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            contentDescription = "Edit task"
            addView(PosterDetailIconView(this@MarketTaskPosterDetailActivity, PosterDetailIcon.EDIT, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            visibility = if (taskStatus == MarketTaskStatus.OPEN) View.VISIBLE else View.GONE
            setOnClickListener { showEditDescription() }
        }
        row.addView(edit, LinearLayout.LayoutParams(dp(48), dp(48)))
        return row
    }

    private fun buildCategoryTags(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(makeChip(task?.category.orEmpty(), categoryFill(), color(R.color.market_ink)))
        task?.courseTag?.takeIf { it.isNotBlank() }?.let { course ->
            row.addView(makeText(course, 13f, true, color(R.color.market_ink)).apply {
                setPadding(dp(12), dp(5), dp(12), dp(5))
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        }
        return row
    }

    private fun buildAuthorRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val owner = task ?: return row
        row.addView(makeText(owner.posterInitials, 16f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            background = oval(color(R.color.market_sky))
        }, LinearLayout.LayoutParams(dp(48), dp(48)))

        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(makeText(owner.posterName, 15f, true, color(R.color.market_ink)))
        info.addView(makeText("Your Market profile", 13f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        row.addView(makeText(ageLabel(owner.createdAt.toDate().time), 13f, false,
            color(R.color.market_ink2)))
        return row
    }

    private fun buildMetricCards(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val current = task ?: return row
        val metrics = listOf(
            Triple("Budget", budgetLabel(current.budgetType, current.budgetAmount), R.color.market_lav),
            Triple("Deadline", deadlineLabel(current.deadline.toDate().time), R.color.market_peach),
            Triple("Proposals", "${current.proposalCount} of 15", R.color.market_sky)
        )
        metrics.forEachIndexed { index, metric ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(8), dp(12))
                background = rounded(color(metric.third), 22f)
                addView(makeText(metric.first, 12f, false, color(R.color.market_ink2)))
                addView(makeText(metric.second, 15f, true, color(R.color.market_ink)).apply {
                    setLineSpacing(dp(2).toFloat(), 1f)
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            }
            val params = LinearLayout.LayoutParams(0, dp(104), 1f)
            if (index > 0) params.marginStart = dp(9)
            row.addView(card, params)
        }
        return row
    }

    private fun buildSkillTags(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val skills = task?.skills.orEmpty()
        if (skills.isEmpty()) {
            row.addView(makeText("No skills specified", 12f, false, color(R.color.market_ink2)))
        }
        skills.forEachIndexed { index, tag ->
            val label = makeText(tag, 13f, true, color(R.color.market_ink)).apply {
                setPadding(dp(12), dp(6), dp(12), dp(6))
            }
            val params = LinearLayout.LayoutParams(-2, -2)
            if (index > 0) params.marginStart = dp(8)
            row.addView(label, params)
        }
        scroll.addView(row, FrameLayout.LayoutParams(-2, -2))
        return scroll
    }

    private fun buildProposalsSummary(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.market_card), 28f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            setOnClickListener { openProposals() }
        }
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        text.addView(makeText("${task?.proposalCount ?: 0} proposals", 17f, true, color(R.color.market_ink)))
        text.addView(makeText("${proposals.count { it.status == MarketProposalStatus.SHORTLISTED }} shortlisted",
            13f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        card.addView(text, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        card.addView(PosterDetailIconView(this, PosterDetailIcon.CHEVRON, color(R.color.market_ink3)),
            LinearLayout.LayoutParams(dp(20), dp(20)))
        return card
    }

    private fun openProposals() {
        val current = task ?: return
        startActivity(Intent(this, MarketProposalsActivity::class.java).apply {
            putExtra(MarketProposalsActivity.EXTRA_TASK_ID, current.id)
            putExtra(MarketProposalsActivity.EXTRA_TASK_LABEL, current.title)
        })
    }

    private fun buildFooter(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(color(R.color.market_bg))
        }
        val current = task ?: return row
        val actionLabel = when (current.status) {
            MarketTaskStatus.ASSIGNED -> "Mark complete"
            MarketTaskStatus.COMPLETED -> if (current.assignedTo.isNullOrBlank()) "Completed" else "Rate helper"
            else -> "Cancel task"
        }
        val actionIcon = if (current.status == MarketTaskStatus.ASSIGNED)
            PosterDetailIcon.CHECK else PosterDetailIcon.CLOSE
        val cancel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(color(R.color.market_card), 28f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            addView(PosterDetailIconView(this@MarketTaskPosterDetailActivity, actionIcon, color(R.color.market_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
            addView(makeText(actionLabel, 15f, true, color(R.color.market_ink)))
        }
        cancel.isEnabled = current.status == MarketTaskStatus.OPEN || current.status == MarketTaskStatus.ASSIGNED ||
            (current.status == MarketTaskStatus.COMPLETED && !current.assignedTo.isNullOrBlank())
        cancel.alpha = if (cancel.isEnabled) 1f else 0.55f
        cancel.setOnClickListener {
            if (current.status == MarketTaskStatus.ASSIGNED) {
                confirmCompleteTask()
                return@setOnClickListener
            }
            if (current.status == MarketTaskStatus.COMPLETED) {
                openRating(current)
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Cancel task?")
                .setMessage("This will close the task and stop accepting proposals.")
                .setNegativeButton("Keep open", null)
                .setPositiveButton("Cancel task") { _, _ ->
                    repository.cancelTask(current.id).addOnCompleteListener { result ->
                        if (result.isSuccessful) {
                            toast("Task cancelled.")
                            loadTask()
                        } else toast(result.exception?.localizedMessage ?: "Task could not be cancelled.")
                    }
                }
                .show()
        }
        row.addView(cancel, LinearLayout.LayoutParams(0, dp(60), 0.9f))

        val viewProposals = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(color(R.color.market_match), 28f)
            isClickable = true
            isFocusable = true
            addView(PosterDetailIconView(this@MarketTaskPosterDetailActivity, PosterDetailIcon.USERS, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(8) })
            addView(makeText("View proposals", 15f, true, color(R.color.market_match_ink)))
        }
        viewProposals.isEnabled = current.proposalCount > 0
        viewProposals.alpha = if (viewProposals.isEnabled) 1f else 0.6f
        viewProposals.setOnClickListener { openProposals() }
        row.addView(viewProposals, LinearLayout.LayoutParams(0, dp(60), 1.35f).apply {
            marginStart = dp(10)
        })
        return row
    }

    private fun showEditDescription() {
        val current = task ?: return
        val input = EditText(this).apply {
            setText(current.description)
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        AlertDialog.Builder(this)
            .setTitle("Edit task details")
            .setMessage("Task details can be updated while the task is open.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val updatedDescription = input.text.toString().trim()
                if (updatedDescription.isBlank()) {
                    toast("Add task details before saving.")
                    return@setPositiveButton
                }
                repository.editTask(current.id, MarketTaskEdit(description = updatedDescription))
                    .addOnCompleteListener { result ->
                        if (result.isSuccessful) {
                            toast("Task details updated.")
                            loadTask()
                        } else toast(result.exception?.localizedMessage
                            ?: "Task details could not be updated.")
                    }
            }
            .show()
    }

    private fun confirmCompleteTask() {
        val current = task ?: return
        AlertDialog.Builder(this)
            .setTitle("Mark task complete?")
            .setMessage("Confirm that the assigned task has been completed.")
            .setNegativeButton("Not yet", null)
            .setPositiveButton("Complete task") { _, _ ->
                repository.completeTask(current.id).addOnCompleteListener { result ->
                    if (result.isSuccessful) {
                        toast("Task marked complete.")
                        loadTask()
                    } else toast(result.exception?.localizedMessage
                        ?: "Task could not be completed.")
                }
            }
            .show()
    }

    private fun openRating(current: MarketTask) {
        val helperUid = current.assignedTo ?: return
        try {
            repository.loadMyTaskRating(current.id).addOnCompleteListener { ratingResult ->
                if (isFinishing || isDestroyed) return@addOnCompleteListener
                if (!ratingResult.isSuccessful) {
                    toast(ratingResult.exception?.localizedMessage ?: "Your rating status could not be checked.")
                    return@addOnCompleteListener
                }
                if (ratingResult.result != null) {
                    toast("You have already rated this completed task.")
                    return@addOnCompleteListener
                }
                val helperName = proposals.firstOrNull {
                    it.proposerId == helperUid && it.status == MarketProposalStatus.ACCEPTED
                }?.proposerName ?: "Student"
                startActivity(Intent(this, MarketTaskRatingActivity::class.java).apply {
                    putExtra(MarketTaskRatingActivity.EXTRA_TASK_ID, current.id)
                    putExtra(MarketTaskRatingActivity.EXTRA_TARGET_UID, helperUid)
                    putExtra(MarketTaskRatingActivity.EXTRA_HELPER_NAME, helperName)
                    putExtra(MarketTaskRatingActivity.EXTRA_TASK_TITLE, current.title)
                })
            }
        } catch (error: Exception) {
            toast(error.localizedMessage ?: "Rating is unavailable.")
        }
    }

    private fun makeBackButton(): View = makeText("Back to My Tasks", 14f, true,
        color(R.color.market_ink)).apply {
        gravity = Gravity.CENTER
        background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
        isClickable = true
        isFocusable = true
        setOnClickListener { finish() }
    }

    private fun budgetLabel(type: MarketBudgetType, amount: Double?): String {
        val amountLabel = amount?.let { "Rs. " + java.text.NumberFormat
            .getIntegerInstance(java.util.Locale.US).format(it.toLong()) }
        return when (type) {
            MarketBudgetType.FREE -> "Free"
            MarketBudgetType.NEGOTIABLE -> amountLabel?.let { "$it · negotiable" } ?: "Negotiable"
            MarketBudgetType.FIXED -> amountLabel ?: "Fixed"
        }
    }

    private fun deadlineLabel(deadlineMillis: Long): String {
        val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
            .format(java.util.Date(deadlineMillis))
        val daysLeft = ((deadlineMillis - System.currentTimeMillis()) / 86_400_000L).toInt()
        val remaining = when {
            daysLeft < 0 -> "Past deadline"
            daysLeft == 0 -> "Due today"
            daysLeft == 1 -> "1 day left"
            else -> "$daysLeft days left"
        }
        return "$date\n$remaining"
    }

    private fun ageLabel(createdAtMillis: Long): String {
        val ageMillis = (System.currentTimeMillis() - createdAtMillis).coerceAtLeast(0L)
        val minutes = ageMillis / 60_000L
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 1_440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1_440}d ago"
        }
    }

    private fun categoryFill(): Int = when (task?.category?.lowercase()) {
        "design", "typing" -> color(R.color.market_peach)
        "coding", "programming" -> color(R.color.market_mint)
        "notes", "writing" -> color(R.color.market_lav)
        else -> color(R.color.market_sky)
    }

    private fun makeChip(label: String, fill: Int, text: Int): TextView =
        makeText(label, 13f, true, text).apply {
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = rounded(fill, 99f)
        }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@MarketTaskPosterDetailActivity,
                if (medium) R.font.poppins_medium else R.font.poppins)
                ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            setTextColor(textColor)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketTaskPosterDetailActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketTaskPosterDetailActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun rounded(fill: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun oval(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

    private fun statusBackgroundColor(): Int =
        if (AppThemePreferences.isNight(this)) 0xFF3B301D.toInt() else 0xFFFFEFC9.toInt()
    private fun statusTextColor(): Int =
        if (AppThemePreferences.isNight(this)) 0xFFFFC857.toInt() else 0xFF8B5A00.toInt()
    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_TASK_ID = "market.poster_detail.task_id"
    }
}
