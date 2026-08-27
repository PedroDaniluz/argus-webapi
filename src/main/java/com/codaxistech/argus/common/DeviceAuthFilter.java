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
 * Deliberately not a bean: Boot would register it for every request and a device key
 * would start working on user endpoints. Only the /api/ingest chain builds it.
 *
 * <p>Rejects nobody: a bad key just fails to authenticate and the entry point answers 401.
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
                logger.warn("rejected X-Device-Key on " + request.getRequestURI());
            }
        }
        chain.doFilter(request, response);
    }
}
