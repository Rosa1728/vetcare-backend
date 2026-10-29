CREATE TABLE historia_publicacion (
 id_publicacion BIGSERIAL PRIMARY KEY,
 id_mascota BIGINT NOT NULL REFERENCES mascota(id_mascota),
 titulo VARCHAR(120) NOT NULL,
 categoria VARCHAR(40) NOT NULL,
 contenido VARCHAR(3000) NOT NULL,
 foto_archivo VARCHAR(100),
 fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_historia_publicacion_fecha ON historia_publicacion(fecha_creacion DESC);
CREATE INDEX idx_historia_publicacion_mascota ON historia_publicacion(id_mascota);
