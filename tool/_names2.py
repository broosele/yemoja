"""One-shot: three names that read as the wrong thing. Deleted after use."""
import io

M = "data/src/commonMain/kotlin/yemoja/data/"
T = "data/src/commonTest/kotlin/yemoja/data/"


def swap(path, pairs):
    s = io.open(path, encoding="utf-8").read()
    n = 0
    for old, new in pairs:
        c = s.count(old)
        assert c >= 1, (path, old[:70])
        s = s.replace(old, new)
        n += c
    io.open(path, "w", encoding="utf-8", newline="\n").write(s)
    print("%-56s %2d" % (path, n))


# 1. oneOff -> oneOffAllowed. The flag says a plain name is allowed, not that this is one.
swap(M + "Description.kt", [
    ("    /** A plain name may stand in, asserting no id. */\n    val oneOff: Boolean = false,",
     "    /** Whether a plain name may stand in, asserting no id. */\n"
     "    val oneOffAllowed: Boolean = false,"),
    ("Reference.parse(text, oneOff)", "Reference.parse(text, oneOffAllowed)"),
    ("value is Reference.OneOff && !oneOff ->", "value is Reference.OneOff && !oneOffAllowed ->"),
])
swap(M + "Reference.kt", [
    ("         * [oneOff] says whether a plain name is allowed here. Where it is not, text without",
     "         * [oneOffAllowed] says whether a plain name may stand in here. Where it may not, text"),
    ("        fun parse(text: String, oneOff: Boolean): Reference {",
     "        fun parse(text: String, oneOffAllowed: Boolean): Reference {"),
    ("            if (!oneOff) throw ValueFormatException(",
     "            if (!oneOffAllowed) throw ValueFormatException("),
])

# 2. suggested -> suggestedSet, so it matches fixedSet beside it.
swap(M + "Description.kt", [
    ("    suggested: Set<String>? = null,", "    suggestedSet: Set<String>? = null,"),
    ("    val suggested: Set<String>? = suggested?.toSet()",
     "    val suggestedSet: Set<String>? = suggestedSet?.toSet()"),
])

# 3. written -> asWritten. The locals in parse keep their own name.
swap(M + "Reference.kt", [
    ("    abstract val written: String", "    abstract val asWritten: String"),
    ('        override val written: String get() = "@$id"',
     '        override val asWritten: String get() = "@$id"'),
    ("        override val written: String get() = name",
     "        override val asWritten: String get() = name"),
    ('    val written: String get() = "*$key"', '    val asWritten: String get() = "*$key"'),
    ("    override fun toString(): String = written",
     "    override fun toString(): String = asWritten"),
])

swap(T + "DescriptionTest.kt", [
    ('TextDescription("colour", suggested = setOf("red", "green"))',
     'TextDescription("colour", suggestedSet = setOf("red", "green"))'),
    ("colour.suggested)", "colour.suggestedSet)"),
    ('targetType = "district", oneOff = true)', 'targetType = "district", oneOffAllowed = true)'),
])
swap(T + "ReferenceTest.kt", [
    ("oneOff = false", "oneOffAllowed = false"),
    ("oneOff = true", "oneOffAllowed = true"),
    (".written)", ".asWritten)"),
    ("reference.asWritten, oneOffAllowed = true", "reference.asWritten, oneOffAllowed = true"),
])
