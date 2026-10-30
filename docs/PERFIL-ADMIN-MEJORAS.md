# VetCare — Mejoras de Mi perfil ADMIN

28 de octubre de 2026. Rama: `mejoras-finales-vetcare`.

## Resultado

Se modificó exclusivamente el frontend integrado en `frontend/` y la validación del DTO de contacto ADMIN. Se conservaron endpoints, JWT, roles, contraseñas, datos existentes, migraciones y almacenamiento de fotografías. No se actualizaron contenedores ni se realizaron commits, push o merges.

El perfil tiene tres tarjetas coherentes con el perfil PROPIETARIO: identidad y fotografía; información protegida de la cuenta; contacto personal. El botón morado compacto con lápiz reemplaza la franja horizontal. Celular y dirección son informativos hasta editar; Guardar y Cancelar aparecen solo durante edición. El celular aparece una sola vez.

## Identidad real

Se consultaron únicamente ID, nombres y apellidos ADMIN en `vetcare_docker`, mediante `BEGIN READ ONLY`, SELECT y COMMIT. La tabla real es `roles`; una primera consulta con `rol` falló sin escribir y se corrigió después de revisar la entidad.

Resultado: usuario 1, nombres `Administrador`, apellidos `VetCare`. Esos valores genéricos están almacenados y no se sustituyeron por datos ficticios. Perfil, encabezado, menú y sidebar ya utilizan el perfil autenticado; la bienvenida ADMIN ahora incluye nombres y apellidos. El rol sigue separado.

Pendiente: proporcionar los nombres y apellidos del responsable y autorizar un procedimiento controlado que actualice exclusivamente esos campos del usuario 1, conservando su ID, credenciales, documento y rol. No se preparó ni ejecutó esa escritura. Una cuenta compartida o con identidad genérica permite identificar la cuenta, pero no atribuir con certeza cada operación a una persona; no se creó un nuevo sistema de auditoría.

## Correcciones funcionales

- Carga/error de perfil con reintento, sin edición mientras falta información.
- Cancelar descarta el borrador y limpia errores; no hace PATCH.
- Validación exclusiva de ADMIN: celular de nueve dígitos, comenzando por 9. Se valida en frontend y backend. Los registros antiguos permanecen intactos; al guardar desde el nuevo formulario deben cumplir la regla.
- Dirección opcional, máximo 200 caracteres, manteniendo la posibilidad de vaciarla.
- Errores discretos junto al campo y limpieza al corregir o cancelar.
- Sin diferencias no se envía PATCH; un bloqueo con ref evita envíos duplicados mientras se guarda.
- Un PATCH correcto conserva su respuesta en pantalla aunque falle la consulta posterior. El mensaje distingue ese caso de un guardado fallido y permite reintentar solo la consulta.
- Nombres, apellidos, correo, documento y rol no se envían en el PATCH de contacto.
- Selección de fotografía con vista previa y confirmación en Mi perfil y en el menú del encabezado ADMIN. Cancelar no sube la imagen.
- Se reutiliza la conversión existente a JPEG y el límite de 2 MB. Se admiten JPG/JPEG, PNG, WebP y AVIF si el navegador puede decodificarlos; no se amplían formatos de ADMIN.
- La opción `verify` de AuthContext vuelve a descargar y compara los bytes de la foto antes del éxito. La eliminación se confirma por un GET 404. Un fallo de comprobación avisa de que la escritura ocurrió sin afirmar que la comprobación fue exitosa. No hay reenvío automático del POST.
- El diálogo visual se comparte entre ambos selectores ADMIN. Las llamadas existentes de otros roles conservan el comportamiento predeterminado sin verificación adicional.

## Archivos modificados

- `frontend/src/pages/PerfilAdmin.jsx`: lectura/edición, identidad, estados, validación y fotografía.
- `frontend/src/pages/Perfil.css`: estilos adicionales limitados a `.admin-profile`.
- `frontend/src/contexts/AuthContext.jsx`: comprobación opcional de subida/eliminación, sin modificar autenticación.
- `frontend/src/components/Shell.jsx`: vista previa y confirmación del selector ADMIN del encabezado; conserva el flujo de otros roles.
- `frontend/src/components/HomeWelcome.jsx`: saludo ADMIN con nombres y apellidos reales; otros saludos intactos.
- `src/main/java/pe/edu/utec/vetcare/dto/request/ContactoAdminRequest.java`: patrón peruano exclusivo del contacto ADMIN.
- `src/test/java/pe/edu/utec/vetcare/integration/AdminHttpIT.java`: prueba de móviles inválidos y atomicidad del rechazo.

Archivos creados:

- `frontend/src/components/ProfilePhotoDialog.jsx`: diálogo de confirmación reutilizable.
- `frontend/src/lib/adminProfile.js`: borrador y errores exclusivos de ADMIN; no cambia `profile.js`.
- `frontend/tests/admin-profile.test.mjs`: tres pruebas específicas.
- `frontend/tests/visual/admin-profile.html`: prueba de componentes reales con adaptador HTTP sintético, sin API ni base real.
- `frontend/tests/visual/admin-profile-responsive.html`: repite el escenario a 375 px.
- `evidencias-perfil-admin/lectura.png`, `edicion.png`: capturas con identidad y datos exclusivamente sintéticos.
- Este informe.

## Pruebas ejecutadas

| Comprobación | Resultado |
|---|---|
| `npm run build` en frontend integrado | Correcto; advertencia de bundle mayor de 500 kB |
| `node --test tests/*.test.mjs` | 168 aprobadas, 0 fallos |
| ESLint de PerfilAdmin, adminProfile, ProfilePhotoDialog, HomeWelcome y Shell | Correcto |
| `git diff --check` | Correcto |
| Maven mediante `scripts/verificar_aislado.py` | BUILD SUCCESS: 72 unitarias y 107 de integración, 179 en total |
| Interfaz de escritorio | 29 comprobaciones aprobadas |
| Interfaz a 375 px | Las mismas 29 aprobadas; sin desbordamiento horizontal |
| Carga inicial y reintento tras error | Observados en navegador con fallo sintético; sin edición durante carga |
| Editar mediante teclado Enter | Correcto |
| Consola de la captura final | Sin errores observados |

Las pruebas de interfaz verifican lectura, identidad dinámica, un celular, Cancelar, ausencia de PATCH innecesario, errores por campo, doble clic, rechazo del PATCH, guardado con consulta fallida, vista previa/cancelación/confirmación, eliminación y verificación fallida sin falso éxito. Incluyen el selector del encabezado.

Maven se ejecutó en `/private/tmp/vetcare-verificacion-vrcynnkf`, excluyendo configuración habitual, `.env`, uploads y target. PostgreSQL 18 de Testcontainers usa `vetcare_p0_integration` y no admite fallback a una base real. Las migraciones existentes se ejecutaron solo para construir esa base temporal de pruebas. No se ejecutaron sobre `vetcare_docker` ni `vetcare_mejoras`.

## Fotografías, Docker y pendientes

El archivo sigue almacenándose mediante `FotoPerfilService`, sin modificaciones, dentro del volumen `/app/uploads`; PostgreSQL conserva su nombre en `usuario.foto_perfil`. No se cambió Compose, Dockerfile ni el volumen. La conservación durante reconstrucción se sostiene en la configuración de volúmenes revisada en la auditoría; no se hizo una reconstrucción real ni se afirma que se haya probado en esta tarea.

Antes de DonWeb:

1. Revisar y autorizar el despliegue posterior de frontend y backend juntos: la validación peruana también requiere actualizar el backend. El entorno Docker actual sigue con su versión anterior.
2. Validar, con una cuenta de prueba autorizada, guardar contacto, subir/reemplazar/quitar foto y consultar tras una nueva sesión en el despliegue real.
3. Verificar JPG/JPEG, PNG, WebP y AVIF con los navegadores objetivo. La prueba de interfaz convirtió un PNG sintético a JPEG; no se probó cada formato y navegador.
4. Probar persistencia tras recreación solo en un entorno desechable; conservar y respaldar tanto PostgreSQL como el volumen de uploads durante el despliegue.
5. Completar la identidad personal del ADMIN mediante autorización separada. No deducirla del correo ni inventarla.
6. Permanece la limitación previamente auditada de coordinación entre transacción SQL y archivos ante fallos excepcionales del servicio de fotos. Su endurecimiento no forma parte de esta corrección visual/funcional y no se introdujeron migraciones.

No se modificaron datos de las bases habituales, roles, contraseñas ni credenciales. No se leyeron archivos `.env`. No se recrearon contenedores ni volúmenes de VetCare.

## Repetir las validaciones

Desde la raíz del backend, con Docker activo para Testcontainers:

```sh
python3 scripts/verificar_aislado.py
```

Desde `frontend/`:

```sh
node --test tests/*.test.mjs
npm run build
```

Para revisar los componentes con datos sintéticos:

```sh
VITE_API_URL=http://127.0.0.1:1/api npm run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Abrir `/tests/visual/admin-profile.html?auto=1` y `/tests/visual/admin-profile-responsive.html`. El adaptador impide solicitudes al backend real. No ejecutar estos escenarios contra las bases habituales.
