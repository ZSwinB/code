import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;

class SessionKey {

    private SecretKey secretKey;

    /*
     * Constructor to create a secret key of a given length
     */
    public SessionKey(Integer length) {
        try {
            if (length != 128 && length != 192 && length != 256) {
                throw new IllegalArgumentException("Key length must be 128, 192, or 256 bits.");
            }   // the key must be in legal length
            KeyGenerator keyGen = KeyGenerator.getInstance("AES"); // THE AES method is already given

            keyGen.init(length); // Initialize with the given key length

            this.secretKey = keyGen.generateKey(); // Generate the secret key
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("AES algorithm not available.", e);
        }
    }

    /*
     * Constructor to create a secret key from key material
     * given as a byte array
     */
    public SessionKey(byte[] keybytes) {
        if (keybytes == null || (keybytes.length != 16 && keybytes.length != 24 && keybytes.length != 32))
        {

            throw new IllegalArgumentException("Key byte array must be 16, 24, or 32 bytes for AES.");

        }   // still, be in legal length
        this.secretKey = new SecretKeySpec(keybytes, "AES"); // Create a SecretKey
    }

    /*
     * Return the secret key
     */
    public SecretKey getSecretKey() {
        return this.secretKey;
    }

    /*
     * Return the secret key encoded as a byte array
     */
    public byte[] getKeyBytes() {
        return this.secretKey.getEncoded();
    }
}
