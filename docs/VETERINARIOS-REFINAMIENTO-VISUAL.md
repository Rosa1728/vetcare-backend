# Refinamiento visual de Veterinarios

28/10/2026 · Rama `mejoras-finales-vetcare` · Frontend integrado `frontend/`.

## Mejoras implementadas

- Editar con menta pastel tenue, texto esmeralda oscuro y borde suave. Eliminar con fondo rosado muy claro, texto rojo apagado y borde tenue. Vincular conserva un morado secundario suave; Registrar y Guardar veterinario mantienen el morado de marca. Estados hover, focus y disabled definidos sin modificar los botones de otras pantallas.
- Confirmación de eliminación compacta y centrada: icono circular pastel, nombre del registro seleccionado, advertencia diferenciada, explicación de que la cuenta de acceso se conserva y botón destructivo suave. Se mantienen el DELETE existente, las restricciones por vínculos clínicos y el bloqueo de doble envío. No se añade recuperación ni desactivación.
- Vinculación con español natural, sin enums ni nombres técnicos. El estado sin candidatos muestra un aviso lavanda y permite ir a Registrar veterinario sin superponer ventanas. Carga y error siguen separados del estado vacío. Los candidatos y los permisos se calculan con la misma lógica existente.
- Registro y edición compactos: dos columnas en escritorio, una en móvil, separaciones discretas entre identificación/contacto/información profesional, botones fuera del área desplazable. La contraseña deja de ocupar innecesariamente una fila completa.
- Una sola ayuda de contraseña. Al guardar una clave inválida, se oculta la ayuda general y se indican únicamente los requisitos faltantes junto al campo. Se mantiene la política compartida: longitud mínima, mayúsculas, minúsculas, número, símbolo, rechazo de contraseñas comunes y límite técnico de BCrypt. Cuando supera este límite se pide una contraseña más breve, sin mostrar la expresión «bytes».
- Validación local de DNI y de CE/Pasaporte; celular peruano opcional de nueve dígitos que comienza por 9. No se alteran valores históricos ni se normalizan números automáticamente.
- Errores de correo/documento/colegiatura duplicados y errores de campos del servidor se presentan junto al campo implicado, conservando el formulario. El aviso se limpia cuando se corrige ese campo. El éxito continúa dependiendo de la respuesta HTTP y actualiza el listado.
- Avatares, títulos explícitos y permisos fotográficos conservados. No se añadieron imágenes ficticias al producto.

## Archivos de esta intervención

1. `frontend/src/pages/Veterinarios.jsx`: clases específicas, textos, iconos, modales y uso de validación por campo.
2. `frontend/src/pages/Veterinarios.css`: tonos suaves y refinamiento limitado a Veterinarios y a su variante de formularios.
3. `frontend/src/lib/veterinarians.js`: presentación de requisitos faltantes, validación local y asignación de errores del servidor.
4. `frontend/src/components/EditorForm.jsx`: opciones reutilizables de validación y errores por campo, activadas para la variante Veterinarios; se conserva el comportamiento predeterminado de otros módulos.
5. `frontend/tests/veterinarians.test.mjs`: tres pruebas adicionales de validación y asignación de errores.
6. `frontend/tests/visual/veterinarians.html`: ampliación de la prueba de interfaz con registro simulado, duplicidad, contraseña común, celular y transición desde vinculación.

Creados este informe y `evidencias-veterinarios-refinamiento/` con `listado.png`, `registro.png`, `vincular.png` y `eliminar.png`. Las capturas contienen **datos sintéticos de una API en memoria**, nunca registros de la instalación habitual.

No se modificaron archivos Java, entidades, endpoints, DTO, política compartida de contraseñas ni migraciones en esta intervención. Los cambios locales anteriores de Usuarios, Mi perfil y seguridad permanecen conservados.

## Validaciones ejecutadas

| Validación | Resultado |
|---|---|
| Node frontend completo | **179 pruebas correctas**, 0 fallos |
| Maven verify en copia aislada / PostgreSQL 18 Testcontainers | **194 correctas**: 72 unitarias + 122 de integración; 0 fallos, errores u omitidas |
| Veterinarios, escritorio sin candidatos | **31 comprobaciones correctas** |
| Veterinarios, móvil de 375 px | **30 comprobaciones correctas** |
| Veterinarios, escritorio con candidato elegible | **30 comprobaciones correctas** |
| Veterinarios, PROPIETARIO solo lectura | **10 comprobaciones correctas** |
| Regresión Usuarios | **26 comprobaciones correctas** |
| Mi perfil / cambio de contraseña ADMIN, VETERINARIO y PROPIETARIO | **19 comprobaciones correctas por rol** |
| Teclado en confirmación y vinculación | Escape cierra y restaura el foco; Shift+Tab queda dentro del modal; Enter abre registro sin superposición |
| npm run build | Correcto; advertencia de chunk de más de 500 kB |
| ESLint de archivos intervenidos | Correcto |
| npm run lint global | Persisten **7 errores y 1 advertencia anteriores**, fuera del alcance |
| git diff --check | Correcto |

Los tests visuales usan los componentes reales con un adaptador Axios en memoria. Registro y DELETE de esos tests no llegan al backend habitual. El POST exitoso, el correo duplicado, los errores y las cancelaciones están comprobados sin crear o borrar cuentas habituales.

La regresión de backend ejecutó los servicios reales en PostgreSQL temporal desechable, incluidas sus migraciones existentes exclusivamente en ese entorno. Resultados en `/private/tmp/vetcare-verificacion-8phxn1ed/target`, log `/private/tmp/vetcare-vets-refined-backend.log`.

Otros logs: `/private/tmp/vetcare-vets-refined-tests.log`, `/private/tmp/vetcare-vets-refined-build.log`, `/private/tmp/vetcare-vets-refined-lint.log` y `/private/tmp/vetcare-vets-refined-global-lint.log`.

## Conexión y límites

Se reutilizan `GET /api/veterinarios`, `GET /api/usuarios`, `GET /api/veterinarios/{id}/foto`, `POST /api/veterinarios/registro`, `POST /api/veterinarios`, `PUT /api/veterinarios/{id}` y `DELETE /api/veterinarios/{id}`. Los datos del producto siguen viniendo del backend. No se alteraron permisos ni transacciones: el registro conjunto de cuenta y perfil conserva su transacción existente y sus comprobaciones de duplicidad.

La validación peruana adicional corresponde al formulario; el backend conserva sus reglas anteriores, más generales para celular y documento. No se presenta esa validación de interfaz como una nueva restricción universal de la API. Si hay números históricos internacionales, el formulario los señalará al guardar; no se cambian sin intervención del administrador. Endurecer esa política en todo el backend requiere acordar cómo tratar esos datos y queda fuera de este refinamiento visual.

Lint global pendiente en App, RecordatoriosBell, ResourcePage, VetDateTimePicker, AuthContext y MisPublicaciones. No se modificaron para corregir problemas ajenos al alcance. La advertencia de tamaño del build no impide compilar; optimizar el empaquetado también queda para otra tarea.

No se ha actualizado todavía la instalación habitual en `localhost:8088`. La comprobación con datos habituales queda pendiente de despliegue y debe comenzar con consultas y cancelaciones, sin escrituras o eliminaciones no autorizadas.

## Repetir verificaciones

Desde la raíz del repositorio:

```sh
python3 scripts/verificar_aislado.py
cd frontend
node --test tests/*.test.mjs
npx eslint src/pages/Veterinarios.jsx src/lib/veterinarians.js src/components/EditorForm.jsx
npm run build
npm run lint
```

Para interfaz sin API real, desde `frontend/`:

```sh
VITE_API_URL=http://127.0.0.1:1/api npm run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Abrir `/tests/visual/veterinarians.html?auto=1`, `/tests/visual/veterinarians.html?auto=1&elegible=1` o `/tests/visual/veterinarians-responsive.html` bajo `http://127.0.0.1:5174`. Se utilizan únicamente datos en memoria.

## Actualizar después de revisar: comandos NO ejecutados

Esta intervención modifica solo frontend. Para desplegarla sin reiniciar backend ni PostgreSQL:

```sh
cd '/Users/rosapp/Desktop/Proyecto Vetcare/vetcare-clinica-veterinaria'
docker compose build frontend
docker compose up -d --no-deps frontend
```

Luego abrir `http://localhost:8088`. No ejecutar `docker compose up` general: no hace falta actualizar backend para este refinamiento. Si quedan cambios de backend de intervenciones anteriores sin desplegar, revisarlos y autorizarlos separadamente; estos comandos no los activan.

## Restricciones respetadas

Sin modificaciones de `vetcare_mejoras` o `vetcare_docker`, sin Flyway sobre bases habituales, sin eliminación de fotos o registros habituales, sin cambios de `.env`, credenciales o volúmenes. No se reconstruyeron los contenedores habituales ni se inició o reinició el backend habitual. Sin commits, push, merges ni cambios de rama.
