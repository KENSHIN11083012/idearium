// Utilidades compartidas por seed.mjs, smoke.mjs y los specs de Playwright. Solo Node 22+ (fetch, FormData, Blob).
import { execFileSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

export const QA_DIR = dirname(fileURLToPath(import.meta.url));
export const REPO_DIR = join(QA_DIR, "..");
/** Las cuentas de prueba con sus contraseñas. Lo genera seed.mjs; git lo ignora. */
export const CREDS_FILE = process.env.QA_CREDS ?? join(QA_DIR, "credenciales.json");
export const API = process.env.QA_API ?? "http://localhost:8080/expoideas/api/v1";

/** Cumple ValidationPatterns.PASSWORD: 8+ caracteres, un número y un símbolo. */
export const newPassword = () => `Qa-${randomBytes(9).toString("base64url")}#7`;

export function loadCreds() {
  if (!existsSync(CREDS_FILE)) throw new Error(`No existe ${CREDS_FILE}: corre primero \`node qa/seed.mjs\``);
  return JSON.parse(readFileSync(CREDS_FILE, "utf8"));
}

export const saveJson = (file, data) => writeFileSync(file, JSON.stringify(data, null, 2) + "\n");

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Petición a la API. Devuelve { status, body, headers } sin lanzar por códigos 4xx/5xx.
 *
 * Nginx frena los intentos en serie contra /auth/ (429 sin JSON). Las pruebas entran muchas veces
 * seguidas desde la misma dirección: cuando pasa, se espera y se reintenta en vez de fallar.
 */
export async function api(method, path, { token, json, form, raw } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let body;
  if (json !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(json);
  } else if (form) {
    body = form;
  }
  for (let attempt = 1; ; attempt++) {
    const res = await fetch(API + path, { method, headers, body });
    const isJson = (res.headers.get("content-type") ?? "").includes("json");
    if (res.status === 429 && !isJson && attempt < 10) {
      await res.arrayBuffer();
      await sleep(2000);
      continue;
    }
    const text = await res.text();
    const parsed = !raw && isJson && text ? JSON.parse(text) : text;
    return { status: res.status, body: parsed, headers: res.headers };
  }
}

export async function login(email, password) {
  const res = await api("POST", "/auth/login", { json: { email, password } });
  return res.status === 200 ? res.body : null;
}

/** Ejecuta SQL dentro del contenedor mysql con el usuario de la app (la clave la toma del propio contenedor). */
export function sql(statement) {
  return execFileSync(
    "docker",
    [
      "compose", "exec", "-T", "mysql", "sh", "-c",
      `MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -N -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "$0"`,
      statement,
    ],
    { cwd: REPO_DIR, encoding: "utf8", env: { ...process.env, MSYS_NO_PATHCONV: "1" } },
  ).trim();
}

export function multipartFile(bytes, filename, type) {
  const form = new FormData();
  form.append("file", new Blob([bytes], { type }), filename);
  return form;
}
