package cn.lemwood.serversee.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Logger;

public class TokenManager {
    private static final String TOKEN_FILE = "token.txt";
    private static final String TOKEN_PREFIX = "serversee_";
    private final File tokenFile;
    private final Logger logger;
    private String currentToken;

    public TokenManager(File dataFolder, Logger logger) {
        this.tokenFile = new File(dataFolder, TOKEN_FILE);
        this.logger = logger;
        loadOrGenerateToken();
    }

    private void loadOrGenerateToken() {
        if (tokenFile.exists()) {
            try {
                String content = new String(Files.readAllBytes(tokenFile.toPath())).trim();
                if (content.startsWith(TOKEN_PREFIX)) {
                    this.currentToken = content;
                    return;
                }
            } catch (IOException e) {
                logger.severe("无法读取 token.txt: " + e.getMessage());
            }
        }

        // 生成新 Token
        this.currentToken = generateRandomToken();
        try {
            Files.write(tokenFile.toPath(), currentToken.getBytes());
            logger.info("已生成新的身份验证 Token 并保存至 token.txt");
        } catch (IOException e) {
            logger.severe("无法保存 Token 到文件: " + e.getMessage());
        }
    }

    private String generateRandomToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public boolean validate(String token) {
        if (token == null || currentToken == null) return false;
        return currentToken.equals(token);
    }

    /**
     * 验证基于 HMAC-SHA256 的签名
     * @param signature 客户端传来的签名
     * @param dataToSign 待签名的数据 (action + timestamp + nonce + data_json)
     * @return 验证是否通过
     */
    public boolean validateSignature(String signature, String dataToSign) {
        if (signature == null || dataToSign == null || currentToken == null) return false;
        try {
            String expected = calculateHMAC(dataToSign, currentToken);
            return expected.equals(signature);
        } catch (Exception e) {
            logger.warning("签名验证出错: " + e.getMessage());
            return false;
        }
    }

    private String calculateHMAC(String data, String key) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(rawHmac);
    }

    public String getCurrentToken() {
        return currentToken;
    }
}
