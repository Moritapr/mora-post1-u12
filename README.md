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
| RF3 | Observer | `notificacion/ObservadorCambioEstado` (observador), `NotificadorEmpleado`, `NotificadorContabilidad`, `NotificadorAuditoria` | Nueva clase `@Component` que implementa `ObservadorCambioEstado` |

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

### Pruebas

`mvn clean test`:

```
[INFO] Tests run: 55, Failures: 0, Errors: 0, Skipped: 0
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
| `FlujoSolicitudesIntegracionTest` | Integración `@SpringBootTest` + MockMvc + H2 | 9 | POST de las 3 categorías con su monto aprobado; 400 sin soportes, por duplicado y por categoría inexistente; PUT APROBADA y RECHAZADA con los 3 observadores como espías (`@MockitoSpyBean`) y Contabilidad actuando solo en APROBADA; PUT 404 |
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
APROBADA):

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
