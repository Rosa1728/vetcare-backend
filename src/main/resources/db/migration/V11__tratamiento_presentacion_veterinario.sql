-- Presentación opcional, explícita y separada del nombre legal. No infiere sexo.
ALTER TABLE veterinario ADD COLUMN tratamiento_profesional VARCHAR(4);
ALTER TABLE veterinario ADD CONSTRAINT ck_veterinario_tratamiento_profesional
    CHECK (tratamiento_profesional IS NULL OR tratamiento_profesional IN ('Dr.', 'Dra.'));
