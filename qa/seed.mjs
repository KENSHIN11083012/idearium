// Crea las cuentas de prueba y deja qa/credenciales.{json,md}. Idempotente: si una cuenta
// ya entra con su contraseña, no la toca. Uso (desde la raíz del repo): node qa/seed.mjs
import { existsSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { API, CREDS_FILE, PNG, api, loadCreds, login, multipartFile, newPassword, saveJson, sql } from "./lib.mjs";

const FACULTY = "Ingenierías";

const ACCOUNTS = [
  { key: "admin", role: "ADMIN", email: "qa.admin@unisimon.edu.co", firstName: "Ada", lastName: "Admin QA", via: "register+sql" },
  { key: "macondolab", role: "MACONDOLAB", email: "qa.macondolab@unisimon.edu.co", firstName: "Mara", lastName: "Macondo QA", via: "admin" },
  { key: "docente1", role: "TEACHER", email: "qa.docente1@unisimon.edu.co", firstName: "Diego", lastName: "Docente Uno QA", via: "admin" },
  { key: "docente2", role: "TEACHER", email: "qa.docente2@unisimon.edu.co", firstName: "Dana", lastName: "Docente Dos QA", via: "admin" },
  { key: "jurado", role: "JUDGE", email: "qa.jurado@example.com", firstName: "Julio", lastName: "Jurado Externo QA", via: "admin" },
  ...[1, 2, 3, 4].map((n) => ({
    key: `estudiante${n}`, role: "STUDENT", email: `qa.estudiante${n}@unisimon.edu.co`,
    firstName: `Estudiante${n}`, lastName: "QA", via: "register",
  })),
];

const creds = existsSync(CREDS_FILE) ? loadCreds() : {};
for (const a of ACCOUNTS) creds[a.key] ??= { ...a, password: newPassword() };
saveJson(CREDS_FILE, creds);

// Campus y facultad de estudiantes y profesores. La facultad la crea el administrador más abajo:
// una base nueva no trae ninguna.
const campusId = (await api("GET", "/campuses")).body[0].id;
let facultyId;

// El registro solo pide correo, contraseña y autorización de datos: el nombre y la adscripción
// se completan en el primer ingreso.
const register = async (c) => {
  const res = await api("POST", "/auth/register", { json: { email: c.email, password: c.password, dataConsent: true } });
  if (res.status !== 201) throw new Error(`Registro de ${c.email}: ${res.status} ${JSON.stringify(res.body)}`);
};

const must = (res, what, ...statuses) => {
  if (!statuses.includes(res.status)) throw new Error(`${what}: ${res.status} ${JSON.stringify(res.body)}`);
  return res.body;
};

/**
 * El primer ingreso de una cuenta, paso a paso, como lo haría la persona: verificar el correo,
 * cambiar la contraseña temporal, autorizar el tratamiento de datos, completar el perfil y subir
 * la foto.
 *
 * @param currentPassword la contraseña con la que entra hoy (la temporal, si la creó la gestión)
 */
const firstLogin = async (c, role, currentPassword) => {
  let session = await login(c.email, currentPassword);
  if (!session) throw new Error(`${c.email} no pudo entrar para su primer ingreso`);
  let { token } = session;
  const pending = new Set(session.pendingSteps);

  if (pending.has("VERIFY_EMAIL")) {
    // El enlace llega por correo y aquí no hay buzón que leer: se da por verificado en la base.
    sql(`UPDATE users SET email_verification_pending = 0, email_verified_at = NOW() WHERE email = '${c.email}';`);
  }
  if (pending.has("CHANGE_PASSWORD")) {
    // Cambiar la contraseña cierra las sesiones abiertas: la respuesta trae el token con el que sigue esta.
    const body = must(
      await api("PUT", "/users/me/password", {
        token, json: { currentPassword, newPassword: c.password, confirmPassword: c.password },
      }),
      `Cambio de clave de ${c.email}`, 200,
    );
    token = body.token;
  }
  if (pending.has("DATA_CONSENT")) {
    must(await api("PUT", "/users/me/data-consent", { token, json: { dataConsent: true } }), `Consentimiento de ${c.email}`, 200, 204);
  }
  if (pending.has("COMPLETE_PROFILE")) {
    const withAffiliation = ["TEACHER", "STUDENT"].includes(role);
    must(
      await api("PUT", "/users/me", {
        token, json: { firstName: c.firstName, lastName: c.lastName, ...(withAffiliation ? { campusId, facultyId } : {}) },
      }),
      `Perfil de ${c.email}`, 200,
    );
  }
  if (pending.has("PROFILE_PHOTO")) await uploadPhoto(c, token);
};

/** La foto de perfil es obligatoria para todos los roles. */
const uploadPhoto = async (c, token) =>
  must(await api("PUT", "/users/me/photo", { token, form: multipartFile(PNG, "foto.png", "image/png") }), `Foto de ${c.email}`, 200);

/**
 * Entra con una cuenta que ya existe. Si lo único que le falta es la foto (la instalación es
 * anterior a que fuera obligatoria), la sube: sin ella la API no la deja hacer nada más.
 */
const enter = async (c) => {
  const session = await login(c.email, c.password);
  if (session?.pendingSteps.includes("PROFILE_PHOTO")) await uploadPhoto(c, session.token);
  return session;
};

// 1. Administrador. Si la instalación arrancó con ADMIN_EMAIL=qa.admin@unisimon.edu.co y
// ADMIN_PASSWORD (así corre la integración continua), la cuenta ya existe con esa contraseña
// temporal y solo falta su primer ingreso. Si no, se registra y se promueve por SQL.
const admin = creds.admin;
const bootstrapPassword = process.env.ADMIN_PASSWORD;
if (!(await enter(admin))) {
  if (bootstrapPassword) {
    await firstLogin(admin, "ADMIN", bootstrapPassword);
  } else {
    await register(admin);
    sql(`UPDATE users SET role = 'ADMIN' WHERE email = '${admin.email}';`);
    await firstLogin(admin, "ADMIN", admin.password);
  }
  console.log(`+ ${admin.email} (ADMIN${bootstrapPassword ? ", creada al arrancar" : ""})`);
}
const adminToken = (await login(admin.email, admin.password)).token;

// 2. La facultad, por la API y con el administrador: como se hace en una instalación de verdad.
let faculties = (await api("GET", "/faculties")).body;
if (!faculties.some((f) => f.name === FACULTY)) {
  must(await api("POST", "/faculties", { token: adminToken, json: { name: FACULTY } }), "Alta de la facultad", 200, 201);
  faculties = (await api("GET", "/faculties")).body;
}
facultyId = faculties.find((f) => f.name === FACULTY).id;
console.log(`Facultad ${facultyId}, campus ${campusId}`);

// 3. El resto.
for (const a of ACCOUNTS.filter((x) => x.via !== "register+sql")) {
  const c = creds[a.key];
  if (await enter(c)) continue;
  if (a.via === "register") {
    await register(c);
    await firstLogin(c, a.role, c.password);
  } else {
    const temp = newPassword();
    const withAffiliation = ["TEACHER", "STUDENT"].includes(a.role);
    must(
      await api("POST", "/admin/users", {
        token: adminToken,
        json: {
          firstName: c.firstName, lastName: c.lastName, email: c.email, password: temp, role: a.role,
          ...(withAffiliation ? { campusId, facultyId } : {}),
        },
      }),
      `Alta de ${c.email}`, 200, 201,
    );
    await firstLogin(c, a.role, temp);
  }
  console.log(`+ ${c.email} (${a.role})`);
}

// 4. Comprobación final y tabla legible.
const rows = [];
for (const a of ACCOUNTS) {
  const c = creds[a.key];
  const s = await login(c.email, c.password);
  if (!s || s.role !== a.role || s.pendingSteps.length) throw new Error(`${c.email} no quedó lista: ${JSON.stringify(s)}`);
  c.id = s.id;
  rows.push(`| ${a.key} | ${a.role} | ${c.email} | \`${c.password}\` | ${a.via} |`);
}
saveJson(CREDS_FILE, creds);
const table = join(dirname(CREDS_FILE), "credenciales.md");
writeFileSync(
  table,
  [
    "# Cuentas de prueba (solo local, ignoradas por git)",
    "",
    `API: ${API}`,
    "",
    "| Clave | Rol | Correo | Contraseña | Creada por |",
    "|---|---|---|---|---|",
    ...rows,
    "",
  ].join("\n"),
);
console.log(`OK: ${rows.length} cuentas listas en ${table}`);
