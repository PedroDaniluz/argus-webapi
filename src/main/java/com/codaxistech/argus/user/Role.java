package com.codaxistech.argus.user;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code enumAsRef} keeps this one shared schema instead of inlining a copy per DTO. */
@Schema(enumAsRef = true)
public enum Role {
    ADMIN, OPERATOR, VIEWER
}
