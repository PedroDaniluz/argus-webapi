package com.codaxistech.argus.common;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";
    public static final String DEVICE_KEY_SCHEME = "deviceKey";
    private static final String API_ERROR_SCHEMA = "ApiError";

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

    /**
     * Documents the failure side of every operation.
     *
     * Without this the contract only describes the happy path, so a generated
     * client types errors as {@code unknown} and each consumer re-invents the
     * error shape by hand. The bodies all come back as {@link ApiError} because
     * that is what {@link GlobalExceptionHandler} writes.
     */
    @Bean
    OpenApiCustomizer errorResponses() {
        return openApi -> {
            registerApiErrorSchema(openApi);

            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperationsMap().forEach((method, operation) -> {
                        // Anything that carries input can be rejected as malformed.
                        if (operation.getRequestBody() != null || operation.getParameters() != null) {
                            add(operation, "400", "Payload or parameters failed validation");
                        }
                        add(operation, "401", "Missing or invalid credentials");
                        // /auth/** is where you go to GET a credential, so there is
                        // no authorization step there to fail and nothing to collide
                        // with. Documenting 403/409 on it would describe responses
                        // the endpoint cannot produce.
                        boolean authenticated = !path.startsWith("/auth/");
                        if (authenticated) {
                            add(operation, "403", "Authenticated but not allowed");
                            if (method == PathItem.HttpMethod.POST) {
                                add(operation, "409", "Conflicts with an existing resource");
                            }
                        }
                        // A path variable is a lookup, and a lookup can miss.
                        if (path.contains("{")) {
                            add(operation, "404", "Resource does not exist");
                        }
                        add(operation, "500", "Unhandled server error");
                    }));
        };
    }

    private static void registerApiErrorSchema(OpenAPI openApi) {
        ResolvedSchema resolved = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(ApiError.class));
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }
        resolved.referencedSchemas.forEach(openApi.getComponents()::addSchemas);
    }

    private static void add(Operation operation, String status, String description) {
        if (operation.getResponses().containsKey(status)) {
            return;
        }
        operation.getResponses().addApiResponse(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>()
                                .$ref("#/components/schemas/" + API_ERROR_SCHEMA)))));
    }
}
