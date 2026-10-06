-- Cerrar sesiones y suspender cuentas.
--
-- token_version: cada token de sesión lleva la versión que tenía la cuenta al
-- emitirlo, y solo vale mientras coincida. Cambiar o restablecer la contraseña
-- la sube: lo que estuviera abierto con la contraseña anterior deja de entrar,
-- en vez de seguir valiendo hasta que el token venza. Los tokens emitidos antes
-- de esta migración no traen versión y cuentan como 0, así que siguen valiendo.
--
-- enabled: una cuenta suspendida no inicia sesión ni usa la que tuviera. Hasta
-- ahora la única forma de cortar el acceso era eliminar la cuenta, y una cuenta
-- con historia (un equipo, un proyecto a cargo, una evaluación) no se puede
-- eliminar.

ALTER TABLE users
  ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER must_change_password,
  ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER role;
