import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
public class SessionCipher {

    private final SessionKey sessionKey;
    private final byte[] iv;
    private final Cipher cipher;
    /*
     * Constructor to create a SessionCipher from a SessionKey. The IV is
     * created automatically.
     */

    private Cipher initCipher(int mode) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(mode, sessionKey.getSecretKey(), new IvParameterSpec(iv));
            return cipher;
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize cipher", e);
        }
    }



    public SessionCipher(SessionKey key) {
        this.sessionKey = key;
        this.iv = new byte[16];
        new SecureRandom().nextBytes(this.iv); // Generate  16 bit random IV
        this.cipher = initCipher(Cipher.ENCRYPT_MODE);

    }

    /*
     * Constructor to create a SessionCipher from a SessionKey and an IV,
     * given as a byte array.
     */

    public SessionCipher(SessionKey key, byte[] ivbytes) {
        this.sessionKey = key;
        this.iv = ivbytes.clone(); // Avoid modifying external reference
        this.cipher = initCipher(Cipher.ENCRYPT_MODE);
    }

// above together i think are the init action

    public SessionKey getSessionKey() {
        return this.sessionKey;
    }

    /*
     * Return the IV as a byte array
     */
    public byte[] getIVBytes() {
        return this.iv.clone();
    }

    /*
     * Attach OutputStream to which encrypted data will be written.
     * Return result as a CipherOutputStream instance.
     */
    CipherOutputStream openEncryptedOutputStream(OutputStream os) {
        return new CipherOutputStream(os, cipher);
    }

    /*
     * Attach InputStream from which decrypted data will be read.
     * Return result as a CipherInputStream instance.
     */

    CipherInputStream openDecryptedInputStream(InputStream inputstream) {
        try {
            Cipher decryptCipher = Cipher.getInstance("AES/CTR/NoPadding");
            decryptCipher.init(Cipher.DECRYPT_MODE, sessionKey.getSecretKey(), new IvParameterSpec(iv));
            return new CipherInputStream(inputstream, decryptCipher);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create decryption stream", e);
        }
    }
}   //init the cipher every time
