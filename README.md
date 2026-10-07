# Idearium

Repositorio académico y sistema de evaluación de los proyectos de la Cátedra INNPRENDE de la
Universidad Simón Bolívar, con MacondoLab: inscripción de proyectos de INNPRENDE I (investigación) e
INNPRENDE II (prototipado), evaluación por jurados y ranking.

La plataforma se llamó **Expoideas** hasta septiembre de 2026; el repositorio, los paquetes y la ruta
de despliegue conservan ese nombre. Las muestras también tienen nombre propio: **Despegue** la de
INNPRENDE I (antes Expoideas 1) y **Aterrizaje** la de INNPRENDE II (antes Expoideas 2).

Construido sobre la arquitectura del proyecto semilla **Dattapro** (sistema de convocatorias de investigación cedido por TI), del que se reutiliza
la infraestructura técnica — auth JWT, separación en capas, catálogos maestros, layout — pero no el
dominio.

| | |
|---|---|
| `expoideas-api` | Spring Boot 4 · Java 25 · MySQL 8.4 · Spring Security + JWT · Flyway · Swagger |
| `expoideas-app` | React 19 · Vite 7 · Tailwind 4 · react-router · TanStack Query · Radix UI · react-hook-form + zod |

**Si solo vienes a desplegarla**, ve directo al
[camino A](#camino-a--desplegarla-con-docker): hacen falta Git y Docker, nada más.

## Qué existe hoy

**Cuentas y catálogos**

- Autenticación: registro con solo el correo institucional y una contraseña, inicio de sesión y
  primer ingreso, donde cada cuenta resuelve sus pasos pendientes (verificar el correo, cambiar la
  contraseña temporal, aceptar el tratamiento de datos, completar el perfil y subir su foto, que es
  obligatoria para todos los roles).
- Enlaces por correo: quien se registra verifica que el correo es suyo abriendo un enlace, y quien
  olvidó la contraseña pone una nueva con otro. Los dos dependen del servidor de correo y de
  `APP_URL`; sin ellos no se pide verificación y la contraseña la restablece la gestión.
- Perfil propio: foto (se cambia por otra, no se quita), datos personales, seguridad (cambio de
  contraseña) y consentimiento de datos.
  Cambiar la contraseña cierra las demás sesiones abiertas de esa cuenta.
- Gestión de usuarios: ADMIN y MacondoLab crean, editan, restablecen contraseña, suspenden y eliminan
  cuentas, y cargan el [listado de la cátedra](docs/listado.md) para que cada persona se registre
  con su rol; el de profesor se recibe al verificar el correo o, sin servidor de correo, cuando la
  gestión lo confirma. Una cuenta
  suspendida no entra, pero conserva sus proyectos, entregas y evaluaciones.
- Rastro de auditoría: queda registrado quién cambió un rol, restableció una contraseña, suspendió o
  eliminó una cuenta, publicó u ocultó notas, corrigió una evaluación (con la nota que tenía y la
  nueva), quitó a un jurado, retiró a un integrante de un equipo o eliminó un proyecto o un
  entregable. Por ahora se consulta por la API (`GET /api/v1/admin/audit`, solo ADMIN); todavía no
  tiene pantalla.
- Estado de la instalación: `/expoideas/salud` dice si la plataforma está arriba, para el monitoreo
  de TI, y el administrador ve la versión, el disco de los archivos y el correo en
  `GET /api/v1/admin/status`.
- Catálogos: campus, facultades, programas académicos, sectores, keywords y tipos de prototipo.
- Archivos: subida y descarga, hasta 5 MB, validados por contenido (JPG, PNG, WEBP y PDF; las
  plantillas también en DOCX y PPTX).
- Avisos por correo: invitaciones a un equipo, cuentas creadas por la gestión, sustentaciones y
  recordatorios a los jurados. Sin servidor SMTP la plataforma funciona igual y no envía nada. Un
  envío que falla se reintenta, y el que no sale queda en el rastro de auditoría.

**Dominio de la Expo**

- Ediciones: cada vuelta de la Expo con sus plazos (apertura y cierre de inscripciones, cierre de
  entregas) y la configuración de cada cátedra (mínimo y máximo de integrantes). Las ediciones no se
  solapan y la plataforma abre y cierra sola según las fechas.
- Inscripción de proyectos: el estudiante que inscribe queda como líder, elige cátedra, sector y
  profesor, e invita a sus compañeros buscándolos por el nombre o con su correo; nadie entra a un
  equipo sin aceptar la invitación, y salir o cambiarse de equipo pasa por el líder o la gestión.
  Cada estudiante está en un solo proyecto por cátedra en cada edición. Al inscribir, el líder dice
  el semestre que cursa y su celular de contacto; cada compañero dice su semestre al aceptar.
- Prerrequisito: nadie cursa las dos cátedras a la vez, y para inscribirse en INNPRENDE II hay que
  haber aprobado INNPRENDE I. El profesor registra el resultado del proyecto al cerrar las entregas.
- Entregables: MacondoLab define qué pide cada cátedra (nombre, formatos aceptados o enlace, si es
  obligatorio, cuántos archivos admite, plantilla para descargar y cierre propio) y el equipo sube los
  suyos hasta el cierre. En INNPRENDE II un entregable puede pedirse solo a un tipo de prototipo.
- Sustentaciones: la gestión asigna fecha y hora a cada proyecto, y el equipo y su profesor reciben
  el aviso por correo.
- Jurados: la gestión asigna a cada proyecto profesores, jurados externos o cuentas de gestión, y
  cada jurado ve los suyos en «Evaluar», con sus entregables.
- Evaluación: las dos [rúbricas](docs/rubricas.md) están cargadas como datos y cada jurado asignado
  califica en un tablero, criterio por criterio. La nota del proyecto es el promedio de sus jurados;
  la ven el profesor del grupo y la gestión en la ficha, en el listado y en el CSV.
- Publicación de notas: la gestión publica las de cada cátedra en «Ediciones», y desde ese momento
  cada equipo ve su nota final y las observaciones de los jurados, sin nombres.
- Directorio y reportes: la gestión ve todos los proyectos y filtra por edición, cátedra, sector o
  profesor, y la ordena por lo más reciente o por título; cada profesor ve solo los que lo nombraron.
  Ambos exportan la lista en CSV, en el orden elegido y con el progreso de entregables de cada
  proyecto.
- Plazos a la vista: la ficha del proyecto y «Mis proyectos» dicen cuántos días quedan para que
  cierren las entregas, y cada entregable con cierre propio, cuántos le quedan a él.

## Qué falta

**De la plataforma**: el rastro de auditoría y lo que pide la Ley 1581 (descargar los propios datos
y anonimizar una cuenta) todavía no tienen pantalla: se usan por la API. Quedan para después de
abrir las mejoras del plan que no eran urgentes: archivar una edición entera, el antivirus de los
archivos, avisar cuando dos personas editan lo mismo y el inicio de sesión institucional.

## Requisitos

| Herramienta | Versión | Para qué | Comprobar |
|---|---|---|---|
| Git | Cualquiera reciente | Clonar el repositorio | `git --version` |
| Docker | Engine 24+ con el plugin Compose (en Windows y macOS viene con Docker Desktop) | MySQL, pruebas de integración y despliegue | `docker compose version` |
| JDK | **25** (Eclipse Temurin) | Compilar y ejecutar la API | `java -version` |
| Node.js | **24** (fijado en `.nvmrc`) | Compilar y ejecutar la app | `node -v` |

**Para desplegar bastan Git y Docker**: Java y Node se usan dentro de las imágenes y no hay que
instalarlos. El equipo necesita además unos 2 GB de RAM libres, del orden de 5 GB de disco para las
imágenes y su construcción (aparte de lo que crezcan la base de datos y los archivos subidos) y
salida a Internet la primera vez, para descargar las imágenes base y las dependencias.

Para programar hacen falta los cuatro. Maven no hay que instalarlo: el repositorio trae el wrapper
(`mvnw` y `mvnw.cmd`). Con `nvm` (o `nvm-windows`), `nvm install` y `nvm use` dentro del repositorio
toman la versión del `.nvmrc`. El mínimo real de Node son 22.12+, pero el CI compila con la 24: si
usas otra versión y algo falla solo en tu equipo, ese es el primer sospechoso.

## Puesta en marcha en otro equipo

Dos caminos, según para qué quieras el proyecto:

- **[Camino A](#camino-a--desplegarla-con-docker)**: desplegarla entera con Docker. Es el mismo
  recorrido en un portátil, para verla funcionando, y en un servidor.
- **[Camino B](#camino-b--para-programar)**: cada parte por separado, con recarga en caliente.

> **Consola.** Todos los comandos del camino A sirven tal cual en Bash, en PowerShell y en la
> terminal de macOS, salvo los que dicen otra cosa. Los del camino B están escritos para una consola
> tipo Unix (Git Bash o WSL): desde PowerShell, cambia `./mvnw` por `.\mvnw.cmd` y `cp` por `Copy-Item`.

### Camino A — desplegarla con Docker

Levanta la plataforma entera —base de datos, API y app— en tres contenedores. Solo la app publica
un puerto; MySQL y la API se quedan en la red interna de Docker, que es justo lo que se quiere en un
servidor.

```
Navegador ──▶ app: Nginx (puerto HTTP_PORT)
                ├─ /expoideas/       la app React
                └─ /expoideas/api/   ──▶ api: Spring Boot ──▶ mysql: MySQL 8.4
                                                          └─▶ volumen de archivos subidos
```

**1. Descargar el proyecto**

```bash
git clone https://github.com/KENSHIN11083012/idearium.git
```

```bash
cd idearium
```

Todos los comandos que siguen se ejecutan desde esa carpeta, que es donde está `docker-compose.yml`.

**2. Crear el archivo de variables**

```bash
cp .env.example .env
```

Abre `.env` con un editor y rellena los tres valores obligatorios. Para generar cada uno sirve este
comando, que solo necesita Docker; ejecútalo tres veces y pega un resultado distinto en cada
variable:

```bash
docker run --rm alpine sh -c "head -c 32 /dev/urandom | base64"
```

| Variable | Obligatoria | Qué poner |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | Sí | Contraseña del administrador de MySQL |
| `DB_PASSWORD` | Sí | Contraseña del usuario `expoideas`, con el que se conecta la API. Distinta de la anterior |
| `JWT_SECRET` | Sí | Clave con la que se firman las sesiones. Tiene que ser Base64 de 32 bytes o más: una frase escrita a mano no sirve |
| `HTTP_PORT` | No | Puerto del equipo donde se publica la plataforma. Por defecto, 8080 |
| `APP_URL` | Solo con correo | URL pública de la app (`https://<dominio>/expoideas`), para los enlaces de los correos. Con ella y con `MAIL_HOST`, el registro pide verificar el correo y funciona «Olvidé mi contraseña»; vacía, ninguna de las dos cosas |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | No | Servidor SMTP para los avisos. Sin `MAIL_HOST` la plataforma funciona, pero no envía correos |
| `MAIL_AUTH`, `MAIL_STARTTLS` | No | `false` si el SMTP no pide autenticación o STARTTLS |
| `JWT_EXPIRATION` | No | Duración de la sesión (`4h`, `30m`…). Por defecto, 4 horas |
| `LOGIN_MAX_FAILED_ATTEMPTS`, `LOGIN_LOCK_DURATION` | No | Contraseñas equivocadas seguidas que bloquean una cuenta (5) y cuánto dura el bloqueo (`5m`) |

Si escribes una contraseña a mano, que no lleve `$`, espacios ni comillas: Docker Compose los
interpreta. El `.env` está ignorado por git y nunca se sube.

> **Las contraseñas de MySQL se fijan la primera vez que arranca.** Cambiarlas después en el `.env`
> no las cambia en la base de datos y deja a la API sin poder conectarse. Decídelas antes del paso 3.

**3. Construir y arrancar**

```bash
docker compose up -d --build
```

La primera vez tarda varios minutos: descarga las imágenes base y compila la API con Maven y la app
con Vite dentro de Docker. El comando termina cuando los tres servicios están arriba. Si falta una
variable obligatoria, se niega a arrancar y dice cuál.

**4. Comprobar que arrancó**

```bash
docker compose ps
```

`mysql` y `api` deben decir `healthy`, y `app`, `Up`. Después, en el navegador:

- `http://localhost:8080/expoideas/` muestra la portada de Idearium.
- `http://localhost:8080/expoideas/api/v1/campuses` responde con los dos campus, Barranquilla y
  Cúcuta. Si esto funciona, la app, la API y la base de datos se están hablando.

Cambia `8080` por tu `HTTP_PORT` si lo tocaste y, desde otro equipo, `localhost` por el nombre o la
IP del servidor.

Recién instalada, la plataforma no tiene ninguna cuenta de gestión: sigue en
[Primer administrador](#primer-administrador).

### Primer administrador

Toda cuenta nace como estudiante, así que el primer administrador no sale del registro: sale del
`.env`. Antes de arrancar por primera vez, se ponen ahí su correo y una contraseña temporal (ocho
caracteres o más, con al menos un número y un símbolo):

```
ADMIN_EMAIL=persona@unisimon.edu.co
ADMIN_PASSWORD=<una contraseña temporal, solo para el primer ingreso>
```

Al arrancar, la API crea esa cuenta como administrador. La persona entra con esa contraseña y la
plataforma le pide cambiarla, autorizar el tratamiento de sus datos, poner su nombre y subir su
foto. Desde ahí crea las demás cuentas y reparte roles en **Usuarios**, y carga las facultades en
**Catálogos**: una base recién creada trae los dos campus (Barranquilla y Cúcuta) pero ninguna
facultad, y sin al menos una los estudiantes y profesores no pueden terminar su primer ingreso.

Las dos variables solo cuentan mientras no haya ningún administrador: después se ignoran y se pueden
borrar del `.env`. Si la plataforma ya estaba arrancada sin ellas, basta añadirlas y volver a
ejecutar `docker compose up -d`.

Con el [camino B](#camino-b--para-programar) son las mismas dos variables, puestas en
`expoideas-api/config/application-local.properties` como `expoideas.admin.email` y
`expoideas.admin.password`.

### Comprobar que quedó bien

1. Abre la app: se ve la portada con los logos de la Universidad Simón Bolívar y MacondoLab.
2. Entra con la cuenta de administrador: en el menú aparecen **Usuarios**, **Catálogos**,
   **Ediciones** y **Sustentaciones**.
3. En **Catálogos**, crea una facultad de prueba y bórrala. Si eso funciona, la app, la API y la base
   de datos se están hablando también para escribir.
4. En **Ediciones**, crea una edición con sus fechas y configura sus dos cátedras. Sin una edición
   con inscripciones abiertas, los estudiantes no pueden inscribir proyectos.

### En un servidor

Los pasos son los mismos del camino A. Lo que cambia en un servidor de verdad es lo de alrededor; el
detalle para TI está en [docs/despliegue.md](docs/despliegue.md), y lo imprescindible es esto:

- **Arranque con el equipo.** Los contenedores vuelven solos tras un reinicio siempre que Docker
  arranque con el sistema (`sudo systemctl enable docker` en Linux).
- **Red.** El cortafuegos debe dejar entrar al `HTTP_PORT`, y a nada más: MySQL y la API no publican
  puerto y no deben quedar expuestas.
- **HTTPS.** Los contenedores hablan HTTP. El certificado lo pone un proxy delante (el de TI o un
  Nginx en el mismo servidor), que debe reenviar `X-Forwarded-Proto` y `X-Forwarded-Host` y admitir
  cuerpos de al menos 10 MB, o cortará las subidas de archivos antes de que lleguen. Como referencia,
  en Nginx (con tu `HTTP_PORT` en lugar de 8080):

  ```nginx
  location /expoideas/ {
      proxy_pass http://127.0.0.1:8080;
      proxy_set_header Host $host;
      proxy_set_header X-Forwarded-Proto https;
      proxy_set_header X-Forwarded-Host $host;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      client_max_body_size 10m;
  }
  ```

- **Correo.** Rellena las variables `MAIL_*` con el SMTP de TI y pon en `APP_URL` la URL pública
  (`https://<dominio>/expoideas`), que es la que llevan los enlaces de los correos. Después,
  `docker compose up -d` aplica el cambio. Desde ese momento quien se registra tiene que abrir el
  enlace que le llega para usar su cuenta, así que conviene probarlo con una cuenta propia antes de
  anunciarlo: si los correos no llegan, nadie nuevo puede entrar. Las cuentas que ya existían no
  tienen que verificar nada.
- **Sin Docker.** Si TI no lo permite, [docs/despliegue.md](docs/despliegue.md) explica cómo
  instalar MySQL, la API y la app por separado, con la tabla completa de variables de entorno y la
  lista de verificación de seguridad.

### Actualizar, apagar y copias de seguridad

**Actualizar a una versión nueva**, después de hacer una copia de seguridad:

```bash
git pull
```

```bash
docker compose up -d --build
```

El `--build` es lo que reconstruye las imágenes: sin él sigue corriendo la versión anterior. La API
aplica sola las migraciones pendientes al arrancar; si una falla, la API no arranca y lo dice en
`docker compose logs api`.

En un servidor de verdad conviene no compilar ahí: la integración continua publica las imágenes de
cada versión que pasa todas las pruebas, y actualizar o volver a la versión anterior es cambiar una
etiqueta en el `.env`. Está en [docs/despliegue.md](docs/despliegue.md#versiones-publicadas). La
versión que corre sale en el pie de la app.

**Apagar y volver a encender**, sin perder nada:

```bash
docker compose down
```

```bash
docker compose up -d
```

Los datos viven en dos volúmenes de Docker, `expoideas_mysql-data` (la base de datos) y
`expoideas_files` (los archivos subidos), y sobreviven al `down`. `docker compose down -v` sí los
borra, con todo lo que tengan: es la forma de empezar de cero.

**Copia de seguridad.** La base de datos y los archivos se respaldan **juntos**: la base guarda los
metadatos y la carpeta, el contenido. Con la plataforma encendida, desde la raíz del repositorio
(en Windows, desde Git Bash):

```bash
scripts/copia.sh
```

Deja en `copias/` un solo paquete con las dos cosas. Lleva datos reales: se guarda fuera del
repositorio (la carpeta está en el `.gitignore`) y fuera del servidor.

**Restaurar una copia** reemplaza la base y los archivos por los de la copia, y pide confirmación
porque lo que haya en ese momento se pierde:

```bash
scripts/restaurar.sh copias/idearium-AAAA-MM-DD-HHMMSS.tar
```

Cómo programar la copia y cómo probar una restauración sin tocar la instalación real está en
[docs/despliegue.md](docs/despliegue.md#copias-de-seguridad).

### Camino B — para programar

Aquí cada parte corre por separado, con recarga en caliente.

**1. Levantar MySQL**

Lo más rápido es en Docker, sin instalar nada:

```bash
docker run -d --name expoideas-mysql \
  -e MYSQL_ROOT_PASSWORD=<elige-una> -e MYSQL_DATABASE=expoideas \
  -p 3306:3306 mysql:8.4 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

Las siguientes veces basta con `docker start expoideas-mysql`. Si prefieres MySQL instalado en el
equipo, crea la base a mano:

```sql
CREATE DATABASE expoideas CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

En los dos casos el esquema lo crea la API sola al arrancar: Flyway aplica las migraciones de
`expoideas-api/src/main/resources/db/migration/`. No hay scripts que ejecutar a mano.

**2. Configurar la API**

```bash
cd expoideas-api
cp config/application-local.properties.example config/application-local.properties
```

Abre ese archivo y pon la contraseña de MySQL y un `expoideas.jwt.secret` nuevo (el mismo comando
generador del camino A). Está ignorado por git: nunca se sube.

**3. Arrancar la API**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Está lista cuando el log dice `Started ExpoideasApplication`. Compruébalo:

```bash
curl http://localhost:8080/actuator/health
```

Debe responder `{"status":"UP"}`.

- API: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html` (apagado en el perfil `prod`)

**Correos en desarrollo (opcional).** La API envía avisos (invitaciones, cuentas creadas,
sustentaciones). Para verlos sin un SMTP real, levanta Mailpit y deja las líneas `spring.mail.*`
del `application-local.properties`:

```bash
docker compose --profile dev up -d mailpit
```

Los correos quedan en **http://localhost:8025**. Sin Mailpit ni SMTP, la API funciona igual y
solo deja en el log qué correo habría enviado.

**Si el puerto 8080 está ocupado** —pasa a menudo, por ejemplo con el camino A levantado—,
descomenta `server.port` en `config/application-local.properties` (por ejemplo, `server.port=8081`)
y apunta el frontend a ese mismo puerto en el paso siguiente.

**4. Arrancar la app**

En otra consola, desde la raíz del repositorio:

```bash
cd expoideas-app
cp .env.example .env
npm ci
npm run dev
```

App en **http://localhost:5173/expoideas/**

`npm ci` —no `npm install`— instala exactamente lo que dice `package-lock.json`, que es lo mismo que
usa el CI. Si cambiaste el puerto de la API, edita `VITE_API_URL` en `expoideas-app/.env` y
**reinicia `npm run dev`**: Vite lee ese archivo solo al arrancar.

El subdirectorio `/expoideas/` sale de `VITE_BASE_PATH`. `App.jsx` lo reutiliza como `basename` del
router, así que no hay que tocarlo en dos sitios; para servir en la raíz durante el desarrollo, mira
el comentario del `.env.example`.

Falta la cuenta de gestión: sigue en [Primer administrador](#primer-administrador).

### Pruebas y formato

Lo mismo que corre el CI en cada push, para pasarlo antes de subir nada:

```bash
cd expoideas-api
./mvnw test              # unitarias, no necesitan base de datos
./mvnw verify            # unitarias + integración + formato + build
./mvnw spotless:apply    # aplica el formato de Java (palantir-java-format)
```

```bash
cd expoideas-app
npm test                 # Vitest + Testing Library, sin navegador ni API
npm run lint
npm run format           # aplica Prettier
npm run format:check     # solo verifica, como en CI
npm run build
```

Las pruebas de integración de la API (`*IT`) levantan la aplicación completa contra un MySQL 8.4 real
que Testcontainers arranca en Docker. **Necesitan Docker encendido**; se descargan la imagen la
primera vez y crean y destruyen su propia base: no tocan la tuya.

**De punta a punta.** La carpeta `qa/` recorre la plataforma entera levantada con Docker, primero
por la API y después desde un navegador (Playwright): registro y primer ingreso, permisos por rol,
inscripción y equipo, entregables, jurados, calificación con la rúbrica y publicación de notas. Crea
cuentas y datos de prueba, así que se corre contra una instalación aparte, nunca contra una con
datos reales. Cómo hacerlo está en [qa/README.md](qa/README.md).

Cada push a `main` y cada pull request pasan por GitHub Actions (`.github/workflows/ci.yml`):
pruebas y formato de la API, lint, formato, pruebas y build de la app, validación de
`docker-compose.yml`, construcción de las dos imágenes Docker y, con la plataforma levantada, las
pruebas de punta a punta. Lo que llega a `main` y pasa todo eso se publica como imágenes con la
etiqueta del commit. Por el camino revisa las dependencias de la app y escanea las dos imágenes en
busca de vulnerabilidades conocidas, y deja en el resumen de la corrida la cobertura de las pruebas
(`npm run test:coverage` en la app; `./mvnw verify` deja la de la API en
`target/site/jacoco/index.html`). Qué hacer cuando el escaneo falla está en
[docs/despliegue.md](docs/despliegue.md#seguridad-de-lo-que-se-publica).

### Si algo falla

Con Docker (camino A), lo primero es siempre mirar qué dice el servicio: `docker compose logs api`
(o `mysql`, o `app`).

| Síntoma | Qué pasa | Qué hacer |
|---|---|---|
| `docker compose up` no arranca y dice `Define DB_PASSWORD en .env` (o `MYSQL_ROOT_PASSWORD`, o `JWT_SECRET`) | Falta el `.env` o esa variable está vacía | Crear el `.env` desde `.env.example` y rellenar las tres obligatorias |
| `Bind for 0.0.0.0:8080 failed: port is already allocated` | Otro programa ocupa ese puerto | Poner otro `HTTP_PORT` en el `.env` y repetir `docker compose up -d` |
| `docker compose up` termina con `dependency failed to start: container expoideas-api-1 is unhealthy` | La API no logra arrancar | Mirar por qué en `docker compose logs api`; los dos casos de abajo son los habituales |
| El log de `api` dice `Access denied for user 'expoideas'` | Se cambió `DB_PASSWORD` después del primer arranque | Volver a poner la contraseña original. Si la instalación es nueva y no hay datos que perder, `docker compose down -v` y empezar otra vez |
| El log de `api` dice `Illegal base64 character` o `WeakKeyException` | `JWT_SECRET` no es Base64 de 32 bytes o más | Generarlo con el comando del paso 2 |
| Después de actualizar se sigue viendo la versión anterior | Se levantó sin reconstruir | `docker compose up -d --build` |
| La portada carga en el servidor pero no desde otro equipo | El cortafuegos no deja entrar al puerto | Abrir el `HTTP_PORT` en el cortafuegos del servidor |
| Detrás del proxy de TI, subir un archivo falla con 413 | El proxy corta los cuerpos grandes antes de la app | Subir su límite a 10 MB (`client_max_body_size 10m` en Nginx) |
| Los enlaces de los correos no abren | `APP_URL` no es la dirección con la que se entra a la plataforma | Poner la URL pública real y `docker compose up -d` |
| Las cuentas nuevas no reciben el enlace de verificación | El SMTP rechaza o demora los envíos | Mirar `docker compose logs api`. Mientras se arregla, vaciar `APP_URL` y `docker compose up -d` deja de pedir la verificación a quien se registre |
| La API no arranca: `Port 8080 was already in use` | Otro programa ocupa el 8080 (camino B) | Poner `server.port=8081` en `config/application-local.properties`, y `VITE_API_URL=http://localhost:8081/api/v1` en `expoideas-app/.env` |
| `Validate failed: Migration checksum mismatch` | Se editó una migración ya aplicada | Nunca se edita una migración aplicada: se añade otra. Para empezar de cero en local, borrar la base y dejar que Flyway la recree |
| Las pruebas `*IT` fallan con `Could not find a valid Docker environment` | Docker Desktop está apagado | Encenderlo y repetir. `./mvnw test` no lo necesita |
| La app carga pero todo dice «Error de red» | La API no está arriba, o `VITE_API_URL` apunta a otro puerto | Comprobar `curl .../actuator/health` y reiniciar `npm run dev` después de tocar el `.env` |
| La sesión se cierra sola al entrar | El token venció o cambió `JWT_SECRET` | Volver a entrar. Cambiar el secreto invalida todas las sesiones, a propósito |
| Acentos rotos al consultar MySQL por consola | Falta el juego de caracteres | Añadir `--default-character-set=utf8mb4` al comando |
| En PowerShell, `./mvnw` no hace nada | Ese es el script de Unix | Usar `.\mvnw.cmd` |

## Estructura

Organización por módulo en los dos proyectos: cada uno agrupa lo suyo (entidad, servicio,
controlador... o página, `api.js`, `queries.js`) en vez de separar por capas técnicas.

```
expoideas-api/src/main/java/co/edu/unisimon/expoideas/
  auth/         Login y registro (rutas públicas)
  security/     JWT, filtros, SecurityConfig
  users/        Cuenta propia, gestión de usuarios, roles, listado de la cátedra
  catalogs/     Campus, facultades, programas académicos, sectores, keywords, tipos de prototipo
  editions/     Ediciones de la Expo, sus plazos y la configuración de cada cátedra
  projects/     Proyectos inscritos, equipo, líder e invitaciones
  deliverables/ Qué pide cada cátedra y qué subió cada equipo
  presentations/ Agenda de sustentaciones
  jury/         Jurados asignados a cada proyecto
  evaluations/  Rúbricas, evaluación de cada jurado y nota del proyecto
  notifications/ Avisos por correo, con reintentos
  reports/      Directorio de proyectos y exportación a CSV
  files/        Almacenamiento y metadatos de archivos subidos
  audit/        Rastro de quién hizo qué, sobre qué y cuándo
  common/       Manejo de errores y configuración centralizada (ExpoideasProperties)

expoideas-app/src/
  features/   Un módulo por área: auth, users, profile, catalogs, editions, projects,
              deliverables, presentations, jury, evaluations, home, errors
  components/ ui/ (primitivos), layout/, forms/ (compartidos entre features)
  lib/        apiClient, rutas (routes.js), roles, cátedras (tracks.js), validaciones, sesión

qa/           Pruebas de punta a punta: sembrado, recorrido de la API y Playwright
scripts/      Copia de seguridad y restauración
```

## Roles y permisos

| Rol | Quién | Puede |
|---|---|---|
| `ADMIN` | Parte técnica de la plataforma | Todo lo de MacondoLab, y además gestionar cuentas de gestión (ADMIN, MACONDOLAB) y la estructura académica (campus, facultades, programas) |
| `MACONDOLAB` | Coordinación de INNPRENDE I y II | Gestionar cuentas de estudiantes, profesores y jurados; administrar sectores, keywords y tipos de prototipo; abrir ediciones y configurar sus entregables; programar sustentaciones, asignar jurados y publicar las notas; ver y exportar el directorio completo |
| `TEACHER` | Profesor de la universidad | Ver y exportar los proyectos que lo nombraron, registrar su resultado y ver sus notas. Puede ser jurado de proyectos de otros profesores. Declara campus y facultad |
| `JUDGE` | Jurado, puede ser externo a la universidad | Calificar con la rúbrica los proyectos que la gestión le asigna, en «Evaluar». Su cuenta la crea la gestión. Sin adscripción académica obligatoria |
| `STUDENT` | Rol con el que nace toda cuenta registrada | Inscribir un proyecto por cátedra, invitar a su equipo, subir los entregables y ver su nota cuando se publica. Declara campus y facultad |

Ser jurado es una asignación por proyecto, no solo un rol: la gestión también puede asignar como
jurado a un profesor o a una cuenta de gestión.

## Convenciones

- **Código en inglés** (clases, métodos, variables, JSON, rutas de la API, columnas de la base de
  datos); **comentarios, documentación, mensajes al usuario y URLs del navegador, en español**
  (`/iniciar-sesion`, `/admin/usuarios`...).
- Commits en [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat`, `fix`, `refactor`, `test`, `chore`, `style`, `docs`...); el historial de commits es la
  referencia real del orden en que avanzó cada fase.
- Formato automático y obligatorio en CI: Prettier en el frontend, Spotless con
  palantir-java-format en el backend (ver comandos arriba).
- Desarrollo por fases: los cambios grandes se dividen en fases pequeñas, cada una probada y revisada
  antes de entrar a `main` como un commit.

## Notas

- Los errores de la API siguen el estándar Problem Details (RFC 9457, `application/problem+json`): el
  mensaje para el usuario va en `detail` y los errores de validación añaden `fields` con el mensaje
  de cada campo. Sin sesión válida la API responde 401 y el frontend cierra la sesión.
- En producción se usa el perfil `prod` (`SPRING_PROFILES_ACTIVE=prod`, ya incluido en la imagen
  Docker): apaga Swagger, oculta detalles de error y respeta las cabeceras del proxy.
- Los archivos subidos (fotos, entregables) se guardan en disco, en `FILES_DIR` (por defecto
  `uploads/` junto a la API, ignorada por git); la BD solo guarda sus metadatos. Esa carpeta debe
  quedar fuera de lo que publica el servidor web y entrar en las copias de seguridad junto con la BD.
  Se aceptan JPG, PNG, WEBP y PDF de hasta 5 MB, validados por su contenido.
- Nunca commitees dumps de base de datos ni logs de ejecución: el `.gitignore` de la raíz los cubre.
