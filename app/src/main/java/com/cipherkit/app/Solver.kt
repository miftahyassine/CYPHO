package com.cipherkit.app

import kotlin.math.max
import kotlin.math.min

class Candidate(
    val method: String,
    val key: String,
    val output: String,
    val score: Double,
    val language: String,
    val cribHit: Boolean,
) {
    /** 0-100, relative to the best result of the run. */
    var percent: Int = 0
}

class SolveReport(
    val results: List<Candidate>,
    val tried: Int,
    val ms: Long,
    val languages: String,
    /** True when the best result clearly beats the runner-up (or matched the known word). */
    val confident: Boolean,
)

object Solver {

    // ------------------------------------------------------------ scoring

    private fun isBad(ch: Char) = ch == '\uFFFD' || (ch.code < 32 && ch != '\n' && ch != '\t' && ch != '\r')

    /** Higher = more like natural text in [a]. Roughly 0.35 for noise, 0.75 and up for real text. */
    fun plausibility(text: String, a: Alphabet): Double {
        if (text.isEmpty()) return 0.0
        var letters = 0
        var sum = 0.0
        var bad = 0
        var nonSpace = 0
        for (ch in text) {
            if (isBad(ch)) bad++
            if (!ch.isWhitespace()) nonSpace++
            val i = a.index(ch)
            if (i >= 0) { letters++; sum += a.weights[i] }
        }
        if (letters == 0) return 0.0
        val ratio = letters.toDouble() / max(1, nonSpace)
        var s = (sum / letters) * (0.4 + 0.6 * ratio)
        if (s > 0.55 && letters >= 3) s += wordBonus(text, a)
        if (bad > 0) s *= 0.3
        return s
    }

    /** Share of the letters that belong to common words of the language (0..0.6). */
    private fun wordBonus(text: String, a: Alphabet): Double {
        val lower = text.lowercase()
        val tokens = ArrayList<String>()
        val sb = StringBuilder()
        val stripped = StringBuilder()
        for (ch in lower) {
            if (ch.isLetter()) {
                sb.append(ch)
                val i = a.index(ch)
                if (i >= 0) stripped.append(a.letters[i])
            } else if (sb.isNotEmpty()) { tokens.add(sb.toString()); sb.setLength(0) }
        }
        if (sb.isNotEmpty()) tokens.add(sb.toString())
        val total = stripped.length
        if (total == 0) return 0.0
        var tokCov = 0
        for (t in tokens) if (t.length >= 2 && a.wordSet.contains(t)) tokCov += t.length
        var flatCov = 0
        val flat = stripped.toString()
        for (w in a.longWords) {
            var from = 0
            while (true) {
                val p = flat.indexOf(w, from)
                if (p < 0) break
                flatCov += w.length
                from = p + w.length
            }
        }
        return 0.6 * min(1.0, max(tokCov, flatCov).toDouble() / total)
    }

    /** Which alphabets plausibly contain this text. */
    fun pickAlphabets(text: String): List<Alphabet> {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return listOf(ALPHABETS[0])
        val picked = ALPHABETS.filter { a ->
            letters.count { a.index(it) >= 0 }.toDouble() / letters.length >= 0.6
        }
        return picked.ifEmpty { ALPHABETS }
    }

    // ------------------------------------------------------------ search

    private class Run(val crib: String, val basePenalty: Double) {
        val all = ArrayList<Candidate>()
        var tried = 0
    }

    private fun emit(run: Run, a: Alphabet, method: String, key: String, out: String, penalty: Double = 0.0) {
        run.tried++
        if (out.isBlank()) return
        var s = plausibility(out, a) - penalty - run.basePenalty
        val hit = run.crib.isNotBlank() && out.lowercase().contains(run.crib.lowercase())
        if (hit) s += 1.0
        run.all.add(Candidate(method, key, out, s, a.name, hit))
    }

    private fun keywordsFor(a: Alphabet): List<String> =
        (a.keywords + ALPHABETS[0].keywords).filter { a.has(it) }.distinct()

    fun solve(text: String, alphabets: List<Alphabet>, methodId: String?, crib: String, maxResults: Int = 60): SolveReport {
        val t0 = System.currentTimeMillis()
        val run = Run(crib.trim(), 0.0)
        for (a in alphabets) attack(text, a, methodId, run, layer = 0)
        // de-duplicate by output, keep the best score
        val best = LinkedHashMap<String, Candidate>()
        for (c in run.all) {
            val old = best[c.output]
            if (old == null || c.score > old.score) best[c.output] = c
        }
        val sorted = best.values.sortedByDescending { it.score }.take(maxResults)
        val top = sorted.firstOrNull()
        for (c in sorted) {
            c.percent = if (c.cribHit) 100 else if (top == null || top.score <= 0.0) 0 else (c.score / top.score * 100).toInt().coerceIn(0, 100)
        }
        val confident = top != null && (top.cribHit || sorted.size < 2 || top.score - sorted[1].score >= 0.07) && top.score >= 0.6
        val langs = alphabets.joinToString(", ") { it.name.substringBefore(" (") }
        return SolveReport(sorted, run.tried, System.currentTimeMillis() - t0, langs, confident)
    }

    private fun want(methodId: String?, vararg ids: String) = methodId == null || ids.contains(methodId)

    private fun attack(text: String, a: Alphabet, m: String?, run: Run, layer: Int, prefix: String = "") {
        fun go(name: String, key: String, out: String) =
            emit(run, a, prefix + name, key, out)

        fun tryDec(c: Cipher, key: String) {
            val out = try { c.dec(text, key, a) } catch (e: Exception) { null }
            if (out != null) emit(run, a, prefix + c.name, key.uppercase(), out)
        }

        val n = a.size
        val idx = letterIndices(text, a)

        if (want(m, "caesar", "rot")) {
            for (s in 1 until n) go("Caesar", "shift $s", mapLetters(text, a) { i, _ -> i - s })
        }
        if (want(m, "atbash")) go("Atbash", "—", mapLetters(text, a) { i, _ -> n - 1 - i })
        if (want(m, "affine")) {
            for (x in 2 until n) {
                val inv = affineInverse(x, n) ?: continue
                for (y in 0 until n) go("Affine", "$x,$y", mapLetters(text, a) { i, _ -> inv * (i - y) })
            }
        }
        if (want(m, "rot47") && layer == 0) go("ROT47", "—", ROT47(text))
        if (want(m, "rail")) {
            for (r in 2..min(25, text.length - 1)) go("Rail fence", "rails $r", railFence(text, r, false))
        }
        if (want(m, "reverse")) go("Reverse", "—", text.reversed())

        if (idx.size >= 6) {
            if (want(m, "vigenere")) periodic(text, a, idx, 0, run, prefix)
            if (want(m, "beaufort")) periodic(text, a, idx, 1, run, prefix)
            if (want(m, "gronsfeld")) periodic(text, a, idx, 2, run, prefix)
        }

        // dictionary attacks with likely keywords
        val kws = keywordsFor(a)
        for (kw in kws) {
            val ks = keyIdx2(kw, a)
            if (want(m, "vigenere")) go("Vigenère", kw.uppercase(), mapLetters(text, a) { i, p -> i - ks[p % ks.size] })
            if (want(m, "beaufort")) go("Beaufort", kw.uppercase(), mapLetters(text, a) { i, p -> ks[p % ks.size] - i })
            if (want(m, "autokey")) cipherById("autokey")?.let { tryDec(it, kw) }
            if (want(m, "keyword")) cipherById("keyword")?.let { tryDec(it, kw) }
            if (want(m, "playfair") && idx.size >= 2) cipherById("playfair")?.let { tryDec(it, kw) }
            if (want(m, "polybius")) cipherById("polybius")?.let { tryDec(it, kw) }
            if (want(m, "columnar") && kw.length >= 2) cipherById("columnar")?.let { tryDec(it, kw) }
        }

        // columnar transposition with every column order up to 7 columns
        if (want(m, "columnar") && layer == 0 && text.length in 8..1500) {
            for (k in 2..7) permutations(k) { perm ->
                go("Columnar transposition", perm.joinToString("") { (it + 1).toString() }, columnar(text, perm, false))
            }
        }

        // XOR on hex input
        if (want(m, "xor")) {
            val bytes = try { fromHex(text) } catch (e: IllegalArgumentException) { null }
            if (bytes != null) {
                for (k in 1..255) go("XOR", "byte 0x%02x".format(k), bytesToText(xorBytes(bytes, byteArrayOf(k.toByte()))))
                for (kw in kws) go("XOR", kw, bytesToText(xorBytes(bytes, kw.toByteArray())))
            }
        }

        // keyless encodings, optionally followed by a second layer
        val keyless = listOf("base64", "base32", "hex", "binary", "decimal", "morse", "a1z26", "polybius", "bacon")
        for (id in keyless) {
            if (!want(m, id)) continue
            if (!strictInput(id, text)) continue
            val c = cipherById(id) ?: continue
            val out = try { c.dec(text, "", a) } catch (e: Exception) { null } ?: continue
            if (out.isBlank() || out == text) continue
            go(c.name, "—", out)
            if (layer == 0 && m == null && out.length >= 4 && plausibility(out, a) < 0.72 && looksTextual(out)) {
                val inner = Run(run.crib, 0.05)
                attack(out, a, null, inner, layer = 1, prefix = c.name + " → ")
                run.tried += inner.tried
                run.all.addAll(inner.all.sortedByDescending { it.score }.take(3))
            }
        }
        if (layer == 0 && want(m, "reverse")) {
            val words = text.split(" ").joinToString(" ") { it.reversed() }
            if (words != text) emit(run, a, prefix + "Reverse each word", "—", words, 0.03)
        }
    }

    /** Lenient decoders only run when the whole input uses their symbols. */
    private fun strictInput(id: String, t: String): Boolean = when (id) {
        "morse" -> t.isNotBlank() && t.all { it in ".-/| \n" }
        "a1z26" -> t.isNotBlank() && t.all { it.isDigit() || it.isWhitespace() || it in "/,.-" }
        "polybius" -> t.isNotBlank() && t.all { it in '1'..'9' || it.isWhitespace() || it == '/' }
        "bacon" -> t.isNotBlank() && t.all { it == 'A' || it == 'B' || it == 'a' || it == 'b' || it.isWhitespace() }
        else -> true
    }

    private fun looksTextual(s: String): Boolean {
        val ok = s.count { it.isLetterOrDigit() || it.isWhitespace() || it in ".,-/;:'!?" }
        return ok.toDouble() / s.length >= 0.8
    }

    private fun keyIdx2(k: String, a: Alphabet): List<Int> = k.map { a.index(it) }.filter { it >= 0 }

    /**
     * Key-length search. Candidate lengths are chosen by index of coincidence (so the search
     * does not "over-fit" with long keys), then the best shift per column comes from letter frequencies.
     */
    private fun periodic(text: String, a: Alphabet, idx: List<Int>, kind: Int, run: Run, prefix: String) {
        val n = a.size
        val w = a.weights
        val maxLen = min(16, idx.size / 8)
        if (maxLen < 2 || idx.size < 16) return
        val shifts = if (kind == 2) min(10, n) else n

        val ics = DoubleArray(maxLen + 1)
        for (len in 2..maxLen) {
            var tot = 0.0
            var cols = 0
            for (j in 0 until len) {
                val counts = IntArray(n)
                var m = 0
                var i = j
                while (i < idx.size) { counts[idx[i]]++; m++; i += len }
                if (m >= 2) {
                    var s = 0
                    for (c in counts) s += c * (c - 1)
                    tot += s.toDouble() / (m * (m - 1))
                    cols++
                }
            }
            ics[len] = if (cols > 0) tot / cols else 0.0
        }
        // overall index of coincidence: if columns are not clearly "more English" than the whole text,
        // the text is not polyalphabetic and a periodic key would only over-fit.
        val all = IntArray(n)
        idx.forEach { all[it]++ }
        var tot = 0
        for (c in all) tot += c * (c - 1)
        val icAll = tot.toDouble() / (idx.size * (idx.size - 1))
        val maxIc = (2..maxLen).maxOf { ics[it] }
        if (maxIc < icAll * 1.25) return
        val good = (2..maxLen).filter { ics[it] >= icAll * 1.25 }
        val lens = good.sortedByDescending { ics[it] }.take(3).toMutableSet()
        good.firstOrNull { ics[it] >= 0.8 * maxIc }?.let { lens.add(it) }

        for (len in lens.sorted()) {
            val key = IntArray(len)
            for (j in 0 until len) {
                var bestS = 0
                var bestV = -1.0
                for (s in 0 until shifts) {
                    var v = 0.0
                    var i = j
                    while (i < idx.size) {
                        val p = if (kind == 1) (s - idx[i]).mod(n) else (idx[i] - s).mod(n)
                        v += w[p]
                        i += len
                    }
                    if (v > bestV) { bestV = v; bestS = s }
                }
                key[j] = bestS
            }
            val out = if (kind == 1) mapLetters(text, a) { i, p -> key[p % len] - i }
            else mapLetters(text, a) { i, p -> i - key[p % len] }
            val keyText = if (kind == 2) key.joinToString("") else key.map { a.letters[it].uppercaseChar() }.joinToString("")
            val name = when (kind) { 0 -> "Vigenère"; 1 -> "Beaufort"; else -> "Gronsfeld" }
            emit(run, a, prefix + name, keyText, out, 0.004 * len)
        }
    }

    private fun permutations(k: Int, f: (IntArray) -> Unit) {
        val arr = IntArray(k) { it }
        fun rec(pos: Int) {
            if (pos == k) { f(arr); return }
            for (i in pos until k) {
                var t = arr[pos]; arr[pos] = arr[i]; arr[i] = t
                rec(pos + 1)
                t = arr[pos]; arr[pos] = arr[i]; arr[i] = t
            }
        }
        rec(0)
    }
}
