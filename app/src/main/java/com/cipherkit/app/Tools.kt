package com.cipherkit.app

class CribResult(val text: String, val key: String?)

/**
 * Recovers a polyalphabetic key from a known piece of the message.
 * [startLetter] is the 1-based position (counting letters only) where the known text begins.
 */
fun cribKey(cipher: String, known: String, a: Alphabet, startLetter: Int): CribResult {
    val c = letterIndices(cipher, a)
    val p = letterIndices(known, a)
    if (p.isEmpty()) fail("Enter the word you know, using the selected language")
    val start = (startLetter - 1).coerceAtLeast(0)
    if (start + p.size > c.size) fail("The known text does not fit at that position")
    val n = a.size
    val stream = List(p.size) { (c[start + it] - p[it]).mod(n) }
    val beaufort = List(p.size) { (c[start + it] + p[it]).mod(n) }
    fun str(l: List<Int>) = l.map { a.letters[it].uppercaseChar() }.joinToString("")

    val sb = StringBuilder()
    sb.append("Key stream (Vigenère): ${str(stream)}\n")
    sb.append("Key stream (Beaufort): ${str(beaufort)}\n")

    var period = -1
    for (q in 1 until stream.size) {
        if ((0 until stream.size - q).all { stream[it] == stream[it + q] }) { period = q; break }
    }
    var fullKey: String? = null
    if (period > 0) {
        val k = IntArray(period)
        for (j in 0 until period) k[(start + j) % period] = stream[j]
        fullKey = str(k.toList())
        sb.append("\nRepeating key, length $period: $fullKey\n")
        sb.append("Only ${stream.size - period} letters confirm the repeat, so test it on the whole message.\n")
    }

    // look for a well-known keyword that fits the stream
    val kws = (a.keywords + ALPHABETS[0].keywords).filter { a.has(it) }.distinct()
    val found = ArrayList<String>()
    for (kw in kws) {
        val ki = kw.map { a.index(it) }
        val l = ki.size
        if (l < 2) continue
        for (o in 0 until l) {
            val ok = stream.indices.all { stream[it] == ki[(o + it) % l] }
            if (ok && (stream.size >= l || stream.size >= 4)) {
                val r = (o - start).mod(l)
                val rotated = kw.uppercase().let { it.substring(r) + it.substring(0, r) }
                found.add("${kw.uppercase()} (key starts at its letter ${r + 1}: $rotated)")
                if (fullKey == null) fullKey = rotated
                break
            }
        }
    }
    if (found.isNotEmpty()) {
        sb.append("\nLooks like the keyword:\n")
        found.forEach { sb.append("• $it\n") }
    } else if (period < 0) {
        sb.append("\nNo repeating key found yet. Try a longer known word or another start position.\n")
    }
    return CribResult(sb.toString().trimEnd(), fullKey)
}

/** Guesses what kind of cipher or encoding a text is. */
fun detect(text: String, a: Alphabet): String {
    val t = text.trim()
    if (t.isEmpty()) fail("Paste some text first")
    val hints = ArrayList<String>()
    val compact = t.filter { !it.isWhitespace() }
    if (compact.all { it == '0' || it == '1' } && compact.length % 8 == 0) hints += "Binary (8-bit groups)"
    if (t.all { it in ".-/| \n" }) hints += "Morse code"
    if (compact.length % 2 == 0 && compact.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) hints += "Hex (could also be XOR output)"
    if (compact.length % 4 == 0 && Regex("^[A-Za-z0-9+/]+={0,2}$").matches(compact)) hints += "Base64"
    if (compact.length >= 8 && compact.length % 8 == 0 && Regex("^[A-Za-z2-7]+={0,6}$").matches(compact) && compact.all { it.isUpperCase() || it in '2'..'7' || it == '=' }) hints += "Base32"
    if (compact.all { it == 'A' || it == 'B' || it == 'a' || it == 'b' }) hints += "Bacon cipher"
    val tokens = t.split(Regex("[\\s/,.-]+")).filter { it.isNotEmpty() }
    if (tokens.isNotEmpty() && tokens.all { it.length == 2 && it.all { d -> d in '1'..'9' } }) hints += "Polybius square"
    if (tokens.isNotEmpty() && tokens.all { it.toIntOrNull() in 1..a.size }) hints += "A1Z26 (letter numbers)"
    if (tokens.isNotEmpty() && tokens.all { it.toIntOrNull() in 0..255 } && tokens.size >= 3) hints += "Decimal bytes"

    val idx = letterIndices(t, a)
    if (idx.size >= 20 && idx.size >= compact.length * 0.8 && (hints.isEmpty() || t.contains(' '))) {
        val counts = IntArray(a.size)
        idx.forEach { counts[it]++ }
        val ic = counts.sumOf { it * (it - 1) }.toDouble() / (idx.size * (idx.size - 1))
        val norm = ic * a.size
        val icText = "%.3f".format(ic)
        if (norm >= 1.5) {
            hints += "Letter pattern like ${a.name.substringBefore(" (")} (IC $icText): Caesar, Atbash, Affine, keyword substitution, or a transposition such as Rail fence or Columnar"
        } else {
            hints += "Flat letter pattern (IC $icText): probably Vigenère, Beaufort, Autokey or Playfair. Use Solve, or Key finder if you know a word."
        }
        if (idx.size % 2 == 0 && a.id == "en") hints += "Even number of letters: Playfair is possible"
    }
    return if (hints.isEmpty()) "No obvious match. Try Solve with all methods." else hints.joinToString("\n") { "• $it" }
}
