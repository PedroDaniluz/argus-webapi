package com.codaxistech.argus.common;

import java.util.UUID;

/** A device resolved from the X-Device-Key header. */
public record AuthenticatedDevice(UUID id, String code) {}
