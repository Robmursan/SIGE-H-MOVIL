# SIGE-H móvil · Contrato HTTP (M3: contrato de 49 operaciones)

**Contrato usado:** `artifacts/openapi/sigeh-v1.json` del repositorio **SIGE-H-WEB** (rama
`feat/codex-b1-iam-org`, publicada como PR #8), copiado y verificado en
[`docs/mobile/contrato/sigeh-v1.json`](contrato/sigeh-v1.json). Es el contrato canónico que exporta el backend
(`npm --prefix apps/api run openapi:check`); el handoff Android está en `docs/mobile/ANDROID_API_HANDOFF.md` de ese
repositorio.

| Dato | Valor |
|---|---|
| SHA-256 | `a6fbe4804d6ecc7eb88ea78254b4b80bd1f1ad0a4bde6eba70a08358a78dba86` (coincide con `sigeh-v1.manifest.json`) |
| Versión (`info.version`) | `1.0.0` · revisión `e2e-mvp-02.1` · 49 operaciones en 42 rutas |
| Backend de referencia | SIGE-H-WEB commit `9de9802` (23-nov-2026, «publish scoped SIGE-H API and Android contract») |
| Prefijo | `/api/v1` |
| Copia anterior | `ab9dd2a4…` (21 operaciones, SIGE-H-Fontend). De las 21 no cambió ninguna: se agregaron 28 y no se quitó ninguna |

**Qué cambió para el móvil.** De las operaciones que usa la app, solo `GET /auth/me/contextos` es nueva; login,
refresh, me, logout, sesiones, `/meta/version`, `/pases-lista`, `PUT …/asistencia`, `/programaciones` y
`/empleados` tienen exactamente el mismo esquema (comparados con todas sus referencias resueltas). Las otras 27
operaciones nuevas son de administración organizacional y gestión laboral, que corresponden a SIGE-H Web (§3).

## 1. Rutas integradas

Todas las protegidas llevan `Authorization: Bearer <accessToken>` (lo agrega `AuthInterceptor`; un 401 se renueva
una sola vez en `SesionAuthenticator`).

| Ruta | operationId | Permiso | Interruptor | Uso en la app | Clase responsable |
|---|---|---|---|---|---|
| `POST /auth/login` | iamLogin | pública | `sigeh.api.iam` | Inicio de sesión (9.1). 400/401 genérico, 429 con cuenta regresiva, 503 | `IamAuthRepository` |
| `POST /auth/refresh` | iamRefresh | pública | `sigeh.api.iam` | Renovación del access token (300 s), proactiva y ante un 401 | `TokenRefresher`, `SesionAuthenticator` |
| `GET /auth/me` | iamMe | sesión | `sigeh.api.iam` | Asignaciones y permisos de la sesión; respaldo del selector si contextos no está disponible | `IamAuthRepository`, `PoliticaAmbito` |
| `GET /auth/me/contextos` | iamOwnContexts | sesión (no pide `CATALOGOS_ADMINISTRAR`) | `sigeh.api.iam` | Selector (9.2): contextos propios vigentes con nombres, zona del hospital y vigencia | `RemotoContextosRepository`, `PoliticaAmbito.ambitosDeContextos` |
| `POST /auth/logout` | iamLogout | sesión | `sigeh.api.iam` | Cerrar sesión (la purga local ocurre aunque falle) | `IamAuthRepository` |
| `GET /auth/sessions` | iamSessions | sesión | `sigeh.api.iam` | Sesiones en Cuenta (9.9) | `RemotoSesionesRepository` |
| `DELETE /auth/sessions/{sessionId}` | iamRevokeSession | sesión | `sigeh.api.iam` | «Cerrar» una sesión; revocar la actual = cerrar sesión y purgar | `RemotoSesionesRepository` |
| `GET /meta/version` | getApiContractVersion | pública | `sigeh.api.iam` | Pie del login y de Cuenta; si no es `1.x`, «Actualiza la aplicación» | `RemoteServidorRepository` |
| `GET /pases-lista` | listScopedAttendanceRoster | `ASISTENCIA_CAPTURAR` o `ASISTENCIA_VALIDAR` | `sigeh.api.asistencia` | Pase de lista del día (9.3), `limit=100` y «Cargar más» | `RemotoPaseListaRepository` |
| `PUT /programaciones/{programacionId}/asistencia` | captureInitialAttendance | `ASISTENCIA_CAPTURAR` + `Idempotency-Key` | `sigeh.api.asistencia` | Primera captura (9.4–9.6) | `RemotoPaseListaRepository` |

## 2. Rutas opcionales (dependen de un permiso)

Los dos permisos están en el código del backend como **propuestos**: lo normal es que un jefe de servicio no los
tenga. Sin ellos la app oculta lo que dependa de esas rutas.

| Ruta | operationId | Permiso | Uso en la app | Clase responsable |
|---|---|---|---|---|
| `GET /programaciones` | listScopedWeeklyProgramming | `PROGRAMACION_CONSULTAR` | Pestaña Programación (9.8). Ventana de lunes 00:00 a lunes 00:00 en la zona del hospital, **máximo 168 h**; todas las páginas | `RemotoProgramacionRepository` |
| `GET /empleados` | listScopedStaff | `PERSONAL_CONSULTAR` | Solo para poner nombres en Programación. Sin permiso o si falla: «Trabajador ··· abcd» | `RemotoProgramacionRepository` |

## 3. Rutas del contrato que la app no usa

Administración de empleados, organización y gestión laboral **corresponden a SIGE-H Web**; el móvil no tiene
clientes Retrofit para ellas.

| Ruta | Motivo |
|---|---|
| `GET /hospitales`, `GET /hospitales/{hospitalId}/divisiones`, `GET /servicios` | Piden `CATALOGOS_ADMINISTRAR`, una facultad administrativa que un jefe de servicio no tiene |
| `POST /servicios`, `POST /divisiones`, `POST /subareas`, `GET`/`PATCH /divisiones/{divisionId}`, `GET`/`PATCH /servicios/{servicioId}`, `GET`/`PATCH /subareas/{subareaId}`, `GET /servicios/{servicioId}/subareas`, `GET /organizacion/arbol` | Administración organizacional (nuevas en e2e-mvp-02.1). Web |
| `GET /personal/elegibles`, `POST /contratos/{contratoId}/adscripciones`, `GET /contratos/{contratoId}/configuracion-laboral`, `POST /contratos/{contratoId}/patrones` | Administración de personal y contratos. Web |
| `GET /servicios/{servicioId}/distribucion`, `POST /servicios/{servicioId}/rotaciones` | Distribución operativa del servicio. Web |
| `GET`/`POST /horarios`, `GET`/`POST /patrones-laborales`, `POST /programaciones/simular`, `POST /programaciones/generar` | Calendario y publicación de programación (`CAL_*`, apagadas). Web |
| `POST /vacaciones`, `POST /incapacidades`, `GET /incidencias`, `GET /incidencias/{incidenciaId}/versiones` | Gestión laboral. Capturar asistencia **no** crea incidencias ni aprueba ausencias. Web |
| `GET /tableros/coordinacion`, `GET /contratos/{contratoId}/adscripciones` | Son de Coordinación web |
| `GET /empleados/{empleadoId}` | No hay pantalla que lo necesite |
| `GET /`, `GET /health`, `GET /health/live`, `GET /health/ready` | Son de operación, no de la app |

## 4. Cómo activar el modo real

En `local.properties` (no se versiona) o con `-P` en Gradle:

```properties
sigeh.api.baseUrl=http://10.0.2.2:3000/api/v1   # emulador; teléfono por USB: adb reverse tcp:3000 tcp:3000 y http://127.0.0.1:3000/api/v1
sigeh.api.iam=real                              # IAM V2: login, refresh, me, logout, sesiones y /meta/version
sigeh.api.asistencia=real                       # /pases-lista, PUT …/asistencia, /programaciones y /empleados
sigeh.features.futuras=false                    # true muestra las pantallas de M0 sin backend
```

| Claves | Resultado |
|---|---|
| Ninguna | Todo Fake (modo demostración) |
| `iam=real` | IAM real con pase y programación Fake (sirve si el backend tiene `ASISTENCIA_ENABLED=false`) |
| `iam=real` y `asistencia=real` | Todo real |
| `asistencia=real` sin `iam=real` | Se ignora: sin tokens del servidor todo daría 401 (`NetworkConfig.asistenciaReal`) |

Banderas del backend (todas `false` por defecto; apagadas, la ruta responde 503):

| Bandera | Rutas | Mensaje con la bandera apagada |
|---|---|---|
| `IAM_V2_ENABLED` | `/auth/*` (incluida `/auth/me/contextos`) y organización | «IAM no disponible» |
| `ASISTENCIA_ENABLED` | `/pases-lista` y `PUT …/asistencia` | «Asistencia no habilitada» |
| `L3A_READS_ENABLED` | `/empleados`, `/programaciones`, tablero | «Lecturas aún no habilitadas» |

En release solo se permite HTTPS (`res/xml/network_security_config.xml`); en debug también HTTP hacia
`10.0.2.2`, `localhost`, `127.0.0.1` y la API de integración del equipo en la red local, `10.255.60.40`
(`sigeh.api.baseUrl=http://10.255.60.40:3000/api/v1/`). La URL va en `local.properties`, nunca en el código.

## 5. Reglas del contrato que la app respeta

**IAM V2 (desde M1).**
- **Access token de 300 s.** Se renueva 30 s antes de vencer o ante un 401, una sola vez por token, con las
  renovaciones serializadas.
- **Refresh de un solo uso.** Rota en cada renovación y un replay revoca toda la familia: nunca se reintenta un
  refresh ya usado.
- **400/401 en el refresh.** La sesión se marca vencida y la app vuelve al login con «Tu sesión terminó».
- **429/503 o falta de red.** Es transitorio y la sesión se conserva.
- **Tokens.** Solo cifrados (AES-GCM, Android Keystore). El registro HTTP de debug no escribe cuerpos de
  `/auth/...` y oculta `Authorization`, cookies e `Idempotency-Key`.

**Contextos (M3).**
- **Fuente del selector.** `GET /auth/me/contextos` sin parámetros (cualquier query da 400). Los contextos del mismo
  ámbito se unen como las asignaciones de `/auth/me`: permisos sumados, TITULAR si alguno lo es, y entra un ámbito
  solo con `ASISTENCIA_CAPTURAR` o `ASISTENCIA_VALIDAR`. `tipo` es informativo: nunca otorga facultades.
- **Sin descendientes.** Un contexto de hospital o división sin `servicioId` se muestra deshabilitado; la app no
  enumera servicios hijos.
- **Respaldo.** La ruta está marcada `prepared-own-contexts-awaiting-sql-validation`. Con 400/403/404/503, un
  cuerpo ilegible o sin red, el selector usa las asignaciones de `/auth/me` con «Servicio ··· c91e». Un 401 (tras
  la única renovación) marca la sesión vencida.
- **Zona y vigencia.** La `zonaHoraria` del contexto elegido es la del primer «hoy» del pase; `vigenteHasta` se
  muestra como «Vigente hasta 1 dic 00:00» en esa zona. Una lista vacía es válida: no hay contextos vigentes.
- **Privacidad.** Los contextos viven solo en memoria y no se registran.

**Asistencia (M2).**
- **Solo en línea.** La captura no pasa por `ColaComandos` ni por WorkManager; sin red, los botones se
  desactivan.
- **Primera captura únicamente.** El cuerpo es siempre `{"estado": …, "expectedRevision": "0"}`.
- **`Idempotency-Key`.** Es un UUID v4 que se genera **una vez** al confirmar, ligado a `(programacionId, estado)`.
  Se guarda en `SavedStateHandle` hasta una respuesta terminal (200, 400, 403, 404 o 409). Se reutiliza tras un
  timeout, un error de red, un 503 o un 401, y cambia si cambia el estado.
- **Capturabilidad.** La decide el servidor (`capturable` y `motivoNoCapturable`); la app no calcula ventanas de
  horario.
- **Identificadores grandes.** `eventoId` y `revision` se manejan como texto (BIGINT decimal).
- **Fecha local del hospital.** La primera consulta usa la zona del contexto elegido (o `America/Mexico_City` si se
  eligió con el respaldo); después se adopta `zonaHoraria` y, si difiere, se recalcula «hoy» y se repite la consulta.
- **Parámetros.** No se envían vacíos ni nulos: el backend responde 400.

## 6. Errores y cómo se muestran

El backend responde con el formato por defecto de NestJS (`ReadHttpErrorDto {statusCode, message, error}`). En los
409 de asistencia, el `message` es el código de negocio. `ApiErrorParser` reconoce los textos exactos
(`CausaErrorApi`) y lee `Retry-After` (60 s si no viene). `ResultadoConsulta`, `ResultadoCaptura` y
`ResultadoProgramacion` traducen cada caso:

| HTTP | `message` real | Qué hace la app |
|---|---|---|
| 400 | `Entrada inválida`, `Fecha inválida`, `Límite inválido`, `Identificador o clave inválidos` | Registro en debug y mensaje genérico |
| 401 | — | Una renovación; si vuelve 401, «Tu sesión terminó» y login |
| 403 | `Acceso denegado` | «Sin permiso en este servicio» con «Elegir servicio» |
| 404 | `Contexto no disponible` · `Programación no disponible` | «Servicio no disponible» · «La jornada ya no está disponible» y recarga |
| 409 | `YA_REGISTRADA` | «Esta jornada ya tenía registro», recarga y «Ver registro» |
| 409 | `FUERA_VENTANA` | «Fuera del horario de captura» |
| 409 | `PROGRAMACION_NO_CAPTURABLE`, `CONTRATO_NO_VIGENTE`, `CIERRE_EXISTENTE` | «La jornada no admite captura» con el motivo |
| 409 | `IDEMPOTENCY_CONFLICT` | Error interno: se descarta la clave y se recarga |
| 429 | `Demasiados intentos` | Cuenta regresiva con `Retry-After` |
| 503 | `Asistencia no habilitada` · `Lecturas aún no habilitadas` | «… no disponible en este servidor», sin reintento automático |
| 503 | `Asistencia no disponible; reintente con la misma clave` · `IAM no disponible` | «No pudimos confirmar el registro» con «Reintentar» (misma clave) |
| 400 | `Contextos propios no admite filtros` | Error de programación (la app no envía parámetros); el selector usa el respaldo |
| 503 | `Contextos no disponibles` | El selector usa las asignaciones de `/auth/me` |

## 7. Brechas del backend que afectan al móvil

| # | Brecha | Efecto en la app | Propuesta |
|---|---|---|---|
| 1 | ~~No hay nombres de hospital, división ni servicio para el usuario~~ | **Resuelta en 9de9802** con `GET /auth/me/contextos`. «Servicio ··· c91e» queda solo como respaldo | — |
| 2 | Los mandatos de hospital o división no enumeran servicios sin `CATALOGOS_ADMINISTRAR` | La tarjeta sale deshabilitada: «Este ámbito no indica un servicio. Pide a Coordinación una asignación por servicio». `/auth/me/contextos` tampoco enumera hijos (por diseño) | Asignar al capturista por servicio |
| 3 | `/programaciones` no trae el nombre del trabajador | Sin `PERSONAL_CONSULTAR`: «Trabajador ··· abcd» | Nombre visible en `ScheduleReadDto` |
| 4 | No hay catálogo de horarios ni de turnos | Los chips de turno se derivan de los items cargados | Catálogo de horarios del servicio |
| 5 | No hay conteos agregados del pase | Los contadores dicen «en esta página» | Totales del servicio y fecha en `PaseListaPageDto` |
| 6 | ~~El OpenAPI del backend estaba desactualizado~~ | **Resuelta**: el canónico es `artifacts/openapi/sigeh-v1.json` con manifiesto y SHA | — |
| 7 | ~~`app.listen(port)` escuchaba en todas las interfaces~~ | **Resuelta**: `HOST=127.0.0.1` por defecto (`main.ts`, `env.validation.ts`) | — |
| 8 | Contextos, pase y captura dicen `local-mock-tested-disabled-awaiting-ephemeral-validation` y `x-sigeh-sql-privileges: not-authorized-awaiting-ephemeral-package` | Nada de esto se ha probado contra PostgreSQL real con Android | Correr `docs/mobile/M3-contrato-49-y-QA.md` en QA efímero |
| 9 | No hay operación HTTP para crear programaciones (`POST /programaciones/generar` sigue apagado) | La prueba de captura depende de que QA tenga una programación sintética legítima en ventana | La prepara el equipo backend; no se insertan filas por SQL |

Siguen vigentes desde M1:
- **Sin filtro global de errores.** No llegan `code`, `traceId` ni `retryable`.
- **`ProfileDto` sin nombre de la persona.** Cuenta muestra el identificador.

## 8. Futuro (sin API en el backend; siguen Fake detrás de `FUNCIONES_FUTURAS`)

| Uso en la app | Método y ruta propuesta | CU |
|---|---|---|
| Alcances vigentes con vigencia | `GET /me/alcances` | CU-IAM-05 |
| Agenda propia (copia offline) | `GET /me/agenda` | CU-CAL-19 |
| Corregir una captura, validar o liberar el pase | (sin propuesta para el móvil) | CU-ASI-03 y siguientes |
| Evento parcial | `POST /asistencias/:id/eventos` | CU-ASI-03 |
| Borrador y envío del pase | `PUT /pases-lista/:id/borrador` · `POST /pases-lista/:id/envio` | CU-ASI-05 · 06 |
| Sincronizar lote offline | `POST /sincronizacion/asistencias` | CU-ASI-13 |
| Estado del lote | `GET /sincronizacion/lotes/:id` | CU-OPS-04 |
| Resolver conflicto | `POST /asistencias/:id/conflictos/resolucion` | CU-ASI-14 |
| Incidencias | `GET /tipos-incidencia` · `POST /incidencias` · `POST /incidencias/parciales` | CU-INC-01 · 03 · 07 |
| Convenios | `GET /me/convenios-pendientes` · `POST /convenios/:id/aceptacion` · `/rechazo` | CU-MOV-22 · 02 · 03 |
| Pases de salida | `GET /pases-salida` · `POST /pases-salida/:id/salida` · `/regreso` · `/cierre` | CU-MOV-14 a 17 |
| Cobertura y recepción de eventual | `GET /necesidades-cobertura` · `POST /asignaciones/:id/incorporaciones` · `/rechazos` | CU-COB-04 · 10 · 11 |
| Lectura de credencial QR | (sin propuesta) | — |

La sincronización persistente llega en el bloque **B7** del backend y requiere antes **V2-INBOX** (idempotencia
persistida). Hasta entonces `ColaComandos`, `SyncProcessor`, `SyncWorker`, `ConflictResolver` y sus DAO se
conservan sin cambios y solo se programan con `FUNCIONES_FUTURAS`.

## 9. Decisiones abiertas

- **Ámbitos de hospital o división.** El backend podría autorizar servicios por jerarquía, pero la app no puede
  enumerarlos (brecha 2). Hoy esas tarjetas van deshabilitadas.
- **Permisos propuestos.** Falta confirmar si `PROGRAMACION_CONSULTAR` y `PERSONAL_CONSULTAR` se asignarán a
  jefes de servicio; sin ellos la pestaña Programación no aparece.
- **«Se habilita a las HH:mm».** Se calcula con el reloj del teléfono solo para el texto; quien decide si se
  puede capturar sigue siendo el servidor.
- **Herencia de M0.** Siguen pendientes registrar la salida efectiva (CU-MOV-14) y «Incapacidad» en la cola
  local (solo el tipo, sin datos clínicos).
