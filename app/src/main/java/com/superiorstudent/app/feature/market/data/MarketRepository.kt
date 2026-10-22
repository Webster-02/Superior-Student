package com.superiorstudent.app.feature.market.data

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Transaction
import java.util.Date
import java.util.Locale

data class MarketTaskDraft(
    val title: String,
    val category: String,
    val description: String,
    val budgetType: MarketBudgetType,
    val deadline: Timestamp,
    val skills: List<String>,
    val courseTag: String? = null,
    val budgetAmount: Double? = null,
    val moderation: MarketModerationStatus = MarketModerationStatus.OK
)

data class MarketTaskEdit(
    val title: String? = null,
    val category: String? = null,
    val description: String? = null,
    val budgetType: MarketBudgetType? = null,
    val deadline: Timestamp? = null,
    val skills: List<String>? = null,
    val courseTag: String? = null,
    val clearCourseTag: Boolean = false,
    val budgetAmount: Double? = null,
    val clearBudgetAmount: Boolean = false
)

data class MarketOfferDraft(
    val title: String,
    val category: String,
    val description: String,
    val priceType: MarketPriceType,
    val courseTag: String? = null,
    val priceAmount: Double? = null,
    val thumbnail: String? = null,
    val moderation: MarketModerationStatus = MarketModerationStatus.OK
)

data class MarketOfferEdit(
    val title: String? = null,
    val category: String? = null,
    val description: String? = null,
    val priceType: MarketPriceType? = null,
    val courseTag: String? = null,
    val clearCourseTag: Boolean = false,
    val priceAmount: Double? = null,
    val clearPriceAmount: Boolean = false,
    val thumbnail: String? = null,
    val clearThumbnail: Boolean = false
)

data class MarketSupportDraft(
    val type: MarketSupportType,
    val subject: String,
    val body: String,
    val relatedType: String? = null,
    val relatedId: String? = null
)

/**
 * The only Firestore write boundary for Market documents.
 *
 * Multi-document changes use atomic transactions. Firestore rules remain the
 * final authority; local validations only provide earlier, friendlier feedback.
 */
class MarketRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    // ------------------------------------------------------------------------
    // Profile bootstrap and self-service preferences
    // ------------------------------------------------------------------------

    fun createMarketProfile(
        skillTags: List<String> = emptyList(),
        settings: MarketProfileSettings = defaultSettings()
    ): Task<Boolean> {
        val uid = requireVerifiedUid()
        validateSkillTags(skillTags)
        val profileRef = marketProfileRef(uid)
        val studentProfileRef = studentProfileRef(uid)

        return firestore.runTransaction { tx ->
            val studentProfile = tx.get(studentProfileRef)
            require(studentProfile.exists()) {
                "Create your Student Connect profile before enabling Market."
            }
            requireValidDisplayName(studentProfile.getString("displayName"))
            require(!tx.get(profileRef).exists()) { "Your Market profile already exists." }

            tx.set(profileRef, linkedMapOf<String, Any?>(
                "uid" to uid,
                "skillTags" to skillTags,
                "blockedUids" to emptyList<String>(),
                "status" to MarketProfileStatus.ACTIVE.wireValue,
                "suspendedUntil" to null,
                "warnings" to 0,
                "settings" to settingsMap(settings),
                "openTasks" to 0,
                "activeOffers" to 0,
                "proposalWindowCount" to 0,
                "proposalWindowStartedAt" to null,
                "reportWindowCount" to 0,
                "reportWindowStartedAt" to null,
                "openSupportTickets" to 0,
                "lastMutation" to null,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            true
        }
    }

    fun updateSkillTags(skillTags: List<String>): Task<Void> {
        val uid = requireSignedInUid()
        validateSkillTags(skillTags)
        return marketProfileRef(uid).update(
            "skillTags", skillTags,
            "updatedAt", FieldValue.serverTimestamp()
        )
    }

    fun updateSettings(settings: MarketProfileSettings): Task<Void> {
        val uid = requireSignedInUid()
        return marketProfileRef(uid).update(
            "settings", settingsMap(settings),
            "updatedAt", FieldValue.serverTimestamp()
        )
    }

    fun blockUser(blockedUid: String): Task<Void> {
        val uid = requireSignedInUid()
        require(blockedUid.isNotBlank() && blockedUid != uid) {
            "Choose another Market user to block."
        }
        // These self-service fields require sign-in, not verified email, per rules.
        return marketProfileRef(uid).update(
            "blockedUids", FieldValue.arrayUnion(blockedUid),
            "updatedAt", FieldValue.serverTimestamp()
        )
    }

    fun unblockUser(blockedUid: String): Task<Void> {
        val uid = requireSignedInUid()
        require(blockedUid.isNotBlank() && blockedUid != uid) {
            "The blocked user ID is invalid."
        }
        return marketProfileRef(uid).update(
            "blockedUids", FieldValue.arrayRemove(blockedUid),
            "updatedAt", FieldValue.serverTimestamp()
        )
    }

    // ------------------------------------------------------------------------
    // Tasks and task lifecycle
    // ------------------------------------------------------------------------

    fun createTask(draft: MarketTaskDraft): Task<String> {
        val uid = requireVerifiedUid()
        validateTaskDraft(draft)
        require(draft.moderation == MarketModerationStatus.OK ||
            draft.moderation == MarketModerationStatus.UNDER_REVIEW) {
            "A new task must be public or sent for review."
        }
        val taskRef = firestore.collection(TASKS).document()
        val profileRef = marketProfileRef(uid)
        val createdAt = MarketRepositoryPolicy.safeClientTimestamp(nowMillis())
        val expiresAt = MarketRepositoryPolicy.taskExpiry(createdAt)

        return firestore.runTransaction { tx ->
            val profile = tx.get(profileRef)
            require(profile.exists()) { "Your Market profile is not ready yet." }
            requirePostingAllowed(profile, nowMillis())
            require(profile.long("openTasks") < MAX_OPEN_TASKS) {
                "You have reached the 3 open task limit."
            }
            val studentProfile = tx.get(studentProfileRef(uid))
            val posterName = requireValidDisplayName(studentProfile.getString("displayName"))

            val data = linkedMapOf<String, Any?>(
                "title" to draft.title,
                "category" to draft.category,
                "description" to draft.description,
                "budgetType" to draft.budgetType.wireValue,
                "deadline" to draft.deadline,
                "skills" to draft.skills,
                "posterId" to uid,
                "posterName" to posterName,
                "posterInitials" to MarketRepositoryPolicy.initials(posterName),
                "status" to MarketTaskStatus.OPEN.wireValue,
                "moderation" to draft.moderation.wireValue,
                "pinned" to false,
                "proposalCount" to 0,
                "reportCount" to 0,
                "createdAt" to createdAt,
                "updatedAt" to FieldValue.serverTimestamp(),
                "expiresAt" to expiresAt
            )
            draft.courseTag?.let { data["courseTag"] = it }
            draft.budgetAmount?.let { data["budgetAmount"] = it }

            tx.set(taskRef, data)
            tx.update(profileRef, mapOf(
                "openTasks" to profile.long("openTasks").toInt() + 1,
                "lastMutation" to mutationMap("task", "create", taskRef.id),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            taskRef.id
        }
    }

    /**
     * Opt-in mapped-result path for new create-task UI consumers.
     *
     * The original [createTask] API remains unchanged for existing callers.
     * Failures are values here so a screen can keep its current content and
     * decide whether to show Retry from [MarketMappedError.retryable].
     */
    internal fun createTaskWithMappedError(
        draft: MarketTaskDraft
    ): Task<MarketOperationResult<String>> =
        MarketOperationTaskAdapter.run { createTask(draft) }

    fun editTask(taskId: String, edit: MarketTaskEdit): Task<Boolean> {
        val uid = requireVerifiedUid()
        require(taskId.isNotBlank()) { "A task is required." }
        val taskRef = firestore.collection(TASKS).document(taskId)
        val profileRef = marketProfileRef(uid)
        val updates = taskEditMap(edit)
        require(updates.isNotEmpty()) { "Make at least one task change." }

        return firestore.runTransaction { tx ->
            val task = tx.get(taskRef)
            require(task.exists()) { "This task no longer exists." }
            require(task.getString("posterId") == uid) { "Only the task poster can edit it." }
            require(task.getString("status") == MarketTaskStatus.OPEN.wireValue) {
                "Only an open task can be edited."
            }
            if ((task.getLong("proposalCount") ?: 0L) > 0L) {
                require(updates.keys.all { it == "description" }) {
                    "Once proposals exist, only the task description can be edited."
                }
            }
            val profile = tx.get(profileRef)
            require(profile.exists()) { "Your Market profile is not ready yet." }
            requirePostingAllowed(profile, nowMillis())

            val merged = task.data.orEmpty().toMutableMap()
            for ((key, value) in updates) {
                if (value == FieldValue.delete()) merged.remove(key) else merged[key] = value
            }
            validateTaskContentMap(merged)
            val patch = LinkedHashMap<String, Any>(updates)
            patch["updatedAt"] = FieldValue.serverTimestamp()
            tx.update(taskRef, patch)
            true
        }
    }

    fun cancelTask(taskId: String): Task<Boolean> =
        closeOpenTask(taskId, MarketTaskStatus.CANCELLED, onlyAfterExpiry = false)

    fun expireTask(taskId: String): Task<Boolean> =
        closeOpenTask(taskId, MarketTaskStatus.EXPIRED, onlyAfterExpiry = true)

    private fun closeOpenTask(
        taskId: String,
        newStatus: MarketTaskStatus,
        onlyAfterExpiry: Boolean
    ): Task<Boolean> {
        val uid = requireVerifiedUid()
        require(taskId.isNotBlank()) { "A task is required." }
        val taskRef = firestore.collection(TASKS).document(taskId)
        val profileRef = marketProfileRef(uid)

        return firestore.runTransaction { tx ->
            val task = tx.get(taskRef)
            require(task.exists()) { "This task no longer exists." }
            require(task.getString("posterId") == uid) { "Only the task poster can close it." }
            require(task.getString("status") == MarketTaskStatus.OPEN.wireValue) {
                "Only an open task can be closed."
            }
            val expiresAt = task.getTimestamp("expiresAt")
            if (onlyAfterExpiry) {
                require(expiresAt != null && nowMillis() > expiresAt.toDate().time) {
                    "This task has not expired yet."
                }
            }
            val profile = tx.get(profileRef)
            require(profile.exists()) { "Your Market profile is not ready yet." }
            val openTasks = profile.long("openTasks").toInt()
            require(openTasks > 0) { "Your open task counter needs attention. Refresh and try again." }

            tx.update(taskRef, mapOf(
                "status" to newStatus.wireValue,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            tx.update(profileRef, mapOf(
                "openTasks" to openTasks - 1,
                "lastMutation" to mutationMap("task", "close", taskId),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            true
        }
    }

    /** Completion is status-only: acceptance already released the poster's open-task slot. */
    fun completeTask(taskId: String): Task<Boolean> {
        val uid = requireVerifiedUid()
        val ref = firestore.collection(TASKS).document(taskId)
        return firestore.runTransaction { tx ->
            val task = tx.get(ref)
            require(task.exists()) { "This task no longer exists." }
            require(task.getString("posterId") == uid) { "Only the task poster can complete it." }
            require(task.getString("status") == MarketTaskStatus.ASSIGNED.wireValue) {
                "Only an assigned task can be completed."
            }
            tx.update(ref, mapOf(
                "status" to MarketTaskStatus.COMPLETED.wireValue,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            true
        }
    }

    // ------------------------------------------------------------------------
    // Proposals and atomic accept + Market conversation creation
    // ------------------------------------------------------------------------

    fun createProposal(taskId: String, message: String, price: Double, deliveryDays: Int): Task<String> {
        val uid = requireVerifiedUid()
        validateProposalFields(message, price, deliveryDays)
        require(taskId.isNotBlank()) { "A task is required." }
        val taskRef = firestore.collection(TASKS).document(taskId)
        val proposalRef = taskRef.collection(PROPOSALS).document(uid)
        val profileRef = marketProfileRef(uid)

        return firestore.runTransaction { tx ->
            val task = tx.get(taskRef)
            require(task.exists()) { "This task no longer exists." }
            require(task.getString("status") == MarketTaskStatus.OPEN.wireValue) {
                "This task is no longer accepting proposals."
            }
            require(task.getString("posterId") != uid) { "You cannot send a proposal on your own task." }
            require(task.getTimestamp("deadline")?.toDate()?.time?.let { nowMillis() < it } == true &&
                task.getTimestamp("expiresAt")?.toDate()?.time?.let { nowMillis() < it } == true) {
                "This task has passed its deadline."
            }
            require((task.getLong("proposalCount") ?: 0L) < MAX_PROPOSALS_PER_TASK) {
                "This task has reached the 15 proposal limit."
            }
            require(!tx.get(proposalRef).exists()) { "You have already proposed on this task." }

            val profile = tx.get(profileRef)
            require(profile.exists()) { "Create your Market profile before proposing." }
            requirePostingAllowed(profile, nowMillis())
            val window = nextWindow(profile, "proposalWindowCount", "proposalWindowStartedAt")
            require(window.count <= MAX_PROPOSALS_PER_24_HOURS) {
                "You have reached the 10 proposals per 24-hour limit."
            }
            val studentProfile = tx.get(studentProfileRef(uid))
            val proposerName = requireValidDisplayName(studentProfile.getString("displayName"))

            tx.set(proposalRef, linkedMapOf<String, Any?>(
                "proposerId" to uid,
                "message" to message.trim(),
                "price" to price,
                "deliveryDays" to deliveryDays,
                "status" to MarketProposalStatus.SENT.wireValue,
                "proposerName" to proposerName,
                "proposerInitials" to MarketRepositoryPolicy.initials(proposerName),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            tx.update(taskRef, mapOf(
                "proposalCount" to (task.getLong("proposalCount") ?: 0L).toInt() + 1,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            val profileUpdate = linkedMapOf<String, Any>(
                "proposalWindowCount" to window.count,
                "lastMutation" to mutationMap("proposal", "create", taskId),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            profileUpdate["proposalWindowStartedAt"] = if (window.reset) {
                FieldValue.serverTimestamp()
            } else {
                window.startedAt ?: throw IllegalStateException("Proposal window start is missing.")
            }
            tx.update(profileRef, profileUpdate)
            uid
        }
    }

    fun editProposal(taskId: String, message: String, price: Double, deliveryDays: Int): Task<Boolean> {
        val uid = requireVerifiedUid()
        validateProposalFields(message, price, deliveryDays)
        val taskRef = firestore.collection(TASKS).document(taskId)
        val proposalRef = taskRef.collection(PROPOSALS).document(uid)
        return firestore.runTransaction { tx ->
            val task = tx.get(taskRef)
            val proposal = tx.get(proposalRef)
            require(task.exists() && proposal.exists()) { "This proposal no longer exists." }
            require(task.getString("status") == MarketTaskStatus.OPEN.wireValue) {
                "The task is closed. This proposal can no longer be edited."
            }
            require(proposal.getString("status") in listOf(
                MarketProposalStatus.SENT.wireValue, MarketProposalStatus.SHORTLISTED.wireValue
            )) { "Only a sent or shortlisted proposal can be edited." }
            tx.update(proposalRef, mapOf(
                "message" to message.trim(),
                "price" to price,
                "deliveryDays" to deliveryDays,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            true
        }
    }

    fun withdrawProposal(taskId: String): Task<Boolean> {
        val uid = requireVerifiedUid()
        val proposalRef = firestore.collection(TASKS).document(taskId).collection(PROPOSALS).document(uid)
        return firestore.runTransaction { tx ->
            val proposal = tx.get(proposalRef)
            require(proposal.exists()) { "This proposal no longer exists." }
            require(proposal.getString("status") in listOf(
                MarketProposalStatus.SENT.wireValue, MarketProposalStatus.SHORTLISTED.wireValue
            )) { "This proposal can no longer be withdrawn." }
            tx.update(proposalRef, mapOf(
                "status" to MarketProposalStatus.WITHDRAWN.wireValue,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            true
        }
    }

    fun setProposalStatus(
        taskId: String,
        proposerUid: String,
        newStatus: MarketProposalStatus
    ): Task<Boolean> {
        val uid = requireVerifiedUid()
        require(newStatus == MarketProposalStatus.SHORTLISTED ||
            newStatus == MarketProposalStatus.DECLINED) {
            "Use acceptProposal to accept a proposal."
        }
        val taskRef = firestore.collection(TASKS).document(taskId)
        val proposalRef = taskRef.collection(PROPOSALS).document(proposerUid)
        return firestore.runTransaction { tx ->
            val task = tx.get(taskRef)
            val proposal = tx.get(proposalRef)
            require(task.exists() && proposal.exists()) { "The task or proposal no longer exists." }
            require(task.getString("posterId") == uid) { "Only the task poster can change proposal status." }
            require(proposal.getString("status") in listOf(
                MarketProposalStatus.SENT.wireValue, MarketProposalStatus.SHORTLISTED.wireValue
            )) { "This proposal is no longer awaiting a decision." }
            require(task.getString("status") == MarketTaskStatus.OPEN.wireValue) {
                "The task is no longer open."
            }
            tx.update(proposalRef, mapOf(
                "status" to newStatus.wireValue,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            true
        }
    }

    /** Assignment, accepted proposal, poster counter decrement, and task chat are one transaction. */
    fun acceptProposal(taskId: String, proposerUid: String): Task<String> {
        val uid = requireVerifiedUid()
        require(proposerUid.isNotBlank() && proposerUid != uid) { "Choose a valid proposal." }
        val taskRef = firestore.collection(TASKS).document(taskId)
        val proposalRef = taskRef.collection(PROPOSALS).document(proposerUid)
        val posterProfileRef = marketProfileRef(uid)
        val sortedParticipants = listOf(uid, proposerUid).sorted()
        val conversationId = MarketRepositoryPolicy.conversationId(
            MarketListingKind.TASK, taskId, sortedParticipants[0], sortedParticipants[1]
        )
        val conversationRef = firestore.collection(MARKET_CONVERSATIONS).document(conversationId)

        return firestore.runTransaction { tx ->
            val task = tx.get(taskRef)
            val proposal = tx.get(proposalRef)
            val posterProfile = tx.get(posterProfileRef)
            require(task.exists() && proposal.exists()) { "The task or proposal no longer exists." }
            require(task.getString("posterId") == uid) { "Only the task poster can accept a proposal." }
            require(task.getString("status") == MarketTaskStatus.OPEN.wireValue) {
                "This task has already been accepted or closed."
            }
            require(proposal.getString("status") in listOf(
                MarketProposalStatus.SENT.wireValue, MarketProposalStatus.SHORTLISTED.wireValue
            )) { "This proposal is no longer available." }
            require(task.getTimestamp("expiresAt")?.toDate()?.time?.let { nowMillis() < it } == true) {
                "This task has expired."
            }
            require(posterProfile.exists()) { "The poster's Market profile is missing." }
            val openTasks = posterProfile.long("openTasks").toInt()
            require(openTasks > 0) { "The poster's open task counter needs attention." }

            tx.update(taskRef, mapOf(
                "status" to MarketTaskStatus.ASSIGNED.wireValue,
                "assignedTo" to proposerUid,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            tx.update(proposalRef, mapOf(
                "status" to MarketProposalStatus.ACCEPTED.wireValue,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            tx.update(posterProfileRef, mapOf(
                "openTasks" to openTasks - 1,
                "lastMutation" to mutationMap("task", "close", taskId),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            tx.set(conversationRef, linkedMapOf<String, Any?>(
                "participants" to sortedParticipants,
                "kind" to MarketListingKind.TASK.wireValue,
                "listingId" to taskId,
                "listingOwnerUid" to uid,
                "taskId" to taskId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            conversationId
        }
    }

    // ------------------------------------------------------------------------
    // Offers and offer lifecycle
    // ------------------------------------------------------------------------

    fun createOffer(draft: MarketOfferDraft): Task<String> {
        val uid = requireVerifiedUid()
        validateOfferDraft(draft)
        require(draft.moderation == MarketModerationStatus.OK ||
            draft.moderation == MarketModerationStatus.UNDER_REVIEW) {
            "A new offer must be public or sent for review."
        }
        val offerRef = firestore.collection(OFFERS).document()
        val profileRef = marketProfileRef(uid)
        val createdAt = MarketRepositoryPolicy.safeClientTimestamp(nowMillis())
        val expiresAt = MarketRepositoryPolicy.offerExpiry(createdAt)

        return firestore.runTransaction { tx ->
            val profile = tx.get(profileRef)
            require(profile.exists()) { "Your Market profile is not ready yet." }
            requirePostingAllowed(profile, nowMillis())
            require(profile.long("activeOffers") < MAX_ACTIVE_OFFERS) {
                "You have reached the 5 active offer limit."
            }
            val studentProfile = tx.get(studentProfileRef(uid))
            val sellerName = requireValidDisplayName(studentProfile.getString("displayName"))

            val data = linkedMapOf<String, Any?>(
                "title" to draft.title,
                "category" to draft.category,
                "description" to draft.description,
                "priceType" to draft.priceType.wireValue,
                "sellerId" to uid,
                "sellerName" to sellerName,
                "sellerInitials" to MarketRepositoryPolicy.initials(sellerName),
                "status" to MarketOfferStatus.ACTIVE.wireValue,
                "moderation" to draft.moderation.wireValue,
                "pinned" to false,
                "reportCount" to 0,
                "createdAt" to createdAt,
                "renewedAt" to createdAt,
                "updatedAt" to FieldValue.serverTimestamp(),
                "expiresAt" to expiresAt
            )
            draft.courseTag?.let { data["courseTag"] = it }
            draft.priceAmount?.let { data["priceAmount"] = it }
            draft.thumbnail?.let { data["thumbnail"] = it }

            tx.set(offerRef, data)
            tx.update(profileRef, mapOf(
                "activeOffers" to profile.long("activeOffers").toInt() + 1,
                "lastMutation" to mutationMap("offer", "create", offerRef.id),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            offerRef.id
        }
    }

    /**
     * Opt-in mapped-result path for new create-offer UI consumers.
     *
     * Existing [createOffer] callers retain their current Task<String> contract.
     */
    internal fun createOfferWithMappedError(
        draft: MarketOfferDraft
    ): Task<MarketOperationResult<String>> =
        MarketOperationTaskAdapter.run { createOffer(draft) }

    fun editOffer(offerId: String, edit: MarketOfferEdit): Task<Boolean> {
        val uid = requireVerifiedUid()
        val offerRef = firestore.collection(OFFERS).document(offerId)
        val profileRef = marketProfileRef(uid)
        val updates = offerEditMap(edit)
        require(updates.isNotEmpty()) { "Make at least one offer change." }

        return firestore.runTransaction { tx ->
            val offer = tx.get(offerRef)
            require(offer.exists()) { "This offer no longer exists." }
            require(offer.getString("sellerId") == uid) { "Only the seller can edit this offer." }
            val profile = tx.get(profileRef)
            require(profile.exists()) { "Your Market profile is not ready yet." }
            requirePostingAllowed(profile, nowMillis())

            val merged = offer.data.orEmpty().toMutableMap()
            for ((key, value) in updates) {
                if (value == FieldValue.delete()) merged.remove(key) else merged[key] = value
            }
            validateOfferContentMap(merged)
            val patch = LinkedHashMap<String, Any>(updates)
            patch["updatedAt"] = FieldValue.serverTimestamp()
            tx.update(offerRef, patch)
            true
        }
    }

    fun pauseOffer(offerId: String): Task<Boolean> =
        transitionOffer(offerId, MarketOfferStatus.PAUSED)

    fun resumeOffer(offerId: String): Task<Boolean> =
        transitionOffer(offerId, MarketOfferStatus.ACTIVE)

    fun removeOffer(offerId: String): Task<Boolean> =
        transitionOffer(offerId, MarketOfferStatus.REMOVED)

    fun expireOffer(offerId: String): Task<Boolean> =
        transitionOffer(offerId, MarketOfferStatus.EXPIRED)

    private fun transitionOffer(offerId: String, newStatus: MarketOfferStatus): Task<Boolean> {
        val uid = requireVerifiedUid()
        require(offerId.isNotBlank()) { "An offer is required." }
        val offerRef = firestore.collection(OFFERS).document(offerId)
        val profileRef = marketProfileRef(uid)

        return firestore.runTransaction { tx ->
            val offer = tx.get(offerRef)
            require(offer.exists()) { "This offer no longer exists." }
            require(offer.getString("sellerId") == uid) { "Only the seller can change this offer." }
            val beforeStatus = offer.getString("status") ?: ""
            val expiresAt = offer.getTimestamp("expiresAt")
            val profile = tx.get(profileRef)
            require(profile.exists()) { "Your Market profile is missing." }
            val activeOffers = profile.long("activeOffers").toInt()
            val patch = linkedMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            var nextActiveOffers = activeOffers
            var mutation: Map<String, Any>? = null

            when (newStatus) {
                MarketOfferStatus.PAUSED -> {
                    require(beforeStatus == MarketOfferStatus.ACTIVE.wireValue) {
                        "Only an active offer can be paused."
                    }
                    require(activeOffers > 0) { "Your active offer counter needs attention." }
                    patch["status"] = MarketOfferStatus.PAUSED.wireValue
                    nextActiveOffers--
                    mutation = mutationMap("offer", "deactivate", offerId)
                }
                MarketOfferStatus.EXPIRED -> {
                    require(beforeStatus == MarketOfferStatus.ACTIVE.wireValue) {
                        "Only an active offer can be marked expired."
                    }
                    require(expiresAt != null && nowMillis() > expiresAt.toDate().time) {
                        "This offer has not expired yet."
                    }
                    require(activeOffers > 0) { "Your active offer counter needs attention." }
                    patch["status"] = MarketOfferStatus.EXPIRED.wireValue
                    nextActiveOffers--
                    mutation = mutationMap("offer", "deactivate", offerId)
                }
                MarketOfferStatus.REMOVED -> {
                    require(beforeStatus == MarketOfferStatus.ACTIVE.wireValue ||
                        beforeStatus == MarketOfferStatus.PAUSED.wireValue) {
                        "Only an active or paused offer can be removed."
                    }
                    patch["status"] = MarketOfferStatus.REMOVED.wireValue
                    if (beforeStatus == MarketOfferStatus.ACTIVE.wireValue) {
                        require(activeOffers > 0) { "Your active offer counter needs attention." }
                        nextActiveOffers--
                        mutation = mutationMap("offer", "deactivate", offerId)
                    }
                }
                MarketOfferStatus.ACTIVE -> {
                    require(beforeStatus == MarketOfferStatus.PAUSED.wireValue) {
                        "Only a paused offer can be resumed. Expired offers must be renewed."
                    }
                    require(expiresAt != null && nowMillis() <= expiresAt.toDate().time) {
                        "This offer has expired. Use Renew when it becomes available."
                    }
                    require(activeOffers < MAX_ACTIVE_OFFERS) { "You have reached the 5 active offer limit." }
                    patch["status"] = MarketOfferStatus.ACTIVE.wireValue
                    nextActiveOffers++
                    mutation = mutationMap("offer", "reactivate", offerId)
                }
            }

            tx.update(offerRef, patch)
            if (mutation != null) {
                tx.update(profileRef, mapOf(
                    "activeOffers" to nextActiveOffers,
                    "lastMutation" to mutation,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
            true
        }
    }

    fun renewOffer(offerId: String): Task<Boolean> {
        val uid = requireVerifiedUid()
        val offerRef = firestore.collection(OFFERS).document(offerId)
        val profileRef = marketProfileRef(uid)
        val renewedAt = MarketRepositoryPolicy.safeClientTimestamp(nowMillis())
        val expiresAt = MarketRepositoryPolicy.offerExpiry(renewedAt)

        return firestore.runTransaction { tx ->
            val offer = tx.get(offerRef)
            val profile = tx.get(profileRef)
            require(offer.exists()) { "This offer no longer exists." }
            require(profile.exists()) { "Your Market profile is missing." }
            require(offer.getString("sellerId") == uid) { "Only the seller can renew this offer." }
            val oldRenewedAt = offer.getTimestamp("renewedAt")
            require(oldRenewedAt != null &&
                renewedAt.toDate().time >= oldRenewedAt.toDate().time + MarketRepositoryPolicy.RENEWAL_INTERVAL_MS) {
                "Renewal is available once every 7 days."
            }
            val beforeStatus = offer.getString("status") ?: ""
            require(beforeStatus == MarketOfferStatus.ACTIVE.wireValue ||
                beforeStatus == MarketOfferStatus.PAUSED.wireValue ||
                beforeStatus == MarketOfferStatus.EXPIRED.wireValue) {
                "This offer cannot be renewed in its current state."
            }
            val patch = mapOf(
                "status" to MarketOfferStatus.ACTIVE.wireValue,
                "renewedAt" to renewedAt,
                "expiresAt" to expiresAt,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (beforeStatus == MarketOfferStatus.ACTIVE.wireValue) {
                tx.update(offerRef, patch)
            } else {
                val activeOffers = profile.long("activeOffers").toInt()
                require(activeOffers < MAX_ACTIVE_OFFERS) { "You have reached the 5 active offer limit." }
                tx.update(offerRef, patch)
                tx.update(profileRef, mapOf(
                    "activeOffers" to activeOffers + 1,
                    "lastMutation" to mutationMap("offer", "reactivate", offerId),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
            true
        }
    }

    // ------------------------------------------------------------------------
    // Reports: report document, target reportCount/moderation, rolling counter
    // ------------------------------------------------------------------------

    fun submitReport(
        targetType: MarketReportTargetType,
        targetId: String,
        reason: MarketReportReason
    ): Task<String> {
        val uid = requireVerifiedUid()
        require(targetId.isNotBlank() && targetId.length <= 128 && targetId != uid) {
            "Choose a valid item or another user to report."
        }
        val reportId = MarketRepositoryPolicy.reportId(targetType, targetId, uid)
        val reportRef = firestore.collection(REPORTS).document(reportId)
        val reporterProfileRef = marketProfileRef(uid)
        val targetRef = when (targetType) {
            MarketReportTargetType.TASK -> firestore.collection(TASKS).document(targetId)
            MarketReportTargetType.OFFER -> firestore.collection(OFFERS).document(targetId)
            MarketReportTargetType.USER -> marketProfileRef(targetId)
        }

        return firestore.runTransaction { tx ->
            val target = if (targetType == MarketReportTargetType.USER) null else tx.get(targetRef)
            if (targetType != MarketReportTargetType.USER) {
                require(target?.exists() == true) { "The item you reported no longer exists." }
            }
            val reporterProfile = tx.get(reporterProfileRef)
            require(reporterProfile.exists()) { "Create your Market profile before reporting." }
            val window = nextWindow(reporterProfile, "reportWindowCount", "reportWindowStartedAt")
            require(window.count <= MAX_REPORTS_PER_24_HOURS) {
                "You have reached the 10 reports per 24-hour limit."
            }

            tx.set(reportRef, linkedMapOf<String, Any?>(
                "targetType" to targetType.wireValue,
                "targetId" to targetId,
                "reporterId" to uid,
                "reason" to reason.wireValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "status" to MarketReportStatus.OPEN.wireValue
            ))

            if (targetType == MarketReportTargetType.TASK) {
                val task = target ?: throw IllegalStateException("The task being reported is unavailable.")
                require(task.getString("posterId") != uid) { "You cannot report your own task." }
                val oldCount = task.long("reportCount").toInt()
                val oldMod = task.getString("moderation") ?: MarketModerationStatus.OK.wireValue
                val newCount = oldCount + 1
                val newMod = if (newCount >= REPORTS_TO_HIDE && oldMod == MarketModerationStatus.OK.wireValue) {
                    MarketModerationStatus.UNDER_REVIEW.wireValue
                } else oldMod
                tx.update(targetRef, mapOf(
                    "reportCount" to newCount,
                    "moderation" to newMod,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            } else if (targetType == MarketReportTargetType.OFFER) {
                val offer = target ?: throw IllegalStateException("The offer being reported is unavailable.")
                require(offer.getString("sellerId") != uid) { "You cannot report your own offer." }
                val oldCount = offer.long("reportCount").toInt()
                val oldMod = offer.getString("moderation") ?: MarketModerationStatus.OK.wireValue
                val newCount = oldCount + 1
                val newMod = if (newCount >= REPORTS_TO_HIDE && oldMod == MarketModerationStatus.OK.wireValue) {
                    MarketModerationStatus.UNDER_REVIEW.wireValue
                } else oldMod
                tx.update(targetRef, mapOf(
                    "reportCount" to newCount,
                    "moderation" to newMod,
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }

            val profileUpdate = linkedMapOf<String, Any>(
                "reportWindowCount" to window.count,
                "lastMutation" to mutationMap("report", "create", targetId, targetType.wireValue),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            profileUpdate["reportWindowStartedAt"] = if (window.reset) {
                FieldValue.serverTimestamp()
            } else {
                window.startedAt ?: throw IllegalStateException("Report window start is missing.")
            }
            tx.update(reporterProfileRef, profileUpdate)
            reportId
        }
    }

    // ------------------------------------------------------------------------
    // Ratings and userStats, committed atomically
    // ------------------------------------------------------------------------

    fun rateCompletedTask(
        taskId: String,
        toUid: String,
        stars: Int,
        tags: List<String> = emptyList(),
        comment: String = ""
    ): Task<String> {
        val uid = requireVerifiedUid()
        require(taskId.isNotBlank() && toUid.isNotBlank() && toUid != uid) {
            "A task participant is required to rate."
        }
        require(stars in 1..5) { "Choose a rating from 1 to 5." }
        require(tags.size <= 8 && tags.all { it.length <= 40 }) { "Choose up to 8 valid rating tags." }
        require(comment.length <= 200) { "The rating comment must be 200 characters or fewer." }
        val ratingId = MarketRepositoryPolicy.ratingId(taskId, uid)
        val taskRef = firestore.collection(TASKS).document(taskId)
        val ratingRef = firestore.collection(RATINGS).document(ratingId)
        val statsRef = firestore.collection(USER_STATS).document(toUid)

        return firestore.runTransaction { tx ->
            val task = tx.get(taskRef)
            val existing = tx.get(ratingRef)
            val stats = tx.get(statsRef)
            require(task.exists()) { "This task no longer exists." }
            require(!existing.exists()) { "You have already rated this task." }
            require(task.getString("status") == MarketTaskStatus.COMPLETED.wireValue) {
                "You can rate only after the task is completed."
            }
            val posterUid = task.getString("posterId")
            val assignedUid = task.getString("assignedTo")
            require((posterUid == uid && assignedUid == toUid) ||
                (assignedUid == uid && posterUid == toUid)) {
                "Only the two task participants can rate each other."
            }

            tx.set(ratingRef, linkedMapOf<String, Any?>(
                "taskId" to taskId,
                "fromUid" to uid,
                "toUid" to toUid,
                "stars" to stars,
                "tags" to tags,
                "comment" to comment,
                "createdAt" to FieldValue.serverTimestamp()
            ))

            val ratingSum = if (stats.exists()) stats.number("ratingSum") + stars else stars.toDouble()
            val ratingCount = if (stats.exists()) stats.long("ratingCount").toInt() + 1 else 1
            val completedCount = if (stats.exists()) stats.long("completedCount").toInt() + 1 else 1
            val statsData = mapOf(
                "ratingSum" to ratingSum,
                "ratingCount" to ratingCount,
                "completedCount" to completedCount,
                "lastMutation" to mutationMap("rating", "create", ratingId),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (stats.exists()) tx.update(statsRef, statsData) else tx.set(statsRef, statsData)
            ratingId
        }
    }

    /**
     * Starts a separate Market conversation for a public offer, without using U2U chat.
     */
    /**
     * Starts a separate Market conversation for a public offer. It never uses U2U chat.
     */
    fun openOfferConversation(offerId: String): Task<String> {
        val uid = requireVerifiedUid()
        require(offerId.isNotBlank()) { "An offer is required." }
        val offerRef = firestore.collection(OFFERS).document(offerId)
        val ownProfileRef = marketProfileRef(uid)

        return offerRef.get().continueWithTask { initialTask ->
            if (!initialTask.isSuccessful) {
                throw initialTask.exception ?: IllegalStateException("Could not load this offer.")
            }
            val initialOffer = initialTask.result
            require(initialOffer.exists()) { "This offer no longer exists." }
            val sellerUid = initialOffer.getString("sellerId")
                ?: throw IllegalStateException("The offer is missing its seller ID.")
            require(sellerUid != uid) { "You cannot start a Market chat with yourself." }
            val participants = listOf(uid, sellerUid).sorted()
            val conversationId = MarketRepositoryPolicy.conversationId(
                MarketListingKind.OFFER, offerId, participants[0], participants[1]
            )
            val conversationRef = firestore.collection(MARKET_CONVERSATIONS).document(conversationId)

            conversationRef.get().continueWithTask { existingRead ->
                if (existingRead.isSuccessful && existingRead.result.exists()) {
                    requireOfferConversationMatches(
                        existingRead.result, offerId, sellerUid, participants
                    )
                    Tasks.forResult(conversationId)
                } else {
                    firestore.runTransaction { tx ->
                        val offer = tx.get(offerRef)
                        val ownProfile = tx.get(ownProfileRef)
                        require(offer.exists()) { "This offer no longer exists." }
                        require(offer.getString("sellerId") == sellerUid) {
                            "The offer owner changed. Refresh and try again."
                        }
                        require(offer.getString("status") == MarketOfferStatus.ACTIVE.wireValue &&
                            offer.getString("moderation") == MarketModerationStatus.OK.wireValue) {
                            "This offer is no longer available for a new conversation."
                        }
                        val expiresAt = offer.getTimestamp("expiresAt")
                        require(expiresAt != null && nowMillis() < expiresAt.toDate().time) {
                            "This offer has expired."
                        }
                        require(offer.long("reportCount") < REPORTS_TO_HIDE) {
                            "This offer is temporarily unavailable for review."
                        }
                        val blocked = ownProfile.get("blockedUids") as? List<*> ?: emptyList<Any>()
                        require(sellerUid !in blocked) {
                            "Unblock this user before starting a Market conversation."
                        }

                        // No conversation read occurs in this transaction: missing-document gets are
                        // not allowed by the participant-only rule. A conflicting existing record
                        // fails the protected update rule; the continuation below handles a race winner.
                        tx.set(conversationRef, linkedMapOf<String, Any?>(
                            "participants" to participants,
                            "kind" to MarketListingKind.OFFER.wireValue,
                            "listingId" to offerId,
                            "listingOwnerUid" to sellerUid,
                            "offerId" to offerId,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ))
                        conversationId
                    }.continueWithTask { createTask ->
                        if (createTask.isSuccessful) {
                            Tasks.forResult(createTask.result)
                        } else {
                            conversationRef.get().continueWithTask { retryRead ->
                                if (retryRead.isSuccessful && retryRead.result.exists()) {
                                    requireOfferConversationMatches(
                                        retryRead.result, offerId, sellerUid, participants
                                    )
                                    Tasks.forResult(conversationId)
                                } else {
                                    throw createTask.exception
                                        ?: IllegalStateException("Could not create the Market conversation.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // Market chat, text only. It never writes to U2U chatConversations.
    // ------------------------------------------------------------------------

    fun sendMarketMessage(
        conversationId: String,
        recipientUid: String,
        text: String,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToText: String? = null
    ): Task<String> {
        val uid = requireVerifiedUid()
        val normalizedText = text.trim()
        require(normalizedText.length in 1..2000) { "A message must contain 1 to 2,000 characters." }
        require(conversationId.isNotBlank() && recipientUid.isNotBlank() && recipientUid != uid) {
            "A valid Market conversation and recipient are required."
        }
        val hasReply = replyToMessageId != null || replyToSenderName != null || replyToText != null
        if (hasReply) {
            require(!replyToMessageId.isNullOrBlank() && !replyToSenderName.isNullOrBlank() &&
                !replyToText.isNullOrBlank() && replyToSenderName.length <= 60 &&
                replyToText.length <= 2000) {
                "Reply details are incomplete."
            }
        }
        val conversationRef = firestore.collection(MARKET_CONVERSATIONS).document(conversationId)
        val messageRef = conversationRef.collection(MESSAGES).document()

        return firestore.runTransaction { tx ->
            val conversation = tx.get(conversationRef)
            val studentProfile = tx.get(studentProfileRef(uid))
            require(conversation.exists()) { "This Market conversation no longer exists." }
            val participants = (conversation.get("participants") as? List<*>)?.filterIsInstance<String>()
                ?: emptyList()
            require(participants.size == 2 && uid in participants && recipientUid in participants) {
                "You are not a participant in this Market conversation."
            }
            val senderName = requireValidDisplayName(studentProfile.getString("displayName"))
            val messageData = linkedMapOf<String, Any?>(
                "senderUid" to uid,
                "recipientUid" to recipientUid,
                "senderName" to senderName,
                "text" to normalizedText,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (hasReply) {
                messageData["replyToMessageId"] = replyToMessageId
                messageData["replyToSenderName"] = replyToSenderName
                messageData["replyToText"] = replyToText
            }
            tx.set(messageRef, messageData)
            tx.update(conversationRef, mapOf(
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastMessage" to mapOf(
                    "text" to normalizedText.take(100),
                    "senderUid" to uid,
                    "at" to FieldValue.serverTimestamp()
                )
            ))
            messageRef.id
        }
    }

    private fun requireOfferConversationMatches(
        snapshot: DocumentSnapshot,
        offerId: String,
        sellerUid: String,
        participants: List<String>
    ) {
        val saved = MarketConverters.conversation(snapshot)
        require(saved != null && saved.kind == MarketListingKind.OFFER &&
            saved.listingId == offerId && saved.listingOwnerUid == sellerUid &&
            saved.participants == participants) {
            "A conflicting Market conversation already exists."
        }
    }

    fun markConversationRead(conversationId: String): Task<Boolean> {
        val uid = requireVerifiedUid()
        val ref = firestore.collection(MARKET_CONVERSATIONS).document(conversationId)
        return firestore.runTransaction { tx ->
            val conversation = tx.get(ref)
            require(conversation.exists()) { "This Market conversation no longer exists." }
            val participants = (conversation.get("participants") as? List<*>)?.filterIsInstance<String>()
                ?: emptyList()
            require(uid in participants) { "You are not a participant in this Market conversation." }

            val existing = conversation.get("lastReadAt") as? Map<*, *> ?: emptyMap<Any, Any>()
            val markers = linkedMapOf<String, Any>()
            for (participant in participants) {
                if (participant != uid && existing[participant] is Timestamp) {
                    markers[participant] = existing[participant] as Timestamp
                }
            }
            markers[uid] = FieldValue.serverTimestamp()
            tx.update(ref, mapOf(
                "lastReadAt" to markers,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            true
        }
    }

    // ------------------------------------------------------------------------
    // Notices, support and appeals
    // ------------------------------------------------------------------------

    fun markNoticeRead(noticeId: String): Task<Void> {
        val uid = requireVerifiedUid()
        require(noticeId.isNotBlank()) { "A notice is required." }
        return firestore.collection(USERS).document(uid).collection(NOTICES).document(noticeId)
            .update("read", true)
    }

    fun createSupportTicket(draft: MarketSupportDraft): Task<String> {
        val uid = requireVerifiedUid()
        require(draft.type != MarketSupportType.APPEAL) { "Use submitAppeal to create an appeal ticket." }
        validateSupportDraft(draft)
        val ticketRef = firestore.collection(SUPPORT).document()
        val profileRef = marketProfileRef(uid)

        return firestore.runTransaction { tx ->
            val profile = tx.get(profileRef)
            require(profile.exists()) { "Create your Market profile before contacting support." }
            require(profile.long("openSupportTickets") < MAX_OPEN_SUPPORT_TICKETS) {
                "You have reached the 5 open support ticket limit."
            }
            val data = linkedMapOf<String, Any?>(
                "userId" to uid,
                "type" to draft.type.wireValue,
                "subject" to draft.subject.trim(),
                "body" to draft.body.trim(),
                "status" to MarketSupportStatus.OPEN.wireValue,
                "replies" to emptyList<Map<String, Any>>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            draft.relatedType?.let { data["relatedType"] = it }
            draft.relatedId?.let { data["relatedId"] = it }
            tx.set(ticketRef, data)
            tx.update(profileRef, mapOf(
                "openSupportTickets" to profile.long("openSupportTickets").toInt() + 1,
                "lastMutation" to mutationMap("support", "create", ticketRef.id),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            ticketRef.id
        }
    }

    fun submitAppeal(noticeId: String, subject: String, body: String): Task<String> {
        val uid = requireVerifiedUid()
        val normalizedSubject = subject.trim()
        val normalizedBody = body.trim()
        require(normalizedSubject.length in 5..80) { "The appeal subject must be 5 to 80 characters." }
        require(normalizedBody.length in 10..500) { "The appeal must be 10 to 500 characters." }
        require(noticeId.isNotBlank()) { "A notice is required for an appeal." }

        val noticeRef = firestore.collection(USERS).document(uid).collection(NOTICES).document(noticeId)
        val ticketId = MarketRepositoryPolicy.appealTicketId(noticeId, uid)
        val ticketRef = firestore.collection(SUPPORT).document(ticketId)
        val profileRef = marketProfileRef(uid)

        return firestore.runTransaction { tx ->
            val notice = tx.get(noticeRef)
            val profile = tx.get(profileRef)
            require(notice.exists()) { "This notice no longer exists." }
            require(notice.getBoolean("canAppeal") == true && notice.getBoolean("appealed") == false) {
                "This notice cannot be appealed again."
            }
            require(profile.exists()) { "Your Market profile is missing." }
            require(profile.long("openSupportTickets") < MAX_OPEN_SUPPORT_TICKETS) {
                "You have reached the 5 open support ticket limit."
            }

            tx.set(ticketRef, linkedMapOf<String, Any?>(
                "userId" to uid,
                "type" to MarketSupportType.APPEAL.wireValue,
                "subject" to normalizedSubject,
                "body" to normalizedBody,
                "relatedType" to "notice",
                "relatedId" to noticeId,
                "status" to MarketSupportStatus.OPEN.wireValue,
                "replies" to emptyList<Map<String, Any>>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            tx.update(noticeRef, "appealed", true)
            tx.update(profileRef, mapOf(
                "openSupportTickets" to profile.long("openSupportTickets").toInt() + 1,
                "lastMutation" to mutationMap("support", "create", ticketId),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            ticketId
        }
    }

    fun replyToOwnSupportTicket(ticketId: String, text: String): Task<Boolean> {
        val uid = requireVerifiedUid()
        val normalizedText = text.trim()
        require(normalizedText.length in 1..1000) { "A support reply must contain 1 to 1,000 characters." }
        val ref = firestore.collection(SUPPORT).document(ticketId)

        return firestore.runTransaction { tx ->
            val ticket = tx.get(ref)
            require(ticket.exists()) { "This support ticket no longer exists." }
            require(ticket.getString("userId") == uid) { "This support ticket belongs to another account." }
            require(ticket.getString("status") != MarketSupportStatus.RESOLVED.wireValue) {
                "This support ticket is already resolved."
            }
            val replies = ticket.get("replies") as? List<*> ?: emptyList<Any>()
            require(replies.size < MAX_SUPPORT_REPLIES) { "This support conversation has reached its reply limit." }
            val updatedReplies = normalizeReplyMaps(replies)
            updatedReplies.add(mapOf(
                "from" to "user",
                "text" to normalizedText,
                "at" to MarketRepositoryPolicy.safeClientTimestamp(nowMillis())
            ))
            tx.update(ref, mapOf(
                "replies" to updatedReplies,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            true
        }
    }

    // ------------------------------------------------------------------------
    // Admin mutations. Every affected-user action includes a notice and audit log.
    // ------------------------------------------------------------------------

    fun adminUpdateMarketConfig(config: MarketConfig): Task<Boolean> {
        val adminUid = requireVerifiedUid()
        require(config.rankWeights.newness >= 0.0 && config.rankWeights.rating >= 0.0 &&
            config.rankWeights.match >= 0.0 &&
            config.rankWeights.newness + config.rankWeights.rating + config.rankWeights.match == 100.0) {
            "Ranking weights must be non-negative and add up to 100."
        }
        require(config.blockedWords.size <= 300 && config.announcement.text.length <= 500) {
            "The Market settings exceed the permitted size."
        }
        val ref = firestore.collection(CONFIG).document(MARKET_CONFIG_ID)
        val logRef = firestore.collection(ADMIN_LOGS).document()
        val configData = MarketConverters.toFirestoreMap(config).toMutableMap().apply {
            put("lastAuditLogId", logRef.id)
        }
        return firestore.runTransaction { tx ->
            tx.set(ref, configData)
            tx.set(logRef, adminLogMap(
                adminUid, "update_market_config", "config", MARKET_CONFIG_ID,
                "Market settings updated", "Announcement, blocked words, or ranking weights changed."
            ))
            true
        }
    }

    fun adminSetReportStatus(
        reportId: String,
        status: MarketReportStatus,
        reason: String,
        note: String = ""
    ): Task<Boolean> {
        val adminUid = requireVerifiedUid()
        val reportRef = firestore.collection(REPORTS).document(reportId)
        val logRef = firestore.collection(ADMIN_LOGS).document()
        return firestore.runTransaction { tx ->
            val report = tx.get(reportRef)
            require(report.exists()) { "This report no longer exists." }
            val reporterUid = report.getString("reporterId")
                ?: throw IllegalStateException("The report is missing its reporter ID.")
            val noticeRef = firestore.collection(USERS).document(reporterUid)
                .collection(NOTICES).document()
            tx.update(reportRef, mapOf(
                "status" to status.wireValue,
                "lastAuditLogId" to logRef.id,
                "lastAdminNoticeId" to noticeRef.id
            ))
            tx.set(logRef, adminLogMap(adminUid, "set_report_status", "report", reportId, reason, note))
            createNoticeInTransaction(
                tx, reporterUid, noticeRef.id, MarketNoticeType.ACTIVITY,
                "Report updated", "Your report status is now " + status.wireValue.replace('_', ' ') + ".",
                relatedType = "report", relatedId = reportId, canAppeal = false
            )
            true
        }
    }

    fun adminModerateTask(
        taskId: String,
        moderation: MarketModerationStatus? = null,
        pinned: Boolean? = null,
        reason: String,
        note: String = ""
    ): Task<Boolean> {
        val adminUid = requireVerifiedUid()
        val taskRef = firestore.collection(TASKS).document(taskId)
        val logRef = firestore.collection(ADMIN_LOGS).document()

        return firestore.runTransaction { tx ->
            val task = tx.get(taskRef)
            require(task.exists()) { "This task no longer exists." }
            val ownerUid = task.getString("posterId")
                ?: throw IllegalStateException("The task is missing its poster ID.")
            val beforeMod = task.getString("moderation") ?: MarketModerationStatus.OK.wireValue
            val afterMod = moderation?.wireValue ?: beforeMod
            val beforePinned = task.getBoolean("pinned") ?: false
            val afterPinned = pinned ?: beforePinned
            if (beforeMod == afterMod && beforePinned == afterPinned) return@runTransaction false

            val open = task.getString("status") == MarketTaskStatus.OPEN.wireValue
            val deactivate = open &&
                beforeMod in listOf(MarketModerationStatus.OK.wireValue, MarketModerationStatus.UNDER_REVIEW.wireValue) &&
                afterMod in listOf(MarketModerationStatus.HIDDEN.wireValue, MarketModerationStatus.REMOVED.wireValue)
            val restore = open &&
                beforeMod in listOf(MarketModerationStatus.HIDDEN.wireValue, MarketModerationStatus.REMOVED.wireValue) &&
                afterMod == MarketModerationStatus.OK.wireValue
            val profileRef = marketProfileRef(ownerUid)
            val ownerProfile = if (deactivate || restore) tx.get(profileRef) else null
            val noticeRef = firestore.collection(USERS).document(ownerUid).collection(NOTICES).document()
            val listingPatch = linkedMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastAuditLogId" to logRef.id,
                "lastAdminNoticeId" to noticeRef.id
            )
            if (moderation != null) listingPatch["moderation"] = moderation.wireValue
            if (pinned != null) listingPatch["pinned"] = pinned
            tx.update(taskRef, listingPatch)

            if (deactivate || restore) {
                val profile = ownerProfile ?: throw IllegalStateException("The poster's Market profile is missing.")
                val count = profile.long("openTasks").toInt()
                require(if (deactivate) count > 0 else count < MAX_OPEN_TASKS) {
                    "The poster's open task counter prevents this moderation change."
                }
                tx.update(profileRef, mapOf(
                    "openTasks" to if (deactivate) count - 1 else count + 1,
                    "lastMutation" to mutationMap(
                        "task", if (deactivate) "moderate_deactivate" else "moderate_restore", taskId
                    ),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }

            tx.set(logRef, adminLogMap(adminUid, "moderate_task", "task", taskId, reason, note))
            val removal = afterMod in listOf(MarketModerationStatus.HIDDEN.wireValue, MarketModerationStatus.REMOVED.wireValue) &&
                beforeMod != afterMod
            val noticeBody = if (beforeMod != afterMod) {
                "Your task moderation status is now " + afterMod.replace('_', ' ') + ". Reason: " + reason.trim()
            } else if (!beforePinned && afterPinned) {
                "Your task was pinned in Market."
            } else {
                "Your task was unpinned in Market."
            }
            createNoticeInTransaction(
                tx, ownerUid, noticeRef.id,
                if (removal) MarketNoticeType.REMOVAL else MarketNoticeType.ACTIVITY,
                if (removal) "Task moderation update" else "Task updated",
                noticeBody, relatedType = "task", relatedId = taskId, canAppeal = removal
            )
            true
        }
    }

    fun adminModerateOffer(
        offerId: String,
        moderation: MarketModerationStatus? = null,
        pinned: Boolean? = null,
        reason: String,
        note: String = ""
    ): Task<Boolean> {
        val adminUid = requireVerifiedUid()
        val offerRef = firestore.collection(OFFERS).document(offerId)
        val logRef = firestore.collection(ADMIN_LOGS).document()

        return firestore.runTransaction { tx ->
            val offer = tx.get(offerRef)
            require(offer.exists()) { "This offer no longer exists." }
            val ownerUid = offer.getString("sellerId")
                ?: throw IllegalStateException("The offer is missing its seller ID.")
            val beforeMod = offer.getString("moderation") ?: MarketModerationStatus.OK.wireValue
            val afterMod = moderation?.wireValue ?: beforeMod
            val beforePinned = offer.getBoolean("pinned") ?: false
            val afterPinned = pinned ?: beforePinned
            if (beforeMod == afterMod && beforePinned == afterPinned) return@runTransaction false

            val active = offer.getString("status") == MarketOfferStatus.ACTIVE.wireValue
            val deactivate = active &&
                beforeMod in listOf(MarketModerationStatus.OK.wireValue, MarketModerationStatus.UNDER_REVIEW.wireValue) &&
                afterMod in listOf(MarketModerationStatus.HIDDEN.wireValue, MarketModerationStatus.REMOVED.wireValue)
            val restore = active &&
                beforeMod in listOf(MarketModerationStatus.HIDDEN.wireValue, MarketModerationStatus.REMOVED.wireValue) &&
                afterMod == MarketModerationStatus.OK.wireValue
            val profileRef = marketProfileRef(ownerUid)
            val ownerProfile = if (deactivate || restore) tx.get(profileRef) else null
            val noticeRef = firestore.collection(USERS).document(ownerUid).collection(NOTICES).document()
            val listingPatch = linkedMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastAuditLogId" to logRef.id,
                "lastAdminNoticeId" to noticeRef.id
            )
            if (moderation != null) listingPatch["moderation"] = moderation.wireValue
            if (pinned != null) listingPatch["pinned"] = pinned
            tx.update(offerRef, listingPatch)

            if (deactivate || restore) {
                val profile = ownerProfile ?: throw IllegalStateException("The seller's Market profile is missing.")
                val count = profile.long("activeOffers").toInt()
                require(if (deactivate) count > 0 else count < MAX_ACTIVE_OFFERS) {
                    "The seller's active offer counter prevents this moderation change."
                }
                tx.update(profileRef, mapOf(
                    "activeOffers" to if (deactivate) count - 1 else count + 1,
                    "lastMutation" to mutationMap(
                        "offer", if (deactivate) "moderate_deactivate" else "moderate_restore", offerId
                    ),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }

            tx.set(logRef, adminLogMap(adminUid, "moderate_offer", "offer", offerId, reason, note))
            val removal = afterMod in listOf(MarketModerationStatus.HIDDEN.wireValue, MarketModerationStatus.REMOVED.wireValue) &&
                beforeMod != afterMod
            val noticeBody = if (beforeMod != afterMod) {
                "Your offer moderation status is now " + afterMod.replace('_', ' ') + ". Reason: " + reason.trim()
            } else if (!beforePinned && afterPinned) {
                "Your offer was pinned in Market."
            } else {
                "Your offer was unpinned in Market."
            }
            createNoticeInTransaction(
                tx, ownerUid, noticeRef.id,
                if (removal) MarketNoticeType.REMOVAL else MarketNoticeType.ACTIVITY,
                if (removal) "Offer moderation update" else "Offer updated",
                noticeBody, relatedType = "offer", relatedId = offerId, canAppeal = removal
            )
            true
        }
    }

    fun adminWarnUser(
        targetUid: String,
        reason: String,
        note: String = "",
        noticeType: MarketNoticeType = MarketNoticeType.WARNING,
        canAppeal: Boolean = false
    ): Task<Boolean> {
        val adminUid = requireVerifiedUid()
        require(targetUid.isNotBlank() && reason.isNotBlank()) { "A target user and reason are required." }
        require(noticeType == MarketNoticeType.WARNING || noticeType == MarketNoticeType.REMOVAL) {
            "This operation supports warning or removal notices."
        }
        val profileRef = marketProfileRef(targetUid)
        val logRef = firestore.collection(ADMIN_LOGS).document()

        return firestore.runTransaction { tx ->
            val profile = tx.get(profileRef)
            require(profile.exists()) { "The target user does not have a Market profile." }
            val warnings = profile.long("warnings").toInt()
            require(warnings < 1000) { "The warning counter has reached its maximum." }
            tx.update(profileRef, mapOf(
                "warnings" to warnings + 1,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            tx.set(logRef, adminLogMap(adminUid, "warn_user", "user", targetUid, reason, note))
            createNoticeInTransaction(
                tx, targetUid, null, noticeType,
                if (noticeType == MarketNoticeType.WARNING) "Market warning" else "Market removal notice",
                reason.trim(), relatedType = null, relatedId = null, canAppeal = canAppeal
            )
            true
        }
    }

    /**
     * Hides currently listed (OK/under_review) tasks/offers and clears their counters.
     * Already hidden/removed items released their slots earlier and do not need another
     * listing counter mutation. A single rule-valid transaction includes logs/notices.
     */
    fun adminSuspendUser(
        targetUid: String,
        durationDays: Int?,
        reason: String,
        note: String = ""
    ): Task<Boolean> {
        val adminUid = requireVerifiedUid()
        require(targetUid.isNotBlank() && targetUid != adminUid) { "Choose a valid target account." }
        require(durationDays == null || durationDays in listOf(7, 30)) {
            "Suspension duration must be 7 or 30 days; use null to ban."
        }
        require(reason.isNotBlank()) { "A moderation reason is required." }

        val profileRef = marketProfileRef(targetUid)
        val tasksQuery = firestore.collection(TASKS)
            .whereEqualTo("posterId", targetUid)
            .whereEqualTo("status", MarketTaskStatus.OPEN.wireValue)
        val offersQuery = firestore.collection(OFFERS)
            .whereEqualTo("sellerId", targetUid)
            .whereEqualTo("status", MarketOfferStatus.ACTIVE.wireValue)
        val logRef = firestore.collection(ADMIN_LOGS).document()
        val noticeRef = firestore.collection(USERS).document(targetUid).collection(NOTICES).document()

        // Firestore's client transaction API supports document reads, not query reads.
        // Discover candidates first, then re-read each candidate reference inside the
        // transaction and revalidate its current owner/status/moderation before writing.
        return tasksQuery.get().continueWithTask { tasksTask ->
            if (!tasksTask.isSuccessful) {
                throw tasksTask.exception ?: IllegalStateException("Could not load the user's tasks.")
            }
            val taskRefs = tasksTask.result.documents.map { it.reference }
            offersQuery.get().continueWithTask { offersTask ->
                if (!offersTask.isSuccessful) {
                    throw offersTask.exception ?: IllegalStateException("Could not load the user's offers.")
                }
                val offerRefs = offersTask.result.documents.map { it.reference }
                firestore.runTransaction { tx ->
                    val profile = tx.get(profileRef)
                    val taskSnapshots = taskRefs.map { ref -> tx.get(ref) }
                    val offerSnapshots = offerRefs.map { ref -> tx.get(ref) }
                    require(profile.exists()) { "The target user does not have a Market profile." }

                    val taskDocs = taskSnapshots.filter { snapshot ->
                        snapshot.exists() &&
                            snapshot.getString("posterId") == targetUid &&
                            snapshot.getString("status") == MarketTaskStatus.OPEN.wireValue &&
                            snapshot.getString("moderation") in listOf(
                                MarketModerationStatus.OK.wireValue,
                                MarketModerationStatus.UNDER_REVIEW.wireValue
                            )
                    }
                    val offerDocs = offerSnapshots.filter { snapshot ->
                        snapshot.exists() &&
                            snapshot.getString("sellerId") == targetUid &&
                            snapshot.getString("status") == MarketOfferStatus.ACTIVE.wireValue &&
                            snapshot.getString("moderation") in listOf(
                                MarketModerationStatus.OK.wireValue,
                                MarketModerationStatus.UNDER_REVIEW.wireValue
                            )
                    }
                    require(taskDocs.size + offerDocs.size <= 450) {
                        "This account has too many active listings for one safe moderation transaction."
                    }
                    val banned = durationDays == null
                    val until = durationDays?.let {
                        Timestamp(Date(nowMillis() + it * MarketRepositoryPolicy.DAY_MS))
                    }

                    for (snapshot in taskDocs) {
                        tx.update(snapshot.reference, mapOf(
                            "moderation" to MarketModerationStatus.HIDDEN.wireValue,
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "lastAuditLogId" to logRef.id,
                            "lastAdminNoticeId" to noticeRef.id
                        ))
                    }
                    for (snapshot in offerDocs) {
                        tx.update(snapshot.reference, mapOf(
                            "moderation" to MarketModerationStatus.HIDDEN.wireValue,
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "lastAuditLogId" to logRef.id,
                            "lastAdminNoticeId" to noticeRef.id
                        ))
                    }
                    tx.update(profileRef, mapOf(
                        "status" to if (banned) MarketProfileStatus.BANNED.wireValue else MarketProfileStatus.SUSPENDED.wireValue,
                        "suspendedUntil" to until,
                        "openTasks" to 0,
                        "activeOffers" to 0,
                        "lastMutation" to mutationMap("user", "suspend", targetUid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                    tx.set(logRef, adminLogMap(
                        adminUid, if (banned) "ban_user" else "suspend_user", "user", targetUid, reason, note
                    ))
                    createNoticeInTransaction(
                        tx, targetUid, noticeRef.id, MarketNoticeType.REMOVAL,
                        if (banned) "Market account banned" else "Market account suspended",
                        reason.trim() + if (durationDays == null) "" else " Suspension duration: " + durationDays + " days.",
                        relatedType = null, relatedId = null, canAppeal = true
                    )
                    true
                }
            }
        }
    }

    fun adminLiftSuspension(targetUid: String, reason: String, note: String = ""): Task<Boolean> {
        val adminUid = requireVerifiedUid()
        val profileRef = marketProfileRef(targetUid)
        val logRef = firestore.collection(ADMIN_LOGS).document()
        return firestore.runTransaction { tx ->
            val profile = tx.get(profileRef)
            require(profile.exists()) { "The target user does not have a Market profile." }
            require(profile.getString("status") != MarketProfileStatus.BANNED.wireValue) {
                "A ban must be lifted through the ban-review workflow."
            }
            tx.update(profileRef, mapOf(
                "status" to MarketProfileStatus.ACTIVE.wireValue,
                "suspendedUntil" to null,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            tx.set(logRef, adminLogMap(adminUid, "lift_suspension", "user", targetUid, reason, note))
            createNoticeInTransaction(
                tx, targetUid, null, MarketNoticeType.ACTIVITY,
                "Market suspension lifted",
                reason.ifBlank { "Your Market posting access has been restored." },
                relatedType = null, relatedId = null, canAppeal = false
            )
            true
        }
    }

    fun adminReplyToSupportTicket(
        ticketId: String,
        replyText: String,
        reason: String = "Support reply",
        note: String = ""
    ): Task<Boolean> {
        val adminUid = requireVerifiedUid()
        val normalizedText = replyText.trim()
        require(normalizedText.length in 1..1000) { "A support reply must contain 1 to 1,000 characters." }
        val ticketRef = firestore.collection(SUPPORT).document(ticketId)
        val logRef = firestore.collection(ADMIN_LOGS).document()
        return firestore.runTransaction { tx ->
            val ticket = tx.get(ticketRef)
            require(ticket.exists()) { "This support ticket no longer exists." }
            require(ticket.getString("status") != MarketSupportStatus.RESOLVED.wireValue) {
                "This support ticket is already resolved."
            }
            val userId = ticket.getString("userId")
                ?: throw IllegalStateException("The support ticket is missing its owner.")
            val replies = ticket.get("replies") as? List<*> ?: emptyList<Any>()
            require(replies.size < MAX_SUPPORT_REPLIES) { "This support conversation has reached its reply limit." }
            val updatedReplies = normalizeReplyMaps(replies)
            updatedReplies.add(mapOf(
                "from" to "admin",
                "text" to normalizedText,
                "at" to MarketRepositoryPolicy.safeClientTimestamp(nowMillis())
            ))
            val noticeRef = firestore.collection(USERS).document(userId)
                .collection(NOTICES).document()
            tx.update(ticketRef, mapOf(
                "status" to MarketSupportStatus.REPLIED.wireValue,
                "replies" to updatedReplies,
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastAuditLogId" to logRef.id,
                "lastAdminNoticeId" to noticeRef.id
            ))
            tx.set(logRef, adminLogMap(adminUid, "reply_support_ticket", "support", ticketId, reason, note))
            createNoticeInTransaction(
                tx, userId, noticeRef.id, MarketNoticeType.SUPPORT_REPLY,
                "Support replied", normalizedText,
                relatedType = "support", relatedId = ticketId, canAppeal = false
            )
            true
        }
    }

    /**
     * Approves or rejects an appeal. Listing restoration and support resolution
     * deliberately use two atomic transactions: Firestore rules allow only one
     * lastMutation kind on the owner's profile per update, so task/offer and support
     * counter mutations cannot be combined into the same profile update.
     */
    fun adminDecideAppeal(
        ticketId: String,
        approved: Boolean,
        reason: String,
        note: String = ""
    ): Task<Boolean> {
        val adminUid = requireVerifiedUid()
        require(ticketId.isNotBlank()) { "An appeal ticket is required." }
        require(reason.isNotBlank()) { "Provide the appeal decision reason." }
        val ticketRef = firestore.collection(SUPPORT).document(ticketId)
        val restoreLogRef = firestore.collection(ADMIN_LOGS).document()

        val restoreStep = firestore.runTransaction { tx ->
            val ticket = tx.get(ticketRef)
            require(ticket.exists()) { "This appeal no longer exists." }
            require(ticket.getString("type") == MarketSupportType.APPEAL.wireValue) {
                "This ticket is not an appeal."
            }
            require(ticket.getString("status") != MarketSupportStatus.RESOLVED.wireValue) {
                "This appeal is already resolved."
            }
            val userId = ticket.getString("userId")
                ?: throw IllegalStateException("The appeal is missing its owner.")
            if (!approved) return@runTransaction false
            require(ticket.getString("relatedType") == "notice") { "The appeal is not linked to a Market notice." }
            val noticeId = ticket.getString("relatedId")
                ?: throw IllegalStateException("The appeal is missing its notice ID.")
            val noticeRef = firestore.collection(USERS).document(userId).collection(NOTICES).document(noticeId)
            val notice = tx.get(noticeRef)
            require(notice.exists()) { "The notice linked to this appeal no longer exists." }
            val linkedType = notice.getString("relatedType") ?: return@runTransaction false
            val linkedId = notice.getString("relatedId") ?: return@runTransaction false
            if (linkedId.isBlank() || linkedType !in listOf("task", "offer")) {
                return@runTransaction false
            }

            val isTask = linkedType == "task"
            val listingRef = if (isTask) firestore.collection(TASKS).document(linkedId)
                else firestore.collection(OFFERS).document(linkedId)
            val listing = tx.get(listingRef)
            require(listing.exists()) { "The listing linked to this appeal no longer exists." }
            val ownerUid = if (isTask) listing.getString("posterId") else listing.getString("sellerId")
            require(ownerUid == userId) { "The appealed listing does not belong to the ticket owner." }
            val beforeMod = listing.getString("moderation") ?: MarketModerationStatus.OK.wireValue
            if (beforeMod == MarketModerationStatus.OK.wireValue) return@runTransaction false
            require(beforeMod in listOf(
                MarketModerationStatus.HIDDEN.wireValue,
                MarketModerationStatus.REMOVED.wireValue,
                MarketModerationStatus.UNDER_REVIEW.wireValue
            )) { "The listing has an unsupported moderation state." }
            val listingStatus = listing.getString("status") ?: ""
            if (!isTask && listingStatus != MarketOfferStatus.ACTIVE.wireValue &&
                beforeMod in listOf(MarketModerationStatus.HIDDEN.wireValue, MarketModerationStatus.REMOVED.wireValue)
            ) {
                throw IllegalStateException("Only an active offer can be restored by this appeal operation.")
            }
            val counterNeeded = beforeMod in listOf(
                MarketModerationStatus.HIDDEN.wireValue, MarketModerationStatus.REMOVED.wireValue
            ) && (if (isTask) listingStatus == MarketTaskStatus.OPEN.wireValue
                else listingStatus == MarketOfferStatus.ACTIVE.wireValue)
            val ownerProfileRef = marketProfileRef(userId)
            val ownerProfile = if (counterNeeded) tx.get(ownerProfileRef) else null
            val nextCounter = if (counterNeeded) {
                val field = if (isTask) "openTasks" else "activeOffers"
                val count = ownerProfile!!.long(field).toInt()
                val limit = if (isTask) MAX_OPEN_TASKS else MAX_ACTIVE_OFFERS
                require(count < limit) { "The owner's listing limit prevents restoration." }
                count + 1
            } else null

            // All reads are complete before any writes.
            tx.update(listingRef, mapOf(
                "moderation" to MarketModerationStatus.OK.wireValue,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            if (counterNeeded && ownerProfile != null && nextCounter != null) {
                val field = if (isTask) "openTasks" else "activeOffers"
                tx.update(ownerProfileRef, mapOf(
                    field to nextCounter,
                    "lastMutation" to mutationMap(
                        if (isTask) "task" else "offer", "moderate_restore", linkedId
                    ),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            }
            tx.set(restoreLogRef, adminLogMap(
                adminUid, "approve_appeal_restore", linkedType, linkedId, reason, note
            ))
            createNoticeInTransaction(
                tx, userId, null, MarketNoticeType.APPEAL_RESULT,
                "Appeal approved", "Your appeal was approved and the listing was restored.",
                relatedType = linkedType, relatedId = linkedId, canAppeal = false
            )
            true
        }

        return restoreStep.continueWithTask { restoreResult ->
            if (!restoreResult.isSuccessful) {
                throw restoreResult.exception ?: IllegalStateException("Could not process the appeal.")
            }
            val listingRestored = restoreResult.result == true
            val resolveLogRef = firestore.collection(ADMIN_LOGS).document()
            firestore.runTransaction { tx ->
                val ticket = tx.get(ticketRef)
                require(ticket.exists()) { "This appeal no longer exists." }
                require(ticket.getString("type") == MarketSupportType.APPEAL.wireValue) {
                    "This ticket is not an appeal."
                }
                require(ticket.getString("status") != MarketSupportStatus.RESOLVED.wireValue) {
                    "This appeal is already resolved."
                }
                val userId = ticket.getString("userId")
                    ?: throw IllegalStateException("The appeal is missing its owner.")
                val profileRef = marketProfileRef(userId)
                val profile = tx.get(profileRef)
                require(profile.exists()) { "The appeal owner's Market profile is missing." }
                val count = profile.long("openSupportTickets").toInt()
                require(count > 0) { "The owner's support ticket counter needs attention." }
                val resolveNoticeRef = firestore.collection(USERS).document(userId)
                    .collection(NOTICES).document()

                tx.update(ticketRef, mapOf(
                    "status" to MarketSupportStatus.RESOLVED.wireValue,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "lastAuditLogId" to resolveLogRef.id,
                    "lastAdminNoticeId" to resolveNoticeRef.id
                ))
                tx.update(profileRef, mapOf(
                    "openSupportTickets" to count - 1,
                    "lastMutation" to mutationMap("support", "resolve", ticketId),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
                tx.set(resolveLogRef, adminLogMap(
                    adminUid, if (approved) "approve_appeal" else "reject_appeal",
                    "support", ticketId, reason, note
                ))
                createNoticeInTransaction(
                    tx, userId, resolveNoticeRef.id, MarketNoticeType.APPEAL_RESULT,
                    if (approved) "Appeal approved" else "Appeal rejected",
                    when {
                        approved && listingRestored -> "Your appeal was approved and the listing was restored. Reason: " + reason.trim()
                        approved -> "Your appeal was approved. Reason: " + reason.trim()
                        else -> "Your appeal was rejected. Reason: " + reason.trim()
                    },
                    relatedType = "support", relatedId = ticketId, canAppeal = false
                )
                true
            }
        }
    }

    fun adminResolveSupportTicket(
        ticketId: String,
        reason: String = "Support ticket resolved",
        note: String = ""
    ): Task<Boolean> {
        val adminUid = requireVerifiedUid()
        val ticketRef = firestore.collection(SUPPORT).document(ticketId)
        val logRef = firestore.collection(ADMIN_LOGS).document()
        return firestore.runTransaction { tx ->
            val ticket = tx.get(ticketRef)
            require(ticket.exists()) { "This support ticket no longer exists." }
            require(ticket.getString("type") != MarketSupportType.APPEAL.wireValue) {
                "Use adminDecideAppeal to approve or reject an appeal."
            }
            require(ticket.getString("status") != MarketSupportStatus.RESOLVED.wireValue) {
                "This support ticket is already resolved."
            }
            val userId = ticket.getString("userId")
                ?: throw IllegalStateException("The support ticket is missing its owner.")
            val profileRef = marketProfileRef(userId)
            val profile = tx.get(profileRef)
            require(profile.exists()) { "The ticket owner's Market profile is missing." }
            val count = profile.long("openSupportTickets").toInt()
            require(count > 0) { "The owner's support ticket counter needs attention." }
            val noticeRef = firestore.collection(USERS).document(userId)
                .collection(NOTICES).document()

            tx.update(ticketRef, mapOf(
                "status" to MarketSupportStatus.RESOLVED.wireValue,
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastAuditLogId" to logRef.id,
                "lastAdminNoticeId" to noticeRef.id
            ))
            tx.update(profileRef, mapOf(
                "openSupportTickets" to count - 1,
                "lastMutation" to mutationMap("support", "resolve", ticketId),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
            tx.set(logRef, adminLogMap(adminUid, "resolve_support_ticket", "support", ticketId, reason, note))
            createNoticeInTransaction(
                tx, userId, noticeRef.id, MarketNoticeType.SUPPORT_REPLY,
                "Support ticket resolved", reason.ifBlank { "Your support ticket has been resolved." },
                relatedType = "support", relatedId = ticketId, canAppeal = false
            )
            true
        }
    }

    // ------------------------------------------------------------------------
    // Shared validation, encoding and mutation helpers
    // ------------------------------------------------------------------------

    private fun taskEditMap(edit: MarketTaskEdit): LinkedHashMap<String, Any> {
        require(!(edit.clearCourseTag && edit.courseTag != null)) {
            "A course tag cannot be set and cleared at the same time."
        }
        require(!(edit.clearBudgetAmount && edit.budgetAmount != null)) {
            "A budget amount cannot be set and cleared at the same time."
        }
        val values = linkedMapOf<String, Any>()
        edit.title?.let { values["title"] = it }
        edit.category?.let { values["category"] = it }
        edit.description?.let { values["description"] = it }
        edit.budgetType?.let { values["budgetType"] = it.wireValue }
        edit.deadline?.let { values["deadline"] = it }
        edit.skills?.let { values["skills"] = it }
        edit.courseTag?.let { values["courseTag"] = it }
        if (edit.clearCourseTag) values["courseTag"] = FieldValue.delete()
        edit.budgetAmount?.let { values["budgetAmount"] = it }
        if (edit.clearBudgetAmount) values["budgetAmount"] = FieldValue.delete()
        return values
    }

    private fun offerEditMap(edit: MarketOfferEdit): LinkedHashMap<String, Any> {
        require(!(edit.clearCourseTag && edit.courseTag != null)) {
            "A course tag cannot be set and cleared at the same time."
        }
        require(!(edit.clearPriceAmount && edit.priceAmount != null)) {
            "A price amount cannot be set and cleared at the same time."
        }
        require(!(edit.clearThumbnail && edit.thumbnail != null)) {
            "A thumbnail cannot be set and cleared at the same time."
        }
        val values = linkedMapOf<String, Any>()
        edit.title?.let { values["title"] = it }
        edit.category?.let { values["category"] = it }
        edit.description?.let { values["description"] = it }
        edit.priceType?.let { values["priceType"] = it.wireValue }
        edit.courseTag?.let { values["courseTag"] = it }
        if (edit.clearCourseTag) values["courseTag"] = FieldValue.delete()
        edit.priceAmount?.let { values["priceAmount"] = it }
        if (edit.clearPriceAmount) values["priceAmount"] = FieldValue.delete()
        edit.thumbnail?.let { values["thumbnail"] = it }
        if (edit.clearThumbnail) values["thumbnail"] = FieldValue.delete()
        return values
    }

    private fun validateTaskDraft(draft: MarketTaskDraft) {
        val map = linkedMapOf<String, Any?>(
            "title" to draft.title,
            "category" to draft.category,
            "description" to draft.description,
            "budgetType" to draft.budgetType.wireValue,
            "deadline" to draft.deadline,
            "skills" to draft.skills
        )
        draft.courseTag?.let { map["courseTag"] = it }
        draft.budgetAmount?.let { map["budgetAmount"] = it }
        validateTaskContentMap(map)
        require(draft.deadline.toDate().time > nowMillis()) { "Choose a future task deadline." }
    }

    private fun validateTaskContentMap(data: Map<String, Any?>) {
        val title = data["title"] as? String
        val category = data["category"] as? String
        val description = data["description"] as? String
        val budgetType = (data["budgetType"] as? String)?.let { marketWireEnum<MarketBudgetType>(it) }
        val deadline = data["deadline"] as? Timestamp
        val skills = data["skills"] as? List<*>
        require(title != null && title.length in 5..80) { "Task title must be 5 to 80 characters." }
        require(category != null && category.length in 1..60) { "Task category must be 1 to 60 characters." }
        require(description != null && description.length in 20..500) { "Task description must be 20 to 500 characters." }
        require(budgetType != null) { "The task budget type is invalid." }
        require(deadline != null) { "Choose a task deadline." }
        require(skills != null && skills.size <= 5 && skills.all { it is String && it.length in 1..40 }) {
            "A task can have up to 5 skills, each 1 to 40 characters."
        }
        val courseTag = data["courseTag"]
        require(courseTag == null || (courseTag is String && courseTag.length <= 60)) {
            "Course tag must be 60 characters or fewer."
        }
        val amount = data["budgetAmount"]
        require(amount == null || (amount is Number && amount.toDouble().isFinite() && amount.toDouble() >= 0.0)) {
            "Budget amount must be a non-negative number."
        }
        require(budgetType != MarketBudgetType.FIXED || amount is Number) {
            "A fixed budget requires a budget amount."
        }
    }

    private fun validateOfferDraft(draft: MarketOfferDraft) {
        val map = linkedMapOf<String, Any?>(
            "title" to draft.title,
            "category" to draft.category,
            "description" to draft.description,
            "priceType" to draft.priceType.wireValue
        )
        draft.courseTag?.let { map["courseTag"] = it }
        draft.priceAmount?.let { map["priceAmount"] = it }
        draft.thumbnail?.let { map["thumbnail"] = it }
        validateOfferContentMap(map)
    }

    private fun validateOfferContentMap(data: Map<String, Any?>) {
        val title = data["title"] as? String
        val category = data["category"] as? String
        val description = data["description"] as? String
        val priceType = (data["priceType"] as? String)?.let { marketWireEnum<MarketPriceType>(it) }
        require(title != null && title.length in 5..80) { "Offer title must be 5 to 80 characters." }
        require(category != null && category.length in 1..60) { "Offer category must be 1 to 60 characters." }
        require(description != null && description.length in 1..400) { "Offer description must be 1 to 400 characters." }
        require(priceType != null) { "The offer price type is invalid." }
        val courseTag = data["courseTag"]
        require(courseTag == null || (courseTag is String && courseTag.length <= 60)) {
            "Course tag must be 60 characters or fewer."
        }
        val amount = data["priceAmount"]
        require(amount == null || (amount is Number && amount.toDouble().isFinite() && amount.toDouble() >= 0.0)) {
            "Price amount must be a non-negative number."
        }
        require(priceType != MarketPriceType.AMOUNT || amount is Number) {
            "An amount price requires a price amount."
        }
        val thumbnail = data["thumbnail"]
        require(thumbnail == null || (thumbnail is String && thumbnail.length <= MAX_THUMBNAIL_CHARS)) {
            "Thumbnail must be 150 KB or smaller."
        }
    }

    private fun validateProposalFields(message: String, price: Double, deliveryDays: Int) {
        require(message.trim().length in 20..400) { "Proposal message must be 20 to 400 characters." }
        require(price.isFinite() && price >= 0.0) { "Proposal price must be non-negative." }
        require(deliveryDays in 1..365) { "Delivery time must be between 1 and 365 days." }
    }

    private fun validateSupportDraft(draft: MarketSupportDraft) {
        require(draft.subject.trim().length in 5..80) { "Support subject must be 5 to 80 characters." }
        require(draft.body.trim().length in 10..500) { "Support details must be 10 to 500 characters." }
        require(draft.relatedType == null || draft.relatedType.length <= 40) { "Related type is too long." }
        require(draft.relatedId == null || draft.relatedId.length <= 128) { "Related ID is too long." }
    }

    private fun validateSkillTags(skillTags: List<String>) {
        require(skillTags.size <= 30 && skillTags.all { it.length <= 60 }) {
            "Add no more than 30 skill tags, each up to 60 characters."
        }
    }

    private fun requirePostingAllowed(profile: DocumentSnapshot, now: Long) {
        val status = profile.getString("status")
        val suspensionEnd = profile.getTimestamp("suspendedUntil")?.toDate()?.time
        val allowed = status == MarketProfileStatus.ACTIVE.wireValue ||
            (status == MarketProfileStatus.SUSPENDED.wireValue && suspensionEnd != null && suspensionEnd <= now)
        require(allowed) {
            if (status == MarketProfileStatus.BANNED.wireValue) {
                "Your Market account is banned from posting."
            } else {
                "Your Market account cannot post right now."
            }
        }
    }

    private fun nextWindow(profile: DocumentSnapshot, countField: String, startField: String): WindowCounter {
        val count = profile.long(countField).toInt()
        val start = profile.getTimestamp(startField)
        return if (start == null || nowMillis() >= start.toDate().time + MarketRepositoryPolicy.DAY_MS) {
            WindowCounter(1, null, reset = true)
        } else {
            WindowCounter(count + 1, start, reset = false)
        }
    }

    private fun mutationMap(
        kind: String,
        operation: String,
        targetId: String,
        targetType: String? = null
    ): Map<String, Any> = linkedMapOf<String, Any>(
        "kind" to kind,
        "operation" to operation,
        "targetId" to targetId,
        "at" to FieldValue.serverTimestamp()
    ).apply {
        targetType?.let { put("targetType", it) }
    }

    private fun studentProfileRef(uid: String): DocumentReference =
        firestore.collection(STUDENT_PROFILES).document(uid)

    private fun marketProfileRef(uid: String): DocumentReference =
        firestore.collection(MARKET_PROFILES).document(uid)

    private fun requireSignedInUid(): String {
        val user = auth.currentUser ?: throw IllegalStateException("Sign in to Student Connect before using Market.")
        return user.uid
    }

    private fun requireVerifiedUid(): String {
        val user = auth.currentUser ?: throw IllegalStateException("Sign in to Student Connect before using Market.")
        require(user.isEmailVerified) {
            "Verify your Student Connect email before making this Market change."
        }
        return user.uid
    }

    private fun requireValidDisplayName(value: String?): String {
        val name = value ?: throw IllegalStateException("Create your Student Connect profile before using Market.")
        require(name.length in 2..60) { "Your Student Connect display name is invalid." }
        return name
    }

    private fun settingsMap(settings: MarketProfileSettings): Map<String, Boolean> = mapOf(
        "alertProposals" to settings.alertProposals,
        "alertUpdates" to settings.alertUpdates,
        "alertNotices" to settings.alertNotices,
        "alertMatchingTasks" to settings.alertMatchingTasks
    )

    private fun adminLogMap(
        adminUid: String,
        action: String,
        targetType: String,
        targetId: String,
        reason: String,
        note: String
    ): Map<String, Any> {
        require(action.length in 1..80 && targetType.length in 1..40 && targetId.length <= 150) {
            "The audit log details exceed the permitted size."
        }
        require(reason.length <= 500 && note.length <= 1000) {
            "The audit log reason or note is too long."
        }
        return mapOf(
            "adminId" to adminUid,
            "action" to action,
            "targetType" to targetType,
            "targetId" to targetId,
            "reason" to reason,
            "note" to note,
            "createdAt" to FieldValue.serverTimestamp()
        )
    }

    private fun createNoticeInTransaction(
        tx: Transaction,
        recipientUid: String,
        noticeId: String?,
        type: MarketNoticeType,
        title: String,
        body: String,
        relatedType: String?,
        relatedId: String?,
        canAppeal: Boolean
    ): DocumentReference {
        require(title.length in 1..100 && body.length in 1..1000) {
            "The Market notice text exceeds the permitted size."
        }
        val collection = firestore.collection(USERS).document(recipientUid).collection(NOTICES)
        val ref = noticeId?.let { collection.document(it) } ?: collection.document()
        val data = linkedMapOf<String, Any?>(
            "type" to type.wireValue,
            "title" to title,
            "body" to body,
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "canAppeal" to canAppeal,
            "appealed" to false
        )
        relatedType?.let { data["relatedType"] = it }
        relatedId?.let { data["relatedId"] = it }
        tx.set(ref, data)
        return ref
    }

    private fun normalizeReplyMaps(replies: List<*>): MutableList<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        replies.forEach { item ->
            val map = item as? Map<*, *> ?: throw IllegalStateException("A saved support reply is malformed.")
            result.add(map.entries.associate { it.key.toString() to it.value })
        }
        return result
    }

    private fun DocumentSnapshot.long(field: String): Long = getLong(field) ?: 0L
    private fun DocumentSnapshot.number(field: String): Double = (get(field) as? Number)?.toDouble() ?: 0.0

    private data class WindowCounter(val count: Int, val startedAt: Timestamp?, val reset: Boolean)

    private companion object {
        const val STUDENT_PROFILES = "profiles"
        const val MARKET_PROFILES = "marketProfiles"
        const val TASKS = "tasks"
        const val PROPOSALS = "proposals"
        const val OFFERS = "offers"
        const val REPORTS = "reports"
        const val RATINGS = "ratings"
        const val USER_STATS = "userStats"
        const val MARKET_CONVERSATIONS = "marketConversations"
        const val MESSAGES = "messages"
        const val USERS = "users"
        const val NOTICES = "notices"
        const val SUPPORT = "support"
        const val ADMIN_LOGS = "adminLogs"
        const val CONFIG = "config"
        const val MARKET_CONFIG_ID = "market"
        const val MAX_OPEN_TASKS = 3
        const val MAX_ACTIVE_OFFERS = 5
        const val MAX_PROPOSALS_PER_TASK = 15
        const val MAX_PROPOSALS_PER_24_HOURS = 10
        const val MAX_REPORTS_PER_24_HOURS = 10
        const val MAX_OPEN_SUPPORT_TICKETS = 5
        const val MAX_SUPPORT_REPLIES = 20
        const val REPORTS_TO_HIDE = 3
        const val MAX_THUMBNAIL_CHARS = 153600
    }

    private fun defaultSettings() = MarketProfileSettings(
        alertProposals = true,
        alertUpdates = true,
        alertNotices = true,
        alertMatchingTasks = true
    )
}

/** Deterministic, side-effect-free policies used by the repository and tests. */
internal object MarketRepositoryPolicy {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val TASK_TTL_MS = 14L * DAY_MS
    const val OFFER_TTL_MS = 30L * DAY_MS
    const val RENEWAL_INTERVAL_MS = 7L * DAY_MS
    const val CLIENT_CLOCK_SAFETY_MS = 30_000L

    fun safeClientTimestamp(nowMillis: Long): Timestamp =
        Timestamp(Date((nowMillis - CLIENT_CLOCK_SAFETY_MS).coerceAtLeast(0L)))

    fun taskExpiry(createdAt: Timestamp): Timestamp =
        Timestamp(Date(createdAt.toDate().time + TASK_TTL_MS))

    fun offerExpiry(renewedAt: Timestamp): Timestamp =
        Timestamp(Date(renewedAt.toDate().time + OFFER_TTL_MS))

    fun initials(displayName: String): String {
        val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val initials = parts.take(4).joinToString("") { it.substring(0, 1).uppercase(Locale.ROOT) }
        return initials.ifBlank { displayName.trim().take(1).uppercase(Locale.ROOT) }.take(4)
    }

    fun conversationId(
        kind: MarketListingKind,
        listingId: String,
        firstUid: String,
        secondUid: String
    ): String {
        require(listingId.isNotBlank() && firstUid.isNotBlank() && secondUid.isNotBlank()) {
            "A listing and two participants are required."
        }
        require(firstUid != secondUid) { "A Market conversation needs two different users." }
        val sorted = listOf(firstUid, secondUid).sorted()
        return kind.wireValue + "_" + listingId + "_" + sorted[0] + "_" + sorted[1]
    }

    fun reportId(type: MarketReportTargetType, targetId: String, reporterUid: String): String =
        type.wireValue + "_" + targetId + "_" + reporterUid

    fun ratingId(taskId: String, fromUid: String): String = taskId + "_" + fromUid

    fun appealTicketId(noticeId: String, uid: String): String = "appeal_" + noticeId + "_" + uid
}
