import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
//update version
public class HandshakeDigest {


    private final MessageDigest md;
    /*
     * Constructor -- initialise a digest for SHA-256
     */

    public HandshakeDigest() {

        try {
            this.md = MessageDigest.getInstance("SHA-256"); //use sha256
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to find SHA-256 MessageDigest", e);
        }

    }


     // Update

    public void update(byte[] input)
    {
        md.update(input);
    }


     // Compute final

    public byte[] digest()
    {

        return md.digest();
    }
}
