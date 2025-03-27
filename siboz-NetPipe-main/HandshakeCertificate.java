import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * HandshakeCertificate class represents X509 certificates exchanged
 * during initial handshake
 */
public class HandshakeCertificate {

    private X509Certificate certificate;

    /*
     * Constructor to create a certificate from data read on an input stream.
     * The data is DER-encoded, in binary or Base64 encoding (PEM format).
     */
    HandshakeCertificate(InputStream instream) throws CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        this.certificate = (X509Certificate) factory.generateCertificate(instream);
    }

    /*
     * Constructor to create a certificate from its encoded representation
     * given as a byte array
     */
    HandshakeCertificate(byte[] certbytes) throws CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        InputStream byteArrayStream = new java.io.ByteArrayInputStream(certbytes);
        this.certificate = (X509Certificate) factory.generateCertificate(byteArrayStream);
    }

    /*
     * Return the encoded representation of certificate as a byte array
     */
    public byte[] getBytes() throws CertificateEncodingException {
        return this.certificate.getEncoded();
    }

    /*
     * Return the X509 certificate
     */
    public X509Certificate getCertificate() {
        return this.certificate;
    }

    /*
     * Cryptographically validate a certificate.
     * Throw relevant exception if validation fails.
     */
    public void verify(HandshakeCertificate cacert) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        try {
            this.certificate.verify(cacert.getCertificate().getPublicKey());
        } catch (NoSuchProviderException e) {
            throw new RuntimeException("No such provider for certificate verification", e);
        }
    }

    /*
     * Return CN (Common Name) of subject
     */
    public String getCN() {
        String distinguishedName = certificate.getSubjectX500Principal().getName();
        return extractField(distinguishedName, "CN");
    }

    /*
     * Return email address of subject
     */
    public String getEmail() {
        // Get the Subject's Distinguished Name (DN) string
        String dn = certificate.getSubjectX500Principal().getName(X500Principal.RFC1779);

        // Use a regular expression to search for the OID.1.2.840.113549.1.9.1= field in RFC1779 format
        Pattern pattern = Pattern.compile("OID\\.1\\.2\\.840\\.113549\\.1\\.9\\.1=([^,]+)");
        Matcher matcher = pattern.matcher(dn);
        if (matcher.find()) {
            return matcher.group(1).trim(); // Return the found email address
        }

        // If not found, get the default DN format and split into parts
        dn = certificate.getSubjectX500Principal().getName();
        String[] parts = dn.split(",");

        for (String part : parts) {
            part = part.trim();
            // Check for the 1.2.840.113549.1.9.1= field in the default format
            if (part.startsWith("1.2.840.113549.1.9.1=")) {
                String encodedEmail = part.substring(part.indexOf('=') + 1).trim();
                // If the value is hex-encoded, decode it
                if (encodedEmail.startsWith("#")) {
                    return decodeHexString(encodedEmail);
                }
                return encodedEmail; // Return the non-encoded email address
            }
        }

        return null; // Return null if no email is found
    }


    /*
     * Helper method to extract fields like CN or EMAILADDRESS from the distinguished name
     */
    private String extractField(String distinguishedName, String field) {
        Pattern pattern = Pattern.compile(field + "=([^,]+)");
        Matcher matcher = pattern.matcher(distinguishedName);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /*
     * Helper method to decode hexadecimal string (for email address decoding)
     */

// 转化到十六进制格式
    private String decodeHexString(String hex) {

        if (hex.startsWith("#16")) {
            hex = hex.substring(2);
        }

        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return new String(bytes);
    }
}
