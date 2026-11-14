package com.cipherkit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CipherTheme { App() } }
    }
}

class UiState {
    var tab by mutableStateOf(0)
    var alphabet by mutableStateOf(ALPHABETS[0])

    // cipher tab
    var category by mutableStateOf("All")
    var cipher by mutableStateOf(CIPHERS[0])
    var key by mutableStateOf("")
    var input by mutableStateOf("")
    var output by mutableStateOf("")
    var error by mutableStateOf("")

    // solve tab
    var solveMethod by mutableStateOf<Cipher?>(null)
    var autoLang by mutableStateOf(false)
    var crib by mutableStateOf("")
    var solving by mutableStateOf(false)
    var report by mutableStateOf<SolveReport?>(null)
    var solveError by mutableStateOf("")
    var shown by mutableStateOf(6)

    // key finder tab
    var known by mutableStateOf("")
    var pos by mutableStateOf("1")
    var cribOut by mutableStateOf<CribResult?>(null)
    var cribError by mutableStateOf("")

    // detect tab
    var detectOut by mutableStateOf("")
    var detectError by mutableStateOf("")

    var toast by mutableStateOf("")
}

private fun runCipher(s: UiState, encode: Boolean) {
    s.error = ""
    s.output = ""
    try {
        s.output = if (encode) s.cipher.enc(s.input, s.key, s.alphabet) else s.cipher.dec(s.input, s.key, s.alphabet)
        if (s.output.isEmpty()) s.error = "Nothing to show. Type a message first."
    } catch (e: Exception) {
        s.error = e.message ?: "Something went wrong"
    }
}

private fun runSolve(s: UiState) {
    if (s.input.isBlank()) { s.solveError = "Paste or type the cipher text first."; return }
    s.solveError = ""
    s.solving = true
    s.report = null
    s.shown = 6
    val text = s.input
    val alphabets = if (s.autoLang) Solver.pickAlphabets(text) else listOf(s.alphabet)
    val method = s.solveMethod?.id
    val crib = s.crib
    thread {
        try {
            val r = Solver.solve(text, alphabets, method, crib)
            s.report = r
        } catch (e: Throwable) {
            s.solveError = "The solver stopped: " + (e.message ?: "unknown error")
        }
        s.solving = false
    }
}

@Composable
fun App() {
    val s = remember { UiState() }
    val clip = LocalClipboardManager.current
    val scroll = rememberScrollState()

    LaunchedEffect(s.toast) {
        if (s.toast.isNotEmpty()) {
            delay(1500)
            s.toast = ""
        }
    }
    LaunchedEffect(s.tab) { scroll.scrollTo(0) }

    val copy: (String) -> Unit = { text ->
        clip.setText(AnnotatedString(text))
        s.toast = "Copied ✓"
    }
    val paste: () -> String = { clip.getText()?.text ?: "" }

    Column(Modifier.fillMaxSize().background(CK.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("Cipher", size = 26, weight = FontWeight.Bold)
            Txt("Kit", color = CK.Accent, size = 26, weight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            LangPill(s.alphabet) { s.alphabet = it }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (s.tab) {
                    0 -> CipherTab(s, copy, paste)
                    1 -> SolveTab(s, copy, paste)
                    2 -> KeyTab(s, paste)
                    else -> DetectTab(s, paste)
                }
                Spacer(Modifier.height(24.dp))
            }
            if (s.toast.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .clip(RoundedCornerShape(50))
                        .background(CK.Accent)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) { Txt(s.toast, color = Color.Black, weight = FontWeight.SemiBold) }
            }
        }
        BottomBar(s.tab) { s.tab = it }
    }
}

// ------------------------------------------------------------------ Cipher tab

@Composable
fun CipherTab(s: UiState, copy: (String) -> Unit, paste: () -> String) {
    val list = if (s.category == "All") CIPHERS else CIPHERS.filter { it.category == s.category }
    val c = s.cipher

    CkCard {
        Caption("Method")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("All") + CATEGORIES).forEach { cat ->
                Chip(cat, s.category == cat) {
                    s.category = cat
                    val l = if (cat == "All") CIPHERS else CIPHERS.filter { it.category == cat }
                    if (!l.contains(s.cipher)) s.cipher = l.first()
                    s.output = ""
                    s.error = ""
                }
            }
        }
        Dropdown("Cipher", list, if (list.contains(c)) c else list.first(), { it.name }, { it.desc }) {
            s.cipher = it
            s.output = ""
            s.error = ""
        }
        Txt(c.desc, color = CK.Muted, size = 13)
        Txt(
            if (c.usesAlphabet) "🌐 Uses the ${s.alphabet.name} alphabet (${s.alphabet.size} letters)"
            else "🌐 Works with text in any language",
            color = CK.Accent, size = 12,
        )
        if (c.keyLabel != null) {
            Field(c.keyLabel, s.key, { s.key = it }, hint = c.keyHelp, singleLine = true)
        }
    }

    CkCard {
        Field("Message", s.input, { s.input = it }, hint = "Type or paste your text here", minLines = 4)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CkButton("Encode", Modifier.weight(1f)) { runCipher(s, true) }
            CkButton("Decode", Modifier.weight(1f), filled = false) { runCipher(s, false) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CkLink("Paste") { s.input = paste() }
            CkLink("Clear") { s.input = ""; s.output = ""; s.error = "" }
            Spacer(Modifier.weight(1f))
            CkLink("Don't know the key? Break it →") {
                s.solveMethod = s.cipher
                s.tab = 1
            }
        }
    }

    if (s.error.isNotEmpty()) {
        CkCard { Txt(s.error, color = CK.Error, size = 15) }
    } else if (s.output.isNotEmpty()) {
        CkCard(accent = true) {
            Caption("Result")
            SelectionContainer { Txt(s.output, mono = true, size = 16) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CkLink("Copy") { copy(s.output) }
                CkLink("Use as message") { s.input = s.output; s.output = "" }
            }
        }
    }
}

// ------------------------------------------------------------------ Solve tab

@Composable
fun SolveTab(s: UiState, copy: (String) -> Unit, paste: () -> String) {
    CkCard {
        Field("Cipher text", s.input, { s.input = it }, hint = "Paste the text you want to crack", minLines = 4)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CkLink("Paste") { s.input = paste() }
            CkLink("Clear") { s.input = ""; s.report = null }
        }
    }

    CkCard {
        Caption("Options")
        Dropdown<Cipher?>(
            "Methods to try",
            listOf<Cipher?>(null) + CIPHERS,
            s.solveMethod,
            { it?.name ?: "All methods (recommended)" },
            { it?.desc ?: "Tries every cipher and encoding, then ranks the results" },
        ) { s.solveMethod = it }
        Caption("Language")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(s.alphabet.name.substringBefore(" ("), !s.autoLang) { s.autoLang = false }
            Chip("Auto-detect", s.autoLang) { s.autoLang = true }
        }
        Field("Word you expect (optional)", s.crib, { s.crib = it }, hint = "A word that is probably in the message", singleLine = true)
        CkButton(
            if (s.solving) "Working…" else "⚡ Break it",
            Modifier.fillMaxWidth(),
            enabled = !s.solving,
        ) { runSolve(s) }
    }

    if (s.solveError.isNotEmpty()) CkCard { Txt(s.solveError, color = CK.Error) }

    val r = s.report
    if (r != null) {
        if (r.results.isEmpty()) {
            CkCard { Txt("Nothing readable came out. Try another language, or pick a single method.", color = CK.Muted) }
        } else {
            Txt(
                "Tried ${"%,d".format(r.tried)} possibilities in ${r.ms} ms · ${r.languages}",
                color = CK.Muted, size = 12,
            )
            Txt(
                if (r.confident) "✓ Clear winner at the top" else "Not sure: compare the first few results",
                color = if (r.confident) CK.Accent else CK.Muted, size = 13, weight = FontWeight.SemiBold,
            )
            r.results.take(s.shown).forEachIndexed { i, c ->
                CkCard(accent = i == 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt("#${i + 1}", color = CK.Accent, weight = FontWeight.Bold, size = 16)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(c.method, weight = FontWeight.SemiBold)
                            val k = if (c.key == "—") "" else "key: ${c.key}  ·  "
                            Txt(k + c.language, color = CK.Muted, size = 12)
                        }
                        Txt("${c.percent}%", color = CK.Muted, size = 13)
                    }
                    MatchBar(c.percent)
                    SelectionContainer { Txt(c.output, mono = true, size = 15) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CkLink("Copy") { copy(c.output) }
                        CkLink("Use as message") { s.input = c.output; s.tab = 0; s.output = ""; s.error = "" }
                    }
                }
            }
            if (r.results.size > s.shown) {
                CkButton("Show more results", Modifier.fillMaxWidth(), filled = false) { s.shown += 6 }
            }
        }
    }
}

// ------------------------------------------------------------------ Key finder tab

@Composable
fun KeyTab(s: UiState, paste: () -> String) {
    CkCard {
        Txt(
            "Know a word that is in the message? Enter it and the app works out the Vigenère key.",
            color = CK.Muted, size = 13,
        )
        Field("Cipher text", s.input, { s.input = it }, hint = "The encrypted message", minLines = 3)
        Field("Known word", s.known, { s.known = it }, hint = "e.g. HELLO", singleLine = true)
        Field("It starts at letter number", s.pos, { s.pos = it.filter { ch -> ch.isDigit() }.take(4) }, hint = "1 = the first letter", singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CkLink("Paste cipher text") { s.input = paste() }
        }
        CkButton("🔑 Find the key", Modifier.fillMaxWidth()) {
            s.cribError = ""
            s.cribOut = null
            try {
                s.cribOut = cribKey(s.input, s.known, s.alphabet, s.pos.toIntOrNull() ?: 1)
            } catch (e: Exception) {
                s.cribError = e.message ?: "Something went wrong"
            }
        }
    }
    if (s.cribError.isNotEmpty()) CkCard { Txt(s.cribError, color = CK.Error) }
    val r = s.cribOut
    if (r != null) {
        CkCard(accent = true) {
            Caption("Result")
            SelectionContainer { Txt(r.text, mono = true, size = 14) }
            if (r.key != null) {
                CkButton("Decode with key ${r.key}", Modifier.fillMaxWidth()) {
                    s.cipher = cipherById("vigenere") ?: CIPHERS[0]
                    s.category = "All"
                    s.key = r.key
                    s.tab = 0
                    runCipher(s, false)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Detect tab

@Composable
fun DetectTab(s: UiState, paste: () -> String) {
    CkCard {
        Txt("Not sure what kind of code you have? Paste it and the app suggests what it could be.", color = CK.Muted, size = 13)
        Field("Unknown text", s.input, { s.input = it }, hint = "Paste the mystery text", minLines = 4)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CkLink("Paste") { s.input = paste() }
            CkLink("Clear") { s.input = ""; s.detectOut = ""; s.detectError = "" }
        }
        CkButton("🔎 Analyse", Modifier.fillMaxWidth()) {
            s.detectError = ""
            s.detectOut = ""
            try {
                s.detectOut = detect(s.input, s.alphabet)
            } catch (e: Exception) {
                s.detectError = e.message ?: "Something went wrong"
            }
        }
    }
    if (s.detectError.isNotEmpty()) CkCard { Txt(s.detectError, color = CK.Error) }
    if (s.detectOut.isNotEmpty()) {
        CkCard(accent = true) {
            Caption("Possible matches")
            Txt(s.detectOut, size = 15)
            CkButton("⚡ Try all methods on this text", Modifier.fillMaxWidth(), filled = false) {
                s.solveMethod = null
                s.tab = 1
                runSolve(s)
            }
        }
    }
}
