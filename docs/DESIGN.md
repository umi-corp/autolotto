# AutoLotto 네이티브 포팅 — 설계 & 계획 (적대검증 대상 문서)

> 이 문서는 외부 모델(codex / gemini / claude / opencode)이 **대화 맥락 없이** 계획을
> 적대적으로 검토할 수 있도록 자기완결적으로 작성됨. §8 "공격 지점" 참조.

## 0. 목적
Flutter Android 앱 **AutoLotto**(로또 6/45 자동구매 · 당첨확인)를 **풀 네이티브(Kotlin +
Jetpack Compose + Material 3)** 로 포팅한다. Flutter를 완전히 제거. 목표 3가지:
1. 최신 디자인 — M3 Expressive 지향 인터랙션("최신앱 느낌")
2. 최상 속도 — Flutter 엔진 제거
3. 세련된 최신 안드로이드 패턴

- 원본(Flutter): `/Users/wjsong/Workspace/autolotto` — 약 3,750줄 Dart, 동작하는 v1.0.0.
- 네이티브(타깃): `/Users/wjsong/Workspace/autolotto-android` — 이 문서 위치.
- 머니패스 원본 진실: `autolotto/lib/services/{auth,purchase,history,result}_service.dart`,
  `lib/utils/{constants,crypto}.dart`. **포팅 충실도는 이 파일들 기준으로 판정.**

## 1. 핵심 제약 — 백엔드 없음, dhlottery 역공학 세션
서버가 없다. 앱이 dhlottery.co.kr 웹 엔드포인트를 브라우저처럼 직접 구동한다. 두 도메인:
- `www.dhlottery.co.kr` — 인증 · 잔액 · 구매내역 · 당첨결과
- `ol.dhlottery.co.kr` — 구매(execBuy)

세션 쿠키 `JSESSIONID`가 두 서브도메인 사이로 전파돼야 구매가 된다. 이게 이 앱의 급소.

## 2. 머니패스 충실도 — 포팅 시 깨지기 쉬운 7곳
1. **도메인별 커스텀 쿠키잭** — OkHttp 기본 CookieJar는 JSESSIONID를 www.↔ol. 사이로 안 옮긴다.
   `AuthService._domainCookies` 병합 규칙(`.suffix` 매칭 + 정확 host)을 1:1 복제. → `DomainCookieStore.kt`
2. **수동 리다이렉트** — `followRedirects=false`, 301/302/303 최대 10회 GET, 상대경로는 origin resolve.
   Referer/쿠키 타이밍이 결과를 좌우. → `DhlotterySession.followingRedirects`
3. **회차 계산 모듈로 함정** 🔴 — Dart `%`는 항상 음이 아님, Kotlin/Java `%`는 부호가 피제수 따라감.
   일요일 `(6 - weekday) % 7`이 Dart=6, Kotlin=-1 → 엉뚱한 회차 구매. `Math.floorMod` + `java.time.DayOfWeek`.
   **(Slice 2에서 포팅 예정 — 미구현)**
4. **RSA** — `Cipher("RSA/ECB/PKCS1Padding")`, UTF-8, 소문자 hex 무구분자. pointycastle 출력과 동일. → `RsaCrypto.kt`
5. **JSON 자리에 HTML = 세션 만료 센티넬** — readySocket/execBuy/잔액/내역 각 호출에서 분기 복제.
6. **구매 성공 = `result.resultCode == '100'`** — execBuy 폼 필드(`saleMdaDcd:'10'`, `param` 슬롯 A~E,
   `nBuyAmount=1000×게임수`) 정확히 일치. **(Slice 2 — 미구현)**
7. **검증은 실계정으로** — 로그인 1건 + 실제 ₩1000 1게임까지 라이브 통과 전엔 신뢰 금지.

## 3. 아키텍처 (네이티브 타깃)

| 영역 | 원본(Flutter) | 네이티브 채택 |
|---|---|---|
| HTTP/세션 | dio + 커스텀 쿠키 | **OkHttp + 커스텀 CookieJar** + `org.json`(내장) |
| 암호화 | pointycastle RSA | **javax.crypto**(내장) |
| 백그라운드 | android_alarm_manager_plus(별도 isolate) | **AlarmManager.setExactAndAllowWhileIdle + BroadcastReceiver** 자가재등록 + 부팅 리시버 |
| 보안저장 | flutter_secure_storage | **EncryptedSharedPreferences**(Jetpack Security) |
| 로컬 DB | Hive | **생략**(내역은 서버 재조회) — 필요 시 Room |
| 상태/UI | Riverpod + 위젯 | **ViewModel/StateFlow + Compose** |
| 테마 | useMaterial3 | **MaterialTheme + dynamic color**, Expressive는 컴포넌트/모션으로 |
| i18n | ARB ko/en/ja | **strings.xml ko/en/ja** |
| 알림 | flutter_local_notifications | **NotificationManagerCompat**(하드코딩 ko) |

**버전 결정(2026-06 기준 Maven 확인):** AGP 8.11.1 · Kotlin 2.2.20 · Gradle 8.14 ·
compileSdk 36 · **minSdk 31**(dynamic color/Expressive 풀, 폴백 0) · targetSdk 36 ·
Compose BOM **2026.06.00**(→ material3 **1.4.0**, Expressive 컴포넌트 공개) · OkHttp 4.12.0 ·
coroutines 1.9.0 · security-crypto 1.1.0-alpha06. 패키지 `com.umicorp.autolotto`.

**M3 Expressive 주의:** stable material3 1.4.0에서 `MaterialExpressiveTheme` **래퍼는 internal**(비공개).
공개된 것은 expressive **컴포넌트/모션**. → 테마는 표준 `MaterialTheme`, "Expressive 느낌"은 컴포넌트로
입힌다(Slice 5). 래퍼까지 원하면 material3 1.5.0-alpha(머니앱이라 미채택).

## 4. 빌드 계획 — 6 수직 슬라이스 (위험한 머니패스 우선)
| # | 슬라이스 | 핵심 | 검증 |
|---|---|---|---|
| 0 | 골격 | Compose 셸, 4탭, 테마, Flutter 제거 | 빌드 그린 ✅ |
| 1 | 로그인 | 쿠키잭 + RSA + 세션 시퀀스 | 단위테스트 ✅ / 라이브 ⏳ |
| 2 | 구매 | readySocket + execBuy + 회차계산(floorMod) | 실 ₩1000 1건 |
| 3 | 내역·당첨확인 | selectMyLotteryledger + ticketDetail | mock + 라이브 |
| 4 | 백그라운드 | AlarmManager 자가연쇄 + 부팅 + 배터리최적화 | 기기 |
| 5 | M3 Expressive 폴리시 | 컴포넌트·모션·dynamic color·i18n 완성 | 시각 검증 |

## 5. 현재 상태 (execute 진행분)
**Slice 0 ✅** — `assembleDebug` BUILD SUCCESSFUL, APK 생성. 파일:
settings/build/app `build.gradle.kts`, `gradle.properties`, `AndroidManifest.xml`(권한 8종),
`MainActivity.kt`, `ui/App.kt`(M3 NavigationBar 4탭), `ui/theme/{Theme,Color}.kt`,
`res/values{,-en,-ja}/strings.xml`, `themes.xml`, wrapper 8.14, 런처 아이콘.

**Slice 1 ✅(단위)** — `testDebugUnitTest` 그린. 파일:
`dhlottery/{ApiConstants,RsaCrypto,DomainCookieStore,DhlotterySession,AuthService}.kt`,
테스트 `{RsaCryptoTest,DomainCookieStoreTest}.kt`.
- 커버됨: RSA 라운드트립(한글), 쿠키 서브도메인 전파, 머니패스 전체 컴파일.
- **미검증**: 라이브 로그인 6단계 시퀀스(계정 필요), 시퀀스 통합테스트(base URL 주입 보류).

## 6. 결정 · 트레이드오프 · ponytail 단순화 (의도된 것)
- 로컬 DB 생략 — 내역은 dhlottery에서 매번 라이브 조회. 데이터 마이그레이션 불필요.
- EncryptedSharedPreferences — deprecated지만 표준·동작. 제거되면 Tink/Keystore.
- 시퀀스 통합테스트 보류 — base URL 주입 리팩터 필요, 라이브 체크포인트로 대체.
- material-icons-extended — 무거움(디버그 APK 61MB). 4개 아이콘이라 core/벡터로 축소 가능(Slice 5).
- 런치테마 흰 플래시 허용 — 필요 시 core-splashscreen.

## 7. 보안 (머니앱)
- 자격증명: id/pw는 EncryptedSharedPreferences(Android Keystore). 평문 로깅 금지.
- 디버그 로그: 원본은 `kDebugMode` 게이트. 네이티브는 `BuildConfig.DEBUG` 게이트 + 자격증명/쿠키 값 마스킹.
- 백그라운드 isolate가 보안저장을 직접 읽는 패턴 유지(원본과 동일).

## 8. 적대검증 — "이걸 공격하라" (검증자 지시)
검증자는 아래를 **의심하고 반증을 시도**하라. 칭찬 금지, 결함만:
- (a) **계획**: 6슬라이스 순서/범위가 옳은가? 머니패스 우선이 맞나? 빠진 슬라이스/단계?
- (b) **머니패스**: §2의 7리스크 외에 Dart→Kotlin 포팅 함정이 더 있나? (인코딩, 타임존, 동시성, 쿠키 RFC 차이)
- (c) **회차계산**: §2-3 floorMod 외에 날짜/회차 경계(토 20:45, 365일 지급기한) 오류 가능성?
- (d) **보안**: 자격증명 저장/로깅/전송에 취약점? RSA 사용 오류?
- (e) **백그라운드**: AlarmManager 정확알람 + OEM(삼성/샤오미) 신뢰성 설계가 충분한가?
- (f) **요구사항 누락**: 원본에 있는데 계획에서 빠진 기능/엣지케이스?
- (g) **현재 코드**(Slice 0/1): `dhlottery/*.kt`가 원본 Dart와 의미상 1:1인가? 차이를 지적하라.

## 9. GATE A 검증 반영 (적대검증 라운드 1 — codex/gemini/opencode/claude)

### 적용한 수정 (현재 코드)
- **로그인 좀비 세션**: `AuthService.login` 전체 try/catch, 실패 시 `isLoggedIn=false` (회귀 테스트 포함).
- **리다이렉트 추적 기본값**: `DhlotterySession.get/post` 기본 `follow=false`(Dart 전역과 일치). Dart가 `_followRedirects` 부른 5곳(메인·로그인페이지·로그인POST·JSESSIONID복구메인·game645)만 `follow=true`. → `getBalance` 만료 302 자동추적 세션오염 제거.
- **시퀀스 검증 복원**: `DhlotterySession`에 baseUrl/olottoUrl 주입. `AuthServiceSequenceTest`(MockWebServer)로 6단계 + 실 RSA end-to-end(POST된 userId를 개인키로 복호화) + INVALID_CREDENTIALS + RSA누락 + getBalance + 좀비세션 회귀.
- **테스트 인프라**: `org.json`(JVM 스텁 우회)·`mockwebserver`·`coroutines-test` + `buildConfig=true`.
- **getBalance HTML 센티넬 명시 가드** + `verifyLogin`이 `BALANCE` 상수 재사용 + `postForm`(Map 폼인코딩, Slice 2 execBuy용) 추가.

### 계획 델타 (다음 슬라이스에서 반드시)
- **Slice 2(구매)**: 회차계산 **KST 고정**(`ZonedDateTime(Asia/Seoul)`); **추첨일을 회차에서 단일소스화**(`firstRound + (round-1)*7일`)해 토 20:45 이후 회차/추첨일 불일치 제거; 판매창(평일+토 20:00 전) 가드; `execBuy`는 `postForm`(모든 값 문자열화); `parseNumbersFromResponse` substring + 회차계산 순수함수 단위테스트(요일·20:45 경계).
- **Slice 4(백그라운드)**: 알람 2개(`1001` 자동구매=사용자시각 / `1002` 결과확인=고정 토 21:00); `canScheduleExactAlarms()` + `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` 런타임 권한 게이트; 잔액부족 알림; OEM(삼성/샤오미) autostart·배터리 가이드; `USE_EXACT_ALARM` Play정책 재검토.
- **보안저장(신규 Slice 1.5)**: EncryptedSharedPreferences로 자격증명·자동구매설정·수동번호 저장 + 백그라운드 직접 읽기(원본 핵심) + splash 자동로그인.
- **UI 재구성**: Slice 5 = 기능 화면(홈/번호/내역/설정) + ViewModel + 보안저장 양방향 동기화 / **Slice 6 = M3 Expressive 폴리시**(모션·컴포넌트·dynamic color).

### 스킵 (충실 포팅 의도 / 비실체 — 추적만)
- URLEncoder(`+`) vs encodeComponent(`%20`): 폼 본문 + 영숫자 ID라 기능 동일(URLEncoder가 폼엔 정확).
- 4xx 비-throw: 최종 `verifyLogin`이 게이트(결과 동일). Max-Age/Expires·`domain=` 오탐: 원본 Dart 동일 버그(충실). payLimit 365일: 서버 권위. minSdk 31: 사용자 결정(<12 드롭 의도).

### 정정
- §5 "단위테스트 ✅" = 이제 RSA·쿠키·**6단계 시퀀스**·좀비세션 회귀 포함.
- §2-4 "pointycastle 출력과 동일"은 부정확(PKCS1 랜덤 패딩) → "개인키 복호 시 원문 복원"이 정확.
- claude 판정문이 codex와 verbatim 동일 → round 2에서 모델 독립성 재확인(md5로 dup 반증, 4/4 독립 APPROVE).

## 10. GATE B1 검증 반영 (서비스 레이어 — codex/gemini/opencode/claude)
라운드 1: 3/4 APPROVE, **codex가 단독으로 진짜 HIGH 포착** → 수정 후 라운드 2.
- **[HIGH] 당첨금 정수 오버플로** 🔴 — Dart `int`(64비트) → Kotlin `Int`(32비트, max 21.4억). 로또 1등 당첨금이 Int.MAX 초과(예: 26.7억) 시 음수/절단. **수정**: `WinningResult.prize1st/2nd/3rd`, `Purchase.prize`/`gamePrizes`, `HistoryService`의 `amt`/`gamePrizes`/`totalPrize`, `ResultService` `rnk*WnAmt` 파싱을 전부 **`Long`/`optLong`**으로. (구매금액 `amount`·`MatchResult.prize`는 소액 → `Int` 유지.) 회귀테스트 2건(26.7억/30억).
- **[Medium] 날짜 파싱 패리티** — `parseDhDateTime`에 `BASIC_ISO_DATE`(yyyyMMdd) + `yyyyMMdd'T'HHmmss` 추가(Dart `DateTime.tryParse`와 동등). 회귀테스트 1건.
- 검증: 44 tests green. 교훈: **단일 모델(codex)만 잡은 진짜 버그 → ≥3 APPROVE라도 수정이 옳다**(라벨이 아니라 실체로 판정).

## 11. Slice 4 스케줄러 반영
android_alarm_manager_plus(별도 isolate) → **네이티브 AlarmManager + BroadcastReceiver + expedited WorkManager**.
- `scheduler/AlarmTimes.kt`(순수 시간계산, `Math.floorMod`) + 9 단위테스트. `AlarmScheduler`(1001 자동구매=사용자 요일/시/분, 1002 결과확인=토 21:00) `setExactAndAllowWhileIdle`.
- 리시버(`AutoPurchase/CheckResult/Boot`)는 onReceive 10초 제한 회피로 **즉시 WorkManager enqueue**. `AutoPurchaseWorker`/`CheckResultWorker`(CoroutineWorker)가 SecureStore 직접읽기 → login → purchase/result → 알림 → **자가재등록(autoEnabled 게이트)**.
- exact-alarm 권한 꺼짐 시 inexact 폴백(방어 추가). 배터리최적화·POST_NOTIFICATIONS 런타임 요청은 Activity 필요 → Slice 5 UI로 보류.
- 알람 id(1001/1002)·알림 id(1/2/50/98/99)·시각·문구·SecureStore 키·자가연쇄 동작 1:1. `assembleDebug` + 53 tests green.

## 12. GATE B-final 검증 반영 (앱 전체 — codex/gemini/opencode/claude)
라운드 1: gemini/claude APPROVE + **codex 단독 HIGH 포착** → 수정 후 라운드 2.
- **[HIGH] 로케일 컨텍스트 startActivity 크래시** 🔴 — `LocalizedApp`가 언어≠system일 때 `createConfigurationContext`(비-Activity Context)를 LocalContext로 주입 → 화면들이 그 컨텍스트로 `startActivity`(충전/오픈소스/후원/배터리/정확알람)를 `FLAG_ACTIVITY_NEW_TASK` 없이 호출 → AndroidRuntimeException 크래시(언어를 ko/en/ja로 바꾼 뒤 외부/설정 진입 시). **수정**: `ui/util/PlatformLaunch.kt`의 `Context.launchActivitySafely`(실제 Activity 탐색→그대로 / 비-Activity→NEW_TASK / 핸들러 없으면 try-catch). Home·Settings의 startActivity 5곳 전부 교체.
- 검증: `assembleDebug` + 53 tests green.
- 교훈(반복): 또 codex 단독 포착 — 다수 APPROVE도 단일 모델의 진짜 크래시 버그를 놓칠 수 있다. **실체 우선** 원칙 유지.

라운드 2: gemini/opencode APPROVE + **codex 단독 HIGH 또 포착** → 수정 후 라운드 3.
- **[HIGH] 구 Flutter 보안저장 데이터 유실** 🔴 — 원본 flutter_secure_storage(encrypted)는 `FlutterSecureStorage` 파일에 저장, 네이티브는 `autolotto_secure_prefs`로 파일명만 달라 같은 applicationId 업데이트 사용자의 자격증명·자동구매설정·manual_numbers를 못 읽음 → **자동구매 조용히 중단**. ("no migration"은 서버 재조회되는 구매내역 얘기였고, 자격증명/설정은 재조회 불가 — 별개 사안.) **수정**: `SecureStore.migrateFromFlutterIfNeeded()` — flutter_secure_storage encrypted = 동일 EncryptedSharedPreferences/기본 MasterKey/AES256_SIV·GCM이라 파일명만 맞춰 1회 복사. try/catch(스킴 불일치·손상·신규설치 안전), `_migrated_from_flutter` 플래그로 1회만, `clearAll`은 플래그 유지(부활 방지). EncryptedSharedPreferences는 Keystore 의존 → 계측 테스트 영역.

라운드 3: gemini/codex가 마이그레이션 **자체의 버그**를 또 포착 → 보강(라운드 4-5).
- **[BLOCKER] 키 접두사 누락**(gemini) — flutter_secure_storage는 모든 키에 고정 접두사(`VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIHNlY3VyZSBzdG9yYWdlCg_`)를 붙여 저장 → `FLUTTER_KEY_PREFIX + key`로 읽도록 보정(안 하면 전부 null = 조용한 유실).
- **[HIGH] 덮어쓰기·재시도**(codex) — 키별 `!prefs.contains(key)`로 네이티브 변경값 보존(덮어쓰기 방지), 성공/실패/구파일없음 **모두 플래그 commit**으로 1회성·재시도루프 차단, **프로세스 락**으로 동시생성 1회, 키별 개별 try/catch 격리.
- ⚠️ **배포·검증 메모**: 이 마이그레이션은 **in-place 업데이트**(기존 Flutter 앱과 동일 applicationId 교체) 배포일 때만 발동(신규 앱이면 구 파일 없어 no-op). EncryptedSharedPreferences는 JVM 단위테스트 불가 → **기기에서 구 Flutter 데이터로 직접 검증 필요**. 신규 앱 배포라면 이 마이그레이션은 불필요(드롭 가능).
- 검증: assembleDebug + 53 tests green.

라운드 5-6: **GATE B-final 통과** — codex(r5)·gemini(r4·r5)·claude(r6) 3개 이상 독립 모델이 현재 코드 APPROVE. 막판 REJECT 2건은 **ground-truth 대조로 false positive 판명**(미수정):
- claude(r5) "접두사 `Cg` 제거하라" → `flutter_secure_storage-9.2.4/.../FlutterSecureStorage.java:30` `ELEMENT_PREFERENCES_KEY_PREFIX="...storageCg"` + `:69` `+"_"+key` 확정 → 코드의 `FLUTTER_KEY_PREFIX`가 정확. (claude는 이 소스 사실 제시 후 r6 APPROVE.)
- gemini(r6) "auto_games만 설정+manual_numbers=[] 시 자동구매 무음 중단" → 원본 `scheduler_service.dart:209-240`과 **1:1 동일**(원본도 동일 동작: 슬롯 파싱→catch만 `autoGames=games` 폴백→`autoGames==0&&empty`면 return) → 포팅 결함 아님. (gemini r4·r5는 APPROVE.)
- 교훈: 검증자는 **비결정적**(동일 코드에 APPROVE↔REJECT). 라벨이 아니라 **소스/원본 ground-truth 대조로 실체 판정**해야 한다. 적대검증이 잡은 진짜 버그(리다이렉트·좀비세션·시퀀스·Long오버플로·로케일크래시·마이그레이션 4종)는 전부 수정·재검증 완료.
