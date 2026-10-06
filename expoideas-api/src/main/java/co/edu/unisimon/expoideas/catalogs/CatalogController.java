package co.edu.unisimon.expoideas.catalogs;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogos. La lectura es pública: la usa el registro antes de que haya sesión.
 * Sedes, facultades y programas los escribe el administrador; sectores y
 * keywords, también MacondoLab (ver SecurityConfig). No hay borrado: los
 * registros pueden estar referenciados por cuentas y programas.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    // ── Sedes ───────────────────────────────────────────────────────────────

    @GetMapping("/campuses")
    @SecurityRequirements
    public List<CatalogItemResponse> listCampuses() {
        return catalogService.listCampuses();
    }

    @PostMapping("/campuses")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItemResponse createCampus(@Valid @RequestBody CatalogItemRequest request) {
        return catalogService.createCampus(request);
    }

    @PutMapping("/campuses/{id}")
    public CatalogItemResponse updateCampus(@PathVariable Integer id, @Valid @RequestBody CatalogItemRequest request) {
        return catalogService.updateCampus(id, request);
    }

    // ── Facultades ──────────────────────────────────────────────────────────

    @GetMapping("/faculties")
    @SecurityRequirements
    public List<CatalogItemResponse> listFaculties() {
        return catalogService.listFaculties();
    }

    @PostMapping("/faculties")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItemResponse createFaculty(@Valid @RequestBody CatalogItemRequest request) {
        return catalogService.createFaculty(request);
    }

    @PutMapping("/faculties/{id}")
    public CatalogItemResponse updateFaculty(@PathVariable Integer id, @Valid @RequestBody CatalogItemRequest request) {
        return catalogService.updateFaculty(id, request);
    }

    // ── Programas académicos ────────────────────────────────────────────────

    @GetMapping("/academic-programs")
    @SecurityRequirements
    public List<AcademicProgramResponse> listAcademicPrograms() {
        return catalogService.listAcademicPrograms();
    }

    @PostMapping("/academic-programs")
    @ResponseStatus(HttpStatus.CREATED)
    public AcademicProgramResponse createAcademicProgram(@Valid @RequestBody AcademicProgramRequest request) {
        return catalogService.createAcademicProgram(request);
    }

    @PutMapping("/academic-programs/{id}")
    public AcademicProgramResponse updateAcademicProgram(
            @PathVariable Integer id, @Valid @RequestBody AcademicProgramRequest request) {
        return catalogService.updateAcademicProgram(id, request);
    }

    // ── Sectores ────────────────────────────────────────────────────────────

    @GetMapping("/sectors")
    @SecurityRequirements
    public List<CatalogItemResponse> listSectors() {
        return catalogService.listSectors();
    }

    @PostMapping("/sectors")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItemResponse createSector(@Valid @RequestBody CatalogItemRequest request) {
        return catalogService.createSector(request);
    }

    @PutMapping("/sectors/{id}")
    public CatalogItemResponse updateSector(@PathVariable Integer id, @Valid @RequestBody CatalogItemRequest request) {
        return catalogService.updateSector(id, request);
    }

    // ── Tipos de prototipo (INNPRENDE II) ───────────────────────────────────

    @GetMapping("/prototype-types")
    @SecurityRequirements
    public List<CatalogItemResponse> listPrototypeTypes() {
        return catalogService.listPrototypeTypes();
    }

    @PostMapping("/prototype-types")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItemResponse createPrototypeType(@Valid @RequestBody CatalogItemRequest request) {
        return catalogService.createPrototypeType(request);
    }

    @PutMapping("/prototype-types/{id}")
    public CatalogItemResponse updatePrototypeType(
            @PathVariable Integer id, @Valid @RequestBody CatalogItemRequest request) {
        return catalogService.updatePrototypeType(id, request);
    }

    // ── Keywords ────────────────────────────────────────────────────────────

    @GetMapping("/keywords")
    @SecurityRequirements
    public List<CatalogItemResponse> listKeywords() {
        return catalogService.listKeywords();
    }

    @PostMapping("/keywords")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogItemResponse createKeyword(@Valid @RequestBody CatalogItemRequest request) {
        return catalogService.createKeyword(request);
    }

    @PutMapping("/keywords/{id}")
    public CatalogItemResponse updateKeyword(@PathVariable Integer id, @Valid @RequestBody CatalogItemRequest request) {
        return catalogService.updateKeyword(id, request);
    }
}
