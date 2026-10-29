# ADMIN → Propietarios: implementación y validación

28/10/2026 · Rama `mejoras-finales-vetcare` · Frontend integrado `frontend/`.

## Resultado funcional

- Tabla con Propietario (avatar, nombre y correo), Celular, Dirección, Estado, Registro y Acciones. Se retiraron únicamente los identificadores visibles; las claves y relaciones se conservan.
- Iniciales lavanda, fotos autorizadas, fecha `dd/mm/aaaa`, estados reales con etiquetas legibles y dirección extensa truncada en el listado, completa en el detalle.
- Buscador de hasta 520 px por nombre, documento, correo y celular. Acciones Ver, Editar en menta y Eliminar en rojo tenue; estados de carga, error y ausencia de registros separados.
- Ver consulta el propietario real y sus mascotas. Modal centrado, información personal ordenada y sección «Mascotas» con foto o huella, nombre, especie, raza y acceso individual al expediente.
- El enlace utiliza la ruta existente `/panel/historias?paciente={idMascota}&consultar=1`. El parámetro adicional inicia la consulta de ese paciente mediante el cargador clínico existente. No se añadió otro endpoint ni una pantalla ficticia; las consultas sin ese parámetro conservan su comportamiento anterior. Se reutilizan los estados de carga y error del expediente.
- Edición compacta de **celular y dirección**, con nombre del propietario, validación local y backend, bloqueo de doble envío, cancelación sin escritura y conservación del borrador ante errores. Se confirma éxito después de la respuesta del backend y se comprueba que contenga el contacto solicitado.
- Nuevo propietario mantiene el flujo existente de **vincular una cuenta ya registrada**. Solo ofrece cuentas PROPIETARIO que todavía no tengan perfil. Si no hay candidatos, explica el motivo y ofrece ir al módulo Usuarios; no crea una segunda cuenta ni pide otra contraseña.
- Confirmación propia para eliminar perfil, con advertencia suave, protección de dependencias y mensajes comprensibles. La cuenta de acceso y su fotografía no se eliminan al borrar un perfil vacío.
- Se retiró solo el botón administrativo «Generar recordatorios». No se eliminaron servicios, tablas, endpoints ni registros y no se creó otra tarea automática.

## Celular y dirección: una fuente para cada dato

El celular permanece en `usuario.nro_celular`. La dirección del propietario permanece en `propietario.direccion`. Se amplió el DTO existente, sin agregar columnas ni duplicar datos persistidos.

`PUT /api/propietarios/{id}` actualiza ambos datos en una sola transacción ADMIN. El `idUsuario` se conserva por compatibilidad con el contrato anterior, pero debe coincidir con la cuenta ya asociada. Se rechazan campos ajenos al formulario, reasignaciones y celulares inválidos. Un celular registrado debe contener nueve dígitos y comenzar por 9; puede dejarse vacío. La dirección conserva su límite de 200 caracteres.

El DTO de lectura de Propietario incorpora correo, documento, celular y disponibilidad de foto desde la cuenta asociada. ADMIN → Usuarios y `/usuarios/me` utilizan la dirección canónica del perfil para cuentas PROPIETARIO, incluso si está vacía, evitando recuperar una dirección antigua de otro campo. La dirección profesional de VETERINARIO se mantiene independiente; una prueba cubre incluso una asociación histórica inconsistente.

Mi perfil del propietario vuelve a consultar los datos al entrar o recuperar el foco. Actualiza sus campos solamente cuando no hay edición ni procesamiento en curso. Una prueba comprueba que un cambio externo se refleja y que un borrador en edición no se sobrescribe. Los demás roles conservan su flujo previo.

## Sincronización de fotografías

Se reutilizan `FotoPerfilService`, los archivos existentes y la referencia almacenada en Usuario. No se guardan nuevas copias en el navegador ni se crea otro almacenamiento.

1. Mi perfil continúa guardando o quitando la foto propia mediante sus endpoints existentes.
2. Después de persistir la operación, se emite una señal de invalidación: únicamente identificador y revisión temporal, sin imágenes, nombres, credenciales ni JWT.
3. Propietarios, Usuarios y Veterinarios vuelven a consultar sus metadatos autorizados y renuevan las imágenes mediante los endpoints privados existentes.
4. Los avatares del listado y el detalle comparten caché por usuario. Se cancelan solicitudes obsoletas y se revocan las URLs de objetos al actualizar o desmontar.
5. La señal se comunica entre pestañas del mismo origen; también se actualiza al recuperar el foco o volver a una pestaña visible. Las lecturas de foto propia y de usuarios administrados responden `Cache-Control: no-store`; la foto veterinaria ya lo hacía.

No se implementó un canal de actualización instantánea entre dispositivos. En otro navegador/dispositivo, recuperar el foco o pulsar Actualizar consulta la versión persistida. Si una foto falla, se muestran iniciales. No hay controles para editar o quitar fotos ajenas en estos listados y no se ampliaron sus permisos de lectura.

## Protección de eliminación

Se revisaron controlador, servicio, entidad, repositorio y migraciones existentes de claves foráneas. La relación de Propietario con Usuario no declara borrado en cascada; Mascota referencia al propietario mediante FK sin borrado en cascada.

- El controlador exige ADMIN y el servicio vuelve a comprobarlo.
- El servicio bloquea HTTP 409 si existen mascotas del propietario.
- Mascotas enlaza con historias, citas y demás registros clínicos; se conserva esa cadena al impedir eliminar el propietario.
- Las claves foráneas permanecen como protección adicional frente a carreras concurrentes.
- Un perfil vacío puede eliminarse; la cuenta permanece. No se añadió desactivación lógica, recuperación ficticia ni migración.
- La interfaz muestra confirmación antes de enviar DELETE, bloquea reenvíos y conserva el modal con el error cuando corresponde. Cancelar y Escape no eliminan datos.

Las comprobaciones destructivas se ejecutaron únicamente con registros sintéticos de PostgreSQL temporal. No se eliminó ningún registro habitual.

## Recordatorios: acceso retirado, lógica conservada

El botón llamaba `POST /api/recordatorios/generar`.

La lógica permanece en:

- `controller/RecordatorioVacunaController.java`: endpoint manual y lecturas existentes.
- `service/RecordatorioVacunaService.java`: `generarManualmente()` exige ADMIN; `ejecutarAutomaticamente()` y `generarPendientes()` mantienen la generación programada existente.
- `config/RecordatoriosSchedulingConfig.java`: habilita la planificación existente.
- `config/RecordatorioCorreoScheduler.java` y `service/RecordatorioCorreoService.java`: envío programado existente.
- `repository/RecordatorioVacunaRepository.java` y su entidad: persistencia conservada.

La captura HTTP 401 apunta a autenticación ausente, vencida o inválida; un permiso insuficiente normalmente corresponde a 403. No se reprodujo esa sesión habitual ni se inspeccionaron sus tokens, por lo que la causa particular sigue pendiente de la auditoría de recordatorios. No se alteró la seguridad global para ocultarla. El panel ya no envía solicitudes de generación manual.

## Endpoints reutilizados

- `GET /api/propietarios`, `GET /api/propietarios/{id}`.
- `POST /api/propietarios`, `PUT /api/propietarios/{id}`, `DELETE /api/propietarios/{id}`.
- `GET /api/usuarios` para cuentas elegibles, y `GET /api/usuarios/{id}/foto` para lectura ADMIN.
- `/api/usuarios/me` y `/api/usuarios/me/foto`, con las operaciones propias de Mi perfil ya existentes.
- `GET /api/mascotas` y `GET /api/mascotas/{id}/foto`.
- La vista clínica conserva sus consultas a historia por mascota, mascota, atenciones, citas, vacunas y profesionales.

No se crearon endpoints adicionales.

## Archivos de esta intervención

### Backend modificados

Bajo `src/main/java/pe/edu/utec/vetcare/`:

- `dto/request/PropietarioRequest.java`: celular opcional, validación y rechazo de campos protegidos.
- `dto/response/PropietarioResponse.java`: contacto/documento/foto derivados de Usuario.
- `mapper/PropietarioMapper.java`: mapeo de los datos existentes.
- `repository/MascotaRepository.java`: comprobación de dependencias por propietario.
- `service/impl/PropietarioServiceImpl.java`: contacto transaccional, rol de la cuenta, identidad inmutable y eliminación protegida.
- `service/impl/UsuarioServiceImpl.java`: dirección canónica del propietario, sin cambiar la profesional.
- `controller/UsuarioController.java`: dirección canónica en perfil propio y cabeceras de fotos sin caché.

### Frontend modificados

- `frontend/src/pages/Propietarios.jsx`: pantalla y flujos autorizados completos.
- `frontend/src/pages/HistoriaClinica.jsx`: consulta automática explícita del enlace administrativo, manteniendo la ruta existente.
- `frontend/src/components/EditorForm.jsx`: variante compacta de contacto y descripción opcional.
- `frontend/src/contexts/AuthContext.jsx`: emisión de invalidación tras guardar/quitar foto; retorno del perfil consultado para refrescar contacto sin perder borradores. JWT, credenciales y login no se cambian.
- `frontend/src/pages/Perfil.jsx`: refresco seguro del contacto del propietario cuando no está editando.
- `frontend/src/pages/Usuarios.jsx`: actualización de metadatos y fotos al recibir la señal o recuperar foco.
- `frontend/src/pages/Veterinarios.jsx`: misma invalidación para avatares veterinarios.

### Frontend creados

- `frontend/src/pages/Propietarios.css`.
- `frontend/src/lib/owners.js`.
- `frontend/src/lib/profilePhoto.js`.

### Pruebas y evidencias

Creados:

- `src/test/java/pe/edu/utec/vetcare/integration/PropietariosAdminHttpIT.java`.
- `frontend/tests/owners.test.mjs`.
- `frontend/tests/profile-photo.test.mjs`.
- `frontend/tests/visual/owners.html`.
- `frontend/tests/visual/owners-responsive.html`.

Ampliados:

- `frontend/tests/visual/veterinarians.html`: actualización de foto e iniciales.
- `frontend/tests/visual/account-security.html`: contacto externo y conservación del borrador de Mi perfil.

Creados este informe y `evidencias-propietarios/listado.png`, `detalle.png`, `editar.png`, `eliminar.png`. Sus datos e imágenes son **sintéticos de pruebas en memoria**, no registros de las bases habituales ni fotos añadidas al producto.

No se modificaron entidades, archivos SQL, Flyway, `.env`, dependencias, credenciales o volúmenes.

## Pruebas y compilación

| Verificación | Resultado |
|---|---|
| Maven verify en copia aislada y PostgreSQL 18 temporal | **203 correctas: 72 unitarias + 131 de integración**, sin fallos, errores ni omitidas |
| PropietariosAdminHttpIT | **7 correctas**: contacto canónico, validación/identidad, permisos, vinculación, eliminación, fotografías y dirección profesional independiente |
| Frontend Node completo | **188 correctas**, sin fallos |
| Propietarios escritorio | **35 comprobaciones correctas** |
| Propietarios móvil 375 px | **34 comprobaciones correctas** |
| Regresión Veterinarios y sincronización | **33 comprobaciones correctas** |
| Regresión Usuarios | **28 comprobaciones correctas** |
| Mi perfil / seguridad de propietario | **22 comprobaciones correctas**, incluido contacto externo, conservación del borrador y cambio de contraseña |
| Teclado | Escape cierra y devuelve el foco; Shift+Tab mantiene el foco en la confirmación; no se ejecutó DELETE al cancelar |
| Build backend | Incluido en Maven verify aislado, correcto |
| npm run build | Correcto, con advertencia de chunk mayor de 500 kB |
| Lint de componentes nuevos/lógica modificada | Sin errores nuevos; AuthContext conserva sus dos errores previos |
| Lint global | **7 errores y 1 advertencia previos**, fuera del alcance de esta tarea |
| git diff --check | Correcto |

Se comprobó el estado realmente vacío del listado, distinto del error de carga. La interfaz utiliza componentes reales con un adaptador HTTP de prueba. MockMvc y PostgreSQL temporal verifican permisos, persistencia y respuestas reales del backend. No se presentan las simulaciones visuales como pruebas sobre la instalación habitual.

Resultado final de Maven: `/private/tmp/vetcare-verificacion-xic7v9hk/target`; log `/private/tmp/vetcare-owners-backend-final.log`. Logs frontend: `/private/tmp/vetcare-owners-front.log`, `/private/tmp/vetcare-owners-build.log`, `/private/tmp/vetcare-owners-new-lint.log`, `/private/tmp/vetcare-owners-lint.log`, `/private/tmp/vetcare-owners-global-lint.log`.

Los errores previos de AuthContext son una variable `err` no utilizada y la regla de exportación Fast Refresh. No se silenciaron ni se rehízo el contexto para resolverlos. Los demás errores globales siguen en App, RecordatoriosBell, ResourcePage, VetDateTimePicker y MisPublicaciones.

## Pendientes y límites

1. Desplegar **backend y frontend juntos**. El DTO de contacto y los metadatos nuevos requieren el backend actualizado. La interfaz detecta un contrato antiguo en el listado en lugar de inventar datos ausentes. Tampoco anuncia un contacto guardado si la respuesta no confirma ambos campos.
2. La instalación habitual no se considera actualizada ni validada por estas pruebas. No se verificó su esquema ni se aplicaron migraciones; esta intervención no necesita una nueva migración.
3. Las fotos se actualizan mediante señal entre pestañas y consultas al recuperar foco/Actualizar. No se añadió sincronización push entre dispositivos.
4. La revisión integral de recordatorios, campanita, vacuna y correo permanece pendiente. Se mantuvo intacta su planificación actual.
5. Resolver por separado el lint global previo y la advertencia de tamaño del bundle.

## Repetir pruebas sin usar bases habituales

Desde la raíz del repositorio, con Docker disponible para la base temporal:

```sh
python3 scripts/verificar_aislado.py
cd frontend
node --test tests/*.test.mjs
npm run build
npx eslint src/pages/Propietarios.jsx src/pages/Perfil.jsx src/pages/HistoriaClinica.jsx src/lib/owners.js src/lib/profilePhoto.js src/components/EditorForm.jsx src/pages/Usuarios.jsx src/pages/Veterinarios.jsx
npm run lint
```

Para probar interfaz en memoria, desde `frontend/`:

```sh
VITE_API_URL=http://127.0.0.1:1/api npm run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Abrir bajo `http://127.0.0.1:5174`:

- `/tests/visual/owners.html?auto=1`.
- `/tests/visual/owners-responsive.html`.
- `/tests/visual/account-security.html?auto=1&role=PROPIETARIO&contact-sync=1`.
- `/tests/visual/veterinarians.html?auto=1`.

## Actualización posterior de contenedores: preparada, NO ejecutada

El Compose existente habilita Flyway. Para actualizar únicamente los dos servicios necesarios, sin ejecutar migraciones, usar una sobreescritura temporal explícita y validar el esquema sin modificarlo:

```sh
cd '/Users/rosapp/Desktop/Proyecto Vetcare/vetcare-clinica-veterinaria'
docker compose build backend frontend
cat > /tmp/vetcare-propietarios-sin-migraciones.yaml <<'YAML'
services:
  backend:
    environment:
      SPRING_FLYWAY_ENABLED: "false"
      SPRING_JPA_HIBERNATE_DDL_AUTO: validate
YAML
docker compose -f compose.yaml -f /tmp/vetcare-propietarios-sin-migraciones.yaml up -d --no-deps backend frontend
```

Ejecutar solamente después de revisar y autorizar el despliegue. No se reinicia PostgreSQL ni se eliminan volúmenes. Si la validación identifica un esquema pendiente, detener esa actualización y revisar; no habilitar Flyway automáticamente.

Después, abrir `http://localhost:8088`, iniciar sesión normalmente y comenzar con consultas y cancelaciones. No probar escrituras o eliminaciones sobre datos habituales sin autorización específica.

## Restricciones respetadas

Sin modificaciones en `vetcare_docker` o `vetcare_mejoras`, sin Flyway sobre bases habituales, sin alteración de fotos persistidas ni volúmenes, sin cambios de `.env` o credenciales. No se reconstruyeron ni reiniciaron los contenedores habituales. No se hicieron commits, push, merges o cambios de rama.
