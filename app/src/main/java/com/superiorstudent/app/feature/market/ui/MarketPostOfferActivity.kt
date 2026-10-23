package com.superiorstudent.app.feature.market.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.superiorstudent.app.R
import com.superiorstudent.app.feature.market.data.MarketErrorMapper
import com.superiorstudent.app.feature.market.data.MarketOfferDraft
import com.superiorstudent.app.feature.market.data.MarketOfferEdit
import com.superiorstudent.app.feature.market.data.MarketPriceType
import com.superiorstudent.app.feature.market.data.MarketRepository
import com.superiorstudent.app.ui.AppThemePreferences
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max

/** Screen 18: creates and edits the student's Market offers. */
class MarketPostOfferActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var titleInput: EditText
    private lateinit var amountInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var descriptionCount: TextView
    private lateinit var amountSection: LinearLayout
    private lateinit var amountField: LinearLayout
    private lateinit var amountPrefix: TextView
    private lateinit var categoryChips: Map<String, TextView>
    private lateinit var priceModeChips: Map<MarketPostOfferPriceMode, TextView>
    private lateinit var photoTitle: TextView
    private lateinit var photoSubtitle: TextView
    private lateinit var photoIcon: View
    private lateinit var photoThumb: ImageView
    private lateinit var submitButton: LinearLayout
    private lateinit var submitLabel: TextView
    private var submittingOffer = false

    private var selectedCategory = MarketPostOfferUiPolicy.REFERENCE_CATEGORY
    private var selectedPriceMode = MarketPostOfferPriceMode.RUPEES
    private var compressedCover: ByteArray? = null

    private val editMode: Boolean
        get() = intent.getBooleanExtra(EXTRA_EDIT_MODE, false)

    private val coverPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handleCoverSelected(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemePreferences.applyToApp(this)
        super.onCreate(savedInstanceState)
        selectedCategory = intent.getStringExtra(EXTRA_CATEGORY)
            ?.takeIf { it in MarketPostOfferUiPolicy.categories }
            ?: MarketPostOfferUiPolicy.REFERENCE_CATEGORY
        selectedPriceMode = intent.getStringExtra(EXTRA_PRICE_MODE)
            ?.let { runCatching { MarketPostOfferPriceMode.valueOf(it) }.getOrNull() }
            ?: MarketPostOfferPriceMode.RUPEES

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

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        scroll.addView(form, FrameLayout.LayoutParams(-1, -2))

        form.addView(makeLabel("Title"), fieldParams(bottom = 6))
        titleInput = makeInput(
            intent.getStringExtra(EXTRA_TITLE).orEmpty(), singleLine = true,
            maxLength = MarketPostOfferUiPolicy.MAX_TITLE_LENGTH, inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ).apply {
            textSize = 16f
            hint = "Enter an offer title"
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }
        form.addView(titleInput, LinearLayout.LayoutParams(-1, dp(56)).apply {
            bottomMargin = dp(14)
        })

        form.addView(makeLabel("Category"), fieldParams(bottom = 6))
        val categoryHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val categories = linkedMapOf<String, TextView>()
        addCategoryRow(categoryHolder, listOf("Notes", "Tutoring", "Design", "Coding"), categories)
        addCategoryRow(categoryHolder, listOf("Typing", "Books"), categories, topMargin = 8)
        categoryChips = categories
        form.addView(categoryHolder, fieldParams(bottom = 14))

        form.addView(makeLabel("Price"), fieldParams(bottom = 6))
        val priceHolder = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = rounded(color(R.color.market_card), 20f, color(R.color.market_line))
        }
        val modeMap = linkedMapOf<MarketPostOfferPriceMode, TextView>()
        MarketPostOfferUiPolicy.priceModes.forEachIndexed { index, mode ->
            val chip = makeText(mode.label, 14f, true, color(R.color.market_ink2)).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedPriceMode = mode
                    updatePriceModes()
                }
            }
            priceHolder.addView(chip, LinearLayout.LayoutParams(0, dp(44), 1f))
            modeMap[mode] = chip
        }
        priceModeChips = modeMap
        form.addView(priceHolder, fieldParams(bottom = 12))
        
        amountSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        amountSection.addView(makeLabel("Amount"), fieldParams(bottom = 6))
        amountField = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
        }
        amountPrefix = makeText("Rs.", 16f, false, color(R.color.market_ink))
        amountField.addView(amountPrefix, LinearLayout.LayoutParams(-2, -2).apply {
            marginEnd = dp(8)
        })
        amountInput = makeInput(
            intent.getStringExtra(EXTRA_AMOUNT).orEmpty(), singleLine = true, maxLength = 8,
            inputType = android.text.InputType.TYPE_CLASS_NUMBER, transparentBackground = true
        ).apply {
            textSize = 16f
            imeOptions = EditorInfo.IME_ACTION_NEXT
            setPadding(0, 0, 0, 0)
            hint = "Enter price"
        }
        amountField.addView(amountInput, LinearLayout.LayoutParams(0, dp(56), 1f))
        amountSection.addView(amountField, LinearLayout.LayoutParams(-1, dp(56)))
        form.addView(amountSection, fieldParams(bottom = 14))

        val descriptionHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        descriptionHeader.addView(makeLabel("Description"), LinearLayout.LayoutParams(0, -2, 1f))
        descriptionCount = makeText(
            MarketPostOfferUiPolicy.descriptionCounter(""),
            12f, false, color(R.color.market_ink2)
        )
        descriptionHeader.addView(descriptionCount)
        form.addView(descriptionHeader, fieldParams(bottom = 6))

        descriptionInput = makeInput(
            intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty(), singleLine = false,
            maxLength = MarketPostOfferUiPolicy.MAX_DESCRIPTION_LENGTH,
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ).apply {
            textSize = 14f
            gravity = Gravity.TOP
            minHeight = dp(118)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setLineSpacing(dp(3).toFloat(), 1f)
            imeOptions = EditorInfo.IME_ACTION_NONE
            hint = "Describe what you are offering"
            movementMethod = ScrollingMovementMethod()
        }
        form.addView(descriptionInput, LinearLayout.LayoutParams(-1, dp(124)).apply {
            bottomMargin = dp(14)
        })
        descriptionInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                descriptionCount.text = MarketPostOfferUiPolicy.descriptionCounter(s?.toString().orEmpty())
                descriptionCount.setTextColor(color(R.color.market_ink2))
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        form.addView(buildPhotoPicker(), LinearLayout.LayoutParams(-1, -2))
        if (editMode) showExistingCover()
        form.addView(View(this), LinearLayout.LayoutParams(1, dp(4)))

        content.addView(buildFooter(), LinearLayout.LayoutParams(-1, -2))
        updateCategoryChips()
        updatePriceModes()
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
        row.addView(iconButton(MarketPostOfferIcon.BACK, "Back") { finish() },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        val headings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headings.addView(makeText(if (editMode) "Edit offer" else "Post an offer",
            20f, true, color(R.color.market_ink)).apply {
            letterSpacing = -0.02f
        })
        headings.addView(makeText(
            if (editMode) "Update your offer details" else "What can you help with?",
            13f, false, color(R.color.market_ink2)))
        row.addView(headings, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        return row
    }

    private fun addCategoryRow(
        parent: LinearLayout,
        labels: List<String>,
        chips: MutableMap<String, TextView>,
        topMargin: Int = 0
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labels.forEachIndexed { index, label ->
            val chip = makeText(label, 13f, true, color(R.color.market_ink)).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(9), dp(14), dp(9))
                background = rounded(color(R.color.market_card), 99f, color(R.color.market_line))
                isClickable = true
                isFocusable = true
                contentDescription = "Category $label"
                setOnClickListener {
                    selectedCategory = label
                    updateCategoryChips()
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(-2, dp(42)).apply {
                if (index > 0) marginStart = dp(8)
            })
            chips[label] = chip
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(42)).apply {
            if (topMargin > 0) this.topMargin = dp(topMargin)
        })
    }

    private fun updateCategoryChips() {
        if (!::categoryChips.isInitialized) return
        categoryChips.forEach { (category, chip) ->
            val active = category == selectedCategory
            chip.background = rounded(
                color(if (active) R.color.market_segment else R.color.market_card),
                99f,
                if (active) null else color(R.color.market_line)
            )
            chip.setTextColor(color(if (active) R.color.market_segment_ink else R.color.market_ink))
        }
    }

    private fun updatePriceModes() {
        if (!::priceModeChips.isInitialized) return
        priceModeChips.forEach { (mode, chip) ->
            val active = mode == selectedPriceMode
            chip.background = rounded(
                color(if (active) R.color.market_segment else android.R.color.transparent),
                15f
            )
            chip.setTextColor(color(if (active) R.color.market_segment_ink else R.color.market_ink2))
        }
        val usesRupees = selectedPriceMode == MarketPostOfferPriceMode.RUPEES
        amountSection.visibility = if (usesRupees) View.VISIBLE else View.GONE
        amountPrefix.visibility = if (usesRupees) View.VISIBLE else View.GONE
    }

    private fun buildPhotoPicker(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(color(R.color.market_card), 20f).apply {
                setStroke(dp(1), color(R.color.market_line), dp(5).toFloat(), dp(4).toFloat())
            }
            isClickable = true
            isFocusable = true
            contentDescription = "Add optional offer cover photo"
            setOnClickListener { coverPicker.launch("image/*") }
        }
        val tile = FrameLayout(this).apply {
            background = rounded(color(R.color.market_lav), 14f)
        }
        photoIcon = MarketPostOfferIconView(this, MarketPostOfferIcon.PHOTO, color(R.color.market_ink))
        tile.addView(photoIcon, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        photoThumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        tile.addView(photoThumb, FrameLayout.LayoutParams(-1, -1))
        container.addView(tile, LinearLayout.LayoutParams(dp(44), dp(44)))
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        photoTitle = makeText("Add a cover photo (optional)", 13.5f, true, color(R.color.market_ink))
        photoSubtitle = makeText("Compressed automatically, under 100 KB", 12f, false,
            color(R.color.market_ink2))
        copy.addView(photoTitle)
        copy.addView(photoSubtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        container.addView(copy, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(14)
        })
        return container
    }

    private fun handleCoverSelected(uri: Uri) {
        try {
            val bytes = compressCover(uri) ?: run {
                toast("Could not prepare this image. Please choose another photo.")
                return
            }
            compressedCover = bytes
            val thumbnail = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (thumbnail != null) {
                photoThumb.setImageBitmap(thumbnail)
                photoThumb.visibility = View.VISIBLE
                photoIcon.visibility = View.GONE
            }
            photoTitle.text = "Cover photo added"
            photoSubtitle.text = MarketPostOfferUiPolicy.compressedCoverLabel(bytes.size)
        } catch (_: Exception) {
            toast("Could not prepare this image. Please choose another photo.")
        }
    }

    private fun showExistingCover() {
        val encoded = intent.getStringExtra(EXTRA_THUMBNAIL).orEmpty()
        if (encoded.isBlank() || encoded.length > MarketPostOfferUiPolicy.MAX_COVER_BYTES * 2) return
        val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: return
        if (bytes.size > MarketPostOfferUiPolicy.MAX_COVER_BYTES) return
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        compressedCover = bytes
        photoThumb.setImageBitmap(bitmap)
        photoThumb.visibility = View.VISIBLE
        photoIcon.visibility = View.GONE
        photoTitle.text = "Current cover photo"
        photoSubtitle.text = MarketPostOfferUiPolicy.compressedCoverLabel(bytes.size)
    }

    /** Compress to a Firestore-safe JPEG thumbnail held only until the offer is saved. */
    private fun compressCover(uri: Uri): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 1280) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val imageStream = contentResolver.openInputStream(uri) ?: return null
        var bitmap = imageStream.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        try {
            repeat(8) {
                for (quality in intArrayOf(88, 80, 72, 64, 56, 48)) {
                    val output = ByteArrayOutputStream()
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) return null
                    if (output.size() <= MarketPostOfferUiPolicy.MAX_COVER_BYTES) {
                        return output.toByteArray()
                    }
                }
                val width = max(1, (bitmap.width * 0.8f).toInt())
                val height = max(1, (bitmap.height * 0.8f).toInt())
                if (width == bitmap.width && height == bitmap.height) return null
                val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
                if (resized !== bitmap) bitmap.recycle()
                bitmap = resized
            }
            return null
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun buildFooter(): View {
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(20))
            setBackgroundColor(color(R.color.market_bg))
        }
        submitButton = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(color(R.color.market_match), 16f)
            isClickable = true
            isFocusable = true
            contentDescription = if (editMode) "Preview offer changes" else "Publish offer"
            addView(MarketPostOfferIconView(this@MarketPostOfferActivity,
                MarketPostOfferIcon.CHECK, color(R.color.market_match_ink)),
                LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
            submitLabel = makeText(if (editMode) "Save changes" else "Post offer",
                15f, true, color(R.color.market_match_ink))
            addView(submitLabel)
            setOnClickListener { validateAndShowPreview() }
        }
        footer.addView(submitButton, LinearLayout.LayoutParams(-1, dp(52)))
        return footer
    }

    private fun validateAndShowPreview() {
        val input = MarketPostOfferInput(
            title = titleInput.text?.toString().orEmpty(),
            category = selectedCategory,
            priceMode = selectedPriceMode,
            amount = amountInput.text?.toString().orEmpty(),
            description = descriptionInput.text?.toString().orEmpty()
        )
        val validation = MarketPostOfferUiPolicy.validate(input)
        titleInput.error = validation.titleError
        amountInput.error = validation.amountError
        descriptionInput.error = validation.descriptionError
        if (!validation.isValid) {
            toast("Please check the highlighted fields.")
            return
        }

        if (editMode) updateOffer(input) else postOffer(input)
    }

    private fun postOffer(input: MarketPostOfferInput) {
        if (submittingOffer) return
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            toast("Please sign in to your U2U account before posting an offer.")
            return
        }
        if (!user.isEmailVerified) {
            toast("Verify your account email before posting an offer.")
            return
        }

        submittingOffer = true
        submitButton.isEnabled = false
        submitButton.alpha = 0.65f
        submitLabel.text = "Publishing…"

        val repository = MarketRepository()
        repository.ensureMarketProfile()
            .addOnSuccessListener { createLiveOffer(repository, input) }
            .addOnFailureListener { failure -> finishPostingWithError(failure) }
    }

    private fun updateOffer(input: MarketPostOfferInput) {
        if (submittingOffer) return
        val offerId = intent.getStringExtra(EXTRA_ID).orEmpty()
        if (offerId.isBlank()) {
            finishPostingWithError(IllegalArgumentException("The offer ID is missing. Open My Offers and try again."))
            return
        }
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || !user.isEmailVerified) {
            toast("Sign in with your verified account before editing an offer.")
            return
        }
        submittingOffer = true
        submitButton.isEnabled = false
        submitButton.alpha = 0.65f
        submitLabel.text = "Saving…"
        val priceType = when (input.priceMode) {
            MarketPostOfferPriceMode.FREE -> MarketPriceType.FREE
            MarketPostOfferPriceMode.SWAP -> MarketPriceType.SWAP
            MarketPostOfferPriceMode.RUPEES -> MarketPriceType.AMOUNT
        }
        val edit = MarketOfferEdit(
            title = input.title.trim(),
            category = input.category,
            description = input.description.trim(),
            priceType = priceType,
            courseTag = intent.getStringExtra(EXTRA_COURSE)?.takeIf { it.isNotBlank() },
            priceAmount = if (priceType == MarketPriceType.AMOUNT) input.amount.trim().toDoubleOrNull() else null,
            clearPriceAmount = priceType != MarketPriceType.AMOUNT,
            thumbnail = compressedCover?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        )
        runCatching { MarketRepository().editOffer(offerId, edit) }
            .onSuccess { task ->
                task.addOnSuccessListener { changed ->
                    restorePostButton()
                    if (!changed) {
                        finishPostingWithError(IllegalStateException("Market did not save the offer changes. Refresh and try again."))
                    } else {
                        Toast.makeText(this, "Offer updated in Market.", Toast.LENGTH_LONG).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                }.addOnFailureListener { failure -> finishPostingWithError(failure) }
            }
            .onFailure { failure -> finishPostingWithError(failure) }
    }

    private fun createLiveOffer(repository: MarketRepository, input: MarketPostOfferInput) {
        val thumbnail = compressedCover?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        val priceType = when (input.priceMode) {
            MarketPostOfferPriceMode.FREE -> MarketPriceType.FREE
            MarketPostOfferPriceMode.SWAP -> MarketPriceType.SWAP
            MarketPostOfferPriceMode.RUPEES -> MarketPriceType.AMOUNT
        }
        val draft = MarketOfferDraft(
            title = input.title.trim(),
            category = input.category,
            description = input.description.trim(),
            priceType = priceType,
            priceAmount = if (priceType == MarketPriceType.AMOUNT) {
                input.amount.trim().toDoubleOrNull()
            } else null,
            thumbnail = thumbnail
        )

        runCatching { repository.createOffer(draft) }
            .onSuccess { task ->
                task.addOnSuccessListener {
                    restorePostButton()
                    Toast.makeText(this, "Offer posted to Market.", Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                }.addOnFailureListener { failure -> finishPostingWithError(failure) }
            }
            .onFailure { failure -> finishPostingWithError(failure) }
    }

    private fun finishPostingWithError(failure: Throwable) {
        restorePostButton()
        AlertDialog.Builder(this)
            .setTitle("Couldn’t post offer")
            .setMessage(MarketErrorMapper.map(failure).message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun restorePostButton() {
        submittingOffer = false
        if (::submitButton.isInitialized) {
            submitButton.isEnabled = true
            submitButton.alpha = 1f
            submitLabel.text = if (editMode) "Save changes" else "Post offer"
        }
    }

    private fun iconButton(icon: MarketPostOfferIcon, label: String, action: () -> Unit): FrameLayout =
        FrameLayout(this).apply {
            background = rounded(color(R.color.market_card), 16f, color(R.color.market_line))
            isClickable = true
            isFocusable = true
            contentDescription = label
            addView(MarketPostOfferIconView(this@MarketPostOfferActivity, icon,
                color(R.color.market_ink)), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
            setOnClickListener { action() }
        }

    private fun makeLabel(value: String): TextView =
        makeText(value, 13f, true, color(R.color.market_ink))

    private fun makeText(value: String, size: Float, medium: Boolean, textColor: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            typeface = ResourcesCompat.getFont(this@MarketPostOfferActivity,
                if (medium) R.font.poppins_medium else R.font.poppins)
                ?: Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            setTextColor(textColor)
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun makeInput(
        value: String,
        singleLine: Boolean,
        maxLength: Int,
        inputType: Int,
        transparentBackground: Boolean = false
    ): EditText = EditText(this).apply {
        setText(value)
        setSingleLine(singleLine)
        filters = arrayOf(InputFilter.LengthFilter(maxLength))
        typeface = ResourcesCompat.getFont(this@MarketPostOfferActivity, R.font.poppins)
            ?: Typeface.create("sans-serif", Typeface.NORMAL)
        setTextColor(color(R.color.market_ink))
        setHintTextColor(color(R.color.market_ink2))
        background = if (transparentBackground) {
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        } else {
            rounded(color(R.color.market_card), 16f, color(R.color.market_line))
        }
        if (!transparentBackground) setPadding(dp(14), if (singleLine) 0 else dp(12),
            dp(14), if (singleLine) 0 else dp(12))
        setRawInputType(inputType)
        if (singleLine) setPadding(if (transparentBackground) 0 else dp(14), 0,
            if (transparentBackground) 0 else dp(14), 0)
    }

    private fun fieldParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply {
            if (top > 0) topMargin = dp(top)
            if (bottom > 0) bottomMargin = dp(bottom)
        }

    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !AppThemePreferences.isNight(this@MarketPostOfferActivity)
            isAppearanceLightNavigationBars = !AppThemePreferences.isNight(this@MarketPostOfferActivity)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
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

    private fun color(id: Int): Int = getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_EDIT_MODE = "market.postoffer.edit_mode"
        const val EXTRA_ID = "market.postoffer.id"
        const val EXTRA_COURSE = "market.postoffer.course"
        const val EXTRA_THUMBNAIL = "market.postoffer.thumbnail"
        const val EXTRA_TITLE = "market.postoffer.title"
        const val EXTRA_CATEGORY = "market.postoffer.category"
        const val EXTRA_PRICE_MODE = "market.postoffer.price_mode"
        const val EXTRA_AMOUNT = "market.postoffer.amount"
        const val EXTRA_DESCRIPTION = "market.postoffer.description"
    }
}
