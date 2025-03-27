import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
//edit in 2024 11 30
public class FileDigest {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java FileDigest <filename>");
            return;
        }

        File file = new File(args[0]);
        if (!file.exists() || !file.isFile() || !file.canRead())
        {

            System.err.println("Error: Provided file is invalid or does not exist.");
            return;
        }

        try (FileInputStream inputStream = new FileInputStream(file)) {
            HandshakeDigest digest = new HandshakeDigest();
            byte[] buffer = new byte[2048];
            int len;
            while ((len = inputStream.read(buffer)) != -1)
            {
                byte[] chunk = new byte[len]; // Copy only the valid part
                System.arraycopy(buffer, 0, chunk, 0, len);
                digest.update(chunk); // Pass the valid part to the update method
            }
            byte[] finalHash = digest.digest();
            String hashString = Base64.getEncoder().encodeToString(finalHash);
            System.out.println(hashString);
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
        }
    }
}
