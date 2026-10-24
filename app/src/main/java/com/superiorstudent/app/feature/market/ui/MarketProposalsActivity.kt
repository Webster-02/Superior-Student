package com.superiorstudent.app.feature.market.ui

import android.app.Dialog
import android.content.Intent
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.superiorstudent.app.R
import com.superiorstudent.app.feature.market.data.MarketProposal
import com.superiorstudent.app.feature.market.data.MarketProposalStatus
import com.superiorstudent.app.feature.market.data.MarketRepository
import com.superiorstudent.app.feature.market.data.MarketUserStats
import com.superiorstudent.app.ui.AppThemePreferences

/** Screen 12: the task poster's live proposal list and decisions. */
class MarketProposalsActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var proposalList: LinearLayout
    private var headerSummary: TextView? = null
    private val filterChips = mutableMapOf<MarketProposalsFilter, TextView>()
    private var activeFilter = MarketProposalsFilter.ALL
    private val repository by lazy { MarketRepository() }
    private var proposals: List<MarketProposalPreview> = emptyList()
    private var loading = true
    private var loadError: String? = null
    private var loadGeneration = 0
    private val pendingProposalIds = mutableSetOf<String>()
    private val taskId by lazy { intent.getStringExtra(EXTRA_TASK_ID).orEmpty() }

    private val taskLabel by lazy {
        intent.getStringExtra(EXTRA_TASK_LABEL).orEmpty().ifBlank { "Task proposals" }
    }

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

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        content.addView(buildHeader(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
            marginStart = dp(16)
            marginEnd = dp(16)
            bottomMargin = dp(6)
        })
        content.addView(buildFilterBar(), LinearLayout.LayoutParams(-1, dp(42)).apply {
            topMargin = dp(6)
        })

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(12)
        })
        proposalList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(24))
        }
        scroll.addView(proposalList, FrameLayout.LayoutParams(-1, -2))

        renderProposals()
        loadProposals()
        applyWindowInsets()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
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
            addView(MarketProposalsIconView(this@MarketProposalsActivity,
                MarketProposalsIcon.BACK, color(R.color.market_ink)),
                FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { finish() }
        }
        row.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))

        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headings.addView(makeText("Proposals", 20f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.02f
        })
        headerSummary = makeText(
            MarketProposalsUiPolicy.capacityLabel(proposals.size) + " · " + taskLabel,
            12f, false, color(R.color.market_ink2)
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        headings.addView(headerSummary)
        row.addView(headings, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun buildFilterBar(): View {
        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        val filters = listOf(
            MarketProposalsFilter.ALL to "All ${proposals.size}",
            MarketProposalsFilter.SHORTLISTED to MarketProposalsUiPolicy.shortlistedLabel(proposals),
            MarketProposalsFilter.CHEAPEST to "Cheapest",
            MarketProposalsFilter.TOP_RATED to "Top rated"
        )
        filters.forEachIndexed { index, (filter, label) ->
            val chip = makeText(label, 12.5f, true, color(R.color.market_ink)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(8), dp(14), dp(8))
                minHeight = dp(38)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    activeFilter = filter
                    renderProposals()
                }
            }
            filterChips[filter] = chip
            row.addView(chip, LinearLayout.LayoutParams(-2, dp(38)).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
        horizontal.addView(row, FrameLayout.LayoutParams(-2, -2))
        return horizontal
    }

    private fun renderProposals() {
        if (!::proposalList.isInitialized) return
        filterChips[MarketProposalsFilter.ALL]?.text = "All ${proposals.size}"
        headerSummary?.text = MarketProposalsUiPolicy.capacityLabel(proposals.size) + " · " + taskLabel
        filterChips[MarketProposalsFilter.SHORTLISTED]?.text =
            MarketProposalsUiPolicy.shortlistedLabel(proposals)
        filterChips.forEach { (filter, chip) ->
            val selected = activeFilter == filter
            chip.background = if (selected) {
                rounded(color(R.color.market_segment), 99f)
            } else {
                rounded(color(R.color.market_card), 99f, color(R.color.market_line))
            }
            chip.setTextColor(color(if (selected) R.color.market_segment_ink else R.color.market_ink))
        }

        proposalList.removeAllViews()
        if (loading) {
            proposalList.addView(makeText("Loading proposals…", 13f, false,
                color(R.color.market_ink2)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(24), dp(12), dp(24))
            })
            return
        }
        loadError?.let { message ->
            proposalList.addView(makeText(message + "  Tap to retry.", 13f, false,
                color(R.color.market_ink2)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(24), dp(12), dp(24))
                isClickable = true
                isFocusable = true
                setOnClickListener { loadProposals() }
            })
            return
        }
        val shown = MarketProposalsUiPolicy.filterAndSort(proposals, activeFilter)
        if (shown.isEmpty()) {
            proposalList.addView(makeText("No proposals in this filter yet.", 13f, false,
                color(R.color.market_ink2)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(24), dp(12), dp(24))
            })
            return
        }
        shown.forEachIndexed { index, proposal ->
            proposalList.addView(buildProposalCard(proposal), LinearLayout.LayoutParams(-1, -2).apply {
                if (index > 0) topMargin = dp(10)
            })
        }
    }

    private fun buildProposalCard(proposal: MarketProposalPreview): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(color(R.color.market_card), 22f, color(R.color.market_line))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = FrameLayout(this).apply {
            background = oval(color(avatarColor(proposal.avatarTone)))
            addView(makeText(proposal.initials, 14f, true, color(R.color.market_ink)).apply {
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
        }
        top.addView(avatar, LinearLayout.LayoutParams(dp(42), dp(42)))

        val identity = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        identity.addView(makeText(proposal.name, 14f, true, color(R.color.market_ink)).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val ratingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        ratingRow.addView(MarketProposalsIconView(this, MarketProposalsIcon.STAR, 0xFFE8A100.toInt()),
            LinearLayout.LayoutParams(dp(14), dp(14)))
        ratingRow.addView(makeText(proposal.ratingLabel + " · " + proposal.ageLabel,
            12f, false, color(R.color.market_ink2)),
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        identity.addView(ratingRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(2)
        })
        top.addView(identity, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(10)
            marginEnd = dp(8)
        })

        val offer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        offer.addView(makeText(MarketProposalsUiPolicy.priceLabel(proposal.pricePkr),
            14f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.END
        })
        if (proposal.status != MarketProposalStatus.SENT) {
            val statusLabel = proposal.status.wireValue.replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
            offer.addView(makeText(statusLabel, 11f, true,
                color(if (AppThemePreferences.isNight(this@MarketProposalsActivity))
                    R.color.ok else R.color.ok)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = rounded(color(R.color.market_mint), 99f)
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        }
        top.addView(offer, LinearLayout.LayoutParams(-2, -2))
        card.addView(top)

        card.addView(makeText(proposal.message, 12.5f, false, color(R.color.market_ink)).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        if (proposal.status == MarketProposalStatus.SENT ||
            proposal.status == MarketProposalStatus.SHORTLISTED) {
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val busy = proposal.id in pendingProposalIds
            actions.addView(buildActionButton("Decline", ActionStyle.OUTLINE) {
                updateProposalStatus(proposal, MarketProposalStatus.DECLINED)
            }.apply { isEnabled = !busy; alpha = if (busy) 0.5f else 1f },
                LinearLayout.LayoutParams(0, dp(38), 1f))
            actions.addView(buildActionButton(if (proposal.shortlisted) "Shortlisted" else "Shortlist",
                ActionStyle.SECONDARY) {
                if (!proposal.shortlisted) updateProposalStatus(proposal, MarketProposalStatus.SHORTLISTED)
            }.apply {
                isEnabled = !busy && !proposal.shortlisted
                alpha = if (isEnabled) 1f else 0.6f
            },
                LinearLayout.LayoutParams(0, dp(38), 1.2f).apply { marginStart = dp(8) })
            actions.addView(buildActionButton("Accept", ActionStyle.ACCEPT) {
                showAcceptConfirmation(proposal)
            }.apply { isEnabled = !busy; alpha = if (busy) 0.5f else 1f },
                LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(8) })
            card.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        return card
    }

    private enum class ActionStyle { OUTLINE, SECONDARY, ACCEPT }

    private fun buildActionButton(label: String, style: ActionStyle, action: () -> Unit): TextView =
        makeText(label, 12.5f, true, when (style) {
            ActionStyle.SECONDARY -> color(R.color.market_message_ink)
            ActionStyle.ACCEPT -> color(R.color.market_match_ink)
            ActionStyle.OUTLINE -> color(R.color.market_ink)
        }).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            setPadding(dp(2), 0, dp(2), 0)
            background = when (style) {
                ActionStyle.OUTLINE -> rounded(color(R.color.market_card), 12f, color(R.color.market_line))
                ActionStyle.SECONDARY -> rounded(color(R.color.market_message_bg), 12f)
                ActionStyle.ACCEPT -> rounded(color(R.color.market_match), 12f)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    /**
     * Reference screen 13: confirmation sheet opened from the proposal's Accept button.
     * The confirmation is intentionally preview-only until real Market acceptance is approved.
     */
    private fun showAcceptConfirmation(proposal: MarketProposalPreview) {
        val sheetDialog = Dialog(this)
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(20))
            background = rounded(color(R.color.market_bg), 28f)
        }

        sheet.addView(View(this).apply {
            background = rounded(color(R.color.market_line), 99f)
        }, LinearLayout.LayoutParams(dp(40), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(14)
        })

        sheet.addView(makeText("Accept ${proposal.name}'s proposal?", 18f, true,
            color(R.color.market_ink)), LinearLayout.LayoutParams(-1, -2))

        val priceAndDelivery = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.market_card), 22f, color(R.color.market_line))
        }
        priceAndDelivery.addView(acceptanceMetric("Price",
            MarketProposalsUiPolicy.priceLabel(proposal.pricePkr)),
            LinearLayout.LayoutParams(0, -2, 1f))
        priceAndDelivery.addView(acceptanceMetric("Delivery",
            "${proposal.deliveryDays} day${if (proposal.deliveryDays == 1) "" else "s"}"),
            LinearLayout.LayoutParams(0, -2, 1f))
        sheet.addView(priceAndDelivery, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        })

        addAcceptanceChecklistRow(sheet,
            "A chat with ${proposal.name} opens, with this task pinned")
        MarketProposalsUiPolicy.ACCEPTANCE_CHECKLIST.drop(1).forEach { copy ->
            addAcceptanceChecklistRow(sheet, copy)
        }

        val safetyNote = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.market_peach), 18f)
        }
        safetyNote.addView(AcceptanceIconView(this, AcceptanceIconKind.SHIELD,
            color(R.color.market_ink)), LinearLayout.LayoutParams(dp(20), dp(20)))
        safetyNote.addView(makeText(MarketProposalsUiPolicy.ACCEPTANCE_SAFETY_NOTE,
            12f, false, color(R.color.market_ink)).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        sheet.addView(safetyNote, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
            bottomMargin = dp(14)
        })

        val acceptAndChat = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            background = rounded(color(R.color.market_match), 16f)
            isClickable = true
            isFocusable = true
            contentDescription = "Accept and open chat"
            addView(AcceptanceIconView(this@MarketProposalsActivity,
                AcceptanceIconKind.CHAT, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(makeText("Accept proposal", 15f, true,
                color(R.color.market_match_ink)), LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = dp(8)
            })
            setOnClickListener {
                sheetDialog.dismiss()
                acceptProposal(proposal)
            }
        }
        sheet.addView(acceptAndChat, LinearLayout.LayoutParams(-1, dp(52)))

        val notNow = makeText("Not now", 15f, true, color(R.color.market_ink)).apply {
            gravity = Gravity.CENTER
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            setOnClickListener { sheetDialog.dismiss() }
        }
        sheet.addView(notNow, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(8)
        })

        sheetDialog.setContentView(sheet)
        sheetDialog.setCancelable(true)
        sheetDialog.setCanceledOnTouchOutside(true)
        sheetDialog.show()
        sheetDialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.45f)
            setGravity(Gravity.BOTTOM)
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun acceptanceMetric(label: String, value: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(makeText(label, 12.5f, false, color(R.color.market_ink2)))
            addView(makeText(value, 15f, true, color(R.color.market_ink)),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }

    private fun addAcceptanceChecklistRow(parent: LinearLayout, copy: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        row.addView(AcceptanceIconView(this, AcceptanceIconKind.CHECK, color(R.color.ok)),
            LinearLayout.LayoutParams(dp(18), dp(18)).apply { topMargin = dp(2) })
        row.addView(makeText(copy, 13f, false, color(R.color.market_ink)).apply {
            setLineSpacing(dp(1).toFloat(), 1f)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })
    }

    private enum class AcceptanceIconKind { CHECK, CHAT, SHIELD }

    private class AcceptanceIconView(
        context: Context,
        private val kind: AcceptanceIconKind,
        tint: Int
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val scale = minOf(width, height) / 24f
            if (scale <= 0f) return
            val saved = canvas.save()
            canvas.translate((width - 24f * scale) / 2f, (height - 24f * scale) / 2f)
            canvas.scale(scale, scale)
            val path = Path()
            when (kind) {
                AcceptanceIconKind.CHECK -> {
                    path.moveTo(4f, 12f)
                    path.lineTo(9.5f, 17f)
                    path.lineTo(20f, 6.5f)
                    canvas.drawPath(path, paint)
                }
                AcceptanceIconKind.CHAT -> {
                    path.moveTo(20.5f, 11f)
                    path.cubicTo(20.5f, 15.5f, 16.8f, 19f, 12f, 19f)
                    path.cubicTo(10.8f, 19f, 9.6f, 18.8f, 8.5f, 18.3f)
                    path.lineTo(3.8f, 20f)
                    path.lineTo(5.1f, 15.9f)
                    path.cubicTo(3.8f, 14.5f, 3f, 12.8f, 3f, 11f)
                    path.cubicTo(3f, 6.5f, 6.8f, 3f, 12f, 3f)
                    path.cubicTo(16.8f, 3f, 20.5f, 6.5f, 20.5f, 11f)
                    canvas.drawPath(path, paint)
                }
                AcceptanceIconKind.SHIELD -> {
                    path.moveTo(12f, 3f)
                    path.cubicTo(14.2f, 4.8f, 17.1f, 5.8f, 20f, 6f)
                    path.cubicTo(19.8f, 13.5f, 17f, 18.8f, 12f, 21f)
                    path.cubicTo(7f, 18.8f, 4.2f, 13.5f, 4f, 6f)
                    path.cubicTo(6.9f, 5.8f, 9.8f, 4.8f, 12f, 3f)
                    path.close()
                    canvas.drawPath(path, paint)
                    canvas.drawCircle(12f, 11f, 1.4f, paint)
                    canvas.drawLine(12f, 12.5f, 12f, 15f, paint)
                }
            }
            canvas.restoreToCount(saved)
        }
    }

    private fun loadProposals() {
        if (taskId.isBlank()) {
            loading = false
            loadError = "This proposal page is missing its task ID. Open it from your task details."
            renderProposals()
            return
        }
        loading = true
        loadError = null
        renderProposals()
        val generation = ++loadGeneration
        val load = try {
            repository.loadTaskProposals(taskId)
        } catch (error: Exception) {
            loading = false
            loadError = error.localizedMessage ?: "Proposals could not be loaded."
            renderProposals()
            return
        }
        load.addOnCompleteListener { result ->
            if (isFinishing || isDestroyed || generation != loadGeneration) return@addOnCompleteListener
            loading = false
            if (!result.isSuccessful) {
                loadError = result.exception?.localizedMessage ?: "Proposals could not be loaded."
                proposals = emptyList()
                renderProposals()
                return@addOnCompleteListener
            }
            loadError = null
            proposals = result.result.orEmpty().map(::toPreview)
            renderProposals()
            loadProposalRatings(generation)
        }
    }

    private fun toPreview(proposal: MarketProposal) = MarketProposalPreview(
        id = proposal.proposerId,
        name = proposal.proposerName,
        initials = proposal.proposerInitials,
        ratingLabel = "Loading rating…",
        ratingScore = 0.0,
        ageLabel = ageLabel(proposal.createdAt.toDate().time),
        pricePkr = proposal.price.toInt(),
        message = proposal.message,
        avatarTone = when (Math.floorMod(proposal.proposerId.hashCode(), 3)) {
            0 -> "sky"
            1 -> "peach"
            else -> "lav"
        },
        shortlisted = proposal.status == MarketProposalStatus.SHORTLISTED,
        status = proposal.status,
        deliveryDays = proposal.deliveryDays
    )

    private fun loadProposalRatings(generation: Int) {
        proposals.map { it.id }.distinct().forEach { uid ->
            try {
                repository.loadPublicUserStats(uid).addOnCompleteListener { result ->
                    if (isFinishing || isDestroyed || generation != loadGeneration) return@addOnCompleteListener
                    val labelAndScore = if (!result.isSuccessful) {
                        "Rating unavailable" to 0.0
                    } else {
                        result.result?.let(::ratingLabelAndScore) ?: ("No ratings" to 0.0)
                    }
                    proposals = proposals.map { proposal ->
                        if (proposal.id == uid) proposal.copy(
                            ratingLabel = labelAndScore.first,
                            ratingScore = labelAndScore.second
                        ) else proposal
                    }
                    renderProposals()
                }
            } catch (_: Exception) {
                proposals = proposals.map { proposal ->
                    if (proposal.id == uid) proposal.copy(ratingLabel = "Rating unavailable") else proposal
                }
            }
        }
    }

    private fun ratingLabelAndScore(stats: MarketUserStats): Pair<String, Double> {
        if (stats.ratingCount <= 0) return "No ratings" to 0.0
        val rating = stats.ratingSum / stats.ratingCount
        return "%.1f · %d rating%s".format(
            java.util.Locale.US,
            rating,
            stats.ratingCount,
            if (stats.ratingCount == 1) "" else "s"
        ) to rating
    }

    private fun updateProposalStatus(
        proposal: MarketProposalPreview,
        newStatus: MarketProposalStatus
    ) {
        if (newStatus == MarketProposalStatus.SENT) {
            toast("A shortlist can’t be removed after saving it.")
            return
        }
        if (!pendingProposalIds.add(proposal.id)) return
        renderProposals()
        val update = try {
            repository.setProposalStatus(taskId, proposal.id, newStatus)
        } catch (error: Exception) {
            pendingProposalIds.remove(proposal.id)
            toast(error.localizedMessage ?: "The proposal could not be updated.")
            renderProposals()
            return
        }
        update.addOnCompleteListener { result ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            pendingProposalIds.remove(proposal.id)
            if (result.isSuccessful) {
                toast(if (newStatus == MarketProposalStatus.SHORTLISTED)
                    "Proposal shortlisted." else "Proposal declined.")
                loadProposals()
            } else {
                toast(result.exception?.localizedMessage ?: "The proposal could not be updated.")
                renderProposals()
            }
        }
    }

    private fun acceptProposal(proposal: MarketProposalPreview) {
        if (!pendingProposalIds.add(proposal.id)) return
        renderProposals()
        val accept = try {
            repository.acceptProposal(taskId, proposal.id)
        } catch (error: Exception) {
            pendingProposalIds.remove(proposal.id)
            toast(error.localizedMessage ?: "The proposal could not be accepted.")
            renderProposals()
            return
        }
        accept.addOnCompleteListener { result ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener
            pendingProposalIds.remove(proposal.id)
            if (result.isSuccessful) {
                val conversationId = result.result.orEmpty()
                if (conversationId.isBlank()) {
                    toast("Proposal accepted, but the conversation ID was missing.")
                    finish()
                    return@addOnCompleteListener
                }
                startActivity(Intent(this, MarketTaskChatActivity::class.java).apply {
                    putExtra(MarketTaskChatActivity.EXTRA_CONVERSATION_ID, conversationId)
                    putExtra(MarketTaskChatActivity.EXTRA_HELPER_NAME, proposal.name)
                    putExtra(MarketTaskChatActivity.EXTRA_TASK_TITLE, taskLabel)
                    putExtra(MarketTaskChatActivity.EXTRA_PRICE_PKR, proposal.pricePkr)
                })
                finish()
            } else {
                toast(result.exception?.localizedMessage ?: "The proposal could not be accepted.")
                renderProposals()
            }
        }
    }

    private fun ageLabel(createdAtMillis: Long): String {
        val minutes = ((System.currentTimeMillis() - createdAtMillis).coerceAtLeast(0L)) / 60_000L
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 1_440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1_440}d ago"
        }
    }

    private fun showActionInfo(proposal: MarketProposalPreview, title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(proposal.name + "\n\n" + message)
            .setPositiveButton("Got it", null)
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun avatarColor(tone: String): Int = when (tone) {
        "peach" -> R.color.market_peach
        "lav" -> R.color.market_lav
        else -> R.color.market_sky
    }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketProposalsActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketProposalsActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@MarketProposalsActivity,
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

    private fun oval(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

    private fun color(id: Int) = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_TASK_LABEL = "market.proposals.task_label"
    }
}
