package org.hai.work;

import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;

public class A {
    static void main() throws Exception {
// Private key
        String privateKeyString = "-----BEGIN PRIVATE KEY-----\n" +
                "MC4CAQAwBQYDK2VwBCIEID3A4VcSnXUQuG1rb7CFPMHWflfSk0Z4mYcIkRIGctvF\n" +
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
        String data = headerEncoded + "." + payloadEncoded;

// Sign
        Signature signer = Signature.getInstance("EdDSA");
        signer.initSign(privateKey);
        signer.update(data.getBytes(StandardCharsets.UTF_8));
        byte[] signature = signer.sign();

        String signatureEncoded = Base64.getUrlEncoder().encodeToString(signature);

        String jwt = data + "." + signatureEncoded;

// Print Token
        System.out.println("Signature:\n" + signatureEncoded);
        System.out.println("JWT:\n" + jwt);

// Print Token
        System.out.println("Signature:\n" + signatureEncoded);
        System.out.println("JWT:\n" + jwt);
        String result1 = HttpRequest.get("https://q53qqjvuay.re.qweatherapi.com/v7/weather/3d?location=101020600")
                .header(Header.AUTHORIZATION, "Bearer eyJhbGciOiAiRWREU0EiLCAia2lkIjogIktOQjI4QVZWN1YifQ==.eyJzdWIiOiAiM0NUTTc1S1Q5MyIsICJpYXQiOiAxNzgyMjY4MjIyLCAiZXhwIjogMTc4MjI2OTEyMn0=.Fbz-9ZGOxLLJi9cq3Qv0xjYTucrpUX4FZIfjU-SYcua0HPIPHjyugRkwOO17CGZyIUpkFwYRguHHMnPf3yAzDQ==").timeout(20000)//超时，毫秒
                .execute().body();

        System.out.println(result1);
    }
}
