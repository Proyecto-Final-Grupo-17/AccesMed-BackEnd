-- ============================================================================
-- AccesMed — Seed del primer usuario SuperAdmin (bootstrap)
-- ============================================================================
-- Para qué es: el huevo y la gallina del login. AdminController.createAdmin exige
-- USER_ALTA, que nadie tiene sin loguearse antes — así que el primer SuperAdmin
-- se siembra directo por SQL, una única vez, en una base dev recién migrada.
-- A partir de acá, cualquier Admin/SuperAdmin adicional se crea desde la app.
--
-- Qué carga: un Admin, su Usuario (contraseña ya hasheada con pgcrypto/bcrypt,
-- lista para loguear sin pasar por RestablecerContrasena) y la asignación del
-- rol de sistema "SuperAdmin" (sembrado por Liquibase junto con Medico/Admin,
-- ver 20260731215500-Rol.xml).
--
-- Uso (perfil dev, ver docker/dev/docker-compose.yml):
--   psql "postgresql://accesmed:accesmed@localhost:5432/accesmed" -f Scripts/seed-superadmin.sql
--
-- Precondición: schema ya migrado por Liquibase (arrancar la app una vez, o
-- correr las migraciones) — necesita que el rol "SuperAdmin" ya exista. Pensado
-- para correrse una sola vez: si se corre de nuevo, el UNIQUE de mail/dni
-- rechaza la reinserción (mismo criterio que Scripts/seed-datos-demo.sql).
--
-- Credenciales resultantes:
--   mail:     proyectofinalgrupo17@gmail.com
--   password: accesmed2026
-- ============================================================================

BEGIN;

WITH nuevo_admin AS (
    INSERT INTO admin (nombre, apellido, dni, email, created_at, created_by)
    VALUES ('Super', 'Admin', '00000001', 'proyectofinalgrupo17@gmail.com', now(), 'seed-script')
    RETURNING id
), nuevo_usuario AS (
    INSERT INTO usuario (mail, password_hash, admin_id, created_at, created_by)
    SELECT 'proyectofinalgrupo17@gmail.com', crypt('accesmed2026', gen_salt('bf')), nuevo_admin.id, now(), 'seed-script'
    FROM nuevo_admin
    RETURNING id
)
INSERT INTO usuario_rol (fecha_inicio_vigencia, usuario_id, rol_id, created_at, created_by)
SELECT now(), nuevo_usuario.id, rol.id, now(), 'seed-script'
FROM nuevo_usuario, rol
WHERE rol.nombre = 'SuperAdmin' AND rol.deleted_at IS NULL;

COMMIT;
