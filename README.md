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
a la clase y un renombre de campo debe replicarse en `orm.xml` (si se olvida, el contexto no arranca, así
que el error aparece en la primera prueba, no en producción).

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
