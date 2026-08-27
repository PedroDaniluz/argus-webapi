package com.codaxistech.argus.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "users", description = "Dashboard accounts")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService service;

    UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "listUsers", summary = "List accounts")
    public List<UserDtos.Response> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "createUser", summary = "Create an account", description = "There is no self sign-up.")
    public ResponseEntity<UserDtos.Response> create(@Valid @RequestBody UserDtos.CreateRequest request) {
        UserDtos.Response created = service.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }
}
