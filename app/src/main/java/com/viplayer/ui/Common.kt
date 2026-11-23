package com.viplayer.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.viplayer.ViPlayerApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun app(): ViPlayerApp = LocalContext.current.applicationContext as ViPlayerApp

@Composable
fun ViTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF9DB7F5))
        else -> lightColorScheme(primary = Color(0xFF2F4F9A))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Scaffold для вкладок: системные отступы снизу уже учтены внешним Scaffold с навигацией. */
@Composable
fun ScreenScaffold(
    topBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(topBar = topBar, contentWindowInsets = WindowInsets(0, 0, 0, 0), content = content)
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** 1:05:12 или 5:12 */
fun formatClock(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** «7 ч 12 мин» */
fun formatLong(ms: Long): String {
    val totalMin = (ms.coerceAtLeast(0) / 60_000.0).roundToLong()
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "$h ч $m мин"
        h > 0 -> "$h ч"
        else -> "$m мин"
    }
}

/** «1.25×» */
fun formatSpeed(speed: Float): String =
    String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.') + "×"

fun formatTimeOfDay(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

val SPEEDS = listOf(0.75f, 1f, 1.1f, 1.2f, 1.25f, 1.3f, 1.4f, 1.5f, 1.6f, 1.75f, 2f, 2.25f, 2.5f, 3f)
