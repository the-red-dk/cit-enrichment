package com.daksha.cit.enrichment

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object LocalClaimStore {
    private const val PREFS_NAME = "cit_local_claims"
    private const val KEY_CLAIMS = "claims"

    data class ClaimRecord(
        val id: String,
        val claimType: String,
        val title: String,
        val nptelCredits: Int,
        val claimDate: String,
        val status: String,
        val amountPaid: String,
        val remarks: String,
        val certificateUrl: String,
        val paymentUrl: String,
        val certificateStoragePath: String,
        val paymentStoragePath: String,
        val certificateFileName: String,
        val paymentFileName: String,
        val submittedAtEpochMs: Long
    )

    fun saveClaim(context: Context, data: Map<String, Any?>): ClaimRecord {
        val claims = loadClaimJsonArray(context)
        val id = UUID.randomUUID().toString()
        val submittedAtEpochMs = System.currentTimeMillis()
        val json = JSONObject().apply {
            put("id", id)
            put("claimType", data["claimType"] as? String ?: "")
            put("title", data["title"] as? String ?: "")
            put("nptelCredits", (data["nptelCredits"] as? Number)?.toInt() ?: 0)
            put("claimDate", data["claimDate"] as? String ?: "")
            put("status", data["status"] as? String ?: "")
            put("amountPaid", data["amountPaid"] as? String ?: "")
            put("remarks", data["remarks"] as? String ?: "")
            put("certificateUrl", data["certificateUrl"] as? String ?: "")
            put("paymentUrl", data["paymentUrl"] as? String ?: "")
            put("certificateStoragePath", data["certificateStoragePath"] as? String ?: "")
            put("paymentStoragePath", data["paymentStoragePath"] as? String ?: "")
            put("certificateFileName", data["certificateFileName"] as? String ?: "")
            put("paymentFileName", data["paymentFileName"] as? String ?: "")
            put("submittedAtEpochMs", submittedAtEpochMs)
        }
        claims.put(json)
        persistClaims(context, claims)
        return jsonToClaim(json)
    }

    fun getClaims(context: Context): List<ClaimRecord> {
        val claims = loadClaimJsonArray(context)
        return buildList {
            for (index in 0 until claims.length()) {
                val json = claims.optJSONObject(index) ?: continue
                add(jsonToClaim(json))
            }
        }.sortedByDescending { it.submittedAtEpochMs }
    }

    fun getClaim(context: Context, claimId: String): ClaimRecord? {
        return getClaims(context).firstOrNull { it.id == claimId }
    }

    private fun jsonToClaim(json: JSONObject): ClaimRecord {
        return ClaimRecord(
            id = json.optString("id"),
            claimType = json.optString("claimType"),
            title = json.optString("title"),
            nptelCredits = json.optInt("nptelCredits", 0),
            claimDate = json.optString("claimDate"),
            status = json.optString("status"),
            amountPaid = json.optString("amountPaid"),
            remarks = json.optString("remarks"),
            certificateUrl = json.optString("certificateUrl"),
            paymentUrl = json.optString("paymentUrl"),
            certificateStoragePath = json.optString("certificateStoragePath"),
            paymentStoragePath = json.optString("paymentStoragePath"),
            certificateFileName = json.optString("certificateFileName"),
            paymentFileName = json.optString("paymentFileName"),
            submittedAtEpochMs = json.optLong("submittedAtEpochMs")
        )
    }

    private fun loadClaimJsonArray(context: Context): JSONArray {
        val raw = prefs(context).getString(KEY_CLAIMS, null).orEmpty()
        return if (raw.isBlank()) JSONArray() else JSONArray(raw)
    }

    private fun persistClaims(context: Context, claims: JSONArray) {
        prefs(context).edit().putString(KEY_CLAIMS, claims.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}