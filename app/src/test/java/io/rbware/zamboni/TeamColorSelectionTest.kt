package io.rbware.zamboni

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.rbware.zamboni.data.model.TeamColors
import io.rbware.zamboni.ui.scores.contrast
import io.rbware.zamboni.ui.scores.selectComparisonColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamColorSelectionTest {
    private val light = Color(0xFFF8F9FF)
    private val dark = Color(0xFF111318)
    private val fallbackAway = Color(0xFF2B5EA7)
    private val fallbackHome = Color(0xFFB3261E)

    // Real values from the API.
    private val seattle = TeamColors.fromHex("#001628", "#99D9D9")
    private val carolina = TeamColors.fromHex("#CE1126", "#000000")
    private val chicago = TeamColors.fromHex("#CF0A2C", "#000000")
    private val nashville = TeamColors.fromHex("#FFB81C", "#041E42")

    private fun pick(away: TeamColors?, home: TeamColors?, background: Color) =
        selectComparisonColors(away, home, background, fallbackAway, fallbackHome)

    @Test
    fun usesPrimaryColorsWhenVisibleAndDistinct() {
        val (away, home) = pick(seattle, carolina, light)
        assertEquals(0xFF001628.toInt(), away.toArgb())
        assertEquals(0xFFCE1126.toInt(), home.toArgb())
    }

    @Test
    fun darkTheme_skipsPrimaryThatDisappearsIntoBackground() {
        val (away, _) = pick(seattle, carolina, dark)
        assertEquals(0xFF99D9D9.toInt(), away.toArgb())
    }

    @Test
    fun clashingPrimaries_homeSwitchesToItsOtherColor() {
        val (away, home) = pick(carolina, chicago, light)
        assertEquals(0xFFCE1126.toInt(), away.toArgb())
        assertEquals(0xFF000000.toInt(), home.toArgb())
    }

    @Test
    fun lightTheme_skipsPrimaryWithTooLittleContrast() {
        // Nashville gold on a near-white background is under 3:1, so navy is used.
        val (away, _) = pick(nashville, carolina, light)
        assertEquals(0xFF041E42.toInt(), away.toArgb())
    }

    @Test
    fun noUsableColors_areAdjustedUntilVisible() {
        val darkOnly = TeamColors.fromHex("#050505", "#101010")
        val (away, _) = pick(darkOnly, carolina, dark)
        assertTrue(contrast(away, dark) >= 3f)
    }

    @Test
    fun missingColors_useFallbacks() {
        val (away, home) = pick(null, null, light)
        assertEquals(fallbackAway, away)
        assertEquals(fallbackHome, home)
        assertNotEquals(away, home)
    }

    @Test
    fun parsesHexColors() {
        assertEquals(TeamColors(0xFF001628.toInt(), null), TeamColors.fromHex("#001628"))
        assertEquals(null, TeamColors.fromHex("bogus", null))
    }
}
