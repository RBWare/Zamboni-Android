package io.rbware.zamboni.data.model

/**
 * Logo image URLs for a team.
 *
 * @param light logo for light backgrounds (used with the light theme).
 * @param dark logo for dark backgrounds (used with the dark theme).
 */
data class TeamLogo(
    val light: String,
    val dark: String,
) {
    fun forTheme(darkTheme: Boolean): String = if (darkTheme) dark else light

    val all: List<String> get() = listOf(light, dark)
}
