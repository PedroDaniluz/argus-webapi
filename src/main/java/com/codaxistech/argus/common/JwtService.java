package com.codaxistech.argus.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * The {@code tv} claim carries the user's {@code token_version}, compared against
 * the database on every request: bumping the column revokes every token at once.
 */
@Service
public class JwtService {

    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_TOKEN_VERSION = "tv";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_EMAIL = "email";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final RSAPublicKey publicKey;
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder refreshDecoder;

    JwtService(@Value("${argus.jwt.issuer}") String issuer,
               @Value("${argus.jwt.access-ttl}") Duration accessTtl,
               @Value("${argus.jwt.refresh-ttl}") Duration refreshTtl,
               @Value("${argus.jwt.private-key:}") String privateKeyPem,
               @Value("${argus.jwt.public-key:}") String publicKeyPem) {
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;

        RSAPublicKey pub;
        RSAPrivateKey priv;
        if (hasText(privateKeyPem) && hasText(publicKeyPem)) {
            priv = readPrivateKey(privateKeyPem);
            pub = readPublicKey(publicKeyPem);
        } else {
            log.warn("JWT_PRIVATE_KEY/JWT_PUBLIC_KEY not set: generating an ephemeral RSA pair. "
                    + "Every issued token becomes invalid when the application restarts.");
            KeyPair pair = generateKeyPair();
            pub = (RSAPublicKey) pair.getPublic();
            priv = (RSAPrivateKey) pair.getPrivate();
        }
        this.publicKey = pub;
        this.encoder = NimbusJwtEncoder.withKeyPair(pub, priv)
                .algorithm(SignatureAlgorithm.RS256)
                .build();
        this.refreshDecoder = NimbusJwtDecoder.withPublicKey(pub).build();
        this.refreshDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public String issueAccess(UUID userId, String email, String role, int tokenVersion) {
        return issue(userId, email, role, tokenVersion, TYPE_ACCESS, accessTtl);
    }

    public String issueRefresh(UUID userId, String email, String role, int tokenVersion) {
        return issue(userId, email, role, tokenVersion, TYPE_REFRESH, refreshTtl);
    }

    /** Checks signature, expiry, issuer and type. Version check is the caller's job. */
    public Jwt decodeRefresh(String token) {
        Jwt jwt = refreshDecoder.decode(token);
        if (!TYPE_REFRESH.equals(jwt.getClaimAsString(CLAIM_TYPE))) {
            throw new BadJwtException("not a refresh token");
        }
        return jwt;
    }

    private String issue(UUID userId, String email, String role, int tokenVersion,
                         String type, Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("could not generate an RSA key pair", e);
        }
    }

    private static RSAPrivateKey readPrivateKey(String pem) {
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decodePem(pem)));
        } catch (Exception e) {
            throw new IllegalStateException("argus.jwt.private-key is not a valid PKCS#8 RSA key", e);
        }
    }

    private static RSAPublicKey readPublicKey(String pem) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decodePem(pem)));
        } catch (Exception e) {
            throw new IllegalStateException("argus.jwt.public-key is not a valid X.509 RSA key", e);
        }
    }

    /** Tolerates PEM headers and newlines flattened to a literal backslash-n, as env vars carry them. */
    private static byte[] decodePem(String pem) {
        String body = pem.replace("\\n", "\n")
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
