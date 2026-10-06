package co.edu.unisimon.expoideas.auth;

import co.edu.unisimon.expoideas.users.AccountLinkService;
import co.edu.unisimon.expoideas.users.EmailVerificationRequest;
import co.edu.unisimon.expoideas.users.PasswordRecoveryRequest;
import co.edu.unisimon.expoideas.users.PasswordRecoveryResetRequest;
import co.edu.unisimon.expoideas.users.RegistrationRequest;
import co.edu.unisimon.expoideas.users.UserAccountService;
import co.edu.unisimon.expoideas.users.UserResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que se hace sin sesión, o antes de poder usar la que se tiene: iniciar
 * sesión, registrarse, verificar el correo y recuperar la contraseña.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@SecurityRequirements
public class AuthController {

    private final AuthService authService;
    private final UserAccountService accountService;
    private final AccountLinkService accountLinks;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Alta de una cuenta de estudiante. 409 si el correo ya existe. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegistrationRequest request) {
        return accountService.register(request);
    }

    /** Da por verificado el correo de la cuenta dueña del enlace. 409 si el enlace ya no sirve. */
    @PostMapping("/email-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody EmailVerificationRequest request) {
        accountLinks.verifyEmail(request.token());
    }

    /**
     * Vuelve a enviar el enlace de verificación a quien tiene la sesión abierta.
     * Toda la ruta /auth es pública, así que aquí se exige la sesión a mano.
     */
    @PostMapping("/email-verification/resend")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Sin sesión");
        }
        accountLinks.resendVerification(authentication.getName());
    }

    /**
     * Pide el enlace para poner una contraseña nueva. Responde igual exista o no
     * una cuenta con ese correo: no sirve para averiguar quién está registrado.
     */
    @PostMapping("/password-recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordRecovery(@Valid @RequestBody PasswordRecoveryRequest request) {
        accountLinks.requestPasswordRecovery(request.email());
    }

    /** Pone la contraseña nueva con el enlace de recuperación. 409 si el enlace ya no sirve. */
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody PasswordRecoveryResetRequest request) {
        accountLinks.resetPassword(request);
    }
}
