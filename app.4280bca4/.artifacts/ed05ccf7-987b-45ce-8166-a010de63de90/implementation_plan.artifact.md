# Prescription OCR Structuring & Accuracy Debugging Plan

Debug and restructure prescription OCR in MediVaani to prevent garbled text (e.g. "VOMILAST" -> "VOAST"), preserve reading order using bounding boxes, extract structured prescription fields (Medicine, strength, dosage, frequency, duration, advice), maintain table row relationships, and provide manual correction + raw debug view.

## User Review Required

> [!IMPORTANT]
> This upgrade restructures prescription scanning (`SCAN REPORT` / `SCAN PRESCRIPTION`):
> 1. **Bounding Box Reading Order**: Sorts ML Kit text blocks top-to-bottom, left-to-right to preserve prescription table row relationships.
> 2. **Structured Prescription Parser**: Parses distinct fields (Patient Name, Date, Diagnosis, Medicine Name, Strength, Dosage, Frequency, Duration, Advice, Follow-up).
> 3. **Uncertainty Flagging**: Marks unreadable or low-confidence text as `"Needs confirmation"` instead of guessing.
> 4. **Card-Based Results & Manual Correction**: Displays extracted medicines in separate structured cards with a manual edit/correction dialog and raw OCR debug output.
> 5. **Confirmation Gate**: Requires user/caregiver confirmation before saving the prescription to patient history.

## Proposed Changes

### [Prescription OCR Parsing & Structured UI]

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Prashant%20Mishra/OneDrive/Desktop/App/app/src/main/java/com/medivaani/app/MainActivity.kt)
- Enhance image preprocessing for prescriptions (high contrast, sharpening, shadow reduction).
- Implement sorted bounding-box text extraction (reading order sorting by vertical/horizontal coordinates).
- Implement structured prescription row parser to separate medicine name, strength, dosage, frequency, and duration.
- Create a clean prescription results screen with separate cards for each medicine, "Needs confirmation" badges, manual edit capability, and raw OCR debug output section.
- Add confirmation dialog before saving verified prescription records.

## Verification Plan

### Automated Tests
- Run `:app:assembleDebug :app:testDebugUnit` to verify compilation and unit tests.

### Manual Verification
- Deploy and test prescription scanning, verifying structured card layout, manual correction, raw OCR debug view, and confirmation gate.
