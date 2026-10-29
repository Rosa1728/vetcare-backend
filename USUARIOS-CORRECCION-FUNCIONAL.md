# ADMIN → Usuarios: corrección funcional y seguridad

28/10/2026 · Rama comprobada: `mejoras-finales-vetcare`.

## 1. Causa confirmada del error de roles

El frontend solicita `GET /api/usuarios/roles`. Esa ruta **ya existe en el código actual** de `UsuarioController`, devuelve una lista de objetos `{idRol, nombre}` desde la tabla real de roles y exige ADMIN. La configuración Axios utiliza el JWT de sesión; el frontend Docker usa `/api` como base. No hizo falta añadir otro endpoint ni inventar números de rol.

Se inspeccionó de forma exclusivamente de lectura el controlador compilado dentro de `/app/app.jar` del contenedor activo `vetcare-backend-1`. Se extrajo únicamente `UsuarioController.class` para comprobar sus rutas, sin leer configuración ni credenciales. Resultado:

```json
{"controlador_empaquetado":true,"ruta_roles_presente":false,"metodo_rolesDisponibles_presente":false,"ruta_id_presente":true}
```

**El backend activo está desactualizado respecto al código utilizado por el frontend.** No contiene el endpoint requerido. No se capturaron credenciales o JWT personales ni se hizo una petición autenticada a la instalación habitual; el código HTTP concreto de esa sesión no se afirma como observado. La ausencia de la ruta en el binario sí se verificó directamente.

En las pruebas aisladas, el controlador del código actual devuelve HTTP 200 para ADMIN y 403 para PROPIETARIO/VETERINARIO; sin autenticación devuelve 401. La prueba verifica que los identificadores del catálogo coincidan con los roles reales de PostgreSQL temporal.

**Estado de Nuevo usuario:** funciona y está probado con el código actual en entorno aislado. En `localhost:8088` necesita desplegar ese backend; no puede recuperarse una ruta ausente cambiando únicamente el CSS o fingiendo opciones en el cliente. No se reconstruyeron contenedores, respetando la restricción de esta tarea.

## 2. Correcciones implementadas

- Listado y catálogo de roles cargan independientemente, con estados y control de respuestas obsoletas separados. Un catálogo lento o fallido ya no retiene la carga del listado.
- Reintentar roles consulta únicamente el catálogo y recupera la creación cuando la respuesta vuelve a ser válida. Se elimina el aviso al resolver el problema.
- Catálogo validado: lista real, IDs numéricos positivos, nombres autorizados y sin duplicados. Catálogo vacío, respuesta incompatible, sesión vencida, permiso denegado y servicio ausente se distinguen sin mostrar trazas.
- Nuevo usuario se habilita cuando están disponibles los roles válidos. No hay IDs de rol ficticios ni alternativas que eludan la autorización.
- Validación de formulario junto a cada campo para documento, celular, contraseña y duplicidades devueltas por el backend. Se conserva el borrador y se limpia el error del campo al corregirlo. Se reutiliza la política compartida de contraseña; no se modifica BCrypt ni el cambio de contraseña personal.
- Seleccionar ADMIN muestra una advertencia discreta sobre sus privilegios. Se conserva la confirmación adicional antes del POST y la comprobación del backend.
- Avatares de iniciales lavanda `#EDE9FE`, texto `#3B2382`. Las fotos existentes mantienen su lectura autorizada y el encuadre circular. No se cambian indicadores positivos de otros módulos.
- Se conservan las cinco columnas, documentos separados y ceros iniciales, búsqueda compacta, filtros, paginación, Ver y Editar.
- Confirmación de eliminación con advertencia en rojo pastel y botón «Eliminar cuenta» suave. Cancelar y Escape no envían DELETE. La redacción describe el bloqueo real de cuentas con perfiles asociados, sin prometer funcionalidades inexistentes.

## 3. Auditoría de eliminación y permisos

Se revisaron `UsuarioController`, `UsuarioServiceImpl`, `UsuarioRepository`, `RolRepository`, DTO, mapper, `SecurityConfig`, entidad Usuario y el manejo de errores. No fue necesario cambiar código de producción del backend en esta intervención.

El código actual conserva:

- ADMIN como requisito de acceso y de servicio, también ante una llamada REST directa.
- Rechazo de autoeliminación: HTTP 400.
- Protección del último ADMIN: HTTP 409, con bloqueo transaccional para serializar las eliminaciones ordinarias concurrentes.
- Bloqueo de cualquier cuenta con perfil de propietario o veterinario: HTTP 409, incluso si ese perfil aún no tiene consultas. Por tanto, una cuenta vinculada con mascotas/citas/atenciones/vacunas conserva sus relaciones al bloquearse antes del DELETE.
- Integridad referencial y ausencia de borrado de datos clínicos en cascada desde esta operación.
- Restricción de cambio de rol durante Editar, preservación de contraseña y vínculos, y comprobación de correo/rol vigentes en JWT.

La eliminación física solo procede para cuentas no protegidas por esas reglas; no se añadió eliminación lógica ni estado activo ficticio. La protección de último ADMIN se refiere a las cuentas ADMIN existentes, porque no se inventó un nuevo estado de activación. SQL manual externo no está cubierto por el bloqueo del servicio.

Estas protecciones están verificadas en el **código actual y PostgreSQL aislado**. No se ejecutó ningún DELETE habitual ni se da por demostrado que el contenedor antiguo contenga todas las protecciones nuevas. Debe desplegarse coordinadamente antes de considerarlas operativas en la instalación habitual.

## 4. Archivos de esta intervención

Modificados:

1. `frontend/src/pages/Usuarios.jsx`: cargas independientes, reintento, aviso ADMIN, validación y confirmación.
2. `frontend/src/pages/Usuarios.css`: avatares lavanda, aviso contextual, errores de campos y advertencia/botones suaves.
3. `frontend/src/lib/users.js`: lectura segura del catálogo, mensajes y validación del formulario.
4. `frontend/src/components/EditorForm.jsx`: soporte opcional de avisos de campo y reutilización de los errores por campo en Usuarios. Se conserva la variante de Veterinarios.
5. `frontend/tests/users.test.mjs`: cuatro pruebas nuevas de catálogo, errores y validaciones.
6. `frontend/tests/visual/users.html`: pruebas ampliadas para carga lenta, error, catálogo vacío/incompatible, reintento, avatares y aviso ADMIN.
7. `src/test/java/pe/edu/utec/vetcare/integration/UsuariosHttpIT.java`: dos pruebas nuevas de catálogo/creación/duplicidad sin cuentas parciales y conservación de cuenta/perfil veterinario ante DELETE REST.

Creados: este informe y `evidencias-usuarios-correccion/` con `listado.png`, `nuevo-usuario.png` y `eliminar.png`.

No se modificaron controladores, DTO, servicios, entidades o migraciones de producción en esta intervención. Se preservaron los cambios locales anteriores.

## 5. Resultados

| Verificación | Resultado |
|---|---|
| Backend Maven en copia aislada con PostgreSQL 18 temporal | **196 correctas: 72 unitarias + 124 de integración**, sin fallos, errores ni omitidas |
| UsuariosHttpIT | **10 correctas**, incluidas las dos nuevas |
| Frontend Node completo | **183 correctas**, sin fallos |
| Usuarios, error de roles y recuperación | **30 comprobaciones correctas** |
| Usuarios, catálogo vacío y recuperación | **30 comprobaciones correctas** |
| Usuarios, respuesta incompatible y recuperación | **30 comprobaciones correctas** |
| Usuarios, carga lenta de roles | **29 comprobaciones correctas** |
| Usuarios, móvil de 375 px | **28 comprobaciones correctas** |
| Regresión visual Veterinarios | **31 comprobaciones correctas** |
| Regresión Mi perfil / cambio de contraseña ADMIN | **19 comprobaciones correctas** |
| Build frontend | Correcto; advertencia de chunk superior a 500 kB |
| ESLint de archivos intervenidos | Correcto |
| Lint global | Persisten **7 errores y 1 advertencia previos**, fuera de esta tarea |
| git diff --check | Correcto |

Las pruebas de interfaz ejecutan componentes reales con un adaptador HTTP de prueba **en memoria**; sus capturas contienen datos sintéticos. La persistencia y los códigos HTTP reales se verificaron mediante MockMvc y Testcontainers. Las escrituras y eliminaciones de pruebas se limitaron a PostgreSQL temporal `vetcare_p0_integration`, nunca a `vetcare_docker` ni `vetcare_mejoras`.

Maven: `/private/tmp/vetcare-verificacion-syj3m3ad/target`; log `/private/tmp/vetcare-users-correccion-backend.log`. Logs frontend: `/private/tmp/vetcare-users-correccion-tests.log`, `/private/tmp/vetcare-users-correccion-build.log`, `/private/tmp/vetcare-users-correccion-lint.log`, `/private/tmp/vetcare-users-correccion-global-lint.log`.

## 6. Pendientes y límites

- Desplegar backend y frontend de forma coordinada para que `localhost:8088` utilice la ruta de roles ya implementada. Actualizar únicamente frontend no corrige la ausencia del endpoint en el backend activo.
- Verificar después del despliegue mediante lectura que ADMIN obtiene el catálogo y que Nuevo usuario abre el formulario. No crear ni eliminar cuentas habituales como prueba sin autorización específica.
- No se comprobó el esquema de las bases habituales ni se ejecutaron migraciones sobre ellas. Esta corrección no añade requisitos de esquema; el código completo conserva los requisitos de las fases anteriores.
- La validación específica de tipo de documento del formulario no convierte automáticamente las reglas generales del DTO en una nueva política universal de la API. Se conservaron las restricciones backend existentes; no se normalizaron datos históricos.
- Lint global pendiente en App, RecordatoriosBell, ResourcePage, VetDateTimePicker, AuthContext y MisPublicaciones. No se corrigieron para evitar cambios ajenos al alcance.
- Mantener la comprobación de esquema/versión en futuros despliegues para evitar servir un frontend más nuevo que su backend.

## 7. Repetir pruebas de forma segura

Desde la raíz:

```sh
python3 scripts/verificar_aislado.py
cd frontend
node --test tests/*.test.mjs
npx eslint src/pages/Usuarios.jsx src/lib/users.js src/components/EditorForm.jsx
npm run build
npm run lint
```

Para interfaz aislada desde `frontend/`:

```sh
VITE_API_URL=http://127.0.0.1:1/api npm run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Abrir bajo `http://127.0.0.1:5174`:

- `/tests/visual/users.html?auto=1&roles=error`
- `/tests/visual/users.html?auto=1&roles=empty`
- `/tests/visual/users.html?auto=1&roles=malformed`
- `/tests/visual/users.html?auto=1&roles=slow`
- `/tests/visual/users-responsive.html`

## 8. Actualización posterior: preparada, NO ejecutada

El Compose existente habilita Flyway. Para no ejecutar migraciones accidentalmente, usar una sobreescritura temporal explícita, sin editar `.env` o el Compose del repositorio. La validación de Hibernate propuesta solo consulta el esquema y detiene el arranque si no corresponde al código; no lo modifica.

Ejecutar únicamente después de revisar y autorizar el despliegue:

```sh
cd '/Users/rosapp/Desktop/Proyecto Vetcare/vetcare-clinica-veterinaria'
docker compose build backend frontend
cat > /tmp/vetcare-usuarios-sin-migraciones.yaml <<'YAML'
services:
  backend:
    environment:
      SPRING_FLYWAY_ENABLED: "false"
      SPRING_JPA_HIBERNATE_DDL_AUTO: validate
YAML
docker compose -f compose.yaml -f /tmp/vetcare-usuarios-sin-migraciones.yaml up -d --no-deps backend frontend
```

Esto actualiza los dos servicios necesarios sin recrear PostgreSQL ni eliminar volúmenes. Si la validación detecta un esquema pendiente, detener el despliegue y revisar; no habilitar Flyway como solución automática. Mantener la sobreescritura mientras las migraciones deban permanecer desactivadas.

Después, abrir `http://localhost:8088` e iniciar sesión normalmente. No pegar tokens en comandos, informes ni capturas.

## 9. Restricciones respetadas

No se modificaron bases habituales, registros, fotos persistidas, `.env`, credenciales ni volúmenes. No se reconstruyeron o reiniciaron los contenedores habituales. La inspección de Docker fue de lectura. No se ejecutó Flyway sobre bases habituales. Sin commits, push, merges o cambios de rama.
