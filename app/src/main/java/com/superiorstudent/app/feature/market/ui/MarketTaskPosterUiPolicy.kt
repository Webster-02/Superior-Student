package com.superiorstudent.app.feature.market.ui

internal data class MarketTaskPosterUiModel(
    val status: String,
    val category: String,
    val course: String,
    val title: String,
    val author: String,
    val rating: String,
    val age: String,
    val budget: String,
    val budgetNote: String,
    val deadline: String,
    val deadlineNote: String,
    val proposals: Int,
    val shortlisted: Int,
    val details: String,
    val skillTags: List<String>
)

internal object MarketTaskPosterUiPolicy {
    const val MAX_PROPOSALS = 15

    val reference = MarketTaskPosterUiModel(
        status = "Open",
        category = "Design",
        course = "BSOPM",
        title = "Poster for Optometry Society health camp",
        author = "Hamza Ali",
        rating = "4.8 · Verified student",
        age = "2h ago",
        budget = "Rs. 1,500",
        budgetNote = "fixed",
        deadline = "24 Oct",
        deadlineNote = "3 days left",
        proposals = 4,
        shortlisted = 1,
        details = "Need an A3 poster for our health camp on 28 Oct. Two colour options please. Text and logo will be shared on chat after selection. Canva or Photoshop both fine.",
        skillTags = listOf("Canva", "Photoshop", "Layout")
    )

    fun proposalCapacityLabel(model: MarketTaskPosterUiModel): String =
        "${model.proposals.coerceAtLeast(0)} of $MAX_PROPOSALS"

    fun shortlistedLabel(model: MarketTaskPosterUiModel): String =
        "${model.shortlisted.coerceIn(0, model.proposals.coerceAtLeast(0))} shortlisted"
}
