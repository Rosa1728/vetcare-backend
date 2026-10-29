-- Ampliación de la única historia clínica. Los registros existentes quedan NO_EVALUADAS.
ALTER TABLE historia_clinica
    ADD COLUMN estado_alergias VARCHAR(30) NOT NULL DEFAULT 'NO_EVALUADAS',
    ADD COLUMN alergias_sustancias VARCHAR(2000),
    ADD COLUMN alergias_reacciones VARCHAR(2000),
    ADD COLUMN alergias_observaciones VARCHAR(2000),
    ADD COLUMN enfermedades_relevantes VARCHAR(4000),
    ADD COLUMN cirugias_previas VARCHAR(4000),
    ADD COLUMN notas_medicas VARCHAR(4000),
    ADD COLUMN alertas_medicas VARCHAR(2000),
    ADD COLUMN antecedentes_actualizados_en TIMESTAMP(6),
    ADD COLUMN antecedentes_id_veterinario BIGINT REFERENCES veterinario(id_veterinario),
    ADD CONSTRAINT ck_historia_estado_alergias CHECK (estado_alergias IN
        ('NO_EVALUADAS', 'SIN_ALERGIAS_CONOCIDAS', 'ALERGIAS_REGISTRADAS'));
