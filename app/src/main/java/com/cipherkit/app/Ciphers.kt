package com.cipherkit.app

import java.text.Normalizer
import java.util.Base64
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

class Cipher(
    val id: String,
    val name: String,
    val category: String,
    val desc: String,
    val keyLabel: String?,
    val keyHelp: String,
    val usesAlphabet: Boolean,
    val enc: (String, String, Alphabet) -> String,
    val dec: (String, String, Alphabet) -> String,
)

fun fail(msg: String): Nothing = throw IllegalArgumentException(msg)

// ---------------------------------------------------------------- helpers

/** Applies [f] (letter index, letter position) to every letter of [a]; keeps case and other characters. */
fun mapLetters(text: String, a: Alphabet, f: (Int, Int) -> Int): String {
    val n = a.size
    val sb = StringBuilder()
    var pos = 0
    for (c in text) {
        val i = a.index(c)
        if (i < 0) {
            sb.append(c)
        } else {
            val r = a.letters[((f(i, pos) % n) + n) % n]
            sb.append(if (c.isUpperCase()) r.uppercaseChar() else r)
            pos++
        }
    }
    return sb.toString()
}

fun letterIndices(text: String, a: Alphabet): List<Int> = text.map { a.index(it) }.filter { it >= 0 }

private fun numKey(k: String): Int = k.trim().toIntOrNull() ?: fail("Key must be a whole number")

private fun keyIdx(k: String, a: Alphabet): List<Int> {
    val ks = k.map { a.index(it) }.filter { it >= 0 }
    if (ks.isEmpty()) fail("The key needs letters from the selected language")
    return ks
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) abs(a) else gcd(b, a % b)

fun affineInverse(a: Int, n: Int): Int? = (1 until n).firstOrNull { (a * it).mod(n) == 1 }

private fun affineParams(k: String, n: Int): Triple<Int, Int, Int> {
    val p = k.split(",", ";", " ").filter { it.isNotBlank() }.map { it.trim().toIntOrNull() }
    if (p.size != 2 || p.any { it == null }) fail("Key format: a,b   for example 5,8")
    val a = p[0]!!
    val b = p[1]!!
    val inv = affineInverse(a.mod(n), n) ?: fail("a must share no factor with $n (try 3, 5, 7...)")
    return Triple(a, b, inv)
}

/** Alphabet rewritten so the keyword letters come first. */
fun mixedAlphabet(key: String, base: String, a: Alphabet, mergeJ: Boolean = false): String {
    val seen = LinkedHashSet<Char>()
    for (c in key) {
        val i = a.index(c)
        if (i < 0) continue
        var ch = a.letters[i]
        if (mergeJ && ch == 'j') ch = 'i'
        if (base.indexOf(ch) >= 0) seen.add(ch)
    }
    for (ch in base) seen.add(ch)
    return seen.joinToString("")
}

private fun letterOf(c: Char, a: Alphabet): Char? = a.index(c).takeIf { it >= 0 }?.let { a.letters[it] }

private fun withCase(orig: Char, r: Char) = if (orig.isUpperCase()) r.uppercaseChar() else r

private fun subst(text: String, a: Alphabet, from: String, to: String): String {
    val sb = StringBuilder()
    for (c in text) {
        val l = letterOf(c, a)
        val j = if (l == null) -1 else from.indexOf(l)
        if (j < 0) sb.append(c) else sb.append(withCase(c, to[j]))
    }
    return sb.toString()
}

// ---------------------------------------------------------------- rail fence / columnar

private fun railOrder(len: Int, rails: Int): List<Int> {
    if (rails < 2) fail("Rails must be 2 or more")
    val pat = IntArray(len)
    var row = 0
    var dir = 1
    for (i in 0 until len) {
        pat[i] = row
        if (row == 0) dir = 1 else if (row == rails - 1) dir = -1
        row += dir
    }
    return (0 until len).sortedBy { pat[it] }
}

fun railFence(t: String, rails: Int, encode: Boolean): String {
    val o = railOrder(t.length, rails)
    if (encode) return o.map { t[it] }.joinToString("")
    val out = CharArray(t.length)
    o.forEachIndexed { j, idx -> out[idx] = t[j] }
    return String(out)
}

private fun colRanks(key: String): IntArray {
    val ks = key.filter { !it.isWhitespace() && it != ',' }
    if (ks.length < 2) fail("The key needs at least 2 characters (word or digits)")
    val idx = ks.indices.sortedWith(compareBy<Int>({ ks[it].lowercaseChar() }, { it }))
    val rank = IntArray(ks.length)
    idx.forEachIndexed { r, i -> rank[i] = r }
    return rank
}

/** Columnar transposition. rank[i] = reading position of column i. */
fun columnar(text: String, rank: IntArray, encode: Boolean): String {
    val k = rank.size
    val n = text.length
    val order = (0 until k).sortedBy { rank[it] }
    if (encode) {
        val sb = StringBuilder()
        for (c in order) {
            var i = c
            while (i < n) { sb.append(text[i]); i += k }
        }
        return sb.toString()
    }
    val out = CharArray(n)
    var p = 0
    for (c in order) {
        var i = c
        while (i < n) { out[i] = text[p++]; i += k }
    }
    return String(out)
}

// ---------------------------------------------------------------- polyalphabetic

private fun autokey(text: String, key: String, a: Alphabet, encode: Boolean): String {
    val ks = keyIdx(key, a).toMutableList()
    val n = a.size
    val sb = StringBuilder()
    var pos = 0
    for (c in text) {
        val i = a.index(c)
        if (i < 0) { sb.append(c); continue }
        val k = ks[pos]
        val r = if (encode) (i + k) % n else ((i - k) % n + n) % n
        ks.add(if (encode) i else r)
        sb.append(withCase(c, a.letters[r]))
        pos++
    }
    return sb.toString()
}

private fun gronsfeldKey(k: String): List<Int> {
    val d = k.filter { it.isDigit() }.map { it - '0' }
    if (d.isEmpty()) fail("Key must be digits, for example 31415")
    return d
}

// ---------------------------------------------------------------- Playfair (any language)

private fun gridDims(n: Int): Pair<Int, Int> {
    var best = 1
    for (r in 1..n) if (n % r == 0 && r * r <= n) best = r
    return best to n / best
}

private fun playfair(t: String, key: String, a: Alphabet, encode: Boolean): String {
    val en = a.id == "en"
    val base = if (en) "abcdefghiklmnopqrstuvwxyz" else a.letters
    val grid = mixedAlphabet(key, base, a, mergeJ = en)
    val (rows, cols) = if (en) 5 to 5 else gridDims(base.length)
    val filler = if (grid.indexOf('x') >= 0) 'x' else grid.last()
    val letters = StringBuilder()
    for (c in t) {
        val l = letterOf(c, a) ?: continue
        letters.append(if (en && l == 'j') 'i' else l)
    }
    val s = letters.toString()
    val pairs = ArrayList<Pair<Char, Char>>()
    if (encode) {
        var i = 0
        while (i < s.length) {
            val x = s[i]
            var y = if (i + 1 < s.length) s[i + 1] else filler
            if (x == y) { y = filler; i += 1 } else i += 2
            pairs.add(x to y)
        }
    } else {
        if (s.length % 2 != 0) fail("Playfair text needs an even number of letters")
        for (i in s.indices step 2) pairs.add(s[i] to s[i + 1])
    }
    val d = if (encode) 1 else -1
    val sb = StringBuilder()
    for ((x, y) in pairs) {
        val ix = grid.indexOf(x)
        val iy = grid.indexOf(y)
        val rx = ix / cols; val cx = ix % cols
        val ry = iy / cols; val cy = iy % cols
        when {
            rx == ry -> {
                sb.append(grid[rx * cols + (cx + d).mod(cols)])
                sb.append(grid[ry * cols + (cy + d).mod(cols)])
            }
            cx == cy -> {
                sb.append(grid[((rx + d).mod(rows)) * cols + cx])
                sb.append(grid[((ry + d).mod(rows)) * cols + cy])
            }
            else -> {
                sb.append(grid[rx * cols + cy])
                sb.append(grid[ry * cols + cx])
            }
        }
    }
    val r = sb.toString()
    return if (encode) r.map { it.uppercaseChar() }.joinToString("") else r
}

// ---------------------------------------------------------------- Polybius / Bacon / A1Z26

private fun polyGrid(a: Alphabet, key: String): Pair<String, Int> {
    val en = a.id == "en"
    val base = if (en) "abcdefghiklmnopqrstuvwxyz" else a.letters
    val w = if (en) 5 else ceil(sqrt(base.length.toDouble())).toInt()
    return mixedAlphabet(key, base, a, mergeJ = en) to w
}

private fun polybius(t: String, key: String, a: Alphabet, encode: Boolean): String {
    val (grid, w) = polyGrid(a, key)
    val en = a.id == "en"
    if (encode) {
        return t.trim().split(Regex("\\s+")).joinToString(" / ") { word ->
            word.mapNotNull { c ->
                var l = letterOf(c, a) ?: return@mapNotNull null
                if (en && l == 'j') l = 'i'
                val p = grid.indexOf(l)
                if (p < 0) null else "${p / w + 1}${p % w + 1}"
            }.joinToString(" ")
        }
    }
    return t.trim().split(Regex("\\s*/\\s*")).joinToString(" ") { word ->
        word.trim().split(Regex("\\s+")).filter { it.length == 2 }.joinToString("") { p ->
            val r = p[0] - '1'
            val c = p[1] - '1'
            val pos = r * w + c
            if (r >= 0 && c in 0 until w && pos < grid.length) grid[pos].uppercaseChar().toString() else "?"
        }
    }
}

private fun baconBits(n: Int): Int {
    var b = 5
    while ((1 shl b) < n) b++
    return b
}

private fun bacon(t: String, a: Alphabet, encode: Boolean): String {
    val bits = baconBits(a.size)
    if (encode) {
        return t.mapNotNull { c ->
            val i = a.index(c)
            if (i < 0) null else (0 until bits).joinToString("") { b -> if (((i shr (bits - 1 - b)) and 1) == 1) "B" else "A" }
        }.joinToString(" ")
    }
    val s = t.uppercase().filter { it == 'A' || it == 'B' }
    return s.chunked(bits).filter { it.length == bits }.joinToString("") { g ->
        val v = g.fold(0) { acc, ch -> acc * 2 + if (ch == 'B') 1 else 0 }
        if (v < a.size) a.letters[v].uppercaseChar().toString() else "?"
    }
}

private fun a1z26(t: String, a: Alphabet, encode: Boolean): String {
    if (encode) {
        return t.trim().split(Regex("\\s+")).joinToString(" / ") { w ->
            w.mapNotNull { c -> a.index(c).takeIf { it >= 0 }?.plus(1) }.joinToString(" ")
        }
    }
    return t.trim().split(Regex("[\\s]*/[\\s]*")).joinToString(" ") { w ->
        w.trim().split(Regex("[\\s,.-]+")).filter { it.isNotEmpty() }.joinToString("") { n ->
            (n.toIntOrNull()?.let { a.letters.getOrNull(it - 1) }?.uppercaseChar() ?: '?').toString()
        }
    }
}

// ---------------------------------------------------------------- Morse (language aware)

private const val MORSE_LATIN = "a .-|b -...|c -.-.|d -..|e .|f ..-.|g --.|h ....|i ..|j .---|k -.-|l .-..|m --|" +
    "n -.|o ---|p .--.|q --.-|r .-.|s ...|t -|u ..-|v ...-|w .--|x -..-|y -.--|z --.."
private const val MORSE_COMMON = "0 -----|1 .----|2 ..---|3 ...--|4 ....-|5 .....|6 -....|7 --...|8 ---..|9 ----.|" +
    ". .-.-.-|, --..--|? ..--..|! -.-.--|: ---...|; -.-.-.|' .----.|\" .-..-.|( -.--.|) -.--.-|" +
    "- -....-|+ .-.-.|= -...-|@ .--.-.|& .-..."

private val morseCache = HashMap<String, Pair<Map<Char, String>, Map<String, Char>>>()

private fun morseTable(a: Alphabet): Pair<Map<Char, String>, Map<String, Char>> = morseCache.getOrPut(a.id) {
    fun parse(s: String) = s.split("|").associate { it[0] to it.substring(2) }
    val m = LinkedHashMap<Char, String>()
    if (a.latin) m.putAll(parse(MORSE_LATIN))
    m.putAll(a.morse)
    m.putAll(parse(MORSE_COMMON))
    val rev = LinkedHashMap<String, Char>()
    for ((k, v) in m) if (!rev.containsKey(v)) rev[v] = k
    m to rev
}

private fun baseChar(c: Char): Char = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)[0]

private fun morseCode(c: Char, m: Map<Char, String>, a: Alphabet): String? {
    val lc = c.lowercaseChar()
    return m[lc] ?: a.fold[lc]?.let { m[it] } ?: m[baseChar(lc)]
}

private fun morse(t: String, a: Alphabet, encode: Boolean): String {
    val (m, rev) = morseTable(a)
    if (encode) {
        return t.trim().split(Regex("\\s+")).joinToString(" / ") { w ->
            w.mapNotNull { morseCode(it, m, a) }.joinToString(" ")
        }
    }
    return t.trim().split(Regex("\\s*[/|]\\s*")).joinToString(" ") { w ->
        w.trim().split(Regex("\\s+")).joinToString("") { code ->
            (rev[code]?.uppercaseChar() ?: '?').toString()
        }
    }
}

// ---------------------------------------------------------------- byte encodings

fun fromHex(s: String): ByteArray {
    val h = s.filter { !it.isWhitespace() && it != ':' }.removePrefix("0x")
    if (h.isEmpty() || h.length % 2 != 0 || !h.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) fail("Not valid hex")
    return ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

fun toHex(b: ByteArray) = b.joinToString(" ") { "%02x".format(it) }

private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

private fun base32Enc(b: ByteArray): String {
    val sb = StringBuilder()
    var buf = 0
    var bits = 0
    for (x in b) {
        buf = (buf shl 8) or (x.toInt() and 0xFF)
        bits += 8
        while (bits >= 5) {
            sb.append(B32[(buf shr (bits - 5)) and 31])
            bits -= 5
        }
        buf = buf and ((1 shl bits) - 1)
    }
    if (bits > 0) sb.append(B32[(buf shl (5 - bits)) and 31])
    while (sb.length % 8 != 0) sb.append('=')
    return sb.toString()
}

private fun base32Dec(s: String): ByteArray {
    val clean = s.uppercase().filter { !it.isWhitespace() && it != '=' }
    if (clean.isEmpty()) fail("Not valid Base32")
    val out = ArrayList<Byte>()
    var buf = 0
    var bits = 0
    for (ch in clean) {
        val v = B32.indexOf(ch)
        if (v < 0) fail("Not valid Base32")
        buf = (buf shl 5) or v
        bits += 5
        if (bits >= 8) {
            out.add(((buf shr (bits - 8)) and 0xFF).toByte())
            bits -= 8
            buf = buf and ((1 shl bits) - 1)
        }
    }
    return out.toByteArray()
}

fun xorBytes(data: ByteArray, k: ByteArray): ByteArray {
    if (k.isEmpty()) fail("Key is empty")
    return ByteArray(data.size) { (data[it].toInt() xor k[it % k.size].toInt()).toByte() }
}

fun bytesToText(b: ByteArray): String = String(b, Charsets.UTF_8)

private fun fromBinary(t: String): ByteArray {
    val bits = t.filter { it == '0' || it == '1' }
    if (bits.isEmpty() || bits.length % 8 != 0) fail("Binary length must be a multiple of 8")
    return bits.chunked(8).map { it.toInt(2).toByte() }.toByteArray()
}

private fun fromDecimal(t: String): ByteArray {
    val parts = t.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) fail("Enter numbers from 0 to 255")
    return parts.map { (it.toIntOrNull() ?: fail("Not a number: $it")).also { v -> if (v !in 0..255) fail("Numbers must be 0-255") }.toByte() }
        .toByteArray()
}

private fun cleanB64(t: String) = t.filter { !it.isWhitespace() }

val ROT47 = { t: String ->
    t.map { if (it.code in 33..126) (33 + (it.code - 33 + 47) % 94).toChar() else it }.joinToString("")
}

// ---------------------------------------------------------------- the catalogue

const val CAT_SHIFT = "Shift"
const val CAT_POLY = "Polyalphabetic"
const val CAT_SUBST = "Substitution"
const val CAT_TRANS = "Transposition"
const val CAT_ENCODE = "Encoding"
val CATEGORIES = listOf(CAT_SHIFT, CAT_POLY, CAT_SUBST, CAT_TRANS, CAT_ENCODE)

val CIPHERS: List<Cipher> = listOf(
    // ---- shift
    Cipher("caesar", "Caesar", CAT_SHIFT, "Moves every letter a fixed number of places.",
        "Shift", "A whole number, e.g. 3", true,
        { t, k, a -> val s = numKey(k); mapLetters(t, a) { i, _ -> i + s } },
        { t, k, a -> val s = numKey(k); mapLetters(t, a) { i, _ -> i - s } }),
    Cipher("rot", "ROT13 (half rotation)", CAT_SHIFT, "Shifts by half the alphabet (13 in English). Encode and decode are the same.",
        null, "", true,
        { t, _, a -> mapLetters(t, a) { i, _ -> i + a.size / 2 } },
        { t, _, a -> mapLetters(t, a) { i, _ -> i - a.size / 2 } }),
    Cipher("atbash", "Atbash", CAT_SHIFT, "Mirrors the alphabet: first letter becomes last.",
        null, "", true,
        { t, _, a -> mapLetters(t, a) { i, _ -> a.size - 1 - i } },
        { t, _, a -> mapLetters(t, a) { i, _ -> a.size - 1 - i } }),
    Cipher("affine", "Affine", CAT_SHIFT, "Each letter x becomes a·x + b.",
        "Key a,b", "Two numbers, e.g. 5,8 (a must be coprime with the alphabet size)", true,
        { t, k, a -> val (x, y, _) = affineParams(k, a.size); mapLetters(t, a) { i, _ -> x * i + y } },
        { t, k, a -> val (_, y, inv) = affineParams(k, a.size); mapLetters(t, a) { i, _ -> inv * (i - y) } }),
    Cipher("rot47", "ROT47", CAT_SHIFT, "Rotates all visible ASCII characters (symbols and digits too).",
        null, "", false,
        { t, _, _ -> ROT47(t) }, { t, _, _ -> ROT47(t) }),

    // ---- polyalphabetic
    Cipher("vigenere", "Vigenère", CAT_POLY, "A Caesar shift that changes with every letter of a keyword.",
        "Keyword", "A word in the selected language", true,
        { t, k, a -> val ks = keyIdx(k, a); mapLetters(t, a) { i, p -> i + ks[p % ks.size] } },
        { t, k, a -> val ks = keyIdx(k, a); mapLetters(t, a) { i, p -> i - ks[p % ks.size] } }),
    Cipher("beaufort", "Beaufort", CAT_POLY, "Vigenère variant that subtracts the letter from the key. Self-reciprocal.",
        "Keyword", "A word in the selected language", true,
        { t, k, a -> val ks = keyIdx(k, a); mapLetters(t, a) { i, p -> ks[p % ks.size] - i } },
        { t, k, a -> val ks = keyIdx(k, a); mapLetters(t, a) { i, p -> ks[p % ks.size] - i } }),
    Cipher("autokey", "Autokey", CAT_POLY, "Vigenère where the message itself extends the keyword.",
        "Keyword", "A word in the selected language", true,
        { t, k, a -> autokey(t, k, a, true) },
        { t, k, a -> autokey(t, k, a, false) }),
    Cipher("gronsfeld", "Gronsfeld", CAT_POLY, "Vigenère with a numeric key.",
        "Digits", "Digits only, e.g. 31415", true,
        { t, k, a -> val ks = gronsfeldKey(k); mapLetters(t, a) { i, p -> i + ks[p % ks.size] } },
        { t, k, a -> val ks = gronsfeldKey(k); mapLetters(t, a) { i, p -> i - ks[p % ks.size] } }),

    // ---- substitution
    Cipher("keyword", "Keyword substitution", CAT_SUBST, "Replaces the alphabet by one that starts with a keyword.",
        "Keyword", "A word in the selected language", true,
        { t, k, a -> subst(t, a, a.letters, mixedAlphabet(k, a.letters, a)) },
        { t, k, a -> subst(t, a, mixedAlphabet(k, a.letters, a), a.letters) }),
    Cipher("playfair", "Playfair", CAT_SUBST, "Encrypts pairs of letters using a keyword grid.",
        "Keyword", "A word in the selected language", true,
        { t, k, a -> playfair(t, k, a, true) },
        { t, k, a -> playfair(t, k, a, false) }),
    Cipher("polybius", "Polybius square", CAT_SUBST, "Each letter becomes its row and column in a grid.",
        "Keyword (optional)", "Leave empty for the plain grid", true,
        { t, k, a -> polybius(t, k, a, true) },
        { t, k, a -> polybius(t, k, a, false) }),
    Cipher("bacon", "Bacon", CAT_SUBST, "Each letter becomes a group of A and B.",
        null, "", true,
        { t, _, a -> bacon(t, a, true) },
        { t, _, a -> bacon(t, a, false) }),
    Cipher("morse", "Morse code", CAT_SUBST, "Dots and dashes, with each language's own letters.",
        null, "", true,
        { t, _, a -> morse(t, a, true) },
        { t, _, a -> morse(t, a, false) }),
    Cipher("a1z26", "A1Z26 (numbers)", CAT_SUBST, "Each letter becomes its position in the alphabet.",
        null, "", true,
        { t, _, a -> a1z26(t, a, true) },
        { t, _, a -> a1z26(t, a, false) }),

    // ---- transposition
    Cipher("rail", "Rail fence", CAT_TRANS, "Writes the text in a zig-zag over several rails.",
        "Rails", "A number of rails, 2 or more", false,
        { t, k, _ -> railFence(t, numKey(k), true) },
        { t, k, _ -> railFence(t, numKey(k), false) }),
    Cipher("columnar", "Columnar transposition", CAT_TRANS, "Writes the text in rows, reads columns in the order of a keyword.",
        "Keyword or digits", "e.g. ZEBRA or 31524", false,
        { t, k, _ -> columnar(t, colRanks(k), true) },
        { t, k, _ -> columnar(t, colRanks(k), false) }),
    Cipher("reverse", "Reverse", CAT_TRANS, "Reads the text backwards.",
        null, "", false,
        { t, _, _ -> t.reversed() }, { t, _, _ -> t.reversed() }),

    // ---- encodings (work for every language through UTF-8)
    Cipher("base64", "Base64", CAT_ENCODE, "Standard text encoding of bytes.",
        null, "", false,
        { t, _, _ -> Base64.getEncoder().encodeToString(t.toByteArray()) },
        { t, _, _ ->
            val c = cleanB64(t)
            if (c.isEmpty()) fail("Nothing to decode")
            try { bytesToText(Base64.getDecoder().decode(c)) } catch (e: IllegalArgumentException) { fail("Not valid Base64") }
        }),
    Cipher("base32", "Base32", CAT_ENCODE, "Encoding with 32 symbols (A-Z, 2-7).",
        null, "", false,
        { t, _, _ -> base32Enc(t.toByteArray()) },
        { t, _, _ -> bytesToText(base32Dec(t)) }),
    Cipher("hex", "Hex", CAT_ENCODE, "Each byte as two hexadecimal digits.",
        null, "", false,
        { t, _, _ -> toHex(t.toByteArray()) },
        { t, _, _ -> bytesToText(fromHex(t)) }),
    Cipher("binary", "Binary", CAT_ENCODE, "Each byte as eight bits.",
        null, "", false,
        { t, _, _ -> t.toByteArray().joinToString(" ") { Integer.toBinaryString(it.toInt() and 0xFF).padStart(8, '0') } },
        { t, _, _ -> bytesToText(fromBinary(t)) }),
    Cipher("decimal", "Decimal bytes", CAT_ENCODE, "Each byte as a number from 0 to 255.",
        null, "", false,
        { t, _, _ -> t.toByteArray().joinToString(" ") { (it.toInt() and 0xFF).toString() } },
        { t, _, _ -> bytesToText(fromDecimal(t)) }),
    Cipher("xor", "XOR (hex output)", CAT_ENCODE, "XORs the bytes with a repeating key and shows hex.",
        "Key text", "Any text", false,
        { t, k, _ -> toHex(xorBytes(t.toByteArray(), k.toByteArray())) },
        { t, k, _ -> bytesToText(xorBytes(fromHex(t), k.toByteArray())) }),
)

fun cipherById(id: String): Cipher? = CIPHERS.firstOrNull { it.id == id }
