package ir.jibito.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import ir.jibito.app.R
import ir.jibito.app.ui.smslist.SmsListScreen
import ir.jibito.app.ui.summary.SummaryScreen

private enum class Tab(val icon: String, val label: Int) {
    Summary("📊", R.string.tab_summary),
    Transactions("🧾", R.string.tab_transactions),
}

/** صفحه‌ی اصلی اپ با نوار پایین: «خلاصه» و «تراکنش‌ها». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    var tab by rememberSaveable { mutableStateOf(Tab.Summary) }

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
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            when (tab) {
                Tab.Summary -> SummaryScreen()
                Tab.Transactions -> SmsListScreen()
            }
        }
    }
}
