\set ON_ERROR_STOP on

BEGIN;

-- SEGURIDAD: solo en nuestra base Docker de demostración.
DO $$
BEGIN
    IF current_database() <> 'vetcare_docker' THEN
        RAISE EXCEPTION 'Base de datos no autorizada';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_extension WHERE extname = 'pgcrypto'
    ) THEN
        RAISE EXCEPTION 'Falta pgcrypto';
    END IF;

    IF (SELECT COUNT(*) FROM usuario) <> 1
       OR (SELECT COUNT(*) FROM veterinario) <> 0
       OR (SELECT COUNT(*) FROM propietario) <> 0
       OR (SELECT COUNT(*) FROM mascota) <> 0 THEN
        RAISE EXCEPTION
          'La base ya contiene datos. No se insertó nada.';
    END IF;
END $$;

-- 1. Usuarios de prueba: 4 veterinarios y 8 propietarios.
-- Contraseña de todos: VetCare2026!
WITH datos(nombres, apellidos, documento, correo, rol) AS (
    VALUES
    ('Sofia', 'Ramirez', '80000001', 'sofia@demo.vetcare.test', 'VETERINARIO'),
    ('Mateo', 'Torres', '80000002', 'mateo@demo.vetcare.test', 'VETERINARIO'),
    ('Valeria', 'Castro', '80000003', 'valeria@demo.vetcare.test', 'VETERINARIO'),
    ('Diego', 'Flores', '80000004', 'diego@demo.vetcare.test', 'VETERINARIO'),

    ('Luciana', 'Perez', '80000005', 'luciana@demo.vetcare.test', 'PROPIETARIO'),
    ('Andres', 'Lopez', '80000006', 'andres@demo.vetcare.test', 'PROPIETARIO'),
    ('Camila', 'Rojas', '80000007', 'camila@demo.vetcare.test', 'PROPIETARIO'),
    ('Carlos', 'Mendoza', '80000008', 'carlos@demo.vetcare.test', 'PROPIETARIO'),
    ('Daniela', 'Vargas', '80000009', 'daniela@demo.vetcare.test', 'PROPIETARIO'),
    ('Fernando', 'Silva', '80000010', 'fernando@demo.vetcare.test', 'PROPIETARIO'),
    ('Mariana', 'Diaz', '80000011', 'mariana@demo.vetcare.test', 'PROPIETARIO'),
    ('Ricardo', 'Leon', '80000012', 'ricardo@demo.vetcare.test', 'PROPIETARIO')
)
INSERT INTO usuario (
    nombres, apellidos, tipo_documento, nro_documento,
    correo, contrasena, id_rol, fecha_registro
)
SELECT
    d.nombres, d.apellidos, 'DNI', d.documento,
    d.correo,
    crypt('VetCare2026!', gen_salt('bf', 10)),
    r.id_rol,
    CURRENT_TIMESTAMP
FROM datos d
JOIN roles r ON r.nombre = d.rol;

-- 2. Perfiles de veterinarios.
INSERT INTO veterinario (
    id_usuario, especialidad, nro_colegiatura,
    tratamiento_profesional
)
SELECT
    u.id_usuario,
    d.especialidad,
    d.colegiatura,
    d.tratamiento
FROM (
    VALUES
    ('sofia@demo.vetcare.test', 'Medicina general', 'CMVP-8001', 'Dra.'),
    ('mateo@demo.vetcare.test', 'Cirugia', 'CMVP-8002', 'Dr.'),
    ('valeria@demo.vetcare.test', 'Dermatologia', 'CMVP-8003', 'Dra.'),
    ('diego@demo.vetcare.test', 'Medicina interna', 'CMVP-8004', 'Dr.')
) AS d(correo, especialidad, colegiatura, tratamiento)
JOIN usuario u ON u.correo = d.correo;

-- 3. Perfiles de propietarios.
INSERT INTO propietario (
    id_usuario, direccion, fecha_registro_clinica, estado_cliente
)
SELECT
    u.id_usuario,
    'Lima, Peru',
    CURRENT_DATE,
    'ACTIVO'
FROM usuario u
JOIN roles r ON r.id_rol = u.id_rol
WHERE r.nombre = 'PROPIETARIO';

-- 4. Mascotas relacionadas con sus propietarios.
WITH datos(correo, nombre, especie, raza, sexo, nacimiento) AS (
    VALUES
    ('luciana@demo.vetcare.test', 'Luna', 'GATO', 'Mestizo', 'HEMBRA', DATE '2022-04-12'),
    ('luciana@demo.vetcare.test', 'Copito', 'PERRO', 'Poodle', 'MACHO', DATE '2021-08-20'),

    ('andres@demo.vetcare.test', 'Max', 'PERRO', 'Labrador', 'MACHO', DATE '2020-11-05'),
    ('andres@demo.vetcare.test', 'Mia', 'GATO', 'Siames', 'HEMBRA', DATE '2023-02-14'),

    ('camila@demo.vetcare.test', 'Toby', 'PERRO', 'Beagle', 'MACHO', DATE '2021-06-10'),
    ('camila@demo.vetcare.test', 'Nala', 'GATO', 'Mestizo', 'HEMBRA', DATE '2022-10-01'),

    ('carlos@demo.vetcare.test', 'Rocky', 'PERRO', 'Bulldog', 'MACHO', DATE '2019-05-03'),
    ('carlos@demo.vetcare.test', 'Kiara', 'GATO', 'Persa', 'HEMBRA', DATE '2023-01-09'),

    ('daniela@demo.vetcare.test', 'Bella', 'PERRO', 'Golden Retriever', 'HEMBRA', DATE '2020-03-18'),
    ('daniela@demo.vetcare.test', 'Simba', 'GATO', 'Mestizo', 'MACHO', DATE '2021-12-23'),

    ('fernando@demo.vetcare.test', 'Bruno', 'PERRO', 'Schnauzer', 'MACHO', DATE '2022-07-08'),
    ('fernando@demo.vetcare.test', 'Kira', 'PERRO', 'Mestizo', 'HEMBRA', DATE '2024-01-15'),

    ('mariana@demo.vetcare.test', 'Chispa', 'GATO', 'Mestizo', 'HEMBRA', DATE '2023-09-11'),

    ('ricardo@demo.vetcare.test', 'Bobby', 'PERRO', 'Cocker Spaniel', 'MACHO', DATE '2020-02-26')
)
INSERT INTO mascota (
    id_propietario, nombre, especie, raza, sexo, fecha_nacimiento
)
SELECT
    p.id_propietario,
    d.nombre,
    d.especie,
    d.raza,
    d.sexo,
    d.nacimiento
FROM datos d
JOIN usuario u ON u.correo = d.correo
JOIN propietario p ON p.id_usuario = u.id_usuario;

-- 5. Historia clínica inicial de cada mascota.
INSERT INTO historia_clinica (
    id_mascota,
    fecha_apertura,
    estado_alergias,
    version_antecedentes
)
SELECT
    m.id_mascota,
    CURRENT_TIMESTAMP,
    'NO_EVALUADAS',
    0
FROM mascota m;

-- 6. Comprobar las cantidades antes del COMMIT.
DO $$
BEGIN
    IF (SELECT COUNT(*) FROM usuario) <> 13
       OR (SELECT COUNT(*) FROM veterinario) <> 4
       OR (SELECT COUNT(*) FROM propietario) <> 8
       OR (SELECT COUNT(*) FROM mascota) <> 14
       OR (SELECT COUNT(*) FROM historia_clinica) <> 14 THEN
        RAISE EXCEPTION
          'Las cantidades no coinciden. Se cancelará la carga.';
    END IF;
END $$;

COMMIT;

SELECT 'CARGA COMPLETADA' AS resultado;

SELECT 'usuarios' AS tabla, COUNT(*) AS cantidad FROM usuario
UNION ALL SELECT 'veterinarios', COUNT(*) FROM veterinario
UNION ALL SELECT 'propietarios', COUNT(*) FROM propietario
UNION ALL SELECT 'mascotas', COUNT(*) FROM mascota
UNION ALL SELECT 'historias clinicas', COUNT(*) FROM historia_clinica;
