-- Plantillas, enlaces y cierre propio por entregable (reunión de septiembre de 2026).
--
-- template_file_id: el formato oficial (PDF, DOCX o PPTX) que el equipo descarga,
-- diligencia y sube. Es un archivo privado que puede leer cualquier sesión.
-- closes_on: cierre propio del entregable, para lo que se sube después de la
-- sustentación (las fotos de evidencia). Si es NULL, vale el cierre de la edición.
-- kind LINK: el entregable es una dirección (video, prototipo), no un archivo.

ALTER TABLE deliverable_types
  ADD COLUMN template_file_id INT NULL AFTER description,
  ADD COLUMN closes_on DATE NULL AFTER sort_order,
  ADD CONSTRAINT fk_deliverable_types_template FOREIGN KEY (template_file_id) REFERENCES files (id),
  DROP CHECK chk_deliverable_types_kind,
  ADD CONSTRAINT chk_deliverable_types_kind CHECK (kind IN ('DOCUMENT', 'IMAGE', 'ANY', 'LINK'));

-- Una fila es un archivo o un enlace, nunca las dos cosas ni ninguna.
ALTER TABLE deliverables
  MODIFY file_id INT NULL,
  ADD COLUMN url VARCHAR(500) NULL AFTER file_id,
  ADD CONSTRAINT chk_deliverables_file_or_url CHECK ((file_id IS NULL) <> (url IS NULL));
