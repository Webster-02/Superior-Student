package com.superiorstudent.app.feature.market.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Strict, side-effect-free codecs for the Market Firestore schema.
 *
 * Document IDs and parent path IDs are kept on models for app use but are never
 * serialized as extra fields. Every top-level encoded document uses only the
 * allow-listed field names from firestore.rules.
 */
object MarketConverters {

    private val profileKeys = setOf(
        "uid", "skillTags", "blockedUids", "status", "suspendedUntil", "warnings", "settings",
        "openTasks", "activeOffers", "proposalWindowCount", "proposalWindowStartedAt",
        "reportWindowCount", "reportWindowStartedAt", "openSupportTickets", "lastMutation",
        "createdAt", "updatedAt"
    )
    private val taskKeys = setOf(
        "title", "category", "description", "budgetType", "deadline", "skills", "posterId",
        "posterName", "posterInitials", "status", "moderation", "pinned", "proposalCount",
        "reportCount", "createdAt", "updatedAt", "expiresAt"
    )
    private val proposalKeys = setOf(
        "proposerId", "message", "price", "deliveryDays", "status", "proposerName",
        "proposerInitials", "createdAt", "updatedAt"
    )
    private val offerKeys = setOf(
        "title", "category", "description", "priceType", "sellerId", "sellerName",
        "sellerInitials", "status", "moderation", "pinned", "reportCount", "createdAt",
        "renewedAt", "updatedAt", "expiresAt"
    )
    private val conversationRequired = setOf(
        "participants", "kind", "listingId", "listingOwnerUid", "createdAt", "updatedAt"
    )
    private val messageRequired = setOf("senderUid", "recipientUid", "senderName", "text", "createdAt")
    private val reportKeys = setOf("targetType", "targetId", "reporterId", "reason", "createdAt", "status")
    private val ratingKeys = setOf("taskId", "fromUid", "toUid", "stars", "tags", "comment", "createdAt")
    private val statsKeys = setOf("ratingSum", "ratingCount", "completedCount", "lastMutation", "updatedAt")
    private val noticeRequired = setOf("type", "title", "body", "read", "createdAt", "canAppeal", "appealed")
    private val supportRequired = setOf(
        "userId", "type", "subject", "body", "status", "replies", "createdAt", "updatedAt"
    )
    private val adminLogKeys = setOf("adminId", "action", "targetType", "targetId", "reason", "note", "createdAt")
    private val configKeys = setOf("announcement", "blockedWords", "rankWeights", "limits")

    fun profile(snapshot: DocumentSnapshot): MarketProfile? =
        snapshot.data?.let { profile(snapshot.id, it) }

    fun profile(documentId: String, data: Map<String, Any?>): MarketProfile? {
        if (!exactKeys(data, profileKeys) || documentId != str(data["uid"])) return null
        val status = enum<MarketProfileStatus>(data["status"]) ?: return null
        val skillTags = strings(data["skillTags"]) ?: return null
        val blockedUids = strings(data["blockedUids"]) ?: return null
        val settings = profileSettings(data["settings"]) ?: return null
        val warnings = int(data["warnings"]) ?: return null
        val openTasks = int(data["openTasks"]) ?: return null
        val activeOffers = int(data["activeOffers"]) ?: return null
        val proposalWindowCount = int(data["proposalWindowCount"]) ?: return null
        val reportWindowCount = int(data["reportWindowCount"]) ?: return null
        val openSupportTickets = int(data["openSupportTickets"]) ?: return null
        val createdAt = timestamp(data["createdAt"]) ?: return null
        val updatedAt = timestamp(data["updatedAt"]) ?: return null
        if (skillTags.size > 30 || blockedUids.size > 200 || warnings < 0 ||
            openTasks < 0 || activeOffers < 0 || proposalWindowCount < 0 ||
            reportWindowCount < 0 || openSupportTickets < 0
        ) return null
        if (!nullableTimestampField(data, "suspendedUntil") ||
            !nullableTimestampField(data, "proposalWindowStartedAt") ||
            !nullableTimestampField(data, "reportWindowStartedAt")
        ) return null
        val mutation = if (data["lastMutation"] == null) null else mutation(data["lastMutation"]) ?: return null
        return MarketProfile(
            uid = documentId,
            skillTags = skillTags,
            blockedUids = blockedUids,
            status = status,
            suspendedUntil = timestamp(data["suspendedUntil"]),
            warnings = warnings,
            settings = settings,
            openTasks = openTasks,
            activeOffers = activeOffers,
            proposalWindowCount = proposalWindowCount,
            proposalWindowStartedAt = timestamp(data["proposalWindowStartedAt"]),
            reportWindowCount = reportWindowCount,
            reportWindowStartedAt = timestamp(data["reportWindowStartedAt"]),
            openSupportTickets = openSupportTickets,
            lastMutation = mutation,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    fun task(snapshot: DocumentSnapshot): MarketTask? =
        snapshot.data?.let { task(snapshot.id, it) }

    fun task(documentId: String, data: Map<String, Any?>): MarketTask? {
        val allowedOptional = setOf("courseTag", "budgetAmount", "assignedTo")
        if (!exactKeys(data, taskKeys, allowedOptional)) return null
        val title = str(data["title"])?.takeIf { it.length in 5..80 } ?: return null
        val category = str(data["category"])?.takeIf { it.length in 1..60 } ?: return null
        val description = str(data["description"])?.takeIf { it.length in 20..500 } ?: return null
        val budgetType = enum<MarketBudgetType>(data["budgetType"]) ?: return null
        val deadline = timestamp(data["deadline"]) ?: return null
        val skills = strings(data["skills"]) ?: return null
        if (skills.size > 5 || skills.any { it.isBlank() || it.length > 40 }) return null
        val posterId = str(data["posterId"])?.takeIf(String::isNotBlank) ?: return null
        val posterName = str(data["posterName"])?.takeIf { it.length in 2..60 } ?: return null
        val posterInitials = str(data["posterInitials"])?.takeIf { it.length in 1..4 } ?: return null
        val status = enum<MarketTaskStatus>(data["status"]) ?: return null
        val moderation = enum<MarketModerationStatus>(data["moderation"]) ?: return null
        val pinned = bool(data["pinned"]) ?: return null
        val proposalCount = int(data["proposalCount"])?.takeIf { it >= 0 } ?: return null
        val reportCount = int(data["reportCount"])?.takeIf { it >= 0 } ?: return null
        val createdAt = timestamp(data["createdAt"]) ?: return null
        val updatedAt = timestamp(data["updatedAt"]) ?: return null
        val expiresAt = timestamp(data["expiresAt"]) ?: return null
        if (!optionalStringValid(data, "courseTag", 60) ||
            !optionalNumberValid(data, "budgetAmount") ||
            !optionalStringValid(data, "assignedTo", Int.MAX_VALUE)
        ) return null
        val courseTag = optionalString(data, "courseTag", 60)
        val budgetAmount = optionalNumber(data, "budgetAmount")
        val assignedTo = optionalString(data, "assignedTo", Int.MAX_VALUE)
        if (budgetType == MarketBudgetType.FIXED && budgetAmount == null) return null
        if (budgetAmount != null && budgetAmount < 0.0) return null
        return MarketTask(
            id = documentId, title = title, category = category, courseTag = courseTag,
            description = description, budgetType = budgetType, budgetAmount = budgetAmount,
            deadline = deadline, skills = skills, posterId = posterId, posterName = posterName,
            posterInitials = posterInitials, status = status, moderation = moderation, pinned = pinned,
            proposalCount = proposalCount, reportCount = reportCount, createdAt = createdAt,
            updatedAt = updatedAt, expiresAt = expiresAt, assignedTo = assignedTo
        )
    }

    fun proposal(snapshot: DocumentSnapshot, taskId: String): MarketProposal? =
        snapshot.data?.let { proposal(snapshot.id, taskId, it) }

    fun proposal(documentId: String, taskId: String, data: Map<String, Any?>): MarketProposal? {
        if (!exactKeys(data, proposalKeys)) return null
        val proposerId = str(data["proposerId"])?.takeIf(String::isNotBlank) ?: return null
        val message = str(data["message"])?.takeIf { it.length in 20..400 } ?: return null
        val price = number(data["price"])?.takeIf { it >= 0.0 } ?: return null
        val deliveryDays = int(data["deliveryDays"])?.takeIf { it in 1..365 } ?: return null
        val status = enum<MarketProposalStatus>(data["status"]) ?: return null
        val proposerName = str(data["proposerName"])?.takeIf { it.length in 2..60 } ?: return null
        val proposerInitials = str(data["proposerInitials"])?.takeIf { it.length in 1..4 } ?: return null
        val createdAt = timestamp(data["createdAt"]) ?: return null
        val updatedAt = timestamp(data["updatedAt"]) ?: return null
        if (documentId != proposerId || taskId.isBlank()) return null
        return MarketProposal(
            id = documentId, taskId = taskId, proposerId = proposerId, message = message,
            price = price, deliveryDays = deliveryDays, status = status, proposerName = proposerName,
            proposerInitials = proposerInitials, createdAt = createdAt, updatedAt = updatedAt
        )
    }

    fun offer(snapshot: DocumentSnapshot): MarketOffer? =
        snapshot.data?.let { offer(snapshot.id, it) }

    fun offer(documentId: String, data: Map<String, Any?>): MarketOffer? {
        val optional = setOf("courseTag", "priceAmount", "thumbnail")
        if (!exactKeys(data, offerKeys, optional)) return null
        val title = str(data["title"])?.takeIf { it.length in 5..80 } ?: return null
        val category = str(data["category"])?.takeIf { it.length in 1..60 } ?: return null
        val description = str(data["description"])?.takeIf { it.length in 1..400 } ?: return null
        val priceType = enum<MarketPriceType>(data["priceType"]) ?: return null
        val sellerId = str(data["sellerId"])?.takeIf(String::isNotBlank) ?: return null
        val sellerName = str(data["sellerName"])?.takeIf { it.length in 2..60 } ?: return null
        val sellerInitials = str(data["sellerInitials"])?.takeIf { it.length in 1..4 } ?: return null
        val status = enum<MarketOfferStatus>(data["status"]) ?: return null
        val moderation = enum<MarketModerationStatus>(data["moderation"]) ?: return null
        val pinned = bool(data["pinned"]) ?: return null
        val reportCount = int(data["reportCount"])?.takeIf { it >= 0 } ?: return null
        val createdAt = timestamp(data["createdAt"]) ?: return null
        val renewedAt = timestamp(data["renewedAt"]) ?: return null
        val updatedAt = timestamp(data["updatedAt"]) ?: return null
        val expiresAt = timestamp(data["expiresAt"]) ?: return null
        if (!optionalStringValid(data, "courseTag", 60) ||
            !optionalNumberValid(data, "priceAmount") ||
            !optionalStringValid(data, "thumbnail", 153600)
        ) return null
        val courseTag = optionalString(data, "courseTag", 60)
        val priceAmount = optionalNumber(data, "priceAmount")
        val thumbnail = optionalString(data, "thumbnail", 153600)
        if (priceType == MarketPriceType.AMOUNT && priceAmount == null) return null
        if (priceAmount != null && priceAmount < 0.0) return null
        return MarketOffer(
            id = documentId, title = title, category = category, courseTag = courseTag,
            description = description, priceType = priceType, priceAmount = priceAmount,
            thumbnail = thumbnail, sellerId = sellerId, sellerName = sellerName,
            sellerInitials = sellerInitials, status = status, moderation = moderation, pinned = pinned,
            reportCount = reportCount, createdAt = createdAt, renewedAt = renewedAt,
            updatedAt = updatedAt, expiresAt = expiresAt
        )
    }

    fun conversation(snapshot: DocumentSnapshot): MarketConversation? =
        snapshot.data?.let { conversation(snapshot.id, it) }

    fun conversation(documentId: String, data: Map<String, Any?>): MarketConversation? {
        val optional = setOf("taskId", "offerId", "lastMessage", "lastReadAt")
        if (!exactKeys(data, conversationRequired, optional)) return null
        val participants = strings(data["participants"]) ?: return null
        if (participants.size != 2 || participants[0] == participants[1] ||
            participants[0] >= participants[1]
        ) return null
        val kind = enum<MarketListingKind>(data["kind"]) ?: return null
        val listingId = str(data["listingId"])?.takeIf { it.length in 1..128 } ?: return null
        val ownerUid = str(data["listingOwnerUid"]) ?: return null
        val createdAt = timestamp(data["createdAt"]) ?: return null
        val updatedAt = timestamp(data["updatedAt"]) ?: return null
        val hasTask = data.containsKey("taskId")
        val hasOffer = data.containsKey("offerId")
        if (!optionalStringValid(data, "taskId", 128) ||
            !optionalStringValid(data, "offerId", 128)
        ) return null
        val taskId = optionalString(data, "taskId", 128)
        val offerId = optionalString(data, "offerId", 128)
        if (ownerUid !in participants) return null
        if (kind == MarketListingKind.TASK) {
            if (!hasTask || hasOffer || taskId != listingId) return null
        } else if (!hasOffer || hasTask || offerId != listingId) return null
        val expectedId = kind.wireValue + "_" + listingId + "_" + participants[0] + "_" + participants[1]
        if (documentId != expectedId) return null

        val lastMessage = if (!data.containsKey("lastMessage")) null
            else conversationLastMessage(data["lastMessage"], participants) ?: return null
        val lastReadAt = if (!data.containsKey("lastReadAt")) emptyMap()
            else timestampMap(data["lastReadAt"], participants.toSet()) ?: return null
        return MarketConversation(
            id = documentId, participants = participants, kind = kind, listingId = listingId,
            listingOwnerUid = ownerUid, createdAt = createdAt, updatedAt = updatedAt,
            taskId = taskId, offerId = offerId, lastMessage = lastMessage, lastReadAt = lastReadAt
        )
    }

    fun message(snapshot: DocumentSnapshot, conversationId: String): MarketConversationMessage? =
        snapshot.data?.let { message(snapshot.id, conversationId, it) }

    fun message(documentId: String, conversationId: String, data: Map<String, Any?>): MarketConversationMessage? {
        val optional = setOf("replyToMessageId", "replyToSenderName", "replyToText")
        if (!exactKeys(data, messageRequired, optional)) return null
        val senderUid = str(data["senderUid"])?.takeIf(String::isNotBlank) ?: return null
        val recipientUid = str(data["recipientUid"])?.takeIf(String::isNotBlank) ?: return null
        val senderName = str(data["senderName"])?.takeIf { it.length in 2..60 } ?: return null
        val text = str(data["text"])?.takeIf { it.length in 1..2000 } ?: return null
        val createdAt = timestamp(data["createdAt"]) ?: return null
        if (senderUid == recipientUid || conversationId.isBlank()) return null
        if (!optionalStringValid(data, "replyToMessageId", 128) ||
            !optionalStringValid(data, "replyToSenderName", 60) ||
            !optionalStringValid(data, "replyToText", 2000)
        ) return null
        val replyToMessageId = optionalString(data, "replyToMessageId", 128)
        val replyToSenderName = optionalString(data, "replyToSenderName", 60)
        val replyToText = optionalString(data, "replyToText", 2000)
        return MarketConversationMessage(
            id = documentId, conversationId = conversationId, senderUid = senderUid,
            recipientUid = recipientUid, senderName = senderName, text = text,
            createdAt = createdAt, replyToMessageId = replyToMessageId,
            replyToSenderName = replyToSenderName, replyToText = replyToText
        )
    }

    fun report(snapshot: DocumentSnapshot): MarketReport? =
        snapshot.data?.let { report(snapshot.id, it) }

    fun report(documentId: String, data: Map<String, Any?>): MarketReport? {
        if (!exactKeys(data, reportKeys)) return null
        val targetType = enum<MarketReportTargetType>(data["targetType"]) ?: return null
        val targetId = str(data["targetId"])?.takeIf { it.length in 1..128 } ?: return null
        val reporterId = str(data["reporterId"])?.takeIf(String::isNotBlank) ?: return null
        val reason = enum<MarketReportReason>(data["reason"]) ?: return null
        val createdAt = timestamp(data["createdAt"]) ?: return null
        val status = enum<MarketReportStatus>(data["status"]) ?: return null
        if (targetId == reporterId ||
            documentId != targetType.wireValue + "_" + targetId + "_" + reporterId
        ) return null
        return MarketReport(documentId, targetType, targetId, reporterId, reason, createdAt, status)
    }

    fun rating(snapshot: DocumentSnapshot): MarketRating? =
        snapshot.data?.let { rating(snapshot.id, it) }

    fun rating(documentId: String, data: Map<String, Any?>): MarketRating? {
        if (!exactKeys(data, ratingKeys)) return null
        val taskId = str(data["taskId"])?.takeIf(String::isNotBlank) ?: return null
        val fromUid = str(data["fromUid"])?.takeIf(String::isNotBlank) ?: return null
        val toUid = str(data["toUid"])?.takeIf(String::isNotBlank) ?: return null
        val stars = int(data["stars"])?.takeIf { it in 1..5 } ?: return null
        val tags = strings(data["tags"]) ?: return null
        val comment = str(data["comment"])?.takeIf { it.length <= 200 } ?: return null
        val createdAt = timestamp(data["createdAt"]) ?: return null
        if (tags.size > 8 || fromUid == toUid || documentId != taskId + "_" + fromUid) return null
        return MarketRating(documentId, taskId, fromUid, toUid, stars, tags, comment, createdAt)
    }

    fun userStats(snapshot: DocumentSnapshot): MarketUserStats? =
        snapshot.data?.let { userStats(snapshot.id, it) }

    fun userStats(documentId: String, data: Map<String, Any?>): MarketUserStats? {
        if (!exactKeys(data, statsKeys)) return null
        val ratingSum = number(data["ratingSum"])?.takeIf { it >= 0.0 } ?: return null
        val ratingCount = int(data["ratingCount"])?.takeIf { it >= 0 } ?: return null
        val completedCount = int(data["completedCount"])?.takeIf { it >= 0 } ?: return null
        val mutation = mutation(data["lastMutation"]) ?: return null
        val updatedAt = timestamp(data["updatedAt"]) ?: return null
        if (mutation.kind != "rating" || mutation.operation != "create") return null
        return MarketUserStats(documentId, ratingSum, ratingCount, completedCount, mutation, updatedAt)
    }

    fun notice(snapshot: DocumentSnapshot, uid: String): MarketNotice? =
        snapshot.data?.let { notice(snapshot.id, uid, it) }

    fun notice(documentId: String, uid: String, data: Map<String, Any?>): MarketNotice? {
        if (!exactKeys(data, noticeRequired, setOf("relatedType", "relatedId"))) return null
        val type = enum<MarketNoticeType>(data["type"]) ?: return null
        val title = str(data["title"])?.takeIf { it.length in 1..100 } ?: return null
        val body = str(data["body"])?.takeIf { it.length in 1..1000 } ?: return null
        val read = bool(data["read"]) ?: return null
        val createdAt = timestamp(data["createdAt"]) ?: return null
        val canAppeal = bool(data["canAppeal"]) ?: return null
        val appealed = bool(data["appealed"]) ?: return null
        if (!optionalStringValid(data, "relatedType", 40) ||
            !optionalStringValid(data, "relatedId", 128)
        ) return null
        val relatedType = optionalString(data, "relatedType", 40)
        val relatedId = optionalString(data, "relatedId", 128)
        if (uid.isBlank()) return null
        return MarketNotice(documentId, uid, type, title, body, read, createdAt, canAppeal, appealed, relatedType, relatedId)
    }

    fun supportTicket(snapshot: DocumentSnapshot): MarketSupportTicket? =
        snapshot.data?.let { supportTicket(snapshot.id, it) }

    fun supportTicket(documentId: String, data: Map<String, Any?>): MarketSupportTicket? {
        if (!exactKeys(data, supportRequired, setOf("relatedType", "relatedId"))) return null
        val userId = str(data["userId"])?.takeIf(String::isNotBlank) ?: return null
        val type = enum<MarketSupportType>(data["type"]) ?: return null
        val subject = str(data["subject"])?.takeIf { it.length in 5..80 } ?: return null
        val body = str(data["body"])?.takeIf { it.length in 10..500 } ?: return null
        val status = enum<MarketSupportStatus>(data["status"]) ?: return null
        val replies = supportReplies(data["replies"]) ?: return null
        if (replies.size > 20) return null
        val createdAt = timestamp(data["createdAt"]) ?: return null
        val updatedAt = timestamp(data["updatedAt"]) ?: return null
        if (!optionalStringValid(data, "relatedType", 40) ||
            !optionalStringValid(data, "relatedId", 128)
        ) return null
        val relatedType = optionalString(data, "relatedType", 40)
        val relatedId = optionalString(data, "relatedId", 128)
        if (type == MarketSupportType.APPEAL) {
            if (relatedType != "notice" || relatedId.isNullOrBlank() ||
                documentId != "appeal_" + relatedId + "_" + userId
            ) return null
        }
        return MarketSupportTicket(
            documentId, userId, type, subject, body, status, replies, createdAt, updatedAt,
            relatedType, relatedId
        )
    }

    fun adminLog(snapshot: DocumentSnapshot): MarketAdminLog? =
        snapshot.data?.let { adminLog(snapshot.id, it) }

    fun adminLog(documentId: String, data: Map<String, Any?>): MarketAdminLog? {
        if (!exactKeys(data, adminLogKeys)) return null
        val adminId = str(data["adminId"])?.takeIf(String::isNotBlank) ?: return null
        val action = str(data["action"])?.takeIf { it.length in 1..80 } ?: return null
        val targetType = str(data["targetType"])?.takeIf { it.length in 1..40 } ?: return null
        val targetId = str(data["targetId"])?.takeIf { it.length <= 150 } ?: return null
        val reason = str(data["reason"])?.takeIf { it.length <= 500 } ?: return null
        val note = str(data["note"])?.takeIf { it.length <= 1000 } ?: return null
        val createdAt = timestamp(data["createdAt"]) ?: return null
        return MarketAdminLog(documentId, adminId, action, targetType, targetId, reason, note, createdAt)
    }

    fun config(snapshot: DocumentSnapshot): MarketConfig? {
        if (snapshot.id != "market") return null
        return snapshot.data?.let(::config)
    }

    fun config(data: Map<String, Any?>): MarketConfig? {
        if (!exactKeys(data, configKeys)) return null
        val announcement = announcement(data["announcement"]) ?: return null
        val blockedWords = strings(data["blockedWords"])?.takeIf { it.size <= 300 } ?: return null
        val rankWeights = rankWeights(data["rankWeights"]) ?: return null
        val limits = limits(data["limits"]) ?: return null
        return MarketConfig(announcement, blockedWords, rankWeights, limits)
    }

    fun toFirestoreMap(value: MarketProfile): Map<String, Any?> = linkedMapOf(
        "uid" to value.uid,
        "skillTags" to value.skillTags,
        "blockedUids" to value.blockedUids,
        "status" to value.status.wireValue,
        "suspendedUntil" to value.suspendedUntil,
        "warnings" to value.warnings,
        "settings" to linkedMapOf(
            "alertProposals" to value.settings.alertProposals,
            "alertUpdates" to value.settings.alertUpdates,
            "alertNotices" to value.settings.alertNotices,
            "alertMatchingTasks" to value.settings.alertMatchingTasks
        ),
        "openTasks" to value.openTasks,
        "activeOffers" to value.activeOffers,
        "proposalWindowCount" to value.proposalWindowCount,
        "proposalWindowStartedAt" to value.proposalWindowStartedAt,
        "reportWindowCount" to value.reportWindowCount,
        "reportWindowStartedAt" to value.reportWindowStartedAt,
        "openSupportTickets" to value.openSupportTickets,
        "lastMutation" to value.lastMutation?.let(::mutationMap),
        "createdAt" to value.createdAt,
        "updatedAt" to value.updatedAt
    )

    fun toFirestoreMap(value: MarketTask): Map<String, Any?> = linkedMapOf<String, Any?>(
        "title" to value.title,
        "category" to value.category,
        "description" to value.description,
        "budgetType" to value.budgetType.wireValue,
        "deadline" to value.deadline,
        "skills" to value.skills,
        "posterId" to value.posterId,
        "posterName" to value.posterName,
        "posterInitials" to value.posterInitials,
        "status" to value.status.wireValue,
        "moderation" to value.moderation.wireValue,
        "pinned" to value.pinned,
        "proposalCount" to value.proposalCount,
        "reportCount" to value.reportCount,
        "createdAt" to value.createdAt,
        "updatedAt" to value.updatedAt,
        "expiresAt" to value.expiresAt
    ).apply {
        value.courseTag?.let { put("courseTag", it) }
        value.budgetAmount?.let { put("budgetAmount", it) }
        value.assignedTo?.let { put("assignedTo", it) }
    }

    fun toFirestoreMap(value: MarketProposal): Map<String, Any?> {
        require(value.id == value.proposerId) { "Proposal document ID must equal proposerId." }
        return linkedMapOf(
            "proposerId" to value.proposerId,
            "message" to value.message,
            "price" to value.price,
            "deliveryDays" to value.deliveryDays,
            "status" to value.status.wireValue,
            "proposerName" to value.proposerName,
            "proposerInitials" to value.proposerInitials,
            "createdAt" to value.createdAt,
            "updatedAt" to value.updatedAt
        )
    }

    fun toFirestoreMap(value: MarketOffer): Map<String, Any?> = linkedMapOf<String, Any?>(
        "title" to value.title,
        "category" to value.category,
        "description" to value.description,
        "priceType" to value.priceType.wireValue,
        "sellerId" to value.sellerId,
        "sellerName" to value.sellerName,
        "sellerInitials" to value.sellerInitials,
        "status" to value.status.wireValue,
        "moderation" to value.moderation.wireValue,
        "pinned" to value.pinned,
        "reportCount" to value.reportCount,
        "createdAt" to value.createdAt,
        "renewedAt" to value.renewedAt,
        "updatedAt" to value.updatedAt,
        "expiresAt" to value.expiresAt
    ).apply {
        value.courseTag?.let { put("courseTag", it) }
        value.priceAmount?.let { put("priceAmount", it) }
        value.thumbnail?.let { put("thumbnail", it) }
    }

    fun toFirestoreMap(value: MarketConversation): Map<String, Any?> {
        require(
            value.participants.size == 2 &&
                value.participants[0] != value.participants[1] &&
                value.participants == value.participants.sorted()
        ) {
            "Market conversation participants must be two distinct, sorted UIDs."
        }
        require(value.listingOwnerUid in value.participants) { "Listing owner must be a participant." }
        val isTask = value.kind == MarketListingKind.TASK
        require(if (isTask) value.taskId == value.listingId && value.offerId == null
            else value.offerId == value.listingId && value.taskId == null
        ) { "Conversation must carry only the matching taskId or offerId." }
        val expectedId = value.kind.wireValue + "_" + value.listingId + "_" +
            value.participants[0] + "_" + value.participants[1]
        require(value.id == expectedId) { "Conversation ID is not deterministic." }
        val result = linkedMapOf<String, Any?>(
            "participants" to value.participants,
            "kind" to value.kind.wireValue,
            "listingId" to value.listingId,
            "listingOwnerUid" to value.listingOwnerUid,
            "createdAt" to value.createdAt,
            "updatedAt" to value.updatedAt
        )
        if (isTask) result["taskId"] = value.taskId else result["offerId"] = value.offerId
        value.lastMessage?.let {
            result["lastMessage"] = linkedMapOf("text" to it.text, "senderUid" to it.senderUid, "at" to it.at)
        }
        if (value.lastReadAt.isNotEmpty()) result["lastReadAt"] = value.lastReadAt
        return result
    }

    fun toFirestoreMap(value: MarketConversationMessage): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "senderUid" to value.senderUid,
            "recipientUid" to value.recipientUid,
            "senderName" to value.senderName,
            "text" to value.text,
            "createdAt" to value.createdAt
        ).apply {
            value.replyToMessageId?.let { put("replyToMessageId", it) }
            value.replyToSenderName?.let { put("replyToSenderName", it) }
            value.replyToText?.let { put("replyToText", it) }
        }

    fun toFirestoreMap(value: MarketReport): Map<String, Any?> {
        require(value.id == value.targetType.wireValue + "_" + value.targetId + "_" + value.reporterId) {
            "Report document ID is not deterministic."
        }
        return linkedMapOf(
            "targetType" to value.targetType.wireValue,
            "targetId" to value.targetId,
            "reporterId" to value.reporterId,
            "reason" to value.reason.wireValue,
            "createdAt" to value.createdAt,
            "status" to value.status.wireValue
        )
    }

    fun toFirestoreMap(value: MarketRating): Map<String, Any?> {
        require(value.id == value.taskId + "_" + value.fromUid) { "Rating document ID is not deterministic." }
        return linkedMapOf(
            "taskId" to value.taskId,
            "fromUid" to value.fromUid,
            "toUid" to value.toUid,
            "stars" to value.stars,
            "tags" to value.tags,
            "comment" to value.comment,
            "createdAt" to value.createdAt
        )
    }

    fun toFirestoreMap(value: MarketUserStats): Map<String, Any?> = linkedMapOf(
        "ratingSum" to value.ratingSum,
        "ratingCount" to value.ratingCount,
        "completedCount" to value.completedCount,
        "lastMutation" to mutationMap(value.lastMutation),
        "updatedAt" to value.updatedAt
    )

    fun toFirestoreMap(value: MarketNotice): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "type" to value.type.wireValue,
            "title" to value.title,
            "body" to value.body,
            "read" to value.read,
            "createdAt" to value.createdAt,
            "canAppeal" to value.canAppeal,
            "appealed" to value.appealed
        ).apply {
            value.relatedType?.let { put("relatedType", it) }
            value.relatedId?.let { put("relatedId", it) }
        }

    fun toFirestoreMap(value: MarketSupportTicket): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "userId" to value.userId,
            "type" to value.type.wireValue,
            "subject" to value.subject,
            "body" to value.body,
            "status" to value.status.wireValue,
            "replies" to value.replies.map(::supportReplyMap),
            "createdAt" to value.createdAt,
            "updatedAt" to value.updatedAt
        ).apply {
            value.relatedType?.let { put("relatedType", it) }
            value.relatedId?.let { put("relatedId", it) }
        }

    fun toFirestoreMap(value: MarketAdminLog): Map<String, Any?> = linkedMapOf(
        "adminId" to value.adminId,
        "action" to value.action,
        "targetType" to value.targetType,
        "targetId" to value.targetId,
        "reason" to value.reason,
        "note" to value.note,
        "createdAt" to value.createdAt
    )

    fun toFirestoreMap(value: MarketConfig): Map<String, Any?> = linkedMapOf(
        "announcement" to linkedMapOf(
            "text" to value.announcement.text,
            "active" to value.announcement.active,
            "until" to value.announcement.until
        ),
        "blockedWords" to value.blockedWords,
        "rankWeights" to linkedMapOf(
            "newness" to value.rankWeights.newness,
            "rating" to value.rankWeights.rating,
            "match" to value.rankWeights.match
        ),
        "limits" to limitsMap(value.limits)
    )

    private fun exactKeys(
        data: Map<String, Any?>,
        required: Set<String>,
        optional: Set<String> = emptySet()
    ): Boolean = data.keys.containsAll(required) && data.keys.all { it in required || it in optional }

    private fun str(value: Any?): String? = value as? String
    private fun bool(value: Any?): Boolean? = value as? Boolean
    private fun timestamp(value: Any?): Timestamp? = value as? Timestamp

    private fun number(value: Any?): Double? =
        (value as? Number)?.toDouble()?.takeIf { it.isFinite() }

    private fun int(value: Any?): Int? {
        val n = value as? Number ?: return null
        if (n !is Byte && n !is Short && n !is Int && n !is Long) return null
        val longValue = n.toLong()
        if (longValue < Int.MIN_VALUE || longValue > Int.MAX_VALUE) return null
        return longValue.toInt()
    }

    private inline fun <reified T> enum(value: Any?): T?
        where T : Enum<T>, T : MarketWireValue =
        (value as? String)?.let { marketWireEnum<T>(it) }

    private fun strings(value: Any?): List<String>? {
        val source = value as? List<*> ?: return null
        val result = ArrayList<String>(source.size)
        for (item in source) result.add(item as? String ?: return null)
        return result
    }

    private fun optionalStringValid(data: Map<String, Any?>, key: String, maxLength: Int): Boolean =
        !data.containsKey(key) || ((data[key] as? String)?.length?.let { it <= maxLength } == true)

    private fun optionalString(data: Map<String, Any?>, key: String, maxLength: Int): String? {
        if (!data.containsKey(key)) return null
        return (data[key] as? String)?.takeIf { it.length <= maxLength }
    }

    private fun optionalNumberValid(data: Map<String, Any?>, key: String): Boolean =
        !data.containsKey(key) || number(data[key]) != null

    private fun optionalNumber(data: Map<String, Any?>, key: String): Double? {
        if (!data.containsKey(key)) return null
        return number(data[key])
    }

    private fun nullableTimestampField(data: Map<String, Any?>, key: String): Boolean =
        data[key] == null || data[key] is Timestamp

    private fun profileSettings(value: Any?): MarketProfileSettings? {
        val data = stringKeyedMap(value) ?: return null
        if (!exactKeys(
                data,
                setOf("alertProposals", "alertUpdates", "alertNotices", "alertMatchingTasks")
            )
        ) return null
        return MarketProfileSettings(
            alertProposals = bool(data["alertProposals"]) ?: return null,
            alertUpdates = bool(data["alertUpdates"]) ?: return null,
            alertNotices = bool(data["alertNotices"]) ?: return null,
            alertMatchingTasks = bool(data["alertMatchingTasks"]) ?: return null
        )
    }

    private fun mutation(value: Any?): MarketLastMutation? {
        val data = stringKeyedMap(value) ?: return null
        if (!exactKeys(data, setOf("kind", "operation", "targetId", "at"), setOf("targetType"))) return null
        val kind = str(data["kind"])?.takeIf(String::isNotBlank) ?: return null
        val operation = str(data["operation"])?.takeIf(String::isNotBlank) ?: return null
        val targetId = str(data["targetId"])?.takeIf(String::isNotBlank) ?: return null
        val at = timestamp(data["at"]) ?: return null
        if (!optionalStringValid(data, "targetType", 40)) return null
        val targetType = optionalString(data, "targetType", 40)
        return MarketLastMutation(kind, operation, targetId, at, targetType)
    }

    private fun mutationMap(value: MarketLastMutation): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "kind" to value.kind,
            "operation" to value.operation,
            "targetId" to value.targetId,
            "at" to value.at
        ).apply { value.targetType?.let { put("targetType", it) } }

    private fun conversationLastMessage(
        value: Any?,
        participants: List<String>
    ): MarketConversationLastMessage? {
        val data = stringKeyedMap(value) ?: return null
        if (!exactKeys(data, setOf("text", "senderUid", "at"))) return null
        val text = str(data["text"])?.takeIf { it.length in 1..100 } ?: return null
        val senderUid = str(data["senderUid"]) ?: return null
        val at = timestamp(data["at"]) ?: return null
        if (senderUid !in participants) return null
        return MarketConversationLastMessage(text, senderUid, at)
    }

    private fun timestampMap(value: Any?, allowedKeys: Set<String>): Map<String, Timestamp>? {
        val data = stringKeyedMap(value) ?: return null
        if (data.keys.any { it !in allowedKeys }) return null
        val result = linkedMapOf<String, Timestamp>()
        for ((key, item) in data) result[key] = timestamp(item) ?: return null
        return result
    }

    private fun supportReplies(value: Any?): List<MarketSupportReply>? {
        val source = value as? List<*> ?: return null
        val replies = ArrayList<MarketSupportReply>(source.size)
        for (item in source) replies.add(supportReply(item) ?: return null)
        return replies
    }

    private fun supportReply(value: Any?): MarketSupportReply? {
        val data = stringKeyedMap(value) ?: return null
        val from = str(data["from"])?.takeIf { it == "user" || it == "admin" } ?: return null
        val text = str(data["text"])?.takeIf { it.length in 1..1000 } ?: return null
        val at = timestamp(data["at"]) ?: return null
        val extras = data.filterKeys { it != "from" && it != "text" && it != "at" }
        return MarketSupportReply(from, text, at, extras)
    }

    private fun supportReplyMap(value: MarketSupportReply): Map<String, Any?> =
        linkedMapOf<String, Any?>().apply {
            putAll(value.extraFields)
            put("from", value.from)
            put("text", value.text)
            put("at", value.at)
        }

    private fun announcement(value: Any?): MarketAnnouncement? {
        val data = stringKeyedMap(value) ?: return null
        if (!exactKeys(data, setOf("text", "active", "until"))) return null
        val text = str(data["text"])?.takeIf { it.length <= 500 } ?: return null
        val active = bool(data["active"]) ?: return null
        if (!nullableTimestampField(data, "until")) return null
        return MarketAnnouncement(text, active, timestamp(data["until"]))
    }

    private fun rankWeights(value: Any?): MarketRankWeights? {
        val data = stringKeyedMap(value) ?: return null
        if (!exactKeys(data, setOf("newness", "rating", "match"))) return null
        val newness = number(data["newness"])?.takeIf { it >= 0.0 } ?: return null
        val rating = number(data["rating"])?.takeIf { it >= 0.0 } ?: return null
        val match = number(data["match"])?.takeIf { it >= 0.0 } ?: return null
        if (newness + rating + match != 100.0) return null
        return MarketRankWeights(newness, rating, match)
    }

    private fun limits(value: Any?): MarketLimits? {
        val data = stringKeyedMap(value) ?: return null
        val openTasks = int(data["openTasks"]) ?: return null
        val proposalsPerDay = int(data["proposalsPerDay"]) ?: return null
        val proposalsPerTask = int(data["proposalsPerTask"]) ?: return null
        val activeOffers = int(data["activeOffers"]) ?: return null
        val reportsToHide = int(data["reportsToHide"]) ?: return null
        if (openTasks != 3 || proposalsPerDay != 10 || proposalsPerTask != 15 ||
            activeOffers != 5 || reportsToHide != 3
        ) return null
        val known = setOf("openTasks", "proposalsPerDay", "proposalsPerTask", "activeOffers", "reportsToHide")
        return MarketLimits(
            openTasks, proposalsPerDay, proposalsPerTask, activeOffers, reportsToHide,
            data.filterKeys { it !in known }
        )
    }

    private fun limitsMap(value: MarketLimits): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        putAll(value.extraFields)
        put("openTasks", value.openTasks)
        put("proposalsPerDay", value.proposalsPerDay)
        put("proposalsPerTask", value.proposalsPerTask)
        put("activeOffers", value.activeOffers)
        put("reportsToHide", value.reportsToHide)
    }

    private fun stringKeyedMap(value: Any?): Map<String, Any?>? {
        val source = value as? Map<*, *> ?: return null
        val result = linkedMapOf<String, Any?>()
        for ((key, item) in source) result[key as? String ?: return null] = item
        return result
    }
}
