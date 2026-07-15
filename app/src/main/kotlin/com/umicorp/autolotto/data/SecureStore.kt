package com.umicorp.autolotto.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 보안저장 키 문자열 — Flutter `SecureStorageService`와 1:1.
 * 오타 방지용 단일 출처. 백그라운드(AlarmManager 리시버)가 raw 문자열로 읽는 키도 전부 여기서 나온다.
 */
object SecureKeys {
    const val USER_ID = "dhlottery_user_id"
    const val PASSWORD = "dhlottery_password"
    const val AUTO_ENABLED = "auto_purchase_enabled"
    const val AUTO_GAMES = "auto_games"
    const val MANUAL_NUMBERS = "manual_numbers"
    const val AUTO_PURCHASE_DAY = "auto_purchase_day"
    const val AUTO_PURCHASE_HOUR = "auto_purchase_hour"
    const val AUTO_PURCHASE_MINUTE = "auto_purchase_minute"
    const val LANGUAGE = "app_language"
    const val BALANCE_ALERT_ENABLED = "balance_alert_enabled"
    const val BALANCE_ALERT_THRESHOLD = "balance_alert_threshold"
    const val BALANCE_ALERT_LAST_DATE = "balance_alert_last_date"

    /** 네이티브 전용(Flutter에 없던 키) — 자동구매 중복 결제 방지용 마지막 구매 회차. ALL(이관 목록) 미포함. */
    const val LAST_PURCHASED_ROUND = "last_purchased_round"

    /** 마이그레이션·일괄 처리용 전체 키 목록. */
    val ALL = listOf(
        USER_ID, PASSWORD, AUTO_ENABLED, AUTO_GAMES, MANUAL_NUMBERS,
        AUTO_PURCHASE_DAY, AUTO_PURCHASE_HOUR, AUTO_PURCHASE_MINUTE, LANGUAGE,
        BALANCE_ALERT_ENABLED, BALANCE_ALERT_THRESHOLD, BALANCE_ALERT_LAST_DATE,
    )
}

/**
 * 보안저장소 (자격증명·자동구매설정·수동번호·알림설정 영속화) — Flutter `SecureStorageService` 포트.
 *
 * 영속 진실의 출처. EncryptedSharedPreferences(Android Keystore 암호화)를 쓴다:
 * MasterKey=AES256_GCM, 키=AES256_SIV, 값=AES256_GCM.
 *
 * Flutter와 1:1 충실도 규칙:
 * - 모든 값을 **문자열**로 저장(불리언="true"/"false", 정수=10진 문자열). 원본 flutter_secure_storage가
 *   문자열만 저장했고, 백그라운드가 raw 키로 읽어 같은 방식으로 파싱하던 패턴을 그대로 유지하기 위함.
 * - 기본값/파싱 규칙도 원본 getter와 동일(미설정 시 day=7, hour=9, minute=0, threshold=5000,
 *   manual_numbers="[]", language="system" 등).
 *
 * 백그라운드 리시버도 같은 `Context`로 인스턴스를 만들어 직접 읽는다(원본의 백그라운드 isolate 직접읽기 패턴 유지).
 */
class SecureStore(context: Context) {

    private val appContext = context.applicationContext

    @Suppress("DEPRECATION") // security-crypto는 deprecated지만 표준·동작 (DESIGN §6). 제거 시 Tink/Keystore.
    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = createPrefs(PREFS_FILE)

    init {
        migrateFromFlutterIfNeeded()
    }

    @Suppress("DEPRECATION")
    private fun createPrefs(fileName: String): SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        fileName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /**
     * 구 Flutter 앱(flutter_secure_storage, encrypted 모드)에서 1회 이관.
     *
     * flutter_secure_storage의 encrypted 모드 = 동일한 Jetpack EncryptedSharedPreferences + 기본 MasterKey +
     * AES256_SIV/AES256_GCM 스킴이고, 파일명만 "FlutterSecureStorage"로 다르다. 그래서 같은 MasterKey/스킴으로
     * 그 파일을 열면 기존 암호값을 그대로 읽을 수 있다. 같은 applicationId로 업데이트하는 기존 사용자의
     * 자격증명·자동구매설정·수동번호가 유실(→ 자동구매 조용히 중단)되지 않게 한다.
     *
     * 안전장치: 플래그로 1회만 / 구 파일 없으면 skip(신규설치) / 읽기 실패(스킴 불일치·손상)는 try-catch로 무시
     * (크래시 없이 신규 상태로 진행). EncryptedSharedPreferences는 Keystore 의존 → 계측 테스트 영역(JVM 불가).
     * 이관 시도 후에는 구 저장소 파일을 삭제한다(로그아웃 후 자격증명 잔존 방지) — 이미 이관된 기기도 잔존 파일이 있으면 정리.
     */
    private fun migrateFromFlutterIfNeeded() {
        // 프로세스 락: AppContainer와 백그라운드 Worker가 SecureStore를 동시 생성해도 1회만.
        synchronized(MIGRATION_LOCK) {
            if (prefs.contains(MIGRATION_FLAG)) {
                // 이미 이관 완료 — 잔존하는 구 파일만 정리(로그아웃·초기화가 지우지 못하는 자격증명 사본 제거).
                // exists() 게이트 없이 항상 호출: 중단된 쓰기가 남긴 .bak까지 프레임워크가 지워준다(없으면 no-op).
                deleteLegacyStore()
                return
            }
            runCatching {
                val legacyFile = java.io.File(appContext.dataDir, "shared_prefs/$FLUTTER_PREFS_FILE.xml")
                if (legacyFile.exists()) {
                    val legacy = createPrefs(FLUTTER_PREFS_FILE)
                    val editor = prefs.edit()
                    for (key in SecureKeys.ALL) {
                        if (prefs.contains(key)) continue // 네이티브에 이미 있는 값은 절대 덮어쓰지 않음(사용자 변경 보존)
                        // flutter_secure_storage는 모든 키에 고정 접두사를 붙여 저장 → 접두사 포함해 읽고, 네이티브엔 논리키로 저장.
                        runCatching { legacy.getString(FLUTTER_KEY_PREFIX + key, null) }
                            .getOrNull()?.let { editor.putString(key, it) }
                    }
                    editor.commit()
                }
            }
            // 성공/실패/구파일없음 모두 플래그 set → 1회성 보장(재시도 루프·재덮어쓰기 방지).
            prefs.edit().putBoolean(MIGRATION_FLAG, true).commit()
            // 이관 시도 후 구 저장소 삭제 — 플래그가 재읽기를 막으므로 남겨둘 가치가 없고,
            // 남겨두면 로그아웃·전체초기화(clearAll)가 지우지 못하는 자격증명 사본이 된다.
            deleteLegacyStore()
        }
    }

    /** 구 Flutter 보안저장소 삭제(파일+메모리 캐시). 실패는 무시 — 파일이 남으면 다음 앱 시작에서 재시도. */
    private fun deleteLegacyStore() {
        runCatching { appContext.deleteSharedPreferences(FLUTTER_PREFS_FILE) }
    }

    // === 계정 ===

    fun saveCredentials(userId: String, password: String) {
        // money-path: commit()으로 즉시 디스크 영속화 (원본 await write 의미 유지, 로그인 직후 강제종료에도 자격증명 보존).
        prefs.edit()
            .putString(SecureKeys.USER_ID, userId)
            .putString(SecureKeys.PASSWORD, password)
            .commit()
    }

    fun getCredentials(): Credentials = Credentials(
        userId = prefs.getString(SecureKeys.USER_ID, null),
        password = prefs.getString(SecureKeys.PASSWORD, null),
    )

    fun hasCredentials(): Boolean {
        val c = getCredentials()
        return c.userId != null && c.password != null
    }

    fun deleteCredentials() {
        prefs.edit()
            .remove(SecureKeys.USER_ID)
            .remove(SecureKeys.PASSWORD)
            .commit()
    }

    // === 자동 구매 설정 ===

    fun setAutoEnabled(enabled: Boolean) = putString(SecureKeys.AUTO_ENABLED, enabled.toString())

    fun getAutoEnabled(): Boolean = prefs.getString(SecureKeys.AUTO_ENABLED, null) == "true"

    fun setAutoGames(games: Int) = putString(SecureKeys.AUTO_GAMES, games.toString())

    /** int.tryParse(val ?? '') ?? 0 와 동일. */
    fun getAutoGames(): Int = prefs.getString(SecureKeys.AUTO_GAMES, null)?.toIntOrNull() ?: 0

    /** 수동 번호 저장 (JSON 5슬롯 문자열). */
    fun setManualNumbers(json: String) = putString(SecureKeys.MANUAL_NUMBERS, json)

    /** 미설정 시 "[]" (원본 기본값). */
    fun getManualNumbers(): String = prefs.getString(SecureKeys.MANUAL_NUMBERS, null) ?: "[]"

    // === 구매 시간 설정 ===

    /** 구매 요일 (1=월 ~ 7=일). */
    fun setAutoPurchaseDay(day: Int) = putString(SecureKeys.AUTO_PURCHASE_DAY, day.toString())

    fun getAutoPurchaseDay(): Int = prefs.getString(SecureKeys.AUTO_PURCHASE_DAY, null)?.toIntOrNull() ?: 7

    fun setAutoPurchaseHour(hour: Int) = putString(SecureKeys.AUTO_PURCHASE_HOUR, hour.toString())

    fun getAutoPurchaseHour(): Int = prefs.getString(SecureKeys.AUTO_PURCHASE_HOUR, null)?.toIntOrNull() ?: 9

    fun setAutoPurchaseMinute(minute: Int) = putString(SecureKeys.AUTO_PURCHASE_MINUTE, minute.toString())

    fun getAutoPurchaseMinute(): Int = prefs.getString(SecureKeys.AUTO_PURCHASE_MINUTE, null)?.toIntOrNull() ?: 0

    // === 언어 설정 ===

    fun setLanguage(lang: String) = putString(SecureKeys.LANGUAGE, lang)

    fun getLanguage(): String = prefs.getString(SecureKeys.LANGUAGE, null) ?: "system"

    // === 잔액 부족 알림 ===

    fun setBalanceAlertEnabled(enabled: Boolean) = putString(SecureKeys.BALANCE_ALERT_ENABLED, enabled.toString())

    fun getBalanceAlertEnabled(): Boolean = prefs.getString(SecureKeys.BALANCE_ALERT_ENABLED, null) == "true"

    fun setBalanceAlertThreshold(threshold: Int) = putString(SecureKeys.BALANCE_ALERT_THRESHOLD, threshold.toString())

    fun getBalanceAlertThreshold(): Int =
        prefs.getString(SecureKeys.BALANCE_ALERT_THRESHOLD, null)?.toIntOrNull() ?: 5000

    fun setBalanceAlertLastDate(date: String) = putString(SecureKeys.BALANCE_ALERT_LAST_DATE, date)

    fun getBalanceAlertLastDate(): String? = prefs.getString(SecureKeys.BALANCE_ALERT_LAST_DATE, null)

    // === 자동구매 멱등 가드 ===

    /** 구매 성공 직후 기록(commit) — Worker 재실행 시 같은 회차 중복 결제 방지. */
    fun setLastPurchasedRound(round: Int) = putString(SecureKeys.LAST_PURCHASED_ROUND, round.toString())

    fun getLastPurchasedRound(): Int = prefs.getString(SecureKeys.LAST_PURCHASED_ROUND, null)?.toIntOrNull() ?: 0

    // === 전체 초기화 ===

    fun clearAll() {
        // 마이그레이션 플래그는 유지 — 초기화 후 구 Flutter 데이터가 되살아나지 않도록.
        prefs.edit().clear().putBoolean(MIGRATION_FLAG, true).commit()
    }

    private fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }

    /** Flutter의 record `({String? userId, String? password})` 대응. */
    data class Credentials(val userId: String?, val password: String?)

    private companion object {
        const val PREFS_FILE = "autolotto_secure_prefs"
        const val FLUTTER_PREFS_FILE = "FlutterSecureStorage" // 구 flutter_secure_storage(encrypted) 파일명
        const val MIGRATION_FLAG = "_migrated_from_flutter"    // 1회 이관 완료 표식(설정 키 아님)

        // flutter_secure_storage가 모든 키 앞에 붙이는 고정 접두사(ELEMENT_PREFERENCES_KEY_PREFIX + "_").
        // base64("This is the prefix for a secure storage\n"). 구 데이터는 이 접두사 + 논리키로 저장돼 있다.
        const val FLUTTER_KEY_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg_"

        // 마이그레이션 1회성 보장용 프로세스 락(동시 생성 대비).
        val MIGRATION_LOCK = Any()
    }
}
