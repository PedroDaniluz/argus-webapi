package com.codaxistech.argus.auth;

import com.codaxistech.argus.common.JwtService;
import com.codaxistech.argus.common.AuthenticatedUser;
import com.codaxistech.argus.user.UserFacade;
import com.codaxistech.argus.common.DomainException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
class AuthService {

    private final UserFacade users;
    private final JwtService jwt;

    AuthService(UserFacade users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    TokenResponse login(LoginRequest request) {
        AuthenticatedUser account = users.authenticate(request.email(), request.password())
                .orElseThrow(() -> unauthorized("invalid email or password"));
        return issue(account);
    }

    /** A refresh only holds while its token version still matches the database. */
    TokenResponse refresh(RefreshRequest request) {
        Jwt token;
        try {
            token = jwt.decodeRefresh(request.refreshToken());
        } catch (JwtException e) {
            throw DomainException.unauthorized("Invalid refresh token");
        }

        UUID userId = subjectOf(token);
        AuthenticatedUser account = users.activeAccount(userId)
                .orElseThrow(() -> unauthorized("user does not exist or is disabled"));

        Object claimedVersion = token.getClaim(JwtService.CLAIM_TOKEN_VERSION);
        if (!(claimedVersion instanceof Number number)
                || number.intValue() != account.tokenVersion()) {
            throw unauthorized("refresh token revoked");
        }
        return issue(account);
    }

    private TokenResponse issue(AuthenticatedUser account) {
        String role = account.role().name();
        return new TokenResponse(
                jwt.issueAccess(account.id(), account.email(), role, account.tokenVersion()),
                jwt.issueRefresh(account.id(), account.email(), role, account.tokenVersion()),
                jwt.accessTtl().toSeconds());
    }

    private static UUID subjectOf(Jwt token) {
        try {
            return UUID.fromString(String.valueOf(token.getSubject()));
        } catch (IllegalArgumentException e) {
            throw unauthorized("invalid refresh token");
        }
    }

    private static DomainException unauthorized(String reason) {
        return DomainException.unauthorized(reason);
    }
}
