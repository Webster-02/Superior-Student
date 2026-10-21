package com.superiorstudent.app.feature.market.data

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Fixed identity and strict decoder for the single Market configuration
 * document. Keeping these decisions pure makes schema and identity checks
 * testable without a Firestore emulator or Android runtime.
 */
object MarketConfigReaderPolicy {
    const val COLLECTION_NAME: String = "config"
    const val DOCUMENT_ID: String = "market"

    fun decode(documentId: String, data: Map<String, Any?>?): MarketConfig? {
        if (documentId != DOCUMENT_ID || data == null) return null
        return MarketConverters.config(data)
    }
}

/**
 * Read-only access to config/market. Firestore Rules remain the authorization
 * boundary; this class has no write methods and never attempts a fallback.
 */
class MarketConfigReader(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun read(): Task<MarketConfig?> =
        firestore.collection(MarketConfigReaderPolicy.COLLECTION_NAME)
            .document(MarketConfigReaderPolicy.DOCUMENT_ID)
            .get()
            .continueWith { completed ->
                if (!completed.isSuccessful) {
                    throw completed.exception
                        ?: IllegalStateException("Market config read failed without an exception.")
                }
                val snapshot = completed.result ?: return@continueWith null
                MarketConfigReaderPolicy.decode(snapshot.id, snapshot.data)
            }
}
