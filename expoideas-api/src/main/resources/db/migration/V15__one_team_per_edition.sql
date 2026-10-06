-- Un estudiante, un equipo aceptado por edición, garantizado por la base.
--
-- Las dos reglas (un proyecto por cátedra en cada edición, y no estar en las dos
-- cátedras a la vez) dicen juntas lo mismo: una sola membresía aceptada por
-- persona y edición. Hasta ahora solo las comprobaba la API antes de guardar, y
-- dos peticiones a la vez (un doble clic, dos invitaciones aceptadas en el mismo
-- instante) pasaban las dos.
--
-- Las invitaciones sin responder no cuentan: a una persona la pueden invitar
-- varios equipos y acepta uno. Por eso la restricción va sobre una columna
-- generada que solo tiene valor cuando la fila está aceptada; MySQL deja repetir
-- los NULL en un índice único.

-- La edición del proyecto, copiada en cada integrante. La llave foránea compuesta
-- impide que la copia diga otra cosa que el proyecto.
ALTER TABLE projects
  ADD CONSTRAINT uk_projects_id_edition UNIQUE (id, edition_id);

ALTER TABLE project_members
  ADD COLUMN edition_id INT NULL AFTER project_id;

UPDATE project_members m
  JOIN projects p ON p.id = m.project_id
   SET m.edition_id = p.edition_id;

ALTER TABLE project_members
  MODIFY COLUMN edition_id INT NOT NULL,
  ADD COLUMN accepted_edition_id INT AS (CASE WHEN status = 'ACCEPTED' THEN edition_id END) VIRTUAL,
  ADD CONSTRAINT fk_project_members_project_edition
    FOREIGN KEY (project_id, edition_id) REFERENCES projects (id, edition_id) ON DELETE CASCADE,
  ADD CONSTRAINT uk_project_members_one_per_edition UNIQUE (user_id, accepted_edition_id);
