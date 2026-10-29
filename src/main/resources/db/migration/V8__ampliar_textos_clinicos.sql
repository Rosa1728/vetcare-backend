-- Amplía los textos clínicos sin alterar migraciones anteriores ni eliminar datos.
ALTER TABLE atenciones
    ALTER COLUMN sintoma TYPE VARCHAR(500),
    ALTER COLUMN diagnostico TYPE VARCHAR(500),
    ALTER COLUMN tratamiento TYPE VARCHAR(500),
    ALTER COLUMN observacion TYPE VARCHAR(500);
