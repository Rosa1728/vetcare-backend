-- Conserva la programación original sin reconstruir fechas históricas desconocidas.
ALTER TABLE vacuna ADD COLUMN fecha_programada DATE;
ALTER TABLE vacuna ADD COLUMN id_vacuna_origen BIGINT REFERENCES vacuna(id_vacuna);
ALTER TABLE vacuna ADD CONSTRAINT uq_vacuna_dosis_siguiente UNIQUE (id_vacuna_origen);
ALTER TABLE vacuna ADD CONSTRAINT ck_vacuna_origen_distinto CHECK (id_vacuna_origen IS NULL OR id_vacuna_origen <> id_vacuna);
