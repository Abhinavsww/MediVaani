# MediVaani Android prototype

## Included
- Android app source (Kotlin)
- On-device ML Kit Latin text OCR
- Small **demo-only** medicine dataset
- Conservative candidate matching with a NOT SURE state
- Hindi/Punjabi/English Android Text-to-Speech preview
- Reminder-time selection UI (demo only)

## Important limitations — do not present these as completed features
1. The current camera intent returns a low-resolution thumbnail. Replace it with full-resolution CameraX capture before testing real foil strips.
2. OCR currently uses ML Kit Latin recognition. It does not solve curved foil, glare, torn text, or multilingual packaging robustly.
3. The bundled medicine list is illustrative, not a validated drug database. Expand it only from a trusted, reviewed source.
4. The reminder screen is a UI prototype; it does not schedule persistent background notifications.
5. No dosage is inferred. The app never generates a dose from a medicine name. A verified prescription/caregiver must supply the schedule.
6. A match is only a candidate. Always verify medicine identity and strength against the physical pack and prescription with a pharmacist/caregiver.

## Build an APK
1. Install Android Studio (current stable) and Android SDK Platform 35.
2. Open this folder in Android Studio.
3. Allow Gradle sync and install any requested SDK components.
4. Connect an Android phone with USB debugging enabled, or use an emulator.
5. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. Android Studio will show the APK location, normally `app/build/outputs/apk/debug/app-debug.apk`.

## Hackathon demo
Use only clearly labelled dummy/test packaging or medicines under pharmacist supervision. Demonstrate:
- readable scan -> candidate result
- missing strength -> NOT SURE / strength not confirmed
- unrelated or poor scan -> NOT SURE

Do not demonstrate a guessed dose or tell a user to take a medicine based on OCR.
