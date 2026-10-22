package com.superiorstudent.app.feature.market.ui

import com.superiorstudent.app.R

internal data class MarketBlockedUser(
    val id: String,
    val name: String,
    val universityId: String,
    val initials: String,
    val avatarBackgroundRes: Int
)

/** Screen 32 uses the supplied blocked-user fixtures only; live block settings are not connected. */
internal object MarketBlockedUsersUiPolicy {
    const val TITLE = "Blocked users"
    const val PREVIEW_CONFIRM_TITLE = "Remove example?"
    const val PREVIEW_CONFIRM_MESSAGE =
        "This is sample reference data. Removing this entry only changes the current preview; it will not change your real blocked-user settings."
    const val PREVIEW_CONFIRM_BUTTON = "Remove example"
    const val INFORMATION_NOTE =
        "You will not see their tasks, offers or proposals, and they cannot start a new Market chat with you."
    const val EMPTY_TITLE = "No blocked users in this preview."

    val referenceUsers = listOf(
        MarketBlockedUser(
            id = "UNI-B9Z4C2L",
            name = "Bilal Raza",
            universityId = "UNI-B9Z4C2L",
            initials = "BR",
            avatarBackgroundRes = R.color.market_mint
        ),
        MarketBlockedUser(
            id = "UNI-K2D8P4M",
            name = "Kamran Hassan",
            universityId = "UNI-K2D8P4M",
            initials = "KH",
            avatarBackgroundRes = R.color.market_peach
        )
    )

    fun countLabel(count: Int): String = "${count.coerceAtLeast(0)} blocked"

    fun withoutUser(users: List<MarketBlockedUser>, id: String): List<MarketBlockedUser> =
        users.filterNot { it.id == id }

    fun hasReferenceContent(): Boolean =
        TITLE == "Blocked users" &&
            referenceUsers.map { it.name } == listOf("Bilal Raza", "Kamran Hassan") &&
            referenceUsers.map { it.universityId } == listOf("UNI-B9Z4C2L", "UNI-K2D8P4M") &&
            referenceUsers.map { it.initials } == listOf("BR", "KH") &&
            INFORMATION_NOTE ==
                "You will not see their tasks, offers or proposals, and they cannot start a new Market chat with you."
}
