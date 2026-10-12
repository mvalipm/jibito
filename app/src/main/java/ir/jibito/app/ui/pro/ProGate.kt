package ir.jibito.app.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.data.pro.ProFeature
import ir.jibito.app.data.pro.ProStatus
import ir.jibito.app.ui.theme.DesignIcons
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme

/**
 * باز کردن برگه‌ی «جیبیتوی کامل» از هر جای اپ؛ MainScreen فراهمش می‌کند.
 * امکانی که کاربر رویش زده را می‌گیرد (null = از تنظیمات، بدون امکان خاص).
 */
val LocalOpenPro = staticCompositionLocalOf<(ProFeature?) -> Unit> { {} }

/** وضعیت «جیبیتوی کامل» (خریده، ماه اول، یا بسته) */
@Composable
fun rememberProStatus(): ProStatus {
    val app = LocalContext.current.applicationContext as JibitoApplication
    val status by app.container.pro.status.collectAsState()
    return status
}

internal val ProFeature.title: Int
    get() = when (this) {
        ProFeature.FORECAST -> R.string.pro_feature_forecast
        ProFeature.BALANCE -> R.string.pro_feature_balance
        ProFeature.NATURE -> R.string.pro_feature_nature
        ProFeature.LONG_RANGE -> R.string.pro_feature_long_range
        ProFeature.THEMES -> R.string.pro_feature_themes
        ProFeature.EXPORT -> R.string.pro_feature_export
    }

internal val ProFeature.description: Int
    get() = when (this) {
        ProFeature.FORECAST -> R.string.pro_feature_forecast_desc
        ProFeature.BALANCE -> R.string.pro_feature_balance_desc
        ProFeature.NATURE -> R.string.pro_feature_nature_desc
        ProFeature.LONG_RANGE -> R.string.pro_feature_long_range_desc
        ProFeature.THEMES -> R.string.pro_feature_themes_desc
        ProFeature.EXPORT -> R.string.pro_feature_export_desc
    }

internal val ProFeature.icon: ImageVector
    get() = when (this) {
        ProFeature.FORECAST -> JibitoIcons.Calendar
        ProFeature.BALANCE -> JibitoIcons.Bank
        ProFeature.NATURE -> ProIcons.Nature
        ProFeature.LONG_RANGE -> ProIcons.Chart
        ProFeature.THEMES -> JibitoIcons.Palette
        ProFeature.EXPORT -> JibitoIcons.Export
    }

private object ProIcons {
    val Nature by lazy { DesignIcons.svg("pro_nature", DesignIcons.SHOP) }
    val Chart by lazy { DesignIcons.svg("pro_chart", DesignIcons.NAV_REPORTS) }
}

/**
 * جای یک امکان «جیبیتوی کامل» وقتی بسته است (بعد از ماه رایگان): چه چیزی این‌جاست و دکمه‌ی دیدن برگه‌ی خرید.
 * داده‌ی خود کاربر را نشان نمی‌دهد، فقط می‌گوید این بخش چه کار می‌کند.
 */
@Composable
fun ProLockedPanel(feature: ProFeature, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val t = JibitoTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(t.chip)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(JibitoIcons.Lock, contentDescription = null, tint = t.amber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.pro_locked_title, stringResource(feature.title)),
                fontSize = 15.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            stringResource(feature.description),
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 13.sp,
            lineHeight = 21.sp,
            color = t.muted,
        )
        Button(
            onClick = onOpen,
            modifier = Modifier.padding(top = 12.dp).heightIn(min = 44.dp),
            shape = RoundedCornerShape(50),
        ) {
            Text(stringResource(R.string.pro_locked_button), fontWeight = FontWeight.Bold)
        }
    }
}
