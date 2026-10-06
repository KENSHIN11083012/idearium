import { creds, deleteUsersByEmail, expect, field, loginAs, path, stamp, test, uiLogout } from "../fixtures.mjs";

// La cuenta registrada aquí queda sin proyectos ni equipo: el administrador la puede eliminar.

test.afterAll(() => deleteUsersByEmail(/^qa\.e2e\.reg\./));

test("registro: valida en el cliente, crea la cuenta y completa su perfil en el primer ingreso", async ({ page }) => {
  const email = `qa.e2e.reg.${stamp()}@unisimon.edu.co`;
  await page.goto(path("/registro"));
  await expect(page.getByRole("heading", { name: "Crea tu cuenta" })).toBeVisible();

  const correo = page.getByLabel("Correo institucional");
  await correo.fill("alguien@gmail.com");
  await correo.blur();
  await expect(correo).toHaveAttribute("aria-invalid", "true");
  await correo.fill(email);

  const clave = page.getByLabel(field("Contraseña"));
  await clave.fill("abcdefgh");
  await clave.blur();
  await expect(page.getByRole("list", { name: "Requisitos de la contraseña" })).toBeVisible();
  await page.getByRole("button", { name: "Crear cuenta" }).click();
  await expect(page).toHaveURL(/registro/); // la clave débil y sin consentimiento no deja enviar

  await clave.fill("Valida#2026");
  await page.getByLabel("Confirmar contraseña").fill("Valida#2026");
  await page.getByLabel(/Autorizo a la Universidad/).check();
  await page.getByRole("button", { name: "Crear cuenta" }).click();

  await expect(page).toHaveURL(/iniciar-sesion/);
  await expect(page.getByText("Tu cuenta fue creada. Ya puedes iniciar sesión.")).toBeVisible();
  await expect(page.getByLabel("Correo institucional")).toHaveValue(email);

  // La cuenta nace sin nombre ni facultad: el primer ingreso los pide antes de dejar usar la plataforma.
  await page.getByLabel(field("Contraseña")).fill("Valida#2026");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/primer-ingreso/);
  await page.goto(path("/mis-proyectos"));
  await expect(page).toHaveURL(/primer-ingreso/);

  await page.getByLabel("Nombres").fill("Registro");
  await page.getByLabel("Apellidos").fill("QA E2E");
  await page.getByLabel("Sede").selectOption({ index: 1 });
  await page.getByLabel("Facultad").selectOption({ label: "Ingenierías" });
  await page.getByRole("button", { name: "Guardar y continuar" }).click();
  await expect(page).not.toHaveURL(/primer-ingreso/);
  await expect(page.getByRole("button", { name: "Abrir menú de la cuenta" })).toBeVisible();

  await page.goto(path("/mis-proyectos"));
  await expect(page.getByRole("heading", { name: "Mis proyectos" })).toBeVisible();
});

test("registro con correo ya usado muestra el error en el campo", async ({ page }) => {
  await page.goto(path("/registro"));
  await page.getByLabel("Correo institucional").fill(creds.estudiante1.email);
  await page.getByLabel(field("Contraseña")).fill("Valida#2026");
  await page.getByLabel("Confirmar contraseña").fill("Valida#2026");
  await page.getByLabel(/Autorizo a la Universidad/).check();
  await page.getByRole("button", { name: "Crear cuenta" }).click();
  await expect(page.getByLabel("Correo institucional")).toHaveAttribute("aria-invalid", "true");
  await expect(page).toHaveURL(/registro/);
});

test("login con clave errada muestra un error y no entra", async ({ page }) => {
  await page.goto(path("/iniciar-sesion"));
  await page.getByLabel("Correo institucional").fill(creds.estudiante1.email);
  await page.getByLabel(field("Contraseña")).fill("NoEsLaClave#1");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page.getByRole("alert").first()).toBeVisible();
  await expect(page).toHaveURL(/iniciar-sesion/);
});

test("logout cierra la sesión y las rutas protegidas vuelven a pedir login", async ({ page }) => {
  await loginAs(page, "estudiante1");
  await page.goto(path("/mis-proyectos"));
  await expect(page.getByRole("heading", { name: "Mis proyectos" })).toBeVisible();
  await uiLogout(page);
  await expect(page).toHaveURL(/iniciar-sesion/);
  await page.goto(path("/mis-proyectos"));
  await expect(page).toHaveURL(/iniciar-sesion/);
});

test("un token inválido en el navegador termina en el login", async ({ page }) => {
  await page.goto(path("/"));
  await page.evaluate(() => {
    localStorage.setItem("token", "xxx.yyy.zzz");
    localStorage.setItem("user", JSON.stringify({ id: 1, email: "x@unisimon.edu.co", firstName: "X", lastName: "Y" }));
  });
  await page.goto(path("/mis-proyectos"));
  await expect(page).toHaveURL(/iniciar-sesion/);
});
