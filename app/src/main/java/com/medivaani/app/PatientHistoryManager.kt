package com.medivaani.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PatientProfile(
    val name: String,
    val age: Int,
    val sex: String,
    val mrn: String,
    val photoUri: String = ""
)

data class ReportScanRecord(
    val id: String,
    val date: String,
    val title: String,
    val summary: String,
    val rawText: String,
    val type: String // "lab", "prescription", "visit", "imaging"
)

class PatientHistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("medivaani_patient_history", Context.MODE_PRIVATE)

    var profile = PatientProfile("A. Okafor", 54, "Female", "004-2291", "")
    private val reports = mutableListOf<ReportScanRecord>()

    init {
        loadHistory()
    }

    fun updateProfile(name: String, age: Int, sex: String, mrn: String, photoUri: String = profile.photoUri) {
        profile = PatientProfile(name, age, sex, mrn, photoUri)
        val obj = JSONObject().apply {
            put("name", profile.name)
            put("age", profile.age)
            put("sex", profile.sex)
            put("mrn", profile.mrn)
            put("photoUri", profile.photoUri)
        }
        prefs.edit().putString("profile_json", obj.toString()).apply()
    }

    fun addReport(title: String, summary: String, rawText: String, type: String = "lab") {
        val id = "rep_" + System.currentTimeMillis()
        val date = android.text.format.DateFormat.format("yyyy-MM-dd", System.currentTimeMillis()).toString()
        reports.add(0, ReportScanRecord(id, date, title, summary, rawText, type))
        saveHistory()
    }

    fun clearHistory() {
        reports.clear()
        prefs.edit().remove("reports_json").apply()
    }

    fun getReports(): List<ReportScanRecord> = reports

    fun getHistorySummaryText(): String {
        val sb = StringBuilder()
        sb.append("Patient: ${profile.name} (${profile.age} yrs, ${profile.sex}, MRN: ${profile.mrn})\n")
        sb.append("Total Scanned Reports: ${reports.size}\n\n")
        if (reports.isEmpty()) {
            sb.append("No clinical reports scanned yet.")
        } else {
            for (r in reports) {
                sb.append("• [${r.date}] ${r.title}\n  Summary: ${r.summary}\n\n")
            }
        }
        return sb.toString().trim()
    }

    private fun saveHistory() {
        val arr = JSONArray()
        for (r in reports) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("date", r.date)
            o.put("title", r.title)
            o.put("summary", r.summary)
            o.put("rawText", r.rawText)
            o.put("type", r.type)
            arr.put(o)
        }
        prefs.edit().putString("reports_json", arr.toString()).apply()
    }

    private fun loadHistory() {
        reports.clear()
        val profileStr = prefs.getString("profile_json", null)
        if (profileStr != null) {
            try {
                val o = JSONObject(profileStr)
                profile = PatientProfile(
                    o.optString("name", "A. Okafor"),
                    o.optInt("age", 54),
                    o.optString("sex", "Female"),
                    o.optString("mrn", "004-2291"),
                    o.optString("photoUri", "")
                )
            } catch (_: Exception) {}
        }

        val jsonStr = prefs.getString("reports_json", null) ?: return
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                reports.add(
                    ReportScanRecord(
                        o.getString("id"),
                        o.getString("date"),
                        o.getString("title"),
                        o.getString("summary"),
                        o.getString("rawText"),
                        o.optString("type", "lab")
                    )
                )
            }
        } catch (_: Exception) {}
    }
}
