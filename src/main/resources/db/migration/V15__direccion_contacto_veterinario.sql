-- Contacto personal del veterinario, vinculado al usuario.
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS direccion_contacto VARCHAR(200);
