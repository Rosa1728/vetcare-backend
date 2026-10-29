# Pruebas automatizadas P0

## Requisitos

- Java 21 y Maven (o el wrapper `./mvnw`).
- Docker Desktop o un motor Docker compatible, iniciado y accesible para el usuario que ejecuta Maven.
- En la primera ejecución puede necesitarse red para dependencias de Maven, `postgres:18-alpine` y la imagen de limpieza de Testcontainers.
- No se necesita iniciar VetCare, configurar PostgreSQL local ni proporcionar cuentas personales.

## Comandos

Desde la carpeta del backend:

```sh
mvn test
mvn verify
```

`test` ejecuta las 46 pruebas unitarias existentes, sin Docker. `verify` ejecuta además `P0HttpIT` mediante Failsafe. La clase base `PostgresIntegrationSupport` permite agregar futuras pruebas `*IT` con el mismo aislamiento.

Para ejecutar solo las pruebas de integración P0, sin repetir las unitarias:

```sh
mvn test-compile failsafe:integration-test failsafe:verify -Dit.test=P0HttpIT
```

También se pueden usar estos comandos con `./mvnw` en lugar de `mvn`. Si dependencias e imágenes están disponibles localmente, puede agregarse `-o` a Maven; esta opción no impide que Docker descargue una imagen faltante.

## Aislamiento

La suite carga `application-integration.properties` en lugar de `application.properties`. `DynamicPropertySource` conecta tanto Hibernate como Flyway al PostgreSQL temporal de Testcontainers, con nombre `vetcare_p0_integration`, puerto asignado dinámicamente y credenciales sintéticas. No hay alternativa de conexión a la base habitual. Si Docker no responde, las pruebas fallan; no se omiten silenciosamente.

Flyway ejecuta las migraciones del proyecto, V1–V8, únicamente en el contenedor. Hibernate usa `validate`, por lo que no crea ni amplía columnas para encubrir una migración faltante. La suite verifica el historial de Flyway y las longitudes declaradas por PostgreSQL.

Las tareas de correo/recordatorios y el bootstrap de ADMIN se desactivan para estas pruebas. Los datos sintéticos se crean con prefijo P0 único por caso y roles obtenidos de V2. No se borran tablas ni se ejecutan migraciones nuevas. El contenedor se destruye al terminar, sin modificar `vetcare_mejoras`.

Los casos no usan una transacción de test compartida: las escrituras HTTP hacen commit y los GET/JDBC posteriores leen el contenido persistido de forma independiente.

## Cobertura

- Login real con BCrypt y JWT del backend; filtros y autorización de método activos, sin `@WithMockUser` ni JWT simulado.
- ADMIN recibe 409 al reprogramar una cita ATENDIDA o CANCELADA. Se verifica el mensaje, la cita completa por GET y su fila de PostgreSQL sin cambios. La atención asociada tampoco cambia.
- ADMIN conserva la reprogramación de citas pendientes.
- Cuatro textos distintos de 500 caracteres con acentos: creación y actualización por HTTP, lectura por GET y comparación exacta con PostgreSQL, incluida `char_length`.
- Cuatro textos de 501 caracteres: 400, sin insertar atención ni cerrar la cita.
- Sin token o token inválido: 401. Credenciales sintéticas incorrectas: 401.
- Veterinario que intenta reprogramar: 403. Propietario que intenta escribir una atención: 403.

MockMvc ejecuta el flujo HTTP de Spring MVC en memoria; no usa un puerto público ni prueba TLS o un proxy de despliegue. No se imprimen solicitudes/respuestas de MockMvc, contraseñas, tokens ni cabeceras de autorización. Evitar activar `print()`, trazas de seguridad o logs de parámetros SQL.

## Resultados

- Unitarias: `target/surefire-reports/`.
- Integración: `target/failsafe-reports/`.

Un resultado válido requiere cero fallos y cero errores en ambas suites; compilar o pasar solo `mvn test` no verifica persistencia PostgreSQL ni los códigos HTTP de integración.
