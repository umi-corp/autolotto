# OkHttp / Okio: 라이브러리가 consumer 규칙을 동봉하므로 대부분 자동. 경고만 억제.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Compose는 AGP 기본 규칙으로 처리됨.

# Tink(androidx.security-crypto/EncryptedSharedPreferences)이 참조하는 errorprone 컴파일 전용
# 애노테이션은 런타임에 없음 → R8 경고 억제(무해, 표준 처리).
-dontwarn com.google.errorprone.annotations.**
