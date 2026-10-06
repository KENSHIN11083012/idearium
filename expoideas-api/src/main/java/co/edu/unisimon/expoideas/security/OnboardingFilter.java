package co.edu.unisimon.expoideas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Mientras la cuenta tenga pasos de primer ingreso sin completar (contraseña
 * temporal, autorización de datos, perfil incompleto), solo puede resolverlos:
 * ver y completar su perfil, cambiar su contraseña y autorizar el tratamiento
 * de datos. Cualquier otra petición a la API responde 403 con {@code pendingSteps}.
 *
 * <p>Corre después de JwtAuthenticationFilter y antes de las reglas de
 * autorización: aplica igual para todos los roles. Como ese filtro, no es un
 * bean: lo crea SecurityConfig.
 */
public class OnboardingFilter extends OncePerRequestFilter {

    private final HandlerExceptionResolver exceptionResolver;
    private final RequestMatcher allowed;

    /** @param publicReads rutas que se leen sin sesión: siguen abiertas aunque la petición lleve una. */
    public OnboardingFilter(HandlerExceptionResolver exceptionResolver, String... publicReads) {
        this.exceptionResolver = exceptionResolver;
        PathPatternRequestMatcher.Builder path = PathPatternRequestMatcher.withDefaults();
        List<RequestMatcher> matchers = new ArrayList<>(List.of(
                path.matcher("/api/v1/auth/**"),
                path.matcher(HttpMethod.GET, "/api/v1/users/me"),
                path.matcher(HttpMethod.PUT, "/api/v1/users/me"),
                path.matcher(HttpMethod.PUT, "/api/v1/users/me/password"),
                path.matcher(HttpMethod.PUT, "/api/v1/users/me/data-consent")));
        for (String read : publicReads) {
            matchers.add(path.matcher(HttpMethod.GET, read));
        }
        this.allowed = new OrRequestMatcher(matchers);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Swagger y demás rutas fuera de la API no dependen del primer ingreso.
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof UserPrincipal principal
                && !principal.getPendingSteps().isEmpty()
                && !allowed.matches(request)) {
            // Mismo camino que los 401/403 de SecurityConfig: Problem Details desde GlobalExceptionHandler.
            exceptionResolver.resolveException(
                    request, response, null, new OnboardingRequiredException(principal.getPendingSteps()));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
