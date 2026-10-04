package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class KeystoreHelper {

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }
    private val secureRandom = SecureRandom()

    init {
        ensureHostKeyPair()
    }

    private fun ensureHostKeyPair() {
        if (!keyStore.containsAlias(ALIAS_HOST_IDENTITY)) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                ALIAS_HOST_IDENTITY,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setKeySize(2048)
                .build()

            kpg.initialize(spec)
            kpg.generateKeyPair()
        }
    }

    fun getHostPublicKeyBase64(): String {
        val cert = keyStore.getCertificate(ALIAS_HOST_IDENTITY) ?: return ""
        val pubKey = cert.publicKey
        return Base64.encodeToString(pubKey.encoded, Base64.NO_WRAP)
    }

    fun getHostPublicKeyFingerprint(): String {
        val cert = keyStore.getCertificate(ALIAS_HOST_IDENTITY) ?: return "UNINITIALIZED"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(cert.publicKey.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    fun signData(data: ByteArray): String {
        val privateKeyEntry = keyStore.getEntry(ALIAS_HOST_IDENTITY, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Private key not found in KeyStore")
        val privateKey: PrivateKey = privateKeyEntry.privateKey

        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(data)
        val signature = signer.sign()
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    fun verifyControllerSignature(
        publicKeyBase64: String,
        data: ByteArray,
        signatureBase64: String
    ): Boolean {
        return try {
            val keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val pubKey: PublicKey = keyFactory.generatePublic(keySpec)

            val sigBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(pubKey)
            verifier.update(data)
            verifier.verify(sigBytes)
        } catch (e: Exception) {
            false
        }
    }

    fun computeFingerprint(publicKeyBase64: String): String {
        return try {
            val keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(keyBytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "INVALID_KEY"
        }
    }

    fun generateSecureNonce(lengthBytes: Int = 32): String {
        val bytes = ByteArray(lengthBytes)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun generateNumericPairingPin(): String {
        val num = secureRandom.nextInt(900000) + 100000
        return num.toString()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val ALIAS_HOST_IDENTITY = "ubaid_host_identity_v1"
    }
}
