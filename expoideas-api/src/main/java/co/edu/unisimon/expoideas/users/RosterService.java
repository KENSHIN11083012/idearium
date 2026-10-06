package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.common.ValidationPatterns;
import co.edu.unisimon.expoideas.files.FileService;
import co.edu.unisimon.expoideas.users.RosterCsv.Row;
import co.edu.unisimon.expoideas.users.RosterImportResponse.RejectedRow;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * El listado de la cátedra. Lo carga la gestión desde un CSV (ver SecurityConfig
 * y docs/listado.md); una carga nueva añade y actualiza, nunca borra. Solo
 * estudiantes y profesores: los jurados externos entran por invitación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RosterService {

    private static final Pattern INSTITUTIONAL_EMAIL = Pattern.compile(ValidationPatterns.INSTITUTIONAL_EMAIL);

    /** Cómo puede venir escrito el rol en el archivo. */
    private static final Map<String, Role> ROLES = Map.of(
            "estudiante", Role.STUDENT,
            "estudiantes", Role.STUDENT,
            "alumno", Role.STUDENT,
            "student", Role.STUDENT,
            "profesor", Role.TEACHER,
            "profesora", Role.TEACHER,
            "docente", Role.TEACHER,
            "teacher", Role.TEACHER);

    private final RosterRepository rosterRepository;
    private final UserRepository userRepository;

    /** Por apellido y nombre como se ordena en español; quien no trae nombre, al final, por correo. */
    @Transactional(readOnly = true)
    public List<RosterEntryResponse> list() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("es"));
        Comparator<String> byText = Comparator.nullsLast(collator::compare);
        List<RosterEntry> entries = rosterRepository.findAll().stream()
                .sorted(Comparator.comparing(RosterEntry::getLastName, byText)
                        .thenComparing(RosterEntry::getFirstName, byText)
                        .thenComparing(RosterEntry::getEmail))
                .toList();
        Set<String> registered = registeredAmong(entries);
        return entries.stream()
                .map(entry -> RosterEntryResponse.from(entry, registered.contains(entry.getEmail())))
                .toList();
    }

    /**
     * Carga un CSV: cada fila válida entra nueva o actualiza la que ya tenía ese
     * correo. Las filas sin correo institucional, con rol desconocido o
     * repetidas dentro del archivo se devuelven con su motivo, y el resto se
     * guarda igual.
     *
     * @throws InvalidFieldsException si el archivo está vacío, no se puede leer o le faltan las columnas
     */
    @Transactional
    public RosterImportResponse importCsv(MultipartFile upload) {
        List<Row> rows;
        try {
            rows = RosterCsv.parse(upload.getBytes());
        } catch (IOException e) {
            throw new InvalidFieldsException(FileService.FIELD, "No se pudo leer el archivo");
        } catch (IllegalArgumentException e) {
            throw new InvalidFieldsException(FileService.FIELD, e.getMessage());
        }

        int added = 0;
        int updated = 0;
        Set<String> seen = new HashSet<>();
        List<RejectedRow> rejected = new ArrayList<>();
        List<RosterEntry> saved = new ArrayList<>();
        for (Row row : rows) {
            String email = row.email() == null ? "" : row.email().toLowerCase(Locale.ROOT);
            if (!INSTITUTIONAL_EMAIL.matcher(email).matches()) {
                rejected.add(new RejectedRow(row.line(), row.email(), ValidationPatterns.INSTITUTIONAL_EMAIL_MESSAGE));
                continue;
            }
            Role role = row.role() == null ? null : ROLES.get(RosterCsv.key(row.role()));
            if (role == null) {
                rejected.add(new RejectedRow(row.line(), email, "El rol debe ser «estudiante» o «profesor»"));
                continue;
            }
            if (!seen.add(email)) {
                rejected.add(new RejectedRow(row.line(), email, "El correo se repite en el archivo"));
                continue;
            }

            RosterEntry entry = rosterRepository.findByEmail(email).orElse(null);
            if (entry == null) {
                entry = RosterEntry.of(email);
                added++;
            } else {
                updated++;
            }
            entry.setRole(role);
            entry.setFirstName(row.firstName());
            entry.setLastName(row.lastName());
            saved.add(rosterRepository.save(entry));
        }

        int registered = registeredAmong(saved).size();
        log.info(
                "Listado de la cátedra: {} nuevos, {} actualizados, {} ya registrados, {} rechazados",
                added,
                updated,
                registered,
                rejected.size());
        return new RosterImportResponse(added, updated, registered, (int) rosterRepository.count(), rejected);
    }

    /** @throws NoSuchElementException si no existe */
    @Transactional
    public void remove(Integer id) {
        RosterEntry entry = rosterRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("Esa fila no está en el listado"));
        rosterRepository.delete(entry);
    }

    /** Vacía el listado. Las cuentas que ya nacieron de él no se tocan. */
    @Transactional
    public void clear() {
        rosterRepository.deleteAllInBatch();
        log.info("Listado de la cátedra vaciado");
    }

    /** De esos correos, cuáles ya tienen cuenta. */
    private Set<String> registeredAmong(List<RosterEntry> entries) {
        if (entries.isEmpty()) {
            return Set.of();
        }
        List<String> emails = entries.stream().map(RosterEntry::getEmail).toList();
        return userRepository.findEmailsIn(emails).stream()
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
