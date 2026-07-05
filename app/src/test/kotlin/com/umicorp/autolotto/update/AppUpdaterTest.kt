package com.umicorp.autolotto.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 버전 비교(semver) 순수 로직 검증 — 자릿수 함정(1.10 > 1.9)·접두사·오류입력 포함. */
class AppUpdaterTest {

    @Test fun newerBasic() {
        assertTrue(AppUpdater.isNewer("1.1.0", "v1.2.0"))
        assertTrue(AppUpdater.isNewer("1.1.0", "1.1.1"))
        assertTrue(AppUpdater.isNewer("1.1.0", "v2.0.0"))
    }

    @Test fun notNewerWhenSameOrOlder() {
        assertFalse(AppUpdater.isNewer("1.1.0", "1.1.0"))
        assertFalse(AppUpdater.isNewer("1.1.0", "v1.1.0"))
        assertFalse(AppUpdater.isNewer("1.2.0", "v1.1.9"))
    }

    @Test fun numericNotLexicographic() {
        // 문자열 비교였다면 "1.9.0" > "1.10.0" 로 잘못 판단 — 정수 비교여야 함.
        assertTrue(AppUpdater.isNewer("1.9.0", "1.10.0"))
        assertFalse(AppUpdater.isNewer("1.10.0", "1.9.0"))
    }

    @Test fun malformedLatestIsNotNewer() {
        assertFalse(AppUpdater.isNewer("1.1.0", "garbage"))
        assertFalse(AppUpdater.isNewer("1.1.0", ""))
    }

    @Test fun shorterVersionsPadWithZero() {
        assertFalse(AppUpdater.isNewer("1.1.0", "1.1"))   // 1.1 == 1.1.0
        assertTrue(AppUpdater.isNewer("1.1", "1.1.1"))
    }

    // --- APK 에셋 ABI 선택 (autolotto 릴리스가 split-per-abi라 중요) ---

    @Test fun picksDeviceAbiFromSplitApks() {
        val apks = listOf(
            "app-armeabi-v7a-release.apk" to "v7a",
            "app-arm64-v8a-release.apk" to "arm64",
            "app-x86_64-release.apk" to "x64",
        )
        assertEquals("arm64", AppUpdater.chooseApkUrl(apks, listOf("arm64-v8a", "armeabi-v7a")))
    }

    @Test fun fallsBackToUniversalThenFirst() {
        val withUniversal = listOf("app-x86-release.apk" to "x86", "app-universal-release.apk" to "uni")
        assertEquals("uni", AppUpdater.chooseApkUrl(withUniversal, listOf("arm64-v8a")))
        val single = listOf("app-release.apk" to "only")
        assertEquals("only", AppUpdater.chooseApkUrl(single, listOf("arm64-v8a")))
    }

    @Test fun noApksIsNull() {
        assertNull(AppUpdater.chooseApkUrl(emptyList(), listOf("arm64-v8a")))
    }
}
