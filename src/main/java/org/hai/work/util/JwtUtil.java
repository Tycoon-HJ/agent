package org.hai.work.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;

/**
 * JWT 工具类
 * <p>
 * 用于生成和风天气 API 鉴权所需的 JWT Token。
 * 使用 EdDSA (Ed25519) 算法签名。
 */
public class JwtUtil {

    /**
     * 环境变量名：JWT 私钥（Base64 编码的 PKCS8 格式）
     */
    private static final String ENV_JWT_PRIVATE_KEY = "JWT_PRIVATE_KEY";

    /**
     * 环境变量名：JWT Key ID
     */
    private static final String ENV_JWT_KID = "JWT_KID";

    /**
     * 环境变量名：JWT Subject
     */
    private static final String ENV_JWT_SUB = "JWT_SUB";

    /**
     * JWT 有效期（秒），默认 15 分钟
     */
    private static final long TOKEN_VALIDITY_SECONDS = 900;

    /**
     * JWT 时钟偏移（秒），用于处理时间差
     */
    private static final long CLOCK_SKEW_SECONDS = 30;

    /**
     * 创建 JWT Token
     *
     * @return Bearer 格式的 JWT Token
     * @throws IllegalStateException 当环境变量缺失时
     * @throws RuntimeException      当签名失败时
     */
    public static String createToken() {
        try {
            // 读取并校验环境变量
            String privateKeyBase64 = System.getenv(ENV_JWT_PRIVATE_KEY);
            String kid = System.getenv(ENV_JWT_KID);
            String sub = System.getenv(ENV_JWT_SUB);

            if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
                throw new IllegalStateException("环境变量 " + ENV_JWT_PRIVATE_KEY + " 未设置");
            }
            if (kid == null || kid.isBlank()) {
                throw new IllegalStateException("环境变量 " + ENV_JWT_KID + " 未设置");
            }
            if (sub == null || sub.isBlank()) {
                throw new IllegalStateException("环境变量 " + ENV_JWT_SUB + " 未设置");
            }

            // 解析私钥
            String privateKeyPem = privateKeyBase64
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "")
                    .trim();
            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyPem);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("EdDSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

            // 构建 Header（使用 withoutPadding 避免 Base64 填充字符破坏 JWT 格式）
            // 转义 JSON 特殊字符防止注入
            String safeKid = kid.replace("\\", "\\\\").replace("\"", "\\\"");
            String safeSub = sub.replace("\\", "\\\\").replace("\"", "\\\"");
            String headerJson = "{\"alg\":\"EdDSA\",\"kid\":\"" + safeKid + "\"}";
            String headerEncoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));

            // 构建 Payload
            long iat = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond() - CLOCK_SKEW_SECONDS;
            long exp = iat + TOKEN_VALIDITY_SECONDS;
            String payloadJson = "{\"sub\":\"" + safeSub + "\",\"iat\":" + iat + ",\"exp\":" + exp + "}";
            String payloadEncoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

            // 签名
            String data = headerEncoded + "." + payloadEncoded;
            Signature signer = Signature.getInstance("EdDSA");
            signer.initSign(privateKey);
            signer.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signature = signer.sign();
            String signatureEncoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(signature);

            return "Bearer " + data + "." + signatureEncoded;

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("JWT Token 创建失败: " + e.getMessage(), e);
        }
    }
}
