// Recorrido de la API por rol, con asserts. Sale con código ≠0 si algo falla.
// Deja la base como la encontró salvo catálogos, edición y entregables QA (se reutilizan).
// Uso (desde la raíz del repo, después de seed.mjs): node qa/smoke.mjs
import { API, PNG, api, loadCreds, login, multipartFile } from "./lib.mjs";

const creds = loadCreds();
const results = [];
const check = async (name, fn) => {
  try {
    await fn();
    results.push({ ok: true, name });
    console.log(`  ✓ ${name}`);
  } catch (e) {
    results.push({ ok: false, name, error: e.message });
    console.log(`  ✗ ${name}\n      ${e.message}`);
  }
};
const expect = (res, ...statuses) => {
  if (!statuses.includes(res.status)) {
    throw new Error(`esperaba ${statuses.join("/")}, llegó ${res.status}: ${JSON.stringify(res.body).slice(0, 300)}`);
  }
  return res.body;
};
const assert = (cond, msg) => {
  if (!cond) throw new Error(msg);
};
const section = (t) => console.log(`\n${t}`);

// --- Archivos de prueba, generados aquí para no depender de binarios en disco ---
const PDF = Buffer.from(
  "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj " +
    "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n",
);
const FAKE_EXE = Buffer.concat([Buffer.from("MZ\x90\x00"), Buffer.alloc(512)]);
const BIG_PDF = Buffer.concat([Buffer.from("%PDF-1.4\n"), Buffer.alloc(5 * 1024 * 1024 + 1024, 0x20)]);

// --- Sesiones ---
const tok = {};
section("1. Autenticación");
for (const [key, c] of Object.entries(creds)) {
  await check(`login ${key} (${c.role})`, async () => {
    const s = await login(c.email, c.password);
    assert(s, "no pudo entrar");
    assert(s.role === c.role, `rol ${s.role}`);
    assert(s.pendingSteps.length === 0, `pasos pendientes ${s.pendingSteps}`);
    tok[key] = s.token;
  });
}
const campusId = (await api("GET", "/campuses")).body[0].id;
const facultyId = (await api("GET", "/faculties")).body[0].id;
await check("sin token → 401 en /users/me", async () => expect(await api("GET", "/users/me"), 401));
await check("token basura → 401", async () => expect(await api("GET", "/users/me", { token: "xxx.yyy.zzz" }), 401));
await check("clave errada → 401", async () =>
  expect(await api("POST", "/auth/login", { json: { email: creds.admin.email, password: "Nope#1234" } }), 401));
await check("registro con correo no institucional → 400 en email", async () => {
  const b = expect(
    await api("POST", "/auth/register", {
      json: { email: "x@gmail.com", password: "Valida#123", dataConsent: true },
    }),
    400,
  );
  assert(b.fields?.email, `sin fields.email: ${JSON.stringify(b)}`);
});
await check("registro con clave débil → 400 en password", async () => {
  const b = expect(
    await api("POST", "/auth/register", {
      json: { email: "qa.debil@unisimon.edu.co", password: "abcdefgh", dataConsent: true },
    }),
    400,
  );
  assert(b.fields?.password, "sin fields.password");
});
await check("registro sin consentimiento → 400", async () =>
  expect(
    await api("POST", "/auth/register", {
      json: { email: "qa.noconsent@unisimon.edu.co", password: "Valida#123", dataConsent: false },
    }),
    400,
  ));
await check("registro con correo repetido → 409", async () =>
  expect(
    await api("POST", "/auth/register", {
      json: { email: creds.estudiante1.email, password: "Valida#123", dataConsent: true },
    }),
    409,
  ));

section("2. Permisos por rol");
await check("STUDENT → /admin/users 403", async () => expect(await api("GET", "/admin/users", { token: tok.estudiante1 }), 403));
await check("TEACHER → /admin/users 403", async () => expect(await api("GET", "/admin/users", { token: tok.docente1 }), 403));
await check("JUDGE → /admin/users 403", async () => expect(await api("GET", "/admin/users", { token: tok.jurado }), 403));
await check("MACONDOLAB → /admin/users 200, por páginas", async () => {
  const page = expect(await api("GET", "/admin/users?size=2", { token: tok.macondolab }), 200);
  assert(page.items.length === 2 && page.size === 2, JSON.stringify(page));
  assert(page.totalItems > 2 && page.totalPages === Math.ceil(page.totalItems / 2), JSON.stringify(page));
});
await check("la búsqueda de cuentas la hace la API", async () => {
  const page = expect(await api("GET", "/admin/users?search=" + encodeURIComponent(creds.jurado.email), { token: tok.macondolab }), 200);
  assert(page.totalItems === 1 && page.items[0].email === creds.jurado.email, JSON.stringify(page));
});
await check("MACONDOLAB no crea facultades (solo ADMIN) → 403", async () =>
  expect(await api("POST", "/faculties", { token: tok.macondolab, json: { name: "QA Prohibida" } }), 403));
await check("MACONDOLAB no crea cuentas ADMIN → 403", async () =>
  expect(
    await api("POST", "/admin/users", {
      token: tok.macondolab,
      json: { firstName: "X", lastName: "Y", email: "qa.intruso@unisimon.edu.co", password: "Valida#123", role: "ADMIN" },
    }),
    403,
  ));
await check("MACONDOLAB no elimina cuentas → 403", async () =>
  expect(await api("DELETE", `/admin/users/${creds.estudiante4.id}`, { token: tok.macondolab }), 403));
await check("ADMIN no se cambia su propio rol → 403", async () =>
  expect(
    await api("PUT", `/admin/users/${creds.admin.id}`, {
      token: tok.admin, json: { role: "STUDENT" },
    }),
    403,
  ));
await check("TEACHER no crea ediciones → 403", async () =>
  expect(await api("POST", "/editions", { token: tok.docente1, json: {} }), 403));
await check("STUDENT no ve el directorio → 403", async () => expect(await api("GET", "/projects", { token: tok.estudiante1 }), 403));
await check("JUDGE no ve el directorio → 403", async () => expect(await api("GET", "/projects", { token: tok.jurado }), 403));
await check("cuenta externa no-JUDGE → 400 (correo no institucional)", async () =>
  expect(
    await api("POST", "/admin/users", {
      token: tok.admin,
      json: { firstName: "X", lastName: "Y", email: "qa.externo@example.com", password: "Valida#123", role: "TEACHER", campusId, facultyId },
    }),
    400,
  ));

section("3. Catálogos, edición y entregables (MacondoLab)");
let sectorId, edition, posterType, photosType;
await check("sector QA (crear o reutilizar)", async () => {
  const sectors = expect(await api("GET", "/sectors"), 200);
  sectorId = sectors.find((s) => s.name === "QA Tecnología")?.id;
  if (!sectorId) sectorId = expect(await api("POST", "/sectors", { token: tok.macondolab, json: { name: "QA Tecnología" } }), 200, 201).id;
});
const today = new Date(new Date().toLocaleString("en-US", { timeZone: "America/Bogota" }));
const day = (d) => new Date(today.getTime() + d * 864e5).toLocaleDateString("en-CA");
const editionBody = {
  name: "Expo QA",
  registrationOpensOn: day(-1),
  registrationClosesOn: day(30),
  submissionClosesOn: day(60),
  tracks: [
    { track: "INNPRENDE_I", minMembers: 1, maxMembers: 3 },
    { track: "INNPRENDE_II", minMembers: 2, maxMembers: 4 },
  ],
};
await check("fechas invertidas → 400", async () =>
  expect(await api("POST", "/editions", { token: tok.macondolab, json: { ...editionBody, name: "QA mala", registrationClosesOn: day(-5) } }), 400));
await check("una sola cátedra → 400", async () =>
  expect(await api("POST", "/editions", { token: tok.macondolab, json: { ...editionBody, name: "QA una", tracks: editionBody.tracks.slice(0, 1) } }), 400));
await check("edición QA abierta (crear o reutilizar)", async () => {
  const eds = expect(await api("GET", "/editions"), 200);
  edition = eds.find((e) => e.name === "Expo QA");
  if (!edition) edition = expect(await api("POST", "/editions", { token: tok.macondolab, json: editionBody }), 200, 201);
  assert(edition.registrationOpen && edition.submissionOpen, `edición no abierta: ${JSON.stringify(edition)}`);
});
await check("edición que se solapa → 400/409", async () =>
  expect(await api("POST", "/editions", { token: tok.macondolab, json: { ...editionBody, name: "QA solapada" } }), 400, 409));
await check("tipos de entregable de INNPRENDE I (crear o reutilizar)", async () => {
  const q = `?editionId=${edition.id}&track=INNPRENDE_I`;
  let types = expect(await api("GET", `/deliverable-types${q}`, { token: tok.macondolab }), 200);
  const ensure = async (name, kind, required, maxFiles, sortOrder) =>
    types.find((t) => t.name === name) ??
    expect(
      await api("POST", "/deliverable-types", {
        token: tok.macondolab,
        json: { editionId: edition.id, track: "INNPRENDE_I", name, description: "Creado por qa/smoke.mjs", kind, required, maxFiles, sortOrder },
      }),
      200, 201,
    );
  posterType = await ensure("Póster QA", "DOCUMENT", true, 1, 1);
  photosType = await ensure("Fotos QA", "IMAGE", false, 3, 2);
});
await check("STUDENT no crea tipos de entregable → 403", async () =>
  expect(await api("POST", "/deliverable-types", { token: tok.estudiante1, json: {} }), 403));

section("4. Proyecto y equipo");
// Limpieza de corridas anteriores: el líder borra sus proyectos QA.
for (const k of ["estudiante1", "estudiante2", "estudiante3", "estudiante4"]) {
  for (const p of (await api("GET", "/projects/mine", { token: tok[k] })).body ?? []) {
    if (p.title.startsWith("QA ")) await api("DELETE", `/projects/${p.id}`, { token: tok[k] });
  }
}
const docente1Id = creds.docente1.id;
const projectBody = {
  editionId: 0, track: "INNPRENDE_I", title: "QA Proyecto Humo", summary: "Proyecto creado por el smoke test.", sectorId: 0, teacherId: docente1Id,
  semester: 5, phone: "3001234567",
};
let project;
await check("TEACHER no inscribe proyectos → 403", async () =>
  expect(await api("POST", "/projects", { token: tok.docente1, json: { ...projectBody, editionId: edition.id, sectorId } }), 403));
await check("docentes visibles para el estudiante", async () => {
  const ts = expect(await api("GET", "/teachers", { token: tok.estudiante1 }), 200);
  assert(ts.some((t) => t.id === docente1Id), "falta docente1");
});
await check("estudiante1 inscribe proyecto → queda como líder", async () => {
  project = expect(await api("POST", "/projects", { token: tok.estudiante1, json: { ...projectBody, editionId: edition.id, sectorId } }), 200, 201);
  const leader = project.members.find((m) => m.userId === creds.estudiante1.id);
  assert(leader?.teamRole === "LEADER", `miembros: ${JSON.stringify(project.members)}`);
});
await check("segundo proyecto en la misma cátedra → 409/400", async () =>
  expect(await api("POST", "/projects", { token: tok.estudiante1, json: { ...projectBody, title: "QA Duplicado", editionId: edition.id, sectorId } }), 400, 409));
const invite = (who, email) => api("POST", `/projects/${project.id}/invitations`, { token: tok[who], json: { email } });
// Una invitación pendiente reserva cupo (ProjectPolicy.requireFreeSeat) y el cupo se valida
// antes que el invitado: los negativos van mientras aún hay lugar.
await check("invitar a estudiante2", async () => expect(await invite("estudiante1", creds.estudiante2.email), 200, 201));
await check("invitar a un docente → 400", async () => expect(await invite("estudiante1", creds.docente1.email), 400));
await check("invitar dos veces → 400", async () => expect(await invite("estudiante1", creds.estudiante2.email), 400));
await check("invitar a estudiante3", async () => expect(await invite("estudiante1", creds.estudiante3.email), 200, 201));
await check("con 2 pendientes + líder, el cupo (3) ya está lleno → 409", async () =>
  expect(await invite("estudiante1", creds.estudiante4.email), 409));
await check("un no-líder no invita → 403", async () => expect(await invite("estudiante4", creds.estudiante4.email), 403, 404));
const accept = async (who, action) => {
  const inv = expect(await api("GET", "/invitations", { token: tok[who] }), 200).find((i) => i.projectId === project.id);
  assert(inv, `${who} no ve la invitación`);
  // Quien acepta dice el semestre que cursa.
  return api("POST", `/invitations/${inv.id}/${action}`, { token: tok[who], json: action === "acceptance" ? { semester: 3 } : undefined });
};
await check("aceptar sin decir el semestre → 400", async () => {
  const inv = expect(await api("GET", "/invitations", { token: tok.estudiante2 }), 200).find((i) => i.projectId === project.id);
  expect(await api("POST", `/invitations/${inv.id}/acceptance`, { token: tok.estudiante2, json: {} }), 400);
});
await check("estudiante2 acepta", async () => expect(await accept("estudiante2", "acceptance"), 200, 204));
await check("el celular del líder lo ven él, su profesor y la gestión; su compañero no", async () => {
  const seen = async (who) => expect(await api("GET", `/projects/${project.id}`, { token: tok[who] }), 200);
  for (const who of ["estudiante1", "docente1", "macondolab"]) {
    assert((await seen(who)).leaderPhone === "3001234567", `${who} no ve el celular`);
  }
  const member = await seen("estudiante2");
  assert(member.leaderPhone === null, "un compañero ve el celular del líder");
  assert(JSON.stringify(member.members.map((m) => m.semester)) === "[5,3,null]", JSON.stringify(member.members));
});
await check("estudiante3 rechaza", async () => expect(await accept("estudiante3", "rejection"), 200, 204));
await check("estudiante3 (fuera del equipo) no ve el proyecto → 403/404", async () =>
  expect(await api("GET", `/projects/${project.id}`, { token: tok.estudiante3 }), 403, 404));
const candidates = (who, text) =>
  api("GET", `/projects/${project.id}/invitation-candidates?search=${encodeURIComponent(text)}`, { token: tok[who] });
await check("el líder busca por nombre y encuentra a estudiante4, sin su correo ni a los que ya están", async () => {
  const found = expect(await candidates("estudiante1", "estudiante4"), 200);
  const one = found.find((x) => x.id === creds.estudiante4.id);
  assert(one && one.fullName === "Estudiante4 QA", JSON.stringify(found));
  assert(!JSON.stringify(found).includes(creds.estudiante4.email), "la búsqueda entrega el correo completo");
  const all = expect(await candidates("estudiante1", "estudiante"), 200);
  assert(!all.some((x) => [creds.estudiante1.id, creds.estudiante2.id].includes(x.id)), "ofrece a quien ya está en el equipo");
  assert(expect(await candidates("estudiante1", "es"), 200).length === 0, "busca con dos letras");
});
await check("solo el líder busca compañeros → 403/404", async () => {
  expect(await candidates("estudiante2", "estudiante4"), 403);
  expect(await candidates("estudiante3", "estudiante4"), 403, 404);
});
await check("estudiante4 entra, invitado por su cuenta y no por su correo: el equipo llega al máximo (3)", async () => {
  expect(await api("POST", `/projects/${project.id}/invitations`, { token: tok.estudiante1, json: { userId: creds.estudiante4.id } }), 200, 201);
  expect(await accept("estudiante4", "acceptance"), 200, 204);
});
await check("invitar con el equipo lleno → 409", async () => expect(await invite("estudiante1", creds.estudiante3.email), 409));

section("5. Entregables y archivos");
const upload = (who, typeId, bytes, name, type) =>
  api("POST", `/projects/${project.id}/deliverables?deliverableTypeId=${typeId}`, { token: tok[who], form: multipartFile(bytes, name, type) });
let posterFileId;
await check("PNG en entregable DOCUMENT → 400", async () =>
  expect(await upload("estudiante1", posterType.id, PNG, "foto.png", "image/png"), 400));
await check("miembro sube el póster PDF", async () => {
  const groups = expect(await upload("estudiante2", posterType.id, PDF, "poster.pdf", "application/pdf"), 200, 201);
  const g = groups.find((x) => x.type.id === posterType.id);
  assert(g.complete && g.files.length === 1, JSON.stringify(g));
  posterFileId = g.files[0].fileId;
});
await check("segundo PDF con maxFiles=1 → 409", async () =>
  expect(await upload("estudiante1", posterType.id, PDF, "otro.pdf", "application/pdf"), 409, 400));
await check(".exe renombrado a .png → 400", async () =>
  expect(await upload("estudiante1", photosType.id, FAKE_EXE, "virus.png", "image/png"), 400));
await check("archivo de más de 5 MB → 400/413", async () =>
  expect(await upload("estudiante1", photosType.id, BIG_PDF, "grande.pdf", "application/pdf"), 400, 413));
await check("PNG válido en Fotos QA", async () => expect(await upload("estudiante4", photosType.id, PNG, "foto.png", "image/png"), 200, 201));
await check("no-miembro no sube → 403/404", async () =>
  expect(await upload("estudiante3", photosType.id, PNG, "foto.png", "image/png"), 403, 404));
await check("miembro descarga el póster", async () => {
  const r = await api("GET", `/files/${posterFileId}`, { token: tok.estudiante2, raw: true });
  expect(r, 200);
  assert(r.body.startsWith("%PDF-"), "contenido distinto");
});
await check("no-miembro no descarga el póster → 403/404", async () =>
  expect(await api("GET", `/files/${posterFileId}`, { token: tok.estudiante3, raw: true }), 403, 404));
await check("docente del proyecto descarga el póster", async () =>
  expect(await api("GET", `/files/${posterFileId}`, { token: tok.docente1, raw: true }), 200));
await check("cambia la foto de perfil por otro PNG; quitarla no se puede → 405", async () => {
  const before = expect(await api("GET", "/users/me", { token: tok.estudiante1 }), 200).photoId;
  const me = expect(await api("PUT", "/users/me/photo", { token: tok.estudiante1, form: multipartFile(PNG, "yo.png", "image/png") }), 200);
  assert(me.photoId && me.photoId !== before, "la foto no cambió");
  expect(await api("DELETE", "/users/me/photo", { token: tok.estudiante1 }), 405);
});

section("6. Directorio y CSV");
await check("MACONDOLAB ve el proyecto con progreso 1/1", async () => {
  const list = expect(await api("GET", `/projects?size=200&editionId=${edition.id}`, { token: tok.macondolab }), 200);
  assert(list.page === 0 && list.totalItems >= 1, JSON.stringify({ ...list, items: undefined }));
  const p = list.items.find((x) => x.id === project.id);
  assert(p, "no aparece");
  assert(p.members === 3 && p.requiredDeliverables === 1 && p.deliveredDeliverables === 1, JSON.stringify(p));
});
await check("el listado acepta el orden por título; uno desconocido → 400", async () => {
  expect(await api("GET", `/projects?size=200&sort=TITLE&editionId=${edition.id}`, { token: tok.macondolab }), 200);
  expect(await api("GET", "/projects/export?sort=TITLE", { token: tok.macondolab, raw: true }), 200);
  expect(await api("GET", "/projects?sort=CUALQUIERA", { token: tok.macondolab }), 400);
});
await check("la ficha trae el día en que cierran las entregas", async () => {
  const p = expect(await api("GET", `/projects/${project.id}`, { token: tok.estudiante1 }), 200);
  assert(/^\d{4}-\d{2}-\d{2}$/.test(p.submissionClosesOn) && p.submissionOpen, JSON.stringify(p));
});
await check("docente1 (nombrado) lo ve", async () => {
  const list = expect(await api("GET", "/projects?size=200", { token: tok.docente1 }), 200);
  assert(list.items.some((x) => x.id === project.id), "no aparece");
});
await check("docente2 (no nombrado) no lo ve", async () => {
  const list = expect(await api("GET", "/projects?size=200", { token: tok.docente2 }), 200);
  assert(!list.items.some((x) => x.id === project.id), "sí aparece");
});
await check("filtro por cátedra INNPRENDE_II lo excluye", async () => {
  const list = expect(await api("GET", "/projects?size=200&track=INNPRENDE_II", { token: tok.macondolab }), 200);
  assert(!list.items.some((x) => x.id === project.id), "aparece");
});
await check("CSV del docente1 trae el proyecto", async () => {
  const r = await api("GET", "/projects/export", { token: tok.docente1, raw: true });
  expect(r, 200);
  assert((r.headers.get("content-type") ?? "").startsWith("text/csv"), r.headers.get("content-type"));
  assert(r.body.includes("QA Proyecto Humo"), "falta el título");
});

section("7. Jurados y evaluación");
const jurors = () => `/projects/${project.id}/jurors`;
const myEvaluation = () => `/projects/${project.id}/evaluations/mine`;
const publication = () => `/editions/${edition.id}/tracks/INNPRENDE_I/grades-publication`;
let rubric;
/** Un nivel por criterio: el de esa posición (0 es el más bajo). */
const scores = (position) => rubric.criteria.map((c) => ({ criterionId: c.id, levelId: c.levels.at(position).id }));
await check("STUDENT no asigna jurados → 403", async () =>
  expect(await api("POST", jurors(), { token: tok.estudiante1, json: { email: creds.jurado.email } }), 403));
await check("el profesor del grupo no puede ser jurado de su proyecto → 400", async () =>
  expect(await api("POST", jurors(), { token: tok.macondolab, json: { email: creds.docente1.email } }), 400));
await check("alguien del equipo no puede ser jurado → 400", async () =>
  expect(await api("POST", jurors(), { token: tok.macondolab, json: { email: creds.estudiante2.email } }), 400));
await check("el jurado sin asignar no ve el proyecto → 404", async () =>
  expect(await api("GET", myEvaluation(), { token: tok.jurado }), 403, 404));
await check("MACONDOLAB asigna al jurado externo y a un profesor de otro grupo", async () => {
  expect(await api("POST", jurors(), { token: tok.macondolab, json: { email: creds.jurado.email } }), 201);
  expect(await api("POST", jurors(), { token: tok.macondolab, json: { email: creds.docente2.email } }), 201);
});
await check("asignar dos veces al mismo jurado → 409", async () =>
  expect(await api("POST", jurors(), { token: tok.macondolab, json: { email: creds.jurado.email } }), 409));
await check("el jurado ve el proyecto entre los suyos", async () => {
  const mine = expect(await api("GET", "/jury/projects", { token: tok.jurado }), 200);
  assert(mine.some((p) => p.id === project.id), "no aparece");
});
await check("la rúbrica del proyecto es la de su edición: el póster, con seis criterios y sus niveles", async () => {
  rubric = expect(await api("GET", `/projects/${project.id}/rubric`, { token: tok.jurado }), 200);
  assert(rubric.criteria.length === 6 && rubric.criteria.every((c) => c.levels.length >= 5), JSON.stringify(rubric).slice(0, 200));
  const ofEdition = expect(await api("GET", `/editions/${edition.id}`, { token: tok.jurado }), 200).tracks.find((t) => t.track === "INNPRENDE_I");
  assert(ofEdition.rubricId === rubric.id && rubric.version >= 1, JSON.stringify({ ofEdition, id: rubric.id, version: rubric.version }));
});
await check("quien no ve el proyecto no ve su rúbrica → 404", async () =>
  expect(await api("GET", `/projects/${project.id}/rubric`, { token: tok.estudiante3 }), 403, 404));
await check("evaluación con un criterio sin nivel → 400", async () =>
  expect(await api("PUT", myEvaluation(), { token: tok.jurado, json: { scores: scores(-1).slice(1) } }), 400));
await check("nivel por debajo de 3.0 sin observación → 400", async () =>
  expect(await api("PUT", myEvaluation(), { token: tok.jurado, json: { scores: scores(0) } }), 400));
await check("el jurado califica todo en el nivel más alto → 5.0", async () => {
  const saved = expect(await api("PUT", myEvaluation(), { token: tok.jurado, json: { scores: scores(-1) } }), 200);
  assert(saved.grade === 5 && saved.scale === "EXCELLENT", JSON.stringify(saved).slice(0, 200));
});
await check("el profesor del grupo ve la nota y quién falta por calificar", async () => {
  const results = expect(await api("GET", `/projects/${project.id}/evaluations`, { token: tok.docente1 }), 200);
  assert(results.grade === 5 && results.evaluations.length === 1 && results.pending.length === 1, JSON.stringify(results).slice(0, 300));
});
await check("el equipo no ve el detalle de las evaluaciones → 403", async () =>
  expect(await api("GET", `/projects/${project.id}/evaluations`, { token: tok.estudiante1 }), 403));
await check("el equipo no ve su nota hasta que se publica", async () =>
  expect(await api("GET", `/projects/${project.id}/grade`, { token: tok.estudiante1 }), 204));
await check("TEACHER no publica notas → 403", async () => expect(await api("PUT", publication(), { token: tok.docente1 }), 403));
await check("MACONDOLAB publica y el equipo ve su nota sin nombres de jurados", async () => {
  expect(await api("PUT", publication(), { token: tok.macondolab }), 200);
  const grade = await api("GET", `/projects/${project.id}/grade`, { token: tok.estudiante2, raw: true });
  expect(grade, 200);
  assert(JSON.parse(grade.body).grade === 5, grade.body);
  assert(!grade.body.includes("Jurado Externo"), "la nota publicada nombra al jurado");
});
await check("el jurado marca que no asistieron: la corrección queda en el rastro con la nota anterior", async () => {
  const corrected = expect(await api("PUT", myEvaluation(), { token: tok.jurado, json: { absent: true } }), 200);
  assert(corrected.grade === 0, JSON.stringify(corrected).slice(0, 200));
  const trail = expect(await api("GET", "/admin/audit?action=EVALUATION_EDITED", { token: tok.admin }), 200);
  const row = trail.items.find((r) => r.targetLabel === project.title);
  assert(row?.detail.includes("5.0 → no asistió (0.0)") && row.detail.includes("ya publicadas"), JSON.stringify(row));
  assert(row.actorEmail === creds.jurado.email, `actor ${row.actorEmail}`);
});
await check("el ADMIN ve el estado de la instalación; MACONDOLAB no", async () => {
  const status = expect(await api("GET", "/admin/status", { token: tok.admin }), 200);
  assert(status.version && status.disk.totalBytes > 0 && status.disk.low === false, JSON.stringify(status));
  expect(await api("GET", "/admin/status", { token: tok.macondolab }), 403);
});
await check("la salud para el monitoreo responde sin sesión y sin detalles", async () => {
  const res = await fetch(new URL("../../salud", API + "/"));
  const body = await res.text();
  assert(res.status === 200 && JSON.parse(body).status === "UP", `${res.status} ${body}`);
  assert(!body.includes("components") && !body.includes("diskSpace"), body);
});
await check("MACONDOLAB no lee el rastro de auditoría → 403", async () =>
  expect(await api("GET", "/admin/audit", { token: tok.macondolab }), 403));
await check("MACONDOLAB oculta las notas y quita a los jurados", async () => {
  expect(await api("DELETE", publication(), { token: tok.macondolab }), 200);
  expect(await api("GET", `/projects/${project.id}/grade`, { token: tok.estudiante1 }), 204);
  expect(await api("DELETE", `${jurors()}/${creds.jurado.id}`, { token: tok.macondolab }), 204);
  expect(await api("DELETE", `${jurors()}/${creds.docente2.id}`, { token: tok.macondolab }), 204);
});

section("8. Limpieza (también prueba borrar)");
await check("un integrante no se sale por su cuenta → 403", async () =>
  expect(await api("DELETE", `/projects/${project.id}/members/${creds.estudiante4.id}`, { token: tok.estudiante4 }), 403));
await check("líder saca a estudiante4", async () =>
  expect(await api("DELETE", `/projects/${project.id}/members/${creds.estudiante4.id}`, { token: tok.estudiante1 }), 200, 204));
await check("un no-líder no borra el proyecto → 403", async () =>
  expect(await api("DELETE", `/projects/${project.id}`, { token: tok.estudiante2 }), 403));
await check("líder borra el proyecto; luego 404", async () => {
  expect(await api("DELETE", `/projects/${project.id}`, { token: tok.estudiante1 }), 200, 204);
  expect(await api("GET", `/projects/${project.id}`, { token: tok.estudiante1 }), 404);
});
await check("el póster borrado ya no se descarga", async () =>
  expect(await api("GET", `/files/${posterFileId}`, { token: tok.macondolab, raw: true }), 404));

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} OK`);
if (failed.length) {
  console.log("Fallas:");
  for (const f of failed) console.log(`  - ${f.name}: ${f.error}`);
  process.exit(1);
}
