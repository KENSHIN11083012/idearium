package co.edu.unisimon.expoideas.deliverables;

import java.util.List;

/**
 * Un entregable de la cátedra con lo que el proyecto lleva subido. {@code
 * complete} dice si ese entregable ya está resuelto: los opcionales siempre lo
 * están, y los obligatorios, cuando hay al menos un archivo.
 */
public record DeliverableGroupResponse(
        DeliverableTypeResponse type, List<DeliverableResponse> files, boolean complete) {

    public static DeliverableGroupResponse of(DeliverableType type, List<Deliverable> uploaded) {
        return new DeliverableGroupResponse(
                DeliverableTypeResponse.from(type),
                uploaded.stream().map(DeliverableResponse::from).toList(),
                !type.isRequired() || !uploaded.isEmpty());
    }
}
