-- Verificación del correo al registrarse y recuperación de la contraseña.
--
-- Las dos cosas funcionan igual: a la persona le llega por correo un enlace con
-- un token de un solo uso. Solo quien lee ese buzón lo tiene, y eso es lo que
-- demuestra que el correo es suyo.
--
-- account_tokens guarda el hash del token, no el token: quien pudiera leer esta
-- tabla no podría verificar cuentas ajenas ni cambiarles la contraseña. Cada
-- token tiene un propósito, vence y se marca al usarse.
--
-- email_verification_pending solo se enciende en las cuentas que se registran
-- cuando la plataforma puede enviar correos. Las anteriores, y las que crea la
-- gestión (que ya reciben su contraseña temporal por correo), no quedan
-- pendientes: activar el correo no deja a nadie fuera.

ALTER TABLE users
  ADD COLUMN email_verification_pending BOOLEAN NOT NULL DEFAULT FALSE AFTER email,
  ADD COLUMN email_verified_at DATETIME NULL AFTER email_verification_pending;

CREATE TABLE account_tokens (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  user_id    INT NOT NULL,
  purpose    VARCHAR(20) NOT NULL,
  -- SHA-256 del token, en hexadecimal.
  token_hash VARCHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  used_at    DATETIME NULL,
  created_at DATETIME NOT NULL,
  -- Eliminar la cuenta se lleva sus enlaces pendientes.
  CONSTRAINT fk_account_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT uk_account_tokens_hash UNIQUE (token_hash),
  CONSTRAINT chk_account_tokens_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_account_tokens_user ON account_tokens (user_id, purpose);
