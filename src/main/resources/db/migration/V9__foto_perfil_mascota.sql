-- Foto privada del avatar; no altera los registros ni las migraciones previas.
ALTER TABLE mascota ADD COLUMN foto_perfil BYTEA;
