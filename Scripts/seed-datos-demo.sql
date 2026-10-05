-- ============================================================================
-- AccesMed — Seed de datos de demo (camino feliz)
-- ============================================================================
-- Único script de carga de datos de demo. Se organiza en tres grupos:
--
--   PARTE 1 — DATOS MAESTROS
--     Especialidades, tipos de indicación, prestaciones (5 por especialidad, la
--     primera es siempre la consulta), indicaciones, obras sociales, planes
--     (5 por obra social) y coberturas.
--   PARTE 2 — PERSONAL
--     Médicos, asignación médico ↔ prestación, administradores, usuarios y
--     pacientes.
--   PARTE 3 — AGENDAS
--     Un período de agenda por médico, con sus horarios (slots) ya expandidos.
--
-- No carga turnos. El orden de las partes respeta las FK (las agendas necesitan
-- médicos y prestaciones ya cargados).
--
-- Alcance deliberado: solo camino feliz. Nada queda NO_PUBLICADA / NO_PUBLICADO /
-- DESHABILITADA ni con baja lógica (deleted_at); todos los tramos de
-- historico_estado_* quedan vigentes (fecha_hora_fin IS NULL) y todas las
-- vigencias quedan abiertas (fecha_fin_vigencia IS NULL).
--
-- Reglas de negocio que el script respeta (Docs/Dominio/dominio-reglas-validaciones.md):
--   · Un médico tiene una sola especialidad y solo se le asignan prestaciones de
--     esa especialidad, todas Publicadas y vigentes.
--   · Cadena de tolerancias de Prestacion:
--       solicitud >= validacion >= reprogramacion >= confirmacion >= cancelacion >= 0
--       confirmacion < recordatorio <= solicitud
--   · La consulta de cada especialidad no tiene indicaciones. Las demás sí, y
--     algunas exigen validación (requiere_validacion).
--   · Todo plan es Publicado y cubre siempre la consulta de cada especialidad.
--   · Cada slot cae dentro del horario de atención de la clínica, su duración
--     divide exacto al bloque y está entre la duración mínima y máxima de la
--     prestación; los bloques de un mismo día no se superponen.
--   · fecha_limite_reserva = inicio del slot (en la zona horaria de la clínica)
--     − tiempo_tolerancia_solicitud.
--   · Ningún paciente tiene dos veces el mismo plan; el teléfono y el DNI son
--     únicos.
--
-- Contraseñas: no hay ninguna escrita en este archivo, solo hashes bcrypt ya
-- calculados (compatibles con el BCryptPasswordEncoder de la app; ver 2.4).
-- El SuperAdmin NO se siembra acá: lo crea la app al arrancar
-- (SuperAdminInicializador) con ACCESMED_SUPERADMIN_MAIL y
-- ACCESMED_SUPERADMIN_PASSWORD del entorno / .env.
--
-- Datos para probar:
--   · Reprogramación: Cardiología, Clínica Médica, Dermatología y Ginecología
--     tienen dos médicos con exactamente las mismas prestaciones y horarios
--     complementarios (uno de mañana, otro de tarde).
--   · Schedulers: "Prestación Demo" con tolerancias de minutos (ver 1.3), su
--     médico demo (sin usuario) y agenda de 10 minutos durante hoy y los dos
--     días siguientes.
--
-- Uso (perfil dev, ver docker/dev/docker-compose.yml):
--   psql "postgresql://accesmed:accesmed@localhost:5432/accesmed" -f Scripts/seed-datos-demo.sql
--
-- Precondición: schema ya migrado por Liquibase (arrancar la app una vez), con
-- los roles de sistema "Medico" y "Admin" y la Clínica ya sembrados. Pensado para
-- una base dev vacía de estas tablas: si se corre dos veces, los UNIQUE rechazan
-- la reinserción. Las agendas cubren 4 semanas desde el día en que se corre.
-- ============================================================================

BEGIN;

-- Contexto: "hoy" y zona horaria según la Clínica (no según la sesión de psql).
CREATE TEMP TABLE seed_ctx ON COMMIT DROP AS
SELECT (now() AT TIME ZONE zona_horaria)::date AS hoy, zona_horaria AS zona
FROM clinica
LIMIT 1;


-- ############################################################################
-- PARTE 1 — DATOS MAESTROS
-- ############################################################################

-- ====================================================================
-- 1.1) Especialidad
-- ====================================================================
INSERT INTO especialidad (codigo, nombre, created_at, created_by) VALUES
    ('CARD', 'Cardiología',          now(), 'seed-script'),
    ('CLIN', 'Clínica Médica',       now(), 'seed-script'),
    ('PED',  'Pediatría',            now(), 'seed-script'),
    ('DERM', 'Dermatología',         now(), 'seed-script'),
    ('TRAU', 'Traumatología',        now(), 'seed-script'),
    ('GINE', 'Ginecología',          now(), 'seed-script'),
    ('DEMO', 'Especialidad Demo',    now(), 'seed-script');

-- ====================================================================
-- 1.2) TipoIndicacionPrestacion
-- ====================================================================
INSERT INTO tipo_indicacion_prestacion (codigo, nombre, created_at, created_by) VALUES
    ('AYUNO',         'Ayuno y alimentación',  now(), 'seed-script'),
    ('ESTUDIO',       'Estudio previo',        now(), 'seed-script'),
    ('MEDICACION',    'Medicación',            now(), 'seed-script'),
    ('VESTIMENTA',    'Vestimenta',            now(), 'seed-script'),
    ('PREPARACION',   'Preparación previa',    now(), 'seed-script'),
    ('DOCUMENTACION', 'Documentación',         now(), 'seed-script'),
    ('ACOMPANANTE',   'Acompañante',           now(), 'seed-script'),
    ('DEMO',          'Indicación Demo',       now(), 'seed-script');

-- ====================================================================
-- 1.3) Prestacion
--   Tres perfiles de tolerancias más el perfil DEMO (minutos, para ver los
--   schedulers en acción). Todos cumplen las restricciones de la cadena.
--   orden = 1 es siempre la consulta de la especialidad; el resto son
--   estudios/procedimientos. slot_min es la duración del turno con que se
--   arma la agenda (siempre entre dur_min y dur_max).
-- ====================================================================
CREATE TEMP TABLE seed_perfil ON COMMIT DROP AS
SELECT * FROM (VALUES
    ('CONSULTA',      interval '3 days',    interval '2 days',    interval '1 day',    interval '12 hours',    interval '2 hours',    interval '30 minutes', interval '18 hours'),
    ('ESTUDIO',       interval '5 days',    interval '3 days',    interval '2 days',   interval '1 day',       interval '6 hours',    interval '30 minutes', interval '30 hours'),
    ('PROCEDIMIENTO', interval '7 days',    interval '3 days',    interval '2 days',   interval '1 day',       interval '6 hours',    interval '45 minutes', interval '36 hours'),
    ('DEMO',          interval '30 minutes', interval '20 minutes', interval '15 minutes', interval '10 minutes', interval '5 minutes', interval '5 minutes',  interval '20 minutes')
) AS t (perfil, solicitud, validacion, reprogramacion, confirmacion, cancelacion, anuncio, recordatorio);

CREATE TEMP TABLE seed_prestacion ON COMMIT DROP AS
SELECT * FROM (VALUES
    -- Cardiología
    ('CARD', 1, 'CARD-CONS', 'Consulta Cardiológica',                   15, 30, 30, 'CONSULTA',      38000.00),
    ('CARD', 2, 'CARD-ECG',  'Electrocardiograma',                      15, 30, 15, 'ESTUDIO',       22000.00),
    ('CARD', 3, 'CARD-ERGO', 'Ergometría (Prueba de Esfuerzo)',         30, 60, 30, 'PROCEDIMIENTO', 65000.00),
    ('CARD', 4, 'CARD-ECO',  'Ecocardiograma Doppler Color',            30, 60, 30, 'ESTUDIO',       78000.00),
    ('CARD', 5, 'CARD-HOLT', 'Holter de Ritmo 24 hs',                   30, 45, 30, 'PROCEDIMIENTO', 70000.00),
    -- Clínica Médica
    ('CLIN', 1, 'CLIN-CONS', 'Consulta Clínica Médica',                 15, 30, 30, 'CONSULTA',      32000.00),
    ('CLIN', 2, 'CLIN-CHEQ', 'Chequeo Médico Anual',                    45, 60, 45, 'ESTUDIO',       45000.00),
    ('CLIN', 3, 'CLIN-APTO', 'Apto Físico',                             20, 30, 20, 'ESTUDIO',       28000.00),
    ('CLIN', 4, 'CLIN-VACU', 'Vacunación de Adultos',                   15, 20, 15, 'ESTUDIO',       18000.00),
    ('CLIN', 5, 'CLIN-CTRL', 'Control de Presión y Glucemia',           15, 30, 15, 'ESTUDIO',       15000.00),
    -- Pediatría
    ('PED',  1, 'PED-CONS',  'Consulta Pediátrica',                     15, 30, 30, 'CONSULTA',      30000.00),
    ('PED',  2, 'PED-NINO',  'Control de Niño Sano',                    20, 30, 20, 'ESTUDIO',       34000.00),
    ('PED',  3, 'PED-VACU',  'Vacunación Infantil',                     15, 20, 15, 'ESTUDIO',       20000.00),
    ('PED',  4, 'PED-APTO',  'Apto Escolar y Deportivo',                20, 30, 20, 'ESTUDIO',       24000.00),
    ('PED',  5, 'PED-LACT',  'Control de Lactante',                     30, 45, 30, 'ESTUDIO',       36000.00),
    -- Dermatología
    ('DERM', 1, 'DERM-CONS', 'Consulta Dermatológica',                  15, 30, 30, 'CONSULTA',      34000.00),
    ('DERM', 2, 'DERM-DERMO','Dermatoscopía Digital',                   20, 30, 20, 'ESTUDIO',       45000.00),
    ('DERM', 3, 'DERM-CRIO', 'Crioterapia',                             30, 45, 30, 'PROCEDIMIENTO', 42000.00),
    ('DERM', 4, 'DERM-BIOP', 'Biopsia de Piel',                         30, 60, 30, 'PROCEDIMIENTO', 68000.00),
    ('DERM', 5, 'DERM-ELEC', 'Electrocoagulación de Lesiones',          30, 45, 30, 'PROCEDIMIENTO', 52000.00),
    -- Traumatología
    ('TRAU', 1, 'TRAU-CONS', 'Consulta Traumatológica',                 15, 30, 30, 'CONSULTA',      36000.00),
    ('TRAU', 2, 'TRAU-YESO', 'Colocación y Retiro de Yeso',             30, 45, 30, 'PROCEDIMIENTO', 55000.00),
    ('TRAU', 3, 'TRAU-INFI', 'Infiltración Articular',                  30, 45, 30, 'PROCEDIMIENTO', 72000.00),
    ('TRAU', 4, 'TRAU-VISC', 'Viscosuplementación',                     30, 45, 30, 'PROCEDIMIENTO', 110000.00),
    ('TRAU', 5, 'TRAU-ECO',  'Ecografía Musculoesquelética',            30, 45, 30, 'ESTUDIO',       58000.00),
    -- Ginecología
    ('GINE', 1, 'GINE-CONS', 'Consulta Ginecológica',                   15, 30, 30, 'CONSULTA',      36000.00),
    ('GINE', 2, 'GINE-PAP',  'Papanicolaou (PAP)',                      15, 30, 15, 'ESTUDIO',       28000.00),
    ('GINE', 3, 'GINE-COLP', 'Colposcopía',                             20, 30, 20, 'ESTUDIO',       42000.00),
    ('GINE', 4, 'GINE-ECO',  'Ecografía Ginecológica',                  20, 30, 20, 'ESTUDIO',       55000.00),
    ('GINE', 5, 'GINE-DIU',  'Colocación de DIU',                       30, 45, 30, 'PROCEDIMIENTO', 85000.00),
    -- Demo (orden 0: no es la consulta de ninguna especialidad real)
    ('DEMO', 0, 'DEMO-PREST','Prestación Demo',                         10, 15, 10, 'DEMO',          10000.00)
) AS t (esp, orden, codigo, nombre, dur_min, dur_max, slot_min, perfil, precio);

INSERT INTO prestacion (
    codigo, nombre,
    duracion_minima, duracion_maxima,
    tiempo_tolerancia_solicitud, tiempo_tolerancia_validacion, tiempo_tolerancia_reprogramacion,
    tiempo_tolerancia_confirmacion, tiempo_tolerancia_cancelacion, tiempo_tolerancia_anuncio,
    tiempo_recordatorio_confirmacion,
    especialidad_id, created_at, created_by
)
SELECT
    sp.codigo, sp.nombre,
    sp.dur_min * interval '1 minute', sp.dur_max * interval '1 minute',
    pf.solicitud, pf.validacion, pf.reprogramacion,
    pf.confirmacion, pf.cancelacion, pf.anuncio,
    pf.recordatorio,
    e.id, now(), 'seed-script'
FROM seed_prestacion sp
JOIN seed_perfil pf ON pf.perfil = sp.perfil
JOIN especialidad e ON e.codigo = sp.esp;

-- Un único tramo PUBLICADA y vigente por prestación (fecha_hora_fin NULL = estado actual).
INSERT INTO historico_estado_prestacion (fecha_hora_inicio, estado, prestacion_id, created_at, created_by)
SELECT now() - interval '180 days', 'PUBLICADA', p.id, now(), 'seed-script'
FROM prestacion p
JOIN seed_prestacion sp ON sp.codigo = p.codigo;

-- ====================================================================
-- 1.4) IndicacionPrestacion
--   Las consultas (orden 1) no llevan indicaciones. requiere_validacion = true
--   marca las obligatorias: el turno nace en ESPERA_VALIDACION hasta que el
--   personal las valide.
-- ====================================================================
CREATE TEMP TABLE seed_indicacion ON COMMIT DROP AS
SELECT * FROM (VALUES
    -- Cardiología
    ('CARD-ECG',  'VESTIMENTA',    'Ropa de dos piezas',
     'Concurrir con ropa de dos piezas que permita descubrir el torso. No aplicar cremas ni aceites en el pecho el día del estudio.', false),
    ('CARD-ERGO', 'VESTIMENTA',    'Ropa y calzado deportivo',
     'Concurrir con ropa cómoda y calzado deportivo, aptos para caminar y correr sobre una cinta.', false),
    ('CARD-ERGO', 'AYUNO',         'Comida liviana previa',
     'No realizar comidas abundantes en las 3 horas previas, no fumar ni tomar café o mate el día del estudio.', false),
    ('CARD-ERGO', 'MEDICACION',    'Suspensión de betabloqueantes',
     'Si toma betabloqueantes, consultar con su cardiólogo si debe suspenderlos 48 horas antes. El personal valida esta indicación antes de confirmar el turno.', true),
    ('CARD-ERGO', 'DOCUMENTACION', 'Orden médica y electrocardiograma previo',
     'Presentar la orden médica y un electrocardiograma de menos de 6 meses.', true),
    ('CARD-ECO',  'DOCUMENTACION', 'Orden médica',
     'Presentar la orden médica del estudio.', true),
    ('CARD-ECO',  'VESTIMENTA',    'Ropa cómoda',
     'Concurrir con ropa cómoda que permita descubrir el torso.', false),
    ('CARD-HOLT', 'DOCUMENTACION', 'Orden médica',
     'Presentar la orden médica del estudio.', true),
    ('CARD-HOLT', 'PREPARACION',   'Baño previo',
     'Higienizarse antes de la colocación: el equipo no se puede mojar durante las 24 horas del estudio. No aplicar cremas en el torso.', false),
    -- Clínica Médica
    ('CLIN-CHEQ', 'AYUNO',         'Ayuno de 8 horas',
     'Concurrir con ayuno de 8 horas (se puede tomar agua). Evitar el alcohol las 24 horas previas.', false),
    ('CLIN-CHEQ', 'ESTUDIO',       'Laboratorio de rutina previo',
     'Traer los resultados del laboratorio de rutina solicitado, realizados en los últimos 30 días.', true),
    ('CLIN-APTO', 'ESTUDIO',       'Electrocardiograma reciente',
     'Presentar un electrocardiograma de menos de 6 meses.', true),
    ('CLIN-APTO', 'DOCUMENTACION', 'DNI y formulario de la institución',
     'Traer el DNI y el formulario de la institución o club que solicita el apto.', false),
    ('CLIN-VACU', 'DOCUMENTACION', 'Carnet de vacunación',
     'Presentar el carnet de vacunación para registrar la dosis aplicada.', true),
    ('CLIN-CTRL', 'AYUNO',         'Ayuno de 4 horas',
     'Concurrir con ayuno de 4 horas para que la medición de glucemia sea válida.', false),
    -- Pediatría
    ('PED-NINO',  'DOCUMENTACION', 'Libreta sanitaria',
     'Traer la libreta sanitaria y el carnet de vacunas del niño o la niña.', false),
    ('PED-VACU',  'DOCUMENTACION', 'Carnet de vacunación',
     'Presentar el carnet de vacunación para registrar la dosis aplicada.', true),
    ('PED-VACU',  'ACOMPANANTE',   'Acompañante responsable',
     'El niño o la niña debe concurrir acompañado por madre, padre o tutor legal.', true),
    ('PED-APTO',  'DOCUMENTACION', 'Formulario de la institución',
     'Traer el formulario del colegio o club que solicita el apto.', false),
    ('PED-LACT',  'DOCUMENTACION', 'Libreta sanitaria',
     'Traer la libreta sanitaria del bebé.', false),
    ('PED-LACT',  'ACOMPANANTE',   'Concurrir con madre o padre',
     'Se recomienda que asista quien cuida al bebé la mayor parte del día.', false),
    -- Dermatología
    ('DERM-DERMO','PREPARACION',   'Piel limpia y sin maquillaje',
     'Concurrir con la piel limpia, sin cremas, maquillaje ni esmalte de uñas.', false),
    ('DERM-CRIO', 'PREPARACION',   'Zona limpia y seca',
     'Higienizar la zona a tratar y no aplicar cremas ni productos previos.', false),
    ('DERM-BIOP', 'MEDICACION',    'Suspensión de anticoagulantes',
     'Si toma anticoagulantes o aspirina, consultar con su médico de cabecera si debe suspenderlos. El personal valida esta indicación antes de confirmar el turno.', true),
    ('DERM-BIOP', 'DOCUMENTACION', 'Consentimiento informado firmado',
     'Traer firmado el consentimiento informado que se entrega en la consulta previa.', true),
    ('DERM-ELEC', 'PREPARACION',   'Zona limpia',
     'Higienizar la zona a tratar y no aplicar cremas antes del procedimiento.', false),
    -- Traumatología
    ('TRAU-YESO', 'VESTIMENTA',    'Ropa holgada',
     'Concurrir con ropa holgada que permita descubrir el miembro a inmovilizar o liberar.', false),
    ('TRAU-INFI', 'MEDICACION',    'Aviso de anticoagulantes y antiinflamatorios',
     'Informar si toma anticoagulantes o antiinflamatorios. El personal valida esta indicación antes de confirmar el turno.', true),
    ('TRAU-INFI', 'ESTUDIO',       'Estudios por imágenes previos',
     'Traer la radiografía o resonancia de la articulación a infiltrar.', true),
    ('TRAU-VISC', 'ESTUDIO',       'Radiografía o resonancia previa',
     'Traer el estudio por imágenes de la articulación, realizado en el último año.', true),
    ('TRAU-VISC', 'DOCUMENTACION', 'Orden médica y autorización',
     'Presentar la orden médica y la autorización de la obra social (si tiene cobertura).', true),
    ('TRAU-ECO',  'VESTIMENTA',    'Ropa que permita ver la zona',
     'Concurrir con ropa que permita descubrir fácilmente la zona a estudiar.', false),
    ('TRAU-ECO',  'DOCUMENTACION', 'Orden médica',
     'Presentar la orden médica del estudio.', true),
    -- Ginecología
    ('GINE-PAP',  'PREPARACION',   'Preparación para el PAP',
     'No mantener relaciones sexuales 48 horas antes, no usar óvulos ni duchas vaginales y no estar menstruando el día del estudio.', false),
    ('GINE-COLP', 'PREPARACION',   'Preparación para la colposcopía',
     'No mantener relaciones sexuales 48 horas antes, no usar óvulos ni duchas vaginales y no estar menstruando el día del estudio.', false),
    ('GINE-COLP', 'ESTUDIO',       'PAP previo',
     'Traer el resultado del último Papanicolaou.', true),
    ('GINE-ECO',  'PREPARACION',   'Vejiga según el tipo de ecografía',
     'Para la ecografía abdominal concurrir con la vejiga llena; para la transvaginal, con la vejiga vacía. Se indica cuál corresponde al pedir el turno.', false),
    ('GINE-ECO',  'DOCUMENTACION', 'Orden médica',
     'Presentar la orden médica del estudio.', true),
    ('GINE-DIU',  'ESTUDIO',       'Ecografía y PAP recientes',
     'Traer una ecografía ginecológica y un Papanicolaou de menos de 1 año.', true),
    ('GINE-DIU',  'PREPARACION',   'Test de embarazo negativo',
     'Realizarse un test de embarazo antes del procedimiento y traer el resultado.', true),
    -- Demo
    ('DEMO-PREST','DEMO',          'Indicación Demo',
     'Indicación de prueba, obligatoria: el turno nace en Espera de Validación y sirve para ver cómo el scheduler lo vence si nadie la valida a tiempo.', true)
) AS t (prestacion, tipo, nombre, descripcion, requiere_validacion);

INSERT INTO indicacion_prestacion (
    nombre, descripcion, requiere_validacion, fecha_inicio_vigencia,
    prestacion_id, tipo_indicacion_prestacion_id, created_at, created_by
)
SELECT
    si.nombre, si.descripcion, si.requiere_validacion, now() - interval '180 days',
    p.id, t.id, now(), 'seed-script'
FROM seed_indicacion si
JOIN prestacion p ON p.codigo = si.prestacion
JOIN tipo_indicacion_prestacion t ON t.codigo = si.tipo;

-- ====================================================================
-- 1.5) ObraSocial y Plan (5 planes por obra social, todos PUBLICADO)
--   tier 1 = plan más básico, 5 = el más completo (define la cobertura, ver 1.6).
-- ====================================================================
INSERT INTO obra_social (codigo, nombre, razon_social, created_at, created_by) VALUES
    ('OSDE',   'OSDE',          'Organización de Servicios Directos Empresarios', now(), 'seed-script'),
    ('SMG',    'Swiss Medical', 'Swiss Medical Group S.A.',                       now(), 'seed-script'),
    ('GAL',    'Galeno',        'Galeno Argentina S.A.',                          now(), 'seed-script'),
    ('MEDIFE', 'Medifé',        'Medifé Asociación Civil',                        now(), 'seed-script'),
    ('SANCOR', 'Sancor Salud',  'Asociación Mutual Sancor Salud',                 now(), 'seed-script'),
    ('OMINT',  'Omint',         'Omint S.A. de Servicios',                        now(), 'seed-script');

CREATE TEMP TABLE seed_plan ON COMMIT DROP AS
SELECT * FROM (VALUES
    ('OSDE',   'OSDE-210',    'OSDE 210',           1),
    ('OSDE',   'OSDE-310',    'OSDE 310',           2),
    ('OSDE',   'OSDE-410',    'OSDE 410',           3),
    ('OSDE',   'OSDE-450',    'OSDE 450',           4),
    ('OSDE',   'OSDE-510',    'OSDE 510',           5),
    ('SMG',    'SMG-20',      'SMG 20',             1),
    ('SMG',    'SMG-30',      'SMG 30',             2),
    ('SMG',    'SMG-40',      'SMG 40',             3),
    ('SMG',    'SMG-50',      'SMG 50',             4),
    ('SMG',    'SMG-60',      'SMG 60',             5),
    ('GAL',    'GAL-CELESTE', 'Galeno Celeste',     1),
    ('GAL',    'GAL-PLATA',   'Galeno Plata',       2),
    ('GAL',    'GAL-ORO',     'Galeno Oro',         3),
    ('GAL',    'GAL-AZUL',    'Galeno Azul',        4),
    ('GAL',    'GAL-BLACK',   'Galeno Black',       5),
    ('MEDIFE', 'MED-BRONCE',  'Medifé Bronce',      1),
    ('MEDIFE', 'MED-PLATA',   'Medifé Plata',       2),
    ('MEDIFE', 'MED-ORO',     'Medifé Oro',         3),
    ('MEDIFE', 'MED-PLATINO', 'Medifé Platino',     4),
    ('MEDIFE', 'MED-ESMERAL', 'Medifé Esmeralda',   5),
    ('SANCOR', 'SAN-1000',    'Sancor Salud 1000',  1),
    ('SANCOR', 'SAN-2000',    'Sancor Salud 2000',  2),
    ('SANCOR', 'SAN-3000',    'Sancor Salud 3000',  3),
    ('SANCOR', 'SAN-4000',    'Sancor Salud 4000',  4),
    ('SANCOR', 'SAN-5000',    'Sancor Salud 5000',  5),
    ('OMINT',  'OMI-ESENCIAL','Omint Esencial',     1),
    ('OMINT',  'OMI-CLASICO', 'Omint Clásico',      2),
    ('OMINT',  'OMI-SUPERIOR','Omint Superior',     3),
    ('OMINT',  'OMI-PREMIUM', 'Omint Premium',      4),
    ('OMINT',  'OMI-ELITE',   'Omint Élite',        5)
) AS t (obra_social, codigo, nombre, tier);

INSERT INTO plan (codigo, nombre, obra_social_id, created_at, created_by)
SELECT sp.codigo, sp.nombre, os.id, now(), 'seed-script'
FROM seed_plan sp
JOIN obra_social os ON os.codigo = sp.obra_social;

-- Un único tramo PUBLICADO y vigente por plan.
INSERT INTO historico_estado_plan (fecha_hora_inicio, estado, plan_id, created_at, created_by)
SELECT now() - interval '180 days', 'PUBLICADO', p.id, now(), 'seed-script'
FROM plan p
JOIN seed_plan sp ON sp.codigo = p.codigo;

-- ====================================================================
-- 1.6) ObraSocialPlanPrestacion — qué cubre cada plan
--   Todos los planes cubren la consulta de cada especialidad. Cuanto más alto
--   el tier, más prestaciones cubre (un plan de tier N cubre las prestaciones
--   de orden <= N; la Prestación Demo, de orden 0, entra en todos):
--     tier 1: PORCENTUAL 50 · tier 2: PORCENTUAL 60 · tier 3: PORCENTUAL 70
--     tier 4: consultas TOTAL, resto PORCENTUAL 80
--     tier 5: consultas TOTAL, resto CARGO_FIJO (coseguro 2500)
--   Coherente con ck_obra_social_plan_prestacion_coherencia: TOTAL sin
--   porcentaje ni coseguro; CARGO_FIJO solo con coseguro; PORCENTUAL solo con
--   porcentaje.
-- ====================================================================
INSERT INTO obra_social_plan_prestacion (
    modalidad_cobertura, porcentaje_cobertura, coseguro, plan_id, prestacion_id, created_at, created_by
)
SELECT
    CASE
        WHEN sp.tier >= 4 AND spr.orden <= 1 THEN 'TOTAL'
        WHEN sp.tier = 5 THEN 'CARGO_FIJO'
        ELSE 'PORCENTUAL'
    END,
    CASE
        WHEN sp.tier >= 4 AND spr.orden <= 1 THEN NULL
        WHEN sp.tier = 5 THEN NULL
        ELSE (CASE sp.tier WHEN 1 THEN 50 WHEN 2 THEN 60 WHEN 3 THEN 70 ELSE 80 END)::numeric(5,2)
    END,
    CASE WHEN sp.tier = 5 AND spr.orden > 1 THEN 2500.00 END,
    pl.id, pr.id, now(), 'seed-script'
FROM seed_plan sp
JOIN plan pl ON pl.codigo = sp.codigo
JOIN seed_prestacion spr ON spr.orden <= sp.tier
JOIN prestacion pr ON pr.codigo = spr.codigo;


-- ############################################################################
-- PARTE 2 — PERSONAL
-- ############################################################################

-- ====================================================================
-- 2.1) Medico
--   Una sola especialidad por médico. Cardiología, Clínica Médica,
--   Dermatología y Ginecología tienen dos médicos (con las mismas
--   prestaciones) para probar reprogramaciones. Ginecología es la
--   especialidad nueva. El médico demo no tiene usuario.
--   turno: 'M' = consulta de mañana y estudios de tarde; 'T' = al revés.
--   dias: días de atención (1 = lunes ... 5 = viernes).
--   factor: multiplica el precio base de la prestación.
-- ====================================================================
CREATE TEMP TABLE seed_medico ON COMMIT DROP AS
SELECT * FROM (VALUES
    ('MP-10234', '30123456', 'Juan',      'Pérez',     'juan.perez@accesmed.com',      '+54 11 4444-1001', 'CARD', 'M', ARRAY[1,2,3,4,5], 1.00, true),
    ('MP-10456', '29876543', 'Lucía',     'Gómez',     'lucia.gomez@accesmed.com',     '+54 11 4444-1002', 'CLIN', 'M', ARRAY[1,2,3,4,5], 1.00, true),
    ('MP-10789', '28456123', 'Martín',    'Ibarra',    'martin.ibarra@accesmed.com',   '+54 11 4444-1003', 'CLIN', 'T', ARRAY[1,2,3,4,5], 1.05, false),
    ('MP-11023', '31567890', 'Sofía',     'Ramírez',   'sofia.ramirez@accesmed.com',   '+54 11 4444-1004', 'PED',  'M', ARRAY[1,2,3,4,5], 1.00, true),
    ('MP-11345', '27890456', 'Federico',  'Suárez',    'federico.suarez@accesmed.com', '+54 11 4444-1005', 'DERM', 'M', ARRAY[1,3,5],     1.00, false),
    ('MP-11678', '32234567', 'Valentina', 'Castro',    'valentina.castro@accesmed.com','+54 11 4444-1006', 'TRAU', 'T', ARRAY[1,2,3,4,5], 1.00, true),
    ('MP-12011', '26345678', 'Ricardo',   'Molina',    'ricardo.molina@accesmed.com',  '+54 11 4444-1007', 'CARD', 'T', ARRAY[2,3,4,5],   1.10, false),
    ('MP-12134', '33456789', 'Camila',    'Vega',      'camila.vega@accesmed.com',     '+54 11 4444-1008', 'DERM', 'T', ARRAY[2,4],       0.95, false),
    ('MP-12277', '31890123', 'Mariana',   'Acosta',    'mariana.acosta@accesmed.com',  '+54 11 4444-1009', 'GINE', 'M', ARRAY[1,2,3,4,5], 1.00, true),
    ('MP-12390', '32901234', 'Paula',     'Domínguez', 'paula.dominguez@accesmed.com', '+54 11 4444-1010', 'GINE', 'T', ARRAY[1,3,4,5],   1.05, false),
    ('MP-DEMO',  '40000001', 'Médico',    'Demo',      'medico.demo@accesmed.com',     '+54 11 4444-1099', 'DEMO', 'M', ARRAY[1,2,3,4,5], 1.00, false)
) AS t (matricula, dni, nombre, apellido, email, telefono, esp, turno, dias, factor, con_usuario);

INSERT INTO medico (matricula, dni, nombre, apellido, email, numero_telefono, especialidad_id, created_at, created_by)
SELECT sm.matricula, sm.dni, sm.nombre, sm.apellido, sm.email, sm.telefono, e.id, now(), 'seed-script'
FROM seed_medico sm
JOIN especialidad e ON e.codigo = sm.esp;

-- ====================================================================
-- 2.2) MedicoPrestacion — cada médico atiende todas las prestaciones de su
--   especialidad (regla de MedicoPrestacionDomainService), vigentes desde
--   hace 90 días, sin fecha de fin y con atención particular.
-- ====================================================================
INSERT INTO medico_prestacion (
    atiende_particular, precio_particular, fecha_inicio_vigencia, medico_id, prestacion_id, created_at, created_by
)
SELECT true, round(spr.precio * sm.factor, -2), c.hoy - 90, m.id, p.id, now(), 'seed-script'
FROM seed_medico sm
JOIN medico m ON m.matricula = sm.matricula
JOIN seed_prestacion spr ON spr.esp = sm.esp
JOIN prestacion p ON p.codigo = spr.codigo
CROSS JOIN seed_ctx c;

-- ====================================================================
-- 2.3) Admin
-- ====================================================================
CREATE TEMP TABLE seed_admin ON COMMIT DROP AS
SELECT * FROM (VALUES
    ('Ana',    'Torres',  '33111222', 'ana.torres@accesmed.com'),
    ('Carlos', 'Medina',  '34222333', 'carlos.medina@accesmed.com'),
    ('Paula',  'Herrera', '35333444', 'paula.herrera@accesmed.com')
) AS t (nombre, apellido, dni, email);

INSERT INTO admin (nombre, apellido, dni, email, created_at, created_by)
SELECT sa.nombre, sa.apellido, sa.dni, sa.email, now(), 'seed-script'
FROM seed_admin sa;

-- ====================================================================
-- 2.4) Usuario y UsuarioRol
--   Solo se guardan hashes bcrypt (costo 10); las contraseñas no están en el
--   repositorio. El mail de acceso es el email de contacto de cada persona.
--   usuario.medico_id es UNIQUE y ck_usuario_medico_xor_admin exige medico_id
--   XOR admin_id: por eso el otro queda NULL. Todos los médicos con
--   con_usuario = true (ver 2.1) tienen que figurar acá, y viceversa.
-- ====================================================================
CREATE TEMP TABLE seed_credencial ON COMMIT DROP AS
SELECT * FROM (VALUES
    ('juan.perez@accesmed.com',         '$2a$10$QrNewihcLT1mCSkY25aG8etoMDGIAfCdaJlIqvlmG1rQAVwb7gCGO'),
    ('lucia.gomez@accesmed.com',        '$2a$10$yzOqP94tirvSKI3TZqA7YOom62sm808de3ItAhFeT9wevDBlK4eiG'),
    ('sofia.ramirez@accesmed.com',      '$2a$10$qTQqhKIb3fbQX2LC4ktYWO5K2lWEc9XdR8FIpz41B4onLKJLNz/ue'),
    ('valentina.castro@accesmed.com',   '$2a$10$pFamT7OBN/PC0SrWJorT3.4Mg6ceH/GER6.gP2B9iYdARrB5x9UNG'),
    ('mariana.acosta@accesmed.com',     '$2a$10$hrUSgzfdRLwny2dpGq6y5OyowsQJ1p0RINp406/ERgn33Ep4UsFIq'),
    ('ana.torres@accesmed.com',         '$2a$10$OZyzWvHkiK06CxMxaTh2aesIAgvEccX90BmAbBDXf8XhSzUKOzd0.'),
    ('carlos.medina@accesmed.com',      '$2a$10$9THVvv3No9dr4NHRtijyT.RF4y6n3kcIaOCFNLyhdz7ZnEnQxuzq6'),
    ('paula.herrera@accesmed.com',      '$2a$10$EmjTCWIruLlTtvj2g4Ne2OeyK8.KP9S2bXhV7PwoNjukIBUb8YvOm')
) AS t (mail, password_hash);

WITH usuario_admin AS (
    INSERT INTO usuario (mail, password_hash, admin_id, created_at, created_by)
    SELECT a.email, sc.password_hash, a.id, now(), 'seed-script'
    FROM seed_admin sa
    JOIN admin a ON a.dni = sa.dni
    JOIN seed_credencial sc ON sc.mail = a.email
    RETURNING id
)
INSERT INTO usuario_rol (fecha_inicio_vigencia, usuario_id, rol_id, created_at, created_by)
SELECT now(), ua.id, r.id, now(), 'seed-script'
FROM usuario_admin ua
JOIN rol r ON r.nombre = 'Admin' AND r.deleted_at IS NULL;

WITH usuario_medico AS (
    INSERT INTO usuario (mail, password_hash, medico_id, created_at, created_by)
    SELECT m.email, sc.password_hash, m.id, now(), 'seed-script'
    FROM seed_medico sm
    JOIN medico m ON m.matricula = sm.matricula
    JOIN seed_credencial sc ON sc.mail = m.email
    WHERE sm.con_usuario
    RETURNING id
)
INSERT INTO usuario_rol (fecha_inicio_vigencia, usuario_id, rol_id, created_at, created_by)
SELECT now(), um.id, r.id, now(), 'seed-script'
FROM usuario_medico um
JOIN rol r ON r.nombre = 'Medico' AND r.deleted_at IS NULL;

-- ====================================================================
-- 2.5) Paciente y ObraSocialPaciente
--   20 pacientes: 15 con un plan Publicado (repartidos entre las 6 obras
--   sociales y distintos niveles de plan) y 5 sin obra social (se atienden
--   como particulares). El teléfono va en formato internacional, como llega
--   desde WhatsApp.
-- ====================================================================
CREATE TEMP TABLE seed_paciente ON COMMIT DROP AS
SELECT * FROM (VALUES
    ('30111001', 'Agustina', 'Fernández', date '1990-03-14', 'agustina.fernandez@example.com', '+5491155501001', 'OSDE-210',    '30111001/01'),
    ('31222002', 'Bruno',    'Sosa',      date '1985-07-22', 'bruno.sosa@example.com',          '+5491155501002', 'OSDE-450',    '31222002/00'),
    ('32333003', 'Carolina', 'Benítez',   date '1978-11-05', 'carolina.benitez@example.com',    '+5491155501003', 'SMG-20',      'SM-4408123'),
    ('33444004', 'Diego',    'Romero',    date '1992-01-30', 'diego.romero@example.com',        '+5491155501004', 'SMG-50',      'SM-5521907'),
    ('34555005', 'Elena',    'Gutiérrez', date '1969-09-18', 'elena.gutierrez@example.com',     '+5491155501005', 'GAL-CELESTE', 'GA-7710234'),
    ('35666006', 'Facundo',  'Álvarez',   date '2001-05-09', 'facundo.alvarez@example.com',     '+5491155501006', 'GAL-ORO',     'GA-7724410'),
    ('36777007', 'Gabriela', 'Núñez',     date '1988-12-02', 'gabriela.nunez@example.com',      '+5491155501007', 'GAL-BLACK',   'GA-7735521'),
    ('37888008', 'Hernán',   'Ponce',     date '1995-04-27', 'hernan.ponce@example.com',        '+5491155501008', 'MED-BRONCE',  'MF-9012345'),
    ('38999009', 'Inés',     'Cabrera',   date '1974-08-11', 'ines.cabrera@example.com',        '+5491155501009', 'MED-PLATINO', 'MF-9023456'),
    ('39100010', 'Joaquín',  'Rivas',     date '2010-02-19', 'joaquin.rivas@example.com',       '+5491155501010', 'SAN-1000',    'SA-3300117'),
    ('40211011', 'Karina',   'Domínguez', date '1983-06-06', 'karina.dominguez@example.com',    '+5491155501011', 'SAN-4000',    'SA-3300982'),
    ('41322012', 'Leandro',  'Paz',       date '1997-10-25', 'leandro.paz@example.com',         '+5491155501012', 'OMI-CLASICO', 'OM-1200456'),
    ('42433013', 'Marina',   'Silva',     date '1966-03-03', 'marina.silva@example.com',        '+5491155501013', 'OMI-ELITE',   'OM-1200871'),
    ('43544014', 'Nicolás',  'Vega',      date '2015-09-14', 'nicolas.vega@example.com',        '+5491155501014', 'OSDE-310',    '43544014/02'),
    ('44655015', 'Olivia',   'Herrera',   date '1999-01-08', 'olivia.herrera@example.com',      '+5491155501015', 'SMG-40',      'SM-5530011'),
    ('45766016', 'Pablo',    'Molina',    date '1981-07-16', 'pablo.molina@example.com',        '+5491155501016', NULL,          NULL),
    ('46877017', 'Romina',   'Ledesma',   date '1993-11-21', 'romina.ledesma@example.com',      '+5491155501017', NULL,          NULL),
    ('47988018', 'Santiago', 'Correa',    date '1972-05-29', 'santiago.correa@example.com',     '+5491155501018', NULL,          NULL),
    ('48099019', 'Tamara',   'Ruiz',      date '2004-08-04', 'tamara.ruiz@example.com',         '+5491155501019', NULL,          NULL),
    ('49100020', 'Ulises',   'Campos',    date '1960-12-12', 'ulises.campos@example.com',       '+5491155501020', NULL,          NULL)
) AS t (dni, nombre, apellido, fecha_nacimiento, email, telefono, plan, nro_socio);

INSERT INTO paciente (dni, nombre, apellido, fecha_nacimiento, email, numero_telefono, created_at, created_by)
SELECT sp.dni, sp.nombre, sp.apellido, sp.fecha_nacimiento, sp.email, sp.telefono, now(), 'seed-script'
FROM seed_paciente sp;

INSERT INTO obra_social_paciente (nro_socio, paciente_id, plan_id, created_at, created_by)
SELECT sp.nro_socio, pa.id, pl.id, now(), 'seed-script'
FROM seed_paciente sp
JOIN paciente pa ON pa.dni = sp.dni AND pa.deleted_at IS NULL
JOIN plan pl ON pl.codigo = sp.plan
WHERE sp.plan IS NOT NULL;


-- ############################################################################
-- PARTE 3 — AGENDAS
-- ############################################################################

-- ====================================================================
-- 3.1) AgendaMedico — un período por médico (no se solapan: hay uno solo).
--   Médicos reales: 4 semanas desde hoy (la generación por API tiene un tope
--   operativo de 500 horarios por confirmación; con 4 semanas queda debajo).
--   Médico demo: hoy y los dos días siguientes.
-- ====================================================================
INSERT INTO agenda_medico (fecha_inicio_vigencia, fecha_fin_vigencia, medico_id, created_at, created_by)
SELECT c.hoy, CASE WHEN sm.esp = 'DEMO' THEN c.hoy + 2 ELSE c.hoy + 27 END, m.id, now(), 'seed-script'
FROM seed_medico sm
JOIN medico m ON m.matricula = sm.matricula
CROSS JOIN seed_ctx c;

-- ====================================================================
-- 3.2) AgendaHorariosDia — horarios de los médicos reales
--   Por cada día de atención del médico se generan dos bloques que no se
--   superponen:
--     · consulta de la especialidad, en bloques de 4 horas, turnos de 30 min;
--     · una prestación de orden 2..5 (rota día a día para que se cubran todas),
--       en bloques de 3 horas, turnos de la duración mínima de la prestación
--       (siempre divide 180 min).
--   turno 'M': consulta 09-13 y estudios 14-17. turno 'T': estudios 09-12 y
--   consulta 14-18. Todo dentro del horario de atención de la clínica.
--   Se omiten los slots de hoy cuyo inicio ya pasó.
-- ====================================================================
WITH dias AS (
    SELECT am.id AS agenda_id, sm.esp, sm.turno,
           g.d::date AS fecha,
           (g.d::date - am.fecha_inicio_vigencia) AS idx
    FROM seed_medico sm
    JOIN medico m ON m.matricula = sm.matricula
    JOIN agenda_medico am ON am.medico_id = m.id
    CROSS JOIN LATERAL generate_series(am.fecha_inicio_vigencia, am.fecha_fin_vigencia, interval '1 day') AS g(d)
    WHERE sm.esp <> 'DEMO'
      AND extract(isodow FROM g.d)::int = ANY (sm.dias)
), bloques AS (
    SELECT d.agenda_id, d.fecha, spr.codigo, spr.slot_min, bl.hora_desde, bl.hora_hasta
    FROM dias d
    CROSS JOIN LATERAL (VALUES
        (1,
         CASE WHEN d.turno = 'M' THEN time '09:00' ELSE time '14:00' END,
         CASE WHEN d.turno = 'M' THEN time '13:00' ELSE time '18:00' END),
        (2 + (d.idx % 4),
         CASE WHEN d.turno = 'M' THEN time '14:00' ELSE time '09:00' END,
         CASE WHEN d.turno = 'M' THEN time '17:00' ELSE time '12:00' END)
    ) AS bl (orden, hora_desde, hora_hasta)
    JOIN seed_prestacion spr ON spr.esp = d.esp AND spr.orden = bl.orden
)
INSERT INTO agenda_horarios_dia (
    fecha, hora_desde, hora_hasta, fecha_limite_reserva, esta_ocupada,
    agenda_medico_id, prestacion_id, created_at, created_by
)
SELECT
    b.fecha,
    t.hora_desde,
    t.hora_desde + b.slot_min * interval '1 minute',
    ((b.fecha + t.hora_desde) AT TIME ZONE c.zona) - p.tiempo_tolerancia_solicitud,
    false,
    b.agenda_id, p.id, now(), 'seed-script'
FROM bloques b
JOIN prestacion p ON p.codigo = b.codigo
CROSS JOIN seed_ctx c
CROSS JOIN LATERAL generate_series(0, (extract(epoch FROM (b.hora_hasta - b.hora_desde))::int / 60 / b.slot_min) - 1) AS s(n)
CROSS JOIN LATERAL (SELECT b.hora_desde + (s.n * b.slot_min) * interval '1 minute' AS hora_desde) AS t
WHERE ((b.fecha + t.hora_desde) AT TIME ZONE c.zona) > now();

-- ====================================================================
-- 3.3) AgendaHorariosDia — médico demo
--   Toda la jornada de la clínica (08:00 a 20:00) en turnos de 10 minutos,
--   para hoy y los dos días siguientes. Con las tolerancias de la Prestación
--   Demo (solicitud 30 min) siempre hay slots reservables, y los turnos que se
--   saquen vencen en minutos.
-- ====================================================================
INSERT INTO agenda_horarios_dia (
    fecha, hora_desde, hora_hasta, fecha_limite_reserva, esta_ocupada,
    agenda_medico_id, prestacion_id, created_at, created_by
)
SELECT
    g.d::date,
    t.hora_desde,
    t.hora_desde + interval '10 minutes',
    ((g.d::date + t.hora_desde) AT TIME ZONE c.zona) - p.tiempo_tolerancia_solicitud,
    false,
    am.id, p.id, now(), 'seed-script'
FROM medico m
JOIN agenda_medico am ON am.medico_id = m.id
JOIN prestacion p ON p.codigo = 'DEMO-PREST'
CROSS JOIN seed_ctx c
CROSS JOIN LATERAL generate_series(am.fecha_inicio_vigencia, am.fecha_fin_vigencia, interval '1 day') AS g(d)
CROSS JOIN LATERAL generate_series(0, 71) AS s(n)
CROSS JOIN LATERAL (SELECT time '08:00' + s.n * interval '10 minutes' AS hora_desde) AS t
WHERE m.matricula = 'MP-DEMO'
  AND ((g.d::date + t.hora_desde) AT TIME ZONE c.zona) > now();


-- ############################################################################
-- Verificación: si alguna regla del seed no se cumple, aborta y no deja nada.
-- ############################################################################
DO $$
BEGIN
    ASSERT (SELECT count(*) FROM usuario WHERE created_by = 'seed-script') = 8,
        'Se esperaban 8 usuarios (5 médicos y 3 admins): revisar seed_credencial';
    ASSERT (SELECT count(*) FROM paciente WHERE created_by = 'seed-script') = 20,
        'Se esperaban 20 pacientes';
    ASSERT (SELECT count(*) FROM paciente p WHERE p.created_by = 'seed-script'
            AND NOT EXISTS (SELECT 1 FROM obra_social_paciente osp WHERE osp.paciente_id = p.id)) = 5,
        'Se esperaban 5 pacientes sin obra social';
    ASSERT NOT EXISTS (SELECT 1 FROM obra_social o WHERE o.created_by = 'seed-script'
            AND (SELECT count(*) FROM plan pl WHERE pl.obra_social_id = o.id) <> 5),
        'Cada obra social debe tener 5 planes';
    ASSERT NOT EXISTS (SELECT 1 FROM especialidad e WHERE e.created_by = 'seed-script' AND e.codigo <> 'DEMO'
            AND (SELECT count(*) FROM prestacion p WHERE p.especialidad_id = e.id) <> 5),
        'Cada especialidad real debe tener 5 prestaciones';
    ASSERT NOT EXISTS (SELECT 1 FROM prestacion p JOIN seed_prestacion sp ON sp.codigo = p.codigo
            WHERE sp.orden = 1 AND EXISTS (SELECT 1 FROM indicacion_prestacion i WHERE i.prestacion_id = p.id)),
        'Las consultas no deben tener indicaciones';
    ASSERT NOT EXISTS (SELECT 1 FROM prestacion p JOIN seed_prestacion sp ON sp.codigo = p.codigo
            WHERE sp.orden <> 1 AND NOT EXISTS (SELECT 1 FROM indicacion_prestacion i WHERE i.prestacion_id = p.id)),
        'Toda prestación que no es consulta debe tener indicaciones';
    ASSERT NOT EXISTS (SELECT 1 FROM plan pl JOIN seed_plan sp ON sp.codigo = pl.codigo
            JOIN prestacion p ON p.codigo LIKE '%-CONS'
            WHERE NOT EXISTS (SELECT 1 FROM obra_social_plan_prestacion c WHERE c.plan_id = pl.id AND c.prestacion_id = p.id)),
        'Todo plan debe cubrir todas las consultas';
    ASSERT NOT EXISTS (SELECT 1 FROM medico m WHERE m.created_by = 'seed-script'
            AND NOT EXISTS (SELECT 1 FROM agenda_medico am WHERE am.medico_id = m.id)),
        'Todo médico debe tener agenda';
    ASSERT NOT EXISTS (SELECT 1 FROM agenda_medico am WHERE am.created_by = 'seed-script'
            AND NOT EXISTS (SELECT 1 FROM agenda_horarios_dia h WHERE h.agenda_medico_id = am.id)),
        'Toda agenda debe tener horarios';
    ASSERT NOT EXISTS (SELECT 1 FROM agenda_horarios_dia h
            JOIN agenda_medico am ON am.id = h.agenda_medico_id
            JOIN medico m ON m.id = am.medico_id
            JOIN prestacion p ON p.id = h.prestacion_id
            WHERE h.created_by = 'seed-script'
              AND (p.especialidad_id <> m.especialidad_id
                   OR NOT EXISTS (SELECT 1 FROM medico_prestacion mp
                                  WHERE mp.medico_id = m.id AND mp.prestacion_id = p.id
                                    AND mp.fecha_inicio_vigencia <= h.fecha
                                    AND (mp.fecha_fin_vigencia IS NULL OR mp.fecha_fin_vigencia >= h.fecha))
                   OR (h.hora_hasta - h.hora_desde) < p.duracion_minima
                   OR (h.hora_hasta - h.hora_desde) > p.duracion_maxima)),
        'Un horario viola especialidad, vigencia de MedicoPrestacion o rango de duración de la prestación';
    ASSERT NOT EXISTS (SELECT 1 FROM agenda_horarios_dia h JOIN clinica c ON true
            WHERE h.created_by = 'seed-script'
              AND (h.hora_desde < c.horario_inicio_atencion OR h.hora_hasta > c.horario_fin_atencion)),
        'Un horario cae fuera del horario de atención de la clínica';
END
$$;

COMMIT;
