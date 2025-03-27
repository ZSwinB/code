import javax.crypto.Cipher;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

public class HandshakeCrypto {
	private final Key key;

	// Constructor with public key from certificate
	public HandshakeCrypto(HandshakeCertificate handshakeCertificate) {
		this.key = handshakeCertificate.getCertificate().getPublicKey();
	}

	// Constructor with private key in PKCS8/DER format
	public HandshakeCrypto(byte[] keybytes) {
		try {
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keybytes);
			this.key = keyFactory.generatePrivate(keySpec);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException("Failed to initialize private key", e);
		}
	}

	// Encrypt method
	public byte[] encrypt(byte[] plaintext) {
		try {
			Cipher cipher = Cipher.getInstance("RSA");
			cipher.init(Cipher.ENCRYPT_MODE, this.key);
			return cipher.doFinal(plaintext);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException("Encryption failed", e);
		}
	}

	// Decrypt method
	public byte[] decrypt(byte[] ciphertext) {
		try {
			Cipher cipher = Cipher.getInstance("RSA");
			cipher.init(Cipher.DECRYPT_MODE, this.key);
			return cipher.doFinal(ciphertext);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException("Decryption failed", e);
		}
	}
}
