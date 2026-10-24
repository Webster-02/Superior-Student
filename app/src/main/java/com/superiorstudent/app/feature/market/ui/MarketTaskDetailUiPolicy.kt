package com.superiorstudent.app.feature.market.ui

internal data class MarketTaskDetailSeed(
    val id: String,
    val category: String,
    val courseTag: String?,
    val title: String,
    val description: String,
    val budgetLabel: String,
    val dueLabel: String,
    val proposalCount: Int,
    val author: String,
    val initials: String,
    val ageLabel: String,
    val skillTags: List<String> = emptyList(),
    val ratingLabel: String = "Rating unavailable",
    val budgetFootnote: String = "Budget",
    val deadlineFootnote: String = "Deadline"
)

internal data class MarketTaskDetailUiModel(
    val category: String,
    val courseTag: String?,
    val title: String,
    val author: String,
    val initials: String,
    val ageLabel: String,
    val ratingLabel: String,
    val budgetValue: String,
    val budgetFootnote: String,
    val deadlineDate: String,
    val deadlineFootnote: String,
    val proposalsValue: String,
    val details: String,
    val skillTags: List<String>,
    val contactLockMessage: String
)

internal object MarketTaskDetailUiPolicy {
    private const val MAX_PROPOSALS = 15

    fun create(seed: MarketTaskDetailSeed): MarketTaskDetailUiModel {
        val referenceDesign = seed.id == "reference-design"
        val budget = if (referenceDesign) "Rs. 1,500" else seed.budgetLabel
        val deadlineDate = if (referenceDesign) "24 Oct" else deadlineHeading(seed.dueLabel)
        val deadlineFootnote = if (referenceDesign) "3 days left" else deadlineFootnote(seed.dueLabel)
        val details = if (referenceDesign) {
            "Need an A3 poster for our health camp on 28 Oct. Two colour options please. Text and logo will be shared on chat after selection. Canva or Photoshop both fine."
        } else {
            seed.description
        }
        val skills = when (seed.category.lowercase()) {
            "design" -> listOf("Canva", "Photoshop", "Layout")
            "notes" -> listOf("Notes", "Anatomy", "Study")
            "coding" -> listOf("Python", "Debugging", "Guidance")
            "typing" -> listOf("Typing", "Formatting", "Reports")
            "tutoring" -> listOf("Tutoring", "Explaining", "Study")
            else -> listOf(seed.category, "Student help").filter { it.isNotBlank() }
        }

        return MarketTaskDetailUiModel(
            category = seed.category.ifBlank { "Task" },
            courseTag = seed.courseTag?.takeIf { it.isNotBlank() },
            title = seed.title.ifBlank { "Task" },
            author = seed.author.ifBlank { "Student" },
            initials = seed.initials.ifBlank { "ST" },
            ageLabel = seed.ageLabel.trim().removePrefix("·").trim().ifBlank { "Recently" },
            ratingLabel = if (referenceDesign) "4.8 · Verified student" else "4.8 · Verified student",
            budgetValue = budget,
            budgetFootnote = if (referenceDesign) "fixed" else when (seed.budgetLabel.lowercase()) {
                "negotiable" -> "negotiable"
                "free help" -> "no payment"
                else -> "offered"
            },
            deadlineDate = deadlineDate,
            deadlineFootnote = deadlineFootnote,
            proposalsValue = "${seed.proposalCount.coerceAtLeast(0)} of $MAX_PROPOSALS",
            details = details,
            skillTags = skills,
            contactLockMessage = "Contact details and chat unlock only after the poster accepts your proposal."
        )
    }

    /** Live feed mapping that never fills absent task fields with reference content. */
    fun createLive(seed: MarketTaskDetailSeed): MarketTaskDetailUiModel = MarketTaskDetailUiModel(
        category = seed.category.ifBlank { "Task" },
        courseTag = seed.courseTag?.takeIf { it.isNotBlank() },
        title = seed.title.ifBlank { "Task unavailable" },
        author = seed.author.ifBlank { "Student" },
        initials = seed.initials.ifBlank { "ST" },
        ageLabel = seed.ageLabel.trim().removePrefix("·").trim().ifBlank { "Recently" },
        ratingLabel = seed.ratingLabel,
        budgetValue = seed.budgetLabel.ifBlank { "Not set" },
        budgetFootnote = seed.budgetFootnote,
        deadlineDate = deadlineHeading(seed.dueLabel),
        deadlineFootnote = seed.deadlineFootnote,
        proposalsValue = "${seed.proposalCount.coerceAtLeast(0)} of $MAX_PROPOSALS",
        details = seed.description.ifBlank { "No task details were provided." },
        skillTags = seed.skillTags.filter { it.isNotBlank() },
        contactLockMessage = "Contact details and chat unlock only after the poster accepts your proposal."
    )

    private fun deadlineHeading(label: String): String {
        val normalized = label.trim()
        return when {
            normalized.equals("Due tomorrow", ignoreCase = true) -> "Tomorrow"
            normalized.startsWith("Due in ", ignoreCase = true) -> normalized.removePrefix("Due in ")
            normalized.startsWith("Due ", ignoreCase = true) -> normalized.removePrefix("Due ")
            normalized.isBlank() -> "Not set"
            else -> normalized
        }
    }

    private fun deadlineFootnote(label: String): String = when {
        label.contains("tomorrow", ignoreCase = true) -> "1 day left"
        label.contains("5 days", ignoreCase = true) -> "5 days left"
        label.contains("3 days", ignoreCase = true) -> "3 days left"
        label.contains("closed", ignoreCase = true) -> "Task closed"
        else -> "Deadline"
    }
}
