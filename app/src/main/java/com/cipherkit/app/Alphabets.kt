package com.cipherkit.app

/**
 * One writing system. [freq] lists the letters from most to least common and is used to
 * score how much a decrypted text looks like that language. [fold] maps variant letters
 * (accents, final forms) onto the base letters of [letters].
 */
class Alphabet(
    val id: String,
    val name: String,
    val letters: String,
    val freq: String,
    val words: List<String>,
    val keywords: List<String>,
    val morse: Map<Char, String> = emptyMap(),
    val latin: Boolean = false,
    val fold: Map<Char, Char> = emptyMap(),
) {
    val size: Int get() = letters.length

    val wordSet: Set<String> = words.filter { it.length >= 2 }.toHashSet()
    val longWords: List<String> = words.filter { it.length >= 4 && it.all { c -> letters.indexOf(c) >= 0 } }

    /** 0..1 weight per letter: common letters are close to 1. */
    val weights: DoubleArray = DoubleArray(letters.length) { i ->
        val r = freq.indexOf(letters[i])
        if (r < 0) 0.05 else (freq.length - r).toDouble() / freq.length
    }

    /** Index of [c] in [letters] (case-insensitive, accent-folded) or -1. */
    fun index(c: Char): Int {
        val lc = c.lowercaseChar()
        val i = letters.indexOf(lc)
        if (i >= 0) return i
        val f = fold[lc] ?: return -1
        return letters.indexOf(f)
    }

    fun has(word: String): Boolean = word.isNotEmpty() && word.all { index(it) >= 0 }
}

private fun pairs(s: String): Map<Char, String> =
    s.trim().split("|").filter { it.length >= 3 }.associate { it[0] to it.substring(2) }

val LATIN = "abcdefghijklmnopqrstuvwxyz"

private val FOLD_GREEK = mapOf(
    'ά' to 'α', 'έ' to 'ε', 'ή' to 'η', 'ί' to 'ι', 'ό' to 'ο', 'ύ' to 'υ', 'ώ' to 'ω',
    'ς' to 'σ', 'ϊ' to 'ι', 'ΐ' to 'ι', 'ϋ' to 'υ', 'ΰ' to 'υ',
)
private val FOLD_ARABIC = mapOf(
    'أ' to 'ا', 'إ' to 'ا', 'آ' to 'ا', 'ٱ' to 'ا', 'ة' to 'ه', 'ى' to 'ي', 'ؤ' to 'و', 'ئ' to 'ي',
)
private val FOLD_RUSSIAN = mapOf('ѣ' to 'е')

val ALPHABETS: List<Alphabet> = listOf(
    Alphabet(
        id = "en", name = "English", latin = true,
        letters = LATIN,
        freq = "etaoinshrdlcumwfgypbvkjxqz",
        words = "the and you that was for are with his they have this from had not but what all were when your can said there use each which she how their will other about out many then them these some her would make like him into time has look two more write see number way could people than first water been call who its now find long down day did get come made may part hello secret message".split(" "),
        keywords = "key secret password cipher code crypto challenge hidden love lemon enigma kryptos monarchy example security privacy puzzle mystery hello admin wonder silver python kotlin android github random master shadow dragon".split(" "),
    ),
    Alphabet(
        id = "es", name = "Español (Spanish)", latin = true,
        letters = "abcdefghijklmnñopqrstuvwxyz",
        freq = "eaosrnidlctumpbhqygvjñfzxkw",
        words = "de la que el en los se del las por un para con una su al es lo como mas pero sus ya fue este ha porque esta son entre cuando muy sin sobre tambien me hasta hay donde quien desde todo nos durante hola mensaje secreto clave".split(" "),
        keywords = "clave secreto codigo amor mensaje hola cifrado mision tesoro".split(" "),
        morse = mapOf('ñ' to "--.--"),
    ),
    Alphabet(
        id = "de", name = "Deutsch (German)", latin = true,
        letters = "abcdefghijklmnopqrstuvwxyzäöüß",
        freq = "enisratdhulcgmobwfkzvüpäßjöyxq",
        words = "der die und in den von zu das mit sich des auf für ist im dem nicht ein eine als auch es an werden aus er hat dass sie nach wird bei einer um am sind noch wie einem über einen so zum war haben nur oder aber vor zur bis mehr durch man sein wurde hallo geheim nachricht".split(" "),
        keywords = "schluessel schlüssel geheim liebe nachricht passwort hallo".split(" "),
        morse = mapOf('ä' to ".-.-", 'ö' to "---.", 'ü' to "..--", 'ß' to "...--.."),
    ),
    Alphabet(
        id = "fr", name = "Français (French)", latin = true,
        letters = "abcdefghijklmnopqrstuvwxyzàâçéèêëîïôûùüÿœæ",
        freq = "esaitnrulodcpmévqfbghjàxèyêzçùôâîœëïûüwkÿæ",
        words = "le la les de des un une et en que qui dans pour pas sur au avec ce il elle est son ses plus par ne se mais comme nous vous ils sont tout ete fait etre cette aux ou ont leur bonjour message secret".split(" "),
        keywords = "cle clé secret code amour message bonjour chiffre mystere".split(" "),
        morse = mapOf('à' to ".--.-", 'ç' to "-.-..", 'è' to ".-..-", 'é' to "..-.."),
    ),
    Alphabet(
        id = "ru", name = "Русский (Russian)",
        letters = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя",
        freq = "оеаинтсрвлкмдпуяыьгзбчйхжшюцщэфъё",
        words = "и в не на я что он с как а то все она так его но да ты к у же вы за бы по только ее мне было вот от меня еще нет о из ему теперь когда даже ну вдруг ли если уже или ни быть был него до вас нибудь опять уж вам сказал ведь там потом себя ничего ей может они тут где есть надо ней для мы тебя их чем была сам чтоб без будто привет сообщение секрет".split(" "),
        keywords = "ключ секрет пароль шифр код любовь привет сообщение тайна".split(" "),
        morse = pairs("а .-|б -...|в .--|г --.|д -..|е .|ё .|ж ...-|з --..|и ..|й .---|к -.-|л .-..|м --|н -.|о ---|п .--.|р .-.|с ...|т -|у ..-|ф ..-.|х ....|ц -.-.|ч ---.|ш ----|щ --.-|ъ --.--|ы -.--|ь -..-|э ..-..|ю ..--|я .-.-"),
        fold = FOLD_RUSSIAN,
    ),
    Alphabet(
        id = "el", name = "Ελληνικά (Greek)",
        letters = "αβγδεζηθικλμνξοπρστυφχψω",
        freq = "ατοεινρσκπμλυδηωγχθβζξφψ",
        words = "και το να η ο του της με που των για δεν στο στη απο την τον ειναι ενα μια θα ως αλλα σε οτι μου σας γεια μυστικο".split(" "),
        keywords = "κλειδι μυστικο κωδικος αγαπη γεια μηνυμα".split(" "),
        morse = pairs("α .-|β -...|γ --.|δ -..|ε .|ζ --..|η ....|θ -.-.|ι ..|κ -.-|λ .-..|μ --|ν -.|ξ -..-|ο ---|π .--.|ρ .-.|σ ...|τ -|υ -.--|φ ..-.|χ ----|ψ --.-|ω .--"),
        fold = FOLD_GREEK,
    ),
    Alphabet(
        id = "ar", name = "العربية (Arabic)",
        letters = "ابتثجحخدذرزسشصضطظعغفقكلمنهوي",
        freq = "اليمونرتبهعدكسفقحجشصطضزخثغذظ",
        words = "في من على ان الى هذا التي الذي هو هي كان ما لا مع كل عن بعد ذلك لم قد او ثم هذه ولا بين حتى كما انا انت نحن مرحبا سر رسالة".split(" "),
        keywords = "مفتاح سر كلمة حب رمز مرحبا رسالة".split(" "),
        morse = pairs("ا .-|ب -...|ت -|ث -.-.|ج .---|ح ....|خ ---|د -..|ذ --..|ر .-.|ز ---.|س ...|ش ----|ص -..-|ض ...-|ط ..-|ظ -.--|ع .-.-|غ --.|ف ..-.|ق --.-|ك -.-|ل .-..|م --|ن -.|ه ..-..|و .--|ي .."),
        fold = FOLD_ARABIC,
    ),
    Alphabet(
        id = "he", name = "עברית (Hebrew)",
        letters = "אבגדהוזחטיכךלמםנןסעפףצץקרשת",
        freq = "יוהלארתמבנשעכדחקפסטגצזםןךףץ",
        words = "של את על לא זה הוא היא אני אתה כי עם מה או גם אבל כל יש היה אם רק שלום מפתח סוד".split(" "),
        keywords = "מפתח סוד קוד אהבה שלום".split(" "),
        morse = pairs("א .-|ב -...|ג --.|ד -..|ה ---|ו .|ז --..|ח ....|ט ..-|י ..|כ -.-|ך -.-|ל .-..|מ --|ם --|נ -.|ן -.|ס -.-.|ע .---|פ .--.|ף .--.|צ .--|ץ .--|ק --.-|ר .-.|ש ...|ת -"),
    ),
)

fun alphabetById(id: String): Alphabet = ALPHABETS.firstOrNull { it.id == id } ?: ALPHABETS[0]
