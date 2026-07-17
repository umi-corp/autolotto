# 즉시 구매 (Instant Purchase) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:test-driven-development로 태스크
> 단위 실행(사용자 파이프라인 지정). 스텝은 체크박스(`- [ ]`)로 추적한다.

**Goal:** 홈 화면에서 저장된 슬롯으로 현재 회차를 즉시 구매(첫 구매)하고, 이미 구매한
회차에는 자동 N게임 추가 구매를 제공한다. 스펙: `docs/DESIGN-instant-purchase.md`.

**Architecture:** 기존 `PurchaseService`(dhlottery 구매)와 앱 공유 세션을 재사용. 실행
경로는 `HomeScreen`(다이얼로그 UI) → `HomeViewModel`(InstantState 상태머신) →
`AppContainer.instantPurchase()`(구매 Mutex 임계구역: 모드별 가드 재판정 → 재로그인 →
구매 → 회차+계정 기록). 예약 `AutoPurchaseWorker`와 같은 프로세스이므로 프로세스 전역
`PurchaseLock.mutex`로 직렬화하고, 워커의 가드~기록 구간도 같은 락으로 감싼다.

**Tech Stack:** Kotlin/Jetpack Compose(M3), kotlinx.coroutines(`Mutex` — 기존 의존성),
EncryptedSharedPreferences(SecureStore), JUnit4(기존 테스트 스타일).

## Global Constraints

- Gradle 실행은 항상: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew <task>`
- 새 파일 금지(테스트 파일 제외), 새 의존성 금지.
- 문자열은 `values/`(ko) · `values-en/` · `values-ja/` 3개 파일에 항상 함께 추가.
- 확정 정책: 첫 구매=저장 슬롯 그대로(게임 수=비-null 슬롯 수, 0이면 번호 설정 유도) /
  추가 구매=자동 게임만 1~5 / 주간 한도는 서버 위임 / 자동구매 ON·OFF와 무관 /
  판매시간·회차는 KST(`Asia/Seoul`).
- 결제 안전: 자동 재시도 금지. 서버 확정 거절(`DhlotteryException`)은 메시지 그대로,
  구매 요청 후 그 외 예외는 "결과 불명"으로 표시(재시도 유도 금지).
- 커밋 메시지는 한국어 conventional commits (`feat(home): …`), 태스크마다 1커밋.
- 배포(버전 범프·릴리스)는 이 계획 범위 밖.

---

### Task 1: 문자열 리소스 (ko/en/ja)

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (말미 `</resources>` 직전)
- Modify: `app/src/main/res/values-en/strings.xml` (동일 위치)
- Modify: `app/src/main/res/values-ja/strings.xml` (동일 위치)

**Interfaces:**
- Produces: `R.string.buttonInstantPurchase`, `buttonExtraPurchase`, `instantNotSaleTime`,
  `instantConfirmTitle`, `instantConfirmBody(%1$d,%2$d,%3$s)`, `extraPickTitle`,
  `extraPickBody`, `instantInProgress`, `instantSuccessTitle`, `instantSuccessBody(%1$d,%2$d)`,
  `instantErrorTitle`, `instantUnknownResult`, `instantAlreadyPurchased`, `instantErrorFallback`
  — Task 7이 사용. 기존 키 재사용: `hintLoginRequired`, `buttonConfirm`, `buttonCancel`,
  `buttonSetupNumbers`.

- [ ] **Step 1: ko 추가** — `values/strings.xml`의 `</resources>` 직전에:

```xml
    <!-- 즉시 구매 -->
    <string name="buttonInstantPurchase">⚡ 지금 바로 구매</string>
    <string name="buttonExtraPurchase">➕ 추가 구매</string>
    <string name="instantNotSaleTime">지금은 판매시간이 아닙니다</string>
    <string name="instantConfirmTitle">즉시 구매</string>
    <string name="instantConfirmBody">제 %1$d회 · %2$d게임 · ₩%3$s\n지금 구매할까요?</string>
    <string name="extraPickTitle">추가 구매 (자동)</string>
    <string name="extraPickBody">자동 게임 수를 선택하세요. 주간 한도(5게임) 초과는 서버에서 거절됩니다.</string>
    <string name="instantInProgress">구매 중…</string>
    <string name="instantSuccessTitle">🎰 구매 완료!</string>
    <string name="instantSuccessBody">제 %1$d회 · %2$d게임</string>
    <string name="instantErrorTitle">구매 실패</string>
    <string name="instantUnknownResult">구매가 접수됐을 수 있습니다.\n내역 탭에서 확인 후 다시 시도해주세요.</string>
    <string name="instantAlreadyPurchased">방금 이 회차가 구매되었습니다. 추가 구매로 이용해주세요.</string>
    <string name="instantErrorFallback">구매에 실패했습니다. 다시 시도해주세요.</string>
```

- [ ] **Step 2: en 추가** — `values-en/strings.xml` 동일 위치에:

```xml
    <!-- Instant purchase -->
    <string name="buttonInstantPurchase">⚡ Buy Now</string>
    <string name="buttonExtraPurchase">➕ Buy More</string>
    <string name="instantNotSaleTime">Outside sale hours</string>
    <string name="instantConfirmTitle">Instant Purchase</string>
    <string name="instantConfirmBody">Round %1$d · %2$d game(s) · ₩%3$s\nBuy now?</string>
    <string name="extraPickTitle">Buy More (Auto)</string>
    <string name="extraPickBody">Choose how many auto games to buy. Requests over the weekly limit (5 games) are rejected by the server.</string>
    <string name="instantInProgress">Purchasing…</string>
    <string name="instantSuccessTitle">🎰 Purchase Complete!</string>
    <string name="instantSuccessBody">Round %1$d · %2$d game(s)</string>
    <string name="instantErrorTitle">Purchase Failed</string>
    <string name="instantUnknownResult">Your purchase may have gone through.\nCheck the History tab before trying again.</string>
    <string name="instantAlreadyPurchased">This round was just purchased. Use Buy More instead.</string>
    <string name="instantErrorFallback">Purchase failed. Please try again.</string>
```

- [ ] **Step 3: ja 추가** — `values-ja/strings.xml` 동일 위치에:

```xml
    <!-- 即時購入 -->
    <string name="buttonInstantPurchase">⚡ 今すぐ購入</string>
    <string name="buttonExtraPurchase">➕ 追加購入</string>
    <string name="instantNotSaleTime">現在は販売時間外です</string>
    <string name="instantConfirmTitle">即時購入</string>
    <string name="instantConfirmBody">第%1$d回 · %2$dゲーム · ₩%3$s\n今すぐ購入しますか?</string>
    <string name="extraPickTitle">追加購入 (自動)</string>
    <string name="extraPickBody">自動ゲーム数を選択してください。週間上限(5ゲーム)を超えるとサーバーで拒否されます。</string>
    <string name="instantInProgress">購入中…</string>
    <string name="instantSuccessTitle">🎰 購入完了!</string>
    <string name="instantSuccessBody">第%1$d回 · %2$dゲーム</string>
    <string name="instantErrorTitle">購入失敗</string>
    <string name="instantUnknownResult">購入が受け付けられた可能性があります。\n履歴タブで確認してから再試行してください。</string>
    <string name="instantAlreadyPurchased">この回はたった今購入されました。追加購入をご利用ください。</string>
    <string name="instantErrorFallback">購入に失敗しました。もう一度お試しください。</string>
```

- [ ] **Step 4: 리소스 검증**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew processDebugResources`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml app/src/main/res/values-ja/strings.xml
git commit -m "feat(i18n): 즉시 구매 문자열 (ko/en/ja)"
```

---

### Task 2: SecureStore 구매 계정 키

**Files:**
- Modify: `app/src/main/kotlin/com/umicorp/autolotto/data/SecureStore.kt`

**Interfaces:**
- Produces: `SecureStore.setLastPurchaseOwner(userId: String)`,
  `SecureStore.getLastPurchaseOwner(): String?` — Task 4(워커)·Task 5(컨테이너)가 사용.
- 주의: `SecureKeys.ALL`(이관 목록)에는 넣지 않는다 — `LAST_PURCHASED_ROUND`와 동일 취급.

- [ ] **Step 1: 키 상수 추가** — `SecureKeys`의 `LAST_PURCHASED_ROUND` 선언(주석 포함) 바로 아래에:

```kotlin
    /** 네이티브 전용 — 회차 가드의 계정 스코프(구매한 계정 ID). ALL(이관 목록) 미포함. */
    const val LAST_PURCHASE_OWNER = "last_purchase_owner"
```

- [ ] **Step 2: 게터/세터 추가** — `getLastPurchasedRound()` 바로 아래에:

```kotlin
    /** 구매 성공 시 회차와 함께 기록 — 다른 계정 로그인 시 가드 리셋 판정에 사용. */
    fun setLastPurchaseOwner(userId: String) = putString(SecureKeys.LAST_PURCHASE_OWNER, userId)

    fun getLastPurchaseOwner(): String? = prefs.getString(SecureKeys.LAST_PURCHASE_OWNER, null)
```

- [ ] **Step 3: 컴파일 확인**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/umicorp/autolotto/data/SecureStore.kt
git commit -m "feat(data): 회차 가드 계정 스코프 키 last_purchase_owner"
```

---

### Task 3: splitSlots 순수 함수 + 게이트 경계 단위테스트 (TDD)

**Files:**
- Modify: `app/src/main/kotlin/com/umicorp/autolotto/AppContainer.kt` (최상위 함수 추가)
- Create(Test): `app/src/test/kotlin/com/umicorp/autolotto/InstantPurchaseLogicTest.kt`

**Interfaces:**
- Consumes: `SettingsViewModel.isValidPurchaseTime(day, hour)` (기존, ViewModels.kt:235).
- Produces: 최상위 `splitSlots(slots: List<List<Int>?>): Pair<Int, List<List<Int>>>`
  (first=자동 게임 수, second=수동 번호 목록) — Task 5의 VM 스냅샷 변환이 사용.

- [ ] **Step 1: 실패하는 테스트 작성** — `InstantPurchaseLogicTest.kt` 생성:

```kotlin
package com.umicorp.autolotto

import com.umicorp.autolotto.ui.vm.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 즉시 구매 순수 로직: 슬롯 분해(splitSlots) + 판매시간 게이트 경계. */
class InstantPurchaseLogicTest {

    @Test
    fun `splitSlots - 빈 리스트는 자동, 채워진 리스트는 수동, null은 제외`() {
        val slots = listOf(
            listOf(1, 2, 3, 4, 5, 6),  // 수동
            emptyList(),                // 자동
            null,                       // 미설정
            emptyList(),                // 자동
            null,                       // 미설정
        )
        val (auto, manual) = splitSlots(slots)
        assertEquals(2, auto)
        assertEquals(listOf(listOf(1, 2, 3, 4, 5, 6)), manual)
    }

    @Test
    fun `splitSlots - 전부 null이면 0게임`() {
        val (auto, manual) = splitSlots(List(5) { null })
        assertEquals(0, auto)
        assertTrue(manual.isEmpty())
    }

    @Test
    fun `판매시간 게이트 - 토요일은 19시대까지, 20시부터 정지`() {
        assertTrue(SettingsViewModel.isValidPurchaseTime(day = 6, hour = 19))
        assertFalse(SettingsViewModel.isValidPurchaseTime(day = 6, hour = 20))
    }

    @Test
    fun `판매시간 게이트 - 매일 06시 전 정지, 06시부터 판매`() {
        assertFalse(SettingsViewModel.isValidPurchaseTime(day = 3, hour = 5))
        assertTrue(SettingsViewModel.isValidPurchaseTime(day = 3, hour = 6))
        assertTrue(SettingsViewModel.isValidPurchaseTime(day = 7, hour = 23)) // 일요일 밤 판매
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --tests "com.umicorp.autolotto.InstantPurchaseLogicTest"`
Expected: FAIL — `Unresolved reference: splitSlots` (컴파일 에러 = red)

- [ ] **Step 3: 구현** — `AppContainer.kt` 파일 말미(최상위, `appContainer` 확장 프로퍼티 아래)에:

```kotlin
/** 5슬롯(null=미설정 / 빈=자동 / 6수=수동) → (자동 게임 수, 수동 번호 목록). 첫 구매 변환. */
fun splitSlots(slots: List<List<Int>?>): Pair<Int, List<List<Int>>> =
    slots.count { it?.isEmpty() == true } to slots.filterNotNull().filter { it.isNotEmpty() }
```

- [ ] **Step 4: 통과 확인**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --tests "com.umicorp.autolotto.InstantPurchaseLogicTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/umicorp/autolotto/AppContainer.kt app/src/test/kotlin/com/umicorp/autolotto/InstantPurchaseLogicTest.kt
git commit -m "feat(core): splitSlots 슬롯 분해 + 판매시간 게이트 경계 테스트"
```

---

### Task 4: PurchaseLock + 워커 임계구역 (가드~기록 직렬화)

**Files:**
- Modify: `app/src/main/kotlin/com/umicorp/autolotto/scheduler/AutoPurchaseWorker.kt`

**Interfaces:**
- Produces: `object PurchaseLock { val mutex: Mutex }` (scheduler 패키지 최상위) — Task 5가 사용.
- 워커 동작 계약 불변: 가드 스킵/성공 알림/재시도 매핑/알람 재등록 모두 기존과 동일.
  달라지는 것은 ① 가드 판정~회차 기록이 `PurchaseLock.mutex` 임계구역이 된 것,
  ② 성공 시 `setLastPurchaseOwner(userId)` 1줄 추가.

- [ ] **Step 1: import + PurchaseLock 추가** — 파일 상단 import에
  `kotlinx.coroutines.sync.Mutex`, `kotlinx.coroutines.sync.withLock` 추가 후,
  `AutoPurchaseWorker` 클래스 선언 위에:

```kotlin
/**
 * 구매 직렬화 락 — 예약 워커와 즉시 구매(AppContainer)가 같은 앱 프로세스에서 공유.
 * "가드 판정~구매 실행~회차 기록"이 임계구역: 기록 전에 풀면 상대가 이전 회차 값을 읽는다.
 * 잔액 조회·알림은 락 밖.
 */
object PurchaseLock {
    val mutex = Mutex()
}
```

- [ ] **Step 2: executeAutoPurchase 임계구역 재구성** — 본문을 다음으로 교체
  (파싱·로그인·구매·기록을 `withLock` 안으로, 잔액·알림은 밖으로. step 매핑·주석 유지):

```kotlin
    /** 원본 `_executeAutoPurchase` 1:1. 실패 시 `[step] 메시지`로 감싸 던진다(doWork의 매핑이 사용). */
    private suspend fun executeAutoPurchase() {
        var step = "init"
        try {
            step = "read_credentials"
            val cred = store.getCredentials()
            val userId = cred.userId
            val password = cred.password
            val autoEnabled = store.getAutoEnabled()
            val games = store.getAutoGames()

            if (!autoEnabled || userId == null || password == null) return
            if (games == 0) return

            val session = DhlotterySession()
            val auth = AuthService(session)

            // 회차 멱등 가드~회차 기록 = 즉시 구매와 공유하는 임계구역(PurchaseLock).
            // Worker 재실행·중복 알람·홈 즉시 구매가 겹쳐도 같은 회차를 두 번 사지 않는다.
            step = "round_guard"
            val result = PurchaseLock.mutex.withLock {
                val currentRound = PurchaseService.getCurrentRound()
                if (store.getLastPurchasedRound() >= currentRound) return

                step = "parse_numbers"
                val manualJson = store.getManualNumbers()
                val manualNumbers = mutableListOf<List<Int>>()
                var autoGames = 0
                try {
                    val parsed = JSONArray(manualJson)
                    for (i in 0 until parsed.length()) {
                        if (parsed.isNull(i)) continue                 // 미설정 슬롯 — 스킵
                        val g = parsed.optJSONArray(i) ?: continue
                        if (g.length() > 0) {                          // 수동 게임
                            manualNumbers.add((0 until g.length()).map { g.getInt(it) })
                        } else {                                       // 빈 배열 = 자동 게임
                            autoGames++
                        }
                    }
                } catch (e: Exception) {
                    manualNumbers.clear()
                    autoGames = games                                  // 파싱 실패 시 전부 자동(원본 폴백)
                }

                if (autoGames == 0 && manualNumbers.isEmpty()) return

                step = "login"
                auth.login(userId, password)

                step = "purchase"
                val purchaseService = PurchaseService(auth, session)
                val r = try {
                    purchaseService.purchase(autoGames = autoGames, manualNumbers = manualNumbers)
                } catch (purchaseError: Exception) {
                    throw Exception(purchaseError.message ?: "$purchaseError")
                }

                // 성공 즉시 회차+계정 기록(commit) — 이후 재실행은 round_guard가 차단.
                // ponytail: 서버 처리~기록 사이 찰나에 킬되는 창은 남음 — 완전 차단은 구매내역 대조 필요.
                store.setLastPurchasedRound(r.round)
                store.setLastPurchaseOwner(userId)
                r
            }

            // 구매 후 잔액 체크 (락 밖, 실패 무시 — 원본 catch (_) {})
            runCatching {
                val postBalance = auth.getBalance()
                BalanceAlert.checkAndNotify(ctx, postBalance)
            }

            step = "notify"
            val numbersText = result.numbers.mapIndexed { idx, nums ->
                "${'A' + idx}: ${nums.joinToString(",")}"
            }.joinToString("\n")

            Notifications.show(
                ctx,
                "🎰 로또 자동 구매 완료!",
                "제 ${result.round}회 · ${result.totalGames}게임\n$numbersText",
                1,
                tab = Notifications.TAB_HISTORY,
            )
        } catch (e: Exception) {
            throw Exception("[$step] ${e.message ?: e}")
        }
    }
```

- [ ] **Step 3: 컴파일 + 기존 테스트 회귀**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 기존 테스트 전부 PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/umicorp/autolotto/scheduler/AutoPurchaseWorker.kt
git commit -m "feat(scheduler): 구매 직렬화 PurchaseLock — 워커 가드~기록 임계구역"
```

---

### Task 5: AppContainer — lastPurchasedRound Flow · 계정 스코프 · instantPurchase

**Files:**
- Modify: `app/src/main/kotlin/com/umicorp/autolotto/AppContainer.kt`

**Interfaces:**
- Consumes: `PurchaseLock.mutex`(Task 4), `store.setLastPurchaseOwner/getLastPurchaseOwner`(Task 2).
- Produces (Task 6이 사용):
  - `val lastPurchasedRound: StateFlow<Int>`
  - `suspend fun refreshLastPurchasedRound()`
  - `suspend fun instantPurchase(extra: Boolean, autoGames: Int, manualNumbers: List<List<Int>>): PurchaseResult?`
    — null = 첫 구매 모드인데 이미 구매된 회차(워커 선점). 서버 확정 거절은
    `DhlotteryException` 그대로, 구매 요청 후 그 외 예외는 `PurchaseResultUnknownException`.
  - `class PurchaseResultUnknownException(cause: Throwable)`

- [ ] **Step 1: import 추가**

```kotlin
import com.umicorp.autolotto.dhlottery.DhlotteryException
import com.umicorp.autolotto.dhlottery.PurchaseResult
import com.umicorp.autolotto.dhlottery.PurchaseService
import com.umicorp.autolotto.scheduler.PurchaseLock
import kotlinx.coroutines.sync.withLock
```

- [ ] **Step 2: StateFlow 추가** — `_loggedInUserId` 선언 아래에:

```kotlin
    /** 마지막 구매 회차(멱등 가드) — 즉시 구매 CTA 모드(첫/추가) 분기의 단일 출처. */
    private val _lastPurchasedRound = MutableStateFlow(0)
    val lastPurchasedRound: StateFlow<Int> = _lastPurchasedRound.asStateFlow()
```

- [ ] **Step 3: 하이드레이션·재읽기** — `loadSettings()` 본문의 `_loggedInUserId.value = …` 아래에
  `_lastPurchasedRound.value = store.getLastPurchasedRound()` 1줄 추가하고, `refreshBalance()` 아래에:

```kotlin
    /** 워커가 백그라운드에서 회차를 갱신했을 수 있어 홈 새로고침 때 재읽기. */
    suspend fun refreshLastPurchasedRound() = withContext(Dispatchers.IO) {
        _lastPurchasedRound.value = store.getLastPurchasedRound()
    }
```

- [ ] **Step 4: 로그인 시 계정 스코프 리셋** — `login()`의 `store.saveCredentials(id, pw)` 아래에:

```kotlin
        // 회차 가드는 계정의 기록 — 다른 계정 로그인 시 무효화(같은 계정 재로그인은 보존).
        val owner = store.getLastPurchaseOwner()
        if (owner != null && owner != id) {
            store.setLastPurchasedRound(0)
            _lastPurchasedRound.value = 0
        }
```

- [ ] **Step 5: instantPurchase + 예외 타입** — `refreshLastPurchasedRound()` 아래에:

```kotlin
    // === 즉시 구매 (홈 CTA — 스펙 docs/DESIGN-instant-purchase.md) ===

    /** 구매 요청 후 결과를 확인 못 한 실패(네트워크·타임아웃) — 재시도 유도 금지 신호. */
    class PurchaseResultUnknownException(cause: Throwable) : Exception(cause)

    /**
     * 즉시 구매. [extra]=false(첫 구매: 저장 슬롯)는 Mutex 안에서 회차 가드를 재판정해 이미
     * 구매된 회차면 null 반환(= "방금 구매됨"). [extra]=true(추가: 자동 N게임)는 가드로 막지
     * 않는다(구매 완료가 정상 전제, 방어선은 서버 주간한도). 세션 만료 대비 매번 재로그인
     * (워커 패턴). 성공 즉시 회차+계정 기록까지가 임계구역, 잔액 갱신은 락 밖(실패 무시).
     */
    suspend fun instantPurchase(
        extra: Boolean,
        autoGames: Int,
        manualNumbers: List<List<Int>>,
    ): PurchaseResult? {
        val result = PurchaseLock.mutex.withLock {
            val round = PurchaseService.getCurrentRound()
            val recorded = store.getLastPurchasedRound()
            _lastPurchasedRound.value = recorded
            if (!extra && recorded >= round) return@withLock null   // 워커 선점 — 이미 구매됨

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
            store.setLastPurchasedRound(r.round)
            store.setLastPurchaseOwner(id)
            _lastPurchasedRound.value = r.round
            r
        } ?: return null
        runCatching { refreshBalance() }                            // 락 밖 — 실패해도 성공 표시 유지
        return result
    }
```

- [ ] **Step 6: 컴파일 + 테스트**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, 전부 PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/umicorp/autolotto/AppContainer.kt
git commit -m "feat(core): instantPurchase — Mutex 임계구역·모드 분기·계정 스코프 가드"
```

---

### Task 6: HomeViewModel — InstantState 상태머신

**Files:**
- Modify: `app/src/main/kotlin/com/umicorp/autolotto/ui/vm/ViewModels.kt` (HomeViewModel)

**Interfaces:**
- Consumes: `container.instantPurchase(extra, autoGames, manualNumbers): PurchaseResult?`,
  `container.lastPurchasedRound`, `container.refreshLastPurchasedRound()`,
  `container.loadManualGames()`, `splitSlots(...)`(Task 3),
  `AppContainer.PurchaseResultUnknownException`(Task 5),
  `SettingsViewModel.isValidPurchaseTime`(기존).
- Produces (Task 7이 사용): `HomeViewModel.InstantState`(아래 정의 그대로),
  `val instantState: StateFlow<InstantState>`, `val lastPurchasedRound: StateFlow<Int>`,
  `fun isSaleOpenNow(): Boolean`, `fun onInstantTap()`, `fun confirmFirst()`,
  `fun confirmExtra(games: Int)`, `fun dismissInstant()`.

- [ ] **Step 1: import 추가** — 파일 상단에:

```kotlin
import com.umicorp.autolotto.dhlottery.PurchaseResult
import com.umicorp.autolotto.splitSlots
import java.time.ZoneId
import java.time.ZonedDateTime
```

- [ ] **Step 2: HomeViewModel에 상태머신 추가** — `refreshAll()` 위에 다음을 삽입하고,
  `refreshAll()`에는 `viewModelScope.launch { runCatching { container.refreshLastPurchasedRound() } }`
  1줄을 추가:

```kotlin
    val lastPurchasedRound = container.lastPurchasedRound

    /**
     * 즉시 구매 다이얼로그 상태머신.
     * Idle → (탭) ConfirmingFirst | PickingExtra | NeedsSetup
     *      → (확정) InProgress → Success | AlreadyPurchased | SaleClosed | Error → (닫기) Idle
     * ConfirmingFirst는 탭 시점 슬롯 스냅샷을 담아 확인창 표시 내용 = 실제 구매 내용을 보장.
     */
    sealed interface InstantState {
        data object Idle : InstantState
        data object NeedsSetup : InstantState
        data class ConfirmingFirst(
            val round: Int,
            val autoGames: Int,
            val manualNumbers: List<List<Int>>,
        ) : InstantState {
            val games: Int get() = autoGames + manualNumbers.size
        }
        data class PickingExtra(val round: Int) : InstantState
        data object InProgress : InstantState
        data object AlreadyPurchased : InstantState   // 첫 구매 확정 직전 워커 선점
        data object SaleClosed : InstantState         // 확정 시점 판매시간 재검증 실패
        data class Success(val result: PurchaseResult) : InstantState
        data class Error(val message: String?, val unknown: Boolean) : InstantState
    }

    private val _instantState = MutableStateFlow<InstantState>(InstantState.Idle)
    val instantState: StateFlow<InstantState> = _instantState.asStateFlow()

    /** 지금(KST)이 판매시간인지 — CTA 표시용. 확정 시점에도 재검증한다. */
    fun isSaleOpenNow(): Boolean {
        val now = ZonedDateTime.now(KST)
        return SettingsViewModel.isValidPurchaseTime(now.dayOfWeek.value, now.hour)
    }

    /** CTA 탭: 게이트 재검증 후 모드 분기(첫 구매/추가/설정 유도). */
    fun onInstantTap() {
        if (_instantState.value != InstantState.Idle) return
        viewModelScope.launch {
            if (!isSaleOpenNow()) return@launch                     // 표시가 stale했던 경우 — 무동작
            runCatching { container.refreshLastPurchasedRound() }
            val round = PurchaseService.getCurrentRound()
            if (container.lastPurchasedRound.value >= round) {
                _instantState.value = InstantState.PickingExtra(round)
                return@launch
            }
            val (auto, manual) = splitSlots(container.loadManualGames())
            _instantState.value = if (auto + manual.size == 0) InstantState.NeedsSetup
            else InstantState.ConfirmingFirst(round, auto, manual)
        }
    }

    /** 첫 구매 확정 — 탭 시점 스냅샷 그대로 실행. 최종 가드 재판정은 컨테이너 Mutex 안. */
    fun confirmFirst() {
        val s = _instantState.value as? InstantState.ConfirmingFirst ?: return
        launchPurchase { container.instantPurchase(extra = false, autoGames = s.autoGames, manualNumbers = s.manualNumbers) }
    }

    /** 추가 구매 확정 — 자동 [games]게임. 가드로 막지 않음(서버 한도 위임). */
    fun confirmExtra(games: Int) {
        if (_instantState.value !is InstantState.PickingExtra) return
        launchPurchase { container.instantPurchase(extra = true, autoGames = games, manualNumbers = emptyList()) }
    }

    fun dismissInstant() {
        if (_instantState.value == InstantState.InProgress) return  // 진행 중 닫기 금지
        _instantState.value = InstantState.Idle
    }

    private fun launchPurchase(block: suspend () -> PurchaseResult?) {
        if (_instantState.value == InstantState.InProgress) return  // 중복 확정 no-op
        if (!isSaleOpenNow()) {                                     // 확정 시점 판매시간 재검증
            _instantState.value = InstantState.SaleClosed
            return
        }
        _instantState.value = InstantState.InProgress
        viewModelScope.launch {
            _instantState.value = try {
                val r = block()
                if (r == null) InstantState.AlreadyPurchased else InstantState.Success(r)
            } catch (e: AppContainer.PurchaseResultUnknownException) {
                InstantState.Error(message = null, unknown = true)
            } catch (e: Exception) {
                InstantState.Error(message = e.message, unknown = false)
            }
        }
    }
```

- [ ] **Step 3: KST 상수** — HomeViewModel 클래스 말미에:

```kotlin
    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
```

- [ ] **Step 4: 컴파일**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/umicorp/autolotto/ui/vm/ViewModels.kt
git commit -m "feat(home): 즉시 구매 InstantState 상태머신"
```

---

### Task 7: HomeScreen — CTA·다이얼로그 UI

**Files:**
- Modify: `app/src/main/kotlin/com/umicorp/autolotto/ui/screen/HomeScreen.kt`

**Interfaces:**
- Consumes: Task 1 문자열, Task 6 `HomeViewModel.InstantState`/액션,
  기존 `CtaButton(onClick, enabled, content)`·`LottoBall(n, size)`(Components.kt),
  `formatNumber`(ui/util).

- [ ] **Step 1: import 추가**

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import com.umicorp.autolotto.ui.vm.HomeViewModel.InstantState
```

- [ ] **Step 2: 상태 수집** — `HomeScreen()`의 `val loadingNumbers by …` 아래에:

```kotlin
    val lastRound by vm.lastPurchasedRound.collectAsState()
    val instantState by vm.instantState.collectAsState()
```

- [ ] **Step 3: CTA 교체** — 기존 `CtaButton(onClick = onNavigateToNumbers) { … }` 블록
  전체를 다음으로 교체 (즉시구매 프라이머리 + 번호설정 아웃라인):

```kotlin
                InstantPurchaseCta(
                    isLoggedIn = isLoggedIn,
                    purchasedThisRound = lastRound >= vm.currentRound,
                    saleOpen = vm.isSaleOpenNow(),
                    onTap = { vm.onInstantTap() },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onNavigateToNumbers, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.buttonSetupNumbers), fontWeight = FontWeight.Bold)
                }
```

- [ ] **Step 4: 다이얼로그 + 미설정 이동 효과** — `HomeScreen()`에서 `Scaffold(...)` 호출 앞에:

```kotlin
    LaunchedEffect(instantState) {
        if (instantState is InstantState.NeedsSetup) {   // 슬롯 0게임 — 번호 설정으로 유도
            vm.dismissInstant()
            onNavigateToNumbers()
        }
    }
    InstantPurchaseDialogs(instantState, vm)
```

- [ ] **Step 5: CTA·다이얼로그 컴포저블** — 파일 말미에 추가:

```kotlin
/** 즉시 구매 프라이머리 CTA. 비활성 사유(로그인 > 판매시간)를 라벨로 표시, 구매완료 회차엔 "추가 구매". */
@Composable
private fun InstantPurchaseCta(
    isLoggedIn: Boolean,
    purchasedThisRound: Boolean,
    saleOpen: Boolean,
    onTap: () -> Unit,
) {
    val disabledLabel = when {
        !isLoggedIn -> stringResource(R.string.hintLoginRequired)
        !saleOpen -> stringResource(R.string.instantNotSaleTime)
        else -> null
    }
    CtaButton(onClick = onTap, enabled = disabledLabel == null) {
        Text(
            disabledLabel ?: stringResource(
                if (purchasedThisRound) R.string.buttonExtraPurchase else R.string.buttonInstantPurchase,
            ),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 즉시 구매 다이얼로그 — InstantState별 확인/게임수선택/진행/결과/에러. */
@Composable
private fun InstantPurchaseDialogs(state: HomeViewModel.InstantState, vm: HomeViewModel) {
    when (state) {
        is InstantState.ConfirmingFirst -> AlertDialog(
            onDismissRequest = { vm.dismissInstant() },
            title = { Text(stringResource(R.string.instantConfirmTitle)) },
            text = {
                Text(
                    stringResource(
                        R.string.instantConfirmBody,
                        state.round, state.games, formatNumber(state.games * 1000),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmFirst() }) { Text(stringResource(R.string.buttonConfirm)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissInstant() }) { Text(stringResource(R.string.buttonCancel)) }
            },
        )
        is InstantState.PickingExtra -> {
            var games by remember { mutableIntStateOf(1) }
            AlertDialog(
                onDismissRequest = { vm.dismissInstant() },
                title = { Text(stringResource(R.string.extraPickTitle)) },
                text = {
                    Column {
                        Text(stringResource(R.string.extraPickBody), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..5).forEach { n ->
                                FilterChip(selected = games == n, onClick = { games = n }, label = { Text("$n") })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(
                                R.string.instantConfirmBody,
                                state.round, games, formatNumber(games * 1000),
                            ),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.confirmExtra(games) }) { Text(stringResource(R.string.buttonConfirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { vm.dismissInstant() }) { Text(stringResource(R.string.buttonCancel)) }
                },
            )
        }
        is InstantState.InProgress -> AlertDialog(
            onDismissRequest = {},                                  // 진행 중 닫기 금지
            title = { Text(stringResource(R.string.instantConfirmTitle)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.instantInProgress))
                }
            },
            confirmButton = {},
        )
        is InstantState.Success -> AlertDialog(
            onDismissRequest = { vm.dismissInstant() },
            title = { Text(stringResource(R.string.instantSuccessTitle)) },
            text = {
                Column {
                    Text(stringResource(R.string.instantSuccessBody, state.result.round, state.result.totalGames))
                    Spacer(Modifier.height(12.dp))
                    state.result.numbers.forEach { game ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(vertical = 2.dp),
                        ) {
                            game.forEach { n -> LottoBall(n, size = 28.dp) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissInstant() }) { Text(stringResource(R.string.buttonConfirm)) }
            },
        )
        is InstantState.AlreadyPurchased -> AlertDialog(
            onDismissRequest = { vm.dismissInstant() },
            title = { Text(stringResource(R.string.instantConfirmTitle)) },
            text = { Text(stringResource(R.string.instantAlreadyPurchased)) },
            confirmButton = {
                TextButton(onClick = { vm.dismissInstant() }) { Text(stringResource(R.string.buttonConfirm)) }
            },
        )
        is InstantState.SaleClosed -> AlertDialog(
            onDismissRequest = { vm.dismissInstant() },
            title = { Text(stringResource(R.string.instantConfirmTitle)) },
            text = { Text(stringResource(R.string.instantNotSaleTime)) },
            confirmButton = {
                TextButton(onClick = { vm.dismissInstant() }) { Text(stringResource(R.string.buttonConfirm)) }
            },
        )
        is InstantState.Error -> AlertDialog(
            onDismissRequest = { vm.dismissInstant() },
            title = { Text(stringResource(R.string.instantErrorTitle)) },
            text = {
                Text(
                    if (state.unknown) stringResource(R.string.instantUnknownResult)
                    else state.message ?: stringResource(R.string.instantErrorFallback),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissInstant() }) { Text(stringResource(R.string.buttonConfirm)) }
            },
        )
        InstantState.Idle, InstantState.NeedsSetup -> Unit
    }
}
```

- [ ] **Step 6: 전체 빌드**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/umicorp/autolotto/ui/screen/HomeScreen.kt
git commit -m "feat(home): 즉시 구매 CTA·확인/추가구매/결과 다이얼로그"
```

---

### Task 8: 검증 (스펙 검증 섹션 실측)

**Files:** 변경 없음 (검증만)

- [ ] **Step 1: 단위테스트 + 빌드**

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest assembleDebug`
Expected: 전부 PASS, BUILD SUCCESSFUL

- [ ] **Step 2: 에뮬레이터 설치** — AVD `autolotto` 기동 후:

Run: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug`
Expected: Installed on 1 device

- [ ] **Step 3: 상태 확인 (스크린샷 근거 수집)**
  - 비로그인: CTA 비활성 + "로그인 후 이용 가능"
  - 로그인 + 슬롯 0게임: CTA 활성 → 탭 시 번호 설정 화면 이동
  - 로그인 + 슬롯 설정: CTA "⚡ 지금 바로 구매" → 탭 → 확인 다이얼로그(회차·게임·금액) → 취소
  - `adb shell su`가 불가하므로 판매시간 외 상태는 기기 시간 변조(설정 > 날짜/시간 수동,
    새벽 3시)로 확인: CTA 비활성 + "지금은 판매시간이 아닙니다" — 확인 후 원복
  - 구매완료 상태 전환: 실결제 없이 확인하려면 에뮬레이터에서 로그인 후
    `lastPurchasedRound`를 만들 수 없으므로, 이 항목은 실계정 E2E(아래)와 묶어 확인
  - 프로세스 재생성: 확인 다이얼로그 연 채 개발자옵션 "액티비티 유지 안함" 또는
    `adb shell am kill com.umicorp.autolotto` 후 재진입 → 다이얼로그 미복원(Idle) 확인
  - 에뮬레이터 종료(배터리)
- [ ] **Step 4: 실결제 E2E는 이 계획 밖** — 사용자 실계정 필요(첫 구매 1게임 ₩1,000 →
  같은 회차 "추가 구매" 전환 → 내역 탭 확인). 배포 전 사용자와 별도 협의해 진행.
