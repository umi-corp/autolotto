package com.umicorp.autolotto.dhlottery

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher

class RsaCryptoTest {

    /**
     * RSA PKCS1 v1.5 + 소문자 hex 인코딩이 올바른지 라운드트립으로 검증.
     * (pointycastle 출력과 동일해야 하지만 PKCS1 패딩은 매번 랜덤이라 동치비교 불가 →
     *  개인키로 복호화해 원문 복원 여부로 확인. 한글 포함.)
     */
    @Test
    fun `encrypt round-trips through RSA PKCS1 with lowercase hex output`() {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pub = kp.public as RSAPublicKey
        val modulusHex = pub.modulus.toString(16)
        val exponentHex = pub.publicExponent.toString(16)

        val plain = "myUserId_한글_pw!123"
        val cipherHex = RsaCrypto.encrypt(plain, modulusHex, exponentHex)

        // 소문자 hex, 짝수 길이
        assertEquals(cipherHex.lowercase(), cipherHex)
        assertEquals(0, cipherHex.length % 2)

        val bytes = ByteArray(cipherHex.length / 2) {
            cipherHex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, kp.private)
        val decrypted = String(cipher.doFinal(bytes), Charsets.UTF_8)

        assertEquals(plain, decrypted)
    }
}
