package com.umicorp.autolotto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.umicorp.autolotto.AppContainer
import com.umicorp.autolotto.R
import kotlinx.coroutines.delay

/**
 * 스플래시 (원본 `splash_screen.dart` 1:1).
 *
 * 동작: SecureStore → 플로우 하이드레이트(언어·설정) → 자격증명 있으면 자동 로그인 시도(실패 무시) →
 * 최소 1.5초 표시 후 [onFinished]로 AppShell 전환.
 *
 * ponytail: 다크 네이비 그라데이션은 원본 브랜드 스플래시 그대로(테마 토큰이 아닌 의도된 브랜드 색).
 * 콜드스타트 흰 플래시가 거슬리면 core-splashscreen으로 교체.
 */
@Composable
fun SplashScreen(container: AppContainer, onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        // 설정 하이드레이션만 대기(로컬, 빠름). autoLogin은 AppRoot 소유 — 네트워크 지연이
        // 스플래시를 붙잡지 않는다(crosscheck R2: OkHttp 블로킹 시퀀스는 withTimeout으로 못 끊음).
        container.ensureHydrated()
        val elapsed = System.currentTimeMillis() - start
        if (elapsed < MIN_SPLASH_MS) delay(MIN_SPLASH_MS - elapsed)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF15152D), Color(0xFF1E2D50)))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(28.dp)),
            )
            Spacer(Modifier.height(24.dp))
            Text("AutoLotto", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Lotto 6/45", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(40.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 2.5.dp,
            )
        }
    }
}

private const val MIN_SPLASH_MS = 1500L
