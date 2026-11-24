package com.aksdown.mobile.ui

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aksdown.mobile.AksApp
import com.aksdown.mobile.BuildConfig
import com.aksdown.mobile.R
import com.aksdown.mobile.data.Format
import com.aksdown.mobile.engine.Engine
import com.aksdown.mobile.engine.UrlTools

@Composable
fun SettingsScreen(vm: MainViewModel, onLanguage: (String) -> Unit) {
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    val app = AksApp.instance
    var toast by remember { mutableStateOf<String?>(null) }
    var licenses by remember { mutableStateOf(false) }
    var storage by remember { mutableStateOf(storageText(ctx)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
        AksCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Text(stringResource(R.string.set_default_format), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Aks.Bg).padding(3.dp)) {
                    Format.entries.forEach { f ->
                        val on = s.defaultFormat == f
                        Text(f.name, fontWeight = FontWeight.ExtraBold, color = if (on) Color.White else Aks.Muted,
                            modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(if (on) Aks.Blue else Color.Transparent)
                                .clickable { vm.setDefaultFormat(f) }.padding(horizontal = 16.dp, vertical = 9.dp))
                    }
                }
            }
            Line()
            SwitchRow(stringResource(R.string.set_auto), stringResource(R.string.set_auto_desc), s.autoDownload) { v -> app.settings.edit { copy(autoDownload = v) } }
            Line()
            SwitchRow(stringResource(R.string.set_wifi), null, s.wifiOnly) { v -> app.settings.edit { copy(wifiOnly = v) } }
            Line()
            SwitchRow(stringResource(R.string.set_notif), stringResource(R.string.set_notif_desc), s.notifications) { v -> app.settings.edit { copy(notifications = v) } }
            Line()
            Text(stringResource(R.string.set_language), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp).horizontalScroll(rememberScrollState())) {
                listOf("" to stringResource(R.string.lang_system), "ar" to "العربية", "en" to "English", "he" to "עברית").forEach { (tag, label) ->
                    FilterChip(selected = s.language == tag, onClick = { onLanguage(tag) }, label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Aks.Blue, selectedLabelColor = Color.White,
                            containerColor = Aks.Bg, labelColor = Aks.Muted))
                }
            }
        }
        AksCard {
            ActionRow(stringResource(R.string.set_history), stringResource(R.string.set_history_desc)) {
                app.history.clearFinished(); toast = ctx.getString(R.string.cleared)
            }
            Line()
            ActionRow(stringResource(R.string.set_temp), null) {
                app.workRoot.listFiles()?.forEach { f ->
                    val busy = app.history.get(f.name)?.status.let { it == com.aksdown.mobile.data.Status.RUNNING || it == com.aksdown.mobile.data.Status.QUEUED }
                    if (!busy) f.deleteRecursively()
                }
                storage = storageText(ctx); toast = ctx.getString(R.string.cleared)
            }
            Line()
            InfoRow(stringResource(R.string.set_storage), storage)
            Line()
            InfoRow(stringResource(R.string.set_version), BuildConfig.VERSION_NAME)
            Line()
            InfoRow(stringResource(R.string.set_engine), Engine.engineVersion())
        }
        AksCard {
            Text(stringResource(R.string.set_about), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.about_body), color = Aks.Muted, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { licenses = true }) { Text(stringResource(R.string.set_licenses), color = Aks.Blue2) }
        }
        toast?.let { Text(it, color = Aks.Ok, style = MaterialTheme.typography.bodyMedium) }
    }

    if (licenses) {
        val text = remember { runCatching { ctx.resources.openRawResource(R.raw.licenses).bufferedReader().readText() }.getOrDefault("") }
        AlertDialog(
            onDismissRequest = { licenses = false }, containerColor = Aks.Card,
            title = { Text(stringResource(R.string.set_licenses)) },
            text = { Text(text, fontSize = 12.sp, color = Aks.Muted, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton({ licenses = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

private fun storageText(ctx: android.content.Context): String = runCatching {
    val st = StatFs(Environment.getExternalStorageDirectory().absolutePath)
    ctx.getString(R.string.storage_free, UrlTools.formatBytes(st.availableBytes), UrlTools.formatBytes(st.totalBytes))
}.getOrDefault("")

@Composable private fun Line() = HorizontalDivider(color = Aks.Line)

@Composable
private fun SwitchRow(title: String, desc: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            desc?.let { Text(it, color = Aks.Muted, style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.width(10.dp))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Aks.Blue, uncheckedTrackColor = Aks.Card2, uncheckedBorderColor = Aks.Line))
    }
}

@Composable
private fun ActionRow(title: String, desc: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Aks.Blue2)
        desc?.let { Text(it, color = Aks.Muted, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, color = Aks.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}
