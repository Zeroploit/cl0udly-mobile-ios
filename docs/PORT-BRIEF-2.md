# Brief 2 — port final (iOS / Compose Multiplatform)

Documento **obligatorio** para los 8 subagentes de esta ronda. Léelo entero antes
de tocar nada. Complementa `docs/PORT-BRIEF.md` (sigue vigente: tabla de APIs,
componentes existentes, iconos, estilo).

## Regla de oro

Mismo producto que Android, **píxel por píxel**: se copia el fuente Android casi
literal. No se rediseña, no se "mejora", no se resume, no se cambia el copy ni
los tiempos de animación. Lo único que cambia es lo que Android tiene y
commonMain/iOS no.

Si el fuente Android hace algo con una API de Android que iOS no tiene, se busca
el equivalente **nativo de iOS** (UIKit/CoreMotion/AVFoundation/UserNotifications).
Solo si de verdad no existe se deja inerte, y se explica por qué en un comentario.

## Propiedad de archivos (para no pisarse)

Estos los cablea **solo el agente principal**. Ningún subagente los edita:

- `ui/Root.kt`, `ui/Nav.kt`, `Graph.kt`, `data/Prefs.kt`
- `ui/screens/DrawerContent.kt`, `ui/screens/SideMenu.kt`
- `ui/theme/*`
- `ui/components/Components.kt`

Cada subagente tiene asignados **archivos exclusivos** (ver su prompt). Puede
crear archivos nuevos con nombres propios. **Nunca** edita un archivo asignado a
otro subagente ni los de la lista de arriba: en esos casos deja
`// TODO(lead): ...` en el archivo que sí está escribiendo y sigue.

Si necesitas un campo nuevo en `Prefs.kt`, **no lo agregues**: escribe
`// TODO(lead): prefs.<campo>` y usa una constante local mientras tanto.

## Seams nuevos de esta ronda (los define el dueño indicado, nadie más)

Cada `expect` va en `shared/src/commonMain/kotlin/app/ocmobile/platform/` y su
`actual` en `iosMain`, `androidMain` y `jvmMain` (los dos últimos, stubs vacíos).
Un `actual` de stub debe ser **silencioso y sin crash**.

| Seam | Firma exacta | Dueño |
|---|---|---|
| `PlatformNotify` | `expect object PlatformNotify { fun requestPermission(); fun show(id: String, title: String, body: String, route: String?); fun cancel(id: String); fun cancelAll(); fun authorized(): Boolean }` | SA2 |
| `PlatformLifecycle` | `expect object PlatformLifecycle { fun observe(onChange: (foreground: Boolean) -> Unit) }` | SA2 |
| `PlatformVoice` | `expect object PlatformVoice { class V(val id: String, val name: String, val lang: String, val quality: Int); fun voices(): List<V>; fun chosen(): String; fun choose(id: String) }` | SA6 |
| `PlatformSensors` | `expect object PlatformSensors { fun start(onTilt: (Float) -> Unit, onShake: () -> Unit); fun stop() }` | SA7 |
| `PlatformBattery` | `expect object PlatformBattery { fun observe(onChange: (plugged: Boolean, low: Boolean) -> Unit) }` | SA7 |
| `scanQrCode` | `expect fun scanQrCode(onResult: (String?) -> Unit)` (null = cancelado) | SA4 |
| `openAuthSession` | `expect fun openAuthSession(url: String, callbackScheme: String, onResult: (String?) -> Unit)` | SA5 |
| `handleOpenUrl` | `fun handleOpenUrl(url: String): Boolean` (NO es expect: es commonMain, lo llama Swift) | SA4 |

`route` en `PlatformNotify.show` es el string que `Graph.chats.openRequest` ya
entiende (`"route:code"`, o un id de sesión a secas).

## Cómo se escribe el código

- Comentarios **en inglés**, frases cortas, explican el *por qué*.
- Sin emojis, sin `println`, sin `TODO()` sin implementar, sin `!!`.
- `internal` para lo del módulo, `private` para lo del archivo.
- KDoc de 1–2 líneas en cada función pública.
- Indentación 4 espacios, líneas ~120 columnas.
- Nada de `System.currentTimeMillis()` (usar `nowMillis()`), `String.format`
  (usar `Fmt.fixed/money/percent`), `java.*`, `android.*`.

## Verificación obligatoria antes de devolver

```
.\gradlew.bat :shared:compileKotlinJvm --configure-on-demand -q
```

Sin salida = verde. Si falla por un archivo de **otro** subagente, vuelve a
correrlo; no edites archivos ajenos. Si falla por el tuyo, arréglalo.

Para lo que toca iOS de verdad (`iosMain`, Swift, `Info.plist`, `pbxproj`) el
compilador local **no sirve**: revisa a mano los imports de `platform.*` y las
firmas de Kotlin/Native. Reglas ya verificadas en este repo:

- `@ObjCSignatureOverride` en **cada** miembro de un set de overloads que choca.
- `NSOperatingSystemVersion` es `CValue` (no tiene `.majorVersion`): usa
  `UIDevice.currentDevice.systemVersion`.
- **`CGRectZero` ya es un `CValue<CGRect>`: se pasa tal cual, sin `.readValue()`.**
  `.readValue()` sobre él rompe la inferencia de `UIView`/`UILabel` y produce una
  cascada de `'val' cannot be reassigned` en líneas que no tienen nada que ver.
  Si necesitas un rect vacío propio: `CGRectMake(0.0, 0.0, 0.0, 0.0)`.
- **Constantes de `NS_ENUM`: dos formas, y solo una compila según el tipo.**
  - Con prefijo en el miembro: `UIDeviceBatteryState.UIDeviceBatteryStateCharging`,
    `UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera`,
    `AVSpeechBoundary.AVSpeechBoundaryImmediate`. Verificado compilando.
  - Import de nivel superior, **sin** tipo: `NSTextAlignmentCenter`
    (`import platform.UIKit.NSTextAlignmentCenter`).
    `NSTextAlignment.NSTextAlignmentCenter` **no existe**; tampoco
    `NSTextAlignment.Center`. Verificado compilando.
- `AVPlayer(uRL=)` no acepta nullable.
- `AVCaptureSession.isRunning()` y `CMMotionManager.isAccelerometerAvailable()`
  son **métodos** en el binding, no propiedades (`session.isRunning`,
  `motion.accelerometerAvailable` no compilan).
- `startRunning()`/`stopRunning()` bloquean: fuera del hilo principal
  (`dispatch_get_global_queue`), nunca en `viewDidAppear`.
- Delegates de `UITextField`/pickers se guardan **débilmente**: hay que
  retenerlos en una variable de módulo (ver `Media.ios.kt`).
- Los callbacks de frameworks van a un hilo cualquiera: salta a main con el
  helper `onMain` de `IosSupport.kt`.
- Nada de APIs iOS 16+ sin `if #available`/`available` (deployment target 14.1).
- `NSURL.fileURLWithPath` es método de clase.
- Para `NSData` → `ByteArray`: `data.bytes?.readBytes(data.length.toInt())`.

## Qué devolver

1. Archivos creados y archivos editados (rutas completas).
2. Qué falta cablear en `Root.kt`/`Graph.kt` (imports, llamadas, orden).
3. Cada `TODO(lead)` que dejaste.
4. Cada sitio donde el comportamiento iOS difiere del Android y por qué.
5. Confirmación de que `:shared:compileKotlinJvm` quedó verde.
