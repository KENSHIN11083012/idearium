package co.edu.unisimon.expoideas.reports;

import co.edu.unisimon.expoideas.common.ForbiddenActionException;
import co.edu.unisimon.expoideas.common.PageResponse;
import co.edu.unisimon.expoideas.deliverables.DeliverableProgressService;
import co.edu.unisimon.expoideas.deliverables.DeliverableProgressService.Progress;
import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.evaluations.EvaluationService;
import co.edu.unisimon.expoideas.evaluations.ProjectGrade;
import co.edu.unisimon.expoideas.projects.Project;
import co.edu.unisimon.expoideas.projects.ProjectPolicy;
import co.edu.unisimon.expoideas.projects.ProjectRepository;
import co.edu.unisimon.expoideas.projects.ProjectResult;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.User;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El directorio de proyectos inscritos, para la gestión y para los docentes.
 *
 * <p>MacondoLab y el administrador ven todos y filtran como quieran; un docente
 * ve solo los proyectos que lo nombraron, sin importar qué pida. Los estudiantes
 * tienen su propia vista (/api/v1/projects/mine).
 */
@Service
@RequiredArgsConstructor
public class ProjectDirectoryService {

    private final ProjectRepository projectRepository;
    private final DeliverableProgressService progressService;
    private final EvaluationService evaluationService;
    private final ProjectPolicy policy;

    /** De la inscripción más reciente a la más antigua; el id desempata. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    /** Una página del listado. */
    @Transactional(readOnly = true)
    public PageResponse<ProjectSummaryResponse> list(String email, ProjectFilter filter, int page, int size) {
        Integer teacherId = teacherScope(email, filter);
        Page<Integer> ids = projectRepository.searchIds(
                filter.editionId(),
                filter.track(),
                teacherId,
                filter.sectorId(),
                filter.result(),
                blankToNull(filter.search()),
                PageResponse.request(page, size, NEWEST_FIRST));
        // Con su equipo en una sola consulta, y en el orden de la página.
        Map<Integer, Project> byId = projectRepository.findWithTeamByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
        List<Project> projects = ids.getContent().stream().map(byId::get).toList();
        return PageResponse.of(summaries(projects), ids);
    }

    private List<ProjectSummaryResponse> summaries(List<Project> projects) {
        Map<Integer, Progress> progress = progressService.of(projects);
        Map<Integer, ProjectGrade> grades = evaluationService.gradesOf(projects);
        return projects.stream()
                .map(project -> ProjectSummaryResponse.of(
                        project,
                        progress.getOrDefault(project.getId(), new Progress(0, 0)),
                        grades.getOrDefault(project.getId(), ProjectGrade.NONE)))
                .toList();
    }

    /** Todos los proyectos que cumplen los filtros, en CSV, para trabajarlos en una hoja de cálculo. */
    @Transactional(readOnly = true)
    public String export(String email, ProjectFilter filter) {
        return ProjectCsv.of(summaries(projectRepository.search(
                filter.editionId(),
                filter.track(),
                teacherScope(email, filter),
                filter.sectorId(),
                filter.result(),
                blankToNull(filter.search()))));
    }

    /** Nombre del archivo exportado, con la fecha de hoy en Colombia. */
    public String exportFilename() {
        return "proyectos-" + policy.today() + ".csv";
    }

    /**
     * De qué profesor son los proyectos que ve quien consulta: la gestión, del que pida (o de
     * todos); un profesor, solo los suyos, pida lo que pida.
     *
     * @throws ForbiddenActionException si no es de la gestión ni profesor
     */
    private Integer teacherScope(String email, ProjectFilter filter) {
        User viewer = policy.account(email);
        if (viewer.getRole() == Role.TEACHER) {
            return viewer.getId();
        }
        if (!viewer.getRole().isManagement()) {
            throw new ForbiddenActionException("Esta vista es de la gestión y de los profesores");
        }
        return filter.teacherId();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Filtros del listado; cualquiera puede venir vacío. */
    public record ProjectFilter(
            Integer editionId, Track track, Integer teacherId, Integer sectorId, ProjectResult result, String search) {}
}
