package co.edu.unisimon.expoideas.users;

import co.edu.unisimon.expoideas.catalogs.AcademicProgram;
import co.edu.unisimon.expoideas.catalogs.CatalogLookup;
import co.edu.unisimon.expoideas.catalogs.Faculty;
import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adscripción académica de una cuenta: sede y facultad obligatorias y programa
 * opcional, que si existe debe ser de esa facultad. La usan el registro, el
 * perfil y la gestión.
 */
@Component
@RequiredArgsConstructor
class AffiliationResolver {

    private final CatalogLookup lookup;

    /**
     * Campos que faltan para una adscripción completa, con los mismos mensajes que
     * las anotaciones del registro para que el cliente los trate igual.
     */
    static Map<String, String> missingFields(Integer campusId, Integer facultyId) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (campusId == null) {
            fields.put("campusId", "La sede es obligatoria");
        }
        if (facultyId == null) {
            fields.put("facultyId", "La facultad es obligatoria");
        }
        return fields;
    }

    /**
     * Reemplaza la adscripción completa.
     *
     * @throws InvalidFieldsException si faltan sede o facultad, o el programa es de otra facultad
     * @throws NoSuchElementException si la sede, la facultad o el programa no existen
     */
    void assign(User user, Integer campusId, Integer facultyId, Integer academicProgramId) {
        Map<String, String> missing = missingFields(campusId, facultyId);
        if (!missing.isEmpty()) {
            throw new InvalidFieldsException(missing);
        }
        user.setCampus(lookup.campus(campusId));
        assignFaculty(user, facultyId, academicProgramId);
    }

    /**
     * Facultad obligatoria y programa opcional (null = sin programa).
     *
     * @throws InvalidFieldsException si el programa es de otra facultad
     */
    void assignFaculty(User user, Integer facultyId, Integer academicProgramId) {
        Faculty faculty = lookup.faculty(facultyId);
        AcademicProgram program = academicProgramId == null ? null : lookup.academicProgram(academicProgramId);
        if (program != null && !program.getFaculty().getId().equals(faculty.getId())) {
            throw new InvalidFieldsException(
                    "academicProgramId", "El programa académico no pertenece a la facultad seleccionada.");
        }
        user.setFaculty(faculty);
        user.setAcademicProgram(program);
    }

    /** Programa sin facultad explícita: la facultad sale del programa. */
    void assignProgram(User user, Integer academicProgramId) {
        AcademicProgram program = lookup.academicProgram(academicProgramId);
        user.setAcademicProgram(program);
        user.setFaculty(program.getFaculty());
    }

    void assignCampus(User user, Integer campusId) {
        user.setCampus(lookup.campus(campusId));
    }
}
