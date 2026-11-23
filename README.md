# ThaiFlash

A native Android (Kotlin + Jetpack Compose + Material 3) Anki-style Thai flashcard & hands-free audio app built for rapid conversational Thai.

## Key Features
- **Custom Sets & <5-Second Card Capture:** Type, paste, or speak (English or Thai) → instant auto-translation, phonetic romanization, and automatic word-by-word Thai breakdown.
- **40-Set / 600-Phrase Master Curriculum (1,105 unique Thai word chunks):** Natural male-spoken Bangkok Thai engineered with Construction Grammar Slot-and-Frame templates, covering CEFR A1 → B1+ daily life, romance/couple routines, family, home chores, Thai dramas, news, and slang — all with pre-verified offline word breakdowns.
- **Bi-Directional 3D Flip Study Player:** Pure Thai by default (optional Romanization toggle in Settings), instant `TH → EN` / `EN → TH` switching, Neural HD Thai TTS (normal + 🐢 slow), one-tap Word Breakdown popup, and **"Say It" 0–100% pronunciation match** with character-level diffing.
- **Science-Backed Smart Review (Active Sets Only):** FSRS-inspired Red/Yellow/Green spaced repetition pulling 10 interleaved cards (~55% Red struggling, ~25% Yellow/New, ~20% oldest Green for retention) with same-session Red retry.
- **Hands-Free Listen Set (Pimsleur Mode):** Bilingual Thai ↔ English audio playback repeating each phrase twice with dynamic phrase-length pauses.
- **Global Search & Backup:** Real-time search across all sets by English, Thai, or romanization with tap-to-edit, plus JSON export/import backup.

## Build
```bash
./gradlew testDebugUnitTest assembleDebug
```
