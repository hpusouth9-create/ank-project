package com.p2pvoice.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserIdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("p2p_voice_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        // Excludes confusable chars: I, O, 0, 1
    }

    /**
     * Returns existing user ID or generates a new one.
     * Format: "XXXX-XXXX" — easy to read aloud and share
     */
    fun getUserId(): String {
        return prefs.getString(KEY_USER_ID, null) ?: generateAndSave()
    }

    private fun generateAndSave(): String {
        val id = buildString {
            repeat(4) { append(CHARS.random()) }
            append("-")
            repeat(4) { append(CHARS.random()) }
        }
        prefs.edit().putString(KEY_USER_ID, id).apply()
        return id
    }

    /**
     * Regenerate ID (e.g. for privacy reset)
     */
    fun regenerateId(): String {
        prefs.edit().remove(KEY_USER_ID).apply()
        return getUserId()
    }

    /**
     * Validate that a peer ID has the correct format
     */
    fun isValidId(id: String): Boolean {
        val cleaned = id.trim().uppercase()
        return cleaned.matches(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}"))
    }

    /**
     * Normalize user input (uppercase, trim, auto-insert dash)
     */
    fun normalizeId(input: String): String {
        val cleaned = input.trim().uppercase().replace(" ", "")
        return if (cleaned.length == 8 && !cleaned.contains("-")) {
            "${cleaned.substring(0, 4)}-${cleaned.substring(4)}"
        } else {
            cleaned
        }
    }
}
