package io.rbware.zamboni

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
        setContent {
            ZamboniTheme {
                val preferences by viewModel.userPreferences.collectAsStateWithLifecycle()
                Crossfade(targetState = preferences?.onboardingComplete, label = "root") { onboarded ->
                    when (onboarded) {
                        null -> Unit
                        false -> OnboardingScreen()
                        true -> ZamboniApp()
                    }
                }
            }
        }
    }
}
