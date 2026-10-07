package co.edu.unisimon.expoideas.evaluations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.unisimon.expoideas.common.AuditableAction;
import co.edu.unisimon.expoideas.common.AuditableAction.Action;
import co.edu.unisimon.expoideas.common.ConflictException;
import co.edu.unisimon.expoideas.common.ForbiddenActionException;
import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.editions.EditionTrack;
import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.evaluations.EvaluationRequest.ScoreRequest;
import co.edu.unisimon.expoideas.notifications.EvaluationReminderEvent;
import co.edu.unisimon.expoideas.projects.Project;
import co.edu.unisimon.expoideas.projects.ProjectPolicy;
import co.edu.unisimon.expoideas.projects.ProjectRepository;
import co.edu.unisimon.expoideas.support.TestData;
import co.edu.unisimon.expoideas.users.Role;
import co.edu.unisimon.expoideas.users.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/** Quién califica, qué se le exige a una evaluación y cómo sale la nota. */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    private static final int PROJECT = 10;

    /** La rúbrica con la que nació la edición del proyecto. */
    private static final int RUBRIC = 1;

    @Mock
    private RubricRepository rubricRepository;

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private ProjectPolicy policy;

    @Mock
    private EvaluatorRule evaluatorRule;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private EditionTrack settings;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 30);

    private EvaluationService service;
    private Project project;
    private Rubric rubric;

    private final User marta = TestData.user(8, "marta@empresa.com", Role.JUDGE);
    private final User pedro = TestData.user(9, "pedro@unisimon.edu.co", Role.TEACHER);
    private final User carlos = TestData.user(2, "carlos@unisimon.edu.co", Role.TEACHER);
    private final User coordination = TestData.user(1, "coordinacion@unisimon.edu.co", Role.MACONDOLAB);
    private final User ana = TestData.user(3, "ana@unisimon.edu.co", Role.STUDENT);

    @BeforeEach
    void setUp() {
        service = new EvaluationService(
                rubricRepository, evaluationRepository, projectRepository, policy, evaluatorRule, events);
        project = mock(Project.class);
        lenient().when(project.getId()).thenReturn(PROJECT);
        lenient().when(project.getTrack()).thenReturn(Track.INNPRENDE_I);
        lenient().when(project.getTeacher()).thenReturn(carlos);
        // Dos criterios: el primero con niveles 0.0 / 3.0 / 4.0 (ids 11-13); el segundo, 1.5 / 4.5 / 5.0 (ids 21-23).
        rubric = rubric(criterion(1, "0.0", "3.0", "4.0"), criterion(2, "1.5", "4.5", "5.0"));
        lenient().when(project.trackSettings()).thenReturn(settings);
        lenient().when(settings.getRubricId()).thenReturn(RUBRIC);
        lenient().when(rubricRepository.findById(RUBRIC)).thenReturn(Optional.of(rubric));
        lenient().when(evaluationRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(policy.now()).thenReturn(NOW);
        for (User user : List.of(marta, pedro, carlos, coordination, ana)) {
            lenient().when(policy.account(user.getEmail())).thenReturn(user);
            lenient().when(policy.findVisible(PROJECT, user)).thenReturn(project);
        }
        lenient().when(evaluatorRule.isEvaluator(project, marta)).thenReturn(true);
        lenient().when(evaluatorRule.isEvaluator(project, pedro)).thenReturn(true);
    }

    @Test
    void onlyAnAssignedJurorEvaluates() {
        // El profesor del grupo ve el proyecto, pero no lo califica.
        assertThatThrownBy(() -> service.save(PROJECT, carlos.getEmail(), scores(score(1, 13), score(2, 23))))
                .isInstanceOf(ForbiddenActionException.class)
                .hasMessageContaining("jurados asignados");
        assertThatThrownBy(() -> service.mine(PROJECT, carlos.getEmail())).isInstanceOf(ForbiddenActionException.class);
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void aProjectIsGradedWithTheRubricOfItsEditionNotWithTheCurrentOne() {
        // La vigente ya es otra versión, con un criterio que la edición del proyecto no conoce.
        Rubric newer = rubric(criterion(3, "0.0", "5.0"));
        newer.setId(9);
        newer.setVersion(2);
        when(rubricRepository.findFirstByTrackOrderByVersionDesc(Track.INNPRENDE_I))
                .thenReturn(Optional.of(newer));

        assertThat(service.rubric(Track.INNPRENDE_I).version()).isEqualTo(2);
        assertThat(service.rubricOf(PROJECT, marta.getEmail()).criteria())
                .extracting(RubricResponse.CriterionResponse::id)
                .containsExactly(1, 2);

        // (4.0 + 5.0) / 2 = 4.5, con los criterios de la edición.
        assertThat(service.save(PROJECT, marta.getEmail(), scores(score(1, 13), score(2, 23)))
                        .grade())
                .isEqualByComparingTo("4.5");
        assertThatThrownBy(() -> service.save(PROJECT, marta.getEmail(), scores(score(3, 32))))
                .isInstanceOfSatisfying(
                        InvalidFieldsException.class,
                        invalid -> assertThat(invalid.getFields())
                                .containsEntry("scores", "Hay criterios que no son de la rúbrica de esta edición"));
    }

    @Test
    void anEditionWithoutRubricCannotBeGraded() {
        when(settings.getRubricId()).thenReturn(null);

        assertThatThrownBy(() -> service.save(PROJECT, marta.getEmail(), scores(score(1, 13), score(2, 23))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("todavía no tiene rúbrica");
        assertThatThrownBy(() -> service.rubricOf(PROJECT, marta.getEmail()))
                .isInstanceOf(NoSuchElementException.class);
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void theGradeIsTheSimpleAverageOfTheCriteria() {
        EvaluationResponse saved = service.save(PROJECT, marta.getEmail(), scores(score(1, 13), score(2, 22)));

        // (4.0 + 4.5) / 2 = 4.25, que con un decimal sube a 4.3.
        assertThat(saved.grade()).isEqualByComparingTo("4.3");
        assertThat(saved.scale()).isEqualTo(GradeScale.GOOD);
        assertThat(saved.absent()).isFalse();
        assertThat(saved.juror()).isEqualTo(marta.fullName());
        assertThat(saved.scores())
                .extracting(EvaluationResponse.ScoreResponse::levelId)
                .containsExactly(13, 22);
    }

    @Test
    void everyCriterionNeedsALevelOfItsOwn() {
        // Falta el segundo criterio.
        assertThatThrownBy(() -> service.save(PROJECT, marta.getEmail(), scores(score(1, 13))))
                .isInstanceOfSatisfying(
                        InvalidFieldsException.class,
                        error -> assertThat(error.getFields()).containsOnlyKeys("scores.2.levelId"));
        // El nivel 23 es del segundo criterio, no del primero.
        assertThatThrownBy(() -> service.save(PROJECT, marta.getEmail(), scores(score(1, 23), score(2, 23))))
                .isInstanceOfSatisfying(
                        InvalidFieldsException.class,
                        error -> assertThat(error.getFields()).containsOnlyKeys("scores.1.levelId"));
        // Un criterio que no es de esta rúbrica.
        assertThatThrownBy(
                        () -> service.save(PROJECT, marta.getEmail(), scores(score(1, 13), score(2, 23), score(7, 71))))
                .isInstanceOfSatisfying(
                        InvalidFieldsException.class,
                        error -> assertThat(error.getFields()).containsOnlyKeys("scores"));
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void theCommentIsOptionalWhenPassingAndRequiredBelowThree() {
        // 3.0 aprueba: sin observación. 1.5 no: la pide, y una en blanco no cuenta.
        assertThatThrownBy(() ->
                        service.save(PROJECT, marta.getEmail(), scores(score(1, 12), new ScoreRequest(2, 21, "   "))))
                .isInstanceOfSatisfying(
                        InvalidFieldsException.class,
                        error -> assertThat(error.getFields()).containsOnlyKeys("scores.2.comment"));

        EvaluationResponse saved = service.save(
                PROJECT,
                marta.getEmail(),
                scores(score(1, 12), new ScoreRequest(2, 21, "  No se entendió el modelo de negocio.  ")));

        assertThat(saved.grade()).isEqualByComparingTo("2.3");
        assertThat(saved.scale()).isEqualTo(GradeScale.FAILING);
        assertThat(saved.scores().get(0).comment()).isNull();
        assertThat(saved.scores().get(1).comment()).isEqualTo("No se entendió el modelo de negocio.");
    }

    @Test
    void ifTheTeamDidNotShowUpTheEvaluationIsZeroWithoutLevels() {
        Evaluation existing = Evaluation.of(project, marta);
        existing.score(rubric.getCriteria().get(0), level(rubric, 13), null, NOW);
        existing.score(rubric.getCriteria().get(1), level(rubric, 23), null, NOW);
        when(evaluationRepository.findByProjectIdAndJurorId(PROJECT, 8)).thenReturn(Optional.of(existing));

        EvaluationResponse saved = service.save(PROJECT, marta.getEmail(), new EvaluationRequest(true, null));

        assertThat(saved.absent()).isTrue();
        assertThat(saved.grade()).isEqualByComparingTo("0.0");
        assertThat(saved.scale()).isEqualTo(GradeScale.FAILING);
        assertThat(saved.scores()).isEmpty();
    }

    @Test
    void correctingChangesTheSameRowsInsteadOfAddingMore() {
        Evaluation existing = Evaluation.of(project, marta);
        existing.markAbsent(NOW);
        when(evaluationRepository.findByProjectIdAndJurorId(PROJECT, 8)).thenReturn(Optional.of(existing));

        service.save(PROJECT, marta.getEmail(), scores(score(1, 12), score(2, 22)));
        EvaluationResponse corrected = service.save(PROJECT, marta.getEmail(), scores(score(1, 13), score(2, 23)));

        assertThat(existing.getScores()).hasSize(2);
        assertThat(corrected.absent()).isFalse();
        assertThat(corrected.grade()).isEqualByComparingTo("4.5");
    }

    @Test
    void correctingASavedEvaluationLeavesATraceWithTheGradeItHad() {
        Evaluation existing = Evaluation.of(project, marta);
        ReflectionTestUtils.setField(existing, "id", 40);
        existing.score(rubric.getCriteria().get(0), level(rubric, 13), null, NOW);
        existing.score(rubric.getCriteria().get(1), level(rubric, 23), null, NOW);
        when(evaluationRepository.findByProjectIdAndJurorId(PROJECT, 8)).thenReturn(Optional.of(existing));
        when(project.getTitle()).thenReturn("BioSensor");

        // De (4.0 + 5.0) / 2 = 4.5 a (3.0 + 4.5) / 2 = 3.75, que sube a 3.8.
        service.save(PROJECT, marta.getEmail(), scores(score(1, 12), score(2, 22)));

        verify(events)
                .publishEvent(new AuditableAction(
                        Action.EVALUATION_EDITED, 40, "BioSensor", "Jurado: Nombre Apellido · Nota: 4.5 → 3.8"));

        // Si el equipo ya está viendo las notas, la corrección lo dice.
        when(settings.isGradesPublished()).thenReturn(true);
        service.save(PROJECT, marta.getEmail(), new EvaluationRequest(true, null));

        verify(events)
                .publishEvent(new AuditableAction(
                        Action.EVALUATION_EDITED,
                        40,
                        "BioSensor",
                        "Jurado: Nombre Apellido · Nota: 3.8 → no asistió (0.0) · con las notas ya publicadas"));
    }

    @Test
    void correctingTheRubricLaterDoesNotRewriteAGradeAlreadyGiven() {
        Evaluation existing = Evaluation.of(project, marta);
        existing.score(rubric.getCriteria().get(0), level(rubric, 13), null, NOW);
        existing.score(rubric.getCriteria().get(1), level(rubric, 22), null, NOW);
        when(evaluationRepository.findByProjectIdAndJurorId(PROJECT, 8)).thenReturn(Optional.of(existing));

        // MacondoLab corrige la rúbrica: el nivel que valía 4.0 ahora vale 5.0.
        level(rubric, 13).setScore(new BigDecimal("5.0"));

        // Lo que Marta ya calificó sigue valiendo lo que valía: (4.0 + 4.5) / 2 = 4.25, que sube a 4.3.
        EvaluationResponse kept = service.mine(PROJECT, marta.getEmail()).orElseThrow();
        assertThat(kept.grade()).isEqualByComparingTo("4.3");
        assertThat(kept.scores())
                .extracting(score -> score.score().toPlainString())
                .containsExactly("4.0", "4.5");

        // Si vuelve a guardar, califica con la rúbrica que tiene delante: (5.0 + 4.5) / 2 = 4.75, que sube a 4.8.
        EvaluationResponse again = service.save(PROJECT, marta.getEmail(), scores(score(1, 13), score(2, 22)));
        assertThat(again.grade()).isEqualByComparingTo("4.8");
        assertThat(again.scores())
                .extracting(score -> score.score().toPlainString())
                .containsExactly("5.0", "4.5");
    }

    @Test
    void evaluatingForTheFirstTimeLeavesNoTrace() {
        when(evaluationRepository.findByProjectIdAndJurorId(PROJECT, 8)).thenReturn(Optional.empty());

        service.save(PROJECT, marta.getEmail(), scores(score(1, 13), score(2, 23)));

        verify(events, never()).publishEvent(any());
    }

    @Test
    void theProjectGradeAveragesTheJurorsWhoAreStillAssigned() {
        User removed = TestData.user(12, "otro@empresa.com", Role.JUDGE);
        when(evaluatorRule.evaluatorsOf(project)).thenReturn(List.of(marta, pedro));
        when(evaluationRepository.findByProjectIdOrderByCreatedAtAsc(PROJECT))
                .thenReturn(List.of(evaluation(marta, 13, 23), evaluation(removed, 11, 21)));

        ProjectEvaluationsResponse results = service.results(PROJECT, carlos.getEmail());

        // Solo cuenta Marta (4.5): a Pedro le falta y el tercero ya no es jurado.
        assertThat(results.grade()).isEqualByComparingTo("4.5");
        assertThat(results.scale()).isEqualTo(GradeScale.VERY_GOOD);
        assertThat(results.jurors()).isEqualTo(2);
        assertThat(results.evaluations())
                .extracting(EvaluationResponse::jurorId)
                .containsExactly(8);
        assertThat(results.pending())
                .extracting(ProjectEvaluationsResponse.PendingJuror::userId)
                .containsExactly(9);

        when(evaluationRepository.findByProjectIdOrderByCreatedAtAsc(PROJECT))
                .thenReturn(List.of(evaluation(marta, 13, 23), evaluation(pedro, 12, 22)));
        // Marta 4.5 y Pedro 3.8 (3.75): (4.5 + 3.8) / 2 = 4.15, que sube a 4.2.
        assertThat(service.results(PROJECT, coordination.getEmail()).grade()).isEqualByComparingTo("4.2");
    }

    @Test
    void withoutEvaluationsThereIsNoGradeYet() {
        when(evaluatorRule.evaluatorsOf(project)).thenReturn(List.of(marta));
        when(evaluationRepository.findByProjectIdOrderByCreatedAtAsc(PROJECT)).thenReturn(List.of());

        ProjectEvaluationsResponse results = service.results(PROJECT, coordination.getEmail());

        assertThat(results.grade()).isNull();
        assertThat(results.scale()).isNull();
        assertThat(results.pending()).hasSize(1);
    }

    @Test
    void gradesAreForTheGroupTeacherAndManagement() {
        assertThatThrownBy(() -> service.results(PROJECT, ana.getEmail()))
                .isInstanceOf(ForbiddenActionException.class)
                .hasMessageContaining("profesor del grupo o la gestión");
        // Un jurado ve la suya, no la de los demás.
        assertThatThrownBy(() -> service.results(PROJECT, marta.getEmail()))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void listsGetTheGradeOfEveryProjectAtOnce() {
        Project other = mock(Project.class);
        when(other.getId()).thenReturn(11);
        when(evaluatorRule.evaluatorsByProject(List.of(project, other)))
                .thenReturn(Map.of(PROJECT, List.of(marta, pedro), 11, List.of(marta)));
        when(evaluationRepository.findByProjectIdInOrderByCreatedAtAsc(List.of(PROJECT, 11)))
                .thenReturn(List.of(evaluation(marta, 13, 23), evaluation(pedro, 12, 22)));

        Map<Integer, ProjectGrade> grades = service.gradesOf(List.of(project, other));

        // Marta 4.5 y Pedro 3.8: 4.2 con los dos jurados; el otro proyecto no tiene nada todavía.
        assertThat(grades.get(PROJECT)).isEqualTo(new ProjectGrade(new BigDecimal("4.2"), GradeScale.GOOD, 2, 2));
        assertThat(grades.get(11)).isEqualTo(new ProjectGrade(null, null, 1, 0));
        assertThat(service.gradesOf(List.of())).isEmpty();
    }

    @Test
    void theReminderGoesOnceToEachJurorWithTheirPendingProjects() {
        Project other = mock(Project.class);
        when(other.getId()).thenReturn(11);
        when(other.getTitle()).thenReturn("Riego inteligente");
        when(project.getTitle()).thenReturn("BioSensor");
        when(projectRepository.search(5, Track.INNPRENDE_I, null, null, null, null, Sort.unsorted()))
                .thenReturn(List.of(project, other));
        when(evaluatorRule.evaluatorsByProject(List.of(project, other)))
                .thenReturn(Map.of(PROJECT, List.of(marta, pedro), 11, List.of(marta)));
        // Marta ya calificó BioSensor; le falta Riego. A Pedro le falta BioSensor.
        when(evaluationRepository.findByProjectIdInOrderByCreatedAtAsc(List.of(PROJECT, 11)))
                .thenReturn(List.of(evaluation(marta, 13, 23)));

        ReminderResponse sent = service.remind(5, Track.INNPRENDE_I);

        assertThat(sent).isEqualTo(new ReminderResponse(2, 2));
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events, times(2)).publishEvent(published.capture());
        assertThat(published.getAllValues())
                .containsExactlyInAnyOrder(
                        new EvaluationReminderEvent(marta.getEmail(), marta.fullName(), List.of("Riego inteligente")),
                        new EvaluationReminderEvent(pedro.getEmail(), pedro.fullName(), List.of("BioSensor")));
    }

    @Test
    void withNothingPendingNobodyGetsMail() {
        when(projectRepository.search(5, Track.INNPRENDE_I, null, null, null, null, Sort.unsorted()))
                .thenReturn(List.of(project));
        when(evaluatorRule.evaluatorsByProject(List.of(project))).thenReturn(Map.of(PROJECT, List.of(marta)));
        when(evaluationRepository.findByProjectIdInOrderByCreatedAtAsc(List.of(PROJECT)))
                .thenReturn(List.of(evaluation(marta, 13, 23)));

        assertThat(service.remind(5, Track.INNPRENDE_I)).isEqualTo(new ReminderResponse(0, 0));
        verify(events, never()).publishEvent(any());
    }

    // ── Datos de apoyo ──────────────────────────────────────────────────────

    /** Un criterio con id {@code number} y niveles con ids {@code number}1, {@code number}2... */
    private static RubricCriterion criterion(int number, String... scores) {
        RubricCriterion criterion = new RubricCriterion();
        criterion.setId(number);
        criterion.setPosition(number);
        criterion.setName("Criterio " + number);
        criterion.setShortName("C" + number);
        for (int index = 0; index < scores.length; index++) {
            RubricLevel level = new RubricLevel();
            level.setId(number * 10 + index + 1);
            level.setCriterion(criterion);
            level.setPosition(index + 1);
            level.setLabel("Nivel " + (index + 1));
            level.setScore(new BigDecimal(scores[index]));
            level.setDescription("Descripción");
            criterion.getLevels().add(level);
        }
        return criterion;
    }

    private static Rubric rubric(RubricCriterion... criteria) {
        Rubric rubric = new Rubric();
        rubric.setId(1);
        rubric.setTrack(Track.INNPRENDE_I);
        rubric.setName("Póster");
        rubric.getCriteria().addAll(List.of(criteria));
        return rubric;
    }

    private static RubricLevel level(Rubric rubric, int levelId) {
        return rubric.getCriteria().stream()
                .flatMap(criterion -> criterion.getLevels().stream())
                .filter(level -> level.getId() == levelId)
                .findFirst()
                .orElseThrow();
    }

    private Evaluation evaluation(User juror, int firstLevel, int secondLevel) {
        Evaluation evaluation = Evaluation.of(project, juror);
        evaluation.setId(juror.getId() * 100);
        evaluation.score(rubric.getCriteria().get(0), level(rubric, firstLevel), "Observación", NOW);
        evaluation.score(rubric.getCriteria().get(1), level(rubric, secondLevel), "Observación", NOW);
        return evaluation;
    }

    private static ScoreRequest score(int criterionId, int levelId) {
        return new ScoreRequest(criterionId, levelId, null);
    }

    private static EvaluationRequest scores(ScoreRequest... scores) {
        return new EvaluationRequest(false, List.of(scores));
    }
}
