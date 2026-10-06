#!/usr/bin/env bash
# Restaura una copia de seguridad hecha con scripts/copia.sh: reemplaza la base
# de datos y los archivos subidos por los de la copia.
#
#   scripts/restaurar.sh copias/idearium-AAAA-MM-DD-HHMMSS.tar
#
# BORRA lo que haya ahora en la base y en los archivos. Pide confirmación; para
# usarlo sin nadie delante (una prueba de restauración programada), añadir --si.
#
# Se ejecuta desde la raíz del repositorio, con MySQL encendido. En Windows,
# desde Git Bash. La copia tiene que ser de esta versión de la plataforma o de
# una anterior: al arrancar, la API aplica las migraciones que le falten.
set -euo pipefail

export MSYS_NO_PATHCONV=1

fallo() {
    echo "ERROR: $*" >&2
    exit 1
}

PAQUETE=""
CONFIRMADO=no
for argumento in "$@"; do
    case "$argumento" in
        --si) CONFIRMADO=si ;;
        -*) fallo "opción desconocida: $argumento" ;;
        *) PAQUETE="$argumento" ;;
    esac
done
[ -n "$PAQUETE" ] || fallo "falta la copia: scripts/restaurar.sh copias/idearium-AAAA-MM-DD-HHMMSS.tar"
[ -f "$PAQUETE" ] || fallo "no existe $PAQUETE"
PAQUETE="$(cd "$(dirname "$PAQUETE")" && pwd)/$(basename "$PAQUETE")"

cd "$(dirname "$0")/.."

TRABAJO="$(mktemp -d)"
trap 'rm -rf "$TRABAJO"' EXIT

# Antes de tocar nada: que la copia esté entera.
tar xf "$PAQUETE" -C "$TRABAJO" 2>/dev/null || fallo "$PAQUETE no es una copia de Idearium"
for parte in MANIFIESTO.txt base.sql.gz archivos.tar.gz; do
    [ -f "$TRABAJO/$parte" ] || fallo "a la copia le falta $parte"
done
(cd "$TRABAJO" && grep -E '[ *](base\.sql\.gz|archivos\.tar\.gz)$' MANIFIESTO.txt | sha256sum -c --quiet) \
    || fallo "la copia está dañada: no coincide con su manifiesto"

docker compose ps --status running --services 2>/dev/null | grep -qx mysql \
    || fallo "MySQL no está encendido. Enciéndelo con: docker compose up -d mysql"

PROYECTO="$(docker compose config --format json | sed -n 's/^  "name": "\(.*\)",\{0,1\}$/\1/p' | head -1)"
echo "Copia:"
sed -n '2,4p' "$TRABAJO/MANIFIESTO.txt" | sed 's/^/  /'
echo "Se va a REEMPLAZAR la base de datos y los archivos de la instalación «${PROYECTO:-actual}»."
echo "Lo que hay ahora se pierde."
if [ "$CONFIRMADO" != si ]; then
    printf 'Escribe RESTAURAR para continuar: '
    read -r respuesta || respuesta=""
    [ "$respuesta" = RESTAURAR ] || fallo "cancelado; no se cambió nada"
fi

echo "1/4 Apagando la app y la API (nadie debe escribir mientras se restaura)..."
docker compose stop app api >/dev/null 2>&1 || true

echo "2/4 Base de datos..."
docker compose exec -T mysql sh -c \
    'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`; CREATE DATABASE \`$MYSQL_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"' \
    2>"$TRABAJO/mysql.log" \
    || fallo "no se pudo vaciar la base: $(grep -v 'Using a password' "$TRABAJO/mysql.log" | tail -3)"
gunzip -c "$TRABAJO/base.sql.gz" | docker compose exec -T mysql sh -c \
    'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 "$MYSQL_DATABASE"' \
    2>"$TRABAJO/mysql.log" \
    || fallo "no se pudo cargar el volcado: $(grep -v 'Using a password' "$TRABAJO/mysql.log" | tail -3)"

echo "3/4 Archivos subidos..."
# Con la imagen de la API pero sin arrancarla: es quien tiene montada la carpeta
# y el usuario dueño de los archivos.
docker compose run --rm --no-deps -T --entrypoint sh api -c \
    'find /data/files -mindepth 1 -delete && tar xzf - -C /data/files' <"$TRABAJO/archivos.tar.gz" \
    || fallo "no se pudieron restaurar los archivos"

echo "4/4 Encendiendo..."
docker compose up -d >/dev/null 2>&1 || fallo "no se pudo encender: revisa docker compose logs api"

echo
echo "Restauración terminada. Comprueba que la API arrancó: docker compose ps"
echo "Si la copia era de una versión anterior, la API aplica al arrancar las migraciones que falten."
