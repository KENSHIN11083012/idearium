# Listado de la cátedra

> **Estado (septiembre de 2026):** TI no entrega este listado por ahora. El panel queda disponible por si
> llega; mientras tanto todos se registran como estudiantes y el administrador asigna los roles a mano.

Lo que MacondoLab carga en «Usuarios → Listado de la cátedra» para que cada persona se registre con
su rol (reunión de septiembre de 2026, punto 10). Quien no está en el listado nace como estudiante,
y el administrador le cambia el rol a mano.

Quien figura como **profesor** también nace como estudiante, con el rol de profesor **por
confirmar**: en «Usuarios» aparece el aviso «Pide rol: Profesor» y la gestión se lo da o lo descarta
desde el menú de la cuenta. El motivo es que, sin servidor de correo, el registro no puede comprobar
que quien se registra sea el dueño del correo; sin ese paso, cualquiera que conociera el correo de
un profesor podría registrarlo y quedar con su rol.

Con el servidor de correo configurado, el registro envía un enlace de verificación y el rol se da
solo al abrirlo, sin pasar por la gestión: quien abre el enlace demostró que el correo es suyo.

## El archivo

Un CSV, como lo exporta Excel con «Guardar como → CSV». La primera fila trae los títulos; las
columnas pueden ir en cualquier orden.

| Columna | Obligatoria | Qué va |
|---|---|---|
| `correo` | Sí | El correo institucional (`@unisimon.edu.co`). También vale `email`. |
| `rol` | Sí | `estudiante` o `profesor` (también `docente`). |
| `nombres` | No | Si viene, la persona lo ve puesto al completar su perfil y puede corregirlo. |
| `apellidos` | No | Igual que los nombres. |

Ejemplo (`listado-catedra.csv`, se descarga como plantilla desde la pantalla):

```csv
correo;rol;nombres;apellidos
ana.perez@unisimon.edu.co;estudiante;Ana María;Pérez
carlos.mendoza@unisimon.edu.co;profesor;Carlos;Mendoza
```

Se aceptan punto y coma o coma como separador, tildes en UTF-8 o en la codificación de Windows, y
mayúsculas en el correo (se guarda en minúsculas).

## Reglas

- Cargar otra vez **añade y actualiza**; no borra a nadie. Para quitar, se usa la pantalla (fila a
  fila o «Vaciar listado»).
- Las filas con correo no institucional, rol desconocido o correo repetido dentro del archivo se
  rechazan y se muestran con su línea; el resto entra igual.
- El listado solo actúa **al registrarse**. Si el correo ya tiene cuenta, no se le cambia nada: eso
  sigue siendo cosa del administrador en «Usuarios».
- Mientras su rol está por confirmar, la persona ve un aviso que se lo explica y usa la plataforma
  como estudiante. Al confirmarlo la gestión, el cambio le aparece sin volver a entrar.
- Cambiarle el rol desde el selector de «Usuarios», al que sea, también deja resuelto el pendiente.
- Solo estudiantes y profesores. Los jurados externos entran por invitación de la gestión.
