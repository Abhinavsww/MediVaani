package com.medivaani.app

import android.content.Context

class LocalizationManager(context: Context) {
    private val prefs = context.getSharedPreferences("medivaani_settings", Context.MODE_PRIVATE)

    var currentLanguage: String
        get() = prefs.getString("selected_lang", "hi") ?: "hi"
        set(value) {
            prefs.edit().putString("selected_lang", value).apply()
        }

    private val strings = mapOf(
        "hi" to mapOf(
            "app_title" to "मेडिवाणी",
            "tagline" to "स्कैन करें • जाँच करें • सुनें • सुरक्षित रहें",
            "scan_medicine" to "दवाई स्कैन करें",
            "scan_report" to "रिफ़ोर्ट स्कैन करें",
            "set_alarm" to "अलार्म सेट करें",
            "scan_first" to "पहले स्कैन करें",
            "listen" to "सुनें",
            "pause" to "रोकें",
            "resume" to "जारी रखें",
            "repeat" to "दहराएँ",
            "my_medicines" to "मेरी दवाइयाँ",
            "ask_voice" to "बोलकर पूछें",
            "edit_profile" to "रोगी प्रोफाइल",
            "caregiver_mode" to "देखभाल करने वाला",
            "reminder_card_title" to "रिमाइंडर",
            "next_reminder" to "अगली खुराक: सुबह 9:00 बजे",
            "set_reminder" to "रिमाइंडर सेट करें",
            "nav_home" to "होम",
            "nav_meds" to "दवाइयाँ",
            "nav_reminders" to "रिमाइंडर",
            "nav_profile" to "प्रोफाइल",
            "status_ready" to "स्कैन करने के लिए तैयार। साफ रोशनी में रखें।",
            "ocr_heading" to "पहचाना गया पाठ",
            "verify_heading" to "दवाई की जाँच और निश्चितता",
            "history_heading" to "रोगी का इतिहास और रिकॉर्ड",
            "safety_warning" to "सुरक्षा: यह ऐप संभावित दवा के उम्मीदवार और रिपोर्ट सारांश दिखाता है। डॉक्टर के पर्चे का विकल्प नहीं है। हमेशा फार्मासिस्ट से पुष्टि करें।",
            "reminder_title" to "दवाई का समय हो गया है",
            "reminder_msg" to "कृपया अपनी निर्धारित दवाई लें और पर्ची का पालन करें।",
            "voice_prompt" to "बोलकर पूछने के लिए माइक बटन दबाएँ...",
            "not_sure" to "निश्चित नहीं — कोई विश्वसनीय मैच नहीं मिला। कृपया फार्मासिस्ट से पूछें।",
            "reminder_prompt_title" to "अलार्म सेट करना चाहते हैं?",
            "reminder_prompt_msg" to "क्या आप स्कैन की गई रिपोर्ट या पर्चे के अनुसार दवाई का अलार्म सेट करना चाहते हैं?",
            "prescription_saved" to "📄 पर्चा स्कैन और सहेजा गया:",
            "no_medicine_verified" to "अभी तक कोई दवा सत्यापित नहीं हुई है। शुरू करने के लिए 'दवाई स्कैन करें' दबाएँ।",
            "food_unspecified" to "खाने से पहले या बाद में उल्लेख नहीं है।"
        ),
        "pa" to mapOf(
            "app_title" to "ਮੇਡੀਵਾਣੀ",
            "tagline" to "ਸਕੈਨ ਕਰੋ • ਜਾਂਚ ਕਰੋ • ਸੁਣੋ • ਸੁਰੱਖਿਅਤ ਰਹੋ",
            "scan_medicine" to "ਦਵਾਈ ਸਕੈਨ ਕਰੋ",
            "scan_report" to "ਰਿਪੋਰਟ ਸਕੈਨ ਕਰੋ",
            "set_alarm" to "ਅਲਾਰਮ ਸੈੱਟ ਕਰੋ",
            "scan_first" to "ਪਹਿਲਾਂ ਸਕੈਨ ਕਰੋ",
            "listen" to "ਸੁਣੋ",
            "pause" to "ਰੋਕੋ",
            "resume" to "ਜਾਰੀ ਰੱਖੋ",
            "repeat" to "ਦੁਹਰਾਓ",
            "my_medicines" to "ਮੇਰੀਆਂ ਦਵਾਈਆਂ",
            "ask_voice" to "ਬੋਲ ਕੇ ਪੁੱਛੋ",
            "edit_profile" to "ਮਰੀਜ਼ ਪ੍ਰੋਫਾਈਲ",
            "caregiver_mode" to "ਦੇਖਭਾਲ ਕਰਨ ਵਾਲਾ",
            "reminder_card_title" to "ਰੀਮਾਈਂਡਰ",
            "next_reminder" to "ਅਗਲੀ ਖੁਰਾਕ: ਸਵੇਰੇ 9:00 ਵਜੇ",
            "set_reminder" to "ਰੀਮਾਈਂਡਰ ਸੈੱਟ ਕਰੋ",
            "nav_home" to "ਹੋਮ",
            "nav_meds" to "ਦਵਾਈਆਂ",
            "nav_reminders" to "ਰੀਮਾਈਂਡਰ",
            "nav_profile" to "ਪ੍ਰੋਫਾਈ...",
            "status_ready" to "ਸਕੈਨ ਕਰਨ ਲਈ ਤਿਆਰ। ਸਾਫ਼ ਰੋਸ਼ਨੀ ਵਿੱਚ ਰੱਖੋ।",
            "ocr_heading" to "ਪਛਾਣਿਆ ਗਿਆ ਪਾਠ",
            "verify_heading" to "ਦਵਾਈ ਦੀ ਜਾਂਚ ਅਤੇ ਨਿਸ਼ਚਿਤਤਾ",
            "history_heading" to "ਮਰੀਜ਼ ਦਾ ਇਤਿਹਾਸ ਅਤੇ ਰਿਕਾਰਡ",
            "safety_warning" to "ਸੁਰੱਖਿਆ: ਇਹ ਐਪ ਸੰਭਾਵਿਤ ਦਵਾਈਆਂ ਅਤੇ ਰਿਪੋਰਟਾਂ ਦੀ ਪਛਾਣ ਕਰਦੀ ਹੈ। ਡਾਕਟਰ ਦੀ ਪਰਚੀ ਦੀ ਪਾਲਣਾ ਕਰੋ।",
            "reminder_title" to "ਦਵਾਈ ਲੈਣ ਦਾ ਸਮਾਂ ਹੋ ਗਿਆ ਹੈ",
            "reminder_msg" to "ਕਿਰਪਾ ਕਰਕੇ ਆਪਣੀ ਨਿਰਧਾਰਤ ਦਵਾਈ ਲਓ।",
            "voice_prompt" to "ਬੋਲ ਕੇ ਪੁੱਛਣ ਲਈ ਮਾਈਕ ਦਬਾਓ...",
            "not_sure" to "ਪੱਕਾ ਨਹੀਂ — ਕੋਈ ਭਰੋਸੇਯੋਗ ਮੇਲ ਨਹੀਂ ਮਿਲਿਆ। ਫਾਰਮਾਸਿਸਟ ਨਾਲ ਪੁੱਛੋ।",
            "reminder_prompt_title" to "ਅਲਾਰਮ ਸੈੱਟ ਕਰਨਾ ਚाहੁੰਦੇ ਹੋ?",
            "reminder_prompt_msg" to "ਕੀ ਤੁਸੀਂ ਇਸ ਸਕੈਨ ਮੁਤਾਬਕ ਦਵਾਈ ਦਾ ਅਲਾਰਮ ਸੈੱਟ ਕਰਨਾ ਚाहੁੰਦੇ ਹੋ?",
            "prescription_saved" to "📄 ਪਰਚਾ ਸਕੈਨ ਅਤੇ ਸੇਵ ਕੀਤਾ ਗਿਆ:",
            "no_medicine_verified" to "ਅਜੇ ਤੱਕ ਕੋਈ ਦਵਾਈ ਦੀ ਜਾਂਚ ਨਹੀਂ ਹੋਈ। ਸ਼ੁਰੂ ਕਰਨ ਲਈ 'ਦਵਾਈ ਸਕੈਨ ਕਰੋ' ਦਬਾਓ।",
            "food_unspecified" to "ਖਾਣੇ ਤੋਂ ਪਹਿਲਾਂ ਜਾਂ ਬਾਅਦ ਵਿੱਚ ਦੱਸਿਆ ਨਹੀਂ ਗਿਆ ਹੈ।"
        ),
        "en" to mapOf(
            "app_title" to "MediVaani",
            "tagline" to "Scan • Verify • Listen • Stay Safe",
            "scan_medicine" to "Scan Medicine",
            "scan_report" to "Scan Report",
            "set_alarm" to "Set Alarm",
            "scan_first" to "Scan first",
            "listen" to "Listen",
            "pause" to "Pause",
            "resume" to "Resume",
            "repeat" to "Repeat",
            "my_medicines" to "My Medicines",
            "ask_voice" to "Ask by Speaking",
            "edit_profile" to "Patient Profile",
            "caregiver_mode" to "Caregiver Mode",
            "reminder_card_title" to "Reminder",
            "next_reminder" to "Next: 9:00 AM • Morning Dose",
            "set_reminder" to "Set Reminder",
            "nav_home" to "Home",
            "nav_meds" to "Meds",
            "nav_reminders" to "Reminders",
            "nav_profile" to "Profile",
            "status_ready" to "Ready to scan. Use good light and keep text in focus.",
            "ocr_heading" to "Detected Text",
            "verify_heading" to "Medicine Verification & Certainty",
            "history_heading" to "Patient History & Records",
            "safety_warning" to "Safety: This app identifies possible medicine candidates and medical report summaries. It does NOT prescribe doses. Confirm all findings with a healthcare professional.",
            "reminder_title" to "It's time for your medicine",
            "reminder_msg" to "Please take your prescribed medicine and follow your instructions.",
            "voice_prompt" to "Tap speech assistant to ask a question...",
            "not_sure" to "NOT SURE — no reliable match found. Please ask a pharmacist.",
            "reminder_prompt_title" to "Set Reminder?",
            "reminder_prompt_msg" to "Do you want to set a medicine alarm according to this scan?",
            "prescription_saved" to "📄 PRESCRIPTION SCANNED & SAVED:",
            "no_medicine_verified" to "No medicine verified yet. Tap 'Scan Medicine' to begin.",
            "food_unspecified" to "Before or after food is not specified."
        )
    )

    fun getString(key: String): String {
        return strings[currentLanguage]?.get(key) ?: strings["hi"]?.get(key) ?: key
    }
}
