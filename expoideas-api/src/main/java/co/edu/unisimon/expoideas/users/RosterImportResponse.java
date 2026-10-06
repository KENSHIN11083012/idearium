package co.edu.unisimon.expoideas.users;

import java.util.List;

/**
 * Qué pasó con una carga del listado: cuántas filas entraron nuevas, cuántas
 * actualizaron una que ya estaba, cuántas ya tienen cuenta, y las que se
 * rechazaron con el motivo y la línea del archivo.
 */
public record RosterImportResponse(int added, int updated, int registered, int total, List<RejectedRow> rejected) {

    public record RejectedRow(int line, String email, String reason) {}
}
