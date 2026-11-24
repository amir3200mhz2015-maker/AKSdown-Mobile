package com.aksdown.mobile.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aksdown.mobile.R
import com.aksdown.mobile.data.DownloadItem
import com.aksdown.mobile.data.Format
import com.aksdown.mobile.data.Stage
import com.aksdown.mobile.data.Status
import com.aksdown.mobile.engine.Platform
import com.aksdown.mobile.engine.UrlTools

@Composable
fun HomeScreen(vm: MainViewModel, onDownload: () -> Unit) {
    val s by vm.home.collectAsState()
    val items by vm.history.collectAsState()
    val current = s.currentId?.let { id -> items.firstOrNull { it.id == id } }
    val ctx = LocalContext.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(50.dp))
            Column {
                Text("AKSdown", style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.subtitle), color = Aks.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }

        AksCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = s.input, onValueChange = vm::onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.url_hint), color = Aks.Muted) },
                    singleLine = true,
                    // Links are always left-to-right, even in Arabic/Hebrew layouts.
                    textStyle = TextStyle(fontSize = 16.sp, textDirection = TextDirection.Ltr, color = Aks.Ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (s.input.isNotEmpty()) IconButton(onClick = vm::clear) {
                            Icon(Icons.Filled.Close, stringResource(R.string.clear), tint = Aks.Muted)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Aks.Blue2, unfocusedBorderColor = Aks.Line,
                        focusedContainerColor = Aks.Bg, unfocusedContainerColor = Aks.Bg, cursorColor = Aks.Blue2,
                    ),
                )
                FilledTonalButton(
                    onClick = {
                        // Clipboard is read ONLY here, when the user presses PASTE.
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                        if (text.isNullOrBlank()) vm.onClipboardEmpty() else vm.onInput(text)
                    },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Aks.Card2, contentColor = Aks.Ink),
                ) {
                    Icon(painterResource(R.drawable.ic_paste), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.paste))
                }
            }
            s.inputErrorKey?.let {
                Text(stringResource(errorRes(it)), color = Aks.Bad, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            AnimatedVisibility(s.url != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Preview(s, onRetry = vm::retryInfo)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormatButton("MP4", stringResource(R.string.video), R.drawable.ic_video, s.format == Format.MP4, Modifier.weight(1f)) { vm.setFormat(Format.MP4) }
            FormatButton("MP3", stringResource(R.string.audio), R.drawable.ic_music, s.format == Format.MP3, Modifier.weight(1f)) { vm.setFormat(Format.MP3) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Row(
                Modifier.clip(RoundedCornerShape(99.dp)).background(Aks.Blue.copy(alpha = .13f))
                    .border(1.dp, Aks.Blue.copy(alpha = .35f), RoundedCornerShape(99.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Star, null, tint = Color(0xFF93B4FF), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (s.format == Format.MP4) R.string.best_quality_mp4 else R.string.best_quality_mp3),
                    color = Color(0xFFBCD0FF), style = MaterialTheme.typography.labelMedium)
            }
        }

        when (current?.status) {
            Status.QUEUED, Status.RUNNING -> ProgressPanel(current, onCancel = vm::cancelCurrent)
            Status.DONE -> DonePanel(current, onAgain = vm::clear)
            Status.FAILED -> FailedPanel(current, onRetry = { vm.retry(current.id) })
            else -> DownloadButton(enabled = s.url != null, onClick = onDownload)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun AksCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Aks.Card)
            .border(1.dp, Aks.Line, RoundedCornerShape(22.dp)).padding(16.dp),
        content = content
    )
}

@Composable
private fun Preview(s: HomeState, onRetry: () -> Unit) {
    val p = s.platform ?: Platform.OTHER
    Column {
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Aks.Line)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Thumb(s.meta?.thumbnail, p, s.format, UrlTools.formatDuration(s.meta?.durationSec), Modifier.width(128.dp))
            Column(Modifier.weight(1f)) {
                PlatformBadge(p)
                Spacer(Modifier.height(6.dp))
                when {
                    s.loadingInfo -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Aks.Blue2)
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.fetching), color = Aks.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    s.meta != null -> {
                        Text(s.meta.title ?: s.url ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(s.meta.uploader, s.meta.maxHeight?.let { "${it}p" }.takeIf { s.format == Format.MP4 }).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, color = Aks.Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    s.infoErrorKey != null -> {
                        Text(stringResource(errorRes(s.infoErrorKey)), color = Aks.Bad, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.retry), color = Aks.Blue2, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 4.dp).clickable(onClick = onRetry))
                    }
                }
            }
        }
    }
}

@Composable
fun PlatformBadge(p: Platform) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(p.tint), contentAlignment = Alignment.Center) {
            Text(p.short, color = if (p == Platform.TIKTOK) Color.Black else Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        Spacer(Modifier.width(6.dp))
        Text(if (p == Platform.OTHER) stringResource(R.string.unknown_site) else p.label, color = Aks.Muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Thumb(url: String?, p: Platform, f: Format, duration: String, modifier: Modifier) {
    Box(
        modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(p.tint, Aks.Card2))),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(if (f == Format.MP4) R.drawable.ic_video else R.drawable.ic_music), null, tint = Color.White, modifier = Modifier.size(26.dp))
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (duration.isNotEmpty()) Text(
            duration, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(6.dp))
                .background(Color(0xAA000000)).padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun FormatButton(title: String, sub: String, icon: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val border by animateFloatAsState(if (selected) 1f else 0f, label = "fmt")
    Column(
        modifier.height(92.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected) Aks.Blue.copy(alpha = .14f) else Aks.Card)
            .border(2.dp, if (border > .5f) Aks.Blue else Aks.Line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(painterResource(icon), null, tint = if (selected) Aks.Blue2 else Aks.Muted, modifier = Modifier.size(22.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(sub, color = if (selected) Color(0xFFBCD0FF) else Aks.Muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DownloadButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Aks.Blue, disabledContainerColor = Aks.Blue.copy(alpha = .35f), disabledContentColor = Color.White.copy(alpha = .6f)),
    ) {
        Icon(painterResource(R.drawable.ic_download), null, Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.download), fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun stageText(item: DownloadItem): String = when {
    item.status == Status.QUEUED -> stringResource(R.string.queued)
    item.stage == Stage.INIT -> stringResource(R.string.preparing)
    item.stage == Stage.INFO -> stringResource(R.string.fetching)
    item.stage == Stage.PROCESSING -> stringResource(R.string.processing)
    item.stage == Stage.SAVING -> stringResource(R.string.saving)
    else -> stringResource(R.string.downloading)
}

@Composable
private fun ProgressPanel(item: DownloadItem, onCancel: () -> Unit) {
    val anim by animateFloatAsState(item.progress, label = "p")
    AksCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stageText(item), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (item.stage == Stage.DOWNLOADING) Text("${(item.progress * 100).toInt()}%", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Aks.Blue2)
        }
        Spacer(Modifier.height(10.dp))
        if (item.stage == Stage.DOWNLOADING) LinearProgressIndicator(
            progress = { anim }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)),
            color = Aks.Blue2, trackColor = Aks.Card2, drawStopIndicator = {}, gapSize = 0.dp,
        ) else LinearProgressIndicator(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)), color = Aks.Blue2, trackColor = Aks.Card2)
        val speed = item.speed
        if (item.stage == Stage.DOWNLOADING && speed != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (item.etaSec > 0) stringResource(R.string.speed_eta, speed, UrlTools.formatEta(item.etaSec)) else speed,
                color = Aks.Muted, style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Filled.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun DonePanel(item: DownloadItem, onAgain: () -> Unit) {
    var shown by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) { shown = true }
    val sc by animateFloatAsState(if (shown) 1f else 0.3f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "ok")
    val ctx = LocalContext.current
    AksCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).scale(sc).clip(CircleShape).background(Aks.Ok.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Aks.Ok), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.done), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                listOfNotNull(item.fileName, item.resolution, UrlTools.formatBytes(item.sizeBytes).ifEmpty { null }).joinToString(" · "),
                color = Aks.Muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { FileActions.open(ctx, item) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Aks.Blue)) { Text(stringResource(R.string.play)) }
                OutlinedButton(onClick = { FileActions.share(ctx, item) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(stringResource(R.string.share))
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onAgain) { Text(stringResource(R.string.download), color = Aks.Blue2) }
        }
    }
}

@Composable
private fun FailedPanel(item: DownloadItem, onRetry: () -> Unit) {
    var details by remember { mutableStateOf(false) }
    AksCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, null, tint = Aks.Bad, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.failed), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(errorRes(item.errorKey)), color = Aks.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!item.errorDetail.isNullOrBlank()) {
            TextButton(onClick = { details = !details }) { Text(stringResource(R.string.details), color = Aks.Muted) }
            if (details) Text(item.errorDetail.takeLast(800), color = Aks.Muted, fontSize = 11.sp, style = TextStyle(textDirection = TextDirection.Ltr))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Aks.Blue)) {
            Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.retry))
        }
    }
}
