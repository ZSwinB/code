import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;

public class NetPipeServer {
    private static Arguments arguments;
    private static SessionCipher serverCipher;

    public static void main(String[] args) {
        try {
            // read the terminal input
            parseArgs(args);
            int port = Integer.parseInt(arguments.get("port"));

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.printf("Server listening on port %d%n", port);
                try (Socket socket = serverSocket.accept()) {
                    System.out.println("Client connected");
                    HandshakeServer(socket);
                    forwardData(socket);
                }
            }
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
            System.exit(1);
        }
    } // 和client一样，the forwarding part was set to the forwardData part.

    private static void parseArgs(String[] args) {
        arguments = new Arguments();
        arguments.setArgumentSpec("port", "portnumber");
        arguments.setArgumentSpec("usercert", "server certification file");
        arguments.setArgumentSpec("cacert", "CA certification file");
        arguments.setArgumentSpec("key", "private key file");

        try {
            arguments.loadArguments(args);
        } catch (IllegalArgumentException ex) {
            usage();
        }
    }
    // I convert the usage to a lighter way just before I realize it's a frame , but anyway......
    private static void usage() {
        System.err.println("Usage: NetPipeServer --port=<portnumber> --usercert=<servercertification> --cacert=<cacertification> --key=<privatekey>");
        System.exit(1);
    }

    private static void HandshakeServer(Socket socket) throws Exception {
        // Create the certiicate, and the part  is the example of how to create one
        HandshakeMessage clientHello = HandshakeMessage.recv(socket);
        HandshakeCertificate clientCert = new HandshakeCertificate(Base64.getDecoder().decode(clientHello.getParameter("Certificate")));
        HandshakeCertificate caCert = new HandshakeCertificate(Files.newInputStream(Paths.get(arguments.get("cacert"))));
        clientCert.verify(caCert);

        // give out the public key to the client
        HandshakeMessage serverHello = new HandshakeMessage(HandshakeMessage.MessageType.SERVERHELLO);
        String serverCert = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(arguments.get("usercert"))));
        serverHello.putParameter("Certificate", serverCert);
        serverHello.send(socket); //  the action that gives out it's public key

        //  Remember the case! sessionkey is wrong
        HandshakeMessage sessionMsg = HandshakeMessage.recv(socket);
        byte[] encryptedKey = Base64.getDecoder().decode(sessionMsg.getParameter("SessionKey"));
        byte[] encryptedIV = Base64.getDecoder().decode(sessionMsg.getParameter("SessionIV"));

        // Decrypt SessionKey and IV using Private Key
        byte[] privateKeyBytes = Files.readAllBytes(Paths.get(arguments.get("key")));
        HandshakeCrypto decryptor = new HandshakeCrypto(privateKeyBytes);
        byte[] SessionKeyBytes = decryptor.decrypt(encryptedKey);
        byte[] SessionIV = decryptor.decrypt(encryptedIV);


        if (SessionKeyBytes.length != 16) {
            throw new IllegalArgumentException("Key byte array must be 16, 24, or 32 bytes for AES.");
        }

        // generate the session key
        SessionKey SessionKey = new SessionKey(SessionKeyBytes);
        serverCipher = new SessionCipher(SessionKey, SessionIV);


        // Most important part!!!!
        HandshakeMessage serverFinished = new HandshakeMessage(HandshakeMessage.MessageType.SERVERFINISHED);

        // Generate timestamp
        String TimeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        byte[] TimeStampBytes = TimeStamp.getBytes("UTF-8");

        // Generate handshake digest

        HandshakeDigest signdigest = new HandshakeDigest();



        signdigest.update(serverHello.getBytes());



        System.out.println("SERVER: Sending SERVERFINISHED message.");
        byte[] computedDigest = signdigest.digest(); // to get rid of the change
        System.out.println("SERVER: Computed digest: " + Base64.getEncoder().encodeToString(computedDigest));



        // Create signature using server's private key
        HandshakeCrypto serverPrivate = new HandshakeCrypto(privateKeyBytes);
        byte[] Signature = serverPrivate.encrypt(computedDigest);
        // System.out.println("SERVER: Encrypted Signature : " + Base64.getEncoder().encodeToString(Signature));
        byte[] TimeStampencrypt = serverPrivate.encrypt(TimeStampBytes);
        // Remember: the timestamp and the signature is apart
        serverFinished.putParameter("Signature", Base64.getEncoder().encodeToString(Signature));
        serverFinished.putParameter("TimeStamp", Base64.getEncoder().encodeToString(TimeStampencrypt));
        serverFinished.send(socket);


        HandshakeDigest localDigest = new HandshakeDigest();
        HandshakeMessage clientFinished = HandshakeMessage.recv(socket);

        // System.out.printf("    MessageType = %s\n", clientFinished.getType());
        // System.out.printf("    Signature = %s (%d bytes)\n", clientFinished.getParameter("Signature"), clientFinished.getParameter("Signature").length());
        // System.out.printf("    TimeStamp = %s\n", clientFinished.getParameter("TimeStamp"));
        if (clientFinished.getType() != HandshakeMessage.MessageType.CLIENTFINISHED) {
            throw new Exception("Expected SERVERFINISHED message");
        }

        //  Verify Signature and Digest，这里开始进行digest gives back to server 的验证
        byte[] Signaturecheck = Base64.getDecoder().decode(clientFinished.getParameter("Signature"));
        byte[] TimeStampcheck = Base64.getDecoder().decode(clientFinished.getParameter("TimeStamp"));
        HandshakeCrypto clientPublic = new HandshakeCrypto(clientCert);
        byte[] decryptedSignature = clientPublic.decrypt(Signaturecheck);
        byte[] decryptedTimeStamp = clientPublic.decrypt(TimeStampcheck);


        localDigest.update(clientHello.getBytes());
        localDigest.update(sessionMsg.getBytes());
        byte[] checkDigest = localDigest.digest();  // 保存结果
        System.out.println("CLIENT: Computed digest for the client: " + Base64.getEncoder().encodeToString(checkDigest));
        System.out.println("CLIENT: Decrypted Signature from client: " + Base64.getEncoder().encodeToString(decryptedSignature));
        if (!Arrays.equals(decryptedSignature, checkDigest)) {
            throw new Exception("Server signature validation failed.");
        }
        System.out.println("CLIENT: Server signature validated successfully.");

        //  if (!java.util.Arrays.equals(decryptedSignature, computedDigest)) {
        //     new Exception("Debugging stack trace for Invalid server Signature").printStackTrace();
        //     throw new Exception("Invalid server Signature");
        //  }







        // 将解码后的字节数组转换为 UTF-8 字符串
        String receivedTimeString = new String(decryptedTimeStamp, "UTF-8");

        // 打印调试日志，检查解码后的字符串
        System.out.println("Decoded TimeStamp String: " + receivedTimeString);

        try {
            // 使用解码后的字符串解析 LocalDateTime
            LocalDateTime receivedTime = LocalDateTime.parse(receivedTimeString,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));



            // 获取当前时间
            LocalDateTime now = LocalDateTime.now();

            // 验证时间戳的时效性
            if (receivedTime.isBefore(now.minusSeconds(20)) || receivedTime.isAfter(now.plusSeconds(20))) {
                throw new Exception("Server TimeStamp validation failed");
            }
            System.out.println("TimeStamp validation passed.");
        } catch (Exception e) {
            System.err.println("Failed to parse or validate TimeStamp: " + e.getMessage());
            throw e; // 重新抛出异常以便追踪问题
        }


        System.out.println("Handshake completed successfully");
    }


    private static void forwardData(Socket socket) throws IOException {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {
            // 包装加密流和解密流
            InputStream encryptedIn = serverCipher.openDecryptedInputStream(in);
            OutputStream encryptedOut = serverCipher.openEncryptedOutputStream(out);

            // 使用加密流进行数据转发
            Forwarder.forwardStreams(System.in, System.out, encryptedIn, encryptedOut, socket);
        }

    }
}
