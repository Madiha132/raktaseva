package com.example.raktaseva

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

// ═══════════════════════════════════════════════════════
// DATA MODELS
// ═══════════════════════════════════════════════════════

data class DonorModel(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val bloodGroup: String = "",
    val location: String = "",
    val age: Int = 0,
    val registeredDate: String = "",
    val available: Boolean = true
)

data class AlertModel(
    val id: String = "",
    val bloodGroup: String = "",
    val hospital: String = "",
    val units: Int = 0,
    val contact: String = "",
    val timestamp: String = "",
    val donorComing: Boolean = false,
    val alertNumber: Int = 0
)

// ═══════════════════════════════════════════════════════
// FIRESTORE HELPER
// ═══════════════════════════════════════════════════════

object FirestoreHelper {

    private val db = FirebaseFirestore.getInstance()

    // ── DONORS ──────────────────────────────────────────

    fun addDonor(
        name: String, phone: String, blood: String,
        location: String, age: Int, date: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val donor = hashMapOf(
            "name"           to name,
            "phone"          to phone,
            "bloodGroup"     to blood,
            "location"       to location,
            "age"            to age,
            "registeredDate" to date,
            "available"      to true
        )
        db.collection("donors").add(donor)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Failed") }
    }

    fun getDonors(
        bloodFilter: String?,
        onResult: (List<DonorModel>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        var query: Query = db.collection("donors").whereEqualTo("available", true)
        if (bloodFilter != null) query = query.whereEqualTo("bloodGroup", bloodFilter)
        query.get()
            .addOnSuccessListener { result ->
                val list = result.documents.map { doc ->
                    DonorModel(
                        id             = doc.id,
                        name           = doc.getString("name") ?: "",
                        phone          = doc.getString("phone") ?: "",
                        bloodGroup     = doc.getString("bloodGroup") ?: "",
                        location       = doc.getString("location") ?: "",
                        age            = (doc.getLong("age") ?: 0L).toInt(),
                        registeredDate = doc.getString("registeredDate") ?: "",
                        available      = doc.getBoolean("available") ?: true
                    )
                }
                onResult(list)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Failed") }
    }

    /** Returns total count of available (registered) donors */
    fun getDonorCount(onResult: (Int) -> Unit) {
        db.collection("donors").whereEqualTo("available", true).get()
            .addOnSuccessListener { onResult(it.size()) }
            .addOnFailureListener { onResult(0) }
    }

    /** Fetch phone numbers of all donors — used for SMS broadcast */
    fun getAllDonorPhones(bloodGroup: String?, onResult: (List<String>) -> Unit) {
        var query: Query = db.collection("donors")
        if (bloodGroup != null) query = query.whereEqualTo("bloodGroup", bloodGroup)
        query.get()
            .addOnSuccessListener { snap ->
                val phones = snap.documents.mapNotNull { it.getString("phone") }
                onResult(phones)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    // ── ALERTS ──────────────────────────────────────────

    fun addAlert(
        blood: String, hospital: String, units: Int,
        contact: String, timestamp: String,
        alertNumber: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val alert = hashMapOf(
            "bloodGroup"  to blood,
            "hospital"    to hospital,
            "units"       to units,
            "contact"     to contact,
            "timestamp"   to timestamp,
            "donorComing" to false,
            "alertNumber" to alertNumber
        )
        db.collection("alerts").add(alert)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Failed") }
    }

    fun getAlerts(
        onResult: (List<AlertModel>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection("alerts")
            .orderBy("alertNumber", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val list = result.documents.map { doc ->
                    AlertModel(
                        id          = doc.id,
                        bloodGroup  = doc.getString("bloodGroup") ?: "",
                        hospital    = doc.getString("hospital") ?: "",
                        units       = (doc.getLong("units") ?: 0L).toInt(),
                        contact     = doc.getString("contact") ?: "",
                        timestamp   = doc.getString("timestamp") ?: "",
                        donorComing = doc.getBoolean("donorComing") ?: false,
                        alertNumber = (doc.getLong("alertNumber") ?: 0L).toInt()
                    )
                }
                onResult(list)
            }
            .addOnFailureListener { e -> onFailure(e.message ?: "Failed") }
    }

    /** Returns total alert count + 1 as the next alert number */
    fun getNextAlertNumber(onResult: (Int) -> Unit) {
        db.collection("alerts").get()
            .addOnSuccessListener { onResult(it.size() + 1) }
            .addOnFailureListener { onResult(1) }
    }

    fun markDonorComing(
        alertId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection("alerts").document(alertId)
            .update("donorComing", true)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e.message ?: "Failed") }
    }

    fun getActiveAlertCount(onResult: (Int) -> Unit) {
        db.collection("alerts").whereEqualTo("donorComing", false).get()
            .addOnSuccessListener { onResult(it.size()) }
            .addOnFailureListener { onResult(0) }
    }
}