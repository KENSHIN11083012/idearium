#!/usr/bin/env bash
# Copia de seguridad de Idearium: la base de datos y los archivos subidos, juntos,
# en un solo paquete. Se respaldan juntos porque la base guarda los datos de cada
# archivo y la carpeta, su contenido: uno sin el otro no sirve.
#
#   scripts/copia.sh [carpeta]     # por defecto, ./copias
#
# Se ejecuta desde la raíz del repositorio, con la plataforma encendida
# (docker compose up -d). En Windows, desde Git Bash.
#
# El paquete lleva datos reales de estudiantes: se guarda fuera del repositorio
# y del servidor. Para restaurarlo: scripts/restaurar.sh.
set -euo pipefail

# Git Bash convierte en rutas de Windows lo que parece una ruta de Linux; las de
# aquí son de dentro de los contenedores.
export MSYS_NO_PATHCONV=1

cd "$(dirname "$0")/.."

DESTINO="${1:-copias}"
SELLO="$(date +%Y-%m-%d-%H%M%S)"
PAQUETE="$DESTINO/idearium-$SELLO.tar"
TRABAJO="$(mktemp -d)"
trap 'rm -rf "$TRABAJO"' EXIT

fallo() {
    echo "ERROR: $*" >&2
    exit 1
}

docker compose ps --status running --services 2>/dev/null | grep -qx mysql \
    || fallo "MySQL no está encendido. Enciende la plataforma con: docker compose up -d"
docker compose ps --status running --services 2>/dev/null | grep -qx api \
    || fallo "La API no está encendida. Enciende la plataforma con: docker compose up -d"

mkdir -p "$DESTINO"

echo "1/3 Base de datos..."
# --single-transaction: una foto coherente sin bloquear a quien está usando la plataforma.
docker compose exec -T mysql sh -c \
    'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --no-tablespaces "$MYSQL_DATABASE"' \
    2>"$TRABAJO/mysqldump.log" | gzip >"$TRABAJO/base.sql.gz" \
    || fallo "mysqldump falló: $(grep -v 'Using a password' "$TRABAJO/mysqldump.log" | tail -3)"
# Un volcado cortado a medias no es una copia: mysqldump termina siempre con esta línea.
gunzip -c "$TRABAJO/base.sql.gz" | tail -1 | grep -q 'Dump completed' \
    || fallo "el volcado de la base quedó incompleto"

echo "2/3 Archivos subidos..."
# Después de la base: un archivo subido entre un paso y otro queda de sobra en
# la copia, que no hace daño; al revés, la base nombraría un archivo que falta.
docker compose exec -T api tar czf - -C /data/files . >"$TRABAJO/archivos.tar.gz" \
    || fallo "no se pudieron empaquetar los archivos"
gzip -t "$TRABAJO/archivos.tar.gz" || fallo "el paquete de archivos quedó dañado"

echo "3/3 Paquete..."
VERSION="$(docker compose exec -T mysql sh -c \
    'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1" "$MYSQL_DATABASE"' \
    2>/dev/null | tr -d '\r')"
{
    echo "Copia de seguridad de Idearium"
    echo "fecha: $SELLO"
    echo "migracion: V$VERSION"
    echo "archivos: $(tar tzf "$TRABAJO/archivos.tar.gz" | grep -vc '/$' || true)"
    (cd "$TRABAJO" && sha256sum base.sql.gz archivos.tar.gz)
} >"$TRABAJO/MANIFIESTO.txt"
tar cf "$PAQUETE" -C "$TRABAJO" MANIFIESTO.txt base.sql.gz archivos.tar.gz

echo
echo "Copia lista: $PAQUETE ($(du -h "$PAQUETE" | cut -f1))"
sed -n '2,4p' "$TRABAJO/MANIFIESTO.txt" | sed 's/^/  /'
echo "Lleva datos reales: guárdala fuera de este servidor."
