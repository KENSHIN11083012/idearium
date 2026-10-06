package co.edu.unisimon.expoideas.reports;

import co.edu.unisimon.expoideas.common.PageResponse;
import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.projects.ProjectResult;
import co.edu.unisimon.expoideas.reports.ProjectDirectoryService.ProjectFilter;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Listado de proyectos inscritos para la gestión y los docentes. Los proyectos
 * de cada estudiante están en /api/v1/projects/mine y la ficha de uno, en
 * /api/v1/projects/{id}.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectDirectoryController {

    private final ProjectDirectoryService directoryService;

    /** Por páginas, de la inscripción más reciente a la más antigua. */
    @GetMapping
    public PageResponse<ProjectSummaryResponse> list(
            Authentication authentication,
            @RequestParam(required = false) Integer editionId,
            @RequestParam(required = false) Track track,
            @RequestParam(required = false) Integer teacherId,
            @RequestParam(required = false) Integer sectorId,
            @RequestParam(required = false) ProjectResult result,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return directoryService.list(
                authentication.getName(),
                new ProjectFilter(editionId, track, teacherId, sectorId, result, search),
                page,
                size);
    }

    /** El mismo listado en CSV, con los filtros que estén puestos. */
    @GetMapping(path = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            Authentication authentication,
            @RequestParam(required = false) Integer editionId,
            @RequestParam(required = false) Track track,
            @RequestParam(required = false) Integer teacherId,
            @RequestParam(required = false) Integer sectorId,
            @RequestParam(required = false) ProjectResult result,
            @RequestParam(required = false) String search) {
        String csv = directoryService.export(
                authentication.getName(), new ProjectFilter(editionId, track, teacherId, sectorId, result, search));
        String filename = directoryService.exportFilename();

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
