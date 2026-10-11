package ir.jibito.app.ui.sleep

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ir.jibito.app.R
import ir.jibito.app.ui.common.Mascot
import ir.jibito.app.ui.common.MascotFace
import ir.jibito.app.ui.theme.JibitoIcons
import ir.jibito.app.ui.theme.JibitoTheme
import ir.jibito.app.util.Jalali

/** برندهایی که اپ‌ها را بیشتر از اندرویدِ خام می‌خوابانند و صفحه‌ی تنظیمات خودشان را دارند */
internal enum class PhoneBrand {
    XIAOMI, SAMSUNG, HUAWEI, OTHER;

    companion object {
        fun of(manufacturer: String?): PhoneBrand = when (manufacturer.orEmpty().lowercase()) {
            "xiaomi", "redmi", "poco" -> XIAOMI
            "samsung" -> SAMSUNG
            "huawei", "honor" -> HUAWEI
            else -> OTHER
        }
    }
}

/**
 * یک قدم «بیدار نگهم دار».
 * @param done null یعنی اپ نمی‌تواند بسنجد (مثلاً «اجرای خودکار» شیائومی)؛ پس فقط دکمه‌ی «باز کن» دارد
 * @param intents صفحه‌ها به ترتیب امتحان؛ اولی که باز شد. آخری همیشه صفحه‌ی تنظیمات خود اپ است تا کاربر بن‌بست نبیند
 */
internal data class SleepStep(val title: String, val detail: String?, val done: Boolean?, val intents: List<Intent>)

/** برگه‌ی «بیدار نگهم دار»: قدم‌های همین برند؛ «انجام دادم» شمارش خرج‌های دیر را از صفر شروع می‌کند */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSheet(onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val brand = remember { PhoneBrand.of(Build.MANUFACTURER) }
    // برگشتن از تنظیمات گوشی ← دوباره سنجیدن قدم باتری
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed++ }
    val batteryOk = remember(resumed) { batteryUnrestricted(context) }
    val steps = sleepSteps(brand, batteryOk, context)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = JibitoTheme.colors.sheet,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
    ) {
        SleepSheetContent(
            body = stringResource(brandBody(brand)),
            steps = steps,
            onOpen = { openFirst(context, it.intents) },
            onDone = onDone,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )
    }
}

/** محتوای برگه (جدا، برای اسکرین‌شات) */
@Composable
internal fun SleepSheetContent(
    body: String,
    steps: List<SleepStep>,
    onOpen: (SleepStep) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(56.dp, face = MascotFace.SLEEPY)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.sleep_sheet_title),
            modifier = Modifier.semantics { heading() },
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = colors.onBackground,
        )
        Text(
            body,
            modifier = Modifier.padding(top = 4.dp),
            fontSize = 14.sp,
            lineHeight = 23.sp,
            color = t.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        steps.forEachIndexed { i, step ->
            if (i > 0) HorizontalDivider(color = t.border)
            StepRow(i + 1, step, onOpen = { onOpen(step) })
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(50),
        ) {
            Text(stringResource(R.string.sleep_done), fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Text(
            stringResource(R.string.sleep_note),
            modifier = Modifier.padding(top = 8.dp),
            fontSize = 12.sp,
            color = t.muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StepRow(number: Int, step: SleepStep, onOpen: () -> Unit) {
    val t = JibitoTheme.colors
    val colors = MaterialTheme.colorScheme
    val done = step.done == true
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .then(if (done) Modifier.background(t.teal) else Modifier.background(t.chip)),
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(JibitoIcons.Check, contentDescription = null, tint = t.onFg, modifier = Modifier.size(16.dp))
            } else {
                Text(Jalali.toPersianDigits(number.toString()), fontSize = 14.sp, fontWeight = FontWeight.Black, color = colors.onBackground)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(step.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            if (done) {
                Text(stringResource(R.string.sleep_on), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = t.teal)
            } else {
                step.detail?.let { Text(it, fontSize = 13.sp, lineHeight = 21.sp, color = t.muted) }
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.padding(top = 8.dp).heightIn(min = 40.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(stringResource(R.string.sleep_open), fontWeight = FontWeight.Bold, color = colors.onBackground)
                }
            }
        }
    }
}

private fun brandBody(brand: PhoneBrand): Int = when (brand) {
    PhoneBrand.XIAOMI -> R.string.sleep_sheet_body_xiaomi
    PhoneBrand.SAMSUNG -> R.string.sleep_sheet_body_samsung
    PhoneBrand.HUAWEI -> R.string.sleep_sheet_body_huawei
    PhoneBrand.OTHER -> R.string.sleep_sheet_body
}

/** قدم‌های همین برند؛ قدم باتری همیشه هست و اپ خودش می‌سنجد */
@Composable
internal fun sleepSteps(brand: PhoneBrand, batteryOk: Boolean, context: Context): List<SleepStep> {
    val appPage = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
    val batteryTitle = stringResource(R.string.sleep_step_battery)
    // شیائومی و سامسونگ «بدون محدودیت» را در صفحه‌ی خود اپ دارند؛ بقیه در فهرست بهینه‌سازی باتری اندروید
    val battery = if (brand == PhoneBrand.XIAOMI || brand == PhoneBrand.SAMSUNG) {
        SleepStep(batteryTitle, stringResource(R.string.sleep_step_battery_app), batteryOk, listOf(appPage))
    } else {
        SleepStep(
            batteryTitle, stringResource(R.string.sleep_step_battery_list), batteryOk,
            listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), appPage),
        )
    }
    return when (brand) {
        PhoneBrand.XIAOMI -> listOf(
            SleepStep(
                stringResource(R.string.sleep_step_autostart), stringResource(R.string.sleep_step_autostart_detail), null,
                listOf(component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"), appPage),
            ),
            battery,
        )
        PhoneBrand.SAMSUNG -> listOf(
            battery,
            SleepStep(
                stringResource(R.string.sleep_step_samsung_sleeping), stringResource(R.string.sleep_step_samsung_sleeping_detail), null,
                listOf(component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"), appPage),
            ),
        )
        PhoneBrand.HUAWEI -> listOf(
            SleepStep(
                stringResource(R.string.sleep_step_huawei), stringResource(R.string.sleep_step_huawei_detail), null,
                listOf(component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"), appPage),
            ),
            battery,
        )
        PhoneBrand.OTHER -> listOf(battery)
    }
}

/** اندروید جیبیتو را از بهینه‌سازی باتری بیرون گذاشته و «محدودیت پس‌زمینه» هم ندارد؟ */
private fun batteryUnrestricted(context: Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java)
    val notOptimized = power?.isIgnoringBatteryOptimizations(context.packageName) == true
    val restricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
    return notOptimized && !restricted
}

/** اولین صفحه‌ای که روی این گوشی باز شود؛ صفحه‌های مخصوص برند در هر مدل نیستند */
private fun openFirst(context: Context, intents: List<Intent>) {
    for (intent in intents) {
        try {
            context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            // صفحه‌ی بعدی
        } catch (e: SecurityException) {
            // بعضی صفحه‌های برند به اپ‌های دیگر بسته‌اند
        }
    }
}
