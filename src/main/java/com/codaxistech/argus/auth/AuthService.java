package com.codaxistech.argus.auth;

import com.codaxistech.argus.common.JwtService;
import com.codaxistech.argus.user.UserDtos;
import com.codaxistech.argus.user.UserFacade;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AuthService {

    private final UserFacade users;
    private final JwtService jwt;

    AuthService(UserFacade users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        UserDtos.Account account = users.authenticate(request.email(), request.password())
                .orElseThrow(() -> unauthorized("invalid email or password"));
        return issue(account);
    }

    /** A refresh only holds while its token version still matches the database. */
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        Jwt token;
        try {
            token = jwt.decodeRefresh(request.refreshToken());
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token", e);
        }

        UUID userId = subjectOf(token);
        UserDtos.Account account = users.activeAccount(userId)
                .orElseThrow(() -> unauthorized("user does not exist or is disabled"));

        Object claimedVersion = token.getClaim(JwtService.CLAIM_TOKEN_VERSION);
        if (!(claimedVersion instanceof Number number)
                || number.intValue() != account.tokenVersion()) {
            throw unauthorized("refresh token revoked");
        }
        return issue(account);
    }

    private AuthDtos.TokenResponse issue(UserDtos.Account account) {
        String role = account.role().name();
        return new AuthDtos.TokenResponse(
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

    private static ResponseStatusException unauthorized(String reason) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
    }
}
