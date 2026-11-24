package com.aksdown.mobile.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aksdown.mobile.AksApp
import com.aksdown.mobile.R
import com.aksdown.mobile.data.DownloadItem
import com.aksdown.mobile.data.Format
import com.aksdown.mobile.data.Status
import com.aksdown.mobile.engine.UrlTools
import com.aksdown.mobile.service.Downloads
import java.text.DateFormat
import java.util.Date

private enum class Filter { ALL, MP4, MP3, FAILED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(vm: MainViewModel) {
    val items by vm.history.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var renaming by remember { mutableStateOf<DownloadItem?>(null) }
    var deleting by remember { mutableStateOf<DownloadItem?>(null) }
    val ctx = LocalContext.current
    val pendingDelete = remember { mutableStateOf<String?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) pendingDelete.value?.let { AksApp.instance.history.remove(it) }
        pendingDelete.value = null
    }

    val shown = items.filter {
        when (filter) {
            Filter.ALL -> true; Filter.MP4 -> it.format == Format.MP4; Filter.MP3 -> it.format == Format.MP3
            Filter.FAILED -> it.status == Status.FAILED || it.status == Status.CANCELED
        } && (q.isBlank() || listOfNotNull(it.title, it.fileName, it.url, UrlTools.platformByName(it.platform).label)
            .any { s -> s.contains(q.trim(), ignoreCase = true) })
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.downloads), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { FileActions.openFolder(ctx) }) {
                Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(18.dp), tint = Aks.Blue2)
                Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.open_folder), color = Aks.Blue2)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.search), color = Aks.Muted) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Aks.Muted) },
            trailingIcon = { if (q.isNotEmpty()) IconButton({ q = "" }) { Icon(Icons.Filled.Close, null, tint = Aks.Muted) } },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Aks.Blue2, unfocusedBorderColor = Aks.Line,
                focusedContainerColor = Aks.Card, unfocusedContainerColor = Aks.Card),
        )
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Filter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f, onClick = { filter = f },
                    label = { Text(when (f) { Filter.ALL -> stringResource(R.string.filter_all); Filter.MP4 -> "MP4"; Filter.MP3 -> "MP3"; Filter.FAILED -> stringResource(R.string.filter_failed) }) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Aks.Blue, selectedLabelColor = androidx.compose.ui.graphics.Color.White,
                        containerColor = Aks.Card, labelColor = Aks.Muted),
                )
            }
        }
        if (shown.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painterResource(R.drawable.ic_download), null, tint = Aks.Line, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.empty_body), color = Aks.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(shown, key = { it.id }) { item ->
                ItemRow(item,
                    onOpen = { FileActions.open(ctx, item) },
                    onShare = { FileActions.share(ctx, item) },
                    onRename = { renaming = item },
                    onDelete = { deleting = item },
                    onRetry = { vm.retry(item.id) },
                    onCancel = { Downloads.cancel(ctx, item.id) },
                )
            }
        }
    }

    renaming?.let { item ->
        var name by remember(item.id) { mutableStateOf(item.fileName?.substringBeforeLast('.') ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename_title)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton({ FileActions.rename(ctx, item, name); renaming = null }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton({ renaming = null }) { Text(stringResource(R.string.cancel)) } },
            containerColor = Aks.Card,
        )
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(if (item.status == Status.DONE) stringResource(R.string.delete_confirm) else (item.title ?: item.url)) },
            confirmButton = {
                TextButton({
                    if (item.status != Status.DONE) AksApp.instance.history.remove(item.id)
                    else FileActions.delete(ctx, item)?.let { sender ->
                        pendingDelete.value = item.id
                        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    deleting = null
                }) { Text(stringResource(R.string.delete), color = Aks.Bad) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.cancel)) } },
            containerColor = Aks.Card,
        )
    }
}

@Composable
private fun ItemRow(
    item: DownloadItem, onOpen: () -> Unit, onShare: () -> Unit, onRename: () -> Unit,
    onDelete: () -> Unit, onRetry: () -> Unit, onCancel: () -> Unit,
) {
    val p = UrlTools.platformByName(item.platform)
    var menu by remember { mutableStateOf(false) }
    val done = item.status == Status.DONE
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Aks.Card)
            .clickable(enabled = done, onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(item.thumbnail, p, item.format, UrlTools.formatDuration(item.durationSec), Modifier.width(104.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title ?: item.fileName ?: item.url, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(item.format.name)
                item.resolution?.takeIf { item.format == Format.MP4 }?.let { Tag(it) }
                Text(statusText(item), color = statusColor(item.status), fontSize = 12.sp, maxLines = 1)
            }
            val meta = listOfNotNull(
                if (p.name == "OTHER") null else p.label,
                UrlTools.formatBytes(item.sizeBytes).ifEmpty { null },
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.finishedAt ?: item.createdAt)),
            ).joinToString(" · ")
            Text(meta, color = Aks.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.status == Status.RUNNING) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(9.dp)),
                    color = Aks.Blue2, trackColor = Aks.Card2, drawStopIndicator = {}, gapSize = 0.dp)
            }
            if (item.status == Status.FAILED) Text(stringResource(errorRes(item.errorKey)), color = Aks.Bad, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when (item.status) {
            Status.FAILED, Status.CANCELED -> IconButton(onRetry) { Icon(Icons.Filled.Refresh, stringResource(R.string.retry), tint = Aks.Blue2) }
            Status.RUNNING, Status.QUEUED -> IconButton(onCancel) { Icon(Icons.Filled.Close, stringResource(R.string.cancel), tint = Aks.Muted) }
            else -> {}
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = Aks.Muted) }
            DropdownMenu(menu, { menu = false }, containerColor = Aks.Card2) {
                if (done) {
                    DropdownMenuItem({ Text(stringResource(R.string.play)) }, { menu = false; onOpen() }, leadingIcon = { Icon(Icons.Filled.PlayArrow, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.share)) }, { menu = false; onShare() }, leadingIcon = { Icon(Icons.Filled.Share, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.rename)) }, { menu = false; onRename() }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
                }
                if (item.status == Status.FAILED || item.status == Status.CANCELED)
                    DropdownMenuItem({ Text(stringResource(R.string.retry)) }, { menu = false; onRetry() }, leadingIcon = { Icon(Icons.Filled.Refresh, null) })
                if (item.status != Status.RUNNING)
                    DropdownMenuItem({ Text(stringResource(R.string.delete)) }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
            }
        }
    }
}

@Composable
private fun Tag(t: String) = Text(
    t, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFFCBD5E1),
    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Aks.Card2).padding(horizontal = 6.dp, vertical = 1.dp)
)

@Composable
private fun statusText(i: DownloadItem) = when (i.status) {
    Status.DONE -> stringResource(R.string.status_done)
    Status.FAILED -> stringResource(R.string.status_failed)
    Status.RUNNING -> if (i.progress > 0f && i.progress < 1f) "${(i.progress * 100).toInt()}%" else stageText(i)
    Status.QUEUED -> stringResource(R.string.status_queued)
    Status.CANCELED -> stringResource(R.string.status_canceled)
}

private fun statusColor(s: Status) = when (s) {
    Status.DONE -> Aks.Ok; Status.FAILED -> Aks.Bad; Status.CANCELED -> Aks.Muted; else -> Aks.Warn
}
