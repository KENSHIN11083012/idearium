package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.common.ConflictException;
import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.files.FileFormat;
import co.edu.unisimon.expoideas.files.FileService;
import co.edu.unisimon.expoideas.files.FileVisibility;
import co.edu.unisimon.expoideas.files.StoredFile;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lo que cada persona hace con su propia cuenta: registrarse, ver y editar su
 * perfil, su foto, autorizar el tratamiento de datos y cambiar su contraseña.
 * La cuenta siempre se identifica por el correo de la sesión, nunca por un id
 * que venga del cliente.
 *
 * <p>Los métodos devuelven respuestas ya armadas dentro de la transacción: con
 * open-in-view desactivado, fuera de aquí no se pueden recorrer relaciones lazy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;
    private final RosterRepository rosterRepository;
    private final AffiliationResolver affiliation;
    private final PasswordUpdater passwords;
    private final FileService fileService;
    private final Clock clock;
    private final SessionTokens sessionTokens;
    private final AccountLinkService accountLinks;

    /**
     * Registro público: la cuenta nace como estudiante, con la autorización de
     * datos que exige el formulario y sin nombre ni adscripción, que quedan como
     * paso pendiente ({@link OnboardingStep#COMPLETE_PROFILE}) del primer ingreso.
     * Si el correo está en el listado de la cátedra, nace con el nombre que traiga
     * el listado; si ahí figura con otro rol, ese rol queda pendiente hasta que
     * verifique su correo o, si la plataforma no envía correos, hasta que la
     * gestión lo confirme.
     *
     * @throws ConflictException si el correo ya está en uso
     */
    @Transactional
    public UserResponse register(RegistrationRequest request) {
        String email = request.email().strip();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("El correo institucional ya está registrado.");
        }
        User.UserBuilder user = User.builder()
                .email(email)
                .passwordHash(passwords.hash(request.password()))
                .role(Role.STUDENT)
                // Si se puede escribir a su correo, tiene que demostrar que es suyo antes de usar la cuenta.
                .emailVerificationPending(accountLinks.canSendLinks());
        rosterRepository.findByEmail(email.toLowerCase(Locale.ROOT)).ifPresent(entry -> {
            // Nadie ha comprobado que quien se registra sea el dueño del correo: un rol con más
            // alcance que el de estudiante espera a que la gestión lo confirme.
            if (entry.getRole() != Role.STUDENT) {
                user.pendingRole(entry.getRole());
            }
            if (entry.hasName()) {
                user.firstName(entry.getFirstName()).lastName(entry.getLastName());
            }
        });
        return register(user.build());
    }

    private UserResponse register(User user) {
        user.giveDataConsent(LocalDateTime.now(clock));

        User saved = userRepository.save(user);
        log.info("Usuario registrado con ID {}", saved.getId());
        if (saved.isEmailVerificationPending()) {
            accountLinks.sendVerification(saved);
        }
        return UserResponse.from(saved);
    }

    /** @throws NoSuchElementException si el correo no existe */
    @Transactional(readOnly = true)
    public UserResponse getProfile(String email) {
        return UserResponse.from(findByEmail(email));
    }

    /**
     * Reemplaza nombre y, si el rol la lleva, adscripción académica.
     *
     * @throws InvalidFieldsException si faltan sede o facultad, o el programa es de otra facultad
     * @throws NoSuchElementException si la sede, la facultad o el programa no existen
     */
    @Transactional
    public UserResponse updateProfile(String email, ProfileUpdateRequest request) {
        User user = findByEmail(email);
        if (user.getRole().requiresAffiliation()) {
            affiliation.assign(user, request.campusId(), request.facultyId(), request.academicProgramId());
        }
        user.setFirstName(request.firstName().strip());
        user.setLastName(request.lastName().strip());
        return UserResponse.from(user);
    }

    /**
     * Sube o reemplaza la foto de perfil. La anterior se borra cuando la nueva
     * queda guardada.
     *
     * @throws InvalidFieldsException si no es JPG, PNG o WEBP, está vacía o pasa de 5 MB
     */
    @Transactional
    public UserResponse updatePhoto(String email, MultipartFile upload) {
        User user = findByEmail(email);
        StoredFile previous = user.getPhoto();
        user.setPhoto(fileService.store(upload, FileFormat.IMAGES, FileVisibility.PUBLIC, user));
        if (previous != null) {
            fileService.delete(previous);
        }
        return UserResponse.from(user);
    }

    /** Quita la foto de perfil, si tiene. */
    @Transactional
    public void deletePhoto(String email) {
        User user = findByEmail(email);
        StoredFile photo = user.getPhoto();
        if (photo != null) {
            user.setPhoto(null);
            fileService.delete(photo);
        }
    }

    /** Autorización de tratamiento de datos; si ya la había dado, conserva la fecha original. */
    @Transactional
    public void giveDataConsent(String email) {
        User user = findByEmail(email);
        if (!user.isDataConsent()) {
            user.giveDataConsent(LocalDateTime.now(clock));
            log.info("Usuario ID {} autorizó el tratamiento de datos", user.getId());
        }
    }

    /**
     * Cambio de la propia contraseña, verificando la actual. Quita la marca de
     * contraseña temporal y cierra las sesiones que hubiera abiertas con la
     * anterior; devuelve el token con el que sigue la de quien hizo el cambio.
     *
     * @throws InvalidFieldsException si la actual no coincide, o la nueva no se confirma o es igual a la actual
     */
    @Transactional
    public String changePassword(String email, PasswordChangeRequest request) {
        User user = findByEmail(email);
        if (!passwords.matches(user, request.currentPassword())) {
            throw new InvalidFieldsException("currentPassword", "La contraseña actual es incorrecta.");
        }
        passwords.replace(user, request.newPassword(), request.confirmPassword(), false);
        return sessionTokens.issueFor(user);
    }

    private User findByEmail(String email) {
        return userRepository
                .findWithProfileByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("No existe un usuario con correo: " + email));
    }
}
