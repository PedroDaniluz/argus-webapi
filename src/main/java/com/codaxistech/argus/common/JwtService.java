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
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Emissao de access e refresh token, assinados com RSA.
 *
 * <p>A claim {@code tv} carrega o {@code token_version} do usuario. Quem valida
 * compara com o banco: incrementar a coluna invalida na hora todos os tokens
 * daquele usuario, sem precisar de blacklist.
 *
 * <p>A chave privada vem do ambiente. Sem ela, um par efemero e gerado na subida:
 * a aplicacao funciona, mas todo token emitido morre junto com o processo e duas
 * instancias nao validam o token uma da outra.
 */
@Service
public class JwtService {

    /** Distingue o refresh do access: um refresh nao serve para chamar a API. */
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
    private final java.security.interfaces.RSAPublicKey publicKey;
    private final JwtEncoder encoder;
    /** Decoder do refresh: assina/valida igual, mas exige typ=refresh. */
    private final NimbusJwtDecoder refreshDecoder;

    JwtService(@Value("${argus.jwt.issuer}") String issuer,
               @Value("${argus.jwt.access-ttl}") Duration accessTtl,
               @Value("${argus.jwt.refresh-ttl}") Duration refreshTtl,
               @Value("${argus.jwt.private-key:}") String privateKeyPem,
               @Value("${argus.jwt.public-key:}") String publicKeyPem) {
        this.issuer = issuer;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;

        java.security.interfaces.RSAPublicKey pub;
        java.security.interfaces.RSAPrivateKey priv;
        if (hasText(privateKeyPem) && hasText(publicKeyPem)) {
            priv = readPrivateKey(privateKeyPem);
            pub = readPublicKey(publicKeyPem);
        } else {
            log.warn("JWT_PRIVATE_KEY/JWT_PUBLIC_KEY ausentes: gerando par RSA efemero. "
                    + "Todo token emitido perde a validade quando a aplicacao reiniciar.");
            KeyPair pair = generateKeyPair();
            pub = (java.security.interfaces.RSAPublicKey) pair.getPublic();
            priv = (java.security.interfaces.RSAPrivateKey) pair.getPrivate();
        }
        this.publicKey = pub;
        this.encoder = NimbusJwtEncoder.withKeyPair(pub, priv)
                .algorithm(SignatureAlgorithm.RS256)
                .build();
        this.refreshDecoder = NimbusJwtDecoder.withPublicKey(pub).build();
        this.refreshDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    }

    /**
     * Valida um refresh token: assinatura, validade, emissor e {@code typ}.
     * A conferencia da versao contra o banco fica com quem chamou.
     *
     * @throws org.springframework.security.oauth2.jwt.JwtException se nao prestar
     */
    public Jwt decodeRefresh(String token) {
        Jwt jwt = refreshDecoder.decode(token);
        if (!TYPE_REFRESH.equals(jwt.getClaimAsString(CLAIM_TYPE))) {
            throw new BadJwtException("token nao e um refresh token");
        }
        return jwt;
    }

    public java.security.interfaces.RSAPublicKey publicKey() {
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
            throw new IllegalStateException("nao foi possivel gerar o par RSA", e);
        }
    }

    private static java.security.interfaces.RSAPrivateKey readPrivateKey(String pem) {
        try {
            byte[] der = decodePem(pem);
            return (java.security.interfaces.RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "argus.jwt.private-key nao e uma chave RSA PKCS#8 valida", e);
        }
    }

    private static java.security.interfaces.RSAPublicKey readPublicKey(String pem) {
        try {
            byte[] der = decodePem(pem);
            return (java.security.interfaces.RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "argus.jwt.public-key nao e uma chave RSA X.509 valida", e);
        }
    }

    /**
     * Aceita PEM com cabecalho/rodape e com as quebras de linha achatadas em
     * {@code \n} literal, que e como uma chave costuma chegar por variavel de
     * ambiente.
     */
    private static byte[] decodePem(String pem) {
        String body = pem.replace("\n", "\n")
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\s", "");
        return Base64.getDecoder().decode(body);
    }
}
