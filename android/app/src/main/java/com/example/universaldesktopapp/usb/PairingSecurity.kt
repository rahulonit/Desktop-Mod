package com.example.universaldesktopapp.usb

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object PairingSecurity {
    private val random = SecureRandom()

    fun newCode(): String = (random.nextInt(900_000) + 100_000).toString()
    fun newChallenge(): ByteArray = ByteArray(32).also(random::nextBytes)

    fun response(code: String, challenge: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(code.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        doFinal(challenge)
    }

    fun verify(code: String, challenge: ByteArray, candidate: ByteArray): Boolean =
        MessageDigest.isEqual(response(code, challenge), candidate)
}
