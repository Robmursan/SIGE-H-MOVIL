# PROMPT M2 — SIGE-H Móvil: pase de lista en línea contra el contrato real

> **Para el agente:** este documento es tu encargo completo. Léelo entero antes de tocar código.
> Trabaja en español (código, comentarios, commits y documentación siguen la convención que ya
> tiene el proyecto). Si algo de lo que encuentres en el código contradice este documento,
> **detente y repórtalo** antes de cambiarlo.

---

## 1. Rol y contexto

Eres el desarrollador Android de **SIGE-H** (Sistema Integral de Gestión de Enfermería
Hospitalaria). Trabajas sobre el proyecto existente:

- **Ruta:** `C:\Users\kaher\AndroidStudioProjects\SIGEH` (Windows, usa PowerShell y `.\gradlew.bat`)
- **Paquete:** `mx.sigeh.app` · `minSdk 24` · `compileSdk/targetSdk 37`
- **Stack ya instalado:** Kotlin 2.4, Jetpack Compose (Material 3), Navigation Compose con
  destinos tipados, Hilt, Room, WorkManager, DataStore, Retrofit 3 + OkHttp 5,
  kotlinx.serialization, MockWebServer para pruebas.

El proyecto ya pasó por dos hitos:

| Hito | Qué dejó |
|---|---|
| **M0** | App completa en modo **Fake**: login, selector de ámbito, pase de lista con resumen, agenda, incidencias, movimientos/pase de salida, recepción de eventual, sincronización y conflictos; cola offline en Room (`ColaComandos`, `SyncProcessor`, `SyncWorker`); design system propio en `core/designsystem`. |
| **M1** | IAM V2 real detrás de interruptor: `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `GET /auth/me`, `GET /meta/version`. Tokens cifrados con Keystore (`EncryptedSessionStore`), renovación serializada (`RenovacionSesion`), `ApiErrorParser`, `network_security_config` de debug. Documentado en `docs/mobile/contrato-pendiente.md`. |

**Este encargo es M2:** integrar el pase de lista real (consulta y primera captura en línea),
rediseñar las pantallas según los nuevos mockups y ocultar lo que no tiene backend.

---

## 2. Fuentes de verdad (en este orden)

1. **Contrato OpenAPI de 21 operaciones:** `contracts/sigeh-v1.json` del repositorio
   `Robmursan/SIGE-H-Fontend`, rama `main`.
   SHA-256 esperado: `ab9dd2a4b17806784c029ca16b79a4837542f83ac3ea3a0246cb6b50a18847f0`.
2. **Código del backend:** repositorio `Robmursan/SIGE-H-WEB`, rama `main`, commit `2d1ec62`
   (19-nov-2026, PR #7 «IAM V2, lecturas y asistencia auditable»). Módulos clave:
   `apps/api/src/modules/asistencia/*`, `iam/presentation/*`, `lecturas/*`, `organizacion/*`.
3. **Especificación del MVP de asistencia:** `docs/casos-uso/CU-ASI-01-02-MVP-online.md` del
   mismo repositorio.
4. **Mockups M2** (descritos completos en la sección 9; el canvas de diseño es solo referencia
   visual para las personas del equipo).

> ⚠️ `docs/api/openapi.json` de SIGE-H-WEB **sigue desactualizado** (5 operaciones). No lo uses.

Obtén el contrato y verifícalo antes de escribir DTOs:

```powershell
New-Item -ItemType Directory -Force docs\mobile\contrato | Out-Null
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/Robmursan/SIGE-H-Fontend/main/contracts/sigeh-v1.json" -OutFile docs\mobile\contrato\sigeh-v1.json
(Get-FileHash docs\mobile\contrato\sigeh-v1.json -Algorithm SHA256).Hash.ToLower()
```

Si el hash no coincide, **no continúes**: reporta el hash obtenido y pregunta.
Si existe `C:\Users\kaher\SIGE-H-WEB`, haz `git pull` en `main` y lee el backend desde ahí.

---

## 3. Qué cambió en el backend desde M1

M1 se hizo contra el commit `2604aa6` (15-nov), cuando asistencia, programación y personal
eran módulos vacíos. Hoy existen **21 rutas** reales:

- **Nuevas y que M2 debe usar:** `GET /pases-lista`, `PUT /programaciones/{programacionId}/asistencia`,
  `GET /auth/sessions`, `DELETE /auth/sessions/{sessionId}`.
- **Nuevas y opcionales (dependen de permisos):** `GET /programaciones`, `GET /empleados`,
  `GET /empleados/{empleadoId}`.
- **Nuevas que la app NO debe usar:** `GET /hospitales`, `GET /hospitales/{id}/divisiones` y
  `GET /servicios` piden `CATALOGOS_ADMINISTRAR`, una facultad administrativa que un jefe de
  servicio no tiene. `GET /tableros/coordinacion` y `GET /contratos/{id}/adscripciones` son de
  Coordinación web.
- **Rutas de la tabla «Pendiente» de `contrato-pendiente.md` que siguen sin existir:** borrador
  y envío del pase, eventos parciales, sincronización de lotes, conflictos, incidencias,
  convenios, pases de salida y cobertura. **No las inventes.**

Banderas del backend (todas `false` por defecto; si están apagadas, la ruta responde 503):
`IAM_V2_ENABLED` (IAM y organización), `L3A_READS_ENABLED` (empleados, programaciones y
tablero), `ASISTENCIA_ENABLED` (pases-lista y captura).

---

## 4. Objetivo de M2 y fuera de alcance

**Objetivo:** un jefe de servicio inicia sesión, elige un servicio autorizado, ve el pase de
lista del día y registra **una sola vez** Presente, Ausente o Parcial por jornada, con
confirmación previa y acuse del servidor. Todo **en línea**, contra el contrato real, y con un
modo Fake equivalente para la demostración.

**Fuera de alcance de M2** (no implementar, no simular contra el backend real):

- Captura sin conexión, colas de lotes y resolución de conflictos (bloque B7 del backend, aún
  sin aprobar).
- Corregir una captura, validar o liberar el pase, «enviar» el pase completo.
- Incidencias, movimientos, pases de salida, recepción de eventuales y cobertura.
- Lectura de credenciales por QR.
- Cambios en los repositorios SIGE-H-WEB o SIGE-H-Fontend.

---

## 5. Reglas de negocio obligatorias

Vienen de `CU-ASI-01-02-MVP-online.md` y del código real. No tienen excepciones.

1. **Solo en línea.** Sin conexión, los botones de captura se desactivan y se muestra un aviso.
   La captura de asistencia **no** pasa por `ColaComandos` ni por WorkManager.
2. **Primera captura únicamente.** El cuerpo siempre lleva `"expectedRevision": "0"`. No hay
   corrección desde la app.
3. **Nada se marca por defecto.** Cada jornada se captura con una acción explícita del usuario.
   Prohibido el patrón «todos presentes y desmarcar».
4. **El servidor decide si se puede capturar.** Usa `capturable` y `motivoNoCapturable` del
   item. No calcules ventanas de horario en el teléfono y no uses su reloj para decidir. El
   `PUT` es la autoridad final.
5. **Idempotencia.** Al pulsar «Confirmar» se genera **un** `UUID v4` (`UUID.randomUUID()`) que
   se envía en el header `Idempotency-Key`. Esa clave:
   - queda ligada a `(programacionId, estado)` y se guarda en `SavedStateHandle` hasta recibir
     una respuesta terminal (200, 400, 403, 404 o 409);
   - **se reutiliza** si hay timeout, error de red, 503 o un 401 seguido de refresh exitoso;
   - se descarta si el usuario cambia el estado o cancela; una intención nueva lleva clave nueva.
6. **Doble toque:** mientras hay un envío en curso para una jornada, sus botones se desactivan.
   Dos toques nunca producen dos peticiones con claves distintas.
7. **Confirmación solo tras el 200.** Mientras tanto la fila dice «Enviando…». Después del 200
   se muestra el acuse y se **vuelve a pedir** `GET /pases-lista`. No hay estados optimistas
   definitivos.
8. **Ausente no es falta.** Es una observación operativa pendiente de validación administrativa
   y no genera incidencia. Los textos de la app lo dicen así.
9. **Los contadores son «en esta página».** No los presentes como totales del servicio.
10. **Identificadores grandes como texto.** `eventoId` y `revision` son `String` (BIGINT
    decimal). Nunca `Long` ni `Int`.
11. **Autor de la captura:** si `ultimaCaptura.actorId == ProfileDto.usuarioId` se muestra
    «Tú»; si no, «Otro usuario». Nunca muestres UUID de personas.
12. **Zona horaria:** la `fecha` del query es la fecha local del hospital. Para la primera
    consulta usa `America/Mexico_City` (constante de configuración) y después adopta
    `zonaHoraria` de la respuesta. Si difiere, recalcula «hoy» y repite la consulta. Las horas se
    muestran en esa zona, no en la del teléfono.
13. **Privacidad:** no registres en Logcat tokens, cuerpos de `/auth/*`, `Idempotency-Key` junto a
    nombres, ni respuestas de `/pases-lista` en release. El registro de debug que ya existe debe
    ocultar también `Idempotency-Key`.

---

## 6. Contrato a integrar

Prefijo `/api/v1`. Todas las rutas protegidas llevan `Authorization: Bearer <accessToken>`.
Copia los DTO **tal cual** del contrato; los de abajo son el resumen verificado.

### 6.1 `GET /pases-lista` · `listScopedAttendanceRoster`

Permiso del servidor: `ASISTENCIA_CAPTURAR` **o** `ASISTENCIA_VALIDAR` en el ámbito.

Query: `hospitalId` (uuid, obligatorio), `servicioId` (uuid, obligatorio), `fecha`
(`YYYY-MM-DD`, obligatorio), `horarioId` (uuid, opcional), `cursor` (uuid, opcional),
`limit` (1–100, por defecto 50). **No envíes parámetros vacíos, nulos ni repetidos**: el backend
responde 400.

```kotlin
@Serializable data class PaseListaPageDto(
    val hospitalId: String, val servicioId: String, val fecha: String, val zonaHoraria: String,
    val items: List<PaseListaItemDto>, val nextCursor: String?, val limit: Int, val order: String,
)
@Serializable data class PaseListaItemDto(
    val programacionId: String,
    val empleado: AttendanceEmployeeDto,          // empleadoId, nombres, primerApellido
    val horarioId: String?, val turnoCodigo: String?,
    val inicioProgramado: String, val finProgramado: String,   // date-time con offset
    val estadoProgramacion: EstadoProgramacion,   // PROGRAMADA | CANCELADA | SUSTITUIDA | CERRADA
    val capturada: Boolean, val asistenciaId: String?,
    val estadoOperativo: EstadoOperativo,         // PENDIENTE | PRESENTE | AUSENTE | PARCIAL | NO_EXIGIBLE
    val validacionAdministrativa: ValidacionAdministrativa?,   // PENDIENTE | VALIDADA | OBSERVADA
    val revision: String,                         // "0" sin eventos
    val ultimaCaptura: LastCaptureDto?,           // eventoId: String, actorId, registradoEn
    val capturable: Boolean,
    val motivoNoCapturable: MotivoNoCapturable?,  // YA_REGISTRADA | FUERA_VENTANA | PROGRAMACION_NO_CAPTURABLE
                                                  // | CONTRATO_NO_VIGENTE | CIERRE_EXISTENTE | SIN_FACULTAD_CAPTURA
)
```

Pide con `limit=100`. Si `nextCursor != null`, muestra «Cargar más» y conserva los mismos filtros.
Una lista vacía es `200` con `items=[]`.

### 6.2 `PUT /programaciones/{programacionId}/asistencia` · `captureInitialAttendance`

Permiso: `ASISTENCIA_CAPTURAR`. Header obligatorio `Idempotency-Key` (UUID v4).

```json
{ "estado": "PRESENTE", "expectedRevision": "0" }
```

Respuesta `200` → `CaptureReceiptDto { programacionId, asistenciaId, eventoId: String,
estadoOperativo, validacionAdministrativa: "PENDIENTE", actorId, registradoEn, revision: String }`.
Un reintento con la misma clave devuelve **el mismo** acuse.

### 6.3 Sesiones · `iamSessions` / `iamRevokeSession`

- `GET /auth/sessions` → `List<SessionDto { sessionId, createdAt, expiresAt, idleExpiresAt, revokedAt?, current }>`
  (últimas 50, sin tokens).
- `DELETE /auth/sessions/{sessionId}` → `204`. Sesión ajena → `404`. Si revocas la `current`,
  trátalo como logout y purga todo.

### 6.4 Opcionales (solo si el usuario tiene el permiso en el ámbito elegido)

- `GET /programaciones?hospitalId&servicioId&desde&hasta[&cursor&limit]` · permiso
  `PROGRAMACION_CONSULTAR`. Ventana **máxima de 168 h** (`desde`/`hasta` date-time con offset).
  `ScheduleReadDto` trae `empleadoId` pero **no el nombre**.
- `GET /empleados?hospitalId&servicioId&fecha` · permiso `PERSONAL_CONSULTAR`. Úsalo solo para
  poner nombres en la pantalla de Programación.

Estas dos facultades están en el código como **propuestas y no asignadas**: lo normal es que el
jefe de servicio reciba 403. La app debe ocultar la pestaña cuando falte el permiso en `/auth/me`.

### 6.5 Permisos que vienen en `/auth/me`

`ProfileDto { usuarioId, sessionId, provider: "local", assignments: [{ kind: TITULAR | ENCARGADO | null,
scope: { hospitalId, divisionId?, servicioId? }, permissions: [String] }] }`

Códigos conocidos: `ASISTENCIA_CAPTURAR`, `ASISTENCIA_VALIDAR`, `PERSONAL_CONSULTAR`,
`PROGRAMACION_CONSULTAR`, `TABLERO_CONSULTAR`, `CATALOGOS_ADMINISTRAR`. Actualiza
`PoliticaAmbito` para que en modo real las facultades salgan de estos códigos y no del nombre
del rol. `ProfileDto` **no trae vigencias ni nombres**.

### 6.6 Errores y cómo se muestran

Cuerpo: `ReadHttpErrorDto { statusCode, message: String | List<String>, error }`. El 429 de IAM
es `{ statusCode: 429, message }` y puede traer `Retry-After`. Extiende `ApiErrorParser` para
estos casos:

| HTTP | `message` real del backend | Qué hace la app |
|---|---|---|
| 400 | `Entrada inválida`, `Fecha inválida`, `Límite inválido`, `Identificador o clave inválidos` | Error de programación: registro en debug, mensaje genérico al usuario. |
| 401 | — | Una renovación con `RenovacionSesion`; si vuelve 401, pantalla «Tu sesión terminó». |
| 403 | `Acceso denegado` | Estado «Sin permiso en este servicio» con acción «Elegir servicio». |
| 404 | `Contexto no disponible`, `Programación no disponible` | Refrescar la lista y avisar que la jornada ya no está disponible. |
| 409 | `YA_REGISTRADA` | «Otra persona ya registró esta jornada» y recargar la lista. |
| 409 | `FUERA_VENTANA` | «Fuera del horario de captura». |
| 409 | `PROGRAMACION_NO_CAPTURABLE`, `CONTRATO_NO_VIGENTE`, `CIERRE_EXISTENTE` | «La jornada no admite captura» con el motivo en texto. |
| 409 | `IDEMPOTENCY_CONFLICT` | Error interno (clave reutilizada con otro estado): descartar la clave y recargar. |
| 429 | — | Cuenta regresiva con `Retry-After` (60 s si no viene). |
| 503 | `Asistencia no habilitada` | Estado «Pase de lista no disponible en este servidor», sin reintento automático. |
| 503 | `Asistencia no disponible; reintente con la misma clave` | «No pudimos confirmar el registro» con botón «Reintentar» que usa la **misma** clave. |

Textos para `motivoNoCapturable` en la lista:

| Código | Texto |
|---|---|
| `YA_REGISTRADA` | (no se muestra; la fila enseña el estado capturado) |
| `FUERA_VENTANA` | «Fuera del horario de captura» (si aún no inicia: «Se habilita a las HH:mm») |
| `PROGRAMACION_NO_CAPTURABLE` | «No capturable: programación {cancelada/sustituida/cerrada}» |
| `CONTRATO_NO_VIGENTE` | «No capturable: contrato no vigente» |
| `CIERRE_EXISTENTE` | «No capturable: el balance del periodo ya está cerrado» |
| `SIN_FACULTAD_CAPTURA` | «Solo consulta: no tienes facultad de captura» |

---

## 7. Rediseño de arquitectura y configuración

### 7.1 Interruptores

Conserva `sigeh.api.baseUrl` y `sigeh.api.iam` y agrega dos claves en `local.properties` /
`-P` (mismo patrón que `app/build.gradle.kts`):

| Clave | Valores | Efecto |
|---|---|---|
| `sigeh.api.asistencia` | `real` \| `fake` (por defecto `fake`) | `BuildConfig.ASISTENCIA_REAL`: pases-lista y captura contra el backend. |
| `sigeh.features.futuras` | `true` \| `false` (por defecto `false`) | `BuildConfig.FUNCIONES_FUTURAS`: muestra u oculta incidencias, movimientos, recepción de eventual, resumen/envío del pase, sincronización y conflictos. |

Así se puede probar con IAM real y asistencia Fake si el backend tiene `ASISTENCIA_ENABLED=false`.

### 7.2 Capa de datos

- Crea `core/network/asistencia/AsistenciaApi.kt` y `AsistenciaDtos.kt`, junto a
  `core/network/iam/`. Agrega `sesiones()` y `revocarSesion()` a `IamApi`.
- Rehaz el contrato de `PaseListaRepository` con el modelo del servidor:
  `consultar(hospitalId, servicioId, fecha, horarioId?, cursor?)` y
  `capturar(programacionId, estado, idempotencyKey): ResultadoCaptura`.
  Implementaciones: `RemotoPaseListaRepository` (en `core/data/remote/RemotoRepositorios.kt` o un
  archivo propio) y `FakePaseListaRepository`.
- `ResultadoCaptura` es un `sealed interface`: `Confirmada(acuse)`, `Rechazada(motivo)`,
  `SinPermiso`, `NoEncontrada`, `Incierta` (timeout, red o 503 transitorio, se reintenta con la
  misma clave), `NoHabilitada`, `SesionTerminada`.
- `DataModule` elige Remoto o Fake con `BuildConfig.ASISTENCIA_REAL`.
- Agrega en `core/data` un `SesionesRepository` para `GET/DELETE /auth/sessions`.
- **No borres** `ColaComandos`, `SyncProcessor`, `SyncWorker`, `ConflictResolver` ni sus DAO:
  la captura de asistencia deja de usarlos y quedan detrás de `FUNCIONES_FUTURAS` para B7.
  `SigehAppViewModel` solo debe llamar a `syncScheduler.programar()` cuando
  `FUNCIONES_FUTURAS` esté activo.
- El bucle de `SigehAppViewModel` que purga por alcance vencido: en modo real `/auth/me` no trae
  vigencias, así que solo aplica en Fake. En real, la vigencia la decide el servidor con 403.

### 7.3 Selector de ámbito sin catálogo Fake

En modo real, las opciones salen **solo** de `ProfileDto.assignments`:

- Entra una asignación si `permissions` contiene `ASISTENCIA_CAPTURAR` o `ASISTENCIA_VALIDAR`.
- Si `scope.servicioId == null` (ámbito de hospital o división), se muestra **deshabilitada** con
  el aviso «Este ámbito no indica un servicio. Pide a Coordinación una asignación por servicio».
  No llames a `/servicios` para enumerar.
- **Nombres:** el backend aún no los entrega. Crea la interfaz `EtiquetasContexto` con
  `etiquetaHospital(id)`, `etiquetaDivision(id)` y `etiquetaServicio(id)`. En Fake devuelve los
  nombres de `DatosFicticios`. En real devuelve un texto honesto, «Servicio ··· c91e» (los últimos 4
  caracteres del UUID). Cuando exista el endpoint propuesto `GET /auth/me/contextos` se cambia solo
  esa implementación.

### 7.4 Navegación

Revisa `navigation/SigehDestinos.kt` y `SigehNavHost.kt`:

- Flujo: `Login → SelectorAmbito → PaseLista` (con barra inferior).
- Barra inferior (`SigehBottomBar`): **Pase de lista** · **Programación** (solo con
  `PROGRAMACION_CONSULTAR` o en Fake) · **Cuenta**.
- Destinos nuevos: `DetalleJornada(programacionId)` y `Cuenta`.
- `ResumenPase`, `Incidencias`, `RegistrarIncidencia`, `Movimientos`, `PaseSalida`,
  `RecepcionEventual`, `Sincronizacion` y `Conflicto` solo se registran con `FUNCIONES_FUTURAS`.

---

## 8. Sistema de diseño

Ajusta `core/designsystem/theme/Color.kt` y `StatusColors.kt` a la paleta del sistema web
(SIGE-H-Fontend `src/styles/_tokens.scss`), para que web y móvil se vean iguales:

| Rol | Hex |
|---|---|
| Barra superior / marca | `#0F2942` |
| Primario (botones) | `#163A5F` |
| Acento clínico | `#0F766E` (fondo suave `#F0FDFA`, texto `#115E59`) |
| Fondo | `#F4F7FA` · tarjetas `#FFFFFF` · borde `#E2E8F0` / `#CBD5E1` |
| Texto | `#0F172A` · secundario `#334155` · tenue `#475569` |
| Presente | fondo `#ECFDF5`, texto `#065F46`, borde `#6EE7B7` |
| Ausente | fondo `#FEF2F2`, texto `#991B1B`, borde `#FCA5A5` |
| Parcial | fondo `#FFFBEB`, texto `#92400E`, borde `#FCD34D` |
| Pendiente / informativo | fondo `#EFF6FF`, texto `#1E40AF`, borde `#93C5FD` |
| Neutro (no capturable, cancelada) | fondo `#F1F5F9`, texto `#334155`, borde `#CBD5E1` |

Tipografía: Inter (ya está `ui-text-google-fonts`). Áreas táctiles de al menos 48 dp. Los
estados nunca se distinguen solo por color: siempre llevan texto e ícono. Agrega
`contentDescription` en los botones con solo ícono.

**Componentes nuevos o modificados** en `core/designsystem/components`:

- `EstadoAsistenciaBadge(estado)`: badge de estado operativo o de programación.
- `BotonesCaptura(onPresente, onAusente, onParcial, enabled)`: reemplaza el uso de
  `AttendanceSelector` en la lista. Son tres botones con borde, ícono y texto.
- `ConfirmarCapturaSheet`: `ModalBottomSheet` (ver 9.4).
- `ProgresoPagina(capturadas, capturables, conteos)`: barra y chips con la leyenda «en esta página».
- `EstadoBloqueo(icono, titulo, texto, accion?)`: tarjeta reutilizable para 401, 403, 429, 503 y
  versión incompatible.
- `BannerSinConexion`: usa el `ConnectivityObserver` que ya existe.
- `SnackbarAcuse`: «Registrado en el servidor · {Nombre} · {Estado} · HH:mm:ss», con la acción
  «Ver acuse».

---

## 9. Pantallas (según los mockups M2)

Datos ficticios en todos los ejemplos. El nombre de una persona es siempre
`nombres + " " + primerApellido` (el contrato no trae segundo apellido).

### 9.1 Iniciar sesión (`features/login`, modificar)

- Logotipo, «SIGE-H», subtítulo «Pase de lista del personal de enfermería».
- Campos «Identificador» (máx. 100) y «Contraseña» con botón para mostrarla. Botón
  «Iniciar sesión».
- Pie con chip de estado: «Servidor en línea · contrato API 1.0.0» (de `/meta/version`). Si
  `apiVersion` no empieza con `1.`, se muestra `EstadoBloqueo` «Actualiza la aplicación».
- Texto: «La sesión se guarda cifrada en el dispositivo. Para registrar asistencia se necesita
  conexión».
- 429: el botón se desactiva con cuenta regresiva. 503: «El inicio de sesión no está disponible
  por ahora».

### 9.2 Elegir servicio (`features/selectorambito`, rediseñar)

- Barra «Elige tu servicio» / «Ámbitos autorizados para esta sesión».
- Tarjetas tipo radio: hospital (pequeño) › división › **servicio** (grande), chip
  «Titular»/«Encargado» y chips de permiso «Capturar» (`ASISTENCIA_CAPTURAR`) y «Consultar»
  (`ASISTENCIA_VALIDAR`).
- Tarjeta deshabilitada con aviso cuando `servicioId == null` (ver 7.3).
- Botón «Continuar al pase de lista». Si solo hay una opción válida, se preselecciona.
- El ámbito elegido se guarda en `ScopeCache` y se purga al cerrar sesión.

### 9.3 Pase de lista (`features/paselista`, rediseñar)

1. **Barra superior:** nombre del servicio, «Hospital · {zonaHoraria}» y botón «Cambiar».
2. **Filtros:** selector de fecha (hoy por defecto, en la zona del hospital) y botón
   «Actualizar». Chips de turno: «Todos» más uno por cada `turnoCodigo` distinto de los items
   cargados. Al elegir un chip con `horarioId`, se vuelve a consultar con `horarioId`. Los
   items sin horario solo aparecen en «Todos».
3. **Progreso:** «{n} de {m} capturables registradas · en esta página». Aquí `m` cuenta los items
   con `capturable == true` o `capturada == true`. Debajo van chips con el conteo por estado.
4. **Filas** (avatar con iniciales, nombre, `turnoCodigo · HH:mm–HH:mm`):
   - `capturable == true`: badge «Por capturar» y `BotonesCaptura`.
   - `capturada == true`: badge del estado y la línea «Capturado por {Tú/Otro usuario} · HH:mm ·
     validación {pendiente/validada/observada}». Al tocar se abre `DetalleJornada`.
   - Otro caso: badge neutro y el texto del motivo (6.6), sin botones.
   - Envío en curso: la fila dice «Enviando…» con un indicador y sin botones.
5. «Cargar más» cuando haya `nextCursor`. Lista vacía: «No hay jornadas programadas para este
   día».
6. `BannerSinConexion` arriba de la lista cuando no haya red; los botones se desactivan.
7. Pull to refresh y recarga al volver a primer plano, porque la capturabilidad cambia con la
   hora del servidor.

### 9.4 Confirmar captura (`ConfirmarCapturaSheet`, nuevo)

- Se abre al tocar Presente, Ausente o Parcial, con ese estado preseleccionado.
- Muestra avatar, nombre y «{turno} · HH:mm–HH:mm · {servicio}».
- Selector segmentado Presente / Ausente / Parcial (se puede cambiar antes de confirmar).
- Aviso ámbar: «**Es la primera y única captura de esta jornada.** Después de confirmar no se
  puede cambiar desde la app».
- Aviso informativo (solo con Ausente o Parcial): «"Ausente" es una observación operativa: no
  genera falta ni incidencia, y queda pendiente de validación administrativa».
- Botones «Cancelar» y «Registrar {estado}», este último del color del estado. Al confirmar se
  genera la `Idempotency-Key` (5.5) y el botón se desactiva.

### 9.5 Captura confirmada (en `PaseLista`)

- Tras el 200 se cierra la hoja, se muestra `SnackbarAcuse` y se recarga la lista. La fila
  actualizada queda resaltada unos segundos.
- «Ver acuse» abre `DetalleJornada` con los datos del `CaptureReceiptDto`.

### 9.6 Errores de captura

Diálogos o banners dentro de `PaseLista`, según 6.6:

- «No pudimos confirmar el registro. Puede que sí se haya guardado. Al reintentar se envía la misma
  operación, así que no se duplica». Botones «Reintentar» y «Más tarde». «Más tarde» guarda la
  clave en `SavedStateHandle`; si el usuario vuelve a abrir esa jornada con el mismo estado, se
  reintenta con esa misma clave.
- «Esta jornada ya tenía registro»: recarga y botón «Ver registro».
- «Fuera del horario de captura» y «La jornada no admite captura».
- Banner oscuro «Sin conexión. Los botones de captura se desactivan hasta reconectar».

### 9.7 Detalle de jornada (`features/detallejornada`, nuevo)

- Toma los datos del último `PaseListaPageDto` en memoria (caché del repositorio por
  `programacionId`) o del acuse. **No** llama a otro endpoint.
- Encabezado: avatar, nombre, servicio y badge de estado.
- Tarjeta «Programación»: turno, inicio, fin y estado de la programación.
- Tarjeta «Asistencia»: estado operativo, validación administrativa y revisión.
- Tarjeta «Acuse del servidor»: capturó (Tú / Otro usuario), registrado (HH:mm:ss, hora del
  servidor) y evento.
- Nota: «Las correcciones y la validación las hace Coordinación; no se editan desde esta app».

### 9.8 Programación (`features/agenda` → renombrar a `features/programacion`)

- Solo si el ámbito tiene `PROGRAMACION_CONSULTAR` (en Fake siempre).
- Selector de semana (‹ 16–22 nov 2026 ›) y chips de día. La consulta va de lunes 00:00 a lunes
  00:00 en la zona del hospital, sin pasar de 168 h.
- Banner: «Muestra lo programado, no la asistencia ni la cobertura real».
- Agrupado por turno con el badge del estado (Programada / Cancelada / Sustituida / Cerrada).
- Nombres: si hay `PERSONAL_CONSULTAR`, se cruzan con `GET /empleados`; si no, «Trabajador ··· abcd».

### 9.9 Cuenta (`features/cuenta`, nuevo)

- Encabezado con el identificador con el que inició sesión (guárdalo cifrado junto a la sesión;
  `ProfileDto` no trae nombre) y «Cuenta local de SIGE-H».
- Tarjeta «Servicio actual» con el botón «Cambiar».
- Tarjeta «Sesiones» (`GET /auth/sessions`): «Iniciada {fecha} · Expira {fecha} · inactividad
  {fecha}». La `current` lleva el chip «Esta sesión», las revocadas van atenuadas con
  «Revocada» y las demás tienen el botón «Cerrar» (`DELETE`).
- Botón «Cerrar sesión» (`POST /auth/logout` y purga local aunque falle) y pie «App {versionName}
  · contrato API {apiVersion}».

### 9.10 Estados de acceso (`EstadoBloqueo`)

Pantallas completas o tarjetas para: 503 por módulo deshabilitado (con «Reintentar»), 403 sin
permiso (con «Elegir servicio»), 401 de sesión terminada (con «Iniciar sesión»; «No se perdió
ningún registro confirmado»), 429 por demasiados intentos (con cuenta regresiva) y versión
incompatible.

---

## 10. Modo Fake

Actualiza `core/data/fake/DatosFicticios.kt` y `FakeRepositorios.kt` para que hablen el
**mismo modelo** que el contrato:

- Un día con 7 jornadas en un servicio, con casos de todo tipo: una ya presente (capturada por
  «ti»), dos por capturar, una ausente capturada por otro usuario, una parcial, una
  `CANCELADA` no capturable y una del turno vespertino que aún no inicia (`FUERA_VENTANA`).
- La captura Fake se comporta como el backend: la misma clave devuelve el mismo acuse, una clave
  distinta sobre una jornada capturada da `YA_REGISTRADA`, y la misma clave con otro estado da
  `IDEMPOTENCY_CONFLICT`. Agrega una latencia simulada de 400–800 ms.
- Dos o tres asignaciones de ejemplo, una de ellas con `servicioId = null`.
- Sesiones Fake para la pantalla de Cuenta.

El modo Fake es el que se usará en la demostración si el backend no está disponible.

---

## 11. Pruebas

Actualiza las pruebas que ya existen (`PaseListaViewModelTest`, `PaseListaScreenTest`,
`IamAuthRepositoryTest`, `ApiErrorParserTest`, `RenovacionSesionTest`) y agrega:

**Unitarias (`app/src/test`):**

- Deserialización de `PaseListaPageDto` y `CaptureReceiptDto` con JSON de ejemplo: `eventoId`
  grande (`"9007199254740993"`) se conserva como texto y los `null` se aceptan.
- `RemotoPaseListaRepository` con `MockWebServer`:
  - el query lleva exactamente los parámetros esperados, sin vacíos;
  - el `PUT` lleva `Authorization`, `Idempotency-Key` UUID v4 y el cuerpo exacto;
  - mapeo de 400, 403, 404, cada 409, 429 y los dos 503;
  - timeout y luego éxito con la **misma** clave;
  - 401, refresh y reintento del `PUT` con la **misma** clave.
- `PaseListaViewModel`:
  - un doble toque produce una sola petición;
  - «Reintentar» reutiliza la clave y cambiar de estado genera una nueva;
  - conteos «en esta página», chips de turno derivados y «Tú» frente a «Otro usuario»;
  - recalcula la fecha cuando cambia `zonaHoraria`.
- `PoliticaAmbito` y el selector con permisos explícitos y `servicioId == null`.

**Instrumentadas (`app/src/androidTest`):** `PaseListaScreenTest` con los tres tipos de fila,
`ConfirmarCapturaSheet` (se abre con el estado preseleccionado; cancelar no envía nada) y el
banner sin conexión con los botones desactivados.

**Comandos que deben pasar:**

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest   # si hay emulador encendido
```

---

## 12. Prueba manual contra el backend local

1. Backend (otra terminal): desde `SIGE-H-WEB\apps\api` (**no** desde la raíz del repo), con
   `.env` de QA y las banderas `IAM_V2_ENABLED=true`, `ASISTENCIA_ENABLED=true` y, si se prueba
   Programación, `L3A_READS_ENABLED=true`. Comando: `npm run start:dev`.
2. `local.properties`:
   - emulador: `sigeh.api.baseUrl=http://10.0.2.2:3000/api/v1`;
   - teléfono por USB: `adb reverse tcp:3000 tcp:3000` y `sigeh.api.baseUrl=http://127.0.0.1:3000/api/v1`.
   - Además: `sigeh.api.iam=real` y `sigeh.api.asistencia=real`.
3. Usuario QA sintético con `ASISTENCIA_CAPTURAR` y `servicioId` explícito, y jornadas cuya
   ventana `[inicio, fin)` incluya la hora actual. Sin eso no hay nada capturable: **no** lo
   resuelvas en la app.
4. Registra el resultado en el reporte (sección 14) sin credenciales ni tokens.

---

## 13. Documentación

- Renombra `docs/mobile/contrato-pendiente.md` a `docs/mobile/contrato.md` y actualízalo:
  - tabla de rutas integradas en M2 con su clase responsable;
  - rutas opcionales y su permiso;
  - rutas que siguen sin existir (pásalas a «Futuro»);
  - el SHA del contrato que usaste.
- Sección **«Brechas del backend que afectan al móvil»** (actualízala con esto):
  1. No hay nombres de hospital, división ni servicio para el usuario. Propuesta:
     `GET /auth/me/contextos` → `[{ hospitalId, hospitalNombre, divisionId?, divisionNombre?,
     servicioId, servicioNombre, kind, permissions }]`, filtrado por las asignaciones propias.
  2. Los mandatos de hospital o división no enumeran servicios sin `CATALOGOS_ADMINISTRAR`.
  3. `/programaciones` no trae el nombre del trabajador.
  4. No hay catálogo de horarios ni de turnos (los chips se derivan de los items).
  5. No hay conteos agregados del pase (solo «en esta página»).
  6. `docs/api/openapi.json` del backend está desactualizado.
  7. `app.listen(port)` escucha en todas las interfaces de red; se sugiere `HOST=127.0.0.1`.
- Nuevo `docs/mobile/M2-pase-lista-online.md` con alcance, decisiones, pruebas ejecutadas y
  resultados, pendientes y cómo reproducir.
- `README.md` en la raíz, para subirlo al repo `Robmursan/SIGE-H-MOVIL`, con el alcance real: MVP
  en línea, sin presencia por defecto y con el modo offline, QR y suplencias como **futuro**. Así
  se corrige el README actual del remoto, que promete todo eso como si existiera.

---

## 14. Plan de trabajo y puntos de control

Haz los commits en local con mensajes `feat(m2): …`, `test(m2): …` y `docs(m2): …`. **No hagas
push ni crees PRs** sin confirmación de la persona.

| Fase | Trabajo | Punto de control |
|---|---|---|
| **F0** | Comprueba si el proyecto es un repo git. Si no lo es: `git init`, revisa `.gitignore` (que `local.properties` y `build/` queden fuera), commit «chore: línea base M1» y rama `feat/m2-pase-lista-online`. Descarga y verifica el contrato (sección 2). Lee el código existente y prepara un **inventario corto**: qué archivo cambia, se crea o queda detrás de un interruptor. | Presenta el inventario y espera confirmación si encuentras algo que contradiga este documento. |
| **F1** | DTOs, `AsistenciaApi`, extensión de `IamApi`, `ApiErrorParser`, `ResultadoCaptura` y pruebas de serialización y de MockWebServer. | `testDebugUnitTest` en verde. |
| **F2** | Repositorios Remoto y Fake, `DataModule`, interruptores de Gradle, `PoliticaAmbito`, `EtiquetasContexto` y ajustes a `SigehAppViewModel`. | Pruebas del repositorio y de la política en verde. |
| **F3** | Tokens de color y componentes nuevos del design system con `@Preview`. | `assembleDebug` en verde. |
| **F4** | Pantallas 9.1 a 9.10 con sus ViewModels y la navegación (7.4). | Recorrido Fake completo: login → servicio → captura → acuse → detalle → cuenta → logout. |
| **F5** | Pruebas de ViewModel e instrumentadas, `lintDebug` y prueba manual contra el backend si está disponible. | Todos los comandos de la sección 11. |
| **F6** | Documentación (sección 13) y reporte final. | Archivos de docs creados. |

---

## 15. Criterios de aceptación

- [ ] Con `sigeh.api.iam=real` y `sigeh.api.asistencia=real`, la app consulta `/pases-lista` y
      registra con `PUT …/asistencia` usando exactamente el contrato `ab9dd2a4…`.
- [ ] Ninguna jornada queda marcada sin una acción explícita y una confirmación.
- [ ] Un timeout seguido de «Reintentar» no duplica el registro (misma `Idempotency-Key`).
- [ ] Un doble toque produce una sola petición.
- [ ] `eventoId` y `revision` son `String` en todo el código.
- [ ] Sin red, la captura queda desactivada y la captura nunca entra a `ColaComandos`.
- [ ] El selector solo muestra ámbitos de `/auth/me` con `ASISTENCIA_CAPTURAR` o `ASISTENCIA_VALIDAR`.
- [ ] Con `FUNCIONES_FUTURAS=false`, incidencias, movimientos, eventuales, resumen y
      sincronización no aparecen en la navegación.
- [ ] Cada respuesta de error de la sección 6.6 tiene su pantalla o mensaje.
- [ ] Los contadores dicen «en esta página».
- [ ] El modo Fake reproduce el mismo flujo para la demostración.
- [ ] `testDebugUnitTest`, `lintDebug` y `assembleDebug` pasan.
- [ ] La documentación de la sección 13 está actualizada.

---

## 16. Prohibido

- Inventar rutas, campos, códigos de permiso o respuestas que no estén en el contrato.
- Llamar a `/hospitales`, `/servicios` o `/tableros/coordinacion` desde la app.
- Calcular en el teléfono si una jornada es capturable o usar su reloj para decidirlo.
- Guardar tokens fuera de `EncryptedSessionStore` o escribirlos en logs.
- Generar una `Idempotency-Key` nueva para reintentar la misma intención.
- Borrar el código offline de M0 (queda oculto, no se elimina).
- Modificar SIGE-H-WEB o SIGE-H-Fontend, hacer push o publicar sin confirmación.
- Usar datos reales de personas: todos los datos son sintéticos.

---

## 17. Reporte final que debes entregar

1. Resumen de lo hecho por fase.
2. Lista de archivos creados, modificados y puestos detrás de un interruptor.
3. Resultado de cada comando de la sección 11 (pasaron, fallaron u omitidos, con el motivo).
4. Resultado de la prueba manual contra el backend, o el motivo por el que no se hizo.
5. Brechas del backend encontradas y decisiones abiertas.
6. Los commits locales creados (hash y mensaje).
