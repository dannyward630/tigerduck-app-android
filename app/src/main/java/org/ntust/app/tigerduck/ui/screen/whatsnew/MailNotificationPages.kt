package org.ntust.app.tigerduck.ui.screen.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ntust.app.tigerduck.R

/**
 * "Mail notifications, your way": School Mail's notification settings mute
 * the NTUST Bulletin Board and Moodle's submission receipts, and keep quiet
 * hours.
 */
internal fun mailNotificationsPage() = WhatsNewPage.Feature(
    id = "mail-notifications",
    visual = WhatsNewVisual.Custom { animate -> MailNotificationsDemo(animate) },
    title = WhatsNewText(
        en = "Mail notifications, your way",
        zhHant = "信件通知由你決定",
    ),
    body = WhatsNewText(
        en = "In School Mail notifications, mute mail from the NTUST Bulletin Board and Moodle's submission receipts, and set quiet hours.",
        zhHant = "在「校園信箱通知」裡，可以不通知台科公告欄與 Moodle 繳交通知的信，也能設定勿擾時段。",
    ),
)

private val mailDemoDescription = WhatsNewText(
    en = "Demo: in School Mail notifications, quiet hours turn on from 23:00 to 07:00, then the NTUST Bulletin Board and Moodle submission receipts are muted.",
    zhHant = "示範：在校園信箱通知裡開啟 23:00 到 07:00 的勿擾時段，再關閉台科公告欄與 Moodle 繳交通知。",
)

private const val MAIL_LOOP_MILLIS = 8_000
private const val ALL_OFF = 0.94f

@Composable
private fun MailNotificationsDemo(animate: Boolean) {
    // Still: everything on.
    val clock = demoClock(animate, MAIL_LOOP_MILLIS, still = 0.85f)
    val t = { clock.value }
    // Each switch goes on after its tap, and they all go back off before the loop starts again.
    fun turnedOn(at: Float) = { span(t(), at, at + 0.04f) * (1f - span(t(), ALL_OFF, ALL_OFF + 0.04f)) }
    val quiet = turnedOn(0.16f)
    val bulletin = turnedOn(0.42f)
    val moodle = turnedOn(0.62f)
    val colors = MaterialTheme.colorScheme
    MockScreen(mailDemoDescription.resolve()) {
        Column(
            Modifier.fillMaxSize().background(colors.surfaceContainerLow).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                MockText(stringResource(R.string.school_mail_notification_settings_title), size = 12.sp, weight = FontWeight.Bold)
            }
            SettingsCard { SwitchRow(stringResource(R.string.school_mail_settings_notifications), on = { 1f }) }
            SectionLabel(stringResource(R.string.school_mail_quiet_hours))
            SettingsCard {
                SwitchRow(stringResource(R.string.school_mail_quiet_hours), on = quiet)
                Column(Modifier.graphicsLayer { alpha = quiet(); translationY = (1f - quiet()) * -6.dp.toPx() }) {
                    ValueRow(stringResource(R.string.school_mail_quiet_hours_start), "23:00")
                    ValueRow(stringResource(R.string.school_mail_quiet_hours_end), "07:00")
                }
            }
            SectionLabel(stringResource(R.string.school_mail_filters))
            SettingsCard {
                SwitchRow(stringResource(R.string.school_mail_filter_preset_bulletin), on = bulletin)
                SwitchRow(stringResource(R.string.school_mail_filter_preset_moodle_submission), on = moodle)
            }
        }
        // The quiet hours switch, then the two mutes.
        Fingertip(192.dp, 90.dp, shown = { window(t(), 0.08f, 0.18f) }, press = { span(t(), 0.13f, 0.17f) })
        Fingertip(192.dp, 196.dp, shown = { window(t(), 0.34f, 0.44f) }, press = { span(t(), 0.39f, 0.43f) })
        Fingertip(192.dp, 222.dp, shown = { window(t(), 0.54f, 0.64f) }, press = { span(t(), 0.59f, 0.63f) })
    }
}

@Composable
private fun SectionLabel(text: String) {
    MockText(text, Modifier.padding(start = 4.dp, top = 2.dp), size = 9.sp, weight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp),
        content = content,
    )
}

@Composable
private fun SwitchRow(label: String, on: () -> Float) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        MockText(label, Modifier.weight(1f), size = 10.sp)
        MockSwitch(on)
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        MockText(label, Modifier.weight(1f), size = 10.sp)
        MockText(value, size = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
