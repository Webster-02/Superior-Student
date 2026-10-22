package com.superiorstudent.app.feature.market.ui

/** Screen 31 alert choices; the toggle values are local UI state until persistence is connected. */
internal enum class MarketSettingsAlert(val label: String, val initialEnabled: Boolean) {
    NEW_PROPOSALS("New proposals on my tasks", true),
    PROPOSAL_UPDATES("Proposal updates", true),
    TEAM_MESSAGES("Messages from U2U team", true),
    MATCHING_TASKS("New tasks matching my skills", false)
}

internal object MarketSettingsUiPolicy {
    const val TITLE = "Market settings"
    const val SKILLS_TITLE = "I can help with"
    const val SKILLS_SUBTITLE = "Used to show you matching tasks first"
    const val ADD_SKILL = "+ Add"
    const val ALERTS_TITLE = "Alerts"
    const val BLOCKED_USERS = "Blocked users"
    const val BLOCKED_COUNT = "2 blocked"
    const val HELP_AND_SAFETY = "Help and safety"
    const val MY_REPORTS = "My reports"
    const val BLOCKED_PREVIEW_TITLE = "Blocked users"
    const val BLOCKED_PREVIEW_MESSAGE =
        "Your blocked-user list on Screen 32 uses reference examples. Live blocked-user data is not connected yet."
    const val LOCAL_PREVIEW_TITLE = "Preview only"
    const val LOCAL_PREVIEW_MESSAGE =
        "Skill selections and alert toggles change on this screen only. Saving them to your Market profile is not connected yet."

    val defaultSkills = listOf("Design", "Typing", "Canva")
    val availableSkills = listOf(
        "Design", "Typing", "Canva", "Photoshop", "Coding",
        "Tutoring", "Notes", "Proofreading", "Research", "Presentation"
    )
    val alerts = MarketSettingsAlert.values().toList()

    fun availableToAdd(selected: List<String>): List<String> =
        availableSkills.filterNot { selected.contains(it) }

    fun canAddSkill(selected: List<String>, candidate: String): Boolean =
        availableSkills.contains(candidate) && !selected.contains(candidate)

    fun hasReferenceContent(): Boolean =
        TITLE == "Market settings" &&
            SKILLS_TITLE == "I can help with" &&
            SKILLS_SUBTITLE == "Used to show you matching tasks first" &&
            defaultSkills == listOf("Design", "Typing", "Canva") &&
            alerts.map { it.label } == listOf(
                "New proposals on my tasks",
                "Proposal updates",
                "Messages from U2U team",
                "New tasks matching my skills"
            ) &&
            alerts.map { it.initialEnabled } == listOf(true, true, true, false) &&
            BLOCKED_USERS == "Blocked users" &&
            HELP_AND_SAFETY == "Help and safety" &&
            MY_REPORTS == "My reports"
}
