# M2 · Pase de lista en línea contra el contrato real

**Hito:** M2 de SIGE-H móvil · **Encargo:** [`PROMPT_M2_SIGE-H_MOVIL.md`](PROMPT_M2_SIGE-H_MOVIL.md) ·
**Contrato:** `sigeh-v1` (SHA-256 `ab9dd2a4…`, ver [`contrato.md`](contrato.md)) · **Backend de referencia:**
SIGE-H-WEB `main` `2d1ec62` · **Repositorio:** `Robmursan/SIGE-H-MOVIL`, PR #1 a #6.

## 1. Alcance

Un jefe de servicio inicia sesión, elige un servicio autorizado, ve el pase de lista del día y registra
**una sola vez** Presente, Ausente o Parcial por jornada. Cada registro lleva confirmación previa y acuse del
servidor. Todo es **en línea**, contra el contrato real, y hay un modo Fake equivalente para la demostración.

| Pantalla | Qué hace |
|---|---|
| 9.1 Iniciar sesión | Identificador y contraseña (con botón para mostrarla). Pie con la versión del contrato. 429 con cuenta regresiva, 503 y versión incompatible |
| 9.2 Elegir servicio | Solo los ámbitos de `/auth/me` con `ASISTENCIA_CAPTURAR` o `ASISTENCIA_VALIDAR`. Sin servicio, la tarjeta va deshabilitada |
| 9.3 Pase de lista | Fecha del hospital, chips de turno, progreso «en esta página», «Cargar más», sin red y recarga al volver a primer plano |
| 9.4 Confirmar captura | Estado preseleccionado y cambiable. Aviso de primera y única captura, y de que «Ausente» no es falta |
| 9.5 Captura confirmada | Snackbar «Registrado en el servidor · …» con «Ver acuse» después del 200, y recarga de la lista |
| 9.6 Errores de captura | Diálogos de §6.6. «Reintentar» usa la misma `Idempotency-Key` |
| 9.7 Detalle de jornada | Programación, asistencia y acuse desde la memoria, sin otro endpoint |
| 9.8 Programación | Semana de lunes a lunes en la zona del hospital, por turno. Solo con `PROGRAMACION_CONSULTAR` |
| 9.9 Cuenta | Identificador, servicio actual, sesiones (cerrar otras, revocar la actual) y cierre de sesión |
| 9.10 Estados de acceso | 503, 403, 401, 429 y versión incompatible con `EstadoBloqueo` |

**Fuera de alcance** (oculto detrás de `FUNCIONES_FUTURAS`, no eliminado):
- captura sin conexión, lotes y conflictos (B7);
- corregir, validar o enviar el pase;
- incidencias, movimientos y pases de salida;
- recepción de eventual y cobertura;
- lectura de credencial QR.

## 2. Decisiones

| Decisión | Motivo |
|---|---|
| El contrato offline de M0 se renombró a `PaseListaOfflineRepository`; la agenda de M0, a `AgendaOfflineRepository` | El prompt pide rehacer `PaseListaRepository` y crear `features/programacion`, pero también prohíbe borrar el código offline de M0. Los renombres fueron commits de refactor sin cambios de comportamiento |
| La agenda propia de M0 queda detrás de `FUNCIONES_FUTURAS` y `features/programacion` es nuevo | §9.8 pide renombrar `features/agenda`, pero esa agenda es la copia offline de M0 (no se borra) |
| `sigeh.api.asistencia=real` exige `sigeh.api.iam=real` | Sin tokens del servidor todas las rutas responderían 401 |
| Programación (`/programaciones`, `/empleados`) usa el mismo interruptor que el pase | Son lecturas operativas del mismo backend; F2 no las incluía y la pantalla 9.8 las necesita (se agregaron en F4) |
| Los permisos salen de los códigos de `/auth/me`, no del nombre del rol | §6.5. Las asignaciones del mismo ámbito se unen antes de decidir (el backend autoriza cada permiso con cualquier asignación vigente) |
| El login Fake trae asignaciones como `/auth/me` (una sin servicio) | El selector y las facultades funcionan igual en Fake y en real |
| La `Idempotency-Key` vive en `SavedStateHandle` por `(programacionId, estado)` | Sobrevive a la muerte del proceso. «Cancelar» o «Más tarde» tras un resultado incierto la conservan: si el servidor sí guardó, el reintento devuelve el mismo acuse |
| «Se habilita a las HH:mm» usa el reloj del teléfono | Solo para el texto. La capturabilidad la decide el servidor |
| La barra superior es `#0F2942` y los íconos del sistema van claros solo mientras se ve | Paleta web (§8). Se cuentan las barras activas para no depender del orden de la navegación |
| El texto tenue es `#475569` (la web usa `#64748B`) | Lo pide el prompt; contraste de 7.6:1 contra 4.8:1 |
| Sin `java.time` | minSdk 24 sin desugaring: `HoraHospital` parsea ISO-8601 con zona y fracciones |
| La cola offline solo se programa con `FUNCIONES_FUTURAS` (al abrir la app y al iniciar sesión) | La captura de M2 no la usa |
| `versionName 0.2.0-m2` | El pie de Cuenta muestra la versión |

## 3. Pruebas ejecutadas y resultados

| Comando (§11) | Resultado |
|---|---|
| `.\gradlew.bat testDebugUnitTest` | ✅ **166/166** |
| `.\gradlew.bat lintDebug` | ✅ sin avisos nuevos. Quedan 4 de versiones de Gradle/AGP en `libs.versions.toml`, que ya existían |
| `.\gradlew.bat assembleDebug` | ✅ sin advertencias de Kotlin |
| `.\gradlew.bat assembleDebugAndroidTest` | ✅ compila |
| `.\gradlew.bat connectedDebugAndroidTest` | ⏭️ **No se ejecutó**: no había emulador encendido. Hay imagen `android-35`, pero ningún AVD |

Pruebas unitarias por área:

| Área | Clases (pruebas) |
|---|---|
| Contrato y red | `AsistenciaDtosTest` (8), `AsistenciaApiTest` (7), `IamSesionesApiTest` (3), `ApiErrorParserTest` (12), `RenovacionSesionTest` (7), `NetworkConfigTest` (3), `RegistroHttpDebugTest` (2, source set `testDebug`) |
| Repositorios | `RemotoPaseListaRepositoryTest` (11), `FakePaseListaRepositoryTest` (12), `RemotoProgramacionRepositoryTest` (5), `FakeProgramacionRepositoryTest` (3), `RemotoSesionesRepositoryTest` (4), `FakeSesionesYEtiquetasTest` (4), `IamAuthRepositoryTest` (6), `ResultadoCapturaTest` (4) |
| Política y sesión | `PoliticaAmbitoTest` (13), `SigehAppViewModelTest` (6) |
| ViewModels | `PaseListaViewModelTest` (12), `PaseListaErroresTest` (4), `LoginViewModelTest` (4), `SelectorYNavegacionTest` (2), `ProgramacionViewModelTest` (4), `DetalleJornadaViewModelTest` (3), `CuentaViewModelTest` (3) |
| Recorrido completo | `RecorridoFakeTest` (2): login → servicio → captura → acuse → detalle → cuenta → logout con los ViewModels y repositorios Fake reales |
| Otros | `HoraHospitalTest` (8), `ComponentesM2Test` (8), `SyncProcessorTest` (6, de M0) |

Lo que §11 pedía, cubierto:
- **Deserialización.** `eventoId` `9007199254740993` se conserva como texto y los nulos explícitos u omitidos
  se aceptan.
- **MockWebServer (cadena real de OkHttp):**
  - query exacto, sin parámetros vacíos ni repetidos;
  - `PUT` con `Authorization`, `Idempotency-Key` UUID v4 y cuerpo exacto;
  - cada código de §6.6;
  - timeout y luego éxito con la misma clave;
  - 401, refresh y reintento con la misma clave.
- **ViewModel del pase:**
  - un doble toque produce una sola petición;
  - «Reintentar» reutiliza la clave y cambiar de estado genera una nueva;
  - la clave sobrevive a la muerte del proceso;
  - conteos «en esta página», chips de turno y «Tú» frente a «Otro usuario»;
  - recálculo de la fecha cuando cambia `zonaHoraria`.
- **Política y selector.** Permisos explícitos y `servicioId == null`.
- **Instrumentadas** (`PaseListaScreenTest`, escritas y compiladas, sin ejecutar):
  - los tres tipos de fila;
  - `ConfirmarCapturaSheet` abre con el estado preseleccionado y «Cancelar» no envía nada;
  - el banner sin conexión desactiva los botones.

Criterios de aceptación (§15) revisados en el código:
- Las 11 rutas declaradas están en `sigeh-v1`, y no se llama a `/hospitales`, `/servicios` ni `/tableros`.
- `eventoId` y `revision` son `String` en todo el código.
- `expectedRevision` siempre vale `"0"`.
- La captura no usa `ColaComandos` ni WorkManager.
- No hay `Log` con tokens, cuerpos ni claves.

### Prueba manual contra el backend (§12)

**No se hizo.** En la máquina de desarrollo no había un backend en `127.0.0.1:3000`. Además, la copia local de
SIGE-H-WEB está en otra rama (sin `apps/`) y no hay PostgreSQL ni Docker. Para hacerla, sigue la sección 5.

## 4. Pendientes

1. **Pruebas instrumentadas.** Correr `connectedDebugAndroidTest` en un emulador o teléfono (aún no se han
   ejecutado nunca).
2. **Revisión visual.** Hacerla en un dispositivo: paleta web, barra de marca, hoja de confirmación y snackbar.
   Hasta ahora solo se vio con `@Preview`.
3. **Prueba manual contra el backend** (§12) con un usuario de QA sintético.
4. **Brechas del backend.** Las siete brechas de [`contrato.md` §7](contrato.md#7-brechas-del-backend-que-afectan-al-móvil),
   sobre todo los nombres de contexto (`GET /auth/me/contextos`).
5. **Dependencias.** Actualizar AGP y las dependencias que marca lint (fuera del alcance de M2).
6. **Bloque B7** (sincronización offline). Al llegar, retomar `ColaComandos`, `SyncProcessor` y las pantallas
   detrás de `FUNCIONES_FUTURAS`.

## 5. Cómo reproducir

**Modo Fake (demostración, sin backend).** Sin claves de `sigeh.api.*` en `local.properties`:

```powershell
.\gradlew.bat installDebug
```

Entra con cualquier identificador y contraseña (se necesita red). Elige **Medicina Interna** (captura) o
**Cirugía General** (solo consulta); la tarjeta de división sin servicio va deshabilitada. El pase trae 7
jornadas:
- una presente capturada por ti;
- dos por capturar;
- una ausente de otro usuario;
- una parcial validada;
- una cancelada;
- una vespertina que aún no inicia.

La misma clave devuelve el mismo acuse, una clave nueva sobre una jornada capturada da `YA_REGISTRADA`, y cada
respuesta tarda entre 400 y 800 ms.

**Contra el backend local.**
1. **Backend.** En otra terminal, desde `SIGE-H-WEB\apps\api` (**no** desde la raíz), con `.env` de QA y
   `IAM_V2_ENABLED=true`, `ASISTENCIA_ENABLED=true` y, para Programación, `L3A_READS_ENABLED=true`:
   `npm run start:dev`.
2. **`local.properties`.**
   - Emulador: `sigeh.api.baseUrl=http://10.0.2.2:3000/api/v1`.
   - Teléfono por USB: `adb reverse tcp:3000 tcp:3000` y `sigeh.api.baseUrl=http://127.0.0.1:3000/api/v1`.
   - En ambos casos, además: `sigeh.api.iam=real` y `sigeh.api.asistencia=real`.
3. **Usuario de QA sintético.** Con `ASISTENCIA_CAPTURAR` y `servicioId` explícito, y jornadas cuya ventana
   `[inicio, fin)` incluya la hora actual. Sin eso no hay nada capturable; no se resuelve en la app.
4. **Pantallas futuras de M0.** Con `sigeh.features.futuras=true` aparecen Incidencias y Movimientos en la
   barra, y en Cuenta los accesos a Sincronización, Resumen del pase y Agenda.

## 6. Reporte final (§17)

### 6.1 Resumen por fase

| Fase | Trabajo | PR |
|---|---|---|
| F0 | Repo git, línea base M1, contrato `sigeh-v1` verificado por SHA-256, unión con el historial del remoto | #1 |
| F1 | DTO de asistencia y sesiones, `AsistenciaApi`, `IamApi` con sesiones, `ApiErrorParser` con las causas reales, `ResultadoCaptura` y pruebas de serialización y MockWebServer | #1 |
| F2 | `PaseListaRepository` en línea (real y Fake), `SesionesRepository`, `EtiquetasContexto`, `PoliticaAmbito` por códigos, interruptores de Gradle, `DataModule` y ajustes de sesión | #2 |
| F3 | Paleta del sistema web, barra de marca y componentes nuevos con `@Preview` | #3 |
| F4 | Pantallas 9.1 a 9.10, navegación §7.4, programación semanal (`/programaciones`, `/empleados`) y recorrido Fake | #4 |
| F5 | Pruebas faltantes de §11, privacidad del registro de debug y criterios de aceptación; corrección del aviso en Cuenta | #5 |
| F6 | Esta documentación y el README | #6 |

### 6.2 Archivos

Rutas bajo `app/src/main/java/mx/sigeh/app/` salvo que se indique otra cosa. Más de 100 archivos de la app
cambiaron frente a la línea base M1.

**Creados:**
- `core/network`: `asistencia/AsistenciaApi.kt`, `asistencia/AsistenciaDtos.kt`, `lecturas/LecturasApi.kt` y
  `lecturas/LecturasDtos.kt`.
- `core/data`:
  - `PaseListaRepository.kt` (contrato nuevo) y `ResultadoCaptura.kt`;
  - `SesionesRepository.kt`, `EtiquetasContexto.kt` y `ProgramacionRepository.kt` (M2);
  - `remote/RemotoPaseListaRepository.kt`, `remote/RemotoSesionesRepository.kt` y
    `remote/RemotoProgramacionRepository.kt`;
  - `fake/FakeAsistencia.kt` y `fake/FakeProgramacion.kt`.
- `core/common`: `HoraHospital.kt` e `Interruptores.kt`.
- `core/model`: `Programacion.kt`.
- `core/designsystem/components`: `EstadoAsistenciaBadge.kt`, `BotonesCaptura.kt`, `ConfirmarCapturaSheet.kt`,
  `ProgresoPagina.kt`, `EstadoBloqueo.kt`, `BannerSinConexion.kt`, `SnackbarAcuse.kt` y `AvatarIniciales.kt`.
- `features`: `detallejornada/` (ViewModel y pantalla), `programacion/` (ViewModel y pantalla), `cuenta/`
  (ViewModel y pantalla) y `paselista/PaseOfflineM0.kt`.
- Pruebas: 22 clases nuevas y 2 utilidades (`ClienteDePrueba`, `PaseListaGuionado`) en `app/src/test` y
  `app/src/testDebug`.

**Modificados:**
- `app/build.gradle.kts` (interruptores y `versionName`).
- `core/network`: `ApiErrorParser`, `NetworkConfig`, `NetworkModule`, `iam/IamApi` e `iam/IamDtos`.
- `core/auth`: `PoliticaAmbito`, `ScopeCache`, `SesionManager` y `Session`.
- `core/data`: `DataModule`, `AuthRepository`, `fake/DatosFicticios`, `fake/FakeRepositorios` y
  `remote/RemotoRepositorios`.
- `core/model/Asistencia` y `core/common`: `CommonModule` y `Reloj`.
- Sistema de diseño: `Color`, `StatusColors`, `SigehTopBar`, `SigehBottomBar`, `SigehFields`, `SigehIcons` y
  `Opciones`.
- Pantallas: `features/login/*`, `features/selectorambito/*` y `features/paselista/PaseLista*`.
- Navegación: `navigation/*`, `MainActivity` y `SigehAppViewModel`.
- `app/src/debug/.../DebugNetworkModule` (oculta `Idempotency-Key`).
- Pruebas que ya existían: `PaseListaViewModelTest`, `PaseListaScreenTest`, `IamAuthRepositoryTest`,
  `ApiErrorParserTest`, `RenovacionSesionTest` y `PoliticaAmbitoTest`.

**Renombrados sin cambio de comportamiento:**
- El contrato de M0 `PaseListaRepository` pasó a `PaseListaOfflineRepository`.
- El contrato de M0 `ProgramacionRepository` pasó a `AgendaOfflineRepository`.
- `docs/mobile/contrato-pendiente.md` pasó a `docs/mobile/contrato.md`.

**Detrás de un interruptor:**

| Interruptor | Qué controla |
|---|---|
| `FUNCIONES_FUTURAS` (`sigeh.features.futuras`) | Pantallas `features/incidencias`, `features/movimientos`, `features/incorporacion`, `features/sincronizacion`, `features/agenda` y `ResumenPase`; programación de `SyncWorker` (`ColaComandos`, `SyncProcessor` y `ConflictResolver` sin cambios) |
| `ASISTENCIA_REAL` (`sigeh.api.asistencia`) | `RemotoPaseListaRepository` y `RemotoProgramacionRepository` (si no, los Fake) |
| `IAM_REAL` (`sigeh.api.iam`, ya existía) | `IamAuthRepository`, `RemoteServidorRepository`, `RemotoSesionesRepository` y `EtiquetasPorIdentificador` |

### 6.3 Commits

| PR | Commits |
|---|---|
| #1 (F0–F1) | `74de668` chore: línea base M1 · `630952d` docs(m2): contrato OpenAPI sigeh-v1 verificado · `b1e85b5` DTO y AsistenciaApi · `5c3300f` sesiones de IAM · `0260d67` causas reales del backend y ResultadoCaptura · `54f57aa` pruebas de serialización y MockWebServer · `3edcc2b` merge con el historial del remoto |
| #2 (F2) | `2b8eaa3` refactor PaseListaOfflineRepository · `6c8868b` interruptores · `16fb33f` PaseListaRepository en línea · `4f2ed42` SesionesRepository y EtiquetasContexto · `5b2777c` PoliticaAmbito por códigos · `11169eb` modo Fake · `7c2a2e6` DataModule y sesión · `d825e5f` pruebas |
| #3 (F3) | `e7520d8` paleta web · `0330b85` barra de marca · `64a41f1` badge y avatar · `9bdbb82` BotonesCaptura y ConfirmarCapturaSheet · `8ee0c1f` progreso, bloqueos, banner y snackbar · `6448063` pruebas |
| #4 (F4) | `3537c6b` HoraHospital · `b3c788e` refactor AgendaOfflineRepository · `22bed4b` programación semanal · `a69b87f` contraseña visible e íconos · `384f230` login 9.1 · `9903db0` selector 9.2 · `c76a489` pase, detalle, programación, cuenta y navegación · `2217177` recorrido Fake |
| #5 (F5) | `bd9e3b3` fix aviso en Cuenta · `9b242f8` errores de §6.6 · `5dc8121` programación y detalle · `559023a` registro HTTP de debug · `3d49d4c` renovación y cola offline |
| #6 (F6) | Esta documentación y el README |
