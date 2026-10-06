# Despliegue de Idearium (antes Expoideas)

Guía para el equipo de TI de la Universidad Simón Bolívar. Describe qué hay que instalar, cómo
configurarlo y cómo operarlo (actualizaciones, copias de seguridad y monitoreo).

## Arquitectura

```
Navegador ──HTTPS──▶ Proxy de TI (opcional) ──▶ app: Nginx :8080
                                                   ├─ /expoideas/        app React (estática)
                                                   └─ /expoideas/api/    ──▶ api: Spring Boot :8080
                                                                              ├─ MySQL 8.4
                                                                              └─ carpeta de archivos
```

| Componente | Tecnología | Estado que guarda |
|---|---|---|
| `app` | React 19 compilada, servida por Nginx (usuario sin privilegios) | Ninguno |
| `api` | Spring Boot 4 · Java 25 (JRE, usuario sin privilegios) | Ninguno en memoria |
| `mysql` | MySQL 8.4 · utf8mb4 | Datos de la plataforma |
| Archivos | Carpeta en disco (`FILES_DIR`) | Fotos y entregables (≤ 5 MB c/u) |

La app y la API comparten origen: Nginx reenvía `/expoideas/api/` a la API. Por eso no hace
falta abrir CORS ni publicar la API. **La API y MySQL no deben quedar expuestas a Internet.**

El esquema de la base de datos lo crea y actualiza la propia API al arrancar (migraciones
Flyway en `expoideas-api/src/main/resources/db/migration`). No hay que ejecutar scripts SQL.

## Opción A: Docker Compose (recomendada)

Requisitos: Docker Engine 24+ con el plugin Compose, 2 GB de RAM libres y espacio en disco para
la base de datos y los archivos.

```bash
git clone https://github.com/KENSHIN11083012/idearium.git
cd idearium
cp .env.example .env
# Editar .env: contraseñas de MySQL, JWT_SECRET (openssl rand -base64 32) y HTTP_PORT
docker compose up -d --build
docker compose ps        # los tres servicios deben quedar "healthy" o "running"
```

La plataforma queda en `http://servidor:HTTP_PORT/expoideas/`. Si falta una variable
obligatoria, Compose se niega a arrancar e indica cuál.

Así se compila en el propio servidor, que sirve para probar. Para producción es mejor no compilar
ahí y usar las imágenes que publica la integración continua: ver
[Versiones publicadas](#versiones-publicadas).

Datos persistentes (volúmenes de Docker):

| Volumen | Contenido |
|---|---|
| `expoideas_mysql-data` | Base de datos |
| `expoideas_files` | Archivos subidos |

Cada servicio tiene un tope de memoria, para que uno que se desboque no tumbe a los otros ni al
servidor. Los valores por defecto suman cerca de 2 GB y se cambian en el `.env`:

| Variable | Por defecto | Servicio |
|---|---|---|
| `MYSQL_MEMORY` | `1g` | MySQL (en reposo usa cerca de 480 MB) |
| `API_MEMORY` | `768m` | API (el montón de Java se queda con el 60 %) |
| `APP_MEMORY` | `128m` | Nginx con la app (usa cerca de 15 MB) |

Si un servicio se reinicia solo y `docker inspect` dice `OOMKilled`, le faltó memoria: se sube su
variable y se ejecuta `docker compose up -d`.

### HTTPS

Nginx de la app escucha HTTP en el puerto 8080 del contenedor. Lo habitual es poner delante el
proxy o balanceador de TI con el certificado de la universidad, que debe reenviar:

- `X-Forwarded-Proto: https`
- `X-Forwarded-Host: <dominio público>`
- `X-Forwarded-For`, con la dirección del cliente al final

Con las dos primeras la API responde como si la petición hubiera llegado por HTTPS (HSTS, URLs y
comprobación de origen correctas).

La tercera la usa el Nginx de la app para frenar los intentos en serie contra el inicio de sesión y
el registro: por cada dirección admite cinco peticiones por segundo sostenidas y ráfagas de hasta 60, y
al resto responde 429. Toma la **última** dirección de la cabecera, que es la que vio el proxy. Si
el proxy no la envía, todas las personas llegan con la dirección del proxy y comparten ese mismo
cupo: en una hora pico de inscripciones se quedarían cortas.

## Opción B: sin Docker

1. **MySQL 8.4**: crear la base y un usuario con todos los privilegios sobre ella.
   ```sql
   CREATE DATABASE expoideas CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE USER 'expoideas'@'%' IDENTIFIED BY '<contraseña>';
   GRANT ALL PRIVILEGES ON expoideas.* TO 'expoideas'@'%';
   ```
2. **API** (Java 25): compilar con `./mvnw -B package` y ejecutar
   `java -jar target/expoideas-api-0.1.0-SNAPSHOT.jar` como servicio (p. ej. systemd), con las
   variables de la tabla de abajo y `SPRING_PROFILES_ACTIVE=prod`.
3. **App** (Node 24 solo para compilar): en `expoideas-app`,
   `npm ci && VITE_API_URL=/expoideas/api/v1 npm run build`, y copiar `dist/` a la carpeta
   `expoideas/` del servidor web.
4. **Servidor web**: tomar `expoideas-app/nginx.conf` como referencia (rutas, SPA, caché,
   cabeceras de seguridad y proxy a la API). Asume que Nginx corre en el contenedor de Docker
   (escucha en 8080, usuario sin privilegios); fuera de Docker hay que adaptar el puerto y,
   si el servidor web no es Nginx, reproducir el mismo comportamiento en el que se use.

## Variables de entorno de la API

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DB_URL` | Sí | `jdbc:mysql://host:3306/expoideas` |
| `DB_USERNAME` / `DB_PASSWORD` | Sí | Usuario de MySQL de la aplicación |
| `JWT_SECRET` | Sí | Clave de firma de sesiones, Base64 de 32 bytes o más (`openssl rand -base64 32`). Cambiarla cierra todas las sesiones |
| `SPRING_PROFILES_ACTIVE` | Sí en producción | `prod`: apaga Swagger, oculta detalles de error y respeta el proxy |
| `FILES_DIR` | No | Carpeta de archivos (por defecto `uploads`; en Docker: `/data/files`) |
| `PORT` | No | Puerto de la API (por defecto 8080) |
| `JWT_EXPIRATION` | No | Duración de la sesión: `4h`, `30m`… (por defecto 4 h; un número sin unidad son milisegundos) |
| `LOGIN_MAX_FAILED_ATTEMPTS` / `LOGIN_LOCK_DURATION` | No | Cuántas contraseñas equivocadas seguidas bloquean una cuenta (por defecto 5) y por cuánto tiempo (por defecto `5m`). Mientras dura el bloqueo no entra ni la contraseña correcta; restablecer la contraseña desde **Usuarios** lo quita |
| `ALLOWED_ORIGINS` | No | Orígenes externos permitidos por CORS, separados por comas. Por defecto vacío: solo el mismo origen, como detrás de Nginx |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` | No | Servidor SMTP de TI para los avisos (invitaciones, cuentas creadas, sustentaciones). Sin host no se envía nada y la API lo dice en el log. En `docker-compose.yml` salen de `MAIL_HOST` y `MAIL_PORT` |
| `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | No | Credenciales del SMTP (`MAIL_USERNAME` y `MAIL_PASSWORD` en el `.env`). Con `MAIL_AUTH=false` y `MAIL_STARTTLS=false` se desactivan la autenticación y STARTTLS |
| `MAIL_FROM` | No | Remitente de los avisos (por defecto `no-reply@unisimon.edu.co`; debe ser una dirección que el SMTP acepte) |
| `DISK_MIN_FREE` | No | Espacio libre mínimo en el disco de los archivos (por defecto `1GB`). Por debajo, la salud de la plataforma pasa a `DOWN` para que el monitoreo avise antes de que se llene |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | No | El primer administrador de una instalación nueva; ver «Primer administrador» abajo |
| `MAIL_ATTEMPTS` / `MAIL_RETRY_DELAY` | No | Cuántas veces se intenta un envío (por defecto 3) y cuánto se espera antes del segundo intento (por defecto `5s`; antes del tercero, el doble). Ver «Si un correo no sale» abajo |
| `APP_URL` | No | URL pública de la app, para los enlaces de los correos (`https://<dominio>.unisimon.edu.co/expoideas`). Junto con el SMTP activa la verificación del correo al registrarse y la recuperación de contraseña; ver «Verificación del correo» abajo |
| `JAVA_TOOL_OPTIONS` | No | Opciones de la JVM. La imagen Docker ya trae `-XX:MaxRAMPercentage=60`; fuera de Docker no hay valor por defecto |

## Verificación del correo y recuperación de contraseña

Cuando la API tiene servidor de correo (`SPRING_MAIL_HOST`) **y** una `APP_URL` válida:

- Quien se registra recibe un enlace y no puede usar su cuenta hasta abrirlo (vale 48 horas; se
  puede pedir otro desde la pantalla de primer ingreso). Es lo que demuestra que el correo es suyo.
- En el inicio de sesión aparece «¿Olvidaste tu contraseña?», que envía un enlace de una hora para
  poner una nueva.
- Quien figura como profesor en el listado de la cátedra recibe ese rol al verificar su correo.

Sin servidor de correo, o con `APP_URL` vacía o mal escrita, nada de eso se activa: el registro no
pide verificación, el rol del listado lo confirma la gestión y las contraseñas se restablecen desde
**Usuarios**.

Al activar el correo en una instalación que ya tiene cuentas, las existentes no quedan pendientes
de verificar. Antes de anunciarlo conviene registrar una cuenta de prueba y comprobar que el enlace
llega y abre: si el SMTP rechaza los envíos, las cuentas nuevas no podrían entrar.

### Si un correo no sale

Los correos salen después de la operación que los pide, en segundo plano: un SMTP lento o caído no
frena a quien está usando la plataforma.

- Cada envío espera como mucho 10 segundos para conectar y 15 para leer o escribir, y se intenta
  tres veces (`MAIL_ATTEMPTS`), con 5 y 10 segundos de espera entre intentos (`MAIL_RETRY_DELAY`).
  Las credenciales rechazadas no se reintentan: otro intento no las arregla.
- Si ningún intento sale, queda en el log de la API y en el rastro de auditoría
  (`GET /api/v1/admin/audit?action=MAIL_FAILED`, solo administradores): a quién iba, el asunto y el
  motivo, nunca el cuerpo. Es donde mirar cuando alguien dice que no le llegó su contraseña
  temporal: se le restablece desde **Usuarios**.
- Hay cuatro envíos a la vez y hasta 500 en espera. Si el SMTP lleva rato caído y la espera se
  llena, el envío lo hace la propia petición, que tarda más pero no falla.
- Al apagar la API, los envíos en curso tienen 30 segundos para terminar.

## Primer administrador

Las cuentas nuevas nacen como estudiante, así que el primer administrador no puede salir del
registro. Sale del `.env`, al arrancar:

```
ADMIN_EMAIL=persona@unisimon.edu.co
ADMIN_PASSWORD=<una contraseña temporal: 8 caracteres o más, con un número y un símbolo>
```

Con esas dos variables, la API crea esa cuenta como administrador la primera vez que arranca
(`docker compose up -d`). La persona entra con esa contraseña y la plataforma le pide cambiarla,
autorizar el tratamiento de sus datos y poner su nombre. Desde **Usuarios** asigna los demás roles
(MacondoLab, profesores, jurados) y desde **Catálogos** crea las facultades, que una base nueva no
trae: sin al menos una, estudiantes y profesores no pueden terminar su primer ingreso.

- **Solo cuenta mientras no haya ningún administrador.** Con uno ya creado, las dos variables se
  ignoran: no sirven para recuperar el acceso ni para nombrar a otro. Después del primer arranque
  conviene borrarlas del `.env`.
- Si esa persona ya se había registrado, la cuenta que tiene pasa a ser administrador y conserva su
  contraseña.
- Si falta `ADMIN_PASSWORD` o no cumple los requisitos, la cuenta no se crea y la API lo dice en
  `docker compose logs api`.
- Queda en el rastro de auditoría como «Primer administrador».

Si se pierde el acceso de todos los administradores, se restablece la contraseña de uno con la
recuperación por correo o, sin servidor de correo, en la base de datos.

## Operación

### Salud y monitoreo

- **Para el monitoreo de TI:** `GET /expoideas/salud`, sin sesión. Responde 200 con
  `{"status":"UP"}` si la API, la base de datos y el disco de los archivos están bien; 503 con
  `"DOWN"` si la base no responde o al disco le queda menos de `DISK_MIN_FREE` (1 GB por defecto);
  y 502 si la API no contesta. No dice nada más: ni versiones ni rutas.
- **Para quien administra:** `GET /api/v1/admin/status`, con la sesión de un administrador. Dice la
  versión que corre, la hora de la plataforma, cuánto disco queda para los archivos y cómo va el
  correo: si hay servidor configurado, si los enlaces están activos y cuántos correos no salieron en
  las últimas 24 horas. El detalle de cada correo perdido está en el rastro de auditoría
  (`/api/v1/admin/audit?action=MAIL_FAILED`).
- `GET /actuator/health/liveness` y `/actuator/health/readiness` en la API (puerto interno 8080)
  son para Docker: Nginx no los publica.
- En Docker, los tres servicios tienen comprobación de salud: `docker compose ps` debe mostrarlos
  `healthy`. La de la app comprueba que Nginx responde; la de la API, que está lista.
- Al parar o actualizar, la API termina las peticiones en curso y los correos que estaba enviando
  antes de apagarse, y MySQL cierra bien sus archivos. Por eso `docker compose stop` puede tardar
  hasta un minuto: no hay que matarlo.
- Nginx vuelve a buscar la dirección de la API cada diez segundos, así que actualizar solo la API
  (`docker compose up -d --build api`) no obliga a reiniciarlo.
- Logs: salida estándar de cada contenedor (`docker compose logs -f api`).

### Copias de seguridad

Hay que respaldar **juntas** la base de datos y la carpeta de archivos: la base guarda los
metadatos y la carpeta, el contenido. Uno sin el otro no sirve.

**Hacer una copia.** Desde la raíz del repositorio, con la plataforma encendida (no hace falta
apagarla ni sacar a nadie):

```bash
scripts/copia.sh
```

Deja en `copias/` un solo archivo, `idearium-AAAA-MM-DD-HHMMSS.tar`, con el volcado de la base, los
archivos subidos y un manifiesto (fecha, versión de la base, número de archivos y sus sumas de
comprobación). Si algo falla, no deja paquete y termina con error: sirve para programarlo.

- La carpeta de destino se puede cambiar: `scripts/copia.sh /ruta/de/copias`.
- El paquete lleva datos reales de estudiantes y **no es una copia hasta que sale del servidor**: hay
  que llevarlo a donde TI guarde sus respaldos. `copias/` está en el `.gitignore`.
- El script no borra copias viejas: cuántas se conservan lo decide quien administra el disco.
- Para programarla cada noche, una línea de `cron` en el servidor:

```
30 2 * * * cd /ruta/a/expoideas && scripts/copia.sh /ruta/de/copias >> /var/log/idearium-copia.log 2>&1
```

**Restaurar una copia.** Reemplaza la base y los archivos por los de la copia; lo que haya en ese
momento se pierde, y por eso pide escribir `RESTAURAR`:

```bash
scripts/restaurar.sh copias/idearium-AAAA-MM-DD-HHMMSS.tar
```

Antes de tocar nada comprueba que el paquete esté entero (si no coincide con su manifiesto, se
niega). Después apaga la app y la API, carga la base, repone los archivos y lo vuelve a encender
todo. La copia puede ser de esta versión de la plataforma o de una anterior: al arrancar, la API
aplica las migraciones que falten. No sirve una copia de una versión **más nueva** que el código
instalado: la API no arrancaría.

**Probar la restauración** sin tocar la instalación real. Una copia que nunca se ha restaurado no
está probada. Se levanta una segunda instalación, vacía, con otro nombre y otro puerto, y se
restaura ahí:

```bash
export COMPOSE_PROJECT_NAME=idearium-prueba HTTP_PORT=8090
docker compose up -d --build
scripts/restaurar.sh copias/idearium-AAAA-MM-DD-HHMMSS.tar
# Entrar a http://servidor:8090/expoideas/ con una cuenta real y abrir un entregable.
docker compose down -v      # borra la instalación de prueba, con sus datos
unset COMPOSE_PROJECT_NAME HTTP_PORT
```

Los dos `export` son los que hacen que todo lo que sigue, incluido el script, actúe sobre la
instalación de prueba y no sobre la real: sin ellos, `restaurar.sh` y `down -v` actuarían sobre la
real. El script dice sobre qué instalación va a actuar antes de pedir la confirmación.

Así se probó el 1 de octubre de 2026: con cuentas, un proyecto con jurados y un archivo subido, se
hizo la copia, se borró la instalación entera, se levantó vacía y se restauró. Volvieron las mismas
cuentas y el mismo proyecto, y el archivo se descargó idéntico al original.

En una instalación sin Docker (opción B) los scripts no aplican: la copia es un `mysqldump
--single-transaction` de la base y un `tar` de la carpeta `FILES_DIR`, hechos en ese orden.

### Versiones publicadas

Cada cambio que llega a `main` y pasa todas las pruebas (incluido el recorrido de punta a punta)
se publica como dos imágenes, la de la API y la de la app, en el registro de GitHub
(`ghcr.io/kenshin11083012/expoideas-api` y `expoideas-app`). La **etiqueta** es el commit: siete
caracteres, como `211162e`. Sale en el resumen de cada corrida de la integración continua
(«Publicar imágenes») y en la página de paquetes del repositorio.

Desplegar así tiene dos ventajas sobre compilar en el servidor: lo que corre es exactamente lo que
pasó las pruebas, y volver a la versión anterior es cambiar una etiqueta, no compilar otra vez.

**Preparar el servidor (una vez).** Las imágenes son privadas: el servidor inicia sesión en el
registro con un token personal de GitHub de quien administre el repositorio, creado con el único
permiso `read:packages`:

```bash
docker login ghcr.io -u <usuario-de-github>
# pide la contraseña: se pega el token
```

Y en el `.env`, además de lo de siempre:

```
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml
IDEARIUM_VERSION=<etiqueta>
```

`COMPOSE_FILE` hace que todos los comandos `docker compose`, y los scripts de copia, usen las
imágenes publicadas sin tener que recordarlo. Sin `IDEARIUM_VERSION`, Compose se niega a arrancar.
Hace falta Docker Compose 2.24 o posterior (`docker compose version`). En Windows los dos archivos de
`COMPOSE_FILE` se separan con `;` en vez de `:`.

**Qué versión está corriendo.** La etiqueta sale en el pie de la app («v. 211162e»), en
`docker compose images` y, para la API:

```bash
docker compose exec app wget -qO- http://api:8080/actuator/info
```

Una imagen compilada en el servidor dice `local`.

### Actualizar a una versión nueva

```bash
scripts/copia.sh
# En el .env: IDEARIUM_VERSION=<etiqueta nueva>  (y anotar cuál era la anterior)
docker compose pull
docker compose up -d
docker compose ps
```

Solo se recrean la API y la app; MySQL sigue corriendo. Tarda menos de un minuto, durante el cual
la plataforma no responde. Las pestañas que la gente tenía abiertas se recargan solas al navegar.

La API aplica sola las migraciones pendientes al arrancar. Si una migración falla, la API no
arranca (`docker compose logs api`). MySQL no revierte cambios de estructura, así que la base
puede quedar a medias: por eso la copia previa es obligatoria.

Compilando en el servidor (sin `COMPOSE_FILE`), actualizar sigue siendo `scripts/copia.sh`,
`git pull` y `docker compose up -d --build`.

### Volver a la versión anterior

Depende de si la versión nueva cambió la base de datos. Se sabe comparando la migración que dice
la copia que se hizo antes de actualizar (`migracion: V21`, la muestra `scripts/copia.sh` al
terminar) con la que tiene la base ahora:

```bash
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1" "$MYSQL_DATABASE"'
```

- **Es la misma: la versión nueva no tocó la base.** Basta con la etiqueta anterior. No se pierde
  nada de lo que la gente hizo mientras tanto.

  ```bash
  # En el .env: IDEARIUM_VERSION=<etiqueta anterior>
  docker compose up -d
  ```

- **Es mayor: la versión nueva aplicó migraciones.** La versión anterior no sabe trabajar con esa
  base (puede arrancar y fallar después, al guardar). Se vuelve con la etiqueta anterior **y** con
  la copia, y lo que la gente hizo desde la actualización se pierde: por eso conviene actualizar
  cuando hay poca actividad y comprobar enseguida.

  ```bash
  # En el .env: IDEARIUM_VERSION=<etiqueta anterior>
  docker compose pull
  scripts/restaurar.sh copias/idearium-<la de antes de actualizar>.tar
  ```

Si se compila en el servidor, la etiqueta anterior es un commit: `git checkout <commit>` y
`docker compose up -d --build`, con la misma regla para la base.

## Seguridad: lista de verificación

- [ ] HTTPS con el certificado de la universidad delante de Nginx.
- [ ] `.env` fuera del repositorio, con permisos restringidos y contraseñas únicas.
- [ ] MySQL y la API sin puertos publicados (solo la app).
- [ ] `SPRING_PROFILES_ACTIVE=prod` (Swagger apagado).
- [ ] Imágenes publicadas por la integración continua (`IDEARIUM_VERSION` en el `.env`), no
      compiladas en el servidor, y anotada la etiqueta anterior antes de cada actualización.
- [ ] Copias de seguridad programadas (`scripts/copia.sh`), guardadas fuera del servidor, y una
      restauración probada con una copia real (`scripts/restaurar.sh` en una instalación de prueba).
- [ ] Opcional: antivirus sobre la carpeta de archivos (p. ej. ClamAV). La plataforma ya valida
      tipo y tamaño de cada archivo por su contenido.

## Integración continua

`.github/workflows/ci.yml` ejecuta en cada push a `main` y en cada pull request las pruebas de
la API, el lint, las pruebas y el build de la app, y construye las dos imágenes Docker. Además
levanta la plataforma completa con `docker compose` y la recorre de punta a punta (carpeta `qa/`).
Cuando todo eso pasa en `main`, publica las dos imágenes con la etiqueta del commit
([Versiones publicadas](#versiones-publicadas)).

### Seguridad de lo que se publica

- **Dependencias de la app.** `npm audit` sobre lo que llega al navegador: una vulnerabilidad alta o
  crítica rompe la corrida.
- **Imágenes.** Las dos se escanean con Trivy antes de poder publicarse. La de la API lleva dentro
  las dependencias de Java y los paquetes del sistema, así que el escaneo cubre las dos cosas. Una
  vulnerabilidad **crítica con arreglo publicado** rompe la corrida; las altas salen en el resumen
  de la corrida sin romperla.
- **Acciones.** Van fijadas al commit de su versión, no a la etiqueta, y Dependabot propone las
  actualizaciones cada semana, igual que las de npm, Maven y las imágenes base.

Las imágenes se construyen siempre sobre la imagen base del día, que es la que trae los parches del
sistema, y la de la API aplica además los que la base todavía no tenga.

Cuando el escaneo rompe la corrida sin que nadie haya cambiado el código, es que se publicó una
vulnerabilidad nueva. Lo que toca, por orden:

1. Mirar en el resumen de la corrida qué paquete es y qué versión la arregla.
2. Si es una dependencia de Java que maneja Spring Boot, subir Spring Boot; si todavía no hay
   versión que la traiga, fijar la del paquete en las `<properties>` del `pom.xml`, como están
   hoy Tomcat y Jackson. Si es del sistema, volver a lanzar la corrida: se construye sobre la imagen
   base de ese día.
3. Solo si se revisó y no aplica a Idearium, anotarla en `.trivyignore` con el motivo y la fecha.

### Cobertura de las pruebas

Cada corrida deja en su resumen cuánto del código ejercitan las pruebas: líneas y ramas de la API
(JaCoCo, unitarias e integración juntas) y de la app (Vitest). No hay un mínimo que rompa la
corrida: sirve para ver si un cambio grande llega sin pruebas. Al 1 de octubre de 2026 la API está
en 97 % de líneas y 90 % de ramas, y la app en 80 % y 80 %.

En local, el informe navegable queda en `expoideas-api/target/site/jacoco/index.html` después de
`./mvnw verify`, y en `expoideas-app/coverage/index.html` después de `npm run test:coverage`.
