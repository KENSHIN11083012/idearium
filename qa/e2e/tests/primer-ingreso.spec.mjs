import { deleteUsersByEmail, expect, field, loginAs, path, stamp, test, uiLogin, uiLogout } from "../fixtures.mjs";

test.afterAll(() => deleteUsersByEmail(/^qa\.e2e\.jurado\./));

test("el ADMIN crea un jurado externo, este completa el primer ingreso y el ADMIN lo elimina", async ({ page }) => {
  const email = `qa.e2e.jurado.${stamp()}@example.com`;
  const temporal = "Temporal#2026";
  const definitiva = "Definitiva#2026";

  // 1. Alta desde Usuarios.
  await loginAs(page, "admin");
  await expect(page).toHaveURL(/admin\/usuarios/);
  await page.getByRole("button", { name: "Nueva cuenta" }).click();
  const dialog = page.getByRole("dialog", { name: "Nueva cuenta" });
  await dialog.getByLabel("Rol").selectOption({ label: "Jurado" });
  await dialog.getByLabel("Nombres").fill("Jurado");
  await dialog.getByLabel("Apellidos").fill("QA E2E");
  await dialog.getByLabel("Correo").fill(email);
  await dialog.getByLabel("Contraseña temporal").fill(temporal);
  await dialog.getByRole("button", { name: "Crear cuenta" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("row").filter({ hasText: email })).toBeVisible();
  await uiLogout(page);

  // 2. Primer ingreso: dos pasos.
  await uiLogin(page, email, temporal);
  await expect(page).toHaveURL(/primer-ingreso/);
  await expect(page.getByText("Primer ingreso · Paso 1 de 2")).toBeVisible();
  await page.getByLabel("Contraseña temporal").fill(temporal);
  await page.getByLabel(field("Nueva contraseña")).fill(definitiva);
  await page.getByLabel("Confirmar nueva contraseña").fill(definitiva);
  await page.getByRole("button", { name: "Guardar y continuar" }).click();

  await expect(page.getByText("Primer ingreso · Paso 2 de 2")).toBeVisible();
  await page.getByRole("button", { name: "Aceptar y continuar" }).click();
  await expect(page.getByRole("alert")).toBeVisible(); // sin marcar la autorización no avanza
  await page.getByLabel(/Autorizo a la Universidad/).check();
  await page.getByRole("button", { name: "Aceptar y continuar" }).click();
  await expect(page).not.toHaveURL(/primer-ingreso/);
  await uiLogout(page);

  // 3. La temporal ya no sirve; la definitiva sí y no vuelve a pedir el primer ingreso.
  await page.goto(path("/iniciar-sesion"));
  await page.getByLabel("Correo institucional").fill(email);
  await page.getByLabel(field("Contraseña")).fill(temporal);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/iniciar-sesion/);
  await uiLogin(page, email, definitiva);
  await expect(page).not.toHaveURL(/primer-ingreso/);
  await uiLogout(page);

  // 4. El ADMIN la elimina desde la lista.
  await loginAs(page, "admin");
  await page.getByLabel("Buscar usuarios").fill(email);
  const row = page.getByRole("row").filter({ hasText: email });
  await row.getByRole("button").last().click();
  await page.getByRole("menuitem", { name: /Eliminar/ }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Eliminar" }).click();
  await expect(page.getByRole("row").filter({ hasText: email })).toHaveCount(0);
});
