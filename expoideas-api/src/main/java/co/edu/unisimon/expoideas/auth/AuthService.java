package co.edu.unisimon.expoideas.auth;

import co.edu.unisimon.expoideas.security.JwtService;
import co.edu.unisimon.expoideas.security.UserPrincipal;
import co.edu.unisimon.expoideas.users.LoginAttempts;
import co.edu.unisimon.expoideas.users.User;
import co.edu.unisimon.expoideas.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final LoginAttempts attempts;

    /**
     * No abre una transacción propia: cada consulta toma su conexión y la devuelve. Con una
     * abierta, el ingreso retenía una conexión mientras comprobaba la contraseña y pedía otra para
     * anotar el intento; unos pocos ingresos a la vez agotaban las conexiones a la base y la
     * plataforma entera dejaba de responder hasta que vencía la espera.
     *
     * @throws LockedException         si la cuenta está bloqueada por intentos fallidos, aunque la contraseña sea la correcta
     * @throws AuthenticationException si las credenciales no son válidas o la cuenta está suspendida
     */
    public LoginResponse login(LoginRequest request) {
        if (attempts.isLocked(request.email())) {
            throw new LockedException(lockedMessage());
        }
        UserPrincipal principal;
        try {
            principal = (UserPrincipal) authenticationManager
                    .authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()))
                    .getPrincipal();
        } catch (BadCredentialsException wrongPassword) {
            // Solo cuenta la contraseña equivocada: una cuenta suspendida no acumula fallos.
            attempts.recordFailure(request.email());
            throw wrongPassword;
        }
        attempts.recordSuccess(principal.getUsername());

        // La cuenta existe: se acaba de autenticar contra ella. Se carga con la foto.
        User user =
                userRepository.findWithProfileByEmail(principal.getUsername()).orElseThrow();
        return new LoginResponse(
                jwtService.generateToken(principal),
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getPhoto() != null ? user.getPhoto().getUuid() : null,
                user.pendingSteps());
    }

    private String lockedMessage() {
        long minutes = Math.max(1, attempts.lockDuration().toMinutes());
        return "Demasiados intentos fallidos. Por seguridad, espera "
                + (minutes == 1 ? "un minuto" : minutes + " minutos")
                + " antes de volver a intentarlo.";
    }
}
