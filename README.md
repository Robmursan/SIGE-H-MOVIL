# SIGE-H Móvil · Pase de lista del personal de enfermería

App Android de **SIGE-H** (Sistema Integral de Gestión de Enfermería Hospitalaria) para que la jefatura de
servicio registre la asistencia del personal de enfermería **en línea**, contra el backend real.

Forma parte de una plataforma con tres repositorios:

| Repositorio | Contenido |
|---|---|
| `Robmursan/SIGE-H-MOVIL` (este) | App Android |
| `Robmursan/SIGE-H-WEB` | Backend (NestJS, PostgreSQL) y el contrato OpenAPI canónico `artifacts/openapi/sigeh-v1.json` |
| `Robmursan/SIGE-H-Fontend` | Portal web |

---

## ✅ Qué hace hoy (hito M2: MVP en línea)

- **Inicio de sesión con IAM V2.** Tokens cifrados con Android Keystore, renovación automática y cierre de sesión.
  El pie muestra la versión del contrato del servidor.
- **Elegir servicio.** Solo ofrece los contextos propios vigentes que el servidor informa (`/auth/me/contextos`),
  con nombres de hospital y servicio, zona horaria y fecha de término de los encargos, y con permiso para capturar
  o consultar asistencia. Si esa ruta no está disponible, usa las asignaciones de `/auth/me` («Servicio ··· c91e»).
- **Pase de lista del día** en la zona horaria del hospital, con filtros por turno.
  - Cada jornada se registra **una sola vez** como Presente, Ausente o Parcial, con **confirmación previa** y
    **acuse del servidor**.
  - **Nada se marca por defecto:** cada jornada requiere una acción explícita.
  - **El servidor decide** qué jornadas se pueden capturar y por qué no.
  - Reintentar no duplica registros: cada intención lleva una `Idempotency-Key`.
  - **«Ausente» no es falta.** Es una observación operativa pendiente de validación administrativa y no genera
    incidencia.
- **Detalle de jornada y acuse del servidor.** Las correcciones y la validación las hace Coordinación, no la app.
- **Programación semanal del servicio**, solo si la sesión tiene el permiso.
- **Cuenta.** Sesiones abiertas (se pueden cerrar las de otros dispositivos) y cierre de sesión.
- **Modo demostración (Fake)** con datos ficticios que se comportan como el backend.

**Sin conexión no se registra nada:** los botones de captura se desactivan y se muestra un aviso.

## 🔜 Qué todavía no hace (futuro)

Estas funciones **no existen todavía** en el backend. Su código de prueba (M0) está oculto detrás de
`sigeh.features.futuras`:

- Operación sin conexión con cola local y sincronización en segundo plano (bloque B7 del backend).
- Lectura de credenciales por QR.
- Suplencias, cobertura y recepción de personal eventual.
- Incidencias, movimientos y pases de salida.
- Corregir una captura, validar o enviar el pase completo.

---

## 🧰 Stack

- Kotlin 2.4, Jetpack Compose (Material 3) y Navigation Compose con destinos tipados.
- Hilt, Retrofit 3 + OkHttp 5, kotlinx.serialization y DataStore.
- Room y WorkManager, solo para la cola offline futura.
- `minSdk 24` · `compileSdk/targetSdk 37` · paquete `mx.sigeh.app`.
- Sistema de diseño propio con la paleta del portal web (`core/designsystem`).

## ▶️ Cómo ejecutar

Sin configuración, la app corre en **modo demostración**: entra con cualquier identificador y contraseña.

```powershell
.\gradlew.bat installDebug
```

Para usar el backend real, agrega estas claves en `local.properties` (no se versiona):

```properties
sigeh.api.baseUrl=http://10.0.2.2:3000/api/v1   # emulador; en teléfono: adb reverse tcp:3000 tcp:3000
sigeh.api.iam=real
sigeh.api.asistencia=real
sigeh.features.futuras=false
```

Requisitos del backend y combinaciones de interruptores: [`docs/mobile/contrato.md`](docs/mobile/contrato.md).

Comandos de verificación:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest   # con un emulador o teléfono conectado
```

## 📚 Documentación

- [`docs/mobile/contrato.md`](docs/mobile/contrato.md): rutas integradas, permisos, errores, brechas del
  backend y lo que sigue pendiente.
- [`docs/mobile/M2-pase-lista-online.md`](docs/mobile/M2-pase-lista-online.md): alcance de M2, decisiones,
  pruebas y cómo reproducir.
- [`docs/mobile/contrato/sigeh-v1.json`](docs/mobile/contrato/sigeh-v1.json): copia verificada del contrato
  (SHA-256 `a6fbe480…`, 49 operaciones; el móvil usa 12).
- [`docs/mobile/M3-contrato-49-y-QA.md`](docs/mobile/M3-contrato-49-y-QA.md): qué cambió con el contrato de 49
  operaciones, el backend simulado y el plan de integración con QA.

## 🗺️ Hitos

| Hito | Estado | Contenido |
|---|---|---|
| M0 | ✅ | App completa en modo Fake, cola offline y sistema de diseño |
| M1 | ✅ | IAM V2 real detrás de interruptor |
| M2 | ✅ | Pase de lista en línea contra el contrato `sigeh-v1`, pantallas rediseñadas y funciones sin backend ocultas |
| M3 | 🟡 | Contrato de 49 operaciones (`a6fbe480…`): `GET /auth/me/contextos` integrado y backend simulado HTTP para probar sin QA. Falta la prueba en QA efímero |
| Siguiente | — | Prueba en un dispositivo y contra el backend de QA con las identidades sintéticas; captura sin conexión cuando exista el bloque B7 |

---

## 🤝 Cómo contribuir

- Ramas con prefijo y nombre en kebab-case (`feat/…`, `fix/…`), creadas desde `feat/david-hernandez`.
- Commits `tipo(alcance): descripción` en español (`feat(m2): …`, `test(m2): …`, `docs(m2): …`).
- Solo datos ficticios: nunca credenciales, `.env` ni datos de personas reales.

Plantilla de PR:

```markdown
## 📋 Descripción del Cambio
**Módulo:** Ausentismo / Cobertura / Camas / Ocupación
**Tipo de cambio:** [ ] Feature [ ] Bugfix [ ] Hotfix [ ] Refactor
**Resumen:**

## 🔍 Lista de Cotejo (Checklist del Desarrollador)
- [ ] La rama cumple con la nomenclatura (prefijo/nombre-kebab-case).
- [ ] No se incluyeron credenciales, variables .env ni datos sensibles de pacientes.
- [ ] El código compila localmente sin advertencias ni errores de TypeScript/linter.
- [ ] Se verificó el flujo visual en dispositivos de escritorio y móviles.

## 👥 Criterios para el Revisor (Code Review)
- [ ] La lógica del cálculo (complejidad/ocupación) arroja resultados consistentes.
- [ ] Las consultas y llamadas a endpoints son eficientes.
- [ ] Se resolvieron todas las conversaciones y observaciones abiertas en este PR.
```
