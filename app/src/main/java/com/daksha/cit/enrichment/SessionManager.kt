package com.daksha.cit.enrichment

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

object SessionManager {
    private const val PREFS_NAME = "cit_session"
    private const val KEY_GUEST_MODE = "guest_mode"
    private const val KEY_GUEST_ID = "guest_id"

    fun enableGuestMode(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_GUEST_MODE, true)
            .putString(KEY_GUEST_ID, getGuestId(context))
            .apply()
    }

    fun clearGuestMode(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_GUEST_MODE, false)
            .apply()
    }

    fun isGuestMode(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_GUEST_MODE, false)
    }

    fun getActiveUserKey(context: Context): String {
        val currentUser = FirebaseAuth.getInstance().currentUser
        return if (isGuestMode(context)) {
            getGuestId(context)
        } else {
            currentUser?.uid ?: getGuestId(context)
        }
    }

    fun hasSignedInUser(): Boolean {
        if (!BuildConfig.ENABLE_FIREBASE_AUTH) {
            return false
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        return currentUser != null && !currentUser.isAnonymous
    }

    fun ensureBackendSession(
        context: Context,
        onReady: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (!BuildConfig.ENABLE_FIREBASE_AUTH) {
            onReady()
            return
        }

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            onReady()
            return
        }

        if (!isGuestMode(context)) {
            onFailure(context.getString(R.string.error_session_unavailable))
            return
        }

        auth.signInAnonymously()
            .addOnSuccessListener {
                onReady()
            }
            .addOnFailureListener { error ->
                onFailure(getAuthErrorMessage(context, error.message, R.string.error_guest_session_failed))
            }
    }

    fun getAuthErrorMessage(context: Context, rawMessage: String?, fallbackResId: Int): String {
        val normalized = rawMessage.orEmpty().lowercase()
        return when {
            "configuration_not_found" in normalized -> context.getString(R.string.error_auth_configuration_not_found)
            "operation_not_allowed" in normalized -> context.getString(R.string.error_auth_provider_disabled)
            else -> rawMessage ?: context.getString(fallbackResId)
        }
    }

    fun getDisplayName(context: Context): String {
        if (!BuildConfig.ENABLE_FIREBASE_AUTH || isGuestMode(context)) {
            return context.getString(R.string.profile_guest_name)
        }
        val currentUser = FirebaseAuth.getInstance().currentUser
        return currentUser?.email
            ?.substringBefore("@")
            ?.replaceFirstChar { it.uppercase() }
            ?: context.getString(R.string.profile_guest_name)
    }

    private fun getGuestId(context: Context): String {
        val existing = prefs(context).getString(KEY_GUEST_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val guestId = "guest-" + UUID.randomUUID().toString().take(8)
        prefs(context).edit().putString(KEY_GUEST_ID, guestId).apply()
        return guestId
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}