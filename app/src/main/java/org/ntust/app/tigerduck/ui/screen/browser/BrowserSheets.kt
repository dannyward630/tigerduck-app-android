package org.ntust.app.tigerduck.ui.screen.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.ntust.app.tigerduck.R
import org.ntust.app.tigerduck.browser.BrowserAppearance
import org.ntust.app.tigerduck.browser.BrowserDownload
import org.ntust.app.tigerduck.browser.BrowserZoom
import org.ntust.app.tigerduck.browser.CertificateInfo
import org.ntust.app.tigerduck.browser.PageSchemeFollow
import org.ntust.app.tigerduck.browser.UploadSource
import org.ntust.app.tigerduck.util.replaceIosArg
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserSheetFrame(onDismiss: () -> Unit, skipPartial: Boolean = true, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartial),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            content()
        }
    }
}

/** The page's host and title at the top of a sheet that acts on it. */
@Composable
private fun PageHeader(state: BrowserPageState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(36.dp)) {
            Icon(
                if (state.isHttps) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = null,
                modifier = Modifier.padding(9.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(state.host.orEmpty(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            state.title?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(13.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, enabled: Boolean = true, showArrow: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (showArrow) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The ⋮ menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserMenuSheet(
    state: BrowserPageState,
    appearance: BrowserAppearance,
    schemeFollow: PageSchemeFollow,
    actions: BrowserActions,
    onAddress: () -> Unit,
    onCertificate: () -> Unit,
    onDownloads: () -> Unit,
    onDismiss: () -> Unit,
) {
    val url = state.url
    fun act(block: () -> Unit) {
        onDismiss()
        block()
    }
    BrowserSheetFrame(onDismiss) {
        PageHeader(state)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            QuickAction(Icons.Filled.ContentCopy, stringResource(R.string.browser_copy), enabled = url != null, onClick = { act { url?.let(actions::copy) } }, modifier = Modifier.weight(1f))
            QuickAction(Icons.Filled.Share, stringResource(R.string.browser_share), enabled = url != null, onClick = { act { url?.let(actions::share) } }, modifier = Modifier.weight(1f))
            QuickAction(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.browser_open_external_short), enabled = url != null, onClick = { act { url?.let(actions::openExternal) } }, modifier = Modifier.weight(1f))
            QuickAction(Icons.Filled.Refresh, stringResource(R.string.browser_reload), onClick = { act(actions::reload) }, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

        // Zoom: − and + only; the percentage puts it back to 100%.
        val locale = LocalConfiguration.current.locales[0]
        val percent = remember(state.zoom, locale) { NumberFormat.getPercentInstance(locale).format(state.zoom / 100.0) }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ZoomIn, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.browser_zoom), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            OutlinedIconButton(onClick = actions::zoomOut, enabled = BrowserZoom.canZoomOut(state.zoom)) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.browser_zoom_out))
            }
            TextButton(onClick = actions::zoomReset) {
                Text(percent, modifier = Modifier.width(48.dp), maxLines = 1)
            }
            OutlinedIconButton(onClick = actions::zoomIn, enabled = BrowserZoom.canZoomIn(state.zoom)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.browser_zoom_in))
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Switch) { actions.setDesktopSite(!state.desktopSite) }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.DesktopWindows, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.browser_desktop_site), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = state.desktopSite, onCheckedChange = null)
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DarkMode, contentDescription = null)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.browser_appearance), style = MaterialTheme.typography.bodyLarge)
            }
            val options = listOf(
                BrowserAppearance.AUTO to stringResource(R.string.common_automatic),
                BrowserAppearance.LIGHT to stringResource(R.string.browser_appearance_light),
                BrowserAppearance.DARK to stringResource(R.string.browser_appearance_dark),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 40.dp)) {
                options.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = appearance == value,
                        onClick = { actions.setAppearance(value) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(label, maxLines = 1) }
                }
            }
            Text(
                stringResource(
                    if (schemeFollow == PageSchemeFollow.LIVE) R.string.browser_appearance_note else R.string.browser_appearance_note_next_load,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp),
            )
        }

        MenuRow(Icons.Filled.Search, stringResource(R.string.browser_find), enabled = state.error == null && state.signIn == null) { act(actions::startFind) }
        MenuRow(Icons.Filled.Print, stringResource(R.string.browser_print), enabled = url != null && state.error == null && state.signIn == null) { act(actions::print) }
        MenuRow(Icons.Filled.Download, stringResource(R.string.browser_downloads), showArrow = true, onClick = onDownloads)
        MenuRow(Icons.Filled.VerifiedUser, stringResource(R.string.browser_security), enabled = url != null, showArrow = true, onClick = onAddress)
    }
}

/** The whole address of the page, its connection and its sign-in. */
@Composable
internal fun BrowserAddressSheet(
    state: BrowserPageState,
    url: String,
    actions: BrowserActions,
    onCertificate: () -> Unit,
    onDismiss: () -> Unit,
) {
    BrowserSheetFrame(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.browser_address_title), style = MaterialTheme.typography.titleLarge)
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                SelectionContainer {
                    Text(url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { actions.copy(url) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.browser_copy_short), maxLines = 1) }
                OutlinedButton(onClick = { actions.share(url) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.browser_share_short), maxLines = 1) }
                OutlinedButton(onClick = { onDismiss(); actions.openExternal(url) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.browser_open_external_short), maxLines = 1) }
            }
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column {
                    InfoRow(
                        icon = if (state.isHttps) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        title = stringResource(if (state.isHttps) R.string.browser_connection_secure else R.string.browser_connection_insecure),
                        detail = if (state.isHttps) stringResource(R.string.browser_connection_secure_detail) else null,
                    )
                    if (state.signedIn) {
                        HorizontalDivider()
                        InfoRow(icon = Icons.Filled.Person, title = stringResource(R.string.browser_signed_in), detail = null)
                    }
                    HorizontalDivider()
                    MenuRow(Icons.Filled.VerifiedUser, stringResource(R.string.browser_certificate), enabled = state.certificate != null, showArrow = true, onClick = onCertificate)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, detail: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** The page certificate's details, and a copy of them as text. */
@Composable
internal fun BrowserCertificateSheet(state: BrowserPageState, actions: BrowserActions, onDismiss: () -> Unit) {
    val info = state.certificate
    val locale = LocalConfiguration.current.locales[0]
    val dates = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }
    fun date(d: Date?) = d?.let(dates::format) ?: "—"
    val issuedTo = stringResource(R.string.browser_certificate_issued_to)
    val issuedBy = stringResource(R.string.browser_certificate_issued_by)
    val commonName = stringResource(R.string.browser_certificate_common_name)
    val organization = stringResource(R.string.browser_certificate_organization)
    val validity = stringResource(R.string.browser_certificate_validity)
    val notBefore = stringResource(R.string.browser_certificate_not_before)
    val notAfter = stringResource(R.string.browser_certificate_not_after)
    val fingerprint = stringResource(R.string.browser_certificate_fingerprint)
    BrowserSheetFrame(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.browser_certificate_title), style = MaterialTheme.typography.titleLarge)
            if (info == null) {
                Text(stringResource(R.string.browser_certificate_none), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            InfoRow(Icons.Filled.VerifiedUser, state.host.orEmpty(), stringResource(R.string.browser_certificate_valid))
            CertificateGroup(issuedTo, listOf(commonName to info.issuedToName, organization to info.issuedToOrganization))
            CertificateGroup(issuedBy, listOf(commonName to info.issuedByName, organization to info.issuedByOrganization))
            CertificateGroup(validity, listOf(notBefore to date(info.validFrom), notAfter to date(info.validUntil)))
            info.sha256?.let { CertificateGroup(fingerprint, listOf("SHA-256" to it), monospace = true) }
            OutlinedButton(
                onClick = {
                    actions.copyCertificate(
                        certificateText(info, state.host, issuedTo, issuedBy, commonName, organization, validity, notBefore, notAfter, fingerprint, ::date),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.browser_certificate_copy)) }
        }
    }
}

/** The certificate as lines of text, under the same labels as the page shows. */
internal fun certificateText(
    info: CertificateInfo,
    host: String?,
    issuedTo: String,
    issuedBy: String,
    commonName: String,
    organization: String,
    validity: String,
    notBefore: String,
    notAfter: String,
    fingerprint: String,
    date: (Date?) -> String,
): String = buildString {
    host?.let { appendLine(it) }
    appendLine(issuedTo)
    appendLine("  $commonName: ${info.issuedToName ?: "—"}")
    appendLine("  $organization: ${info.issuedToOrganization ?: "—"}")
    appendLine(issuedBy)
    appendLine("  $commonName: ${info.issuedByName ?: "—"}")
    appendLine("  $organization: ${info.issuedByOrganization ?: "—"}")
    appendLine(validity)
    appendLine("  $notBefore: ${date(info.validFrom)}")
    appendLine("  $notAfter: ${date(info.validUntil)}")
    info.sha256?.let {
        appendLine(fingerprint)
        append("  SHA-256: $it")
    }
}.trimEnd()

@Composable
private fun CertificateGroup(title: String, rows: List<Pair<String, String?>>, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column {
                rows.forEachIndexed { i, (label, value) ->
                    if (i > 0) HorizontalDivider()
                    if (monospace) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SelectionContainer { Text(value ?: "—", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SelectionContainer(Modifier.weight(1f)) {
                                Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

/** This run's downloads. */
@Composable
internal fun BrowserDownloadsSheet(downloads: List<BrowserDownload>, actions: BrowserActions, onDismiss: () -> Unit) {
    BrowserSheetFrame(onDismiss) {
        Text(stringResource(R.string.browser_downloads), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        if (downloads.isEmpty()) {
            Text(stringResource(R.string.browser_downloads_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
        downloads.forEach { DownloadRow(it, actions) }
    }
}

@Composable
private fun DownloadRow(download: BrowserDownload, actions: BrowserActions) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.InsertDriveFile, contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(download.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (download.state == BrowserDownload.State.RUNNING) {
                val total = download.total
                if (total != null && total > 0) {
                    LinearProgressIndicator(progress = { (download.bytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            Text(downloadStatus(download), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (download.state) {
            BrowserDownload.State.RUNNING -> IconButton(onClick = { actions.cancelDownload(download.id) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.browser_download_cancel))
            }
            BrowserDownload.State.DONE -> TextButton(onClick = { actions.openDownload(download) }) { Text(stringResource(R.string.browser_download_open)) }
            else -> Unit
        }
    }
}

/** A download's state line: progress while it runs, its size once done. */
@Composable
internal fun downloadStatus(download: BrowserDownload): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun size(bytes: Long) = android.text.format.Formatter.formatShortFileSize(context, bytes)
    return when (download.state) {
        BrowserDownload.State.RUNNING -> {
            val total = download.total
            val amount = if (total != null && total > 0) "${size(download.bytes)} / ${size(total)}" else size(download.bytes)
            stringResource(R.string.browser_download_progress).replaceIosArg(1, amount)
        }
        BrowserDownload.State.DONE -> stringResource(R.string.browser_download_done).replaceIosArg(1, size(download.bytes))
        BrowserDownload.State.FAILED -> stringResource(R.string.browser_download_failed)
        BrowserDownload.State.CANCELLED -> stringResource(R.string.browser_download_canceled)
    }
}

/** Where a file for a page's upload field comes from. Dismissing it answers the page with nothing. */
@Composable
internal fun BrowserUploadSheet(request: UploadRequest, actions: BrowserActions) {
    BrowserSheetFrame(onDismiss = actions::cancelUpload) {
        Text(stringResource(R.string.browser_upload_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        if (request.imagesOnly && request.host != null) {
            Text(
                stringResource(R.string.browser_upload_wants_image).replaceIosArg(1, request.host),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        request.sources.forEach { source ->
            val (icon, label) = when (source) {
                UploadSource.PHOTOS -> Icons.Filled.Image to R.string.browser_upload_photos
                UploadSource.CAMERA -> Icons.Filled.CameraAlt to R.string.browser_upload_camera
                UploadSource.FILES -> Icons.Filled.Folder to R.string.browser_upload_files
            }
            MenuRow(icon, stringResource(label)) { actions.pickUpload(source) }
        }
        OutlinedButton(onClick = actions::cancelUpload, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

/** A long-pressed link's menu. */
@Composable
internal fun BrowserLinkSheet(url: String, actions: BrowserActions) {
    fun act(block: () -> Unit) {
        actions.closeLinkMenu()
        block()
    }
    BrowserSheetFrame(onDismiss = actions::closeLinkMenu) {
        Text(
            url,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        MenuRow(Icons.Filled.Search, stringResource(R.string.browser_link_open)) { act { actions.open(url) } }
        MenuRow(Icons.Filled.ContentCopy, stringResource(R.string.browser_copy_link)) { act { actions.copy(url) } }
        MenuRow(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.browser_open_external)) { act { actions.openExternal(url) } }
        MenuRow(Icons.Filled.Share, stringResource(R.string.browser_share_link)) { act { actions.share(url) } }
    }
}

/**
 * A link off the allowlist: open it in the phone's browser, copy it, or stay. Its buttons in two
 * rows: Cancel alone on the right, then Copy link on the left and the phone's browser on the right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserOutsideDialog(url: String, actions: BrowserActions) {
    BasicAlertDialog(onDismissRequest = actions::stayHere) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(Modifier.padding(24.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = AlertDialogDefaults.iconContentColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.browser_outside_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.browser_outside_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlertDialogDefaults.textContentColor,
                )
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(10.dp))
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = actions::stayHere) { Text(stringResource(R.string.action_cancel)) }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { actions.copy(url); actions.stayHere() }) { Text(stringResource(R.string.browser_copy_link)) }
                    Button(onClick = { actions.leaveFor(url) }) { Text(stringResource(R.string.browser_open_external)) }
                }
            }
        }
    }
}

/** A page's alert, confirm or prompt, as the app's dialog, naming the site that shows it. */
@Composable
internal fun BrowserPageDialog(dialog: PageDialog, actions: BrowserActions) {
    var value by remember(dialog) { mutableStateOf(dialog.defaultValue.orEmpty()) }
    val cancellable = dialog.kind != PageDialog.Kind.ALERT
    AlertDialog(
        onDismissRequest = { actions.answerDialog(confirmed = false, value = null) },
        title = { Text(stringResource(R.string.browser_dialog_title).replaceIosArg(1, dialog.host.orEmpty())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (dialog.message.isNotEmpty()) Text(dialog.message)
                if (dialog.kind == PageDialog.Kind.PROMPT) {
                    OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.answerDialog(confirmed = true, value = value.takeIf { dialog.kind == PageDialog.Kind.PROMPT }) }) {
                Text(stringResource(R.string.browser_dialog_ok))
            }
        },
        dismissButton = if (cancellable) {
            { TextButton(onClick = { actions.answerDialog(confirmed = false, value = null) }) { Text(stringResource(R.string.action_cancel)) } }
        } else {
            null
        },
    )
}

/** Cards over the page for the downloads running now and the last one done. */
@Composable
internal fun DownloadCards(downloads: List<BrowserDownload>, actions: BrowserActions, modifier: Modifier = Modifier) {
    var dismissed by remember { mutableStateOf(setOf<Long>()) }
    val shown = downloads.filter { it.id !in dismissed }.let { list ->
        list.filter { it.state == BrowserDownload.State.RUNNING } +
            list.filter { it.state != BrowserDownload.State.RUNNING }.take(1)
    }.take(3)
    if (shown.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { download ->
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (download.state == BrowserDownload.State.RUNNING) Icons.Filled.Download else Icons.Filled.InsertDriveFile, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(download.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(downloadStatus(download), style = MaterialTheme.typography.bodySmall)
                    }
                    when (download.state) {
                        BrowserDownload.State.RUNNING -> IconButton(onClick = { actions.cancelDownload(download.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.browser_download_cancel))
                        }
                        BrowserDownload.State.DONE -> {
                            TextButton(onClick = { actions.openDownload(download) }) {
                                Text(stringResource(R.string.browser_download_open), color = MaterialTheme.colorScheme.inversePrimary)
                            }
                            IconButton(onClick = { dismissed = dismissed + download.id }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                            }
                        }
                        else -> IconButton(onClick = { dismissed = dismissed + download.id }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    }
                }
            }
        }
    }
}
