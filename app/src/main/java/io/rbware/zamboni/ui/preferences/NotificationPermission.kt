package io.rbware.zamboni.ui.preferences

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns a function that asks for POST_NOTIFICATIONS on Android 13+ if it isn't granted yet,
 * then calls `onResult` (immediately on older versions or when already granted). The user's
 * notification choices are saved either way; delivery just won't happen without the permission.
 */
@Composable
fun rememberNotificationPermissionRequester(): (onResult: () -> Unit) -> Unit {
    val context = LocalContext.current
    // Callback to run once the permission dialog closes; remembered so it survives recomposition.
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending[0]?.invoke()
        pending[0] = null
    }
    return remember(launcher) {
        { onResult ->
            val needsRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsRequest) {
                pending[0] = onResult
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onResult()
            }
        }
    }
}
