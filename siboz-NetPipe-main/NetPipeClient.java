import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;

public class NetPipeClient {
    private static Arguments arguments;

    private static SessionCipher clientCipher;

    public static void main(String[] args) {
        try {
            // read the terminal input
            parseArgs(args);
            String host = arguments.get("host");
            int port = Integer.parseInt(arguments.get("port"));

            try (Socket socket = new Socket(host, port)) {
                System.out.println("Connected to server at " + host + ":" + port);
                HandshakeClient(socket);
                forwardData(socket);
            } // 在这里尝试进行握手和数据的传输，注意，传输之前先加密。(handshake and communication
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
            System.exit(1);
        }
    }  // compares to the template combine the handshake and forwoarding together

    private static void parseArgs(String[] args) {
        arguments = new Arguments();
        arguments.setArgumentSpec("host", "hostname");
        arguments.setArgumentSpec("port", "portnumber");
        arguments.setArgumentSpec("usercert", "user certification file");
        arguments.setArgumentSpec("cacert", "CA certification file");
        arguments.setArgumentSpec("key", "private key file");
// 这里要严格按照题目要求来
        try {
            arguments.loadArguments(args);
        } catch (IllegalArgumentException ex) {
            usage();
        }
    }
    // same as the server, it seems to more free to use this type.
    private static void usage() {
        System.err.println("Usage: NetPipeClient --host=<hostname> --port=<portnumber> --usercert=<usercertification> --cacert=<cacertification> --key=<privatekey>");
        System.exit(1);
    }

    private static void HandshakeClient(Socket socket) throws Exception {
        //  still, exchange the public key
        HandshakeMessage clientHello = new HandshakeMessage(HandshakeMessage.MessageType.CLIENTHELLO);
        HandshakeDigest localDigest = new HandshakeDigest(); // 用于验证收到的消息
        HandshakeDigest signDigest = new HandshakeDigest();  // 用于生成和验证签名
        String clientCert = Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(arguments.get("usercert"))));
        clientHello.putParameter("Certificate", clientCert);
        clientHello.send(socket);
        // here client hello for sendback digest
        signDigest.update(clientHello.getBytes());


        //  look how we deal with the public key
        HandshakeMessage serverHello = HandshakeMessage.recv(socket);

        HandshakeCertificate serverCert = new HandshakeCertificate(Base64.getDecoder().decode(serverHello.getParameter("Certificate")));

        HandshakeCertificate caCert = new HandshakeCertificate(Files.newInputStream(Paths.get(arguments.get("cacert"))));
        // use ca for verify
        serverCert.verify(caCert);
        // here server hello for the local signature verify
        localDigest.update(serverHello.getBytes());
        System.out.println("CLIENT: Verified SERVERHELLO certificate.");

        // give out the session key
        SessionKey SessionKey = new SessionKey(128);
        clientCipher = new SessionCipher(SessionKey);
        System.out.println("CLIENT: Generated session key and initialized cipher.");

        byte[] SessionKeyBytes = SessionKey.getKeyBytes();  // 获取未加密的SessionKey
        byte[] SessionIVBytes = clientCipher.getIVBytes();  // 获取未加密的SessionIV

        System.out.println("CLIENT: SessionKey (raw bytes before encryption): " + Base64.getEncoder().encodeToString(SessionKeyBytes));
        System.out.println("CLIENT: SessionIV (raw bytes before encryption): " + Base64.getEncoder().encodeToString(SessionIVBytes));

        HandshakeCrypto encryptor = new HandshakeCrypto(serverCert);
        byte[] encryptedKey = encryptor.encrypt(SessionKeyBytes);
        byte[] encryptedIV = encryptor.encrypt(SessionIVBytes);
        System.out.println("CLIENT: SessionKey: " + Base64.getEncoder().encodeToString(encryptedKey));
        System.out.println("CLIENT: SessionIV: " + Base64.getEncoder().encodeToString(encryptedIV));

        HandshakeMessage sessionMsg = new HandshakeMessage(HandshakeMessage.MessageType.SESSION);
        sessionMsg.putParameter("SessionKey", Base64.getEncoder().encodeToString(encryptedKey));
        sessionMsg.putParameter("SessionIV", Base64.getEncoder().encodeToString(encryptedIV));
        sessionMsg.send(socket);
        // here sessionmsg for sendback digest
        signDigest.update(sessionMsg.getBytes());
        System.out.println("CLIENT: Sent SESSION message.");

        //    receive the server signature (handshake1) decrypt and verify it.
        HandshakeMessage serverFinished = HandshakeMessage.recv(socket);

        System.out.printf("    MessageType = %s\n", serverFinished.getType());
        System.out.printf("    Signature = %s (%d bytes)\n", serverFinished.getParameter("Signature"), serverFinished.getParameter("Signature").length());
        System.out.printf("    TimeStamp = %s\n", serverFinished.getParameter("TimeStamp"));
        if (serverFinished.getType() != HandshakeMessage.MessageType.SERVERFINISHED) {
            throw new Exception("Expected SERVERFINISHED message");
        }

        // Signature and Digest
        byte[] Signature = Base64.getDecoder().decode(serverFinished.getParameter("Signature"));
        byte[] TimeStamp = Base64.getDecoder().decode(serverFinished.getParameter("TimeStamp"));
        HandshakeCrypto serverPublic = new HandshakeCrypto(serverCert);
        byte[] decryptedSignature = serverPublic.decrypt(Signature);
        byte[] decryptedTimeStamp = serverPublic.decrypt(TimeStamp);




        byte[] computedDigest = localDigest.digest();  // 保存结果
        System.out.println("CLIENT: Computed digest: " + Base64.getEncoder().encodeToString(computedDigest));
        System.out.println("CLIENT: Decrypted Signature: " + Base64.getEncoder().encodeToString(decryptedSignature));
        if (!Arrays.equals(decryptedSignature, computedDigest)) {
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





        //  Generate CLIENTFINISHED message sendback

        HandshakeMessage clientFinished = new HandshakeMessage(HandshakeMessage.MessageType.CLIENTFINISHED);

        // Generate timestamp
        String TimeStampout = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        byte[] TimeStampBytes = TimeStampout.getBytes("UTF-8");

        // Generate handshake digest



        byte[] privateKeyBytes = Files.readAllBytes(Paths.get(arguments.get("key")));
        System.out.println("SERVER: Sending CLIENTFINISHED message.");
        byte[] outputDigest = signDigest.digest(); // to get rid of the change

        System.out.println("SERVER: Computed digest: " + Base64.getEncoder().encodeToString(outputDigest));



        // Create signature using server's private key
        HandshakeCrypto clientPrivate = new HandshakeCrypto(privateKeyBytes);
        byte[] Signatureout = clientPrivate.encrypt(outputDigest);
        System.out.println("SERVER: Encrypted Signature : " + Base64.getEncoder().encodeToString(Signatureout));
        byte[] TimeStampencrypt = clientPrivate.encrypt(TimeStampBytes);
        // Remember: the timestamp and the signature is apart
        clientFinished.putParameter("Signature", Base64.getEncoder().encodeToString(Signatureout));
        clientFinished.putParameter("TimeStamp", Base64.getEncoder().encodeToString(TimeStampencrypt));
        clientFinished.send(socket);





        System.out.println("CLIENT: Handshake completed successfully.");
    }





    private static void forwardData(Socket socket) throws IOException {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {
            // 包装加密和解密流， 这里用了Session cipher方法，输出的是解密数据而不是明文
            InputStream encryptedIn = clientCipher.openDecryptedInputStream(in);
            OutputStream encryptedOut = clientCipher.openEncryptedOutputStream(out);

            // 使用加密流进行数据转发
            Forwarder.forwardStreams(System.in, System.out, encryptedIn, encryptedOut, socket);
        }
    }
}
