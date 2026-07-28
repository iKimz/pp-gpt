package com.ppgpt.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption service for sensitive credentials (API Keys, OAuth Secrets) stored at rest.
 *
 * <p>Payload Format: Base64( IV(12 bytes) || Ciphertext + AuthTag(128 bits) )</p>
 * <p>This service performs non-blocking, in-memory cryptographic transformations.</p>
 */
@Slf4j
@Service
public class CryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;

    /**
     * Initializes CryptoService with a 256-bit (64 hex characters) secret key.
     *
     * @param hexKey Hex-encoded 64 character AES-256 key
     */
    public CryptoService(@Value("${app.encryption.key}") String hexKey) {
        byte[] keyBytes = hexToBytes(hexKey);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "Encryption key must be 64 hex characters (32 bytes / AES-256). Got " + keyBytes.length + " bytes.");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }

    /**
     * Encrypts plaintext API key or token into a Base64-encoded ciphertext payload with random IV.
     *
     * @param plaintext Sensitive credential string
     * @return Base64 encoded payload
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("[CryptoService] Encryption failed: {}", e.getMessage());
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts Base64-encoded ciphertext payload back into plaintext string.
     *
     * @param encoded Base64 encoded ciphertext
     * @return Decrypted plaintext string
     */
    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(combined, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[CryptoService] Decryption failed: {}", e.getMessage());
            throw new RuntimeException("Decryption failed — key mismatch or corrupted ciphertext", e);
        }
    }

    /**
     * Converts a hex string representation into raw byte array.
     *
     * @param hex Hex string
     * @return Byte array
     */
    private byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException(
                    "Hex key must be an even number of characters. Got: " + (hex == null ? "null" : hex.length()) + " chars.");
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
