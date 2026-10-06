package co.edu.unisimon.expoideas.catalogs;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AcademicProgramRepository extends JpaRepository<AcademicProgram, Integer> {

    /** Listado con la facultad resuelta: evita una consulta por programa al armar la respuesta. */
    @Query("SELECT p FROM AcademicProgram p JOIN FETCH p.faculty")
    List<AcademicProgram> findAllWithFaculty();
}
