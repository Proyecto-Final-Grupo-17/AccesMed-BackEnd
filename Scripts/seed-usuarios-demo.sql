-- ============================================================================
-- AccesMed — Seed de usuarios de prueba (Admin y Médico)
-- ============================================================================
-- Para qué es: probar el login y el panel con los tres roles del sistema. El
-- Scripts/seed-superadmin.sql deja solo el SuperAdmin; este agrega un usuario
-- con rol "Admin" y otro con rol "Medico" para comparar qué ve cada uno
-- (el menú del panel cambia según el rol que devuelve GET /Auth/Me).
--
-- Por qué por SQL y no por la app: POST /Usuario/AsignarMedico|AsignarAdmin
-- crea el usuario pendiente de activación y manda un mail con un link a
-- /activar-cuenta?token=... — hoy ese flujo no se puede completar en local
-- (hace falta SMTP real y el front todavía no tiene esa pantalla). Mismo
-- criterio de bootstrap que seed-superadmin.sql.
--
-- Qué carga:
--   1) Admin "Ana Torres" + su Usuario + rol de sistema "Admin".
--   2) Usuario para el médico Juan Pérez (MP-10234, Cardiología), ya sembrado
--      por seed-datos-demo.sql, + rol de sistema "Medico".
--   Las contraseñas van hasheadas con pgcrypto/bcrypt, listas para loguear sin
--   pasar por RestablecerContrasena.
--
-- Uso (perfil dev, ver docker/dev/docker-compose.yml):
--   psql "postgresql://accesmed:accesmed@localhost:5432/accesmed" -f Scripts/seed-usuarios-demo.sql
--
-- Precondiciones:
--   - Schema ya migrado por Liquibase (arrancar la app una vez) — necesita que
--     los roles "Admin" y "Medico" ya existan (20260731215500-Rol.xml).
--   - Scripts/seed-datos-demo.sql ya corrido — el usuario médico se engancha al
--     Medico con id 20000000-0000-4000-8000-000000000001.
--   Pensado para correrse una sola vez: si se corre de nuevo, los UNIQUE de
--   dni/mail y el UNIQUE de usuario.medico_id rechazan la reinserción (mismo
--   criterio que los otros seeds).
--
-- Credenciales resultantes:
--   Admin   → mail: ana.torres@accesmed.com    | password: accesmed2026
--   Médico  → mail: juan.perez@accesmed.com    | password: accesmed2026
-- ============================================================================

BEGIN;

-- ====================================================================
-- 1) Usuario con rol Admin (Admin nuevo + Usuario + UsuarioRol)
-- ====================================================================
WITH nuevo_admin AS (
    INSERT INTO admin (nombre, apellido, dni, email, created_at, created_by)
    VALUES ('Ana', 'Torres', '00000002', 'ana.torres@accesmed.com', now(), 'seed-script')
    RETURNING id
), nuevo_usuario AS (
    INSERT INTO usuario (mail, password_hash, admin_id, created_at, created_by)
    SELECT 'ana.torres@accesmed.com', crypt('accesmed2026', gen_salt('bf')), nuevo_admin.id, now(), 'seed-script'
    FROM nuevo_admin
    RETURNING id
)
INSERT INTO usuario_rol (fecha_inicio_vigencia, usuario_id, rol_id, created_at, created_by)
SELECT now(), nuevo_usuario.id, rol.id, now(), 'seed-script'
FROM nuevo_usuario, rol
WHERE rol.nombre = 'Admin' AND rol.deleted_at IS NULL;

-- ====================================================================
-- 2) Usuario con rol Medico (sobre un Medico ya existente + UsuarioRol)
-- ====================================================================
-- El Medico ya existe (seed-datos-demo.sql): acá solo se le da acceso.
-- usuario.medico_id es UNIQUE y ck_usuario_medico_xor_admin exige que venga
-- medico_id XOR admin_id — por eso admin_id queda en NULL.
WITH nuevo_usuario AS (
    INSERT INTO usuario (mail, password_hash, medico_id, created_at, created_by)
    SELECT 'juan.perez@accesmed.com', crypt('accesmed2026', gen_salt('bf')), medico.id, now(), 'seed-script'
    FROM medico
    WHERE medico.id = '20000000-0000-4000-8000-000000000001' AND medico.deleted_at IS NULL
    RETURNING id
)
INSERT INTO usuario_rol (fecha_inicio_vigencia, usuario_id, rol_id, created_at, created_by)
SELECT now(), nuevo_usuario.id, rol.id, now(), 'seed-script'
FROM nuevo_usuario, rol
WHERE rol.nombre = 'Medico' AND rol.deleted_at IS NULL;

COMMIT;
