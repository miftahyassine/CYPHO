# CipherKit 2.0
Android cipher toolkit (Kotlin + Jetpack Compose), black and turquoise.

* **Cipher** - 25 ciphers and encodings, all aware of the selected language
  (English, Spanish, German, French, Russian, Greek, Arabic, Hebrew).
* **Solve** - brute force on every method at once (or one method), ranked by how much
  the result looks like real text in the language. Optional known word, auto language
  detection, and two-layer decoding (e.g. Base64 then Caesar).
* **Key finder** - recover a Vigenere/Beaufort key from a known word.
* **Detect** - guesses what kind of code a text is.

## Build
Push to GitHub, open **Actions > Build APK**, download the **CipherKit-apk** artifact.

## Extend
* New cipher: add one `Cipher(...)` to `CIPHERS` in `Ciphers.kt` (and an attack in `Solver.kt` if it can be brute forced).
* New language: add one `Alphabet(...)` to `ALPHABETS` in `Alphabets.kt`.
