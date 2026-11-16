package io.rbware.zamboni.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.rbware.zamboni.R
import io.rbware.zamboni.ui.preferences.NewsSourcePicker
import io.rbware.zamboni.ui.preferences.NotificationPicker
import io.rbware.zamboni.ui.preferences.SocialSourcePicker
import io.rbware.zamboni.ui.preferences.TeamPicker
import io.rbware.zamboni.ui.preferences.rememberNotificationPermissionRequester

/** Max content width so onboarding doesn't stretch edge-to-edge on tablets / unfolded foldables. */
private val MaxContentWidth = 840.dp

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val requestNotificationPermission = rememberNotificationPermissionRequester()

    BackHandler(enabled = !state.isFirstStep, onBack = viewModel::back)

    Scaffold(
        // Grouped pickers draw their rows on surfaceBright; this background sets them apart.
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        bottomBar = {
            OnboardingButtons(
                state = state,
                onBack = viewModel::back,
                onNext = {
                    when {
                        !state.isLastStep -> viewModel.next()
                        state.notificationTypes.isNotEmpty() -> requestNotificationPermission { viewModel.finish() }
                        else -> viewModel.finish()
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = MaxContentWidth).fillMaxSize()) {
                if (!state.isFirstStep) {
                    LinearProgressIndicator(
                        progress = { state.step.ordinal.toFloat() / OnboardingStep.entries.lastIndex },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                AnimatedContent(
                    targetState = state.step,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
                    },
                    label = "onboarding-step",
                ) { step ->
                    when (step) {
                        OnboardingStep.WELCOME -> WelcomeStep()
                        OnboardingStep.TEAMS -> StepScaffold(R.string.onboarding_teams_title, R.string.onboarding_teams_body) {
                            TeamPicker(state.teams, state.selectedTeamIds, viewModel::toggleTeam)
                        }
                        OnboardingStep.NOTIFICATIONS -> StepScaffold(
                            R.string.onboarding_notifications_title,
                            R.string.onboarding_notifications_body,
                        ) {
                            NotificationPicker(state.notificationTypes, viewModel::toggleNotification)
                        }
                        OnboardingStep.NEWS -> StepScaffold(R.string.onboarding_news_title, R.string.onboarding_news_body) {
                            NewsSourcePicker(state.newsSources, state.newsSourceIds, viewModel::toggleNewsSource)
                        }
                        OnboardingStep.SOCIAL -> StepScaffold(R.string.onboarding_social_title, R.string.onboarding_social_body) {
                            SocialSourcePicker(
                                sources = state.socialSources,
                                selectedIds = state.socialSourceIds,
                                onToggle = viewModel::toggleSocialSource,
                                teams = state.teams.orEmpty(),
                                favoriteTeamIds = state.selectedTeamIds,
                                onToggleTeam = viewModel::toggleTeamAccounts,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(160.dp),
        )
        Text(
            stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(12.dp))
        Text(
            stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepScaffold(title: Int, body: Int, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun OnboardingButtons(state: OnboardingUiState, onBack: () -> Unit, onNext: () -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!state.isFirstStep) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.onboarding_back)) }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onNext, enabled = state.canAdvance) {
                Text(
                    stringResource(
                        when {
                            state.isFirstStep -> R.string.onboarding_get_started
                            state.isLastStep -> R.string.onboarding_finish
                            else -> R.string.onboarding_next
                        },
                    ),
                )
            }
        }
    }
}
