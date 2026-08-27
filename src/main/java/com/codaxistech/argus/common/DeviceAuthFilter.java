package com.codaxistech.argus.common;

import com.codaxistech.argus.device.DeviceDtos;
import com.codaxistech.argus.device.DeviceFacade;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Autentica o dispositivo pelo header {@code X-Device-Key}.
 *
 * <p>Header proprio em vez de {@code Authorization} porque o esquema e outro:
 * chave opaca de longa duracao, sem refresh, revogavel individualmente. Manter
 * separado deixa espaco para acrescentar {@code X-Device-Signature}
 * (HMAC-SHA256 do corpo mais {@code ts}) sem quebrar o contrato do firmware.
 *
 * <p>O filtro nao rejeita ninguem: chave ausente ou invalida simplesmente nao
 * autentica, e a cadeia de seguranca devolve 401 pelo entry point. Assim so
 * existe um lugar que formata erro de autenticacao.
 *
 * <p>Nao e um bean de proposito. Como bean, o Boot registraria o filtro para
 * toda requisicao e uma chave de dispositivo passaria a valer nos endpoints de
 * usuario. Ele e instanciado pela cadeia de /api/ingest e so vive la.
 */
public class DeviceAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Device-Key";
    public static final String ROLE = "ROLE_DEVICE";

    private final DeviceFacade devices;

    public DeviceAuthFilter(DeviceFacade devices) {
        this.devices = devices;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key != null && !key.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<DeviceDtos.Authenticated> device = devices.authenticate(key);
            if (device.isPresent()) {
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        device.get(), null, List.of(new SimpleGrantedAuthority(ROLE)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                logger.warn("X-Device-Key rejeitada em " + request.getRequestURI());
            }
        }
        chain.doFilter(request, response);
    }
}
