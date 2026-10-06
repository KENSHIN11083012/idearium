package co.edu.unisimon.expoideas.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Autentica la petición con el token de la cabecera Authorization. No es un
 * bean: SecurityConfig lo crea dentro de la cadena de Spring Security, para que
 * Spring Boot no lo registre también como filtro del contenedor.
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final HandlerExceptionResolver exceptionResolver;

    public JwtAuthenticationFilter(
            JwtService jwtService, UserDetailsService userDetailsService, HandlerExceptionResolver exceptionResolver) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null
                && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // Nunca registrar la cabecera ni el token: identifican una sesión activa.
                JwtService.Session session = jwtService.parse(
                        header.substring(BEARER_PREFIX.length()).strip());
                // Las autoridades salen de la BD, no del claim "role" del token: si alguien
                // pierde un rol, lo pierde en la siguiente petición y no cuando venza el token.
                UserDetails user = userDetailsService.loadUserByUsername(session.email());
                if (stillOpen(session, user)) {
                    UsernamePasswordAuthenticationToken authentication =
                            UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.debug("La sesión de la petición a {} ya fue cerrada", request.getRequestURI());
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
                // Token inválido, vencido o cuenta inexistente: se sigue sin autenticar.
                // Las rutas protegidas responden 401; las públicas siguen funcionando.
                SecurityContextHolder.clearContext();
                log.debug("No se pudo autenticar la petición a {}: {}", request.getRequestURI(), e.getMessage());
            } catch (RuntimeException e) {
                // Cualquier otra cosa (la base no responde) no dice nada sobre la sesión. Seguir
                // sin autenticar acabaría en un 401, y con un 401 la app cierra la sesión: se
                // responde el fallo tal cual y la petición no sigue.
                SecurityContextHolder.clearContext();
                exceptionResolver.resolveException(request, response, null, e);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Un token bien firmado y sin vencer puede ser de una sesión que ya no vale:
     * la cuenta está suspendida, o cambió la contraseña después de emitirlo.
     */
    private static boolean stillOpen(JwtService.Session session, UserDetails user) {
        if (!user.isEnabled()) {
            return false;
        }
        return !(user instanceof UserPrincipal principal) || principal.getTokenVersion() == session.tokenVersion();
    }
}
