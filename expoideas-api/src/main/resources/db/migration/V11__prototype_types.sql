-- Tipos de prototipo en INNPRENDE II (reunión de septiembre de 2026, punto 8).
--
-- MacondoLab define los tipos (digital, físico...) y, según el tipo, qué
-- evidencia se pide. El catálogo empieza vacío: los tipos los carga la gestión
-- desde Catálogos cuando la profesora los entregue. Mientras esté vacío, los
-- proyectos de II se inscriben sin tipo y ven solo los entregables generales.

CREATE TABLE prototype_types (
  id   INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  CONSTRAINT uk_prototype_types_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Solo los proyectos de INNPRENDE II llevan tipo; lo valida la API.
ALTER TABLE projects
  ADD COLUMN prototype_type_id INT NULL AFTER sector_id,
  ADD CONSTRAINT fk_projects_prototype_type FOREIGN KEY (prototype_type_id) REFERENCES prototype_types (id);

-- Un entregable con tipo solo se pide a los proyectos de ese tipo; sin tipo, a todos.
ALTER TABLE deliverable_types
  ADD COLUMN prototype_type_id INT NULL AFTER track,
  ADD CONSTRAINT fk_deliverable_types_prototype_type FOREIGN KEY (prototype_type_id) REFERENCES prototype_types (id);
