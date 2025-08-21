package com.gmwapp.hima;

import java.time.Instant;

public class TokenGenerator {

    private static int getRandom() {
        int random = (int) Math.round(Math.random() * 1000000000);
        return random;
    }

    private static long getTimestamp() {
        long timestamp = Instant.now().getEpochSecond();
        return timestamp;
    }

    public static String getToken() {
        int random = getRandom();
        long timestamp = getTimestamp();
        String secretKey = io.jsonwebtoken.impl.TextCodec.BASE64.encode("UTA5U1VEQXdNREF4VFZSSmVrNUVWVEpPZWxVd1RuYzlQUT09");
        String token = io.jsonwebtoken.Jwts.builder()
                .setIssuer("PSPRINT")
                .setHeaderParam("typ", "JWT")
                .setHeaderParam("alg", "HS256")
                .claim("iss", "PSPRINT") // not Compulsary
                .claim("timestamp", timestamp)
                .claim("partnerId", "CORP00001") // PARTNER ID
                .claim("product","WALLET") // not Compulsary
                .claim("reqid", random) // Random request no
                .signWith(io.jsonwebtoken.SignatureAlgorithm.HS256, secretKey)
                .compact();

        return token;
    }
}
