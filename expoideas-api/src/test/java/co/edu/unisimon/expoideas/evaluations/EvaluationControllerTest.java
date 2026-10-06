package co.edu.unisimon.expoideas.evaluations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.unisimon.expoideas.common.InvalidFieldsException;
import co.edu.unisimon.expoideas.editions.Track;
import co.edu.unisimon.expoideas.support.SecuredWebMvcTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Evaluación: cada jurado guarda la suya; la rúbrica y las notas piden sesión. */
@SecuredWebMvcTest({EvaluationController.class, RubricController.class})
class EvaluationControllerTest {

    private static final String JUROR = "marta@empresa.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluationService evaluationService;

    @Test
    @WithMockUser(username = JUROR, roles = "JUDGE")
    void aJurorReadsAndSavesTheirEvaluation() throws Exception {
        when(evaluationService.mine(10, JUROR)).thenReturn(Optional.empty());
        when(evaluationService.save(eq(10), eq(JUROR), any())).thenReturn(evaluation());
        when(evaluationService.allMine(JUROR)).thenReturn(List.of(evaluation()));

        mockMvc.perform(get("/api/v1/projects/10/evaluations/mine")).andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/projects/10/evaluations/mine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"absent\":false,\"scores\":[{\"criterionId\":1,\"levelId\":5,\"comment\":null}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value(4.5))
                .andExpect(jsonPath("$.scale").value("VERY_GOOD"))
                .andExpect(jsonPath("$.scores[0].levelId").value(5));
        mockMvc.perform(get("/api/v1/evaluations/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(10));
    }

    @Test
    @WithMockUser(username = JUROR, roles = "JUDGE")
    void aScoreWithoutLevelOrWithAnEndlessCommentIsAFieldError() throws Exception {
        mockMvc.perform(put("/api/v1/projects/10/evaluations/mine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scores\":[{\"criterionId\":1,\"comment\":\"" + "a".repeat(501) + "\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['scores[0].levelId']").exists())
                .andExpect(jsonPath("$.fields['scores[0].comment']").exists());
        verifyNoInteractions(evaluationService);
    }

    @Test
    @WithMockUser(username = JUROR, roles = "JUDGE")
    void whatTheRubricRejectsComesBackByCriterion() throws Exception {
        when(evaluationService.save(any(), any(), any()))
                .thenThrow(new InvalidFieldsException(Map.of("scores.2.comment", "Explica la calificación")));

        mockMvc.perform(put("/api/v1/projects/10/evaluations/mine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scores\":[{\"criterionId\":2,\"levelId\":7}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['scores.2.comment']").value("Explica la calificación"));
    }

    @Test
    @WithMockUser(username = "carlos@unisimon.edu.co", roles = "TEACHER")
    void theRubricAndTheProjectGradeAreRead() throws Exception {
        when(evaluationService.rubric(Track.INNPRENDE_II))
                .thenReturn(new RubricResponse(
                        2,
                        Track.INNPRENDE_II,
                        1,
                        "Pitch de proyecto de innovación",
                        null,
                        List.of(new RubricResponse.CriterionResponse(
                                1,
                                1,
                                "Propuesta de valor",
                                "Propuesta de valor",
                                List.of(new RubricResponse.LevelResponse(
                                        5, 1, "Muy deficiente", new BigDecimal("0.0"), "No presenta."))))));
        when(evaluationService.rubricOf(10, "carlos@unisimon.edu.co"))
                .thenReturn(new RubricResponse(7, Track.INNPRENDE_II, 3, "Pitch, tercera versión", null, List.of()));
        when(evaluationService.results(10, "carlos@unisimon.edu.co"))
                .thenReturn(new ProjectEvaluationsResponse(
                        10, new BigDecimal("4.5"), GradeScale.VERY_GOOD, 2, List.of(evaluation()), List.of()));

        mockMvc.perform(get("/api/v1/rubrics/INNPRENDE_II"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria[0].levels[0].score").value(0.0))
                .andExpect(jsonPath("$.criteria[0].levels[0].label").value("Muy deficiente"));
        mockMvc.perform(get("/api/v1/rubrics/OTRA")).andExpect(status().isBadRequest());
        // La de un proyecto es la de su edición, que puede no ser la vigente.
        mockMvc.perform(get("/api/v1/projects/10/rubric"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.version").value(3));
        mockMvc.perform(get("/api/v1/projects/10/evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value(4.5))
                .andExpect(jsonPath("$.jurors").value(2));
    }

    @Test
    @WithMockUser(roles = "MACONDOLAB")
    void managementRemindsTheJurors() throws Exception {
        when(evaluationService.remind(1, Track.INNPRENDE_I)).thenReturn(new ReminderResponse(2, 3));

        mockMvc.perform(post("/api/v1/evaluations/reminders")
                        .param("editionId", "1")
                        .param("track", "INNPRENDE_I"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jurors").value(2))
                .andExpect(jsonPath("$.projects").value(3));
        mockMvc.perform(post("/api/v1/evaluations/reminders").param("editionId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = JUROR, roles = "JUDGE")
    void aJurorDoesNotSendReminders() throws Exception {
        mockMvc.perform(post("/api/v1/evaluations/reminders")
                        .param("editionId", "1")
                        .param("track", "INNPRENDE_I"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(evaluationService);
    }

    @Test
    void everythingAsksForASession() throws Exception {
        mockMvc.perform(get("/api/v1/rubrics/INNPRENDE_I")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects/10/rubric")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects/10/evaluations")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/projects/10/evaluations/mine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"absent\":true}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(evaluationService);
    }

    private static EvaluationResponse evaluation() {
        return new EvaluationResponse(
                4,
                10,
                8,
                "Marta Ríos",
                false,
                new BigDecimal("4.5"),
                GradeScale.VERY_GOOD,
                List.of(new EvaluationResponse.ScoreResponse(1, 5, new BigDecimal("4.5"), null)),
                LocalDateTime.of(2026, 11, 20, 10, 0));
    }
}
