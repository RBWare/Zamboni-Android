package io.rbware.zamboni.data.model

/** A team's brand colors as ARGB ints. Either may be missing. */
data class TeamColors(
    val primary: Int?,
    val secondary: Int? = null,
) {
    companion object {
        /** Builds colors from "#RRGGBB" strings; invalid or missing values become null. */
        fun fromHex(primary: String?, secondary: String? = null): TeamColors? {
            val p = parseHexColor(primary)
            val s = parseHexColor(secondary)
            return if (p == null && s == null) null else TeamColors(p, s)
        }

        private fun parseHexColor(hex: String?): Int? {
            val digits = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
            return digits.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }
        }
    }
}
