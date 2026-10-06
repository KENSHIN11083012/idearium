package co.edu.unisimon.expoideas.catalogs;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Busca registros de catálogo por id para otros módulos (p. ej. la adscripción de
 * una cuenta). Los ids se resuelven contra la BD en vez de crear entidades
 * sueltas: así un id inexistente falla aquí con 404 y no como violación de FK
 * al hacer flush.
 */
@Component
@RequiredArgsConstructor
public class CatalogLookup {

    private final CampusRepository campusRepository;
    private final FacultyRepository facultyRepository;
    private final AcademicProgramRepository academicProgramRepository;
    private final SectorRepository sectorRepository;
    private final PrototypeTypeRepository prototypeTypeRepository;

    /** @throws NoSuchElementException si no existe */
    public Campus campus(Integer id) {
        return campusRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe una sede con ID: " + id));
    }

    /** @throws NoSuchElementException si no existe */
    public Faculty faculty(Integer id) {
        return facultyRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe una facultad con ID: " + id));
    }

    /** @throws NoSuchElementException si no existe */
    public Sector sector(Integer id) {
        return sectorRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe un sector con ID: " + id));
    }

    /** @throws NoSuchElementException si no existe */
    public PrototypeType prototypeType(Integer id) {
        return prototypeTypeRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe un tipo de prototipo con ID: " + id));
    }

    /** Si MacondoLab ya cargó tipos de prototipo: mientras no, los proyectos de II van sin tipo. */
    public boolean hasPrototypeTypes() {
        return prototypeTypeRepository.count() > 0;
    }

    /** @throws NoSuchElementException si no existe */
    public AcademicProgram academicProgram(Integer id) {
        return academicProgramRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe un programa académico con ID: " + id));
    }
}
