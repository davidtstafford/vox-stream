package com.voxstream.core.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AES/GCM encryption service for secure configuration values.
 * Derives key from a master secret provided via env var VOXSTREAM_MASTER_SECRET
 * or system property voxstream.masterSecret.
 * If absent, generates a random ephemeral key (NOT persisted) and logs a
 * warning.
 */
@Service
public class EncryptionService {
    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);
    private static final String ENC_PREFIX = "ENC:";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 65_536;

    private final SecureRandom random = new SecureRandom();
    private final String masterSecret;

    public EncryptionService() {
        String fromEnv = System.getenv("VOXSTREAM_MASTER_SECRET");
        String fromProp = System.getProperty("voxstream.masterSecret");
        this.masterSecret = fromEnv != null ? fromEnv : (fromProp != null ? fromProp : generateEphemeral());
    }

    private String generateEphemeral() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        log.warn("No master secret provided; using ephemeral key (secrets won't decrypt across restarts)");
        return Base64.getEncoder().encodeToString(b);
    }

    public String encrypt(String plain) {
        if (plain == null)
            return null;
        try {
            byte[] salt = new byte[SALT_BYTES];
            random.nextBytes(salt);
            SecretKey key = deriveKey(salt);
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer bb = ByteBuffer.allocate(salt.length + iv.length + ciphertext.length);
            bb.put(salt).put(iv).put(ciphertext);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(bb.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String enc) {
        if (enc == null)
            return null;
        if (!enc.startsWith(ENC_PREFIX))
            return enc; // not encrypted
        try {
            byte[] all = Base64.getDecoder().decode(enc.substring(ENC_PREFIX.length()));
            ByteBuffer bb = ByteBuffer.wrap(all);
            byte[] salt = new byte[SALT_BYTES];
            byte[] iv = new byte[IV_BYTES];
            bb.get(salt).get(iv);
            byte[] ciphertext = new byte[bb.remaining()];
            bb.get(ciphertext);
            SecretKey key = deriveKey(salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private SecretKey deriveKey(byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(masterSecret.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX);
    }
}
