package com.codaxistech.argus.common;

import com.codaxistech.argus.user.Role;

import java.util.UUID;

/** A user resolved from their credentials. No password hash leaves the user package. */
public record AuthenticatedUser(UUID id, String email, String name, Role role, int tokenVersion) {}
