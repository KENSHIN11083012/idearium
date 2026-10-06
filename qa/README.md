# Pruebas de punta a punta

Lo que las pruebas de cada proyecto no ven: la plataforma entera levantada con Docker, con Nginx
delante, recorrida como la recorre una persona. Corre en la integración continua (job `e2e`) y se
puede correr en local.

| Qué | Con qué | Qué comprueba |
|---|---|---|
| `seed.mjs` | Node | Crea las nueve cuentas de prueba (un administrador, MacondoLab, dos profesores, un jurado externo y cuatro estudiantes) pasando por el registro y el primer ingreso de verdad |
| `smoke.mjs` | Node | Recorre la API por rol, con lo que debe funcionar y lo que debe rechazar: permisos, edición, equipo, entregables, jurados, evaluación, publicación de notas y rastro de auditoría |
| `e2e/` | Playwright | Lo mismo desde el navegador: registro y primer ingreso, guardas por rol, inscripción y equipo, entregables, vista del profesor, gestión, jurados, calificación con la rúbrica (incluido el borrador que sobrevive a recargar) y diseño a 375 px |

## Correrlas

Con la plataforma encendida (`docker compose up -d --build`) y Node 24, desde la raíz del repositorio:

```bash
node qa/seed.mjs
```

```bash
node qa/smoke.mjs
```

```bash
cd qa/e2e && npm ci && npx playwright install chromium && npx playwright test
```

El orden importa: el sembrado crea las cuentas, el humo deja la edición «Expo QA» con su sector y sus
entregables, y Playwright cuenta con las dos cosas. Los tres se pueden repetir: reutilizan lo que ya
existe y borran los proyectos que crean.

Si Playwright falla, `npx playwright show-report` (dentro de `qa/e2e`) abre el informe con la captura
y la traza del fallo.

## Contra qué instalación

Por defecto, la de `http://localhost:8080`. **No se corren contra una instalación con datos reales**:
crean cuentas, una facultad, una edición y proyectos. Para no usar la instalación de trabajo se
levanta otra aparte, que arranca con su primer administrador como lo haría una de verdad:

```bash
export COMPOSE_PROJECT_NAME=idearium-prueba HTTP_PORT=8090
export QA_API=http://localhost:8090/expoideas/api/v1 QA_APP=http://localhost:8090/expoideas/
export ADMIN_EMAIL=qa.admin@unisimon.edu.co ADMIN_PASSWORD='Temporal#2026'
docker compose up -d --build
```

Con `ADMIN_PASSWORD` en el entorno, el sembrado entra con esa contraseña temporal y hace el primer
ingreso del administrador. Sin ella (una instalación que ya estaba arrancada), lo registra y lo
vuelve administrador por SQL.

Y al terminar, `docker compose down -v` (con esas variables todavía puestas) la borra entera.

| Variable | Por defecto | Para qué |
|---|---|---|
| `QA_API` | `http://localhost:8080/expoideas/api/v1` | La API que usan el sembrado, el humo y la preparación de cada spec |
| `QA_APP` | `http://localhost:8080/expoideas/` | La app que abre Playwright |
| `QA_CREDS` | `qa/credenciales.json` | Dónde quedan las cuentas de prueba |
| `ADMIN_PASSWORD` | — | La contraseña temporal con la que arrancó el primer administrador (`qa.admin@unisimon.edu.co`) |
| `COMPOSE_PROJECT_NAME` | el del `docker-compose.yml` | La instalación sobre la que el sembrado ejecuta SQL, cuando le hace falta |

## Cuentas de prueba

El sembrado genera una contraseña al azar para cada cuenta y las deja en `qa/credenciales.json` y en
una tabla legible, `qa/credenciales.md`. Git ignora los dos (`qa/credenciales*`): son de cada
instalación y no se suben. Sirven también para entrar a mano con cualquier rol.

## Lo que hay que saber al tocarlas

- **Nginx frena los intentos en serie** contra el inicio de sesión y el registro, y las pruebas entran
  decenas de veces desde la misma dirección. `lib.mjs` y `uiLogin` esperan y reintentan cuando les
  llega ese freno; no lo esquivan.
- **Un estudiante solo puede estar en un proyecto por edición**, así que cada spec usa los suyos:
  `estudiante1` y `estudiante2` son de `proyecto.spec`, `estudiante3` de `docente.spec` y
  `estudiante4` de `jurado.spec` y `evaluacion.spec`. Cada spec borra lo que crea.
- **Un solo worker**: todas comparten la edición «Expo QA» y las mismas cuentas.
- Con servidor de correo configurado, el registro exige verificar el correo y el sembrado lo da por
  verificado en la base. `auth.spec` y `primer-ingreso.spec` suponen una instalación **sin** correo,
  que es como corre la integración continua.
- Los selectores van por rol y por texto, como en las pruebas de la app: si cambia una etiqueta de la
  interfaz, se cambia aquí.
