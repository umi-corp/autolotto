package com.umicorp.autolotto.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umicorp.autolotto.AppContainer
import com.umicorp.autolotto.data.Purchase
import com.umicorp.autolotto.data.WinningResult
import com.umicorp.autolotto.dhlottery.PurchaseService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 화면별 ViewModel (원본 Riverpod 화면 상태 포트).
 *
 * 공유 반응형 상태(로그인·잔액·자동구매설정·언어·잔액알림)는 [AppContainer]가 단일 출처로 들고
 * 있고(원본 전역 프로바이더와 1:1), ViewModel은 그 StateFlow를 그대로 재노출 + 화면-로컬 상태와
 * 액션만 갖는다. suspend 서비스 호출은 전부 viewModelScope에서 수행한다.
 *
 * UI는 단위테스트 불가 → 검증은 컴파일 + assembleDebug. 실제 화면 배선은 Slice 5b.
 */

/** 홈: 카운트다운(회차) + 지난 회차 당첨번호 + 잔액/자동구매 상태(공유). */
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val isLoggedIn = container.isLoggedIn
    val balance = container.balance
    val autoEnabled = container.autoEnabled
    val autoPurchaseDay = container.autoPurchaseDay
    val autoPurchaseHour = container.autoPurchaseHour
    val autoPurchaseMinute = container.autoPurchaseMinute
    val balanceAlertEnabled = container.balanceAlertEnabled
    val balanceAlertThreshold = container.balanceAlertThreshold

    /** 현재 판매 중인 회차(KST). 카운트다운 헤더용. */
    val currentRound: Int get() = PurchaseService.getCurrentRound()

    private val _winning = MutableStateFlow<WinningResult?>(null)
    val winning: StateFlow<WinningResult?> = _winning.asStateFlow()
    private val _loadingNumbers = MutableStateFlow(false)
    val loadingNumbers: StateFlow<Boolean> = _loadingNumbers.asStateFlow()

    init { fetchWinningNumbers() }

    /** 지난 회차(현재-1) 당첨번호 조회. 실패는 무시(원본 debugPrint 후 무시). */
    fun fetchWinningNumbers() {
        viewModelScope.launch {
            _loadingNumbers.value = true
            runCatching { container.resultService.getWinningNumbers(currentRound - 1) }
                .getOrNull()?.let { _winning.value = it }
            _loadingNumbers.value = false
        }
    }

    /** 당겨서 새로고침: 당첨번호 + 잔액. */
    fun refreshAll() {
        fetchWinningNumbers()
        viewModelScope.launch { container.refreshBalance() }
    }
}

/** 번호 설정: 5슬롯(null=미설정 / emptyList=자동 / [nums]=수동) 읽기·쓰기. */
class NumberViewModel(private val container: AppContainer) : ViewModel() {
    val autoEnabled = container.autoEnabled
    // 저장 완료 스낵바의 스케줄 문구용(원본 number_screen이 autoPurchaseDay/Hour/Minute 프로바이더를 읽음).
    val autoPurchaseDay = container.autoPurchaseDay
    val autoPurchaseHour = container.autoPurchaseHour
    val autoPurchaseMinute = container.autoPurchaseMinute

    private val _games = MutableStateFlow<List<List<Int>?>>(List(5) { null })
    val games: StateFlow<List<List<Int>?>> = _games.asStateFlow()

    init { loadSavedGames() }

    fun loadSavedGames() {
        viewModelScope.launch { _games.value = container.loadManualGames() }
    }

    /** 게임 수는 설정된 슬롯 수로 자동 반영(원본 `_saveConfig`). */
    fun saveConfig(games: List<List<Int>?>) {
        _games.value = games
        viewModelScope.launch { container.saveManualGames(games) }
    }
}

/** 기록: 로그인 상태면 dhlottery에서 최근 구매내역 라이브 조회(로컬 DB 없음). */
class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    val isLoggedIn = container.isLoggedIn  // 빈 화면 문구 분기용(원본 isLoggedInProvider watch)
    private val _purchases = MutableStateFlow<List<Purchase>>(emptyList())
    val purchases: StateFlow<List<Purchase>> = _purchases.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { loadHistory() }

    fun loadHistory() {
        if (!container.isLoggedIn.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _purchases.value = container.historyService.fetchRecentPurchases(5)
                    .sortedByDescending { it.round }
            } catch (e: Exception) {
                _error.value = e.message  // 5b가 historyLoadError 템플릿으로 로컬라이즈
            } finally {
                _loading.value = false
            }
        }
    }
}

/** 설정: 로그인/로그아웃·잔액·자동구매 설정·언어·잔액알림·초기화. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val isLoggedIn = container.isLoggedIn
    val balance = container.balance
    val autoEnabled = container.autoEnabled
    val autoGames = container.autoGames
    val autoPurchaseDay = container.autoPurchaseDay
    val autoPurchaseHour = container.autoPurchaseHour
    val autoPurchaseMinute = container.autoPurchaseMinute
    val balanceAlertEnabled = container.balanceAlertEnabled
    val balanceAlertThreshold = container.balanceAlertThreshold
    val language = container.language
    val loggedInUserId = container.loggedInUserId

    /** 로그인 진행/결과 (스낵바·다이얼로그용). 원본 INVALID_CREDENTIALS 분기 유지. */
    sealed interface LoginState {
        data object Idle : LoginState
        data object InProgress : LoginState
        data object Success : LoginState
        data object InvalidCredentials : LoginState
        data object Error : LoginState
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    fun login(id: String, pw: String) {
        val u = id.trim()
        val p = pw.trim()
        if (u.isEmpty() || p.isEmpty()) return
        viewModelScope.launch {
            _loginState.value = LoginState.InProgress
            _loginState.value = try {
                container.login(u, p)
                LoginState.Success
            } catch (e: Exception) {
                if ((e.message ?: "").contains("INVALID_CREDENTIALS")) LoginState.InvalidCredentials
                else LoginState.Error
            }
        }
    }

    /** 스낵바 표시 후 상태 소비. */
    fun consumeLoginState() { _loginState.value = LoginState.Idle }

    fun logout() { viewModelScope.launch { container.logout() } }
    fun refreshBalance() { viewModelScope.launch { container.refreshBalance() } }

    fun setAutoEnabled(v: Boolean) { viewModelScope.launch { container.setAutoEnabled(v) } }

    /** 요일 변경. 구매 불가 시간이면 false 반환(원본 검증 스낵바). */
    fun setPurchaseDay(day: Int): Boolean {
        if (!isValidPurchaseTime(day, autoPurchaseHour.value)) return false
        viewModelScope.launch { container.setAutoPurchaseDay(day) }
        return true
    }

    /** 시간 변경. 구매 불가 시간이면 false 반환. */
    fun setPurchaseTime(hour: Int, minute: Int): Boolean {
        if (!isValidPurchaseTime(autoPurchaseDay.value, hour)) return false
        viewModelScope.launch { container.setAutoPurchaseTime(hour, minute) }
        return true
    }

    fun setLanguage(lang: String) { viewModelScope.launch { container.setLanguage(lang) } }
    fun setBalanceAlertEnabled(v: Boolean) { viewModelScope.launch { container.setBalanceAlertEnabled(v) } }
    fun setBalanceAlertThreshold(v: Int) { viewModelScope.launch { container.setBalanceAlertThreshold(v) } }
    fun resetAll() { viewModelScope.launch { container.resetAll() } }

    companion object {
        /**
         * 구매 가능 시간 (원본 `_isValidPurchaseTime`). day: 1=월 .. 7=일.
         * 토(6): 06:00~19:59, 그 외(평일/일): 06:00~23:59. (토 20:00~일 05:59 판매정지)
         */
        fun isValidPurchaseTime(day: Int, hour: Int): Boolean =
            if (day == 6) hour in 6..19 else hour >= 6
    }
}
