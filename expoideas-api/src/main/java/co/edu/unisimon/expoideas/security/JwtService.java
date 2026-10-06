package co.edu.unisimon.expoideas.security;

import co.edu.unisimon.expoideas.common.ExpoideasProperties;
import co.edu.unisimon.expoideas.users.SessionTokens;
import co.edu.unisimon.expoideas.users.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Tokens de sesión (JWT firmados con HS256). El subject es el correo y el claim
 * {@code role} lleva el rol, que el frontend usa solo para pintar la interfaz:
 * los permisos se leen de la BD en cada petición.
 *
 * <p>El claim {@code ver} es la versión de sesión de la cuenta al emitir el
 * token. Si la cuenta la sube (cambió la contraseña, la suspendieron), el token
 * deja de valer aunque no haya vencido: lo comprueba {@link JwtAuthenticationFilter}.
 */
@Service
public class JwtService implements SessionTokens {

    private static final String ROLE = "role";
    private static final String VERSION = "ver";

    private final SecretKey key;
    private final Duration expiration;

    /** Una clave mal formada o de menos de 256 bits impide arrancar la API. */
    public JwtService(ExpoideasProperties properties) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.jwt().secret()));
        this.expiration = properties.jwt().expiration();
    }

    public String generateToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim(ROLE, principal.getRole().name())
                .claim(VERSION, principal.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public String issueFor(User user) {
        return generateToken(new UserPrincipal(user));
    }

    /**
     * De quién es el token y con qué versión de sesión se emitió.
     *
     * @throws JwtException si la firma no es válida, el token está mal formado o ya venció
     */
    public Session parse(String token) {
        Claims claims =
                Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        // Los tokens anteriores a la versión de sesión no traen el claim: valen como versión 0.
        Integer version = claims.get(VERSION, Integer.class);
        return new Session(claims.getSubject(), version == null ? 0 : version);
    }

    /** Lo que un token dice de su sesión. */
    public record Session(String email, int tokenVersion) {}
}
