package com.superiorstudent.app.feature.studentconnect.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.Timestamp
import com.superiorstudent.app.feature.transfer.data.P2pOfferState
import com.superiorstudent.app.feature.transfer.data.P2pTransferOffer
import com.superiorstudent.app.feature.transfer.data.P2pTransferValidation
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Small private, per-account cache used to paint U2U from the last successful session.
 * Firebase remains authoritative; cache reads/writes are asynchronous and never replace remote checks.
 */
class StudentConnectLocalCache private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "StudentConnectLocalCache").apply { isDaemon = true }
    }
    private val preferences by lazy {
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun loadProfile(uid: String, callback: (StudentProfile?) -> Unit) {
        ioExecutor.execute {
            val profile = runCatching {
                val raw = preferences.getString(profileKey(uid), null) ?: return@runCatching null
                val obj = JSONObject(raw)
                val saved = StudentProfile(
                    uid = obj.optString("uid"),
                    publicId = obj.optString("publicId"),
                    displayName = obj.optString("displayName")
                )
                saved.takeIf {
                    uid.isNotBlank() && it.uid == uid &&
                        StudentPublicIdFormat.isValid(it.publicId) &&
                        it.displayName.trim().length in 2..60
                }
            }.getOrNull()
            mainHandler.post { callback(profile) }
        }
    }

    fun saveProfile(profile: StudentProfile) {
        if (profile.uid.isBlank() || !StudentPublicIdFormat.isValid(profile.publicId) ||
            profile.displayName.trim().length !in 2..60
        ) return
        ioExecutor.execute {
            runCatching {
                preferences.edit().putString(
                    profileKey(profile.uid),
                    JSONObject()
                        .put("uid", profile.uid)
                        .put("publicId", profile.publicId)
                        .put("displayName", profile.displayName)
                        .toString()
                ).apply()
            }
        }
    }

    fun loadChatSummaries(uid: String, callback: (List<StudentChatSummary>) -> Unit) {
        ioExecutor.execute {
            val summaries = runCatching {
                val raw = preferences.getString(summariesKey(uid), null) ?: return@runCatching emptyList()
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val summary = item.toChatSummary(uid) ?: continue
                        add(summary)
                        if (size >= MAX_CACHED_CONVERSATIONS) break
                    }
                }.sortedByDescending { (it.lastMessageAt ?: it.updatedAt)?.toDate()?.time ?: 0L }
            }.getOrDefault(emptyList())
            mainHandler.post { callback(summaries) }
        }
    }

    fun saveChatSummaries(uid: String, summaries: List<StudentChatSummary>) {
        if (uid.isBlank()) return
        ioExecutor.execute {
            runCatching {
                val array = JSONArray()
                summaries.asSequence()
                    .filter { it.isValidFor(uid) }
                    .distinctBy { it.conversationId }
                    .sortedByDescending { (it.lastMessageAt ?: it.updatedAt)?.toDate()?.time ?: 0L }
                    .take(MAX_CACHED_CONVERSATIONS)
                    .forEach { summary ->
                        array.put(
                            JSONObject()
                                .put("conversationId", summary.conversationId)
                                .put("otherUid", summary.otherUid)
                                .put("otherDisplayName", summary.otherDisplayName)
                                .putNullableString("lastMessageText", summary.lastMessageText)
                                .putNullableString("lastMessageSenderUid", summary.lastMessageSenderUid)
                                .putTimestamp("lastMessageAt", summary.lastMessageAt)
                                .putTimestamp("updatedAt", summary.updatedAt)
                                .put("unreadCount", summary.unreadCount.coerceAtLeast(0))
                        )
                    }
                preferences.edit().putString(summariesKey(uid), array.toString()).apply()
            }
        }
    }

    fun loadMessages(
        uid: String,
        conversationId: String,
        otherUid: String,
        callback: (List<StudentChatMessage>) -> Unit
    ) {
        ioExecutor.execute {
            val messages = runCatching {
                if (uid.isBlank() || otherUid.isBlank() ||
                    conversationId != StudentChatIds.conversationId(uid, otherUid)
                ) return@runCatching emptyList()
                val raw = preferences.getString(messagesKey(uid, conversationId), null)
                    ?: return@runCatching emptyList()
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val message = item.toChatMessage(uid, otherUid) ?: continue
                        add(message)
                        if (size >= MAX_CACHED_MESSAGES) break
                    }
                }.sortedBy { it.createdAt?.toDate()?.time ?: 0L }
            }.getOrDefault(emptyList())
            mainHandler.post { callback(messages) }
        }
    }

    fun saveMessages(
        uid: String,
        conversationId: String,
        otherUid: String,
        messages: List<StudentChatMessage>
    ) {
        if (uid.isBlank() || otherUid.isBlank() ||
            conversationId != runCatching { StudentChatIds.conversationId(uid, otherUid) }.getOrNull()
        ) return
        ioExecutor.execute {
            runCatching {
                val array = JSONArray()
                messages.filter { it.isValidFor(uid, otherUid) }
                    .takeLast(MAX_CACHED_MESSAGES)
                    .forEach { message ->
                        array.put(
                            JSONObject()
                                .put("id", message.id)
                                .put("senderUid", message.senderUid)
                                .put("recipientUid", message.recipientUid)
                                .put("senderName", message.senderName)
                                .put("text", message.text)
                                .putTimestamp("createdAt", message.createdAt)
                                .putNullableString("replyToMessageId", message.replyToMessageId)
                                .putNullableString("replyToSenderName", message.replyToSenderName)
                                .putNullableString("replyToText", message.replyToText)
                        )
                    }
                preferences.edit().putString(messagesKey(uid, conversationId), array.toString()).apply()
            }
        }
    }

    fun loadTransferOffers(
        uid: String,
        conversationId: String,
        otherUid: String,
        callback: (List<P2pTransferOffer>) -> Unit
    ) {
        ioExecutor.execute {
            val offers = runCatching {
                if (uid.isBlank() || otherUid.isBlank() ||
                    conversationId != StudentChatIds.conversationId(uid, otherUid)
                ) return@runCatching emptyList()
                val raw = preferences.getString(transferOffersKey(uid, conversationId), null)
                    ?: return@runCatching emptyList()
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val offer = item.toTransferOffer(uid, conversationId, otherUid) ?: continue
                        add(offer)
                        if (size >= MAX_CACHED_TRANSFER_OFFERS) break
                    }
                }.sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }.getOrDefault(emptyList())
            mainHandler.post { callback(offers) }
        }
    }

    fun saveTransferOffers(
        uid: String,
        conversationId: String,
        otherUid: String,
        offers: List<P2pTransferOffer>
    ) {
        if (uid.isBlank() || otherUid.isBlank() ||
            conversationId != runCatching { StudentChatIds.conversationId(uid, otherUid) }.getOrNull()
        ) return
        ioExecutor.execute {
            runCatching {
                val array = JSONArray()
                offers.asSequence()
                    .filter { it.isValidFor(uid, conversationId, otherUid) }
                    .distinctBy { it.transferId }
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                    .takeLast(MAX_CACHED_TRANSFER_OFFERS)
                    .forEach { offer ->
                        array.put(
                            JSONObject()
                                .put("transferId", offer.transferId)
                                .put("conversationId", offer.conversationId)
                                .put("senderUid", offer.senderUid)
                                .put("receiverUid", offer.receiverUid)
                                .put("fileName", offer.fileName)
                                .put("sizeBytes", offer.sizeBytes)
                                .put("mimeType", offer.mimeType)
                                .put("sha256", offer.sha256)
                                .put("chunkSize", offer.chunkSize)
                                .put("senderPublicKeyBase64", offer.senderPublicKeyBase64)
                                .putNullableString("thumbnailBase64", offer.thumbnailBase64)
                                .put("status", offer.status.wireValue)
                                .putTimestamp("createdAt", offer.createdAt)
                                .putTimestamp("updatedAt", offer.updatedAt)
                        )
                    }
                preferences.edit().putString(transferOffersKey(uid, conversationId), array.toString()).apply()
            }
        }
    }

    fun clearAccount(uid: String) {
        if (uid.isBlank()) return
        ioExecutor.execute {
            runCatching {
                val editor = preferences.edit()
                preferences.all.keys
                    .filter {
                        it == profileKey(uid) || it == summariesKey(uid) ||
                            it.startsWith("messages:$uid:") || it.startsWith("transfers:$uid:")
                    }
                    .forEach(editor::remove)
                editor.apply()
            }
        }
    }

    private fun StudentChatSummary.isValidFor(uid: String): Boolean =
        otherUid.isNotBlank() && otherUid != uid &&
            conversationId == runCatching { StudentChatIds.conversationId(uid, otherUid) }.getOrNull() &&
            otherDisplayName.trim().length in 2..60 && unreadCount >= 0 &&
            (lastMessageSenderUid == null || lastMessageSenderUid == uid || lastMessageSenderUid == otherUid) &&
            (lastMessageText == null || lastMessageText.length <= StudentChatMessageText.MAX_LENGTH)

    private fun JSONObject.toChatSummary(uid: String): StudentChatSummary? = runCatching {
        val summary = StudentChatSummary(
            conversationId = optString("conversationId"),
            otherUid = optString("otherUid"),
            otherDisplayName = optString("otherDisplayName"),
            lastMessageText = nullableString("lastMessageText"),
            lastMessageSenderUid = nullableString("lastMessageSenderUid"),
            lastMessageAt = readTimestamp("lastMessageAt"),
            updatedAt = readTimestamp("updatedAt"),
            unreadCount = optInt("unreadCount", 0)
        )
        summary.takeIf { it.isValidFor(uid) }
    }.getOrNull()

    private fun JSONObject.toChatMessage(uid: String, otherUid: String): StudentChatMessage? = runCatching {
        val message = StudentChatMessage(
            id = optString("id"),
            senderUid = optString("senderUid"),
            recipientUid = optString("recipientUid"),
            senderName = optString("senderName"),
            text = optString("text"),
            createdAt = readTimestamp("createdAt"),
            replyToMessageId = nullableString("replyToMessageId"),
            replyToSenderName = nullableString("replyToSenderName"),
            replyToText = nullableString("replyToText")
        )
        message.takeIf { it.isValidFor(uid, otherUid) }
    }.getOrNull()

    private fun StudentChatMessage.isValidFor(uid: String, otherUid: String): Boolean =
        id.isNotBlank() && id.length <= 128 &&
            senderUid in setOf(uid, otherUid) &&
            recipientUid in setOf(uid, otherUid) &&
            senderUid != recipientUid &&
            senderName.trim().length in 1..60 &&
            text.isNotBlank() && text.length <= StudentChatMessageText.MAX_LENGTH

    private fun JSONObject.putNullableString(key: String, value: String?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.nullableString(key: String): String? {
        val value = opt(key)
        return if (value == null || value === JSONObject.NULL) null else value as? String
    }

    private fun JSONObject.putTimestamp(key: String, timestamp: Timestamp?): JSONObject {
        if (timestamp == null) return put(key, JSONObject.NULL)
        return put(
            key,
            JSONObject()
                .put("seconds", timestamp.seconds)
                .put("nanoseconds", timestamp.nanoseconds)
        )
    }

    private fun JSONObject.readTimestamp(key: String): Timestamp? {
        val obj = optJSONObject(key) ?: return null
        val seconds = obj.optLong("seconds", Long.MIN_VALUE)
        val nanoseconds = obj.optInt("nanoseconds", -1)
        if (seconds == Long.MIN_VALUE || nanoseconds !in 0..999_999_999) return null
        return runCatching { Timestamp(seconds, nanoseconds) }.getOrNull()
    }

    private fun profileKey(uid: String) = "profile:$uid"
    private fun summariesKey(uid: String) = "summaries:$uid"
    private fun messagesKey(uid: String, conversationId: String) = "messages:$uid:$conversationId"
    private fun transferOffersKey(uid: String, conversationId: String) = "transfers:$uid:$conversationId"

    private fun P2pTransferOffer.isValidFor(uid: String, conversationId: String, otherUid: String): Boolean =
        this.conversationId == conversationId &&
            senderUid in setOf(uid, otherUid) &&
            receiverUid in setOf(uid, otherUid) &&
            senderUid != receiverUid &&
            P2pTransferValidation.isConversationIdForPair(conversationId, senderUid, receiverUid) &&
            P2pTransferValidation.isValidTransferId(transferId) &&
            P2pTransferValidation.isValidFileName(fileName) &&
            sizeBytes > 0L &&
            P2pTransferValidation.isValidMimeType(mimeType) &&
            P2pTransferValidation.isValidSha256(sha256) &&
            P2pTransferValidation.isValidChunkSize(chunkSize) &&
            P2pTransferValidation.isValidPublicKey(senderPublicKeyBase64) &&
            P2pTransferValidation.isValidThumbnail(thumbnailBase64)

    private fun JSONObject.toTransferOffer(
        uid: String,
        conversationId: String,
        otherUid: String
    ): P2pTransferOffer? {
        val status = P2pOfferState.fromWire(optString("status")) ?: return null
        val offer = P2pTransferOffer(
            transferId = optString("transferId"),
            conversationId = optString("conversationId"),
            senderUid = optString("senderUid"),
            receiverUid = optString("receiverUid"),
            fileName = optString("fileName"),
            sizeBytes = optLong("sizeBytes", -1L),
            mimeType = optString("mimeType"),
            sha256 = optString("sha256"),
            chunkSize = optInt("chunkSize", -1),
            senderPublicKeyBase64 = optString("senderPublicKeyBase64"),
            thumbnailBase64 = nullableString("thumbnailBase64"),
            status = status,
            createdAt = readTimestamp("createdAt"),
            updatedAt = readTimestamp("updatedAt")
        )
        return offer.takeIf { it.isValidFor(uid, conversationId, otherUid) }
    }

    companion object {
        private const val PREFERENCES_NAME = "student_connect_local_cache_v1"
        private const val MAX_CACHED_CONVERSATIONS = 50
        private const val MAX_CACHED_MESSAGES = 100
        private const val MAX_CACHED_TRANSFER_OFFERS = 50

        @Volatile
        private var instance: StudentConnectLocalCache? = null

        fun get(context: Context): StudentConnectLocalCache =
            instance ?: synchronized(this) {
                instance ?: StudentConnectLocalCache(context).also { instance = it }
            }
    }
}
