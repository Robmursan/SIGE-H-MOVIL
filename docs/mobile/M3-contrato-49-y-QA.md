# SIGE-H móvil · M3: contrato de 49 operaciones, backend simulado y plan de QA

**Contrato:** SIGE-H-WEB `artifacts/openapi/sigeh-v1.json`, commit `9de9802` (PR #8, rama
`feat/codex-b1-iam-org`), revisión `e2e-mvp-02.1`, SHA-256 `a6fbe480…` (verificado contra el manifiesto).
**Alcance móvil:** autenticación, contextos autorizados, consulta del pase de lista y captura de asistencia.
La administración de empleados, organización y gestión laboral corresponden a SIGE-H Web.

## 1. Qué cambió en el contrato

Comparación operación por operación contra la copia anterior (`ab9dd2a4…`, 21 operaciones), con todas las
referencias `$ref` resueltas:

| Grupo | Operaciones | Cambio | Móvil |
|---|---|---|---|
| Sesión | `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me` | Idénticas | Ya integradas (M1) |
| Contextos | `GET /auth/me/contextos` | **Nueva** | **Integrada en M3** |
| Pase y captura | `GET /pases-lista`, `PUT /programaciones/{programacionId}/asistencia` | Idénticas | Ya integradas (M2) |
| Otras que usa la app | `/auth/sessions`, `DELETE /auth/sessions/{id}`, `/meta/version`, `/programaciones`, `/empleados` | Idénticas | Ya integradas |
| Administración y gestión laboral | 27 operaciones (organización, personal, horarios, patrones, vacaciones, incapacidades, incidencias, calendario) | Nuevas | No aplican: SIGE-H Web |

Ninguna operación se quitó. Por eso los DTO de sesión, pase y captura no se modificaron: solo se actualizó la
referencia al contrato en sus comentarios.

## 2. Qué se implementó

**Contextos (`GET /auth/me/contextos`).**
- `IamApi.contextos()` y `OwnContextDto` con los 12 campos del contrato. Los campos nullable aceptan el `null`
  explícito y el campo ausente; los campos desconocidos se ignoran. Ojo: aquí llegan `permisos` y `tipo`, no
  `permissions` y `kind` como en `/auth/me`.
- `ContextosRepository` con `RemotoContextosRepository` (IAM real) y `FakeContextosRepository` (demostración),
  elegidos en `SeleccionApiModule`. Resultados: `Exito`, `SesionVencida` (401 tras la única renovación),
  `NoDisponible(httpCode)` y `SinConexion`.
- `PoliticaAmbito.ambitosDeContextos` aplica la misma regla que con `/auth/me`. Los contextos del mismo ámbito se
  unen y las facultades salen solo de `permisos`; `tipo` nunca las otorga. Sin servicio, la tarjeta queda
  deshabilitada. Se conservan los nombres, la zona y la vigencia más lejana.
- El selector muestra los nombres del servidor y «Vigente hasta 1 dic 00:00» en los encargos. Si contextos no
  está disponible (la ruta está marcada `prepared-own-contexts-awaiting-sql-validation`), usa las asignaciones de
  `/auth/me` como antes.
- `ServicioActivo` lleva `zonaHoraria`: el pase calcula «hoy» en la zona del hospital desde la primera consulta.

**Sesión, pase y captura (sin cambios de contrato, revisados contra el handoff).**

| Regla del handoff | Dónde se cumple |
|---|---|
| `Authorization: Bearer` solo en encabezado; nunca en query, body ni logs | `AuthInterceptor`; el registro de debug oculta `Authorization` e `Idempotency-Key` |
| Access en memoria; refresh solo cifrado (Keystore); borrar ambos al logout | `EncryptedSessionStore`, `SesionManager.cerrarSesion` |
| Refresh de un solo uso, renovaciones serializadas (single-flight); a lo sumo una renovación por 401 | `TokenRefresher.renovar` (`synchronized`), `SesionAuthenticator` (`priorResponse`) |
| Logout 204; descartar tokens aunque se pierda la respuesta | `IamAuthRepository.cerrarSesion` (best effort) + purga local |
| `Idempotency-Key` UUID v4 estable por intención; misma clave tras timeout/503 | `PaseListaViewModel` (`SavedStateHandle`), `ResultadoCaptura.esTerminal` |
| `expectedRevision: "0"` siempre (solo primera captura) | `CapturarAsistenciaDto.primeraCaptura` |
| `eventoId` y `revision` como texto (BIGINT) | `CaptureReceiptDto`, `PaseListaItemDto`, `LastCaptureDto` |
| `PENDIENTE` = sin captura, nunca AUSENTE; `validacionAdministrativa` aparte | Pantalla del pase (M2) |

## 3. Backend simulado (mientras no hay QA)

`app/src/test/java/mx/sigeh/app/testutil/BackendSimulado.kt` es un `Dispatcher` de MockWebServer. Reproduce las
reglas de `apps/api` en 9de9802 para las siete operaciones del alcance más `/meta/version`. Habla HTTP de
verdad, así que la prueba usa la misma cadena de OkHttp, los mismos clientes Retrofit y los mismos repositorios
que hablarán con QA.

| Comportamiento | Cómo lo imita |
|---|---|
| Login | `ValidationPipe` (400 con lista), 401 genérico, 429 por cuenta tras 5 fallos sin `Retry-After` |
| Refresh | 64 caracteres, rota en cada uso; **replay** de uno consumido → revoca la sesión y 401 |
| Contextos | Solo los propios con nombres; cualquier query → 400 «Contextos propios no admite filtros» |
| Pase | Query estricto (400 «Entrada inválida» / «Límite inválido» / «Fecha inválida»), 403 sin permiso en el ámbito, 404 «Contexto no disponible», cursor exclusivo `id:asc` |
| Captura | Clave UUID v4 obligatoria; cuerpo exacto; replay con el mismo comando → mismo acuse; otro comando → 409 `IDEMPOTENCY_CONFLICT`; 409 con el motivo; 404 fuera del ámbito |
| Banderas y fallas | `iamHabilitado`, `asistenciaHabilitada`, `contextosDisponibles` (503 con los textos reales); `capturasInciertas` guarda y responde 503 «reintente con la misma clave»; `vencerAccessTokens()`, `revocarSesiones()` |

Identidades sintéticas (contraseña `Contrasena-Sintetica-2026`; ninguna es real):

| Identidad | Contextos | Para qué sirve |
|---|---|---|
| `capturista_android_sintetico` | TITULAR con `ASISTENCIA_CAPTURAR` en Medicina Interna Sintética; ENCARGADO de solo consulta en Pediatría Sintética, vigente hasta el 1 dic | Recorrido feliz, encargo con vigencia |
| `admin_web_sintetico` | TITULAR de hospital con `CATALOGOS_ADMINISTRAR` | El selector no ofrece nada; pase y captura → 403 |
| `capturista_b_sintetico` | TITULAR con captura en otro hospital (`America/Tijuana`) | Aislamiento: no lee (403) ni captura (404) el Servicio A |

Pruebas:

```powershell
.\gradlew.bat testDebugUnitTest --tests "mx.sigeh.app.core.network.FlujoMovilContraBackendSimuladoTest"
.\gradlew.bat testDebugUnitTest --tests "mx.sigeh.app.core.network.iam.ContextosApiTest" --tests "mx.sigeh.app.core.auth.PoliticaContextosTest"
.\gradlew.bat testDebugUnitTest
```

> El simulador **no** acredita integración: `ANDROID_QA_CHECKLIST.md` exige evidencia en QA efímero para marcar
> «Android/PostgreSQL real PASS».

## 4. Plan para el entorno QA

**Configuración** (`local.properties`, no se versiona; nunca en el código):

```properties
sigeh.api.baseUrl=http://10.255.60.40:3000/api/v1/
sigeh.api.iam=real
sigeh.api.asistencia=real
sigeh.features.futuras=false
```

`10.255.60.40` ya está permitido en HTTP solo en debug (`src/debug/res/xml/network_security_config.xml`). En el
emulador con la API en la misma PC también sirve `http://10.0.2.2:3000/api/v1/`. Por USB, `adb reverse tcp:3000
tcp:3000` y `http://127.0.0.1:3000/api/v1/`. El handoff pide no exponer la API en la LAN: confirmar con backend
que `10.255.60.40` está autorizado y usar solo identidades sintéticas.

**Antes de abrir la app** (a cargo de backend):
1. `GET /api/v1/meta/version` responde `1.0.0` y el SHA del contrato desplegado es `a6fbe480…`.
2. `IAM_V2_ENABLED=true` y `ASISTENCIA_ENABLED=true` solo en el proceso QA; ORG, workforce, L3A y CAL apagadas.
3. Identidades sintéticas:
   - **Capturista Android** con `ASISTENCIA_CAPTURAR` vigente, limitada a un servicio.
   - **Administrador web**, que confirma desde SIGE-H Web que la captura quedó persistida.
4. Al menos una programación sintética **legítima y en ventana** en ese servicio (no se fabrica por SQL).

**Recorrido manual con la app** (registrar códigos HTTP, sin PII ni tokens):

| # | Paso | Esperado |
|---|---|---|
| 1 | Login con la capturista | 200; el pie muestra la versión `1.0.0` |
| 2 | Contraseña incorrecta | Mensaje genérico; no revela si la cuenta existe |
| 3 | Selector | Nombres reales del servicio; solo contextos propios vigentes; chip «Capturar» |
| 4 | Pase de hoy | Fecha en la zona del hospital; `PENDIENTE` como «sin captura» |
| 5 | Capturar Presente/Ausente/Parcial | Confirmación, 200 y acuse con «Validación pendiente» |
| 6 | Recargar | La jornada aparece registrada; `revision` = `eventoId` del acuse |
| 7 | Desde web (admin) | El GET muestra la captura persistida |
| 8 | Esperar > 300 s y actualizar | Un solo `POST /auth/refresh` y la petición sigue |
| 9 | Admin web entra en la app | El selector no ofrece servicios (no tiene permisos de asistencia) |
| 10 | Cerrar sesión | 204; la app vuelve al login y no queda sesión |

**Lo que queda pendiente con solo dos identidades.** El checklist pide un **usuario B en otro ámbito** para
comprobar que A no lee el hospital o servicio B (403/404). El administrador web cubre «sin
`ASISTENCIA_CAPTURAR` → 403», pero no el aislamiento entre dos capturistas. Pedir una tercera identidad o un
segundo servicio sintético. El simulador ya lo cubre con `capturista_b_sintetico`.

## 5. Decisiones abiertas

- **Sondeo cada 5 s.** El checklist menciona «Polling cada 5 s». La app actualiza al volver a primer plano, con
  «Actualizar» o «deslizar para actualizar» y después de cada captura, pero **no** sondea de forma automática.
  Si el equipo lo quiere, se agrega en `PaseListaViewModel`. Debe empezar desde la primera página, sin superponer
  peticiones, y pausarse al salir, cerrar sesión o cambiar de servicio.
- **Contextos con respaldo.** Mientras contextos espere validación SQL, la app vuelve a `/auth/me` si responde
  503. Cuando backend la declare estable, se puede retirar el respaldo y `EtiquetasPorIdentificador`.
- **Contextos vacíos.** Un `200 []` se respeta: la app no ofrece servicios y **no** usa el respaldo, porque el
  servidor dice que no hay contextos vigentes. Si en QA contextos devuelve `[]` pero `/auth/me` trae una
  asignación con `ASISTENCIA_CAPTURAR`, es una discrepancia del backend: reportarla en lugar de forzar el respaldo.
