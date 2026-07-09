package org.hai.work.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;

public class JwtUtil {
    public static String createToken() {
        String data;
        String signatureEncoded;
        try {
            // Private key
            String privateKeyString = "-----BEGIN PRIVATE KEY-----\n" +
                    System.getenv("JWT_PRIVATE_KEY") + "\n" +
                    "-----END PRIVATE KEY-----";
            privateKeyString = privateKeyString.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").trim();
            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyString);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("EdDSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            // Header
            String headerJson = "{\"alg\": \"EdDSA\", \"kid\": \"KNB28AVV7V\"}";
            // Payload
            long iat = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond() - 30;
            long exp = iat + 900;
            String payloadJson = "{\"sub\": \"3CTM75KT93\", \"iat\": " + iat + ", \"exp\": " + exp + "}";
            // Base64url header+payload
            String headerEncoded = Base64.getUrlEncoder().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String payloadEncoded = Base64.getUrlEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            data = headerEncoded + "." + payloadEncoded;
            // Sign
            Signature signer = Signature.getInstance("EdDSA");
            signer.initSign(privateKey);
            signer.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signature = signer.sign();

            signatureEncoded = Base64.getUrlEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return "Bearer " + data + "." + signatureEncoded;
    }
}
