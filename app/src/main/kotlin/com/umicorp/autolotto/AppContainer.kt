package com.umicorp.autolotto

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.umicorp.autolotto.data.SecureStore
import com.umicorp.autolotto.dhlottery.AuthService
import com.umicorp.autolotto.dhlottery.DhlotteryException
import com.umicorp.autolotto.dhlottery.DhlotterySession
import com.umicorp.autolotto.dhlottery.HistoryService
import com.umicorp.autolotto.dhlottery.PurchaseResult
import com.umicorp.autolotto.dhlottery.PurchaseService
import com.umicorp.autolotto.dhlottery.ResultService
import com.umicorp.autolotto.scheduler.AlarmScheduler
import com.umicorp.autolotto.scheduler.BalanceAlert
import com.umicorp.autolotto.scheduler.PurchaseLock
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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime

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

    /** 마지막 구매 회차(멱등 가드) — 즉시 구매 CTA 모드(첫/추가) 분기의 단일 출처. */
    private val _lastPurchasedRound = MutableStateFlow(0)
    val lastPurchasedRound: StateFlow<Int> = _lastPurchasedRound.asStateFlow()

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
        _lastPurchasedRound.value = store.getLastPurchasedRound()
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
        // 로컬 계정 전환 커밋은 구매 임계구역과 직렬화(PurchaseLock) — 워커 구매와 경합 방지.
        // 회차 가드는 계정의 기록 — 다른 계정 로그인 시 무효화(같은 계정 재로그인은 보존).
        PurchaseLock.mutex.withLock {
            store.saveCredentials(id, pw)
            val owner = store.getLastPurchaseOwner()
            if (owner != null && owner != id) {
                store.setLastPurchasedRound(0)
                _lastPurchasedRound.value = 0
            }
        }
        _isLoggedIn.value = true
        _loggedInUserId.value = id
        _balance.value = auth.getBalance()
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        auth.logout()
        store.deleteCredentials()
        // 자격증명 없는 자동구매는 매주 조용히 무동작(+다른 계정 재로그인 시 이전 설정으로 재개 위험)
        // → 로그아웃과 함께 해제·알람 취소. 비로그인 상태에선 스위치가 잠겨 사용자가 끌 수도 없다.
        if (_autoEnabled.value) setAutoEnabled(false)
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

    /** 워커가 백그라운드에서 회차를 갱신했을 수 있어 홈 새로고침 때 재읽기. */
    suspend fun refreshLastPurchasedRound() = withContext(Dispatchers.IO) {
        _lastPurchasedRound.value = store.getLastPurchasedRound()
    }

    // === 즉시 구매 (홈 CTA — 스펙 docs/DESIGN-instant-purchase.md) ===

    /** 구매 요청 후 결과를 확인 못 한 실패(네트워크·타임아웃) — 재시도 유도 금지 신호. */
    class PurchaseResultUnknownException(cause: Throwable) : Exception(cause)

    /** 확정 다이얼로그가 표시한 회차와 실제 회차가 달라 구매 없이 중단한 경우. */
    class RoundChangedException : Exception()

    /** Mutex 획득 시점에 판매시간이 종료되어 구매 없이 중단한 경우(락 대기 중 경계 통과). */
    class SaleClosedException : Exception()

    /**
     * 즉시 구매. [expectedRound]는 확정 다이얼로그가 표시한 회차 — Mutex 안에서 현재 회차와
     * 대조해 다르면 [RoundChangedException](구매 요청 없음, 표시≠결제 방지). [extra]=false
     * (첫 구매: 저장 슬롯)는 회차 가드 재판정 후 이미 구매된 회차면 null(= "방금 구매됨"),
     * [extra]=true(추가: 자동 N게임)는 가드로 막지 않는다(서버 주간한도가 방어선). 세션 만료
     * 대비 매번 재로그인(워커 패턴). 성공 응답 관측 즉시 성공 확정 — 회차+계정 기록 실패가
     * 성공 결과를 가리면 재시도를 오유도하므로 runCatching. 잔액 갱신은 락 밖(실패 무시).
     */
    suspend fun instantPurchase(
        extra: Boolean,
        expectedRound: Int,
        autoGames: Int,
        manualNumbers: List<List<Int>>,
    ): PurchaseResult? {
        val result = PurchaseLock.mutex.withLock {
            // 락 대기 중 판매 마감·회차 경계를 넘었을 수 있어 게이트 전부를 락 안에서 재평가.
            val now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
            val saleOpen = SettingsViewModel.isValidPurchaseTime(now.dayOfWeek.value, now.hour)
            val round = PurchaseService.getCurrentRound()
            val recorded = store.getLastPurchasedRound()
            _lastPurchasedRound.value = recorded
            when (purchaseGate(extra, recorded, round, expectedRound, saleOpen)) {
                PurchaseGate.SALE_CLOSED -> throw SaleClosedException()
                PurchaseGate.ROUND_CHANGED -> throw RoundChangedException()
                PurchaseGate.ALREADY_PURCHASED -> return@withLock null  // 워커 선점 — 이미 구매됨
                PurchaseGate.PROCEED -> Unit
            }

            val cred = store.getCredentials()
            val id = requireNotNull(cred.userId) { "로그인이 필요합니다." }
            val pw = requireNotNull(cred.password) { "로그인이 필요합니다." }
            auth.login(id, pw)
            _isLoggedIn.value = true

            val r = try {
                PurchaseService(auth, session).purchase(autoGames = autoGames, manualNumbers = manualNumbers)
            } catch (e: DhlotteryException) {
                throw e                                             // 서버 확정 거절 — 메시지 그대로
            } catch (e: Exception) {
                throw PurchaseResultUnknownException(e)             // 요청 후 결과 불명
            }
            // 성공 확정 — 로컬 기록 실패가 성공 결과를 가리면 안 됨(재시도 오유도 방지).
            runCatching {
                store.setLastPurchasedRound(r.round)
                store.setLastPurchaseOwner(id)
            }
            _lastPurchasedRound.value = r.round
            r
        } ?: return null
        runCatching { refreshBalance() }                            // 락 밖 — 실패해도 성공 표시 유지
        return result
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

/** 5슬롯(null=미설정 / 빈=자동 / 6수=수동) → (자동 게임 수, 수동 번호 목록). 첫 구매 변환. */
fun splitSlots(slots: List<List<Int>?>): Pair<Int, List<List<Int>>> =
    slots.count { it?.isEmpty() == true } to slots.filterNotNull().filter { it.isNotEmpty() }

/** Mutex 내 구매 게이트(순수) — 판매 종료·회차 변경은 모드 공통 중단, 회차 가드는 첫 구매에만. */
enum class PurchaseGate { PROCEED, ALREADY_PURCHASED, ROUND_CHANGED, SALE_CLOSED }

fun purchaseGate(extra: Boolean, recordedRound: Int, currentRound: Int, expectedRound: Int, saleOpen: Boolean): PurchaseGate = when {
    !saleOpen -> PurchaseGate.SALE_CLOSED
    currentRound != expectedRound -> PurchaseGate.ROUND_CHANGED
    !extra && recordedRound >= currentRound -> PurchaseGate.ALREADY_PURCHASED
    else -> PurchaseGate.PROCEED
}
