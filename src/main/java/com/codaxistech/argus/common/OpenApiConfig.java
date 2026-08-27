package com.codaxistech.argus.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacao: o springdoc gera o contrato em /v3/api-docs e serve o Swagger UI
 * em /swagger-ui.html.
 */
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
                                Recebe posicoes dos dispositivos embarcados, persiste e serve \
                                para o painel web.

                                Dois esquemas de autenticacao: usuarios usam JWT Bearer, \
                                dispositivos usam o header X-Device-Key em /api/ingest/**."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token devolvido por POST /auth/login."))
                        .addSecuritySchemes(DEVICE_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(DeviceAuthFilter.HEADER)
                                .description("Chave opaca do dispositivo, no formato "
                                        + "<code>.<secret>, exibida uma unica vez na criacao.")));
    }
}
