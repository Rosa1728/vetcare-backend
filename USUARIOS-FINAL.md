# ADMIN → Usuarios: entrega de implementación

Fecha: 28/10/2026. Rama comprobada: `mejoras-finales-vetcare`.

## Resultado funcional

- Listado conectado a `GET /api/usuarios`: cinco columnas (Usuario, Tipo de documento, N.º de documento, Rol, Acciones). Se conserva el identificador interno, sin mostrarlo. Documentos tratados como texto, incluidos ceros iniciales.
- Avatar circular de 38 px mediante el endpoint privado existente `GET /api/usuarios/{id}/foto`, solo para ADMIN; iniciales si no hay foto o falla. Caché por cuenta compartida entre listado y detalle, cancelación de solicitudes y liberación de URLs al actualizar/desmontar.
- Búsqueda por nombre completo, correo o documento, tolerante a acentos; filtro combinado por rol. Buscador de hasta 520 px, selector de 190 px. Paginación cliente de 10 filas sobre **todos los usuarios recuperados**, sin fingir paginación de servidor ni descartar resultados.
- Ver consulta `GET /api/usuarios/{id}` antes de presentar el detalle. Modal de 580 px, centrado, desplazamiento interno, cierre X/Cerrar/Escape y control de foco. Datos reales de identidad/contacto/cuenta y fecha de registro existente. Dirección del propietario consultada del perfil asociado cuando existe. Se omiten campos ausentes; no se inventan estado o último acceso.
- Ver → Editar conserva la identidad interna. Edición consulta datos actuales, conserva rol, contraseña y vínculos; errores mantienen el borrador. Cambio del correo propio cierra la sesión y conduce a Login con aviso.
- Crear utiliza `GET /api/usuarios/roles` para identificar los roles reales, sin asumir sus números. Formulario compacto por Identificación, Contacto y Acceso, con política de contraseña compartida. Crear ADMIN exige confirmación adicional tanto en interfaz como en backend.
- Se mantiene el flujo existente: crear un usuario crea una **cuenta**. Los perfiles profesionales y de propietario se vinculan desde sus módulos correspondientes; no se crean perfiles duplicados automáticamente.
- Eliminar exige confirmación y una sola petición mientras está en curso. Backend verifica ADMIN, impide autoeliminación, protege al último ADMIN y bloquea cuentas con perfil de veterinario o propietario. No se añade borrado en cascada ni eliminación lógica.
- El bloqueo de la fila del rol ADMIN serializa las eliminaciones ordinarias para conservar al menos un administrador incluso ante dos solicitudes simultáneas. Esto no protege frente a modificaciones SQL externas al servicio.
- JWT conserva firma, expiración y verificación de credencial previamente implementadas. Además, compara correo y rol con los datos vigentes: un token anterior deja de ser válido después de cambiarlos. La carga del rol se hace en la misma consulta del usuario.

## Archivos de esta intervención

Rutas relativas a la raíz del repositorio; no representan todos los cambios locales anteriores que ya existían.

### Frontend modificados

- `frontend/src/pages/Usuarios.jsx`: listado y flujos reales de consulta/creación/edición/eliminación.
- `frontend/src/components/EditorForm.jsx`: opciones compatibles para secciones, estilo Usuarios y confirmación previa al envío; comportamiento predeterminado conservado.
- `frontend/src/components/VetcareModal.jsx`: clase opcional para dimensionar el detalle. Estilos específicos del módulo limitados a Usuarios.

### Frontend creados

- `frontend/src/pages/Usuarios.css`.
- `frontend/src/components/UserAvatar.jsx`.
- `frontend/src/lib/users.js`.
- `frontend/src/lib/useUserPhotos.js`.
- `frontend/tests/users.test.mjs`.
- `frontend/tests/visual/users.html`.
- `frontend/tests/visual/users-responsive.html`.

### Backend modificados

Todos bajo `src/main/java/pe/edu/utec/vetcare/`:

- `controller/UsuarioController.java`: catálogo autorizado de roles.
- `dto/request/UsuarioRequest.java`: confirmación ADMIN y límites compatibles con las columnas existentes.
- `dto/request/ActualizarUsuarioRequest.java`: recepción explícita de idRol para rechazar cambios indebidos.
- `dto/response/UsuarioResponse.java`: disponibilidad de fotografía, sin exponer su contenido ni secretos en JSON.
- `mapper/UsuarioMapper.java`: mapeo de disponibilidad de fotografía.
- `repository/UsuarioRepository.java`: conteo de administradores y consulta de usuario con rol para validar JWT.
- `repository/RolRepository.java`: bloqueo transaccional usado al eliminar.
- `service/impl/UsuarioServiceImpl.java`: autorización, detalle, confirmación y restricciones de edición/eliminación.
- `security/SecurityConfig.java`: rechazo de JWT con correo o rol desactualizados.

### Pruebas backend y evidencias

- Modificado `src/test/java/pe/edu/utec/vetcare/integration/AdminHttpIT.java`: expectativas ajustadas al rechazo explícito del cambio de rol y al JWT invalidado tras cambiar correo.
- Creado `src/test/java/pe/edu/utec/vetcare/integration/UsuariosHttpIT.java`: ocho pruebas nuevas.
- Creados `evidencias-usuarios/listado.png`, `evidencias-usuarios/detalle.png` y este informe.

No se añadieron dependencias, entidades, columnas ni migraciones. Se preservó el trabajo local previo de Mi perfil, Veterinarios y Cambio de contraseña.

## Verificaciones ejecutadas

| Verificación | Resultado |
|---|---|
| Maven verify, copia aislada, PostgreSQL 18 Testcontainers | **194 correctas: 72 unitarias + 122 de integración; 0 fallos, 0 errores, 0 omitidas** |
| Nuevas pruebas UsuariosHttpIT | **8 correctas**, incluidas autorización/fotos, confirmación ADMIN, duplicados/vínculos, restricciones de eliminación, concurrencia y JWT con correo/rol desactualizados |
| Node frontend | **176 correctas**, 0 fallos |
| Interfaz Usuarios, escritorio | **26 comprobaciones correctas** |
| Interfaz Usuarios, 375 px | **26 comprobaciones correctas** |
| Regresión visual Veterinarios | **23 comprobaciones correctas** |
| npm run build | Correcto; advertencia de tamaño de chunk superior a 500 kB |
| ESLint de archivos JS/JSX intervenidos | Correcto |
| git diff --check | Correcto |
| npm run lint global | **7 errores y 1 advertencia previos**, detallados abajo |

Las pruebas de integración ejecutaron los controladores y seguridad reales con MockMvc y persistencia en PostgreSQL temporal `vetcare_p0_integration`, sin recurrir a las bases habituales. Incluyen operaciones sintéticas de escritura y eliminación exclusivamente dentro del entorno desechable.

La validación visual utiliza los componentes reales y un adaptador HTTP de prueba en memoria, con datos sintéticos; sus capturas **no son datos de la base habitual**. El código del producto consulta las APIs reales, sin datos de ejemplo incorporados. Las pruebas backend cubren las respuestas HTTP y persistencia reales que corresponden.

Resultados de Maven conservados en `/private/tmp/vetcare-verificacion-yyg1auxt/target`; registro `/private/tmp/vetcare-users-security-final.log`. Frontend: `/private/tmp/vetcare-users-front-final.log`, `/private/tmp/vetcare-users-build-final.log`. Lint global: `/private/tmp/vetcare-users-lint.log`.

## Repetir pruebas de forma segura

Desde la raíz del repositorio, con Docker disponible para PostgreSQL temporal:

```sh
python3 scripts/verificar_aislado.py
cd frontend
node --test tests/*.test.mjs
npm run build
npx eslint src/pages/Usuarios.jsx src/components/UserAvatar.jsx src/components/EditorForm.jsx src/components/VetcareModal.jsx src/lib/users.js src/lib/useUserPhotos.js
npm run lint
```

El último comando mostrará los problemas globales pendientes. No ejecutar Spring Boot habitual para estas pruebas. El script aislado excluye configuraciones personales y utiliza Testcontainers.

Para repetir las comprobaciones visuales sin API habitual, ejecutar en otra terminal desde `frontend/`:

```sh
VITE_API_URL=http://127.0.0.1:1/api npm run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Abrir `http://127.0.0.1:5174/tests/visual/users.html?auto=1` y `http://127.0.0.1:5174/tests/visual/users-responsive.html`. Estos adaptadores no realizan solicitudes a PostgreSQL ni al backend habitual.

## Pendientes y límites

1. Revisar este resultado antes de actualizar Docker. **No está desplegado todavía en localhost:8088**.
2. Lint global: importación no utilizada en App; efectos en RecordatoriosBell, ResourcePage y MisPublicaciones; variable no utilizada en VetDateTimePicker; dos advertencias de reglas en AuthContext (variable err y exportación Fast Refresh). Se dejó fuera de alcance para conservar los módulos existentes.
3. Paginación cliente adecuada al catálogo actual: si el volumen crece mucho, evaluar una API paginada y una búsqueda de servidor en otra tarea.
4. No se añadió un mecanismo de cambio administrativo de rol ni eliminación lógica. El rol permanece protegido para no romper perfiles ni sesiones.
5. La protección del último ADMIN depende de que las eliminaciones pasen por el servicio; SQL manual queda fuera de esta garantía.
6. Después de desplegar, validar visualmente con las fotografías y datos habituales mediante operaciones de consulta. Las pruebas actuales no iniciaron sesión en la instalación habitual ni modificaron sus registros.

## Actualización posterior de Docker: comandos preparados, NO ejecutados

El Compose actual fija Flyway en true. Para actualizar únicamente backend/frontend **sin ejecutar migraciones**, utilizar una sobreescritura temporal explícita. Revisar y ejecutar solo cuando se autorice el despliegue:

```sh
cd '/Users/rosapp/Desktop/Proyecto Vetcare/vetcare-clinica-veterinaria'
docker compose build backend frontend
cat > /tmp/vetcare-sin-migraciones.yaml <<'YAML'
services:
  backend:
    environment:
      SPRING_FLYWAY_ENABLED: "false"
YAML
docker compose -f compose.yaml -f /tmp/vetcare-sin-migraciones.yaml up -d --no-deps backend frontend
```

Estos comandos actualizan solo los servicios indicados; no incluyen down, eliminación de volúmenes, operaciones SQL ni modificación de `.env`. El archivo temporal no modifica el Compose del repositorio. Mantener la sobreescritura en futuras actualizaciones mientras Flyway deba permanecer desactivado. Este módulo no necesita migraciones nuevas, pero requiere que el esquema previamente acordado ya esté instalado.

Después, consultar `http://localhost:8088`, iniciar sesión normalmente y verificar listado/detalle/filtros/cancelaciones. No utilizar eliminaciones ni crear cuentas de prueba sobre la base habitual sin autorización específica.

## Restricciones respetadas

No se modificaron `vetcare_docker` ni `vetcare_mejoras`; no se ejecutaron migraciones sobre ellas. No se reinició ni reconstruyó la instalación habitual, no se tocaron sus volúmenes, no se modificó `.env` y no se mostraron credenciales ni JWT. Se mantiene la rama indicada y no se hicieron commits, push ni merges.
