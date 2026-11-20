# SIGE-H móvil · Contrato HTTP (M1: IAM V2 integrado)

Fuente del contrato real: repositorio **SIGE-H-WEB**, rama `main`, commit `2604aa6` (2026-11-15).
Archivos: `apps/api/src/modules/iam/presentation/auth.controller.ts`, `auth.dto.ts`, `iam.guards.ts`
y `apps/api/src/modules/operaciones/operaciones.controller.ts`. Ficha de IAM:
`docs/iam/D1-implementacion-IAM-V2.md`. Prefijo de todas las rutas: `/api/v1`.

> `docs/api/openapi.json` del backend está **desactualizado** (11-nov, solo 5 rutas, sin IAM).
> El contrato se tomó de los DTO y controladores reales; Swagger en ejecución (`/docs/openapi.json`)
> sí incluye las rutas de IAM.

## 1. Integrado en la app (detrás de interruptor)

| Ruta | operationId | Uso en la app | Código |
|---|---|---|---|
| `POST /auth/login` | iamLogin | Inicio de sesión. 400/401 = credencial genérica; 429 = límite de intentos; 503 = IAM deshabilitado | `IamAuthRepository` |
| `GET /auth/me` | iamMe | Asignaciones (TITULAR/ENCARGADO, ámbito por UUID y permisos) tras el login | `IamAuthRepository` |
| `POST /auth/refresh` | iamRefresh | Renovación del access token (300 s), proactiva y ante un 401 | `TokenRefresher` y `SesionAuthenticator` |
| `POST /auth/logout` | iamLogout | Cierre de sesión (best effort; la purga local ocurre siempre) | `IamAuthRepository` |
| `GET /meta/version` | getApiContractVersion | Versión del contrato en el pie del login | `RemoteServidorRepository` |

**No integradas:** `GET /auth/sessions` y `DELETE /auth/sessions/:sessionId`, porque no hay pantalla
para ellas en los mockups. `GET /health*` tampoco se usa: es un endpoint de operación, no de la app.

### Cómo activar el modo real

En `local.properties` (no se versiona):

```properties
sigeh.api.baseUrl=http://10.0.2.2:3000/api/v1   # backend local visto desde el emulador (solo debug)
sigeh.api.iam=real
```

- Sin estas dos claves la app sigue **100 % Fake** (igual que en M0).
- En release solo se permite HTTPS (`res/xml/network_security_config.xml`). En debug también se
  permite HTTP únicamente hacia `10.0.2.2`, `localhost` y `127.0.0.1`.
- El backend debe tener `IAM_V2_ENABLED=true`, `IAM_JWT_ISSUER`, `IAM_JWT_AUDIENCE` y `JWT_SECRET`.
  Con el valor por defecto (`false`) todas las rutas de IAM responden **503 "IAM no disponible"**, y la
  app muestra "El inicio de sesión no está disponible por ahora".

### Reglas de IAM V2 que la app respeta

- **Access token de 300 s.** Se renueva 30 s antes de vencer o al recibir un 401, una sola vez por
  token. Las renovaciones se serializan: si otro hilo ya renovó, no se reenvía el refresh.
- **Refresh opaco de un solo uso.** Rota en cada renovación y **un replay revoca toda la familia**.
  Por eso nunca se reintenta un refresh ya usado.
- **400/401 en el refresh**: la sesión se marca vencida, se detiene la cola de sincronización y se
  pide iniciar sesión de nuevo. **429/503 o falta de red**: es transitorio y la sesión se conserva.
- **Sesión de 8 h absolutas y 30 min de inactividad** (solo el refresh renueva la inactividad). Un
  teléfono que pasa más de 30 min sin red tendrá que iniciar sesión otra vez. Las capturas quedan
  en Room y se envían después.
- **Tokens:** solo se guardan cifrados (AES-GCM, Android Keystore). El registro HTTP de debug no
  escribe cuerpos de `/auth/...` y oculta `Authorization`.
- **Errores:** el backend responde con el formato por defecto de NestJS,
  `{statusCode, message, error}`, no con el de la especificación §8. `ApiErrorParser` acepta ambos.

## 2. Brechas del backend que afectan al móvil

| Brecha | Efecto en la app | Qué se necesita |
|---|---|---|
| `/auth/me` no trae el nombre de la persona | El saludo muestra el identificador ("Hola, cuenta_ficticia") | Nombre o alias en `ProfileDto` o un endpoint de PER |
| Asignaciones sin nombres ni vigencias, y ORG sin rutas (CU-ORG-01/04/07 "sin controller") | El selector sigue con el catálogo Fake. El rol real solo **limita** facultades (`PoliticaAmbito`); sin asignaciones no se ofrece ningún ámbito | `GET /hospitales`, `GET /hospitales/{id}/divisiones`, `GET /servicios` y vigencia en la asignación |
| Códigos de permiso definidos en la tabla `permisos` de la base, no en el contrato | La app guarda `permisos` pero aún no los interpreta | Lista oficial de códigos por acción (capturar pase, recibir eventual…) |
| Sin filtro global de errores | No hay `code`, `traceId` ni `retryable` | Filtro con el formato de la especificación §8 |

## 3. Pendiente (sin API en el backend; sigue Fake)

Estado de la especificación entre paréntesis. Ninguna ruta existe aún en SIGE-H-WEB: los módulos
asistencia, programación, cobertura, personal y balances son contenedores vacíos.

| Uso en la app | Método y ruta propuesta | CU |
|---|---|---|
| Alcances vigentes | `GET /me/alcances` (AC) | CU-IAM-05 · hoy se cubre en parte con `/auth/me` |
| Programación / agenda | `GET /programaciones` · `GET /me/agenda` (AC) | CU-CAL-13 · CU-CAL-19 |
| Pase de lista | `GET /pases-lista` (AC) | CU-ASI-01 |
| Registrar asistencia | `PUT /programaciones/:id/asistencia` (AC) | CU-ASI-02 |
| Evento parcial | `POST /asistencias/:id/eventos` (AC) | CU-ASI-03 |
| Borrador y envío del pase | `PUT /pases-lista/:id/borrador` (PROP) · `POST /pases-lista/:id/envio` (AC) | CU-ASI-05 · 06 |
| Sincronizar lote offline | `POST /sincronizacion/asistencias` (PROP) | CU-ASI-13 |
| Estado del lote | `GET /sincronizacion/lotes/:id` (PROP) | CU-OPS-04 |
| Resolver conflicto | `POST /asistencias/:id/conflictos/resolucion` (PROP) | CU-ASI-14 |
| Incidencias | `GET /tipos-incidencia` · `POST /incidencias` · `POST /incidencias/parciales` (AC) | CU-INC-01 · 03 · 07 |
| Convenios | `GET /me/convenios-pendientes` (PROP) · `POST /convenios/:id/aceptacion` · `/rechazo` (AC) | CU-MOV-22 · 02 · 03 |
| Pases de salida | `GET /pases-salida` · `POST /pases-salida/:id/salida` · `/regreso` · `/cierre` (AC) | CU-MOV-14 a 17 |
| Cobertura | `GET /necesidades-cobertura` · `POST /asignaciones/:id/incorporaciones` · `/rechazos` (AC) | CU-COB-04 · 10 · 11 |

Según el roadmap del backend (`docs/ejecucion/ROADMAP_BLOQUES.md`), Android empieza por la lectura
del pase. La sincronización persistente llega en el bloque **B7** y requiere antes **V2-INBOX**
(idempotencia persistida). Hasta entonces `ColaComandos` y `FakeSyncRepository` se mantienen.

## 4. Decisiones abiertas

- Facultades exactas del encargado (spec §16, decisión 2). Hoy: rol real → `Facultades.para(rol)`.
- Registrar la salida efectiva (CU-MOV-14) se trata como acción en línea.
- "Incapacidad" en la cola local: se guarda solo el tipo, sin datos clínicos.
