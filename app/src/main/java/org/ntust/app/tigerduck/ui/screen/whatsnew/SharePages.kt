package org.ntust.app.tigerduck.ui.screen.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ntust.app.tigerduck.R

/**
 * "Share your class table": the share sheet's QR code, scanned on another
 * phone in TigerDuck, which imports it, or in any QR scanner, which opens it
 * on the web.
 */
internal fun classTableSharePage() = WhatsNewPage.Feature(
    id = "class-table-share",
    visual = WhatsNewVisual.Custom { animate -> ShareDemo(animate) },
    title = WhatsNewText(
        en = "Share your class table",
        zhHant = "分享你的課表",
    ),
    body = WhatsNewText(
        en = "Choose Share class table under More actions on the class table to show a QR code. Scanning it in TigerDuck imports it, and any QR scanner opens it on the web.",
        zhHant = "在課表的「更多操作」選「分享課表」就會出現 QR code。對方用 TigerDuck 掃描就能匯入，用任何 QR code 掃描器掃也能打開網頁版。",
    ),
)

private val inTigerDuckCaption = WhatsNewText(en = "Scanned in TigerDuck", zhHant = "用 TigerDuck 掃描")
private val anyScannerCaption = WhatsNewText(en = "Any QR scanner", zhHant = "用任何 QR code 掃描器")
private val openInTigerDuck = WhatsNewText(en = "Open in TigerDuck", zhHant = "在 TigerDuck 開啟")
private val shareDemoDescription = WhatsNewText(
    en = "Demo: one phone shows its class table's QR code; another scans it, in TigerDuck to import it, or in any QR scanner to open it on the web.",
    zhHant = "示範：一支手機顯示課表 QR code，另一支掃描它：用 TigerDuck 掃描可直接匯入，用任何 QR code 掃描器則會打開網頁版。",
)

private const val SHARE_LOOP_MILLIS = 10_000
private val PhoneWidth = 132.dp
private val PhoneHeight = 220.dp

@Composable
private fun ShareDemo(animate: Boolean) {
    // Still: the import, scanned in TigerDuck.
    val clock = demoClock(animate, SHARE_LOOP_MILLIS, still = 0.5f)
    val t = { clock.value }
    // The second phone: scanning, the import; scanning again, the web page.
    val scanning = { window(t(), 0.0f, 0.36f, fade = 0.03f) + window(t(), 0.58f, 0.70f, fade = 0.03f) }
    val imported = { window(t(), 0.34f, 0.60f, fade = 0.03f) }
    val web = { window(t(), 0.68f, 0.99f, fade = 0.03f) }
    Column(Modifier.demoDescription(shareDemoDescription.resolve()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MockScreen(width = PhoneWidth, height = PhoneHeight) { ShareSheet() }
            MockScreen(width = PhoneWidth, height = PhoneHeight) {
                Scanner(Modifier.graphicsLayer { alpha = scanning().coerceAtMost(1f) }, sweep = { (t() * 5f) % 1f })
                ImportConfirm(Modifier.graphicsLayer { alpha = imported() })
                WebPage(Modifier.graphicsLayer { alpha = web() })
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            DemoCaption(inTigerDuckCaption.resolve(), alpha = { window(t(), 0.0f, 0.60f, fade = 0.03f) })
            DemoCaption(anyScannerCaption.resolve(), alpha = { window(t(), 0.60f, 0.99f, fade = 0.03f) })
        }
    }
}

/** The share sheet: scanner, title and link along the top, the QR code under them. */
@Composable
private fun ShareSheet() {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            MockText(stringResource(R.string.class_table_share), Modifier.weight(1f), size = 11.sp, weight = FontWeight.Bold)
            Icon(Icons.Filled.Link, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.height(4.dp))
        MockQr(96.dp, Modifier.border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp)))
        TextLine(0.6f, height = 5.dp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MockText(stringResource(R.string.class_table_semester_picker_label), Modifier.weight(1f), size = 9.sp)
            MockText("114-1", size = 9.sp, color = colors.primary)
        }
    }
}

/** A camera finding the code: dark, a frame, the code inside it and a line sweeping by [sweep]. */
@Composable
private fun Scanner(modifier: Modifier, sweep: () -> Float) {
    Box(modifier.fillMaxSize().background(Color(0xFF15171A)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(96.dp).border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            MockQr(64.dp, Modifier.graphicsLayer { alpha = 0.85f })
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.TopCenter)
                    .graphicsLayer { translationY = sweep() * 92.dp.toPx() }
                    .background(Color(0xFF7BE495)),
            )
        }
    }
}

/** TigerDuck's import: what the code holds, and Import. */
@Composable
private fun ImportConfirm(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(colors.scrim.copy(alpha = 0.35f)).padding(10.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHigh).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MockText(stringResource(R.string.class_table_import), size = 11.sp, weight = FontWeight.Bold)
            ImportRow(stringResource(R.string.sign_in_student_id)) { TextLine(0.8f, strong = true) }
            ImportRow(stringResource(R.string.class_table_semester_picker_label)) { MockText("114-1", size = 9.sp) }
            ImportRow(stringResource(R.string.class_table_import_course_count)) { MockText("8", size = 9.sp) }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(colors.primary).padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                MockText(stringResource(R.string.action_import), size = 10.sp, weight = FontWeight.SemiBold, color = colors.onPrimary)
            }
        }
    }
}

@Composable
private fun ImportRow(label: String, value: @Composable BoxScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MockText(label, Modifier.weight(1f), size = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd, content = value)
    }
}

/** The share page on the web: its address, the class table, and a way into the app. */
@Composable
private fun WebPage(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(colors.surface)) {
        Row(
            Modifier.fillMaxWidth().background(colors.surfaceContainer).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(3.dp))
            MockText("share.tigerduck.app", size = 8.sp, color = colors.onSurfaceVariant)
        }
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextLine(0.5f, height = 7.dp, strong = true)
            // The class table, small: a few courses across the week.
            Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerLow)) {
                listOf(
                    Triple(0.dp, 4.dp, colors.primaryContainer),
                    Triple(28.dp, 30.dp, colors.tertiaryContainer),
                    Triple(56.dp, 8.dp, colors.secondaryContainer),
                    Triple(84.dp, 50.dp, colors.primaryContainer),
                ).forEach { (x, y, fill) ->
                    Box(
                        Modifier
                            .padding(start = x + 4.dp, top = y)
                            .width(24.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(fill),
                    )
                }
            }
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(colors.primary).padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                MockText(openInTigerDuck.resolve(), size = 9.sp, weight = FontWeight.SemiBold, color = colors.onPrimary)
            }
        }
    }
}
