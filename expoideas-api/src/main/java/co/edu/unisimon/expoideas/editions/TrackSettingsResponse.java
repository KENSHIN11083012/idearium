package co.edu.unisimon.expoideas.editions;

import java.time.LocalDateTime;

/**
 * Configuración de una cátedra tal como sale por la API.
 *
 * @param rubricId          la versión de la rúbrica con la que se califica, o null si no tiene
 * @param gradesPublishedAt desde cuándo los equipos ven su nota, o null si todavía no
 */
public record TrackSettingsResponse(
        Track track, int minMembers, int maxMembers, Integer rubricId, LocalDateTime gradesPublishedAt) {

    public static TrackSettingsResponse from(EditionTrack settings) {
        return new TrackSettingsResponse(
                settings.getTrack(),
                settings.getMinMembers(),
                settings.getMaxMembers(),
                settings.getRubricId(),
                settings.getGradesPublishedAt());
    }
}
