-- Esquema inicial de Expoideas.
--
-- Reúne en una sola migración el estado al que llegó el desarrollo antes del
-- primer despliegue. A partir de aquí, cada cambio de esquema va en una
-- migración nueva (V2, V3...) y nunca se edita una que ya se aplicó.
--
-- Los valores de los enums se guardan como texto en mayúsculas (el nombre del
-- enum en Java) con un CHECK: agregar un valor es cambiar el enum y la
-- constraint en una migración.

-- ---------------------------------------------------------------
-- Estructura académica: la escribe el administrador
-- ---------------------------------------------------------------

CREATE TABLE campuses (
  id   INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  CONSTRAINT uk_campuses_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE faculties (
  id   INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(150) NOT NULL,
  CONSTRAINT uk_faculties_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE academic_programs (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(150) NOT NULL,
  faculty_id INT NOT NULL,
  CONSTRAINT fk_academic_programs_faculty FOREIGN KEY (faculty_id) REFERENCES faculties (id),
  -- El mismo nombre puede repetirse en facultades distintas, no dentro de una.
  CONSTRAINT uk_academic_programs_faculty_name UNIQUE (faculty_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- Clasificación de proyectos: la escriben MacondoLab y el administrador
-- ---------------------------------------------------------------

CREATE TABLE categories (
  id   INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  CONSTRAINT uk_categories_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE keywords (
  id   INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  CONSTRAINT uk_keywords_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- Cuentas
-- ---------------------------------------------------------------
--
-- role: ADMIN (parte técnica; incluye todo lo de MACONDOLAB), MACONDOLAB
-- (coordinación de INNPRENDE I y II), TEACHER (docente), JUDGE (jurado, puede
-- ser externo) y STUDENT (rol con el que nace toda cuenta registrada).
--
-- Docentes y estudiantes declaran su adscripción: sede y facultad obligatorias
-- y programa opcional; si hay programa, es de esa facultad. Lo valida la API.
--
-- must_change_password: la contraseña la puso la gestión (cuenta creada o
-- restablecida) y es temporal. data_consent: autorización de tratamiento de
-- datos personales (Ley 1581 de 2012). Mientras falte alguna de las dos cosas,
-- la API solo deja completar el primer ingreso.

CREATE TABLE users (
  id                   INT AUTO_INCREMENT PRIMARY KEY,
  first_name           VARCHAR(100) NOT NULL,
  last_name            VARCHAR(100) NOT NULL,
  email                VARCHAR(150) NOT NULL,
  password_hash        VARCHAR(255) NOT NULL,
  must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
  role                 VARCHAR(30)  NOT NULL DEFAULT 'STUDENT',
  data_consent         BOOLEAN      NOT NULL DEFAULT FALSE,
  data_consent_at      DATETIME     NULL,
  campus_id            INT          NULL,
  faculty_id           INT          NULL,
  academic_program_id  INT          NULL,
  photo_id             INT          NULL,
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_users_email UNIQUE (email),
  CONSTRAINT fk_users_campus FOREIGN KEY (campus_id) REFERENCES campuses (id),
  CONSTRAINT fk_users_faculty FOREIGN KEY (faculty_id) REFERENCES faculties (id),
  CONSTRAINT fk_users_academic_program FOREIGN KEY (academic_program_id) REFERENCES academic_programs (id),
  CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'MACONDOLAB', 'TEACHER', 'JUDGE', 'STUDENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_users_role ON users (role);

-- ---------------------------------------------------------------
-- Archivos subidos
-- ---------------------------------------------------------------
--
-- Solo los metadatos: el contenido vive en disco (FILES_DIR), en storage_path.
-- uuid es el identificador público, el único que sale por la API. TI fija un
-- máximo de 5 MB por archivo.

CREATE TABLE files (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  uuid          VARCHAR(36)  NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type  VARCHAR(100) NOT NULL,
  size_bytes    INT          NOT NULL,
  sha256        VARCHAR(64)  NOT NULL,
  storage_path  VARCHAR(255) NOT NULL,
  visibility    VARCHAR(20)  NOT NULL,
  owner_id      INT          NOT NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_files_uuid UNIQUE (uuid),
  CONSTRAINT uk_files_storage_path UNIQUE (storage_path),
  CONSTRAINT fk_files_owner FOREIGN KEY (owner_id) REFERENCES users (id),
  CONSTRAINT chk_files_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
  CONSTRAINT chk_files_size CHECK (size_bytes BETWEEN 1 AND 5242880)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_files_owner ON files (owner_id);

-- La foto de perfil es un archivo subido por la propia persona. users y files
-- se referencian entre sí, así que esta FK va después de crear las dos tablas.
ALTER TABLE users
  ADD CONSTRAINT fk_users_photo FOREIGN KEY (photo_id) REFERENCES files (id) ON DELETE SET NULL;

-- ---------------------------------------------------------------
-- Datos iniciales
-- ---------------------------------------------------------------

-- El registro exige sede: la universidad tiene dos.
INSERT INTO campuses (name) VALUES ('Barranquilla'), ('Cúcuta');
