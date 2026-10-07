package co.edu.unisimon.expoideas.users;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma el archivo con todo lo que la plataforma guarda de quien tiene la
 * sesión: su cuenta y lo que cada módulo aporte ({@link PersonalDataSource}).
 * Solo lo pide la propia persona; no hay forma de pedir el de otra.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalDataService {

    private final UserRepository userRepository;
    private final List<PersonalDataSource> sources;
    private final Clock clock;

    /** Los datos de la cuenta, tal como están guardados. */
    public record Account(
            Integer id,
            String email,
            String firstName,
            String lastName,
            String role,
            String campus,
            String faculty,
            String academicProgram,
            boolean hasPhoto,
            LocalDateTime createdAt,
            LocalDateTime emailVerifiedAt,
            boolean dataConsent,
            LocalDateTime dataConsentAt,
            String dataConsentVersion,
            boolean suspended) {

        static Account of(User user) {
            return new Account(
                    user.getId(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getRole().label(),
                    user.getCampus() != null ? user.getCampus().getName() : null,
                    user.getFaculty() != null ? user.getFaculty().getName() : null,
                    user.getAcademicProgram() != null
                            ? user.getAcademicProgram().getName()
                            : null,
                    user.getPhoto() != null,
                    user.getCreatedAt(),
                    user.getEmailVerifiedAt(),
                    user.isDataConsent(),
                    user.getDataConsentAt(),
                    user.getDataConsentVersion(),
                    !user.isEnabled());
        }
    }

    /**
     * El archivo: cuándo se generó, la cuenta y una sección por cada cosa que los
     * módulos guardan de la persona, en el orden en que la aportan.
     *
     * @throws NoSuchElementException si el correo no existe
     * @throws IllegalStateException  si dos módulos usan el mismo nombre de sección
     */
    @Transactional(readOnly = true)
    public Map<String, Object> export(String email) {
        User user = userRepository
                .findWithProfileByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("No existe una cuenta con el correo: " + email));

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("generatedAt", LocalDateTime.now(clock));
        export.put("account", Account.of(user));
        for (PersonalDataSource source : sources) {
            source.dataOf(user).forEach((section, rows) -> {
                if (export.putIfAbsent(section, rows) != null) {
                    throw new IllegalStateException("Dos módulos aportan la sección de datos personales: " + section);
                }
            });
        }
        log.info("Usuario ID {} descargó sus datos", user.getId());
        return export;
    }
}
