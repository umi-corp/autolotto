package com.umicorp.autolotto

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.umicorp.autolotto.data.SecureStore
import com.umicorp.autolotto.dhlottery.AuthService
import com.umicorp.autolotto.dhlottery.DhlotterySession
import com.umicorp.autolotto.dhlottery.HistoryService
import com.umicorp.autolotto.dhlottery.ResultService
import com.umicorp.autolotto.scheduler.AlarmScheduler
import com.umicorp.autolotto.scheduler.BalanceAlert
import com.umicorp.autolotto.update.AppUpdater
import com.umicorp.autolotto.update.UpdateInfo
import java.io.File
import com.umicorp.autolotto.ui.vm.HistoryViewModel
import com.umicorp.autolotto.ui.vm.HomeViewModel
import com.umicorp.autolotto.ui.vm.NumberViewModel
import com.umicorp.autolotto.ui.vm.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 앱 스코프 컴포지션 루트 (원본 Riverpod `ProviderScope` + 전역 프로바이더 대응).
 *
 * 두 가지를 한곳에 모은다:
 *  1) **공유 서비스** — 로그인 세션을 앱 전역에서 공유하기 위한 단일 [DhlotterySession]/[AuthService]/
 *     [SecureStore]/[AlarmScheduler]. (백그라운드 Worker는 자체 세션을 쓴다 — 이미 구현됨.)
 *  2) **공유 반응형 상태** — 화면 간(홈↔설정 등) 즉시 동기화되어야 하는 값들의 단일 출처(StateFlow).
 *     원본 전역 프로바이더(isLoggedIn/balance/autoEnabled/...)와 1:1.
 *
 * 중앙 설계 사실(이중 상태): 설정 변경은 **StateFlow(UI 반응) + SecureStore(영속) 양쪽**에 써야 한다.
 * 백그라운드 알람 콜백은 위젯/플로우 접근 없이 SecureStore를 raw 키로 직접 읽으므로, 백그라운드가
 * 필요로 하는 값은 반드시 SecureStore에도 영속해야 한다. 그 양방향 쓰기를 이 클래스의 setter들이 일원화한다.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    // === 공유 서비스 ===
    val store = SecureStore(appContext)
    val session = DhlotterySession()
    val auth = AuthService(session)
    val scheduler = AlarmScheduler(appContext)
    val resultService = ResultService()            // 로그인 불필요 — 자체 세션
    val historyService = HistoryService(session)   // 로그인된 공유 세션 차용

    // === 공유 반응형 상태 (원본 전역 프로바이더와 1:1) ===
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _balance = MutableStateFlow(0)
    val balance: StateFlow<Int> = _balance.asStateFlow()

    private val _autoEnabled = MutableStateFlow(false)
    val autoEnabled: StateFlow<Boolean> = _autoEnabled.asStateFlow()

    private val _autoGames = MutableStateFlow(0)
    val autoGames: StateFlow<Int> = _autoGames.asStateFlow()

    private val _autoPurchaseDay = MutableStateFlow(7)     // 기본 일요일 (SecureStore 기본값과 동일)
    val autoPurchaseDay: StateFlow<Int> = _autoPurchaseDay.asStateFlow()

    private val _autoPurchaseHour = MutableStateFlow(9)
    val autoPurchaseHour: StateFlow<Int> = _autoPurchaseHour.asStateFlow()

    private val _autoPurchaseMinute = MutableStateFlow(0)
    val autoPurchaseMinute: StateFlow<Int> = _autoPurchaseMinute.asStateFlow()

    private val _balanceAlertEnabled = MutableStateFlow(false)
    val balanceAlertEnabled: StateFlow<Boolean> = _balanceAlertEnabled.asStateFlow()

    private val _balanceAlertThreshold = MutableStateFlow(5000)
    val balanceAlertThreshold: StateFlow<Int> = _balanceAlertThreshold.asStateFlow()

    private val _language = MutableStateFlow("system")     // "system"/"ko"/"en"/"ja"
    val language: StateFlow<String> = _language.asStateFlow()

    private val _loggedInUserId = MutableStateFlow<String?>(null)
    val loggedInUserId: StateFlow<String?> = _loggedInUserId.asStateFlow()

    // === 인앱 업데이트 (사이드로드 배포) ===
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _updateProgress = MutableStateFlow<Float?>(null)  // null=대기, 0..1=다운로드 중
    val updateProgress: StateFlow<Float?> = _updateProgress.asStateFlow()

    // === 스플래시 하이드레이션 (원본 splash_screen `_initialize` 1:1) ===

    /** SecureStore → 플로우. 네트워크 없음. */
    suspend fun loadSettings() = withContext(Dispatchers.IO) {
        _autoEnabled.value = store.getAutoEnabled()
        _autoGames.value = store.getAutoGames()
        _autoPurchaseDay.value = store.getAutoPurchaseDay()
        _autoPurchaseHour.value = store.getAutoPurchaseHour()
        _autoPurchaseMinute.value = store.getAutoPurchaseMinute()
        _balanceAlertEnabled.value = store.getBalanceAlertEnabled()
        _balanceAlertThreshold.value = store.getBalanceAlertThreshold()
        _language.value = store.getLanguage()
        _loggedInUserId.value = store.getCredentials().userId
        // 앱 실행마다 알람 재무장(자동구매 활성 시). 업데이트·강제종료·OEM 정리로 소실된 알람 복구 — 멱등.
        scheduler.rescheduleAll()
    }

    /** GitHub 릴리스에서 새 버전 확인 → updateInfo 세팅(없으면 null). AppShell 진입 시 호출. */
    suspend fun checkForUpdate() {
        _updateInfo.value = AppUpdater.check(BuildConfig.VERSION_NAME)
    }

    fun dismissUpdate() { _updateInfo.value = null }

    /** 감지된 업데이트 APK 다운로드 → 성공 시 File. 진행률은 [updateProgress]. */
    suspend fun downloadUpdate(context: Context): File? {
        val info = _updateInfo.value ?: return null
        _updateProgress.value = 0f
        val file = AppUpdater.download(context, info.downloadUrl) { p -> _updateProgress.value = p }
        _updateProgress.value = null
        return file
    }

    /** 저장된 자격증명으로 자동 로그인 + 잔액 + 잔액부족 체크. 실패는 조용히 무시(원본). */
    suspend fun autoLogin() {
        if (!store.hasCredentials()) return
        val cred = store.getCredentials()
        val id = cred.userId ?: return
        val pw = cred.password ?: return
        try {
            auth.login(id, pw)
            _isLoggedIn.value = true
            _loggedInUserId.value = id
            val b = auth.getBalance()
            _balance.value = b
            BalanceAlert.checkAndNotify(appContext, b, _balanceAlertEnabled.value, _balanceAlertThreshold.value)
        } catch (_: Exception) {
            // 원본 debugPrint('자동 로그인 실패') 후 무시.
        }
    }

    // === 계정 ===

    /** 수동 로그인(설정 화면). 실패 시 throw(INVALID_CREDENTIALS 등) — 호출자가 매핑. 잔액알림은 안 함(원본과 동일). */
    suspend fun login(id: String, pw: String) {
        auth.login(id, pw)
        store.saveCredentials(id, pw)
        _isLoggedIn.value = true
        _loggedInUserId.value = id
        _balance.value = auth.getBalance()
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        auth.logout()
        store.deleteCredentials()
        _isLoggedIn.value = false
        _balance.value = 0
        _loggedInUserId.value = null
    }

    /** 잔액 재조회 + 잔액부족 체크(원본 home/_refreshBalance). */
    suspend fun refreshBalance() {
        val b = auth.getBalance()
        _balance.value = b
        BalanceAlert.checkAndNotify(appContext, b, _balanceAlertEnabled.value, _balanceAlertThreshold.value)
    }

    // === 자동구매 설정 (write-through: 플로우 + SecureStore + 알람) ===

    suspend fun setAutoEnabled(v: Boolean) = withContext(Dispatchers.IO) {
        _autoEnabled.value = v
        store.setAutoEnabled(v)
        if (v) {
            scheduler.scheduleAutoPurchase()
            scheduler.scheduleCheckResult()
        } else {
            scheduler.cancelAll()
        }
    }

    suspend fun setAutoGames(n: Int) = withContext(Dispatchers.IO) {
        _autoGames.value = n
        store.setAutoGames(n)
    }

    suspend fun setAutoPurchaseDay(day: Int) = withContext(Dispatchers.IO) {
        _autoPurchaseDay.value = day
        store.setAutoPurchaseDay(day)            // 스토어 먼저 — 스케줄러가 스토어를 다시 읽는다
        if (_autoEnabled.value) scheduler.scheduleAutoPurchase()
    }

    suspend fun setAutoPurchaseTime(hour: Int, minute: Int) = withContext(Dispatchers.IO) {
        _autoPurchaseHour.value = hour
        _autoPurchaseMinute.value = minute
        store.setAutoPurchaseHour(hour)
        store.setAutoPurchaseMinute(minute)
        if (_autoEnabled.value) scheduler.scheduleAutoPurchase()
    }

    suspend fun setLanguage(lang: String) = withContext(Dispatchers.IO) {
        _language.value = lang
        store.setLanguage(lang)
    }

    suspend fun setBalanceAlertEnabled(v: Boolean) = withContext(Dispatchers.IO) {
        _balanceAlertEnabled.value = v
        store.setBalanceAlertEnabled(v)
    }

    suspend fun setBalanceAlertThreshold(v: Int) = withContext(Dispatchers.IO) {
        _balanceAlertThreshold.value = v
        store.setBalanceAlertThreshold(v)
    }

    // === 수동 번호 (manual_numbers: 5슬롯 JSON, 백그라운드 구매 잡과 공유 포맷) ===

    /** SecureStore의 manual_numbers → 5슬롯 리스트(null=미설정 / emptyList=자동 / [nums]=수동). */
    suspend fun loadManualGames(): List<List<Int>?> = withContext(Dispatchers.IO) {
        val out = MutableList<List<Int>?>(5) { null }
        runCatching {
            val arr = JSONArray(store.getManualNumbers())
            for (i in 0 until minOf(arr.length(), 5)) {
                if (arr.isNull(i)) continue
                val g = arr.optJSONArray(i) ?: continue
                out[i] = if (g.length() == 0) emptyList() else (0 until g.length()).map { g.getInt(it) }
            }
        }
        out
    }

    /** 5슬롯 → JSON 저장 + 게임 수(=설정된 슬롯 수) 반영. 백그라운드 구매 잡이 같은 포맷으로 읽는다. */
    suspend fun saveManualGames(games: List<List<Int>?>) = withContext(Dispatchers.IO) {
        setAutoGames(games.count { it != null })
        val arr = JSONArray()
        for (g in games) {
            when {
                g == null -> arr.put(JSONObject.NULL)
                g.isEmpty() -> arr.put(JSONArray())
                else -> arr.put(JSONArray(g))
            }
        }
        store.setManualNumbers(arr.toString())
    }

    // === 데이터 초기화 (원본 설정 화면 `_showResetDialog`) ===
    suspend fun resetAll() = withContext(Dispatchers.IO) {
        store.clearAll()
        auth.logout()
        scheduler.cancelAll()  // 삭제된 설정을 참조하는 알람 제거 (원본 대비 의도된 보강)
        _isLoggedIn.value = false
        _balance.value = 0
        _autoEnabled.value = false
        _autoGames.value = 0
        _autoPurchaseDay.value = 7
        _autoPurchaseHour.value = 9
        _autoPurchaseMinute.value = 0
        _balanceAlertEnabled.value = false
        _balanceAlertThreshold.value = 5000
        _loggedInUserId.value = null
    }

    /** 화면별 ViewModel 팩토리(컴포지션 루트가 컨테이너 주입). */
    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer { HomeViewModel(this@AppContainer) }
        initializer { NumberViewModel(this@AppContainer) }
        initializer { HistoryViewModel(this@AppContainer) }
        initializer { SettingsViewModel(this@AppContainer) }
    }
}

/**
 * Application 서브클래스 — 프로세스 1개당 [AppContainer] 1개(applicationContext 기반).
 * 매니페스트 `android:name=".AutoLottoApplication"`로 등록.
 */
class AutoLottoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Compose/Activity에서 앱 스코프 컨테이너 접근. */
val Context.appContainer: AppContainer
    get() = (applicationContext as AutoLottoApplication).container
