\set ON_ERROR_STOP on

BEGIN;

-- Verificar que estamos en la base de demostración.
DO $$
BEGIN
    IF current_database() <> 'vetcare_docker' THEN
        RAISE EXCEPTION 'Base no autorizada';
    END IF;

    IF (SELECT COUNT(*) FROM usuario) <> 13
       OR (SELECT COUNT(*) FROM mascota) <> 14
       OR (SELECT COUNT(*) FROM veterinario) <> 4
       OR (SELECT COUNT(*) FROM historia_clinica) <> 14
       OR (SELECT COUNT(*) FROM cita) <> 0
       OR (SELECT COUNT(*) FROM atenciones) <> 0
       OR (SELECT COUNT(*) FROM vacuna) <> 0 THEN
        RAISE EXCEPTION 'La base no está en el estado esperado. No se insertó nada.';
    END IF;
END $$;

-- 1. Crear citas pasadas y futuras.
WITH datos(correo, mascota, veterinario, dias, estado, motivo) AS (
    VALUES
    ('luciana@demo.vetcare.test','Luna','sofia@demo.vetcare.test',-30,'ATENDIDA','Control general'),
    ('luciana@demo.vetcare.test','Copito','mateo@demo.vetcare.test',-20,'ATENDIDA','Consulta digestiva'),
    ('andres@demo.vetcare.test','Max','valeria@demo.vetcare.test',-14,'ATENDIDA','Revisión de piel'),
    ('andres@demo.vetcare.test','Mia','diego@demo.vetcare.test',-7,'ATENDIDA','Control preventivo'),
    ('camila@demo.vetcare.test','Toby','mateo@demo.vetcare.test',-3,'ATENDIDA','Revisión clínica'),
    ('camila@demo.vetcare.test','Nala','mateo@demo.vetcare.test',1,'CONFIRMADA','Evaluación general'),
    ('carlos@demo.vetcare.test','Rocky','sofia@demo.vetcare.test',2,'CONFIRMADA','Control de salud'),
    ('carlos@demo.vetcare.test','Kiara','valeria@demo.vetcare.test',3,'CONFIRMADA','Evaluación dermatológica'),
    ('daniela@demo.vetcare.test','Bella','diego@demo.vetcare.test',4,'PENDIENTE','Consulta preventiva'),
    ('daniela@demo.vetcare.test','Simba','sofia@demo.vetcare.test',5,'PENDIENTE','Control general'),
    ('fernando@demo.vetcare.test','Bruno','mateo@demo.vetcare.test',6,'PENDIENTE','Chequeo anual'),
    ('fernando@demo.vetcare.test','Kira','diego@demo.vetcare.test',-2,'CANCELADA','Consulta cancelada')
)
INSERT INTO cita (
    id_veterinario, id_mascota, fecha_cita,
    estado, motivo_cita
)
SELECT
    v.id_veterinario,
    m.id_mascota,
    (CURRENT_DATE + d.dias) + TIME '10:00',
    d.estado,
    d.motivo
FROM datos d
JOIN usuario propietario_usuario
    ON propietario_usuario.correo = d.correo
JOIN propietario p
    ON p.id_usuario = propietario_usuario.id_usuario
JOIN mascota m
    ON m.id_propietario = p.id_propietario
   AND m.nombre = d.mascota
JOIN usuario veterinario_usuario
    ON veterinario_usuario.correo = d.veterinario
JOIN veterinario v
    ON v.id_usuario = veterinario_usuario.id_usuario;

-- 2. Atenciones para las cinco citas atendidas.
INSERT INTO atenciones (
    id_historia, id_veterinario_atencion,
    id_cita, fecha_atencion,
    precio_atencion, peso, sintoma,
    diagnostico, tratamiento, observacion
)
SELECT
    h.id_historia,
    c.id_veterinario,
    c.id_cita,
    c.fecha_cita + INTERVAL '20 minutes',
    85.00,
    8.50,
    'Consulta de evaluación clínica',
    'Evaluación general completada',
    'Seguimiento preventivo y recomendaciones de cuidado',
    'Paciente estable durante la consulta'
FROM cita c
JOIN historia_clinica h ON h.id_mascota = c.id_mascota
WHERE c.estado = 'ATENDIDA';

-- 3. Vacunas aplicadas vinculadas a las atenciones.
INSERT INTO vacuna (
    id_mascota, id_veterinario, id_atencion,
    nombre, fecha_aplicacion, proxima_dosis, observaciones
)
SELECT
    c.id_mascota,
    c.id_veterinario,
    a.id_atencion,
    'Vacuna antirrábica',
    c.fecha_cita::date,
    (c.fecha_cita::date + INTERVAL '1 year')::date,
    'Aplicada durante atención de demostración'
FROM atenciones a
JOIN cita c ON c.id_cita = a.id_cita;

-- 4. Vacunas pendientes de aplicación.
INSERT INTO vacuna (
    id_mascota, id_veterinario,
    nombre, proxima_dosis, fecha_programada, observaciones
)
SELECT
    c.id_mascota,
    c.id_veterinario,
    'Vacuna múltiple',
    c.fecha_cita::date,
    c.fecha_cita::date,
    'Vacuna programada para demostración'
FROM cita c
WHERE c.estado IN ('PENDIENTE', 'CONFIRMADA')
ORDER BY c.fecha_cita
LIMIT 5;

-- 5. Verificar la carga completa.
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM cita) <> 12
       OR (SELECT COUNT(*) FROM atenciones) <> 5
       OR (SELECT COUNT(*) FROM vacuna) <> 10 THEN
        RAISE EXCEPTION 'Cantidades incorrectas. Se cancela la transacción.';
    END IF;
END $$;

COMMIT;

SELECT 'CARGA CLINICA COMPLETADA' AS resultado;

SELECT 'citas' AS tabla, COUNT(*) AS cantidad FROM cita
UNION ALL
SELECT 'atenciones', COUNT(*) FROM atenciones
UNION ALL
SELECT 'vacunas', COUNT(*) FROM vacuna;

SELECT estado, COUNT(*) AS cantidad
FROM cita
GROUP BY estado
ORDER BY estado;
