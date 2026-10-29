# VetCare — Acabado de Veterinarios y cambio seguro de contraseña

28 de octubre de 2026. Rama `mejoras-finales-vetcare`.

## Resultado

Se conservaron fotos/iniciales, permisos, búsqueda, títulos y flujos de registro/edición/vinculación del módulo Veterinarios, así como las mejoras de Mi perfil ADMIN. Esta intervención no modifica datos de `vetcare_docker` ni `vetcare_mejoras`, archivos .env, volúmenes o migraciones. No se iniciaron ni reconstruyeron los contenedores habituales, ni se hicieron commits, push o merges.

## Modales y presentación

- `VetcareModal` es un modal propio renderizado mediante portal en document.body, con centrado respecto al viewport, fondo oscurecido, panel blanco de hasta 500 px, sombra suave, desplazamiento interno y estilos responsive. No usa window.confirm, window.alert ni diálogo nativo.
- Foco inicial, retención de Tab/Shift+Tab, Escape seguro, retorno de foco, aria-modal y bloqueo durante procesamiento. Se bloquea el desplazamiento de la página mientras está abierto.
- `ProfilePhotoDialog` reutiliza este contenedor, de modo que Eliminar y Vincular sin candidatos usan el mismo comportamiento. No se han creado implementaciones independientes para esos casos.
- Registrar, Editar y Vincular con candidatos conservan EditorForm compacto y ahora también se renderizan en un portal; mantienen encabezado/pie accesibles fuera del cuerpo desplazable. No se altera el modo predeterminado de los otros módulos.
- Eliminar muestra el nombre y título reales, explica que se conserva la cuenta y que las relaciones clínicas impiden borrar. Su botón destructivo es rojo discreto; Cancelar no envía DELETE. Las reglas del backend no se cambiaron.
- Buscador con máximo 520 px y selector de 200 px, alineados sin ocupar toda la pantalla; adaptación a una columna en móvil.
- Se conserva Vincular y el acceso a Registrar cuando no quedan candidatos; no se crearon cuentas de demostración para ese flujo.

## Cambio de contraseña: interfaz

Una única implementación `AccountSecurity` se integra debajo de Datos personales en Mi perfil ADMIN y en el perfil compartido por VETERINARIO y PROPIETARIO.

Muestra «Seguridad de la cuenta» y «Cambiar contraseña». El modal tiene contraseña actual, nueva y confirmación, controles mostrar/ocultar, errores por campo, ayuda sin detalles técnicos y bloqueo frente a doble envío. Cancelar y el cierre seguro eliminan el estado del formulario sin escribir.

Las contraseñas solo viven temporalmente en el estado React y en el cuerpo de la petición; no se almacenan en localStorage/sessionStorage. Tras éxito se limpia el estado, se cierra la sesión local y se navega al login con confirmación temporal.

## Backend y contrato

No existía un endpoint autenticado equivalente. Se añadió exclusivamente:

`POST /api/usuarios/me/contrasena`

Cuerpo: `contrasenaActual`, `nuevaContrasena`, `confirmarContrasena`. El usuario se selecciona exclusivamente con `idUsuario` del JWT validado; un ID del cuerpo nunca selecciona otra cuenta.

El servicio:

1. Aplica límite de intentos por cuenta.
2. Bloquea y refresca la fila de usuario para comprobar la credencial vigente.
3. Verifica la contraseña actual con PasswordEncoder.matches.
4. Reutiliza PoliticaContrasena para la nueva clave; exige confirmación y rechaza repetir la actual.
5. Guarda exclusivamente BCrypt y conserva identidad, correo y rol.
6. Devuelve solo una confirmación. No registra ni devuelve contraseñas ni hashes.

Códigos: 200 al guardar; 400 con campos para errores de datos; 401 sin autenticación válida; 429 cuando se supera el límite. El límite permite cinco intentos dentro de un minuto, con mapa acotado y limpieza de entradas vencidas. Un éxito limpia el contador.

El límite está en memoria por instancia: se reinicia al reiniciar el proceso y no se comparte entre réplicas. Es una protección razonable para la configuración actual; un despliegue con varias réplicas debe coordinar un límite compartido o en el proxy. No se añadió infraestructura.

## JWT: invalidación real

Antes, cambiar BCrypt no invalidaba JWT ya emitidos. Ahora el login añade un claim `credencial`: HMAC-SHA256 con separación de dominio, el ID y el hash BCrypt vigente, usando la clave de firma existente. El claim no contiene el hash BCrypt ni permite obtenerlo sin la clave secreta. No se cambian secretos ni variables de entorno.

Además de conservar validación de firma y tiempo, el decoder consulta el usuario y compara esa huella de forma constante con la credencial actual. Al cambiar la contraseña, cambia el hash y todos los JWT anteriores de esa cuenta son rechazados con 401 en las siguientes solicitudes. La invalidación persiste tras reinicio porque depende del hash persistente, no de una lista temporal de tokens revocados.

**Impacto de despliegue:** los JWT del formato anterior sin huella se rechazan. Al actualizar el backend, todos los usuarios con sesiones antiguas deben cerrar sesión/iniciar sesión nuevamente. No se modificaron sus contraseñas. El cambio debe desplegarse de forma coordinada para evitar mezclar emisores antiguos y nuevos.

El mecanismo agrega una consulta de usuario por petición con JWT. Debe verificarse su coste y disponibilidad en el entorno de despliegue. No revierte operaciones que ya hubieran sido autenticadas y estuvieran en curso al cambiar la contraseña.

No requiere nueva columna, tabla, migración, almacenamiento de sesiones o dependencia adicional. No se modificaron los endpoints ni el código de Recuperar/Restablecer contraseña ni se añadió SMTP al flujo autenticado. Como consecuencia de la comprobación general de credenciales, cualquier cambio del hash persistente también invalida JWT anteriores, independientemente de qué flujo autorizado lo cambie.

## Archivos de esta intervención

### Modificados

- `frontend/src/pages/Veterinarios.jsx`: advertencia y botón destructivo.
- `frontend/src/pages/Veterinarios.css`: ancho de búsqueda y filtro.
- `frontend/src/components/EditorForm.jsx`: portal para el modo compacto y ayuda sin límite técnico visible en ese modo.
- `frontend/src/components/ProfilePhotoDialog.jsx`: delega en el modal propio.
- `frontend/src/pages/PerfilAdmin.jsx`: incorpora AccountSecurity.
- `frontend/src/pages/Perfil.jsx`: incorpora AccountSecurity en el perfil compartido de los otros roles.
- `frontend/tests/visual/veterinarians.html`: pruebas adaptadas al modal propio y comprobaciones de centrado/ancho.
- `src/main/java/pe/edu/utec/vetcare/controller/UsuarioController.java`: endpoint autenticado.
- `src/main/java/pe/edu/utec/vetcare/service/AuthService.java`: añade huella al JWT.
- `src/main/java/pe/edu/utec/vetcare/security/SecurityConfig.java`: valida la huella junto a la firma y vigencia.
- `src/main/java/pe/edu/utec/vetcare/exception/GlobalExceptionHandler.java`: errores por campo del cambio.

### Creados

- `frontend/src/components/VetcareModal.jsx` y `VetcareModal.css`.
- `frontend/src/components/AccountSecurity.jsx` y `AccountSecurity.css`.
- `frontend/src/lib/changePassword.js`.
- `frontend/tests/change-password.test.mjs`.
- `frontend/tests/visual/account-security.html` y `account-security-responsive.html`.
- `src/main/java/pe/edu/utec/vetcare/dto/request/CambiarContrasenaRequest.java`.
- `src/main/java/pe/edu/utec/vetcare/service/CambioContrasenaService.java`.
- `src/main/java/pe/edu/utec/vetcare/security/CredencialJwt.java`.
- `src/main/java/pe/edu/utec/vetcare/exception/CambioContrasenaInvalidoException.java`.
- `src/test/java/pe/edu/utec/vetcare/integration/CambiarContrasenaHttpIT.java`.
- `evidencias-seguridad/cambiar-contrasena.png`, `eliminar-centrado.png`, `vincular-centrado.png` y este informe.

Los demás cambios locales de Mi perfil y Veterinarios que ya existían antes de esta intervención se conservaron. No se sustituyeron por versiones antiguas.

## Pruebas realizadas

| Validación | Resultado |
|---|---|
| Maven aislado, PostgreSQL 18 temporal | **186 aprobadas: 72 unitarias + 114 integración**, sin fallos, errores ni omitidas |
| Nuevas pruebas HTTP de contraseña | 7 aprobadas |
| Frontend, node --test tests/*.test.mjs | **173 aprobadas**, sin fallos |
| Compilación npm run build | Correcta; advertencia existente de bundle superior a 500 kB |
| ESLint de componentes/helpers de esta intervención | Correcto |
| git diff --check | Correcto |
| npm run lint global | 7 errores y 1 advertencia preexistentes, descritos debajo |

Las siete pruebas HTTP verifican cambio real y BCrypt para los tres roles, identidad/rol intactos, actual incorrecta, débil, confirmación distinta, repetición, acceso anónimo/token inválido, aislamiento de cuentas, límite de intentos, formato antiguo de JWT, rechazo de dos sesiones antiguas, login nuevo y rechazo de la clave anterior. También se prueba una contraseña actual demasiado larga sin producir un error interno.

La primera ejecución falló por un documento sintético que excedía la longitud del esquema; se corrigió únicamente el fixture. La verificación final completa fue BUILD SUCCESS en `/private/tmp/vetcare-verificacion-3nh2lx5j`. Configuración habitual, .env, uploads y target excluidos; base temporal obligatoria `vetcare_p0_integration`, sin fallback. Las migraciones existentes solo construyeron esa base temporal de pruebas; no se aplicaron en las bases habituales.

Interfaz con componentes reales y adaptador sin API real:

- 19 comprobaciones de contraseña para cada rol: ADMIN, VETERINARIO y PROPIETARIO.
- Las mismas 19 a 375 px.
- Veterinarios: 23 en escritorio sin candidatos, 23 con candidatos y 22 a 375 px (la diferencia es la comprobación de ancho exclusivo de escritorio).
- Verificadas confirmación, logout, errores por campo, limpieza al cancelar, controles mostrar/ocultar, doble clic, bloqueo durante envío y ausencia de desbordamiento.
- Verificados Tab/Shift+Tab, Escape y retorno de foco en el modal de contraseña. Escape también cierra la confirmación de eliminación sin solicitar DELETE.
- Captura final sin errores de consola observados.

El lint global conserva incidencias en App.jsx (import no usado), RecordatoriosBell.jsx, ResourcePage.jsx, VetDateTimePicker.jsx, AuthContext.jsx y MisPublicaciones.jsx. No se editaron esos archivos en esta intervención para resolverlas; se dejó su corrección para una tarea separada. Por tanto, no se afirma que el lint de todo el repositorio esté aprobado.

## Revisión en navegador sin tocar la demostración

Desde una terminal:

```sh
cd '/Users/rosapp/Desktop/Proyecto Vetcare/vetcare-clinica-veterinaria/frontend'
VITE_API_URL=http://127.0.0.1:1/api npm run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Abrir estas rutas de revisión aislada:

- `http://127.0.0.1:5174/tests/visual/account-security.html` — perfil ADMIN y botón Cambiar contraseña.
- `http://127.0.0.1:5174/tests/visual/account-security.html?role=VETERINARIO`
- `http://127.0.0.1:5174/tests/visual/account-security.html?role=PROPIETARIO`
- Añadir `&auto=1` a las dos anteriores, o `?auto=1` a la primera, ejecuta las comprobaciones.
- `http://127.0.0.1:5174/tests/visual/veterinarians.html` — modales de Veterinarios.
- `http://127.0.0.1:5174/tests/visual/veterinarians.html?elegible=1` — vinculación con candidato sintético.
- `http://127.0.0.1:5174/tests/visual/account-security-responsive.html`
- `http://127.0.0.1:5174/tests/visual/veterinarians-responsive.html`

Estas páginas usan cuentas, respuestas y fotos exclusivamente sintéticas; no cambian contraseñas de personas ni crean cuentas en PostgreSQL. No representan el backend desplegado. Las pruebas HTTP reales se ejecutaron por separado con MockMvc y PostgreSQL aislado.

Para repetir las pruebas:

```sh
cd '/Users/rosapp/Desktop/Proyecto Vetcare/vetcare-clinica-veterinaria'
python3 scripts/verificar_aislado.py
cd frontend
node --test tests/*.test.mjs
npm run build
npm run lint
```

La última orden seguirá mostrando las incidencias globales conocidas. El script Maven requiere Docker activo y usa exclusivamente su contenedor temporal.

## Antes del despliegue

1. Revisar y autorizar actualización coordinada de frontend/backend. No se actualizó Docker: la aplicación actual no muestra todavía estos cambios.
2. Avisar que las sesiones anteriores deben iniciar sesión nuevamente. No desplegar emisores antiguos y nuevos en paralelo.
3. Verificar en un entorno de ensayo la lectura adicional por JWT y, si se escala a varias instancias, el límite de intentos.
4. Revisar las migraciones pendientes del proyecto y cómo las ejecuta Compose antes de iniciar una actualización. Esta intervención no crea ninguna migración ni autoriza aplicar las preparadas previamente.
5. Mantener HTTPS y respaldos coordinados de base y uploads en DonWeb. No se cambió esa infraestructura.
6. La eliminación lógica sigue pendiente de decisión. No se probó eliminando profesionales reales ni de demostración.

Las bases y volúmenes habituales permanecieron intactos durante esta intervención. No se leyeron ni modificaron credenciales .env y no se realizaron cambios de contraseña sobre cuentas habituales.
