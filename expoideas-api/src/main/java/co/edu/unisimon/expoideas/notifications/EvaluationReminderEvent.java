package co.edu.unisimon.expoideas.notifications;

import java.util.List;

/**
 * La gestión pidió recordar a un jurado lo que le falta por calificar.
 *
 * @param projectTitles los proyectos sin evaluación de ese jurado
 */
public record EvaluationReminderEvent(String email, String fullName, List<String> projectTitles) {}
