package org.example.drivesimui.service;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

/**
 * AES-256-GCM encrypted credential storage for platform passwords.
 *
 * <p>Each platform's password is encrypted with a key derived entirely from
 * machine identity (hostname + OS username) via PBKDF2WithHmacSHA256.
 * The encrypted file is stored at {@code ~/.drivesim/<platform_lower>.enc}.
 *
 * <p>File format: {@code [12-byte random IV][ciphertext + 16-byte GCM auth tag]}
 *
 * <p>Passwords are always handled as {@code char[]} and zeroed after use;
 * they are never held as a {@code String} beyond the call site.
 */
public class CredentialService {

    private static final Path   STORE_DIR    = Path.of(System.getProperty("user.home"), ".drivesim");
    private static final int    IV_LEN       = 12;
    private static final int    TAG_BITS     = 128;
    private static final int    KEY_LEN_BITS = 256;
    private static final int    PBKDF2_ITERS = 65_536;

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Encrypts {@code password} with the machine-derived key and writes the result
     * to {@code ~/.drivesim/<platform_lower>.enc}.
     *
     * <p>The {@code password} array is zeroed by this method before it returns.
     */
    public void savePassword(String platform, char[] password) throws Exception {
        byte[] plain = null;
        byte[] key   = null;
        try {
            plain = encode(password);
            Arrays.fill(password, '\0');

            key = machineKey();

            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plain);

            byte[] out = ByteBuffer.allocate(IV_LEN + ciphertext.length)
                    .put(iv).put(ciphertext).array();

            Files.createDirectories(STORE_DIR);
            Files.write(credFile(platform), out);
        } finally {
            if (plain != null) Arrays.fill(plain, (byte) 0);
            if (key   != null) Arrays.fill(key,   (byte) 0);
        }
    }

    /**
     * Reads and decrypts the stored credential for {@code platform}.
     *
     * @return the decrypted password as a {@code char[]}; caller must zero after use
     * @throws IOException if no credential file exists for this platform
     */
    public char[] loadPassword(String platform) throws Exception {
        Path file = credFile(platform);
        if (!Files.exists(file)) throw new IOException("No saved credential for " + platform);

        byte[] key   = null;
        byte[] plain = null;
        try {
            byte[] data = Files.readAllBytes(file);
            ByteBuffer buf = ByteBuffer.wrap(data);

            byte[] iv = new byte[IV_LEN];
            buf.get(iv);
            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);

            key = machineKey();

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            plain = cipher.doFinal(ciphertext);
            return decode(plain);
        } finally {
            if (key   != null) Arrays.fill(key,   (byte) 0);
            if (plain != null) Arrays.fill(plain, (byte) 0);
        }
    }

    /** Returns {@code true} if a credential file exists for this platform. */
    public boolean hasSavedCredential(String platform) {
        return Files.exists(credFile(platform));
    }

    /** Deletes the stored credential file for this platform (silent on missing file). */
    public void deleteCredential(String platform) {
        try { Files.deleteIfExists(credFile(platform)); }
        catch (IOException e) {
            System.err.println("[CredentialService] delete failed: " + e.getMessage());
        }
    }

    // ── Key derivation ─────────────────────────────────────────────────────

    /**
     * Derives a 256-bit AES key from the machine identity (hostname + OS username).
     * No user-provided password is mixed in — the machine itself is the "passphrase".
     */
    private byte[] machineKey() throws Exception {
        String identity = machineIdentity();
        byte[] salt     = identity.getBytes(StandardCharsets.UTF_8);
        char[] pass     = identity.toCharArray();
        try {
            KeySpec spec = new PBEKeySpec(pass, salt, PBKDF2_ITERS, KEY_LEN_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            Arrays.fill(pass, '\0');
            Arrays.fill(salt, (byte) 0);
        }
    }

    private String machineIdentity() {
        String host = "localhost";
        try { host = InetAddress.getLocalHost().getHostName(); }
        catch (Exception ignored) {}
        return host + "|" + System.getProperty("user.name", "user");
    }

    // ── Encoding helpers ───────────────────────────────────────────────────

    private byte[] encode(char[] chars) {
        ByteBuffer buf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        if (buf.hasArray()) Arrays.fill(buf.array(), (byte) 0);
        return bytes;
    }

    private char[] decode(byte[] bytes) {
        CharBuffer buf = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
        char[] chars = new char[buf.remaining()];
        buf.get(chars);
        return chars;
    }

    private Path credFile(String platform) {
        return STORE_DIR.resolve(platform.toLowerCase() + ".enc");
    }
}
