package com.rastudio.commerce.marketplace;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.security.SecureRandom; import java.util.Base64;
import javax.crypto.Cipher; import javax.crypto.spec.GCMParameterSpec; import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Component;

/**
 * Encrypts marketplace API keys/secrets/OAuth tokens with AES-256-GCM before they are stored in
 * marketplace_connection.credential_ref, which schema.sql documents as "an opaque reference/alias to secret
 * storage; never the raw secret". In a production deployment this key should come from a managed secret store
 * (AWS KMS/Secrets Manager, GCP KMS, Vault, etc.) via CREDENTIAL_ENCRYPTION_KEY; the value below is a
 * development-only default, matching the pattern already used for app.jwt.secret.
 */
@Component
public class CredentialCipherService {
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private final SecretKeySpec key;

  public CredentialCipherService(@Value("${app.credential-encryption-key}") String secret) {
    try {
      byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
      this.key = new SecretKeySpec(keyBytes, "AES");
    } catch (Exception e) {
      throw new IllegalStateException("Unable to initialize credential cipher", e);
    }
  }

  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
      return Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException("Credential encryption failed", e);
    }
  }

  public String decrypt(String encoded) {
    try {
      byte[] combined = Base64.getDecoder().decode(encoded);
      byte[] iv = new byte[IV_LENGTH_BYTES];
      System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
      byte[] ciphertext = new byte[combined.length - IV_LENGTH_BYTES];
      System.arraycopy(combined, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Credential decryption failed", e);
    }
  }
}