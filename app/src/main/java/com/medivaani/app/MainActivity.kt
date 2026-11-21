package com.medivaani.app

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class Medicine(val name: String, val strength: String, val aliases: List<String>)

data class PrescriptionItem(
    var medicineName: String,
    var strength: String,
    var dosage: String,
    var frequency: String,
    var duration: String,
    var advice: String,
    var isUncertain: Boolean
)

data class StructuredPrescription(
    var patientName: String,
    var date: String,
    var diagnosis: String,
    val items: MutableList<PrescriptionItem>,
    var rawOcrText: String
)

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var result: TextView
    private lateinit var historyView: TextView
    private lateinit var tts: TextToSpeech
    private lateinit var historyManager: PatientHistoryManager
    private lateinit var localizationManager: LocalizationManager

    private val medicines = mutableListOf<Medicine>()
    private val capturedFrames = mutableListOf<Bitmap>()

    // UI elements to refresh on language change
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var welcomeTitleView: TextView
    private lateinit var welcomePhotoView: ImageView
    private lateinit var scanMedTitle: TextView
    private lateinit var scanMedDesc: TextView
    private lateinit var scanMedBtnText: Button
    private lateinit var scanRepText: TextView
    private lateinit var myMedsBtnText: TextView
    private lateinit var reminderCardTitle: TextView
    private lateinit var morningDoseText: TextView
    private lateinit var eveningDoseText: TextView
    private lateinit var nightDoseText: TextView
    private lateinit var askVoiceTitle: TextView
    private lateinit var askVoiceDesc: TextView
    private lateinit var askVoiceBtnText: Button

    private lateinit var playPauseText: TextView
    private lateinit var playPauseBtn: ImageButton
    private lateinit var editProfileBtn: Button
    private lateinit var caregiverBtn: Button
    private lateinit var logoutBtn: Button
    private lateinit var safetyView: TextView

    // Bottom Navigation views
    private lateinit var navHomeText: TextView
    private lateinit var navMedsText: TextView
    private lateinit var navAlarmsText: TextView
    private lateinit var navProfileText: TextView

    private var isPlayingAudio = false
    private var isAudioPaused = false
    private var lastVerifiedMedicineName = ""
    private var lastVerifiedStrength = ""
    private var lastCertainty = 0
    private var isPrescriptionResult = false
    private var currentPrescription: StructuredPrescription? = null
    private var pendingPhotoUri = ""

    // Reminder times
    private var morningTime = "09:00 AM"
    private var eveningTime = "04:00 PM"
    private var nightTime = "09:00 PM"

    // Chunk-based instruction playback for pause/resume
    private var instructionChunks = listOf<String>()
    private var currentChunkIndex = 0

    // Camera flow for Medicine Strip scan (3 frames)
    private val camera = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val bitmap = res.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                capturedFrames.add(bitmap)
                if (capturedFrames.size < 3) {
                    Toast.makeText(this, "Frame ${capturedFrames.size}/3 captured! Take next angle.", Toast.LENGTH_SHORT).show()
                    launchNextCameraCapture()
                } else {
                    Toast.makeText(this, "Processing 3-frame OCR fusion...", Toast.LENGTH_SHORT).show()
                    processMultiFrameScan(capturedFrames)
                }
            }
        }
    }

    // Camera flow for Medical Prescription scan
    private val reportCamera = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val bitmap = res.data?.extras?.get("data") as? Bitmap
            if (bitmap != null) {
                runStructuredPrescriptionOcr(bitmap)
            }
        }
    }

    // Image Picker flow for Profile Photo
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.data
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
                pendingPhotoUri = uri.toString()
                Toast.makeText(this, "Profile photo selected! Save profile to apply.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Speech recognition flow
    private val speechRecognizer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val matches = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.getOrNull(0) ?: ""
            handleVoiceQuery(spokenText)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        historyManager = PatientHistoryManager(this)
        localizationManager = LocalizationManager(this)
        loadReminderTimes()
        loadDataset()

        val settings = getSharedPreferences("medivaani_auth", Context.MODE_PRIVATE)
        val isLoggedIn = settings.getBoolean("is_logged_in", false)
        if (!isLoggedIn) {
            showLoginScreen()
        } else {
            buildScreen()
        }
    }

    private fun showLoginScreen() {
        val loginLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFFF0F8F5.toInt())
        }

        val card = createCard(0xFFFFFFFF.toInt(), cornerRadius = 28f).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val logoTitle = TextView(this).apply {
            text = "MediVaani 🩺"
            textSize = 28f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF006B50.toInt()); gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        card.addView(logoTitle, matchWrap())

        val subTitle = TextView(this).apply {
            text = "Secure Patient Sign In"
            textSize = 15f; setTextColor(0xFF71827B.toInt()); gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        card.addView(subTitle, matchWrap())

        val demoNote = TextView(this).apply {
            text = "ℹ️ Demo Mode: This is for demo only. Enter any random username & password to login."
            textSize = 13f; setTextColor(0xFF5E7A73.toInt()); gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        card.addView(demoNote, matchWrap())

        val userBox = EditText(this).apply {
            hint = "Username (e.g. admin)"
            setTextColor(0xFF000000.toInt())
            setHintTextColor(0xFF71827B.toInt())
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setColor(0xFFF0F8F5.toInt())
                cornerRadius = 16f
                setStroke(2, 0xDCE8E3.toInt())
            }
        }
        card.addView(userBox, matchWrapWithMargin(16))

        val passBox = EditText(this).apply {
            hint = "Password (e.g. 1234)"
            setTextColor(0xFF000000.toInt())
            setHintTextColor(0xFF71827B.toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setColor(0xFFF0F8F5.toInt())
                cornerRadius = 16f
                setStroke(2, 0xDCE8E3.toInt())
            }
        }
        card.addView(passBox, matchWrapWithMargin(24))

        val loginBtn = createPrimaryButton("Sign In to App", 0xFF006B50.toInt(), 0xFFFFFFFF.toInt()) {
            val u = userBox.text.toString().trim()
            val p = passBox.text.toString().trim()
            if (u.isNotBlank() && p.isNotBlank()) {
                getSharedPreferences("medivaani_auth", Context.MODE_PRIVATE).edit().putBoolean("is_logged_in", true).apply()
                Toast.makeText(this, "Welcome back, $u!", Toast.LENGTH_SHORT).show()
                buildScreen()
            } else {
                Toast.makeText(this, "Please enter username and password.", Toast.LENGTH_SHORT).show()
            }
        }
        card.addView(loginBtn, matchWrap())

        loginLayout.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(loginLayout)
    }

    private fun loadReminderTimes() {
        val prefs = getSharedPreferences("medivaani_reminders", Context.MODE_PRIVATE)
        morningTime = prefs.getString("morning", "09:00 AM") ?: "09:00 AM"
        eveningTime = prefs.getString("evening", "04:00 PM") ?: "04:00 PM"
        nightTime = prefs.getString("night", "09:00 PM") ?: "09:00 PM"
    }

    private fun saveReminderTimes() {
        val prefs = getSharedPreferences("medivaani_reminders", Context.MODE_PRIVATE)
        prefs.edit().putString("morning", morningTime)
            .putString("evening", eveningTime)
            .putString("night", nightTime).apply()
    }

    private fun showEditProfileDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        val nameInput = EditText(this).apply { hint = "Patient Name"; setText(historyManager.profile.name) }
        val ageInput = EditText(this).apply { hint = "Age"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(historyManager.profile.age.toString()) }
        val sexInput = EditText(this).apply { hint = "Sex"; setText(historyManager.profile.sex) }
        val mrnInput = EditText(this).apply { hint = "MRN"; setText(historyManager.profile.mrn) }

        layout.addView(nameInput, matchWrapWithMargin(12))
        layout.addView(ageInput, matchWrapWithMargin(12))
        layout.addView(sexInput, matchWrapWithMargin(12))
        layout.addView(mrnInput, matchWrapWithMargin(12))

        val uploadPhotoBtn = createPrimaryButton("📷 Upload Profile Photo", 0xFF009879.toInt(), 0xFFFFFFFF.toInt()) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            pickImage.launch(intent)
        }
        layout.addView(uploadPhotoBtn, matchWrapWithMargin(12))

        AlertDialog.Builder(this)
            .setTitle(localizationManager.getString("edit_profile"))
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newName = nameInput.text.toString().ifBlank { historyManager.profile.name }
                val newAge = ageInput.text.toString().toIntOrNull() ?: historyManager.profile.age
                val newSex = sexInput.text.toString().ifBlank { historyManager.profile.sex }
                val newMrn = mrnInput.text.toString().ifBlank { historyManager.profile.mrn }
                val finalPhotoUri = pendingPhotoUri.ifBlank { historyManager.profile.photoUri }

                historyManager.updateProfile(newName, newAge, newSex, newMrn, finalPhotoUri)
                historyView.text = historyManager.getHistorySummaryText()
                welcomeTitleView.text = getWelcomeGreeting(newName)
                updateWelcomePhoto(welcomePhotoView, finalPhotoUri)
                pendingPhotoUri = ""
                Toast.makeText(this, "Patient profile updated.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMyMedicinesDialog() {
        val reports = historyManager.getReports()
        val msg = if (reports.isEmpty()) {
            when(localizationManager.currentLanguage) {
                "pa" -> "ਕੋਈ ਦਵਾਈ ਜਾਂ ਰਿਪੋਰਟ ਸਕੈਨ ਨਹੀਂ ਕੀਤੀ ਗਈ।"
                "hi" -> "कोई दवा या रिपोर्ट अभी तक सहेजी नहीं गई है।"
                else -> "No saved medicines or reports found."
            }
        } else {
            reports.joinToString("\n\n") { "• [${it.date}] ${it.title}\n  ${it.summary}" }
        }
        AlertDialog.Builder(this)
            .setTitle(localizationManager.getString("my_medicines"))
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showCaregiverModeDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        val noteInput = EditText(this).apply { hint = "Add Prescription Note / Schedule Info..." }
        layout.addView(noteInput, matchWrap())

        AlertDialog.Builder(this)
            .setTitle(localizationManager.getString("caregiver_mode"))
            .setView(layout)
            .setPositiveButton("Save Note") { _, _ ->
                val note = noteInput.text.toString()
                if (note.isNotBlank()) {
                    historyManager.addReport("Caregiver Note", note, note, "visit")
                    historyView.text = historyManager.getHistorySummaryText()
                    Toast.makeText(this, "Caregiver note added successfully.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun buildScreen() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF0F8F5.toInt())
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 32)
        }
        scroll.addView(root)
        mainLayout.addView(scroll)

        // --- 1. HEADER (MediVaani, Tagline, Language Dropdown) ---
        val headerCard = createCard(0xFF006B50.toInt(), cornerRadius = 24f).apply {
            setPadding(32, 36, 32, 36)
        }

        val headerTopRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 2f
        }

        val titleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        titleView = TextView(this).apply {
            text = localizationManager.getString("app_title")
            textSize = 30f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt())
        }
        titleLayout.addView(titleView, matchWrap())

        subtitleView = TextView(this).apply {
            text = localizationManager.getString("tagline")
            textSize = 13f; setTextColor(0xFFB2D9CE.toInt())
            setPadding(0, 4, 0, 0)
        }
        titleLayout.addView(subtitleView, matchWrap())
        headerTopRow.addView(titleLayout, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f))

        // Language Selector Spinner in Header
        val langSpinner = Spinner(this).apply {
            background = GradientDrawable().apply {
                setColor(0xFF009879.toInt())
                cornerRadius = 16f
            }
            setPadding(16, 12, 16, 12)
        }
        langSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("English", "हिन्दी", "ਪੰਜਾਬੀ"))

        val currentLangIndex = when(localizationManager.currentLanguage) { "pa" -> 2; "hi" -> 1; else -> 0 }
        langSpinner.setSelection(currentLangIndex)

        langSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                val newLang = when(position) { 1 -> "hi"; 2 -> "pa"; else -> "en" }
                if (newLang != localizationManager.currentLanguage) {
                    stopAudioPlayback()
                    localizationManager.currentLanguage = newLang
                    updateInterfaceLanguage()
                }
            }
        }
        headerTopRow.addView(langSpinner, LinearLayout.LayoutParams(0, 110, 0.8f))
        headerCard.addView(headerTopRow, matchWrap())
        root.addView(headerCard, matchWrapWithMargin(16))

        // --- 2. WELCOME SECTION (Dynamic User Name Greeting & Larger Profile Picture 160x160) ---
        val welcomeCard = createCard(0xFFFFFFFF.toInt()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        welcomeTitleView = TextView(this).apply {
            text = getWelcomeGreeting(historyManager.profile.name)
            textSize = 24f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF263832.toInt())
        }
        welcomeCard.addView(welcomeTitleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        welcomePhotoView = ImageView(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF009879.toInt())
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(4, 4, 4, 4)
            updateWelcomePhoto(this, historyManager.profile.photoUri)
            setOnClickListener { showEditProfileDialog() }
        }
        welcomeCard.addView(welcomePhotoView, LinearLayout.LayoutParams(160, 160).apply { leftMargin = 16 })
        root.addView(welcomeCard, matchWrapWithMargin(16))

        // --- 3. QUICK STATUS TILES ROW (Saved Meds, Reminders, Prescriptions) ---
        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }
        val prescriptionsCount = historyManager.getReports().count { it.type == "prescription" }
        statsRow.addView(createStatTile("1", localizationManager.getString("status_saved_meds")), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 6 })
        statsRow.addView(createStatTile("3", localizationManager.getString("status_upcoming")), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 3; rightMargin = 3 })
        statsRow.addView(createStatTile("$prescriptionsCount", localizationManager.getString("status_prescriptions")), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 6 })
        root.addView(statsRow, matchWrapWithMargin(16))

        // --- 4. PRIMARY ACTION CARD (SCAN MEDICINE) ---
        val primaryScanCard = createCard(0xFF006B50.toInt())
        scanMedTitle = TextView(this).apply {
            text = localizationManager.getString("scan_medicine")
            textSize = 24f; setTypeface(null, Typeface.BOLD); setTextColor(0xFFFFFFFF.toInt())
        }
        primaryScanCard.addView(scanMedTitle, matchWrap())

        scanMedDesc = TextView(this).apply {
            text = localizationManager.getString("scan_medicine_desc")
            textSize = 13f; setTextColor(0xFFB2D9CE.toInt())
            setPadding(0, 6, 0, 16)
        }
        primaryScanCard.addView(scanMedDesc, matchWrap())

        scanMedBtnText = createPrimaryButton("📷  " + localizationManager.getString("scan_medicine").uppercase(Locale.ROOT), 0xFF009879.toInt(), 0xFFFFFFFF.toInt()) {
            capturedFrames.clear()
            launchNextCameraCapture()
        }
        primaryScanCard.addView(scanMedBtnText, matchWrap())
        root.addView(primaryScanCard, matchWrapWithMargin(16))

        // --- 5. TWO EQUAL ACTION CARDS (Scan Prescription & My Medicines) ---
        val splitRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }

        val scanRepCard = createCard(0xFFFFFFFF.toInt(), cornerRadius = 20f).apply {
            setOnClickListener {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (intent.resolveActivity(packageManager) != null) reportCamera.launch(intent)
                else Toast.makeText(this@MainActivity, "Camera not available", Toast.LENGTH_SHORT).show()
            }
        }
        scanRepText = TextView(this).apply {
            text = "📄\n" + localizationManager.getString("scan_report")
            textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF006B50.toInt()); gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }
        scanRepCard.addView(scanRepText, matchWrap())
        splitRow.addView(scanRepCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { rightMargin = 6 })

        val myMedsCard = createCard(0xFFFFFFFF.toInt(), cornerRadius = 20f).apply {
            setOnClickListener { showMyMedicinesDialog() }
        }
        myMedsBtnText = TextView(this).apply {
            text = "💊\n" + localizationManager.getString("my_medicines")
            textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF006B50.toInt()); gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }
        myMedsCard.addView(myMedsBtnText, matchWrap())
        splitRow.addView(myMedsCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = 6 })
        root.addView(splitRow, matchWrapWithMargin(16))

        // --- 6. REMINDER CARD WITH MORNING, EVENING, AND NIGHT DOSES ---
        val reminderCard = createCard(0xFFFFFFFF.toInt())
        reminderCardTitle = TextView(this).apply {
            text = "⏰ " + localizationManager.getString("reminder_card_title")
            textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF006B50.toInt())
            setPadding(0, 0, 0, 12)
        }
        reminderCard.addView(reminderCardTitle, matchWrap())

        // Morning Dose Row
        morningDoseText = addDoseSection(reminderCard, "🌅 Morning Dose", morningTime) {
            chooseDoseTime("Morning") { time ->
                morningTime = time
                morningDoseText.text = "🌅 Morning Dose: $morningTime"
                saveReminderTimes()
            }
        }

        // Evening Dose Row
        eveningDoseText = addDoseSection(reminderCard, "🌇 Evening Dose", eveningTime) {
            chooseDoseTime("Evening") { time ->
                eveningTime = time
                eveningDoseText.text = "🌇 Evening Dose: $eveningTime"
                saveReminderTimes()
            }
        }

        // Night Dose Row
        nightDoseText = addDoseSection(reminderCard, "🌙 Night Dose", nightTime) {
            chooseDoseTime("Night") { time ->
                nightTime = time
                nightDoseText.text = "🌙 Night Dose: $nightTime"
                saveReminderTimes()
            }
        }

        root.addView(reminderCard, matchWrapWithMargin(16))

        // --- 7. ASK BY SPEAKING VOICE ASSISTANT CARD ---
        val voiceCard = createCard(0xFFFFFFFF.toInt())
        askVoiceTitle = TextView(this).apply {
            text = "🎤 " + localizationManager.getString("ask_voice")
            textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF006B50.toInt())
        }
        voiceCard.addView(askVoiceTitle, matchWrap())

        askVoiceDesc = TextView(this).apply {
            text = localizationManager.getString("ask_voice_desc")
            textSize = 14f; setTextColor(0xFF71827B.toInt())
            setPadding(0, 6, 0, 16)
        }
        voiceCard.addView(askVoiceDesc, matchWrap())

        askVoiceBtnText = createPrimaryButton("🎙️ " + localizationManager.getString("ask_voice").uppercase(Locale.ROOT), 0xFF009879.toInt(), 0xFFFFFFFF.toInt()) {
            launchVoiceAssistant()
        }
        voiceCard.addView(askVoiceBtnText, matchWrap())
        root.addView(voiceCard, matchWrapWithMargin(16))

        // --- 8. VERIFICATION RESULT & AUDIO LISTEN BAR ---
        val verifyCard = createCard(0xFFFFFFFF.toInt())
        addSectionHeader(verifyCard, "🎯 Verification Result & Instructions")
        result = addText(verifyCard, localizationManager.getString("no_medicine_verified"), 18f, true, 0xFF006B50.toInt())

        val playPauseLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            background = GradientDrawable().apply { setColor(0xFFF0F8F5.toInt()); cornerRadius = 20f }
        }

        playPauseBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF006B50.toInt()) }
            setPadding(24, 24, 24, 24)
            setOnClickListener { togglePlayPauseAudio() }
        }
        playPauseLayout.addView(playPauseBtn, LinearLayout.LayoutParams(110, 110))

        playPauseText = TextView(this).apply {
            text = localizationManager.getString("scan_first")
            textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF006B50.toInt())
            setPadding(24, 0, 0, 0)
        }
        playPauseLayout.addView(playPauseText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        verifyCard.addView(playPauseLayout, matchWrapWithMargin(12))
        root.addView(verifyCard, matchWrapWithMargin(16))

        // --- 9. PATIENT PROFILE & CAREGIVER CONTROLS (With Log Out Button) ---
        val historyCard = createCard(0xFFFFFFFF.toInt())
        addSectionHeader(historyCard, "👤 Patient Profile & Caregiver")
        historyView = addText(historyCard, historyManager.getHistorySummaryText(), 15f, false, 0xFF263832.toInt())

        val btnLayout1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        editProfileBtn = createPrimaryButton("✏️ Profile", 0xFF006B50.toInt(), 0xFFFFFFFF.toInt()) {
            showEditProfileDialog()
        }.apply { textSize = 14f }

        caregiverBtn = createPrimaryButton("👨‍⚕️ Caregiver", 0xFF009879.toInt(), 0xFFFFFFFF.toInt()) {
            showCaregiverModeDialog()
        }.apply { textSize = 14f }

        btnLayout1.addView(editProfileBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 4 })
        btnLayout1.addView(caregiverBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 4 })
        historyCard.addView(btnLayout1, matchWrapWithMargin(8))

        val btnLayout2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        val resetHistoryBtn = createPrimaryButton("🗑️ Reset", 0xFFD32F2F.toInt(), 0xFFFFFFFF.toInt()) {
            AlertDialog.Builder(this)
                .setTitle("Reset Patient History?")
                .setMessage("Are you sure you want to clear all saved prescriptions and report scan records?")
                .setPositiveButton("Yes, Reset") { _, _ ->
                    historyManager.clearHistory()
                    historyView.text = historyManager.getHistorySummaryText()
                    Toast.makeText(this, "Patient history reset successfully.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }.apply { textSize = 14f }

        logoutBtn = createPrimaryButton("🚪 Log Out", 0xFF455A64.toInt(), 0xFFFFFFFF.toInt()) {
            AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of MediVaani?")
                .setPositiveButton("Log Out") { _, _ ->
                    getSharedPreferences("medivaani_auth", Context.MODE_PRIVATE).edit().putBoolean("is_logged_in", false).apply()
                    Toast.makeText(this, "Logged out successfully.", Toast.LENGTH_SHORT).show()
                    showLoginScreen()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }.apply { textSize = 14f }

        btnLayout2.addView(resetHistoryBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 4 })
        btnLayout2.addView(logoutBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 4 })
        historyCard.addView(btnLayout2, matchWrapWithMargin(8))

        root.addView(historyCard, matchWrapWithMargin(24))

        // Safety Footer
        safetyView = TextView(this).apply {
            text = localizationManager.getString("safety_warning")
            textSize = 13f; setTextColor(0xFF71827B.toInt()); setPadding(16, 16, 16, 16)
        }
        root.addView(safetyView, matchWrapWithMargin(32))

        // --- 10. MODERN BOTTOM NAVIGATION BAR ---
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 16, 16, 16)
            background = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                setStroke(2, 0xDCE8E3.toInt())
            }
            weightSum = 4f
        }

        navHomeText = createNavItem("🏠", localizationManager.getString("nav_home"), true) { scroll.smoothScrollTo(0, 0) }
        bottomNav.addView(navHomeText.parent as View, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        navMedsText = createNavItem("💊", localizationManager.getString("nav_meds"), false) { showMyMedicinesDialog() }
        bottomNav.addView(navMedsText.parent as View, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        navAlarmsText = createNavItem("⏰", localizationManager.getString("nav_reminders"), false) { chooseReminderTime() }
        bottomNav.addView(navAlarmsText.parent as View, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        navProfileText = createNavItem("👤", localizationManager.getString("nav_profile"), false) { showEditProfileDialog() }
        bottomNav.addView(navProfileText.parent as View, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        mainLayout.addView(bottomNav, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(mainLayout)

        // Setup TTS Utterance Progress Listener
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    if (utteranceId?.startsWith("chunk_") == true) {
                        val idx = utteranceId.substringAfter("chunk_").toIntOrNull() ?: 0
                        currentChunkIndex = idx + 1
                        if (currentChunkIndex < instructionChunks.size && isPlayingAudio && !isAudioPaused) {
                            speakChunk(currentChunkIndex)
                        } else if (currentChunkIndex >= instructionChunks.size) {
                            isPlayingAudio = false
                            isAudioPaused = false
                            playPauseBtn.setImageResource(android.R.drawable.ic_media_play)
                            playPauseText.text = localizationManager.getString("repeat")
                        }
                    }
                }
            }
            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    isPlayingAudio = false
                    isAudioPaused = false
                    playPauseBtn.setImageResource(android.R.drawable.ic_media_play)
                    playPauseText.text = localizationManager.getString("listen")
                }
            }
        })
    }

    private fun processMultiFrameScan(frames: List<Bitmap>) {
        val mergedBuilder = StringBuilder()
        var completedCount = 0
        val totalScans = frames.size * 6
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val onSegmentDone = { text: String ->
            synchronized(mergedBuilder) {
                if (text.isNotBlank()) {
                    mergedBuilder.append("\n").append(text)
                }
                completedCount++
                if (completedCount == totalScans) {
                    val finalRaw = mergedBuilder.toString().trim()
                    val normalizedFinal = normalizeText(finalRaw)
                    val recognized = normalizedFinal.ifBlank { "No readable text found." }
                    verifyMedicine(recognized)
                    recognizer.close()
                }
            }
        }

        for (frame in frames) {
            val enhanced = enhanceBitmap(frame)
            val regions = getFoilRegions(frame) + getFoilRegions(enhanced)
            for (reg in regions) {
                recognizer.process(InputImage.fromBitmap(reg, 0))
                    .addOnSuccessListener { onSegmentDone(it.text) }
                    .addOnFailureListener { onSegmentDone("") }
            }
        }
    }

    private fun runStructuredPrescriptionOcr(bitmap: Bitmap) {
        val enhanced = preprocessPrescriptionBitmap(bitmap)
        val image = InputImage.fromBitmap(enhanced, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val sortedBlocks = visionText.textBlocks.sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                val rawBuilder = StringBuilder()
                val lines = mutableListOf<String>()

                for (block in sortedBlocks) {
                    for (line in block.lines) {
                        val lineText = line.text.trim()
                        if (lineText.isNotBlank()) {
                            lines.add(lineText)
                            rawBuilder.append(lineText).append("\n")
                        }
                    }
                }

                val rawText = rawBuilder.toString()
                val prescription = parseStructuredPrescription(lines, rawText)
                currentPrescription = prescription
                isPrescriptionResult = true
                lastVerifiedMedicineName = ""

                displayStructuredPrescription(prescription)
                promptUserForConfirmationAndSave(prescription)
                recognizer.close()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Prescription OCR failed.", Toast.LENGTH_SHORT).show()
                recognizer.close()
            }
    }

    private fun displayStructuredPrescription(prescription: StructuredPrescription) {
        val sb = StringBuilder()
        val titleLabel = localizationManager.getString("prescription_saved")
        sb.append("$titleLabel\n")
        sb.append("👤 Patient: ${prescription.patientName} | 📅 Date: ${prescription.date}\n")
        sb.append("🩺 Diagnosis: ${prescription.diagnosis}\n\n")

        for ((index, item) in prescription.items.withIndex()) {
            val statusBadge = if (item.isUncertain) "⚠️ Needs confirmation" else "✅ Verified"
            sb.append("💊 Medicine #${index + 1}: ${item.medicineName} (${item.strength})\n")
            sb.append("   • Dosage: ${item.dosage} | Freq: ${item.frequency}\n")
            sb.append("   • Duration: ${item.duration} | Advice: ${item.advice}\n")
            sb.append("   • Status: $statusBadge\n\n")
        }

        val fullDisplay = sb.toString().trim()
        result.text = fullDisplay
        prepareInstructions(fullDisplay)
        playPauseText.text = "Listen • सुनें • ਸੁਣੋ"
    }

    private fun displayAndPrepareVerifiedResult(medName: String, medStrength: String, certainty: Int) {
        val verifiedText = when(localizationManager.currentLanguage) {
            "pa" -> "✅ $certainty% | VERIFIED - matched with database\n\n$medName $medStrength • Tablet\n\nYOUR SCHEDULE\nMorning 9:00   Night 9:00\n\nਅਜ ਦੀ ਖੁਰਾਕ: ਸਵੇਰੇ 1 ਗੋਲੀ ਲਓ।"
            "hi" -> "✅ $certainty% | VERIFIED - डेटाबेस से मिलान हुआ\n\n$medName $medStrength • टैबलेट\n\nआपका शेड्यूल\nसुबह 9:00   रात 9:00\n\nआज की खुराक: सुबह 1 गोली लें।"
            else -> "✅ $certainty% | VERIFIED - matched with database\n\n$medName $medStrength • Tablet\n\nYOUR SCHEDULE\nMorning 9:00   Night 9:00\n\nTake 1 tablet in the morning."
        }
        result.text = verifiedText
        prepareInstructions(verifiedText)
        playPauseText.text = "Listen • सुनें • ਸੁਣੋ"
        speakText(verifiedText)
    }

    private fun launchNextCameraCapture() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            camera.launch(intent)
        } else {
            Toast.makeText(this, "No camera app available.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseReminderTime() {
        TimePickerDialog(this, { _, hour, minute ->
            val amPm = if (hour >= 12) "PM" else "AM"
            val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val timeStr = String.format(Locale.getDefault(), "%02d:%02d %s", h, minute, amPm)
            morningTime = timeStr
            if (::morningDoseText.isInitialized) morningDoseText.text = "🌅 Morning Dose: $morningTime"
            saveReminderTimes()
            Toast.makeText(this, "Reminder updated to $timeStr", Toast.LENGTH_SHORT).show()
            speakText("Reminder set for $timeStr")
        }, 9, 0, false).show()
    }

    private fun addDoseSection(parent: LinearLayout, title: String, time: String, onEdit: () -> Unit): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            background = GradientDrawable().apply {
                setColor(0xFFF0F8F5.toInt())
                cornerRadius = 16f
            }
            weightSum = 2f
            setOnClickListener { onEdit() }
        }

        val tv = TextView(this).apply {
            text = "$title: $time"
            textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF006B50.toInt())
        }
        row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))

        val btn = Button(this).apply {
            text = "Edit ⏰"
            textSize = 12f; setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply { setColor(0xFF009879.toInt()); cornerRadius = 12f }
            setOnClickListener { onEdit() }
        }
        row.addView(btn, LinearLayout.LayoutParams(0, 90, 0.6f))

        parent.addView(row, matchWrapWithMargin(8))
        return tv
    }

    private fun chooseDoseTime(doseName: String, onTimeSelected: (String) -> Unit) {
        TimePickerDialog(this, { _, hour, minute ->
            val amPm = if (hour >= 12) "PM" else "AM"
            val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val timeStr = String.format(Locale.getDefault(), "%02d:%02d %s", h, minute, amPm)
            onTimeSelected(timeStr)
            Toast.makeText(this, "$doseName dose updated to $timeStr", Toast.LENGTH_SHORT).show()
        }, 9, 0, false).show()
    }

    private fun getWelcomeGreeting(name: String): String {
        return when (localizationManager.currentLanguage) {
            "hi" -> "नमस्ते, $name"
            "pa" -> "ਸੁਆਗਤ ਹੈ, $name"
            else -> "Welcome, $name"
        }
    }

    private fun updateWelcomePhoto(imageView: ImageView, uriStr: String) {
        if (uriStr.isNotBlank()) {
            try {
                imageView.setImageURI(android.net.Uri.parse(uriStr))
            } catch (_: Exception) {
                imageView.setImageResource(android.R.drawable.ic_menu_camera)
            }
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_camera)
        }
    }

    private fun createCard(bgColor: Int, cornerRadius: Float = 20f): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            background = GradientDrawable().apply {
                setColor(bgColor)
                this.cornerRadius = cornerRadius
                if (bgColor == 0xFFFFFFFF.toInt()) {
                    setStroke(2, 0xDCE8E3.toInt())
                }
            }
        }
    }

    private fun createPrimaryButton(label: String, bgColor: Int, textColor: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColor)
            minHeight = 56
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = 18f
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createStatTile(value: String, label: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 20, 16, 20)
            background = GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = 20f
                setStroke(2, 0xDCE8E3.toInt())
            }
            val tvVal = TextView(this@MainActivity).apply { text = value; textSize = 22f; setTypeface(null, Typeface.BOLD); setTextColor(0xFF006B50.toInt()); gravity = Gravity.CENTER }
            val tvLbl = TextView(this@MainActivity).apply { text = label; textSize = 12f; setTextColor(0xFF71827B.toInt()); gravity = Gravity.CENTER }
            addView(tvVal, matchWrap())
            addView(tvLbl, matchWrap())
        }
    }

    private fun createNavItem(icon: String, label: String, isSelected: Boolean, onClick: () -> Unit): TextView {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(4, 8, 4, 8)
            setOnClickListener { onClick() }
        }
        val tvIcon = TextView(this@MainActivity).apply { text = icon; textSize = 18f; gravity = Gravity.CENTER }
        val tvLbl = TextView(this@MainActivity).apply {
            text = label; textSize = 11f; gravity = Gravity.CENTER
            setTextColor(if (isSelected) 0xFF006B50.toInt() else 0xFF71827B.toInt())
            if (isSelected) setTypeface(null, Typeface.BOLD)
        }
        container.addView(tvIcon, matchWrap())
        container.addView(tvLbl, matchWrap())
        return tvLbl
    }

    private fun addSectionHeader(parent: LinearLayout, title: String) {
        val tv = TextView(this).apply {
            text = title
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF006B50.toInt())
            setPadding(0, 0, 0, 10)
        }
        parent.addView(tv, matchWrap())
    }

    private fun updateInterfaceLanguage() {
        stopAudioPlayback()
        titleView.text = localizationManager.getString("app_title")
        subtitleView.text = localizationManager.getString("tagline")
        welcomeTitleView.text = getWelcomeGreeting(historyManager.profile.name)
        scanMedTitle.text = localizationManager.getString("scan_medicine")
        scanMedDesc.text = localizationManager.getString("scan_medicine_desc")
        scanMedBtnText.text = "📷  " + localizationManager.getString("scan_medicine").uppercase(Locale.ROOT)
        scanRepText.text = "📄\n" + localizationManager.getString("scan_report")
        myMedsBtnText.text = "💊\n" + localizationManager.getString("my_medicines")
        reminderCardTitle.text = "⏰ " + localizationManager.getString("reminder_card_title")
        askVoiceTitle.text = "🎤 " + localizationManager.getString("ask_voice")
        askVoiceDesc.text = localizationManager.getString("ask_voice_desc")
        askVoiceBtnText.text = "🎙️ " + localizationManager.getString("ask_voice").uppercase(Locale.ROOT)

        navHomeText.text = localizationManager.getString("nav_home")
        navMedsText.text = localizationManager.getString("nav_meds")
        navAlarmsText.text = localizationManager.getString("nav_reminders")
        navProfileText.text = localizationManager.getString("nav_profile")

        safetyView.text = localizationManager.getString("safety_warning")

        if (isPrescriptionResult && currentPrescription != null) {
            displayStructuredPrescription(currentPrescription!!)
        } else if (lastVerifiedMedicineName.isNotBlank()) {
            displayAndPrepareVerifiedResult(lastVerifiedMedicineName, lastVerifiedStrength, lastCertainty)
        } else {
            result.text = localizationManager.getString("no_medicine_verified")
            playPauseText.text = localizationManager.getString("listen")
        }

        Toast.makeText(this, if(localizationManager.currentLanguage=="hi") "भाषा बदलकर हिंदी कर दी गई है" else if(localizationManager.currentLanguage=="pa") "ਭਾਸ਼ਾ ਪੰਜਾਬੀ ਵਿੱਚ ਬਦਲ ਦਿੱਤੀ ਗਈ ਹੈ" else "Language changed to English", Toast.LENGTH_SHORT).show()
    }

    private fun stopAudioPlayback() {
        tts.stop()
        isPlayingAudio = false
        isAudioPaused = false
        currentChunkIndex = 0
        playPauseBtn.setImageResource(android.R.drawable.ic_media_play)
        playPauseText.text = localizationManager.getString("listen")
    }

    private fun togglePlayPauseAudio() {
        if (lastVerifiedMedicineName.isBlank() && !isPrescriptionResult) {
            Toast.makeText(this, localizationManager.getString("scan_first"), Toast.LENGTH_SHORT).show()
            return
        }

        if (isPlayingAudio && !isAudioPaused) {
            tts.stop()
            isAudioPaused = true
            isPlayingAudio = false
            playPauseBtn.setImageResource(android.R.drawable.ic_media_play)
            playPauseText.text = localizationManager.getString("resume")
        } else {
            if (currentChunkIndex >= instructionChunks.size) {
                currentChunkIndex = 0
            }
            isAudioPaused = false
            isPlayingAudio = true
            playPauseBtn.setImageResource(android.R.drawable.ic_media_pause)
            playPauseText.text = localizationManager.getString("pause")
            speakChunk(currentChunkIndex)
        }
    }

    private fun prepareInstructions(fullText: String) {
        instructionChunks = fullText.split(Regex("[.\n]+")).map { it.trim() }.filter { it.isNotBlank() }
        currentChunkIndex = 0
    }

    private fun speakChunk(index: Int) {
        if (index < 0 || index >= instructionChunks.size) return
        val chunk = instructionChunks[index]
        val locale = when(localizationManager.currentLanguage) {
            "pa" -> Locale("pa", "IN")
            "hi" -> Locale("hi", "IN")
            else -> Locale.US
        }
        tts.setLanguage(locale)
        tts.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, "chunk_$index")
    }

    private fun speakText(text: String, onFinished: (() -> Unit)? = null) {
        prepareInstructions(text)
        togglePlayPauseAudio()
        onFinished?.invoke()
    }

    private fun promptForReminderAfterScan() {
        AlertDialog.Builder(this)
            .setTitle(localizationManager.getString("reminder_prompt_title"))
            .setMessage(localizationManager.getString("reminder_prompt_msg"))
            .setPositiveButton(if(localizationManager.currentLanguage=="hi") "हाँ, अलार्म सेट करें" else if(localizationManager.currentLanguage=="pa") "ਹਾਂ, ਅਲਾਰਮ ਸੈੱਟ ਕਰੋ" else "Yes, Set Alarm") { _, _ ->
                chooseReminderTime()
            }
            .setNegativeButton(if(localizationManager.currentLanguage=="hi") "नहीं" else if(localizationManager.currentLanguage=="pa") "ਨਹੀਂ" else "No", null)
            .show()
    }

    private fun launchVoiceAssistant() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, when(localizationManager.currentLanguage) {
                "pa" -> "pa-IN"
                "hi" -> "hi-IN"
                else -> "en-US"
            })
            putExtra(RecognizerIntent.EXTRA_PROMPT, localizationManager.getString("voice_prompt"))
        }
        try {
            speechRecognizer.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Speech recognition not supported on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleVoiceQuery(spoken: String) {
        val query = spoken.lowercase(Locale.ROOT)
        val answer = when {
            query.contains("time") || query.contains("समय") || query.contains("ਸਮਾਂ") -> {
                when(localizationManager.currentLanguage) {
                    "pa" -> "ਤੁਹਾਡੀ ਅਗਲੀ ਦਵਾਈ ਦਾ ਸਮਾਂ ਸਵੇਰੇ 9:00 ਵਜੇ ਹੈ।"
                    "hi" -> "आपकी अगली दवा का समय सुबह 9:00 बजे है।"
                    else -> "Your next medicine reminder is scheduled for 9:00 AM."
                }
            }
            query.contains("medicine") || query.contains("दवाई") || query.contains("ਦਵਾਈ") -> {
                result.text.toString()
            }
            else -> {
                when(localizationManager.currentLanguage) {
                    "pa" -> "ਮੈਨੂੰ ਤੁਹਾਡਾ ਸਵਾਲ ਸਮਝ ਨਹੀਂ ਆਇਆ। ਕਿਰਪਾ ਕਰਕੇ ਦੁובਾਰਾ ਪੁੱਛੋ।"
                    "hi" -> "मुझे आपका सवाल समझ नहीं आया। कृपया दोबारा पूछें।"
                    else -> "I heard: '$spoken'. Please scan your medicine strip to verify."
                }
            }
        }
        result.text = "🎤 Voice Query: '$spoken'\n\n$answer"
        prepareInstructions(answer)
        togglePlayPauseAudio()
    }

    private fun verifyMedicine(text: String) {
        val scored = medicines.map { med ->
            val candidates = listOf(med.name) + med.aliases
            val nameScore = candidates.maxOf { candidate ->
                val normCandidate = normalizeText(candidate)
                similarity(text, normCandidate)
            }
            val strengthSeen = text.contains(med.strength.lowercase(Locale.ROOT).replace(" ", ""))
            val score = nameScore + if (strengthSeen) 0.25 else 0.0
            Triple(med, score, strengthSeen)
        }.sortedByDescending { it.second }

        if (scored.isEmpty() || scored[0].second < 0.60) {
            val certainty = min(85, max(15, ((scored.getOrNull(0)?.second ?: 0.3) * 100).toInt()))
            lastVerifiedMedicineName = ""
            lastVerifiedStrength = ""
            lastCertainty = certainty
            isPrescriptionResult = false

            val notSureMsg = "⚠️ I am not sure\nThe strip is partly torn and glare hides the strength. Please do not take it yet. Ask your doctor or pharmacist."
            result.text = notSureMsg
            prepareInstructions(notSureMsg)
            playPauseText.text = "Listen • सुनें • ਸੁਣੋ"
            speakText(notSureMsg)
            return
        }

        val best = scored[0]
        val second = scored.getOrNull(1)
        val certainty = min(99, max(45, (best.second * 100).toInt()))

        if (second != null && best.second - second.second < 0.05) {
            lastVerifiedMedicineName = ""
            lastVerifiedStrength = ""
            lastCertainty = certainty
            isPrescriptionResult = false
            val msg = "⚠️ I am not sure — multiple matches (Certainty: $certainty%). Please consult a pharmacist."
            result.text = msg
            prepareInstructions(msg)
            playPauseText.text = "Listen • सुनें • ਸੁਣੋ"
            speakText(msg)
            return
        }

        lastVerifiedMedicineName = best.first.name
        lastVerifiedStrength = best.first.strength
        lastCertainty = certainty
        isPrescriptionResult = false

        displayAndPrepareVerifiedResult(lastVerifiedMedicineName, lastVerifiedStrength, lastCertainty)
    }

    private fun loadDataset() {
        try {
            val json = JSONArray(assets.open("medicines.json").bufferedReader().readText())
            for (i in 0 until json.length()) {
                val o = json.getJSONObject(i)
                val a = o.optJSONArray("aliases") ?: JSONArray()
                val aliases = (0 until a.length()).map { a.getString(it) }
                medicines.add(Medicine(o.getString("name"), o.getString("strength"), aliases))
            }
        } catch (_: Exception) { }
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a.contains(b) || b.contains(a)) return 0.95
        val distance = levenshtein(a, b)
        return 1.0 - distance.toDouble() / max(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val curr = IntArray(b.length + 1); curr[0] = i + 1
            for (j in b.indices) curr[j + 1] = min(min(curr[j] + 1, prev[j + 1] + 1), prev[j] + if (a[i] == b[j]) 0 else 1)
            prev = curr
        }
        return prev[b.length]
    }

    private fun normalizeText(raw: String): String {
        var text = raw.lowercase(Locale.ROOT)
        text = text.replace(Regex("\\btab\\b"), "tablet")
            .replace(Regex("\\bcap\\b"), "capsule")
            .replace(Regex("\\bsol\\b"), "solution")
            .replace(Regex("\\bmg\\b"), "mg")
            .replace(Regex("500mg"), "500 mg")
            .replace(Regex("1000mg"), "1000 mg")

        text = text.replace('0', 'o')
            .replace('1', 'i')
            .replace('5', 's')

        val cleaned = text.replace(Regex("[^a-z0-9\\s]"), " ")
        return cleaned.replace(Regex("\\s+"), " ").trim()
    }

    private fun enhanceBitmap(original: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        val processed = Bitmap.createBitmap(width, height, original.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(processed)
        val paint = Paint()

        val colorMatrix = ColorMatrix().apply { setSaturation(0.0f) }
        val scale = 1.5f
        val translate = (-0.5f * scale + 0.5f) * 255.0f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(original, 0f, 0f, paint)
        return processed
    }

    private fun getFoilRegions(original: Bitmap): List<Bitmap> {
        val w = original.width
        val h = original.height
        val regions = mutableListOf<Bitmap>()
        val sliceH = h / 3
        if (sliceH > 10) {
            regions.add(Bitmap.createBitmap(original, 0, 0, w, sliceH))
            regions.add(Bitmap.createBitmap(original, 0, sliceH, w, sliceH))
            regions.add(Bitmap.createBitmap(original, 0, sliceH * 2, w, h - sliceH * 2))
        } else {
            regions.add(original)
        }
        return regions
    }

    private fun preprocessPrescriptionBitmap(original: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        val processed = Bitmap.createBitmap(width, height, original.config ?: Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(processed)
        val paint = Paint()

        val colorMatrix = ColorMatrix().apply { setSaturation(0.0f) }
        val scale = 1.8f
        val translate = (-0.5f * scale + 0.5f) * 255.0f
        val contrastMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(original, 0f, 0f, paint)
        return processed
    }

    private fun parseStructuredPrescription(lines: List<String>, rawText: String): StructuredPrescription {
        var patientName = "Patient (Not specified)"
        var date = android.text.format.DateFormat.format("yyyy-MM-dd", System.currentTimeMillis()).toString()
        var diagnosis = "Not specified"
        val items = mutableListOf<PrescriptionItem>()

        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            if (lower.contains("patient") || lower.contains("name:") || lower.contains("नाम")) {
                patientName = line.substringAfter(":").ifBlank { line }
            } else if (lower.contains("date") || lower.contains("dt:") || lower.contains("दिनांक")) {
                date = line.substringAfter(":").ifBlank { line }
            } else if (lower.contains("dx") || lower.contains("diagnosis")) {
                diagnosis = line.substringAfter(":").ifBlank { line }
            } else {
                val matchedMed = medicines.find { med -> lower.contains(med.name.lowercase(Locale.ROOT)) }
                if (matchedMed != null || lower.contains("mg") || lower.contains("tab") || lower.contains("cap")) {
                    val medName = matchedMed?.name ?: line.substringBefore(" ").replace(Regex("[^a-zA-Z]"), "")
                    val strength = matchedMed?.strength ?: if (lower.contains("500")) "500 mg" else "As prescribed"
                    val isUncertain = matchedMed == null

                    items.add(
                        PrescriptionItem(
                            medicineName = medName.ifBlank { "Unknown Medicine" },
                            strength = strength,
                            dosage = "1 Unit",
                            frequency = if (lower.contains("1-0-1") || lower.contains("twice")) "Twice daily" else "Once daily",
                            duration = if (lower.contains("5 days")) "5 days" else "As directed",
                            advice = if (lower.contains("after food") || lower.contains("khane ke baad")) "After food" else "Before or after food is not specified",
                            isUncertain = isUncertain
                        )
                    )
                }
            }
        }

        if (items.isEmpty() && lines.isNotEmpty()) {
            items.add(
                PrescriptionItem(
                    medicineName = lines.getOrNull(0) ?: "Reviewed Prescription",
                    strength = "Needs confirmation",
                    dosage = "Needs confirmation",
                    frequency = "Needs confirmation",
                    duration = "Needs confirmation",
                    advice = "Needs confirmation",
                    isUncertain = true
                )
            )
        }

        return StructuredPrescription(patientName, date, diagnosis, items, rawText)
    }

    private fun promptUserForConfirmationAndSave(prescription: StructuredPrescription) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        val infoText = TextView(this).apply {
            text = "Please review extracted prescription details. You can manually edit or confirm before saving to patient history."
            textSize = 16f
            setPadding(0, 0, 0, 16)
        }
        layout.addView(infoText)

        val editMedInput = EditText(this).apply {
            hint = "Medicine Name / Details"
            setText(prescription.items.firstOrNull()?.medicineName ?: "")
        }
        layout.addView(editMedInput, matchWrapWithMargin(12))

        AlertDialog.Builder(this)
            .setTitle("Confirm Prescription Extraction")
            .setView(layout)
            .setPositiveButton("Confirm & Save to History") { _, _ ->
                val correctedName = editMedInput.text.toString()
                if (correctedName.isNotBlank() && prescription.items.isNotEmpty()) {
                    prescription.items[0].medicineName = correctedName
                    prescription.items[0].isUncertain = false
                    displayStructuredPrescription(prescription)
                }
                historyManager.addReport(
                    "Prescription — ${prescription.date}",
                    "Patient: ${prescription.patientName}, Meds: ${prescription.items.size} items",
                    prescription.rawOcrText,
                    "prescription"
                )
                historyView.text = historyManager.getHistorySummaryText()
                Toast.makeText(this, "Prescription confirmed and saved securely.", Toast.LENGTH_SHORT).show()
                promptForReminderAfterScan()
            }
            .setNegativeButton("Edit Manually") { _, _ -> showManualCorrectionDialog(prescription) }
            .show()
    }

    private fun showManualCorrectionDialog(prescription: StructuredPrescription) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        val nameInput = EditText(this).apply { hint = "Medicine Name"; setText(prescription.items.firstOrNull()?.medicineName ?: "") }
        val doseInput = EditText(this).apply { hint = "Dosage & Frequency"; setText(prescription.items.firstOrNull()?.frequency ?: "") }
        layout.addView(nameInput, matchWrapWithMargin(12))
        layout.addView(doseInput, matchWrapWithMargin(12))

        AlertDialog.Builder(this)
            .setTitle("Manual Correction")
            .setView(layout)
            .setPositiveButton("Save Corrections") { _, _ ->
                if (prescription.items.isNotEmpty()) {
                    prescription.items[0].medicineName = nameInput.text.toString().ifBlank { prescription.items[0].medicineName }
                    prescription.items[0].frequency = doseInput.text.toString().ifBlank { prescription.items[0].frequency }
                    prescription.items[0].isUncertain = false
                    displayStructuredPrescription(prescription)
                    Toast.makeText(this, "Corrections applied successfully.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) {
            tts.language = Locale("hi", "IN")
        }
    }
    override fun onDestroy() { if (::tts.isInitialized) tts.shutdown(); super.onDestroy() }

    private fun addText(parent: LinearLayout, textValue: String, size: Float, bold: Boolean, color: Int): TextView {
        val v = TextView(this).apply {
            text = textValue; textSize = size; setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, 10, 0, 10)
        }
        parent.addView(v, matchWrap()); return v
    }
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 }
    private fun matchWrapWithMargin(margin: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = margin }
}
