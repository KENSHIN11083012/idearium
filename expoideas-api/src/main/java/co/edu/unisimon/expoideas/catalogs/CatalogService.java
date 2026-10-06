package co.edu.unisimon.expoideas.catalogs;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alta, edición y listado de los catálogos: estructura académica (sedes,
 * facultades, programas) y clasificación de proyectos (sectores, keywords).
 *
 * <p>Sedes, facultades, sectores y keywords solo tienen nombre y comparten el
 * mismo código a través de {@link CatalogItem}. Los programas llevan además su
 * facultad.
 *
 * <p>Un nombre repetido choca con una restricción única de la BD y sale como
 * DataIntegrityViolationException, que GlobalExceptionHandler responde con 409.
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CampusRepository campusRepository;
    private final FacultyRepository facultyRepository;
    private final AcademicProgramRepository academicProgramRepository;
    private final SectorRepository sectorRepository;
    private final PrototypeTypeRepository prototypeTypeRepository;
    private final KeywordRepository keywordRepository;
    private final CatalogLookup lookup;

    // ── Sedes ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> listCampuses() {
        return list(campusRepository);
    }

    @Transactional
    public CatalogItemResponse createCampus(CatalogItemRequest request) {
        return create(campusRepository, Campus::new, request);
    }

    /** @throws NoSuchElementException si el id no existe */
    @Transactional
    public CatalogItemResponse updateCampus(Integer id, CatalogItemRequest request) {
        return update(campusRepository, id, request, "una sede");
    }

    // ── Facultades ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> listFaculties() {
        return list(facultyRepository);
    }

    @Transactional
    public CatalogItemResponse createFaculty(CatalogItemRequest request) {
        return create(facultyRepository, Faculty::new, request);
    }

    /** @throws NoSuchElementException si el id no existe */
    @Transactional
    public CatalogItemResponse updateFaculty(Integer id, CatalogItemRequest request) {
        return update(facultyRepository, id, request, "una facultad");
    }

    // ── Programas académicos ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AcademicProgramResponse> listAcademicPrograms() {
        return academicProgramRepository.findAllWithFaculty().stream()
                .map(AcademicProgramResponse::from)
                .toList();
    }

    /** @throws NoSuchElementException si la facultad no existe */
    @Transactional
    public AcademicProgramResponse createAcademicProgram(AcademicProgramRequest request) {
        return saveAcademicProgram(new AcademicProgram(), request);
    }

    /** @throws NoSuchElementException si el programa o la facultad no existen */
    @Transactional
    public AcademicProgramResponse updateAcademicProgram(Integer id, AcademicProgramRequest request) {
        return saveAcademicProgram(lookup.academicProgram(id), request);
    }

    private AcademicProgramResponse saveAcademicProgram(AcademicProgram program, AcademicProgramRequest request) {
        program.setName(request.name().strip());
        program.setFaculty(lookup.faculty(request.facultyId()));
        return AcademicProgramResponse.from(academicProgramRepository.saveAndFlush(program));
    }

    // ── Sectores ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> listSectors() {
        return list(sectorRepository);
    }

    @Transactional
    public CatalogItemResponse createSector(CatalogItemRequest request) {
        return create(sectorRepository, Sector::new, request);
    }

    /** @throws NoSuchElementException si el id no existe */
    @Transactional
    public CatalogItemResponse updateSector(Integer id, CatalogItemRequest request) {
        return update(sectorRepository, id, request, "un sector");
    }

    // ── Tipos de prototipo ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> listPrototypeTypes() {
        return list(prototypeTypeRepository);
    }

    @Transactional
    public CatalogItemResponse createPrototypeType(CatalogItemRequest request) {
        return create(prototypeTypeRepository, PrototypeType::new, request);
    }

    /** @throws NoSuchElementException si el id no existe */
    @Transactional
    public CatalogItemResponse updatePrototypeType(Integer id, CatalogItemRequest request) {
        return update(prototypeTypeRepository, id, request, "un tipo de prototipo");
    }

    // ── Keywords ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> listKeywords() {
        return list(keywordRepository);
    }

    @Transactional
    public CatalogItemResponse createKeyword(CatalogItemRequest request) {
        return create(keywordRepository, Keyword::new, request);
    }

    /** @throws NoSuchElementException si el id no existe */
    @Transactional
    public CatalogItemResponse updateKeyword(Integer id, CatalogItemRequest request) {
        return update(keywordRepository, id, request, "una keyword");
    }

    // ── Común a los catálogos que solo tienen nombre ────────────────────────

    private static <T extends CatalogItem> List<CatalogItemResponse> list(JpaRepository<T, Integer> repository) {
        return repository.findAll().stream().map(CatalogItemResponse::from).toList();
    }

    private static <T extends CatalogItem> CatalogItemResponse create(
            JpaRepository<T, Integer> repository, Supplier<T> factory, CatalogItemRequest request) {
        return save(repository, factory.get(), request);
    }

    private static <T extends CatalogItem> CatalogItemResponse update(
            JpaRepository<T, Integer> repository, Integer id, CatalogItemRequest request, String description) {
        T item = repository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("No existe " + description + " con ID: " + id));
        return save(repository, item, request);
    }

    private static <T extends CatalogItem> CatalogItemResponse save(
            JpaRepository<T, Integer> repository, T item, CatalogItemRequest request) {
        item.setName(request.name().strip());
        // saveAndFlush: si el nombre choca con una restricción única, falla aquí como
        // DataIntegrityViolationException (409) y no al hacer commit, donde puede
        // llegar envuelta en una TransactionSystemException (500).
        return CatalogItemResponse.from(repository.saveAndFlush(item));
    }
}
