package io.rbware.zamboni

import io.rbware.zamboni.data.leagues.Leagues
import io.rbware.zamboni.data.leagues.leagueIdOfTeam
import io.rbware.zamboni.data.model.Team
import io.rbware.zamboni.data.preferences.EnabledLeaguesMigration
import io.rbware.zamboni.ui.onboarding.OnboardingStep
import io.rbware.zamboni.ui.onboarding.OnboardingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaguesTest {

    @Test
    fun nhlIsTheOnlyDefault() {
        assertEquals(setOf("nhl"), Leagues.defaultEnabledIds)
        assertEquals(listOf("nhl", "ahl", "sphl", "nahl"), Leagues.all.map { it.id })
    }

    @Test
    fun leagueOfTeam() {
        assertEquals("ahl", leagueIdOfTeam("ahl-bel"))
        assertEquals("sphl", leagueIdOfTeam("sphl-fay"))
        assertEquals("nahl", leagueIdOfTeam("nahl-lone-star-brahmas"))
        assertNull(leagueIdOfTeam("xyz-abc"))
        assertNull(leagueIdOfTeam("dal"))
    }

    @Test
    fun existingUsersKeepWhatTheyUse() {
        assertEquals(setOf("nhl"), EnabledLeaguesMigration.enabledLeaguesFor(setOf("nhl-dal"), hasGamesheetKey = false))
        assertEquals(
            setOf("nhl", "ahl", "nahl"),
            EnabledLeaguesMigration.enabledLeaguesFor(setOf("ahl-tex", "nahl-lone-star-brahmas"), hasGamesheetKey = false),
        )
        assertEquals(setOf("nhl", "nahl"), EnabledLeaguesMigration.enabledLeaguesFor(emptySet(), hasGamesheetKey = true))
    }

    private val dal = Team("nhl-dal", "Dallas Stars", "DAL", Leagues.NHL)
    private val tex = Team("ahl-tex", "Texas Stars", "TEX", Leagues.AHL)

    @Test
    fun onboarding_teamsFollowTheChosenLeagues() {
        val state = OnboardingUiState(
            step = OnboardingStep.TEAMS,
            teams = listOf(dal, tex),
            selectedLeagueIds = setOf("nhl"),
            selectedTeamIds = setOf("ahl-tex"),
        )
        assertEquals(listOf(dal), state.visibleTeams)
        // Texas was picked, then the AHL turned off: it doesn't count.
        assertEquals(emptySet<String>(), state.effectiveTeamIds)
        assertFalse(state.canAdvance)
        assertTrue(state.copy(selectedLeagueIds = setOf("nhl", "ahl")).canAdvance)
    }

    @Test
    fun onboarding_leagueWithNoTeamsYetDoesNotBlock() {
        // NAHL alone without a Gamesheet key: nothing to pick, so it can't be required.
        val state = OnboardingUiState(step = OnboardingStep.TEAMS, teams = listOf(dal, tex), selectedLeagueIds = setOf("nahl"))
        assertTrue(state.canAdvance)
        assertFalse(state.copy(teams = null).canAdvance)
    }
}
