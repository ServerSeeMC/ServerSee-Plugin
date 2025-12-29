package cn.lemwood.serversee.auth;

import java.io.File;
import java.io.IOException;
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

    public String getCurrentToken() {
        return currentToken;
    }
}
