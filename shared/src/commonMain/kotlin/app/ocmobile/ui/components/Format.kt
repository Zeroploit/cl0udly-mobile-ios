package app.ocmobile.ui.components

/**
 * Money, tokens and percents in one format everywhere (the usage panel, the Usage screen, the price hint),
 * with the decimal separator of the phone's locale.
 *
 * KMP port: `java.util.Locale`/`String.format` do not exist in commonMain, so the
 * formatting is done with a small Kotlin helper ([fixed], same HALF-UP rounding as
 * Java's `Formatter`); the separator is always ".".
 */
object Fmt {

    /** Java `%.<digits>f` semantics: scale, round half away from zero, print with fixed digits. */
    internal fun fixed(x: Double, digits: Int): String {
        val f = when (digits) { 0 -> 1L; 1 -> 10L; 2 -> 100L; 3 -> 1000L; else -> {
            var p = 1L; repeat(digits) { p *= 10 }; p
        } }
        val n = kotlin.math.round(x * f).toLong()
        val neg = n < 0
        val a = kotlin.math.abs(n)
        val intPart = a / f
        val frac = a % f
        var s = intPart.toString()
        if (digits > 0) s += "." + frac.toString().padStart(digits, '0')
        return if (neg) "-$s" else s
    }

    fun money(x: Double): String = "$" + if (x >= 100) fixed(x, 0) else fixed(x, 2)

    /** A price that may be tiny: "<$0.01" instead of "$0.00". */
    fun price(x: Double): String = if (x > 0 && x < 0.01) "<" + money(0.01) else money(x)

    fun tokens(n: Long): String = when {
        n >= 1_000_000 -> fixed(n / 1_000_000.0, 1) + "M"
        n >= 10_000 -> fixed(n / 1000.0, 0) + "k"
        n >= 1000 -> fixed(n / 1000.0, 1) + "k"
        else -> n.toString()
    }

    /** [p] in percent (0..100). */
    fun percent(p: Double, digits: Int = 0): String = fixed(p, digits) + "%"

    /** "1 reply", "2 replies". */
    fun count(n: Long, one: String, many: String) = "$n " + if (n == 1L) one else many
}
