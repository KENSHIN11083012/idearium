package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.files.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * La propia cuenta de quien tiene la sesión. Las cuentas de otras personas se
 * gestionan en /api/v1/admin/users; el registro está en /api/v1/auth/register.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserAccountService accountService;

    @GetMapping
    public UserResponse getProfile(Authentication authentication) {
        return accountService.getProfile(authentication.getName());
    }

    /** Nombre y, si el rol la lleva, adscripción académica. */
    @PutMapping
    public UserResponse updateProfile(Authentication authentication, @Valid @RequestBody ProfileUpdateRequest request) {
        return accountService.updateProfile(authentication.getName(), request);
    }

    /** Sube o reemplaza la foto de perfil: JPG, PNG o WEBP de hasta 5 MB. */
    @PutMapping(path = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse updatePhoto(Authentication authentication, @RequestPart(FileService.FIELD) MultipartFile file) {
        return accountService.updatePhoto(authentication.getName(), file);
    }

    @DeleteMapping("/photo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(Authentication authentication) {
        accountService.deletePhoto(authentication.getName());
    }

    /**
     * Autorización de tratamiento de datos, p. ej. en el primer ingreso de una cuenta
     * creada por la gestión. El cuerpo solo se valida: debe traer {@code dataConsent: true}.
     */
    @PutMapping("/data-consent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void giveDataConsent(Authentication authentication, @Valid @RequestBody DataConsentRequest request) {
        accountService.giveDataConsent(authentication.getName());
    }

    /**
     * Exige la contraseña actual y quita la marca de temporal. Las demás sesiones
     * de la cuenta se cierran; esta sigue con el token de la respuesta.
     */
    @PutMapping("/password")
    public SessionTokenResponse changePassword(
            Authentication authentication, @Valid @RequestBody PasswordChangeRequest request) {
        return new SessionTokenResponse(accountService.changePassword(authentication.getName(), request));
    }
}
