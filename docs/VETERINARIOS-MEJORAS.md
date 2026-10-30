# VetCare — Mejoras prioritarias de Veterinarios

28 de octubre de 2026. Rama `mejoras-finales-vetcare`.

## Resultado

Implementadas en el frontend integrado `frontend/`: fotografías circulares e iniciales, nombres con título profesional explícito, filtros compactos, modales proporcionados y confirmación de eliminación. Se conservaron las mejoras locales de Mi perfil ADMIN y sus pruebas. No se realizaron commits, push, merges ni cambios de rama.

No se modificaron registros de `vetcare_docker` ni `vetcare_mejoras`, variables de entorno o migraciones. No se reconstruyeron, eliminaron ni recrearon contenedores o volúmenes habituales. Las únicas escrituras de pruebas se realizaron con datos sintéticos en PostgreSQL temporal de Testcontainers. La ejecución de Maven usa una copia fuera del backend habitual, excluyendo application.properties, .env, target y uploads habituales.

## Fotografías y permisos

- Nuevo `GET /api/veterinarios/{idVeterinario}/foto`: reutiliza FotoPerfilService y resuelve el usuario exclusivamente a través de un perfil veterinario existente. Devuelve los bytes de su foto y `Cache-Control: no-store`.
- Usa la autorización existente de GET de veterinarios: usuarios autenticados. El acceso anónimo devuelve 401; un perfil inexistente o sin foto devuelve 404.
- `VeterinarioResponse.fotoDisponible` indica si existe una referencia almacenada; no expone nombres físicos de archivo ni rutas del volumen.
- Axios descarga la foto con el JWT existente. La URL blob se libera al desmontar o actualizar; si la foto no existe o falla su lectura, el listado muestra iniciales. Actualizar vuelve a consultar listado e imágenes.
- ADMIN y PROPIETARIO ven el mismo nombre: Dr./Dra. solo cuando está registrado, más nombres y apellidos. No se infieren títulos por nombre o imagen.
- Se retiró el botón Fotografía de la gestión ADMIN de veterinarios. No queda un modal administrativo de escritura en este listado.
- `POST` y `DELETE /api/usuarios/{id}/foto` rechazan con 403 la gestión administrativa de cuentas VETERINARIO. Se mantienen las operaciones existentes sobre usuarios de otros roles.
- El acceso genérico `GET /api/usuarios/{id}/foto` sigue siendo exclusivo de ADMIN. No se abrió `/api/usuarios/**` a propietarios.
- Cada veterinario conserva GET/POST/DELETE `/api/usuarios/me/foto` para gestionar únicamente su foto. No se modificaron su formulario ni el servicio de archivos.
- No cambia el almacenamiento: referencia en usuario y archivo en el volumen de uploads. No se duplican fotografías ni se crean tablas.

Estas restricciones están implementadas y probadas en código y entorno aislado; todavía no están desplegadas en los contenedores habituales.

## Listado, filtros y carga

- Buscador y selector «Título profesional» de 220 px comparten fila en escritorio y se apilan en móvil.
- Mantiene opciones Dr./Dra., ausencia de título y Todos, y la búsqueda sin distinción de acentos.
- Listado con bordes grises, superficies blancas/lavanda y acciones moradas. Avatares de 42 px.
- Cargas independientes mediante Promise.allSettled: un error de usuarios no descarta los veterinarios recuperados.
- Diferencia carga, listado vacío, búsqueda sin coincidencias, error del listado y error de cuentas.
- Deshabilita edición si no se dispone de la cuenta necesaria, evitando un formulario parcialmente rellenado que pudiera sobrescribir datos.

## Registro, edición y vinculación

Reutilizan los endpoints y DTO existentes y EditorForm con una opción `compact` exclusiva de este módulo. Encabezado y pie permanecen fuera del cuerpo desplazable; los controles tienen 42 px, límites de longitud coherentes con DTO y disposición de dos columnas/una columna móvil. Conserva la política común de contraseña.

La edición conserva contraseña y rol y explica que incluye datos personales y profesionales. El registro sigue creando cuenta VETERINARIO y perfil juntos.

La vinculación requiere cuenta VETERINARIO sin perfil. Se distinguen: consultando, error recuperable, ausencia legítima y candidatos disponibles. Sin candidatos no aparece Guardar; se ofrece Registrar veterinario. No se crean automáticamente cuentas ni se cambian roles.

El modo compacto añade navegación por teclado, retorno de foco, Escape y bloqueo de cierre/controles durante el envío. Los errores locales de contraseña no se presentan como errores de conexión. EditorForm mantiene su comportamiento y presentación predeterminados para los demás módulos; el envoltorio fieldset usa display:contents fuera del modo compacto.

## Eliminación

Se sustituyó window.confirm por un diálogo que identifica al profesional y explica que se elimina el perfil, se conserva la cuenta y las relaciones clínicas impiden eliminarlo. Cancelar no envía DELETE. La solicitud se bloquea mientras está en curso.

No se modificaron el servicio de eliminación, claves foráneas ni reglas de integridad. HTTP 409 mantiene el diálogo y explica el bloqueo. No se implementó desactivación lógica. No se enviaron DELETE a las bases habituales; el escenario visual usa un adaptador que simula 409 sin eliminar sus datos.

## Archivos de esta intervención

Modificados:

- `frontend/src/pages/Veterinarios.jsx`.
- `frontend/src/components/EditorForm.jsx`: modo compacto opcional.
- `frontend/src/components/ProfilePhotoDialog.jsx`: etiqueta accesible configurable; conserva el valor predeterminado para fotos.
- `src/main/java/pe/edu/utec/vetcare/controller/VeterinarioController.java`: lectura de fotografía por perfil.
- `src/main/java/pe/edu/utec/vetcare/controller/UsuarioController.java`: impide escritura administrativa de fotos veterinarias.
- `src/main/java/pe/edu/utec/vetcare/dto/response/VeterinarioResponse.java`: disponibilidad de foto.
- `src/main/java/pe/edu/utec/vetcare/mapper/VeterinarioMapper.java`: mapeo de disponibilidad.
- `src/test/java/pe/edu/utec/vetcare/integration/AdminHttpIT.java`: sustituye la expectativa anterior de gestión ADMIN por la nueva política, conservando las pruebas locales de contacto ADMIN.

Creados:

- `frontend/src/pages/Veterinarios.css`.
- `frontend/src/components/VeterinarioAvatar.jsx`.
- `frontend/src/lib/veterinarians.js`.
- `frontend/tests/veterinarians.test.mjs`.
- `frontend/tests/visual/veterinarians.html` y `veterinarians-responsive.html`.
- `evidencias-veterinarios/` y este informe.

Los demás archivos que aparecen modificados en git status pertenecen a las mejoras anteriores de Mi perfil y se conservaron; no deben confundirse con cambios nuevos de esta tarea.

## Validación ejecutada

- Maven: **BUILD SUCCESS, 179 pruebas aprobadas** (72 unitarias y 107 de integración), cero fallos/errores/omitidas. Copia `/private/tmp/vetcare-verificacion-dsobeikl`, PostgreSQL 18 temporal `vetcare_p0_integration`. Las migraciones existentes solo construyen esa base temporal, nunca las bases habituales.
- Prueba HTTP de foto: ADMIN, VETERINARIO y PROPIETARIO consultan la foto por perfil; anónimo 401; perfil inexistente 404; propietario no accede por ID arbitrario de usuario; ADMIN no puede subir/eliminar foto veterinaria; titular puede subir y quitar su foto; disponibilidad cambia y la lectura posterior a quitar devuelve 404; respuesta no-store.
- Se conservaron y ejecutaron las pruebas de registro, edición, permisos y bloqueo de eliminación por relaciones de AdminHttpIT.
- Frontend: **171 pruebas aprobadas**, cero fallos. Incluye tres nuevas de nombres/títulos/iniciales, elegibilidad y mensajes.
- `npm run build`: correcto; conserva advertencia de bundle mayor de 500 kB.
- ESLint de los componentes/helpers de esta tarea: correcto. `git diff --check`: correcto.
- Interfaz con componentes reales y adaptador sin API: **19 comprobaciones ADMIN de escritorio, 19 a 375 px, 19 con candidatos para vincular y 9 de PROPIETARIO**. No sustituyen las pruebas HTTP reales anteriores.
- Comprobado manualmente en navegador el error de carga de cuentas: el listado permanece, Editar se deshabilita y Vincular ofrece Reintentar, sin confundirlo con ausencia legítima.
- Captura final del registro sin errores de consola observados.
- Capturas de listado y registro realizadas con datos sintéticos; la imagen de prueba es un PNG generado localmente, no una fotografía real de un profesional.

## Pendientes y riesgos antes de desplegar

1. Revisar y autorizar actualización coordinada de backend y frontend Docker. No se actualizó Docker en esta tarea. El frontend anterior no conoce la nueva ruta y la política anterior sigue vigente en la aplicación actualmente desplegada.
2. Verificar posteriormente con cuentas autorizadas y fotos existentes la lectura en el despliegue real. La prueba HTTP utilizó imágenes sintéticas persistidas en el entorno aislado.
3. La disponibilidad se basa en la referencia de PostgreSQL: si faltara físicamente un archivo, el avatar cae a iniciales. Respaldar base y volumen juntos; no se reconstruyeron contenedores para probar persistencia aquí.
4. La eliminación continúa siendo física cuando no existen relaciones. Una desactivación lógica requiere una decisión y fase separadas. No se introdujo ese modelo.
5. La comprobación de colegiatura duplicada sigue en el servicio; no se agregó una restricción de base ni se resolvió su riesgo de concurrencia mediante migración.
6. El listado descarga una imagen por profesional con foto. No se incorporaron paginación ni miniaturas nuevas; evaluar si el número de profesionales crece.
7. No se endureció la coordinación transaccional entre archivos y SQL de FotoPerfilService; se conservó la implementación existente.

## Repetir pruebas

Desde la raíz del backend, Docker disponible para la base temporal:

```sh
python3 scripts/verificar_aislado.py
```

Desde `frontend/`:

```sh
node --test tests/*.test.mjs
npm run build
```

Prueba de interfaz sin backend real:

```sh
VITE_API_URL=http://127.0.0.1:1/api npm run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Abrir `/tests/visual/veterinarians.html?auto=1`, añadir `&elegible=1` o `&role=owner` para los otros escenarios y usar `/tests/visual/veterinarians-responsive.html` para móvil. `?error=1` simula fallo de usuarios; `?list-error=1` simula fallo del listado. El adaptador no conecta a PostgreSQL ni al backend habitual.
