package com.umicorp.autolotto.ui

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 릴리스 노트 초경량 마크다운 렌더링 검증 — 문법 제거·굵게 스팬·목록 불릿·비정상 입력. */
class MarkdownLiteTest {

    @Test fun stripsSyntaxAndKeepsText() {
        val out = markdownLite("### 새로워진 점\n- **Material 3** 적용\n일반 문장")
        assertEquals("새로워진 점\n•  Material 3 적용\n일반 문장", out.text)
    }

    @Test fun boldSpansApplied() {
        val out = markdownLite("- **굵게** 일반")
        val bold = out.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, bold.size)
        assertEquals("굵게", out.text.substring(bold[0].start, bold[0].end))
    }

    @Test fun headingIsBoldWholeLine() {
        val out = markdownLite("## 제목")
        assertTrue(out.spanStyles.any { it.item.fontWeight == FontWeight.Bold && out.text.substring(it.start, it.end) == "제목" })
    }

    @Test fun unmatchedBoldLeftAsIs() {
        assertEquals("짝 없는 **별표", markdownLite("짝 없는 **별표").text)
    }
}
