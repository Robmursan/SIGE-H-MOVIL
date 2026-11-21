# SIGE-H móvil · Contrato HTTP (M2: pase de lista en línea)

**Contrato usado:** `contracts/sigeh-v1.json` del repositorio **SIGE-H-Fontend** (rama `main`), copiado
y verificado en [`docs/mobile/contrato/sigeh-v1.json`](contrato/sigeh-v1.json).

| Dato | Valor |
|---|---|
| SHA-256 | `ab9dd2a4b17806784c029ca16b79a4837542f83ac3ea3a0246cb6b50a18847f0` |
| Versión (`info.version`) | `1.0.0` · 21 operaciones |
| Backend de referencia | SIGE-H-WEB `main`, commit `2d1ec62` (19-nov-2026, PR #7 «IAM V2, lecturas y asistencia auditable») |
| Prefijo | `/api/v1` |

> `docs/api/openapi.json` de SIGE-H-WEB **sigue desactualizado** (5 operaciones). No se usó.

## 1. Rutas integradas

Todas las protegidas llevan `Authorization: Bearer <accessToken>` (lo agrega `AuthInterceptor`; un 401 se renueva
una sola vez en `SesionAuthenticator`).

| Ruta | operationId | Permiso | Interruptor | Uso en la app | Clase responsable |
|---|---|---|---|---|---|
| `POST /auth/login` | iamLogin | pública | `sigeh.api.iam` | Inicio de sesión (9.1). 400/401 genérico, 429 con cuenta regresiva, 503 | `IamAuthRepository` |
| `POST /auth/refresh` | iamRefresh | pública | `sigeh.api.iam` | Renovación del access token (300 s), proactiva y ante un 401 | `TokenRefresher`, `SesionAuthenticator` |
| `GET /auth/me` | iamMe | sesión | `sigeh.api.iam` | Asignaciones y permisos → ámbitos del selector (9.2) | `IamAuthRepository`, `PoliticaAmbito` |
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

| Ruta | Motivo |
|---|---|
| `GET /hospitales`, `GET /hospitales/{hospitalId}/divisiones`, `GET /servicios` | Piden `CATALOGOS_ADMINISTRAR`, una facultad administrativa que un jefe de servicio no tiene |
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
| `IAM_V2_ENABLED` | `/auth/*` y organización | «IAM no disponible» |
| `ASISTENCIA_ENABLED` | `/pases-lista` y `PUT …/asistencia` | «Asistencia no habilitada» |
| `L3A_READS_ENABLED` | `/empleados`, `/programaciones`, tablero | «Lecturas aún no habilitadas» |

En release solo se permite HTTPS (`res/xml/network_security_config.xml`); en debug también HTTP hacia
`10.0.2.2`, `localhost` y `127.0.0.1`.

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
- **Fecha local del hospital.** La primera consulta usa `America/Mexico_City`; después se adopta `zonaHoraria` y,
  si difiere, se recalcula «hoy» y se repite la consulta.
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

## 7. Brechas del backend que afectan al móvil

| # | Brecha | Efecto en la app | Propuesta |
|---|---|---|---|
| 1 | No hay nombres de hospital, división ni servicio para el usuario | El selector muestra «Servicio ··· c91e» (`EtiquetasPorIdentificador`) | `GET /auth/me/contextos` → `[{ hospitalId, hospitalNombre, divisionId?, divisionNombre?, servicioId, servicioNombre, kind, permissions }]`, filtrado por las asignaciones propias. Solo cambiaría `EtiquetasContexto` |
| 2 | Los mandatos de hospital o división no enumeran servicios sin `CATALOGOS_ADMINISTRAR` | La tarjeta sale deshabilitada: «Este ámbito no indica un servicio. Pide a Coordinación una asignación por servicio» | Incluir los servicios cubiertos en la propuesta 1 |
| 3 | `/programaciones` no trae el nombre del trabajador | Sin `PERSONAL_CONSULTAR`: «Trabajador ··· abcd» | Nombre visible en `ScheduleReadDto` |
| 4 | No hay catálogo de horarios ni de turnos | Los chips de turno se derivan de los items cargados | Catálogo de horarios del servicio |
| 5 | No hay conteos agregados del pase | Los contadores dicen «en esta página» | Totales del servicio y fecha en `PaseListaPageDto` |
| 6 | `docs/api/openapi.json` del backend está desactualizado | Se usó `sigeh-v1` de SIGE-H-Fontend | Regenerar el OpenAPI del backend desde Swagger |
| 7 | `app.listen(port)` escucha en todas las interfaces de red (`apps/api/src/main.ts`) | En pruebas locales el API queda expuesto en la red | `HOST=127.0.0.1` por defecto en desarrollo |

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
