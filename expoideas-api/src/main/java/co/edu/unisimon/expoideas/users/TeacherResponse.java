package co.edu.unisimon.expoideas.users;

/** Docente como lo ve quien inscribe un proyecto: para elegirlo de una lista. */
public record TeacherResponse(Integer id, String fullName, String faculty) {

    public static TeacherResponse from(User teacher) {
        return new TeacherResponse(
                teacher.getId(),
                teacher.fullName(),
                teacher.getFaculty() == null ? null : teacher.getFaculty().getName());
    }
}
