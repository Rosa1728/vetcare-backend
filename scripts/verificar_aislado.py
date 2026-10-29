#!/usr/bin/env python3
"""Ejecuta Maven en una copia temporal, nunca en target del backend habitual.

No copia application.properties, .env, uploads ni target. Conserva los resultados
bajo /private/tmp para revisión. No inicia Spring Boot contra la base habitual.
"""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile


def main():
    project = Path(__file__).resolve().parents[1]
    isolated = Path(tempfile.mkdtemp(prefix="vetcare-verificacion-", dir="/private/tmp"))
    if isolated == project or project in isolated.parents:
        raise RuntimeError("El directorio de pruebas debe estar fuera del proyecto habitual")
    support = (project / "src/test/java/pe/edu/utec/vetcare/integration/PostgresIntegrationSupport.java").read_text()
    required = ['withDatabaseName("vetcare_p0_integration")', 'classpath:/application-integration.properties',
                'spring.flyway.url', 'spring.datasource.url', 'withReuse(false)']
    if not all(part in support for part in required):
        raise RuntimeError("La configuración de integración no cumple las garantías de aislamiento")
    shutil.copy2(project / "pom.xml", isolated / "pom.xml")
    for folder in ("src/main/java", "src/main/resources/db", "src/test"):
        shutil.copytree(project / folder, isolated / folder)
    if (isolated / "src/main/resources/application.properties").exists():
        raise RuntimeError("No se permite copiar la configuración habitual")
    # Nunca heredar opciones que puedan redirigir Spring a una base/configuración real.
    blocked = ("SPRING_", "JWT_", "VETCARE_", "MAIL_", "DB_", "PG", "MAVEN_")
    environment = {key: value for key, value in os.environ.items()
                   if not key.startswith(blocked) and key not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")}
    environment["MAVEN_SKIP_RC"] = "true"
    environment["MAVEN_OPTS"] = "-Dspring.devtools.restart.enabled=false"
    print("Copia y salida de compilación aisladas:", isolated, flush=True)
    print("Configuración habitual/.env/uploads/target: excluidos.", flush=True)
    print("Datasource y Flyway: PostgreSQL temporal vetcare_p0_integration, sin fallback.", flush=True)
    result = subprocess.run(["mvn", "-o", "verify"], cwd=isolated, env=environment, check=False)
    print("Resultados conservados en:", isolated / "target", flush=True)
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
