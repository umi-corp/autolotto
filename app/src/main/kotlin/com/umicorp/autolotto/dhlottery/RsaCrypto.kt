package com.umicorp.autolotto.dhlottery

import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher

/**
 * RSA 암호화 (동행복권 로그인용). Flutter crypto.dart의 pointycastle 구현과 동일한 결과:
 * PKCS1 v1.5 패딩 → 소문자 hex. JDK 내장 Cipher만 쓰므로 의존성 없음.
 */
object RsaCrypto {
    fun encrypt(plainText: String, modulusHex: String, exponentHex: String): String {
        val modulus = BigInteger(modulusHex, 16)
        val exponent = BigInteger(exponentHex, 16)
        val publicKey = KeyFactory.getInstance("RSA")
            .generatePublic(RSAPublicKeySpec(modulus, exponent))

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return encrypted.joinToString("") { "%02x".format(it) }
    }
}
