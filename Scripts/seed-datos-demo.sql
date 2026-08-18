-- ============================================================================
-- AccesMed — Seed de datos de demo (camino feliz)
-- ============================================================================
-- Qué carga:
--   Especialidad, Medico (con especialidad asignada), Prestacion (publicada,
--   por especialidad), MedicoPrestacion (médico ↔ prestación de su misma
--   especialidad, vigente), ObraSocial, Plan (publicado), ObraSocialPlanPrestacion
--   (cobertura de cada plan sobre las prestaciones).
--
-- Alcance deliberado: solo camino feliz. Nada queda NO_PUBLICADO/DESHABILITADO
-- ni con baja lógica (deleted_at); todos los tramos de historico_estado_* quedan
-- vigentes (fecha_hora_fin IS NULL) y todas las vigencias de medico_prestacion
-- quedan abiertas (fecha_fin_vigencia IS NULL).
--
-- Uso (perfil dev, ver docker/dev/docker-compose.yml):
--   psql "postgresql://accesmed:accesmed@localhost:5432/accesmed" -f Scripts/seed-datos-demo.sql
--
-- Precondición: el schema ya migrado por Liquibase (arrancar la app una vez, o
-- correr las migraciones). Pensado para una base dev vacía de estas tablas —
-- si se corre dos veces, los UNIQUE de codigo/nombre van a rechazar la reinserción.
-- ============================================================================

BEGIN;

-- ====================================================================
-- 1) Especialidad
-- ====================================================================
INSERT INTO especialidad (id, codigo, nombre, created_at, created_by) VALUES
    ('10000000-0000-0000-0000-000000000001', 'CARD', 'Cardiología',      now(), 'seed-script'),
    ('10000000-0000-0000-0000-000000000002', 'CLIN', 'Clínica Médica',  now(), 'seed-script'),
    ('10000000-0000-0000-0000-000000000003', 'PED',  'Pediatría',       now(), 'seed-script'),
    ('10000000-0000-0000-0000-000000000004', 'DERM', 'Dermatología',    now(), 'seed-script'),
    ('10000000-0000-0000-0000-000000000005', 'TRAU', 'Traumatología',   now(), 'seed-script');

-- ====================================================================
-- 2) Medico (cada uno con una única especialidad, vía especialidad_id)
-- ====================================================================
INSERT INTO medico (id, matricula, dni, nombre, apellido, email, numero_telefono, especialidad_id, created_at, created_by) VALUES
    ('20000000-0000-0000-0000-000000000001', 'MP-10234', '30123456', 'Juan',       'Pérez',   'juan.perez@accesmed.com',      '+54 11 4444-1001', '10000000-0000-0000-0000-000000000001', now(), 'seed-script'),
    ('20000000-0000-0000-0000-000000000002', 'MP-10456', '29876543', 'Lucía',      'Gómez',   'lucia.gomez@accesmed.com',     '+54 11 4444-1002', '10000000-0000-0000-0000-000000000002', now(), 'seed-script'),
    ('20000000-0000-0000-0000-000000000003', 'MP-10789', '28456123', 'Martín',     'Ibarra',  'martin.ibarra@accesmed.com',   '+54 11 4444-1003', '10000000-0000-0000-0000-000000000002', now(), 'seed-script'),
    ('20000000-0000-0000-0000-000000000004', 'MP-11023', '31567890', 'Sofía',      'Ramírez', 'sofia.ramirez@accesmed.com',   '+54 11 4444-1004', '10000000-0000-0000-0000-000000000003', now(), 'seed-script'),
    ('20000000-0000-0000-0000-000000000005', 'MP-11345', '27890456', 'Federico',   'Suárez',  'federico.suarez@accesmed.com', '+54 11 4444-1005', '10000000-0000-0000-0000-000000000004', now(), 'seed-script'),
    ('20000000-0000-0000-0000-000000000006', 'MP-11678', '32234567', 'Valentina',  'Castro',  'valentina.castro@accesmed.com','+54 11 4444-1006', '10000000-0000-0000-0000-000000000005', now(), 'seed-script');

-- ====================================================================
-- 3) Prestacion (una por cada especialidad como consulta + procedimientos
--    puntuales en Cardiología/Dermatología/Traumatología)
--    Dos perfiles de tolerancias, ambos cumplen la cadena decreciente
--    solicitud >= validacion >= reprogramacion >= confirmacion >= cancelacion >= 0
--    y confirmacion < recordatorio <= solicitud.
-- ====================================================================
INSERT INTO prestacion (
    id, codigo, nombre,
    duracion_minima, duracion_maxima,
    tiempo_tolerancia_solicitud, tiempo_tolerancia_validacion, tiempo_tolerancia_reprogramacion,
    tiempo_tolerancia_confirmacion, tiempo_tolerancia_cancelacion, tiempo_tolerancia_anuncio,
    tiempo_recordatorio_confirmacion,
    especialidad_id, created_at, created_by
) VALUES
    -- Perfil "consulta": 15-30 min, tolerancias en días/horas
    ('30000000-0000-0000-0000-000000000001', 'CARD-CONS', 'Consulta Cardiológica',
     interval '15 minutes', interval '30 minutes',
     interval '3 days', interval '2 days', interval '1 day', interval '12 hours', interval '2 hours', interval '1 day',
     interval '18 hours',
     '10000000-0000-0000-0000-000000000001', now(), 'seed-script'),

    ('30000000-0000-0000-0000-000000000003', 'CLIN-CONS', 'Consulta Clínica Médica',
     interval '15 minutes', interval '30 minutes',
     interval '3 days', interval '2 days', interval '1 day', interval '12 hours', interval '2 hours', interval '1 day',
     interval '18 hours',
     '10000000-0000-0000-0000-000000000002', now(), 'seed-script'),

    ('30000000-0000-0000-0000-000000000004', 'PED-CONS', 'Consulta Pediátrica',
     interval '15 minutes', interval '30 minutes',
     interval '3 days', interval '2 days', interval '1 day', interval '12 hours', interval '2 hours', interval '1 day',
     interval '18 hours',
     '10000000-0000-0000-0000-000000000003', now(), 'seed-script'),

    ('30000000-0000-0000-0000-000000000005', 'DERM-CONS', 'Consulta Dermatológica',
     interval '15 minutes', interval '30 minutes',
     interval '3 days', interval '2 days', interval '1 day', interval '12 hours', interval '2 hours', interval '1 day',
     interval '18 hours',
     '10000000-0000-0000-0000-000000000004', now(), 'seed-script'),

    ('30000000-0000-0000-0000-000000000007', 'TRAU-CONS', 'Consulta Traumatológica',
     interval '15 minutes', interval '30 minutes',
     interval '3 days', interval '2 days', interval '1 day', interval '12 hours', interval '2 hours', interval '1 day',
     interval '18 hours',
     '10000000-0000-0000-0000-000000000005', now(), 'seed-script'),

    -- Perfil "procedimiento": 30-45 min, tolerancias más amplias
    ('30000000-0000-0000-0000-000000000002', 'CARD-ERGO', 'Ergometría',
     interval '30 minutes', interval '45 minutes',
     interval '7 days', interval '3 days', interval '2 days', interval '1 day', interval '6 hours', interval '2 days',
     interval '36 hours',
     '10000000-0000-0000-0000-000000000001', now(), 'seed-script'),

    ('30000000-0000-0000-0000-000000000006', 'DERM-CRIO', 'Crioterapia',
     interval '30 minutes', interval '45 minutes',
     interval '7 days', interval '3 days', interval '2 days', interval '1 day', interval '6 hours', interval '2 days',
     interval '36 hours',
     '10000000-0000-0000-0000-000000000004', now(), 'seed-script'),

    ('30000000-0000-0000-0000-000000000008', 'TRAU-INFI', 'Infiltración',
     interval '30 minutes', interval '45 minutes',
     interval '7 days', interval '3 days', interval '2 days', interval '1 day', interval '6 hours', interval '2 days',
     interval '36 hours',
     '10000000-0000-0000-0000-000000000005', now(), 'seed-script');

-- ====================================================================
-- 4) HistoricoEstadoPrestacion — un único tramo PUBLICADA y vigente por
--    cada prestación (fecha_hora_fin NULL = estado actual).
-- ====================================================================
INSERT INTO historico_estado_prestacion (id, fecha_hora_inicio, fecha_hora_fin, estado, prestacion_id, created_at, created_by) VALUES
    ('80000000-0000-0000-0000-000000000001', now() - interval '180 days', NULL, 'PUBLICADA', '30000000-0000-0000-0000-000000000001', now(), 'seed-script'),
    ('80000000-0000-0000-0000-000000000002', now() - interval '180 days', NULL, 'PUBLICADA', '30000000-0000-0000-0000-000000000002', now(), 'seed-script'),
    ('80000000-0000-0000-0000-000000000003', now() - interval '180 days', NULL, 'PUBLICADA', '30000000-0000-0000-0000-000000000003', now(), 'seed-script'),
    ('80000000-0000-0000-0000-000000000004', now() - interval '180 days', NULL, 'PUBLICADA', '30000000-0000-0000-0000-000000000004', now(), 'seed-script'),
    ('80000000-0000-0000-0000-000000000005', now() - interval '180 days', NULL, 'PUBLICADA', '30000000-0000-0000-0000-000000000005', now(), 'seed-script'),
    ('80000000-0000-0000-0000-000000000006', now() - interval '180 days', NULL, 'PUBLICADA', '30000000-0000-0000-0000-000000000006', now(), 'seed-script'),
    ('80000000-0000-0000-0000-000000000007', now() - interval '180 days', NULL, 'PUBLICADA', '30000000-0000-0000-0000-000000000007', now(), 'seed-script'),
    ('80000000-0000-0000-0000-000000000008', now() - interval '180 days', NULL, 'PUBLICADA', '30000000-0000-0000-0000-000000000008', now(), 'seed-script');

-- ====================================================================
-- 5) MedicoPrestacion — asignación médico ↔ prestación de su misma
--    especialidad (regla de negocio de MedicoPrestacionDomainService),
--    todas vigentes (fecha_fin_vigencia NULL) y con atención particular.
-- ====================================================================
INSERT INTO medico_prestacion (id, atiende_particular, precio_particular, fecha_inicio_vigencia, fecha_fin_vigencia, medico_id, prestacion_id, created_at, created_by) VALUES
    ('40000000-0000-0000-0000-000000000001', true, 18000.00, now() - interval '90 days', NULL, '20000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', now(), 'seed-script'), -- Juan Pérez → Consulta Cardiológica
    ('40000000-0000-0000-0000-000000000002', true, 35000.00, now() - interval '90 days', NULL, '20000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002', now(), 'seed-script'), -- Juan Pérez → Ergometría
    ('40000000-0000-0000-0000-000000000003', true, 16000.00, now() - interval '90 days', NULL, '20000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000003', now(), 'seed-script'), -- Lucía Gómez → Consulta Clínica Médica
    ('40000000-0000-0000-0000-000000000004', true, 16000.00, now() - interval '90 days', NULL, '20000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000003', now(), 'seed-script'), -- Martín Ibarra → Consulta Clínica Médica
    ('40000000-0000-0000-0000-000000000005', true, 17000.00, now() - interval '90 days', NULL, '20000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000004', now(), 'seed-script'), -- Sofía Ramírez → Consulta Pediátrica
    ('40000000-0000-0000-0000-000000000006', true, 17500.00, now() - interval '90 days', NULL, '20000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000005', now(), 'seed-script'), -- Federico Suárez → Consulta Dermatológica
    ('40000000-0000-0000-0000-000000000007', true, 32000.00, now() - interval '90 days', NULL, '20000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000006', now(), 'seed-script'), -- Federico Suárez → Crioterapia
    ('40000000-0000-0000-0000-000000000008', true, 18500.00, now() - interval '90 days', NULL, '20000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000007', now(), 'seed-script'), -- Valentina Castro → Consulta Traumatológica
    ('40000000-0000-0000-0000-000000000009', true, 40000.00, now() - interval '90 days', NULL, '20000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000008', now(), 'seed-script'); -- Valentina Castro → Infiltración

-- ====================================================================
-- 6) ObraSocial
-- ====================================================================
INSERT INTO obra_social (id, codigo, nombre, razon_social, created_at, created_by) VALUES
    ('50000000-0000-0000-0000-000000000001', 'OSDE', 'OSDE',           'Organización de Servicios Directos Empresarios', now(), 'seed-script'),
    ('50000000-0000-0000-0000-000000000002', 'SMG',  'Swiss Medical',  'Swiss Medical Group S.A.',                       now(), 'seed-script'),
    ('50000000-0000-0000-0000-000000000003', 'GAL',  'Galeno',         'Galeno Argentina S.A.',                          now(), 'seed-script');

-- ====================================================================
-- 7) Plan (dos por obra social)
-- ====================================================================
INSERT INTO plan (id, codigo, nombre, obra_social_id, created_at, created_by) VALUES
    ('60000000-0000-0000-0000-000000000001', 'OSDE-210',  'OSDE 210',      '50000000-0000-0000-0000-000000000001', now(), 'seed-script'),
    ('60000000-0000-0000-0000-000000000002', 'OSDE-310',  'OSDE 310',      '50000000-0000-0000-0000-000000000001', now(), 'seed-script'),
    ('60000000-0000-0000-0000-000000000003', 'SMG-20',    'SMG 20',        '50000000-0000-0000-0000-000000000002', now(), 'seed-script'),
    ('60000000-0000-0000-0000-000000000004', 'SMG-40',    'SMG 40',        '50000000-0000-0000-0000-000000000002', now(), 'seed-script'),
    ('60000000-0000-0000-0000-000000000005', 'GAL-PLATA', 'Galeno Plata',  '50000000-0000-0000-0000-000000000003', now(), 'seed-script'),
    ('60000000-0000-0000-0000-000000000006', 'GAL-ORO',   'Galeno Oro',    '50000000-0000-0000-0000-000000000003', now(), 'seed-script');

-- ====================================================================
-- 8) HistoricoEstadoPlan — un único tramo PUBLICADO y vigente por cada plan.
-- ====================================================================
INSERT INTO historico_estado_plan (id, fecha_hora_inicio, fecha_hora_fin, estado, plan_id, created_at, created_by) VALUES
    ('90000000-0000-0000-0000-000000000001', now() - interval '180 days', NULL, 'PUBLICADO', '60000000-0000-0000-0000-000000000001', now(), 'seed-script'),
    ('90000000-0000-0000-0000-000000000002', now() - interval '180 days', NULL, 'PUBLICADO', '60000000-0000-0000-0000-000000000002', now(), 'seed-script'),
    ('90000000-0000-0000-0000-000000000003', now() - interval '180 days', NULL, 'PUBLICADO', '60000000-0000-0000-0000-000000000003', now(), 'seed-script'),
    ('90000000-0000-0000-0000-000000000004', now() - interval '180 days', NULL, 'PUBLICADO', '60000000-0000-0000-0000-000000000004', now(), 'seed-script'),
    ('90000000-0000-0000-0000-000000000005', now() - interval '180 days', NULL, 'PUBLICADO', '60000000-0000-0000-0000-000000000005', now(), 'seed-script'),
    ('90000000-0000-0000-0000-000000000006', now() - interval '180 days', NULL, 'PUBLICADO', '60000000-0000-0000-0000-000000000006', now(), 'seed-script');

-- ====================================================================
-- 9) ObraSocialPlanPrestacion — cobertura de cada plan sobre las 8
--    prestaciones. Regla aplicada (camino feliz, coherente con el CHECK
--    ck_obra_social_plan_prestacion_coherencia):
--      · Plan "básico" (210/20/Plata):  PORCENTUAL 70% en todas las prestaciones.
--      · Plan "superior" (310/40/Oro):  TOTAL en consultas, CARGO_FIJO en
--        procedimientos (Ergometría, Crioterapia, Infiltración).
-- ====================================================================
INSERT INTO obra_social_plan_prestacion (id, modalidad_cobertura, porcentaje_cobertura, coseguro, plan_id, prestacion_id, created_at, created_by) VALUES
    -- OSDE 210 (básico) — PORCENTUAL 70% en las 8 prestaciones
    ('70000000-0000-0000-0000-000000000001', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000002', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000003', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000003', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000004', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000004', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000005', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000005', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000006', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000006', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000007', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000007', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000008', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000008', now(), 'seed-script'),

    -- OSDE 310 (superior) — TOTAL en consultas, CARGO_FIJO en procedimientos
    ('70000000-0000-0000-0000-000000000009', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000010', 'CARGO_FIJO', 0.00,   1500.00, '60000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000002', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000011', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000003', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000012', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000004', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000013', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000005', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000014', 'CARGO_FIJO', 0.00,   1500.00, '60000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000006', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000015', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000007', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000016', 'CARGO_FIJO', 0.00,   1500.00, '60000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000008', now(), 'seed-script'),

    -- SMG 20 (básico) — PORCENTUAL 70% en las 8 prestaciones
    ('70000000-0000-0000-0000-000000000017', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000001', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000018', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000002', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000019', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000003', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000020', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000004', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000021', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000005', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000022', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000006', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000023', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000007', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000024', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000008', now(), 'seed-script'),

    -- SMG 40 (superior) — TOTAL en consultas, CARGO_FIJO en procedimientos
    ('70000000-0000-0000-0000-000000000025', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000001', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000026', 'CARGO_FIJO', 0.00,   1500.00, '60000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000002', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000027', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000003', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000028', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000004', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000029', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000005', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000030', 'CARGO_FIJO', 0.00,   1500.00, '60000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000006', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000031', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000007', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000032', 'CARGO_FIJO', 0.00,   1500.00, '60000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000008', now(), 'seed-script'),

    -- Galeno Plata (básico) — PORCENTUAL 70% en las 8 prestaciones
    ('70000000-0000-0000-0000-000000000033', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000001', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000034', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000002', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000035', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000003', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000036', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000004', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000037', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000005', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000038', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000006', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000039', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000007', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000040', 'PORCENTUAL', 70.00, 0.00, '60000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000008', now(), 'seed-script'),

    -- Galeno Oro (superior) — TOTAL en consultas, CARGO_FIJO en procedimientos
    ('70000000-0000-0000-0000-000000000041', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000001', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000042', 'CARGO_FIJO', 0.00,   1500.00, '60000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000002', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000043', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000003', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000044', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000004', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000045', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000005', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000046', 'CARGO_FIJO', 0.00,   1500.00, '60000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000006', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000047', 'TOTAL',      100.00, 0.00,    '60000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000007', now(), 'seed-script'),
    ('70000000-0000-0000-0000-000000000048', 'CARGO_FIJO', 0.00,   1500.00, '60000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000008', now(), 'seed-script');

COMMIT;
