package com.ppgpt.gateway.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CryptoService}.
 * No Spring context needed — CryptoService is pure CPU work.
 */
class CryptoServiceTest {

    private static final String VALID_KEY_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private CryptoService cryptoService(String hexKey) {
        return new CryptoService(hexKey);
    }

    @Test
    void constructor_acceptsValid64HexChars() {
        assertThatNoException().isThrownBy(() -> cryptoService(VALID_KEY_HEX));
    }

    @Test
    void constructor_rejectsKeyTooShort() {
        String shortKey = "0".repeat(62);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> cryptoService(shortKey))
                .withMessageContaining("31 bytes");
    }

    @Test
    void constructor_rejectsOddLengthHexKey() {
        String oddKey = "0".repeat(63);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> cryptoService(oddKey))
                .withMessageContaining("even number of characters");
    }

    @Test
    void constructor_rejectsNullHexKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> cryptoService(null))
                .withMessageContaining("even number of characters");
    }

    @Test
    void encryptDecrypt_roundtrip_returnsOriginalPlaintext() {
        CryptoService svc = cryptoService(VALID_KEY_HEX);
        String original  = "{\"apiKey\": \"sk-test-abc123\"}";
        String encrypted = svc.encrypt(original);
        String decrypted = svc.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void encryptDecrypt_emptyString_roundtrips() {
        CryptoService svc = cryptoService(VALID_KEY_HEX);
        assertThat(svc.decrypt(svc.encrypt(""))).isEmpty();
    }

    @Test
    void encrypt_producesDifferentCiphertextEachCall_dueToRandomIv() {
        CryptoService svc = cryptoService(VALID_KEY_HEX);
        String ct1 = svc.encrypt("hello");
        String ct2 = svc.encrypt("hello");
        assertThat(ct1).isNotEqualTo(ct2);
    }

    @Test
    void decrypt_throwsForTamperedCiphertext() {
        CryptoService svc = cryptoService(VALID_KEY_HEX);
        String encrypted = svc.encrypt("sensitive data");
        String tampered  = encrypted.substring(0, encrypted.length() - 4) + "XXXX";
        assertThatRuntimeException()
                .isThrownBy(() -> svc.decrypt(tampered))
                .withMessageContaining("Decryption failed");
    }

    @Test
    void decrypt_throwsForWrongKey() {
        CryptoService encryptor = cryptoService(VALID_KEY_HEX);
        CryptoService decryptor = cryptoService(
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        String encrypted = encryptor.encrypt("top secret");
        assertThatRuntimeException()
                .isThrownBy(() -> decryptor.decrypt(encrypted))
                .withMessageContaining("Decryption failed");
    }

    @Test
    void encrypt_handlesUnicodeCharacters() {
        CryptoService svc = cryptoService(VALID_KEY_HEX);
        String unicode = "สวัสดี 🤖 テスト";
        assertThat(svc.decrypt(svc.encrypt(unicode))).isEqualTo(unicode);
    }
}
