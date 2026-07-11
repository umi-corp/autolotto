package com.umicorp.autolotto

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.umicorp.autolotto.scheduler.Notifications
import com.umicorp.autolotto.ui.AppRoot
import com.umicorp.autolotto.ui.LocalizedApp
import com.umicorp.autolotto.ui.theme.AutoLottoTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** 알림 탭 진입 시 이동할 탭 — cold(onCreate 인텐트)/warm(onNewIntent) 공용, AppShell이 소비. */
    private val pendingTab = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTab.value = intent.getStringExtra(Notifications.EXTRA_TAB)
        val container = (application as AutoLottoApplication).container
        setContent {
            // 선택 언어를 구독 → 변경 시 전체 재컴포지션(원본 appLocaleProvider watch).
            val language by container.language.collectAsState()
            AutoLottoTheme {
                // ActivityResult/ViewModel owner를 Activity로 명시 제공 — LocalizedApp의 로케일 컨텍스트가
                // LocalContext를 덮어써도 rememberLauncherForActivityResult/viewModel()이 owner를 찾게 한다.
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides this@MainActivity,
                    LocalViewModelStoreOwner provides this@MainActivity,
                ) {
                    LocalizedApp(language) {
                        AppRoot(pendingTab = pendingTab)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingTab.value = intent.getStringExtra(Notifications.EXTRA_TAB)
    }
}
