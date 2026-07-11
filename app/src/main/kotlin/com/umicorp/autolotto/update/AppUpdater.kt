package com.umicorp.autolotto.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** GitHub 릴리스에서 감지한 새 버전 정보. */
data class UpdateInfo(val versionName: String, val downloadUrl: String, val notes: String)

/**
 * 사이드로드 배포용 인앱 업데이터 (Play 자동업데이트 부재 대체).
 *
 * GitHub Releases API의 latest를 읽어 현재 versionName과 semver 비교 → 새 버전이면 APK 에셋을 받아
 * 시스템 패키지 인스톨러로 설치. Play가 아니므로 REQUEST_INSTALL_PACKAGES 사용 가능(사용자 1회 승인).
 *
 * ⚠️ 릴리스 규칙: 아래 [RELEASES_API] 저장소에 태그 `v<versionName>`(예: v1.2.0)로 릴리스를 만들고
 *    빌드한 `*.apk`를 에셋으로 첨부해야 감지된다. 저장소를 바꾸려면 이 상수만 수정.
 */
object AppUpdater {

    // 릴리스 저장소: 공개 + Pages(배포 페이지)가 있는 umi-corp/autolotto. 업데이터는 비인증이라
    // 공개 저장소여야 함(비공개 autolotto-android는 releases/latest가 404). 소스는 비공개 유지, APK만 공개 배포.
    private const val RELEASES_API =
        "https://api.github.com/repos/umi-corp/autolotto/releases/latest"

    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    private val client = OkHttpClient()  // GitHub CDN 302 리다이렉트 자동 추종(기본값)

    /** latest 릴리스 조회 → 새 버전이면 [UpdateInfo], 아니면(또는 실패 시) null. 네트워크는 IO. */
    suspend fun check(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(RELEASES_API)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val json = JSONObject(resp.body?.string() ?: return@use null)
                val tag = json.optString("tag_name")
                if (tag.isBlank() || !isNewer(currentVersionName, tag)) return@use null
                val assetsJson = json.optJSONArray("assets") ?: return@use null
                val apks = (0 until assetsJson.length())
                    .map { assetsJson.getJSONObject(it) }
                    .map { it.optString("name") to it.optString("browser_download_url") }
                    .filter { it.first.endsWith(".apk", ignoreCase = true) && it.second.isNotBlank() }
                val url = chooseApkUrl(apks, Build.SUPPORTED_ABIS.toList()) ?: return@use null
                UpdateInfo(tag.removePrefix("v"), url, json.optString("body").trim())
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 업데이트 APK 크기 상한 — 릴리스 채널 침해 시 저장공간 고갈 방지(실제 APK ~4MB). */
    private const val MAX_APK_BYTES = 50L * 1024 * 1024

    /** APK를 캐시로 스트리밍 다운로드. 진행률(0..1)은 [onProgress]. 성공 시 File, 실패 시 null. */
    suspend fun download(context: Context, url: String, onProgress: (Float) -> Unit): File? =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }   // 이전 다운로드 정리
            val out = File(dir, "autolotto-update.apk")
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body ?: return@use null
                    val total = body.contentLength()
                    if (total > MAX_APK_BYTES) return@use null // 상한 초과 선언 시 시작하지 않음
                    body.byteStream().use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(8192)
                            var read = 0L
                            var n: Int
                            while (input.read(buf).also { n = it } != -1) {
                                read += n
                                // 길이 미신고·허위 신고 대비 — 상한 초과분은 쓰지 않고 즉시 중단(catch가 부분 파일 삭제)
                                if (read > MAX_APK_BYTES) throw IOException("update apk exceeds size cap")
                                output.write(buf, 0, n)
                                if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                    // 설치 전 자체 검증 — 실패 시 파일 폐기 + null(호출부의 기존 다운로드 실패 처리로 수렴).
                    if (!isValidUpdate(context, out)) {
                        out.delete()
                        return@use null
                    }
                    out
                }
            } catch (_: Exception) {
                runCatching { out.delete() }   // 부분 파일 즉시 정리(저장공간 잔류 방지)
                null
            }
        }

    /**
     * 다운로드한 APK가 이 앱의 정식 업데이트인지 검증 — 패키지명과 서명 인증서가 현재 설치본과
     * 정확히 일치해야 한다. 서명 불일치 업데이트는 어차피 OS가 거부하지만, 다른 패키지명으로 위장한
     * APK의 설치 유도를 다운로드 단계에서 차단한다. 판단 불가(파싱 실패 등)는 전부 거부(fail-closed).
     */
    private fun isValidUpdate(context: Context, apk: File): Boolean = try {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        @Suppress("DEPRECATION")
        val own = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val archiveSigners = archive?.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        val ownSigners = own.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        archive != null &&
            archive.packageName == context.packageName &&
            !archiveSigners.isNullOrEmpty() &&
            archiveSigners == ownSigners
    } catch (_: Exception) {
        false
    }

    /** 시스템 패키지 인스톨러 실행(FileProvider content:// URI). */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** "이 출처의 앱 설치" 허용 여부(API26+). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** 미허용 시 유도할 시스템 설정 화면 Intent. */
    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * 여러 APK 에셋 중 기기 ABI에 맞는 것 우선 선택(split-per-abi 대응). 매칭 없으면 universal, 그다음 첫 apk.
     * ([apks] = (name, downloadUrl) 목록, [abis] = Build.SUPPORTED_ABIS.) (테스트 대상)
     */
    internal fun chooseApkUrl(apks: List<Pair<String, String>>, abis: List<String>): String? {
        if (apks.isEmpty()) return null
        // ABI는 우선순위 순(SUPPORTED_ABIS: 주 ABI가 먼저) — 에셋 목록 순서가 아니라 가장 선호되는 ABI부터 매칭.
        for (abi in abis) {
            apks.firstOrNull { (name, _) -> name.contains(abi, ignoreCase = true) }?.let { return it.second }
        }
        apks.firstOrNull { (name, _) -> name.contains("universal", ignoreCase = true) }?.let { return it.second }
        return apks.first().second
    }

    /** semver 비교: latest가 current보다 크면 true. `v` 접두사·`-` 접미 허용, 누락 자리는 0. (테스트 대상) */
    internal fun isNewer(current: String, latest: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }
        val c = parts(current)
        val l = parts(latest)
        if (l.isEmpty()) return false
        for (i in 0 until maxOf(c.size, l.size)) {
            val cv = c.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }
}
