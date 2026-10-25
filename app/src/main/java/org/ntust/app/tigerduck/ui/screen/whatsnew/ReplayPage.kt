package org.ntust.app.tigerduck.ui.screen.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import org.ntust.app.tigerduck.R

/**
 * "Want to see these again?" — the first of a release's pages: Settings ›
 * What's new shows them again any time, which the demo walks to, from More.
 */
internal fun replayPage() = WhatsNewPage.Feature(
    id = "whats-new-replay",
    visual = WhatsNewVisual.Custom { animate -> ReplayDemo(animate) },
    title = WhatsNewText(
        en = "Want to see these again?",
        zhHant = "想再次看到以下的新功能描述？",
    ),
    body = WhatsNewText(
        en = "Go to Settings › What’s new, any time.",
        zhHant = "隨時到「設定 › 新功能說明」就能再看一次。",
    ),
)

private val replayDemoDescription = WhatsNewText(
    en = "Demo: from More, Settings opens, scrolls down to What’s new, and the new features show again.",
    zhHant = "示範：從「更多」打開設定，往下滑到「新功能說明」，新功能說明就會再出現一次。",
)

private const val REPLAY_LOOP_MILLIS = 8_000

// The demo's steps, as parts of its loop.
private const val SETTINGS_IN = 0.18f
private const val SCROLL_FROM = 0.28f
private const val SCROLL_TO = 0.45f
private const val TAP_ROW = 0.57f
private const val SHEET_FROM = 0.62f
private const val SHEET_TO = 0.72f
private const val ALL_OUT = 0.94f

/** How far Settings scrolls to bring About into view. */
private val SettingsScroll = 120.dp

@Composable
private fun ReplayDemo(animate: Boolean) {
    // Still: Settings at About, What's new about to be tapped.
    val clock = demoClock(animate, REPLAY_LOOP_MILLIS, still = 0.52f)
    val t = { clock.value }
    MockScreen(replayDemoDescription.resolve()) {
        MoreHeader()
        SettingsList(
            Modifier.graphicsLayer {
                val now = t()
                val shown = span(now, 0.15f, SETTINGS_IN + 0.04f) * (1f - span(now, ALL_OUT, 0.99f))
                translationX = (1f - shown) * size.width
            },
            scroll = { span(t(), SCROLL_FROM, SCROLL_TO) },
            tapped = { window(t(), TAP_ROW - 0.03f, SHEET_TO) },
        )
        WhatsNewSheetMock(
            Modifier.offset {
                val now = t()
                val up = span(now, SHEET_FROM, SHEET_TO) * (1f - span(now, ALL_OUT, 0.99f))
                IntOffset(0, lerp(MockScreenHeight, 24.dp, up).roundToPx())
            },
        )
        // More's gear, then What's new.
        Fingertip(196.dp, 22.dp, shown = { window(t(), 0.06f, 0.18f) }, press = { span(t(), 0.14f, 0.18f) })
        Fingertip(110.dp, 187.dp, shown = { window(t(), 0.48f, 0.60f) }, press = { span(t(), TAP_ROW, TAP_ROW + 0.03f) })
    }
}

/** More: its title and the gear that opens Settings, over its grid. */
@Composable
private fun MoreHeader() {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.surfaceContainerLow).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MockText(stringResource(R.string.feature_more_short), Modifier.weight(1f), size = 15.sp, weight = FontWeight.Bold)
            Icon(Icons.Filled.Settings, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) {
                    Column(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(8.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.6f)))
                        TextLine(0.6f, strong = true)
                    }
                }
            }
        }
    }
}

/** Settings, scrolled by [scroll] (0 to 1) down to About, its What's new row lit by [tapped]. */
@Composable
private fun SettingsList(modifier: Modifier, scroll: () -> Float, tapped: () -> Float) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(colors.surfaceContainerLow).clipToBounds()) {
        Column(
            Modifier
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .padding(horizontal = 12.dp)
                .graphicsLayer { translationY = -scroll() * SettingsScroll.toPx() },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.height(28.dp), contentAlignment = Alignment.CenterStart) {
                MockText(stringResource(R.string.feature_settings), size = 15.sp, weight = FontWeight.Bold)
            }
            repeat(2) {
                SectionLine()
                SettingsCard { repeat(3) { PlainRow() } }
            }
            Box(Modifier.height(16.dp), contentAlignment = Alignment.CenterStart) {
                MockText(stringResource(R.string.settings_section_about), size = 9.sp, weight = FontWeight.SemiBold, color = colors.primary)
            }
            SettingsCard {
                LabeledRow(stringResource(R.string.settings_check_server_status))
                LabeledRow(
                    stringResource(R.string.settings_whats_new),
                    Modifier.drawBehind { drawRect(colors.primary.copy(alpha = 0.16f * tapped())) },
                )
                LabeledRow(stringResource(R.string.settings_official_website))
            }
        }
    }
}

@Composable
private fun SectionLine() {
    Box(Modifier.height(16.dp), contentAlignment = Alignment.CenterStart) { TextLine(0.25f, height = 5.dp) }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface), content = content)
}

@Composable
private fun PlainRow() {
    Row(Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { TextLine(0.5f, strong = true) }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun LabeledRow(label: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(26.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        MockText(label, Modifier.weight(1f), size = 10.sp)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
    }
}

/** What's New as it opens: page dots, its sparkles, a title and a button. */
@Composable
private fun WhatsNewSheetMock(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .height(MockScreenHeight - 24.dp)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(colors.surface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { index ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (index == 0) colors.primary else colors.outlineVariant))
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.size(56.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
        }
        TextLine(0.6f, height = 8.dp, strong = true)
        TextLine(0.8f)
        TextLine(0.7f)
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(50)).background(colors.primary))
    }
}
