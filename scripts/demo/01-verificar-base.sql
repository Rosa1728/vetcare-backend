\set ON_ERROR_STOP on

DO $$
BEGIN
    IF current_database() <> 'vetcare_docker' THEN
        RAISE EXCEPTION 'SEGURIDAD: base no autorizada';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_extension
        WHERE extname = 'pgcrypto'
    ) THEN
        RAISE EXCEPTION 'Falta pgcrypto';
    END IF;

    IF (
        SELECT COUNT(*)
        FROM roles
        WHERE nombre IN ('ADMIN', 'PROPIETARIO', 'VETERINARIO')
    ) <> 3 THEN
        RAISE EXCEPTION 'Faltan roles del sistema';
    END IF;
END $$;

SELECT 'BASE VERIFICADA' AS resultado,
       current_database() AS base_datos;

SELECT 'usuarios' AS tabla, COUNT(*) FROM usuario
UNION ALL SELECT 'veterinarios', COUNT(*) FROM veterinario
UNION ALL SELECT 'propietarios', COUNT(*) FROM propietario
UNION ALL SELECT 'mascotas', COUNT(*) FROM mascota
UNION ALL SELECT 'citas', COUNT(*) FROM cita
UNION ALL SELECT 'atenciones', COUNT(*) FROM atenciones
UNION ALL SELECT 'vacunas', COUNT(*) FROM vacuna;
