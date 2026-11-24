package com.aksdown.mobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.aksdown.mobile.R

@Composable
fun AksRoot(vm: MainViewModel, onLanguage: (String) -> Unit) = AksTheme {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val home by vm.home.collectAsState()
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }

    // Permissions are only asked when they are actually needed (first download).
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val storagePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startDownload()
    }
    val startDownload: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 && vm.settings.value.notifications) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT < 29 && !hasLegacyStorage(ctx)) storagePerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else vm.startDownload()
    }

    LaunchedEffect(home.toastKey) {
        home.toastKey?.let { snack.showSnackbar(ctx.getString(errorRes(it))); vm.toastShown() }
    }
    LaunchedEffect(home.currentId) { if (home.currentId != null) tab = 0 }

    Scaffold(
        containerColor = Aks.Bg,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar(containerColor = Aks.Card) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Aks.Blue2, selectedTextColor = Aks.Ink, indicatorColor = Aks.Blue.copy(alpha = .18f),
                    unselectedIconColor = Aks.Muted, unselectedTextColor = Aks.Muted,
                )
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Home, null) }, label = { Text(stringResource(R.string.home)) }, colors = colors)
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(painterResource(R.drawable.ic_download), null) }, label = { Text(stringResource(R.string.downloads)) }, colors = colors)
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Filled.Settings, null) }, label = { Text(stringResource(R.string.settings)) }, colors = colors)
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> HomeScreen(vm, onDownload = startDownload)
                1 -> DownloadsScreen(vm)
                else -> SettingsScreen(vm, onLanguage)
            }
        }
    }
}

private fun hasLegacyStorage(ctx: android.content.Context) =
    androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

/** Maps an error/message key to its translated string. */
fun errorRes(key: String?): Int = when (key) {
    "err_invalid_url" -> R.string.err_invalid_url
    "err_clipboard_empty" -> R.string.err_clipboard_empty
    "err_unsupported" -> R.string.err_unsupported
    "err_unavailable" -> R.string.err_unavailable
    "err_private" -> R.string.err_private
    "err_drm" -> R.string.err_drm
    "err_geo" -> R.string.err_geo
    "err_bot" -> R.string.err_bot
    "err_network" -> R.string.err_network
    "err_wifi" -> R.string.err_wifi
    "err_storage" -> R.string.err_storage
    "err_ffmpeg" -> R.string.err_ffmpeg
    "err_invalid_file" -> R.string.err_invalid_file
    "err_playlist" -> R.string.err_playlist
    "err_live" -> R.string.err_live
    "err_init" -> R.string.err_init
    "err_save" -> R.string.err_save
    "duplicate" -> R.string.duplicate
    "cleared" -> R.string.cleared
    else -> R.string.err_engine
}
