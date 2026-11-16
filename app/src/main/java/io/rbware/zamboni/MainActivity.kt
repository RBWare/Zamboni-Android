package io.rbware.zamboni

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.rbware.zamboni.ui.ZamboniApp
import io.rbware.zamboni.ui.onboarding.OnboardingScreen
import io.rbware.zamboni.ui.theme.ZamboniTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until we know whether to show onboarding, avoiding a flash of the wrong UI.
        splash.setKeepOnScreenCondition { viewModel.userPreferences.value == null }
        enableEdgeToEdge()
        // On recreation the launch intent is delivered again; it was already handled.
        if (savedInstanceState == null) handleNotificationIntent(intent)
        setContent {
            val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
            ZamboniTheme(dynamicColor = preferences?.dynamicColor ?: true) {
                Crossfade(targetState = preferences?.onboardingComplete, label = "root") { onboarded ->
                    when (onboarded) {
                        null -> Unit
                        false -> OnboardingScreen()
                        true -> {
                            val openGameId by viewModel.openGameRequest.collectAsStateWithLifecycle()
                            ZamboniApp(
                                openGameId = openGameId,
                                onOpenGameHandled = viewModel::openGameHandled,
                                onOpenGame = viewModel::openGame,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as ZamboniApplication).onAppVisible()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    /** A tapped game notification carries the game to open. */
    private fun handleNotificationIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_GAME_ID)?.let(viewModel::openGame)
    }

    companion object {
        const val EXTRA_GAME_ID = "io.rbware.zamboni.extra.GAME_ID"
    }
}
