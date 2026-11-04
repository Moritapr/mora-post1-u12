# mora-post1-u12 - Proyecto Integrador: Sistema de Solicitudes de Reembolso de Gastos Corporativos

Post-contenido U12 (Patrones de Diseño de Software, UDES). Proyecto Maven `reembolsos-integrado`
(Spring Boot 3.5.16, Java 17; dependencias: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
`h2` en runtime y `spring-boot-starter-test`). Paquete base `com.empresa.reembolsos`.

```bash
mvn clean test      # pruebas
mvn spring-boot:run # API en http://localhost:8080/api/solicitudes
```

Todas las salidas pegadas en este documento se obtuvieron ejecutando el comando indicado.

---

## Parte 1 - Análisis de requisitos, diseño propuesto e implementación

### Diseño Propuesto

Escrito **antes** de implementar cualquier patrón (commit `docs:` de línea base). Para cada requisito se
identifica qué varía, qué patrón lo resuelve y por qué ese y no otro (test de explicabilidad: una oración).

| RF | Qué varía (punto de variabilidad) | Patrón | Justificación (una oración) |
|---|---|---|---|
| RF1 - Validación en varias etapas | El **conjunto de verificaciones** que una solicitud debe superar (hoy 3; mañana p. ej. el límite anual acumulado), cada una con su propio motivo de rechazo | **Chain of Responsibility** | Las verificaciones se aplican *todas*, en secuencia, y la primera que rechaza corta el recorrido con su motivo, así que modelar cada una como un eslabón que solo conoce al siguiente permite agregar el límite anual como un eslabón nuevo sin modificar ni conocer los tres existentes. |
| RF2 - Cálculo distinto por categoría | La **fórmula del monto aprobado**, que depende de la categoría (tope diario x días, monto completo hasta tope mensual, 80 % hasta tope anual; mañana "bienestar") | **Strategy** | Para cada solicitud se aplica exactamente *una* fórmula entre varias intercambiables, elegida en tiempo de ejecución por la categoría, de modo que "bienestar" será una clase nueva que implementa `CalculadoraReembolso` sin tocar las fórmulas de viaje, representación ni capacitación. |
| RF3 - Notificación simultánea | El **número de interesados** en un cambio de estado y la **reacción** de cada uno (empleado: todo; Contabilidad: solo APROBADA; auditoría: todo; mañana el panel de métricas) | **Observer** | Un mismo evento (la solicitud cambió de estado) debe llegar a *todos* los interesados, cada uno con su propio filtro y reacción, sin que el servicio que cambia el estado sepa quiénes son, así que el panel de métricas será un observador más registrado sin tocar el servicio. |

**Por qué son tres problemas distintos (y no el mismo patrón repetido).** Los tres requisitos "tienen
varias implementaciones", pero la relación entre ellas es diferente en cada uno:

| | Cuántas implementaciones actúan por solicitud | ¿Puede una cortar a las demás? | ¿Devuelven resultado? |
|---|---|---|---|
| RF1 (Chain) | Todas, en orden, hasta el primer rechazo | Sí: el rechazo detiene la cadena | Sí: válida / motivo |
| RF2 (Strategy) | Exactamente una, según la categoría | No aplica: solo se ejecuta una | Sí: monto aprobado |
| RF3 (Observer) | Todas, sin orden de negocio | No: todas deben enterarse | No: solo reaccionan |

**Alternativas consideradas y por qué no:**

- RF1 con Strategy (elegir *una* validación) no cumple el requisito: no se elige una, se exigen las tres.
  RF1 con una simple `List<Validador>` recorrida por el servicio funcionaría, pero dejaría en el servicio
  la política de corte y el orden; la cadena encapsula ambos en `CadenaValidadores`.
- RF2 con un `switch` sobre `CategoriaGasto` o con métodos abstractos en el enum concentra todas las
  fórmulas (y sus topes) en un mismo lugar que hay que **modificar** por cada categoría nueva, justo lo que
  RF2 prohíbe; además llevaría reglas de Finanzas al paquete `dominio`.
- RF3 con llamadas directas desde el servicio a los tres notificadores acopla el servicio a cada interesado
  y obliga a editarlo para el panel de métricas.
- La selección de la estrategia (`SelectorCalculadora`) es un mapa categoría -> calculadora construido con
  los beans que inyecta Spring; no se introduce una Factory aparte porque no hay lógica de creación que
  encapsular (las calculadoras no tienen estado).

### Estructura de paquetes y límites

```
src/main/java/com/empresa/reembolsos/
├── ReembolsosApplication.java
├── dominio/            SolicitudReembolso, CategoriaGasto, EstadoSolicitud (Java puro)
├── procesamiento/
│   ├── validacion/     RF1 - Chain of Responsibility
│   ├── calculo/        RF2 - Strategy
│   └── notificacion/   RF3 - Observer
├── aplicacion/         SolicitudReembolsoService (único que coordina los 3 patrones y el repositorio)
├── infraestructura/    RepositorioSolicitudesJpa (Spring Data JPA)
└── adaptadores/rest/   SolicitudReembolsoController (solo habla con aplicacion y dominio)
```

### Modelo de dominio sin Spring ni JPA (mapeo en `orm.xml`)

El enunciado pide un dominio sin dependencias de Spring ni de JPA, pero luego persiste
`SolicitudReembolso` con `JpaRepository<SolicitudReembolso, Long>`, lo que normalmente exige `@Entity`.
**Decisión:** el mapeo se declara en XML (`src/main/resources/META-INF/orm.xml`, registrado con
`spring.jpa.mapping-resources=META-INF/orm.xml`), un mecanismo estándar de JPA. Así el dominio queda
con 0 imports de `org.springframework` y `jakarta`, y el repositorio JPA funciona igual. Se descartó
`@Entity` en el dominio porque contradiría el requisito, y una clase de entidad JPA separada con mapper
porque duplicaría los 9 campos sin ningún beneficio hoy (YAGNI). Coste aceptado: el mapeo no está junto
a la clase y un renombre de campo debe replicarse en `orm.xml`. Si se olvida, el contexto **sí arranca**:
Hibernate solo emite un aviso y mapea el campo con valores por defecto (un enum pasaría a `ORDINAL`).
Comprobado renombrando temporalmente `diasViaje` en `orm.xml`:

```
WARN ... JPAXMLOverriddenAnnotationReader : HHH000207: Property 'com.empresa.reembolsos.dominio.SolicitudReembolso.diasDeViaje' not found in class but described in <mapping-file/> (possible typo error)
```

Por eso ese aviso debe tratarse como error al revisar el log de arranque.

Verificación (Hibernate genera la tabla desde `orm.xml`, `mvn -q test -Dspring.jpa.show-sql=true`):

```
Hibernate: create table solicitud_reembolso (dias_viaje integer, documentos_soporte boolean, fecha_gasto date not null, monto_aprobado numeric(15,2), monto_solicitado numeric(15,2) not null, id bigint generated by default as identity, empleado_id varchar(255) not null, categoria enum ('CAPACITACION','REPRESENTACION','VIAJE') not null, estado enum ('APROBADA','PENDIENTE','RECHAZADA','REQUIERE_CORRECCION') not null, primary key (id))
```

Imports del dominio:

```
$ grep -rnE "^import (org\.springframework|jakarta)" src/main/java/com/empresa/reembolsos/dominio/ | wc -l
       0
$ grep -rn "^import" src/main/java/com/empresa/reembolsos/dominio/
src/main/java/com/empresa/reembolsos/dominio/SolicitudReembolso.java:3:import java.math.BigDecimal;
src/main/java/com/empresa/reembolsos/dominio/SolicitudReembolso.java:4:import java.time.LocalDate;
```

`mvn clean package` en la línea base:

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

### Implementación

| RF | Patrón | Clases (`procesamiento/...`) | Punto de extensión |
|---|---|---|---|
| RF1 | Chain of Responsibility | `validacion/ValidadorSolicitud` (eslabón base), `ValidadorDocumentosSoporte`, `ValidadorMontoMaximo`, `ValidadorDuplicados`, `CadenaValidadores`, `ResultadoValidacion` | Nueva subclase de `ValidadorSolicitud` + enlazarla en `CadenaValidadores` (los eslabones existentes no cambian) |
| RF2 | Strategy | `calculo/CalculadoraReembolso` (estrategia), `CalculadoraViaje`, `CalculadoraRepresentacion`, `CalculadoraCapacitacion`, `SelectorCalculadora` | Nueva clase `@Component` que implementa `CalculadoraReembolso` |
| RF3 | Observer | `notificacion/ObservadorCambioEstado` (observador), `NotificadorAuditoria` (`@Order(1)`), `NotificadorEmpleado` (`@Order(2)`), `NotificadorContabilidad` (`@Order(3)`) | Nueva clase `@Component` que implementa `ObservadorCambioEstado` y declara su `@Order` |

`aplicacion/SolicitudReembolsoService` es el único que coordina los tres patrones y el único que conoce
`infraestructura/RepositorioSolicitudesJpa`. `adaptadores/rest/SolicitudReembolsoController` solo habla
con el servicio; `adaptadores/rest/ManejadorErroresRest` (`@RestControllerAdvice`) traduce las
excepciones de aplicación a HTTP.

### Decisión de diseño 1 - Orden de la cadena de validadores

**Elegido: documentos -> monto -> duplicados** (de la verificación más barata a la más cara).

| Eslabón | Coste por solicitud |
|---|---|
| `ValidadorDocumentosSoporte` | O(1): lee un `boolean` |
| `ValidadorMontoMaximo` | O(1): búsqueda en un `Map` de 3 entradas y un `compareTo` |
| `ValidadorDuplicados` | O(n): recorre las pendientes del empleado y construye un `YearMonth` por cada una |

- **Con duplicados al final (elegido)** se gana que toda solicitud rechazada por documentos o por monto
  no recorre la lista, y que el empleado recibe primero el motivo más fácil de corregir (adjuntar
  soportes, ajustar el monto). Se pierde que una solicitud duplicada ejecuta antes dos verificaciones
  O(1) inútiles, un coste constante y despreciable.
- **Con duplicados primero** se ganaría cortar antes solo si los duplicados fueran el rechazo más
  frecuente (no hay datos que lo indiquen); se pierde que *todas* las solicitudes, incluidas las que
  fallarían por un `boolean`, pagan el recorrido, y que una solicitud sin soportes y duplicada se
  rechazaría como "duplicada" aunque, aun quitando la otra, seguiría siendo inválida.

Matiz honesto: en este diseño la consulta a la base (`findByEmpleadoIdAndEstado`) la hace el servicio
**antes** de invocar la cadena, así que el orden ahorra el recorrido en memoria, no la consulta. Pasar
un proveedor perezoso (`Supplier<List<...>>`) para ahorrarla también se descartó: la propia regla de
duplicados limita la lista a una pendiente por empleado, categoría y mes, de modo que es corta, y
cambiar la firma de todos los eslabones por esa ganancia no supera el test de explicabilidad. El orden
queda fijado por `CadenaValidadoresTest` (con varios incumplimientos gana el primer eslabón).

### Decisión de diseño 2 - Selección de la calculadora: mapa desde `List<CalculadoraReembolso>` vs. `switch`

**Elegido: el mapa** que `SelectorCalculadora` construye con la lista de `@Component` que inyecta Spring.

RF2 exige agregar categorías "sin modificar el cálculo de las categorías existentes". Para agregar
"bienestar":

| | Mapa desde la lista (elegido) | `switch` manual sobre `CategoriaGasto` |
|---|---|---|
| Código nuevo | `CalculadoraBienestar` + la constante `BIENESTAR` en el enum | Igual |
| Código existente **modificado** | Ninguna línea: `SelectorCalculadora` y las 3 calculadoras quedan intactas | El método del `switch` (un `case` más) cada vez |
| Acoplamiento | El selector solo conoce la interfaz | El selector importa cada calculadora concreta |
| Categoría sin calculadora | Detectada al arrancar (ver abajo) | Detectada al compilar si el `switch` es una expresión exhaustiva |

Solo el mapa cumple RF2 sin tocar una línea ya escrita (la constante del enum es una línea agregada,
inevitable en ambas opciones, porque la categoría es un dato del dominio). Lo que el `switch` hacía mejor
era detectar una categoría olvidada; para no perderlo, `SelectorCalculadora` comprueba al construirse
que cada valor de `CategoriaGasto` tenga calculadora y que no haya dos para la misma, y si no, el
contexto no arranca (`IllegalStateException`, probado en `SelectorCalculadoraTest`). Alternativa
descartada: el `switch`, porque obliga a editar el selector en cada semestre que Finanzas agregue una
categoría, que es exactamente el cambio que RF2 anticipa.

### Desviaciones respecto al código del enunciado

| Cambio | Motivo |
|---|---|
| Mapeo JPA en `META-INF/orm.xml` en vez de `@Entity` | El dominio no puede importar Spring ni JPA (ver "Modelo de dominio"). |
| `ValidadorDuplicados` compara `YearMonth` (año y mes) en vez de `getMonth()` | Con `getMonth()`, marzo de 2025 y marzo de 2026 serían el mismo periodo y se rechazaría una solicitud legítima. Prueba: `pendienteDelMismoMesDeOtroAnio_noEsDuplicado`. |
| Se crean `SolicitudInvalidaException` y `SolicitudNoEncontradaException` en `aplicacion/` | El enunciado usa la primera sin definirla; la segunda reemplaza el `IllegalArgumentException("Solicitud no encontrada")`, que llegaría al cliente como 500. |
| `ManejadorErroresRest` (`@RestControllerAdvice`) | 400 con el motivo del validador, 404 para solicitud inexistente y 400 para JSON ilegible o categoría inexistente, en formato `ProblemDetail`. Solo usa `aplicacion`, `org.springframework.web`, `org.springframework.http` y `java` (Regla 2 de la Parte 2). |
| `crear` exige `empleadoId`, `categoria`, `montoSolicitado` y `fechaGasto` | Sin ellos los validadores lanzarían `NullPointerException` (500). Es una precondición de entrada, no una regla de negocio, por eso no es un eslabón de la cadena. |
| `crear` ignora `id`, `estado` y `montoAprobado` enviados por el cliente | Un `id` en el cuerpo del POST haría que `save` sobrescribiera otra solicitud. |
| `SelectorCalculadora` valida al arrancar que toda categoría tenga calculadora | Decisión de diseño 2. |
| Orden fijo de observadores con `@Order`: Auditoría -> Empleado -> Contabilidad | Sin `@Order`, la ejecución manual mostró Auditoría -> Contabilidad -> Empleado, un orden accidental del registro de beans. Como `forEach` se detiene en la primera excepción, RF3 ("auditoría registra todo cambio, sin excepción") dependía de esa casualidad. Auditoría va primero para que ningún fallo posterior la impida, y Contabilidad al final porque es la que integrará la API externa de pagos. El aislamiento de fallos por observador **no** se implementa (ver ADR-001, Parte 2). |

### Pruebas

`mvn clean test`:

```
[INFO] Tests run: 56, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| Clase de prueba | Tipo | Pruebas | Qué cubre |
|---|---|---|---|
| `ValidadorDocumentosSoporteTest` | Unitaria | 2 | Con soportes válida; sin soportes rechazo con motivo |
| `ValidadorMontoMaximoTest` | Unitaria | 6 | Monto igual al tope válido y sobre el tope rechazado, en las 3 categorías |
| `ValidadorDuplicadosTest` | Unitaria | 4 | Sin pendientes; duplicado mismo mes; otra categoría; mismo mes de otro año (no duplicado) |
| `CadenaValidadoresTest` | Unitaria | 4 | Válida completa y corte en el primer, segundo y tercer eslabón |
| `CalculadoraViajeTest` | Unitaria | 4 | Bajo y sobre el tope diario x días; 0 días |
| `CalculadoraRepresentacionTest` | Unitaria | 3 | Bajo y sobre el tope mensual |
| `CalculadoraCapacitacionTest` | Unitaria | 3 | 80 %; 80 % sobre el tope anual |
| `SelectorCalculadoraTest` | Unitaria | 4 | Selección por categoría; categoría sin calculadora o duplicada falla al construir; categoría nula |
| `NotificadoresTest` | Unitaria | 9 | Empleado; Contabilidad solo en APROBADA (y no en los otros 3 estados); Auditoría en los 4 estados |
| `SolicitudReembolsoServiceTest` | Unitaria (Mockito) | 6 | Crear válida / rechazada / duplicada / sin datos; cambiar estado notifica; 404 sin notificar |
| `FlujoSolicitudesIntegracionTest` | Integración `@SpringBootTest` + MockMvc + H2 | 10 | POST de las 3 categorías con su monto aprobado; 400 sin soportes, por duplicado y por categoría inexistente; PUT APROBADA y RECHAZADA con los 3 observadores como espías (`@MockitoSpyBean`) y Contabilidad actuando solo en APROBADA; orden de notificación Auditoría -> Empleado -> Contabilidad (`InOrder` y orden en consola); PUT 404 |
| `ReembolsosApplicationTests` | Contexto | 1 | El contexto arranca |

### Evidencia de ejecución manual

`java -jar target/reembolsos-integrado-0.0.1-SNAPSHOT.jar` (construido en `e60caf1`; el refactor posterior
solo afecta al arranque del selector) y peticiones con `curl`:

```
$ curl -s -X POST localhost:8080/api/solicitudes -H "Content-Type: application/json" -d '{"empleadoId":"E-1","categoria":"VIAJE","montoSolicitado":120000,"fechaGasto":"2026-03-15","diasViaje":2,"documentosSoporte":true}'
{"id":1,"empleadoId":"E-1","categoria":"VIAJE","montoSolicitado":120000,"montoAprobado":120000,"fechaGasto":"2026-03-15","diasViaje":2,"documentosSoporte":true,"estado":"PENDIENTE"}

$ curl -s -X POST localhost:8080/api/solicitudes -H "Content-Type: application/json" -d '{"empleadoId":"E-1","categoria":"REPRESENTACION","montoSolicitado":1500000,"fechaGasto":"2026-03-15","documentosSoporte":true}'
{"id":2,"empleadoId":"E-1","categoria":"REPRESENTACION","montoSolicitado":1500000,"montoAprobado":1500000,"fechaGasto":"2026-03-15","diasViaje":0,"documentosSoporte":true,"estado":"PENDIENTE"}

$ curl -s -X POST localhost:8080/api/solicitudes -H "Content-Type: application/json" -d '{"empleadoId":"E-1","categoria":"CAPACITACION","montoSolicitado":1000000,"fechaGasto":"2026-03-15","documentosSoporte":true}'
{"id":3,"empleadoId":"E-1","categoria":"CAPACITACION","montoSolicitado":1000000,"montoAprobado":800000.0,"fechaGasto":"2026-03-15","diasViaje":0,"documentosSoporte":true,"estado":"PENDIENTE"}

$ curl -s -w ' HTTP %{http_code}' -X POST localhost:8080/api/solicitudes -H "Content-Type: application/json" -d '{"empleadoId":"E-2","categoria":"VIAJE","montoSolicitado":100000,"fechaGasto":"2026-03-15","diasViaje":1,"documentosSoporte":false}'
{"type":"about:blank","title":"Solicitud invalida","status":400,"detail":"Faltan documentos soporte","instance":"/api/solicitudes"} HTTP 400

$ curl -s -X PUT localhost:8080/api/solicitudes/3/estado -H "Content-Type: application/json" -d '"APROBADA"'
{"id":3,"empleadoId":"E-1","categoria":"CAPACITACION","montoSolicitado":1000000.00,"montoAprobado":800000.00,"fechaGasto":"2026-03-15","diasViaje":0,"documentosSoporte":true,"estado":"APROBADA"}

$ curl -s -X PUT localhost:8080/api/solicitudes/1/estado -H "Content-Type: application/json" -d '"RECHAZADA"'
{"id":1,"empleadoId":"E-1","categoria":"VIAJE","montoSolicitado":120000.00,"montoAprobado":120000.00,"fechaGasto":"2026-03-15","diasViaje":2,"documentosSoporte":true,"estado":"RECHAZADA"}

$ curl -s -w ' HTTP %{http_code}' -X PUT localhost:8080/api/solicitudes/99/estado -H "Content-Type: application/json" -d '"APROBADA"'
{"type":"about:blank","title":"Solicitud no encontrada","status":404,"detail":"Solicitud no encontrada: 99","instance":"/api/solicitudes/99/estado"} HTTP 404
```

Consola de la aplicación durante los dos PUT (los tres observadores reaccionan; Contabilidad solo en
APROBADA). Esta salida es anterior a `@Order` y muestra el orden accidental Auditoría -> Contabilidad ->
Empleado que motivó fijarlo:

```
2026-11-04T01:26:12.662-05:00  INFO 64875 --- [reembolsos-integrado] [nio-8080-exec-9] c.e.r.p.n.NotificadorAuditoria           : Auditoria: solicitud 3 cambio de PENDIENTE a APROBADA
Contabilidad: programar pago de 800000.00 para E-1
Notificando a E-1: su solicitud paso de PENDIENTE a APROBADA
2026-11-04T01:26:12.671-05:00  INFO 64875 --- [reembolsos-integrado] [nio-8080-exec-1] c.e.r.p.n.NotificadorAuditoria           : Auditoria: solicitud 1 cambio de PENDIENTE a RECHAZADA
Notificando a E-1: su solicitud paso de PENDIENTE a RECHAZADA
```

La diferencia de escala (`800000.0` en el POST, `800000.00` en el PUT) es porque el POST devuelve el
objeto en memoria y el PUT lo relee de la base, cuya columna es `numeric(15,2)`; el valor es el mismo.

### Límites entre paquetes (grep de imports)

```
$ grep -n "^import" src/main/java/com/empresa/reembolsos/procesamiento/validacion/ValidadorDuplicados.java
3:import com.empresa.reembolsos.dominio.SolicitudReembolso;
4:import java.time.YearMonth;
5:import java.util.List;
6:import org.springframework.stereotype.Component;

$ grep -rlE "^import (com\.empresa\.reembolsos\.infraestructura|org\.springframework\.data|jakarta\.persistence)" src/main/java/com/empresa/reembolsos/procesamiento/ | wc -l
       0
$ grep -rnE "^import (org\.springframework|jakarta)" src/main/java/com/empresa/reembolsos/dominio/ | wc -l
       0

$ grep -rh "^import" src/main/java/com/empresa/reembolsos/adaptadores/rest/ | sort -u
import com.empresa.reembolsos.aplicacion.SolicitudInvalidaException;
import com.empresa.reembolsos.aplicacion.SolicitudNoEncontradaException;
import com.empresa.reembolsos.aplicacion.SolicitudReembolsoService;
import com.empresa.reembolsos.dominio.EstadoSolicitud;
import com.empresa.reembolsos.dominio.SolicitudReembolso;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
```

### Historial de commits (Parte 1)

| Commit | Contenido |
|---|---|
| `82fba1d` docs | Diseño Propuesto, `pom.xml` y dominio (antes de cualquier patrón) |
| `a8620b9` feat | RF1: cadena de validadores + pruebas |
| `35bfac3` feat | RF2: calculadoras y selector + pruebas |
| `9d17250` feat | RF3: observadores + pruebas |
| `90c2677` feat | Servicio, repositorio, controlador, excepciones y advice |
| `e60caf1` test | Pruebas del servicio y de integración del flujo completo |
| `898eedb` refactor | Selector valida al arrancar que toda categoría tenga calculadora |
| `a03ac7b` docs | Decisiones 1 y 2, desviaciones, pruebas y evidencia |

---

## Parte 2 - Decisión arquitectónica y análisis de impacto

La decisión arquitectónica está en [`docs/adr/ADR-001.md`](docs/adr/ADR-001.md).

### Reglas de arquitectura (ArchUnit 1.2.1)

`src/test/java/com/empresa/reembolsos/ReglasArquitectura.java`, con
`@AnalyzeClasses(packages = "com.empresa.reembolsos", importOptions = ImportOption.DoNotIncludeTests.class)`.

| Regla | Qué verifica |
|---|---|
| `procesamientoSinPersistenciaNiWeb` | `procesamiento` no depende de `infraestructura`, `adaptadores`, `org.springframework.data`, `jakarta.persistence`, `org.springframework.web` ni `org.springframework.http` |
| `controladorSoloUsaServicio` | `adaptadores.rest` solo accede a sí mismo, a `aplicacion`, `dominio`, `org.springframework.web`, `org.springframework.http` y `java` |
| `calculadorasImplementanInterfaz` | Las clases `Calculadora*` de `procesamiento.calculo` implementan `CalculadoraReembolso` |
| `notificadoresImplementanInterfaz` | Las clases de `procesamiento.notificacion` implementan `ObservadorCambioEstado` |

**Hallazgos al ejecutar las reglas tal como vienen en el enunciado** (`mvn test -Dtest=ReglasArquitectura`):

```
[ERROR] Tests run: 4, Failures: 2, Errors: 0, Skipped: 0
```

1. **Regla 3: `SelectorCalculadora` no es una calculadora.** Vive en `procesamiento.calculo` (junto a las
   estrategias que elige) y no implementa `CalculadoraReembolso`, ni debe hacerlo:

   ```
   Architecture Violation [Priority: MEDIUM] - Rule 'classes that reside in a package '..procesamiento.calculo..' and are not interfaces should implement com.empresa.reembolsos.procesamiento.calculo.CalculadoraReembolso' was violated (1 times):
   Class <com.empresa.reembolsos.procesamiento.calculo.SelectorCalculadora> does not implement com.empresa.reembolsos.procesamiento.calculo.CalculadoraReembolso in (SelectorCalculadora.java:0)
   ```

   Solución: acotar la regla con `.and().haveSimpleNameStartingWith("Calculadora")`, sin mover ni
   ocultar el selector. La convención de nombres ya era la del proyecto (`CalculadoraViaje`,
   `CalculadoraRepresentacion`, `CalculadoraCapacitacion`), y una `CalculadoraBienestar` que olvide
   implementar la interfaz seguiría siendo detectada.
2. **Regla 2: falla sin ninguna dependencia prohibida.** Las 6 violaciones son accesos de cada clase a sí
   misma, porque la lista permitida no incluye el propio paquete `adaptadores.rest`. Fallaría incluso con
   el controlador literal del enunciado, que asigna y lee su campo `servicio`:

   ```
   Architecture Violation [Priority: MEDIUM] - Rule 'classes that reside in a package '..adaptadores.rest..' should only access classes that reside in any package ['..aplicacion..', '..dominio..', 'org.springframework.web..', 'org.springframework.http..', 'java..']' was violated (6 times):
   Constructor <com.empresa.reembolsos.adaptadores.rest.SolicitudReembolsoController.<init>(com.empresa.reembolsos.aplicacion.SolicitudReembolsoService)> sets field <com.empresa.reembolsos.adaptadores.rest.SolicitudReembolsoController.servicio> in (SolicitudReembolsoController.java:23)
   Method <com.empresa.reembolsos.adaptadores.rest.ManejadorErroresRest.cuerpoIlegible(org.springframework.http.converter.HttpMessageNotReadableException)> calls method <com.empresa.reembolsos.adaptadores.rest.ManejadorErroresRest.problema(org.springframework.http.HttpStatus, java.lang.String, java.lang.String)> in (ManejadorErroresRest.java:30)
   Method <com.empresa.reembolsos.adaptadores.rest.ManejadorErroresRest.solicitudInvalida(com.empresa.reembolsos.aplicacion.SolicitudInvalidaException)> calls method <com.empresa.reembolsos.adaptadores.rest.ManejadorErroresRest.problema(org.springframework.http.HttpStatus, java.lang.String, java.lang.String)> in (ManejadorErroresRest.java:20)
   Method <com.empresa.reembolsos.adaptadores.rest.ManejadorErroresRest.solicitudNoEncontrada(com.empresa.reembolsos.aplicacion.SolicitudNoEncontradaException)> calls method <com.empresa.reembolsos.adaptadores.rest.ManejadorErroresRest.problema(org.springframework.http.HttpStatus, java.lang.String, java.lang.String)> in (ManejadorErroresRest.java:25)
   Method <com.empresa.reembolsos.adaptadores.rest.SolicitudReembolsoController.cambiarEstado(java.lang.Long, com.empresa.reembolsos.dominio.EstadoSolicitud)> gets field <com.empresa.reembolsos.adaptadores.rest.SolicitudReembolsoController.servicio> in (SolicitudReembolsoController.java:33)
   Method <com.empresa.reembolsos.adaptadores.rest.SolicitudReembolsoController.crear(com.empresa.reembolsos.dominio.SolicitudReembolso)> gets field <com.empresa.reembolsos.adaptadores.rest.SolicitudReembolsoController.servicio> in (SolicitudReembolsoController.java:28)
   ```

   Solución: agregar `"..adaptadores.rest.."` a la lista permitida. Con eso, la regla sigue prohibiendo
   todo lo externo (infraestructura, procesamiento, Spring Data).
3. **`DoNotIncludeTests` es necesario y el prefijo no lo sustituye.** Las pruebas unitarias viven en los
   mismos paquetes que el código. Quitando la opción, las Reglas 3 y 4 fallan por
   `CalculadoraViajeTest`, `CalculadoraRepresentacionTest`, `CalculadoraCapacitacionTest` (que además
   empiezan por "Calculadora") y `NotificadoresTest`.
4. **Regla 1 endurecida.** Su nombre dice "NiWeb", pero en el enunciado solo prohíbe `..adaptadores..`:
   un `RestClient` dentro de `procesamiento` pasaría la regla. Se agregan `org.springframework.web..` y
   `org.springframework.http..`, coherente con ADR-001 (el cliente de la API de Contabilidad vivirá en
   `infraestructura`).

5. **Surefire no ejecutaba las reglas en la suite completa.** `ReglasArquitectura` no sigue los patrones
   por defecto de Surefire (`*Test`, `*Tests`, `Test*`, `*TestCase`). Solo corría con
   `-Dtest=ReglasArquitectura`: `mvn clean verify` informaba 56 pruebas y 0 ejecuciones de
   `ReglasArquitectura`. Se agrega un `<include>` explícito en `maven-surefire-plugin` y, desde
   entonces, `mvn clean verify` ejecuta 60 pruebas (56 + 4 reglas), sin renombrar la clase que pide el
   enunciado.

Resultado final:

```
$ mvn test -Dtest=ReglasArquitectura
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Decisión de diseño 3 - Capas simples vs. Hexagonal

Documentada en ADR-001.

### Decisión de diseño 4 - Por qué la Regla 1 distingue los estereotipos de Spring del acceso a datos y web

La Regla 1 prohíbe en `procesamiento` las dependencias de persistencia (`org.springframework.data`,
`jakarta.persistence`, `infraestructura`) y de web (`adaptadores`, `org.springframework.web/http`),
pero no todo `org.springframework`. El criterio es si la dependencia cambia **cómo se comporta o cómo se
prueba** la lógica, o solo **cómo se registra**:

- **Estereotipos (aceptables).** Los únicos imports de Spring en `procesamiento` son
  `org.springframework.stereotype.Component` (11 clases) y `org.springframework.core.annotation.Order`
  (3 observadores). Son metadatos de registro y orden: no cambian ninguna línea de lógica, y las 9 clases
  de prueba unitaria de `procesamiento` instancian validadores, calculadoras y notificadores con `new`,
  sin contexto de Spring. Prohibirlos obligaría a declarar los 11 beans a mano en una clase
  `@Configuration`, y además a mano el orden de la cadena y de los observadores, que hoy está en
  `CadenaValidadores` y en `@Order`. Sería más código y más puntos de cambio sin desacoplar nada.
- **Acceso a datos y web (no aceptables).** Cambian el comportamiento y la forma de probar. Si
  `ValidadorDuplicados` consultara `RepositorioSolicitudesJpa`, la cadena haría consultas por su cuenta,
  sus pruebas necesitarían una base de datos y se perdería la decisión de que el servicio entregue las
  pendientes ya cargadas. Un cliente HTTP en un notificador haría que una prueba unitaria dependiera de la
  red.

Prohibir todo `org.springframework` sería una regla más simple de escribir, pero castigaría lo inocuo
(anotaciones) igual que lo dañino (dependencias de infraestructura). Una regla que obliga a escribir
excepciones o código de configuración por algo inofensivo acaba desactivándose.

### Qué se descartó y por qué

Criterio de la Guía (sección 4.2): ¿la variabilidad que justificaría la alternativa es real y está
documentada hoy, o es una especulación sobre el futuro? Ninguno de estos descartes es definitivo: cada uno
es la decisión vigente hoy y lleva la señal que haría reconsiderarlo.

#### Ejemplos de referencia del enunciado

**1. `ApplicationEventPublisher` + `@EventListener` en lugar de inyectar `List<ObservadorCambioEstado>`.**
- *Qué se consideró:* que `cambiarEstado` publicara un evento de dominio ("solicitud cambió de estado") y
  que cada interesado lo escuchara con `@EventListener`, como en la Guía (sección 2.3).
- *Por qué se descartó:* hay exactamente tres interesados, fijos y conocidos al compilar, y la interfaz
  `ObservadorCambioEstado` ya desacopla el servicio de ellos. Hoy, buscar las implementaciones de la
  interfaz muestra quién reacciona; con un bus genérico, habría que rastrear quién escucha un tipo de
  evento. El bus tampoco resuelve el riesgo real que encontramos: los listeners síncronos también se
  ejecutan en serie y propagan la excepción, así que el orden y el aislamiento de fallos seguirían
  pendientes. No pasa el test de explicabilidad, porque no se puede decir en una oración qué desacopla
  que la interfaz no desacople ya.
- *Señal para reconsiderar:* que la integración con la API de Contabilidad requiera entrega **después
  del commit** o **asíncrona** (`@TransactionalEventListener(phase = AFTER_COMMIT)`, `@Async`), o que
  aparezcan interesados en otro módulo que no deban depender de `procesamiento.notificacion`. La primera
  señal coincide con la de ADR-001.

**2. Visitor o Abstract Factory para exportar el historial en varios formatos.**
- *Qué se consideró:* anticipar que Contabilidad pediría exportar en PDF y Excel, y construir desde ya
  una jerarquía de exportadores.
- *Por qué se descartó:* en esta especificación **no hay ningún requisito de exportación**, ni siquiera
  en CSV. Construir la abstracción sería resolver un problema inexistente (YAGNI), y la Rule of Three
  ni siquiera tiene su primer caso.
- *Señal para reconsiderar:* un primer formato pedido se implementa directamente; un segundo formato real
  justifica extraer una interfaz `ExportadorHistorial`; con un tercero (Rule of Three) se evalúa si hace
  falta algo más que Strategy.

#### Elementos propios surgidos en esta implementación

**3. Máquina de estados (patrón State) para las transiciones de `EstadoSolicitud`.**
- *Qué se observó:* `cambiarEstado` acepta cualquier transición. Una prueba temporal contra el servicio
  real, que no se versionó, lo mostró así (incluye el caso 5):

  ```
  SONDA monto -50000 -> estado PENDIENTE, aprobado -50000
  SONDA monto 0 -> estado PENDIENTE, aprobado 0.0
  Notificando a E-001: su solicitud paso de PENDIENTE a APROBADA
  Contabilidad: programar pago de -50000.00 para E-001
  Notificando a E-001: su solicitud paso de APROBADA a APROBADA
  Contabilidad: programar pago de -50000.00 para E-001
  Notificando a E-001: su solicitud paso de APROBADA a PENDIENTE
  ```

  `APROBADA -> APROBADA` programa el pago **dos veces**, y `APROBADA -> PENDIENTE` se permite.
- *Qué se consideró:* una clase por estado con sus transiciones permitidas (State) o, más simple, una
  tabla `EnumMap<EstadoSolicitud, Set<EstadoSolicitud>>`.
- *Por qué se descartó:* la especificación enumera los estados que disparan notificaciones, pero **no
  define qué transiciones son válidas** (¿se puede volver de REQUIERE_CORRECCION a PENDIENTE? ¿se puede
  revocar una aprobación?). Implementar State sería inventar reglas de negocio que Finanzas no ha dado
  (YAGNI). Además, los estados no tienen comportamiento propio, solo un nombre, que es justo lo que
  justificaría State. No pasa el test de explicabilidad: no se puede explicar en una oración qué problema
  resuelve sin inventar las reglas.
- *Señal para reconsiderar:* (a) Finanzas define el flujo de estados; empezar con la tabla de
  transiciones y llegar a State solo si los estados ganan comportamiento distinto. (b) La integración de
  la API de pagos de Contabilidad: con pagos reales, el doble `APROBADA` deja de ser un mensaje repetido
  y pasa a ser un doble pago. Esa integración debe llegar, como mínimo, con el rechazo de la transición a
  un mismo estado y con una clave de idempotencia del pago.

**4. Centralizar los topes que se solapan entre `ValidadorMontoMaximo` y las calculadoras.**
- *Qué se observó:* con los valores del enunciado, el validador rechaza antes de que las calculadoras
  puedan aplicar su tope, así que en el flujo real los topes de las calculadoras nunca actúan:

  | Categoría | Tope del validador (monto solicitado) | Tope de la calculadora | ¿Alcanzable en el flujo? |
  |---|---|---|---|
  | VIAJE | 150 000 en total | 150 000 x días | Solo con `diasViaje = 0` (aprueba 0); con 1 día o más, nunca |
  | REPRESENTACION | 2 000 000 | 2 000 000 mensual | No: son iguales |
  | CAPACITACION | 3 000 000 | 3 000 000 anual sobre el 80 % | No: el 80 % de 3 000 000 es 2 400 000 |

  Las pruebas unitarias de las calculadoras sí ejercitan sus topes (llamándolas directamente).
- *Qué se consideró:* una única fuente de topes por categoría (un enum `TopesCategoria` o
  `@ConfigurationProperties`) que usaran el validador y las calculadoras.
- *Por qué se descartó:* los topes están en **dos** sitios, y el enunciado los define como conceptos
  distintos (tope de la solicitud frente a tope diario, mensual o anual del reembolso). Unificarlos obliga
  a decidir una semántica que Finanzas no ha dado, y tal como están, hoy no cambiaría ningún resultado.
  Con dos usos, la Rule of Three no se cumple. No se modificaron.
- *Señal para reconsiderar:* el **tercer consumidor** ya está anunciado. El validador de "límite anual
  acumulado por empleado" de RF1 necesitará el tope anual de capacitación, y ahí se cumple la Rule of
  Three. También sería señal que Finanzas cambie un tope y haya que editarlo en dos sitios, con riesgo de
  que diverjan.

**5. Bean Validation (`@Positive`, `@NotNull`) para rechazar montos menores o iguales a 0.**
- *Qué se observó:* `ValidadorMontoMaximo` solo comprueba el límite superior. Un monto de -50 000 se
  guarda con un monto aprobado de -50 000, y un monto de 0 se acepta (ver la salida del punto 3).
- *Qué se consideró:* `spring-boot-starter-validation` con `@Positive` en `montoSolicitado` y `@Valid` en
  el controlador.
- *Por qué se descartó:* las anotaciones irían en `SolicitudReembolso` y meterían `jakarta.validation` en
  el dominio, rompiendo la decisión de 0 imports de `jakarta` (la otra salida exige DTOs, ver punto 6).
  Además, repartiría las reglas de negocio entre dos mecanismos (anotaciones y cadena), cuando RF1 ya
  ofrece el punto de extensión correcto. Un monto positivo es una regla de negocio, no de forma del JSON,
  y pertenece a la cadena: un `ValidadorMontoPositivo` es una clase nueva más una línea en
  `CadenaValidadores`, sin tocar los demás eslabones.
- *Estado del hueco:* **sigue abierto.** El enunciado fija exactamente tres verificaciones y no se agregó
  una cuarta sin acordarlo. Queda identificado como el primer uso real del punto de extensión de RF1.
- *Señal para reconsiderar Bean Validation:* la existencia de DTOs de entrada (punto 6) con varias reglas
  de formato en la frontera HTTP (longitudes, patrones de `empleadoId`, fechas no futuras). Ahí sí son
  restricciones de forma y las anotaciones viven fuera del dominio.

**6. DTOs de entrada y salida con mappers, separados del dominio.**
- *Qué se observó:* el controlador recibe y devuelve `SolicitudReembolso` directamente. El único problema
  real que eso causó fue la asignación masiva: un `id` en el cuerpo del POST habría sobrescrito otra
  solicitud. Se resolvió en el servicio ignorando `id`, `estado` y `montoAprobado` del cliente (tres
  líneas, desviación documentada en la Parte 1).
- *Qué se consideró:* `CrearSolicitudRequest` + `SolicitudResponse` + un mapper, y también una entidad
  JPA separada con su mapper (descartada en la Parte 1 a favor de `orm.xml`).
- *Por qué se descartó:* la API expone uno a uno los 9 campos, y el cliente necesita verlos todos. Dos
  DTOs y un mapper copiarían 9 campos idénticos para resolver un problema ya resuelto con tres líneas: no
  pasa el test de explicabilidad. La Regla 2 de ArchUnit ya garantiza que el adaptador REST solo depende
  de `aplicacion` y `dominio`.
- *Señal para reconsiderar:* que la entrada y la salida diverjan (por ejemplo, soportes como archivos y
  no como un `boolean`), que el dominio gane campos internos que no deban salir (notas de auditoría),
  versionado de la API, o un segundo cliente con otro contrato.
