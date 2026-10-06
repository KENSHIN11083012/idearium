-- Listado de la cátedra (reunión de septiembre de 2026, punto 10).
--
-- MacondoLab carga quiénes cursan o dictan la cátedra en el semestre, con su
-- rol. Cuando una de esas personas se registra, la cuenta nace con ese rol (y
-- con el nombre, si venía) en vez de nacer como estudiante. Quien no está en
-- el listado queda como estudiante, y el administrador le cambia el rol a mano.
--
-- El listado no es una cuenta ni apunta a una: si el correo ya tiene cuenta, el
-- listado no la toca. Por eso no hay clave foránea a users: se cruza por correo.

CREATE TABLE roster_entries (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  email      VARCHAR(150) NOT NULL,
  role       VARCHAR(30)  NOT NULL,
  first_name VARCHAR(100) NULL,
  last_name  VARCHAR(100) NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_roster_entries_email UNIQUE (email),
  CONSTRAINT chk_roster_entries_role CHECK (role IN ('STUDENT', 'TEACHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
