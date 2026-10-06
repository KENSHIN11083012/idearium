package co.edu.unisimon.expoideas.catalogs;

/** Programa académico con su facultad ya resuelta. */
public record AcademicProgramResponse(Integer id, String name, Integer facultyId, String faculty) {

    /** Requiere la facultad cargada (fetch join o dentro de la transacción). */
    public static AcademicProgramResponse from(AcademicProgram program) {
        return new AcademicProgramResponse(
                program.getId(),
                program.getName(),
                program.getFaculty().getId(),
                program.getFaculty().getName());
    }
}
