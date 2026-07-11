# AutoLotto 배포 워크플로우 (vX.Y.Z 릴리스)

"vX.Y.Z 배포" 요청 시 아래 순서를 그대로 따른다. 배포 채널은 GitHub Releases 하나이며,
랜딩 페이지(autolotto.umicorp.kr)와 인앱 업데이터가 `releases/latest`를 자동 추적하므로
릴리스만 올리면 끝 — **랜딩 페이지(docs/index.html)는 절대 손대지 않는다.**

## 0. 전제

- 서명 키: 리포 루트의 `key.properties` + `autolotto-key.jks` (둘 다 gitignored, **유일본** — 커밋·노출·이동 금지)
- GitHub 토큰: 로컬 `.env`의 `umicorp` PAT. 토큰을 argv·echo·출력에 절대 노출하지 않는다 —
  `-H @<(printf 'Authorization: Bearer %s\n' "$TOKEN")` 프로세스 치환으로만 전달
- JDK: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` (gradlew·apksigner 공통)
- 커밋/푸시는 사용자가 배포를 요청한 경우에 한함. force-push는 건별 명시 승인 필수

## 1. 버전 범프

`app/build.gradle.kts`의 `versionCode` +1, `versionName = "X.Y.Z"`.
(versionCode 계보: 2001=구 Flutter 최종. OS 인스톨러는 versionCode, 인앱 업데이터는 versionName/태그로 비교)

## 2. 커밋·푸시

기능 커밋(들) → `chore(release): X.Y.Z` 순서로 main에 푸시.

## 3. 릴리스 빌드

```sh
JAVA_HOME=... ./gradlew assembleRelease
```

산출물: `app/build/outputs/apk/release/app-release.apk`

## 4. 빌드 검증 (필수)

빌드툴: `~/Library/Android/sdk/build-tools/<최신>/`

- `aapt dump badging app-release.apk | head -1` → versionCode/versionName 일치
- `apksigner verify --print-certs app-release.apk` → 인증서 SHA-256이
  `0da60b4d4f41a6fb779f2ae5866cdd6b1d5b61ed4f4daf9dd799e1f348ef4a55` 와 일치

## 5. GitHub 릴리스 생성

- 요청 본문은 **JSON 파일로 작성** 후 `-d @파일` (zsh echo가 `\n`을 해석해 JSON을 깨뜨리는 사고 방지)
- `tag_name: "vX.Y.Z"`, `name: "vX.Y.Z"`, `body`: 릴리스 노트
- 노트는 인앱 markdownLite가 지원하는 서브셋만 사용: `###` 헤딩, `**굵게**`, `-` 리스트
- `POST /repos/umi-corp/autolotto/releases` → 응답을 파일로 저장해 python으로 `id` 파싱

## 6. APK 업로드

`POST https://uploads.github.com/repos/umi-corp/autolotto/releases/{id}/assets?name=autolotto-X.Y.Z-universal.apk`
(`Content-Type: application/vnd.android.package-archive`, `--data-binary @app-release.apk`)
에셋 이름은 `.apk`로 끝나기만 하면 됨(업데이터·랜딩 모두 이름 무관) — 관례상 `autolotto-X.Y.Z-universal.apk`.

## 7. 최종 검증 (필수)

- `GET /repos/umi-corp/autolotto/releases/latest` → `tag_name == vX.Y.Z`, 에셋 존재
- 공개 다운로드 URL로 받아 `md5 == 로컬 빌드 md5` 확인

## 8. 사후

- 실기기(폰) 설치는 사용자가 명시 요청할 때만 — 사용자는 인앱 업데이터 안내로 직접 업데이트한다
- 에뮬레이터로 검증했다면 종료한다(배터리)
- 업데이터 `download()`/`install()` 코드를 건드린 릴리스는 배포 후 에뮬레이터에서
  구버전(릴리스 서명, versionName만 임시 하향) → 신버전 업데이트 경로를 E2E로 1회 검증한다
