package ir.jibito.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import ir.jibito.app.JibitoApplication
import ir.jibito.app.R
import ir.jibito.app.ui.review.ReviewScreen
import ir.jibito.app.ui.smslist.SmsListScreen
import ir.jibito.app.ui.summary.SummaryScreen
import ir.jibito.app.util.Jalali

private enum class Tab(val icon: String, val label: Int) {
    Summary("📊", R.string.tab_summary),
    Transactions("🧾", R.string.tab_transactions),
}

/**
 * صفحه‌ی اصلی اپ با نوار پایین: «خلاصه» و «تراکنش‌ها».
 * موقع باز شدن اپ: پیامک‌ها خوانده می‌شوند؛ اگر پیامک تازه‌ای در «صندوق بررسی» باشد،
 * اول صفحه‌ی بررسی نشان داده می‌شود (به‌جای نوتیفیکیشن — تا نوتیفیکیشن‌های اپ همیشه معتبر بمانند).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val container = (LocalContext.current.applicationContext as JibitoApplication).container
    var tab by rememberSaveable { mutableStateOf(Tab.Summary) }
    var showReview by rememberSaveable { mutableStateOf(false) }
    val pendingFlow = remember { container.reviewRepository.observePending() }
    val pending by pendingFlow.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        container.transactionRepository.syncFromSms()
        if (container.reviewRepository.countNotYetShown() > 0) {
            container.reviewRepository.markAllShown()
            showReview = true
        }
    }

    if (showReview) {
        BackHandler { showReview = false }
        ReviewScreen(onClose = { showReview = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.icon, fontSize = 20.sp) },
                        label = { Text(stringResource(t.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        // فاصله‌ها (نوار وضعیت و نوار پایین) همین‌جا اعمال و «مصرف» می‌شوند تا صفحه‌ها دوباره اعمالشان نکنند
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            if (pending.isNotEmpty()) {
                ReviewBanner(count = pending.size, onClick = { showReview = true })
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.Summary -> SummaryScreen()
                    Tab.Transactions -> SmsListScreen()
                }
            }
        }
    }
}

@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(colors.secondaryContainer, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📩", fontSize = 18.sp)
        Text(
            Jalali.toPersianDigits(stringResource(R.string.review_banner, count)),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSecondaryContainer,
        )
        Text("›", fontSize = 20.sp, color = colors.onSecondaryContainer)
    }
}
