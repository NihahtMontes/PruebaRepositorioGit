# LockApp

App Android para uso personal que bloquea las aplicaciones que elijas (WhatsApp, Instagram, Galería…) con un **PIN propio para cada app** y, si quieres, con huella. Pensada para teléfonos Samsung con One UI y Android 13 o superior.

- Sin permiso de `INTERNET`, sin anuncios y sin telemetría.
- Los PIN nunca se guardan en texto plano: solo un hash PBKDF2-HMAC-SHA256 con sal aleatoria.
- Kotlin, Jetpack Compose, Material 3, DataStore. minSdk 26, targetSdk 34.

## Cómo funciona

| Elemento | Qué hace |
|---|---|
| **PIN maestro** | Se crea en el primer uso. Protege la propia LockApp (lista de apps y ajustes), para que nadie pueda quitar los bloqueos. |
| **PIN de cada app** | Al activar el interruptor de una app eliges su PIN (4 a 6 dígitos). Cada app tiene el suyo, distinto del maestro y del de las demás. |
| **Huella** | Interruptor global en Ajustes y otro por app ("Permitir huella"). Ten en cuenta que cualquier huella registrada en el teléfono abrirá esa app. |
| **Intentos fallidos** | Cada PIN tiene su propio contador. Tras 5 fallos se bloquea 30 s, luego 1 min, 5 min, 15 min y 30 min. Reiniciar el teléfono no anula la espera. |
| **Periodo de gracia** | 0 s, 30 s, 1 min o 5 min. Mientras sigues dentro de la app desbloqueada nunca se vuelve a pedir el PIN; el tiempo empieza a contar cuando sales de ella. |
| **Bloquear al apagar la pantalla** | Olvida todos los desbloqueos. Si la app abierta estaba bloqueada, deja la pantalla de PIN puesta para cuando vuelvas a encender. |
| **Pausar bloqueos** | Desactiva todos los bloqueos sin borrar los PIN. |
| **Fondo** | Color sólido, degradado o imagen de la galería (Photo Picker, sin permisos de almacenamiento). |

### Estructura del proyecto

```
app/src/main/java/com/nihaht/lockapp/
├── LockApp.kt, AppContainer.kt, MainActivity.kt
├── data/          DataStore (ajustes, apps bloqueadas, intentos), apps instaladas, imagen de fondo
├── security/      PinHasher, PinCredential, LockoutPolicy, PinVerifier, UnlockSessionManager, Authenticator
├── service/       AppLockAccessibilityService, KeepAliveService, BootReceiver, ExcludedPackages
├── lock/          LockActivity (pantalla que aparece sobre la app bloqueada)
├── permissions/   PermissionChecker
└── ui/            tema, teclado de PIN, lista de apps, editor por app, ajustes, fondo, asistente de permisos
app/src/test/      pruebas unitarias del PIN, intentos fallidos y periodo de gracia
```

## Abrir el proyecto en Android Studio

1. Instala [Android Studio](https://developer.android.com/studio) (Ladybug 2024.2 o más reciente).
2. **File > Open** y elige la carpeta de este repositorio.
3. Espera a que termine la sincronización de Gradle. La primera vez descarga Gradle 8.11.1, el Android Gradle Plugin 8.7.3 y las librerías.
4. Si pide instalar el SDK de Android 35 (compileSdk), acepta.

## Compilar el APK de depuración

Desde Android Studio: **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.

Desde una terminal en la carpeta del proyecto:

```bash
./gradlew assembleDebug          # en Windows: gradlew.bat assembleDebug
./gradlew testDebugUnitTest      # pruebas unitarias
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Instalar en el Samsung

**Opción A: cable USB (recomendada)**

1. En el teléfono: **Ajustes > Acerca del teléfono > Información de software** y toca 7 veces **Número de compilación** para activar las Opciones de desarrollador.
2. **Ajustes > Opciones de desarrollador > Depuración USB**: activar.
3. Conecta el cable y acepta la huella RSA del ordenador.
4. En Android Studio pulsa **Run ▶** con el teléfono seleccionado, o en la terminal:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

**Opción B: copiar el APK**

1. Copia `app-debug.apk` al teléfono (cable, Drive, Quick Share…).
2. Ábrelo con **Mis archivos** y permite instalar apps de esa fuente cuando lo pida.
3. Si aparece Play Protect, toca **Instalar de todas formas** (es tu propia app).

## Permisos y batería en One UI (pasos manuales)

La app tiene un asistente que muestra en tiempo real qué falta y abre cada ajuste. Estos son los pasos:

1. **Ajustes restringidos (solo APK instalados a mano, Android 13+).** Si al activar la accesibilidad sale "Ajuste restringido":
   **Ajustes > Aplicaciones > LockApp > ⋮ (arriba a la derecha) > Permitir ajustes restringidos**.
2. **Servicio de accesibilidad:** **Ajustes > Accesibilidad > Aplicaciones instaladas > LockApp > Activar**. Solo lee el nombre de la app que se abre, nunca el contenido de la pantalla.
3. **Batería sin restricciones:** **Ajustes > Aplicaciones > LockApp > Batería > Sin restricciones** (o acepta el diálogo que abre el asistente).
4. **Apps que nunca se suspenden:** **Ajustes > Batería > Límites de uso en segundo plano > Apps que nunca se suspenden > + > LockApp**.
5. **Mantener abierta en Recientes:** abre Recientes, toca el icono de LockApp encima de su miniatura y elige **Mantener abierta**.
6. **Notificaciones:** permítelas. La notificación es de prioridad mínima y sirve para que Android trate el servicio como activo.

"Mostrar sobre otras apps" **no** hace falta: la pantalla de bloqueo la abre el servicio de accesibilidad.

## Qué debes probar en el teléfono

Ninguna de estas cosas se puede comprobar sin un dispositivo real:

- [ ] **Primer uso:** crear el PIN maestro, completar el asistente y ver que cada permiso cambia a "Activo" al volver de Ajustes.
- [ ] **Bloqueo básico:** bloquear WhatsApp con un PIN, abrirlo desde el launcher, desde una notificación y desde Recientes. Debe pedir **su** PIN, no el maestro.
- [ ] **PIN distintos:** bloquear dos apps con PIN diferentes y comprobar que el PIN de una no abre la otra.
- [ ] **Parpadeo:** fíjate si se ve la app bloqueada una fracción de segundo antes del PIN. Depende de la velocidad del teléfono.
- [ ] **Botón/gesto Atrás** en la pantalla de PIN: debe ir al inicio, nunca a la app.
- [ ] **Recientes:** la pantalla de PIN no debe aparecer como tarjeta, y no se debe poder hacer captura de ella.
- [ ] **Huella:** con "Permitir huella" activo en una app, debe aparecer el diálogo de huella automáticamente.
- [ ] **Intentos fallidos:** 5 PIN incorrectos deben mostrar la cuenta atrás de 30 s, y reiniciar la app no debe saltársela.
- [ ] **Periodo de gracia:** con 30 s, sal de la app y vuelve antes y después de ese tiempo.
- [ ] **Apagar la pantalla** dentro de una app bloqueada: al encender y desbloquear el teléfono debe pedir el PIN.
- [ ] **Llamada entrante** mientras estás en una app bloqueada: la llamada debe verse sin pedir PIN.
- [ ] **Teclado y panel de notificaciones** dentro de una app desbloqueada: no deben volver a pedir el PIN.
- [ ] **Reinicio del teléfono:** tras reiniciar y desbloquear, las apps deben seguir bloqueadas (puede tardar unos segundos en arrancar el servicio).
- [ ] **Uso prolongado:** deja el teléfono varias horas y comprueba que sigue bloqueando (señal de que la batería no está matando el servicio).
- [ ] **Fondo:** probar color, degradado y una foto vertical tomada con la cámara (debe verse con la orientación correcta).

## Comportamientos que pueden variar en One UI

- **Desactivación de la accesibilidad:** algunas versiones de One UI desactivan el servicio tras actualizar la app o tras un cierre forzado. Si deja de bloquear, revisa el asistente de permisos.
- **Gestor de batería:** aunque esté "Sin restricciones", el modo de ahorro extremo o "Suspensión profunda" pueden detener el servicio.
- **Paquetes del sistema:** se excluyen el launcher, la interfaz de llamadas (`com.samsung.android.incallui`), el panel Edge, los teclados y los diálogos del sistema. Si alguna pantalla del sistema se queda bloqueada, añade su paquete en `ExcludedPackages.kt`.
- **Volver a la app tras desbloquear:** normalmente se vuelve a la app bloqueada; en algún caso One UI puede llevar al inicio en su lugar.
- **Miniatura en Recientes:** la miniatura de la app bloqueada (no la de LockApp) puede mostrar su último contenido. One UI no permite que otra app la oculte.
- **Huella:** solo se admite biometría "fuerte". El reconocimiento facial de Samsung se considera débil y no aparecerá.

## Limitaciones conocidas

- Si alguien desactiva el servicio de accesibilidad o desinstala LockApp, los bloqueos desaparecen. Para eso está el PIN maestro, pero los Ajustes del sistema solo quedan protegidos si también bloqueas la app **Ajustes**. LockApp la deja pasar durante 2 minutos cuando el asistente de permisos la abre.
- El periodo de gracia vive en memoria: si el sistema mata el proceso, todas las apps vuelven a pedir PIN (es lo más seguro).

## Mejoras futuras

- **Protección contra desinstalación** con Device Admin (`DevicePolicyManager`) para impedir desinstalar LockApp sin el PIN maestro.
- Publicación en Google Play (requiere justificar el uso de la API de accesibilidad).
- Copia de seguridad cifrada de la configuración.
