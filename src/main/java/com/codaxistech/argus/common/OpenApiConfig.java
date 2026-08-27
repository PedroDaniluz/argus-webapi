package com.codaxistech.argus.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";
    public static final String DEVICE_KEY_SCHEME = "deviceKey";

    @Bean
    OpenAPI argusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Argus WebAPI")
                        .version("v1")
                        .description("""
                                Receives positions from embedded devices, stores them and serves \
                                the web dashboard.

                                Two authentication schemes: users carry a JWT bearer token, devices \
                                send the X-Device-Key header on /api/ingest/**."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token returned by POST /auth/login."))
                        .addSecuritySchemes(DEVICE_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(DeviceAuthFilter.HEADER)
                                .description("Opaque device key, shaped as <code>.<secret>, "
                                        + "shown only once when the device is created.")));
    }
}
