#!/usr/bin/env bash
set -euo pipefail

BOOT_VERSION="${1:?usage: consumer-probe.sh <spring-boot-version>}"
IF_VERSION="$(./gradlew -q properties --property version | sed -n 's/^version: //p')"
PROBE="$(mktemp -d)"
status=0
OPTIONAL="spring-security|micrometer-core|resilience4j|spring-boot-actuator|spring-boot-health|spring-orm|jakarta\.persistence|spring-data|jimmer|openmapper"

emit_pom() {
  mkdir -p "$1"
  cat > "$1/pom.xml" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    $2
    <groupId>probe</groupId><artifactId>probe</artifactId><version>1.0</version>
    <properties><java.version>21</java.version><maven.compiler.release>21</maven.compiler.release></properties>
    <dependencies>
        $3
        <dependency>
            <groupId>io.github.serfom256</groupId>
            <artifactId>intentflow-spring-boot-starter</artifactId>
            <version>${IF_VERSION}</version>
        </dependency>
    </dependencies>
</project>
POM
}

intentflow_dep() {
  echo "<dependency><groupId>io.github.serfom256</groupId><artifactId>$1</artifactId><version>${IF_VERSION}</version></dependency>"
}

echo "== Probe A: the starter alone must not pull in any executor, dialect or database driver =="
emit_pom "$PROBE/a" "" ""
mvn -B -q -f "$PROBE/a/pom.xml" dependency:tree -DoutputFile="$PROBE/a/tree.txt"
leaked="$(grep -E "intentflow-(jdbc|jpa|jimmer|openmapper|spring-data|postgresql|mysql|h2|cassandra)|spring-jdbc|HikariCP|hibernate|java-driver-core|postgresql:|mysql-connector|$OPTIONAL" "$PROBE/a/tree.txt" || true)"
if [ -n "$leaked" ]; then
  echo "FAIL: the starter pulled in optional modules:"
  echo "$leaked"
  status=1
else
  echo "OK: only the core and Spring Boot's base starter."
fi

echo
echo "== Probe B: inside a Boot ${BOOT_VERSION} application, all Boot artifacts must be ${BOOT_VERSION} =="
emit_pom "$PROBE/b" \
  "<parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>${BOOT_VERSION}</version><relativePath/></parent>" \
  "$(intentflow_dep intentflow-jdbc)$(intentflow_dep intentflow-h2)<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency><dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency>"
mvn -B -f "$PROBE/b/pom.xml" dependency:tree > "$PROBE/b/tree.txt"
offending="$(grep -oE "org\.springframework\.boot:[A-Za-z0-9._-]+:jar:[^:]+" "$PROBE/b/tree.txt" \
             | grep -v ":jar:${BOOT_VERSION}\$" || true)"
if [ -n "$offending" ]; then
  echo "FAIL: spring-boot artifacts off the ${BOOT_VERSION} line:"
  echo "$offending"
  status=1
else
  echo "OK: every spring-boot artifact is on the ${BOOT_VERSION} line."
fi

framework_versions="$(grep -oE "org\.springframework:[A-Za-z0-9._-]+:jar:[^:]+" "$PROBE/b/tree.txt" \
                      | sed 's/.*:jar://' | sort -u)"
if [ "$(echo "$framework_versions" | wc -l)" -gt 1 ]; then
  echo "FAIL: mixed Spring Framework versions on one classpath:"
  echo "$framework_versions"
  status=1
else
  echo "OK: Spring Framework modules agree on ${framework_versions}."
fi

optional="$(grep -E "$OPTIONAL" "$PROBE/b/tree.txt" || true)"
if [ -n "$optional" ]; then
  echo "FAIL: optional libraries on the application's classpath, so Probe C would not test running without them:"
  echo "$optional"
  status=1
else
  echo "OK: no Spring Security, micrometer-core, Resilience4j, Actuator, JPA or Spring Data on the classpath."
fi

echo
echo "== Probe C: a Boot ${BOOT_VERSION} application with IntentFlow must start =="
mkdir -p "$PROBE/b/src/main/java/probe" "$PROBE/b/src/main/resources"
cat > "$PROBE/b/src/main/resources/application.properties" <<'PROPS'
intentflow.llm.model=probe
PROPS
cat > "$PROBE/b/src/main/java/probe/ProbeApp.java" <<'JAVA'
package probe;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class ProbeApp {

    @IntentService(value = "books", uri = "/books/{{id}}")
    public record Book(@IntentField long id, @IntentField String title) {
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(ProbeApp.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext ctx = app.run(args)) {
            ctx.getBean(IntentPipeline.class);
            System.out.println("PROBE_DIALECT=" + ctx.getBean(SqlDialect.class).name());
            System.out.println("PROBE_CONTEXT_STARTED");
        }
    }
}
JAVA
if mvn -B -f "$PROBE/b/pom.xml" -q compile exec:java \
      -Dexec.mainClass=probe.ProbeApp > "$PROBE/b/run.txt" 2>&1 \
   && grep -q PROBE_CONTEXT_STARTED "$PROBE/b/run.txt" && grep -q PROBE_DIALECT=h2 "$PROBE/b/run.txt"; then
  echo "OK: context started against an embedded DataSource ($(grep -o 'PROBE_DIALECT=.*' "$PROBE/b/run.txt"))."
else
  echo "FAIL: context did not start with the h2 dialect:"
  grep -o 'PROBE_DIALECT=.*' "$PROBE/b/run.txt" || true
  grep -E "NoClassDefFoundError|ClassNotFoundException|Caused by|APPLICATION FAILED|Description:" -A2 \
       "$PROBE/b/run.txt" | head -12
  status=1
fi

exit $status
