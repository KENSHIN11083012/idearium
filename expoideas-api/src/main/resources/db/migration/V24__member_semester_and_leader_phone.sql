-- Dos datos que pedía el formulario con el que se inscribían los proyectos antes
-- de la plataforma (MacondoLab, 6 de octubre de 2026): el semestre que cursa cada
-- integrante y el celular del líder.
--
-- Van en el equipo y no en la cuenta porque son de esa inscripción: quien vuelve
-- en INNPRENDE II ya está en otro semestre, y el celular es el de contacto de ese
-- proyecto.
--
-- Los dos admiten NULL: una invitación sin responder todavía no dice su semestre,
-- el celular solo lo da el líder, y los equipos inscritos antes de esta migración
-- no tienen ninguno de los dos.

ALTER TABLE project_members
  ADD COLUMN semester INT NULL AFTER status,
  ADD COLUMN phone VARCHAR(20) NULL AFTER semester,
  ADD CONSTRAINT chk_project_members_semester CHECK (semester BETWEEN 1 AND 12);
