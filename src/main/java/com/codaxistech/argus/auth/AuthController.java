package com.codaxistech.argus.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "auth", description = "User token issuing and renewal")
public class AuthController {

    private final AuthService service;

    AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email and password for a token pair")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh token for a new pair")
    public AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return service.refresh(request);
    }
}
