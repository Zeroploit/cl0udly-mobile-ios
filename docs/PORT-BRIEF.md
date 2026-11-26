# Brief para el port de las pantallas que faltan (iOS / Compose Multiplatform)

Documento de referencia **obligatorio** para todo subagente que porte código de
`app/src/main/java/app/ocmobile/` a `shared/src/commonMain/kotlin/app/ocmobile/`.
Léelo entero antes de tocar nada.

## Regla de oro

El objetivo es **el mismo producto, píxel por píxel**: se copia el fuente Android
casi literal. No se rediseña, no se "mejora", no se resume, no se cambia el copy.
Lo único que cambia es lo que Android tiene y commonMain no.

## Qué NO tocar (para no pisarse entre subagentes)

Ningún subagente edita estos archivos; los cablea el agente principal al final:

- `shared/src/commonMain/kotlin/app/ocmobile/ui/Root.kt`
- `shared/src/commonMain/kotlin/app/ocmobile/ui/Nav.kt`
- `shared/src/commonMain/kotlin/app/ocmobile/Graph.kt`
- `shared/src/commonMain/kotlin/app/ocmobile/data/Prefs.kt`
- `shared/src/commonMain/kotlin/app/ocmobile/ui/screens/DrawerContent.kt`
- `shared/src/commonMain/kotlin/app/ocmobile/ui/screens/SideMenu.kt`
- `shared/src/commonMain/kotlin/app/ocmobile/ui/theme/*`
- `shared/src/commonMain/kotlin/app/ocmobile/ui/components/Components.kt`
- `shared/src/commonMain/kotlin/app/ocmobile/ui/components/CloudUi.kt`
- cualquier `expect` ya existente en `platform/`

Si necesitás una función nueva en uno de esos, **no la agregues**: dejá un comentario
`// TODO(lead): ...` en el archivo que estás escribiendo y seguí.

Cada subagente crea **solo archivos nuevos** con su propio nombre, más como mucho
los archivos de `data/` que se le asignen explícitamente.

## APIs de Android que no existen y su reemplazo

| Android | En commonMain |
|---|---|
| `Context`, `Activity`, `Intent`, `Uri` | no hay. Usar `PlatformMedia` o quitar la acción |
| `LocalContext.current` | no existe: borrar el import y la variable |
| `rememberLauncherForActivityResult` | `PlatformMedia.pickImages/pickFiles/takePhoto` |
| `AndroidView(...)` | `PlatformHtmlView` / `PlatformBrowser` (ya existen) |
| `WebView`, `CookieManager` | no hay: la pantalla usa `PlatformBrowser` o se omite |
| `SharedPreferences` | `Graph.prefs.<campo>` (ya existe) |
| `java.io.File` | no hay. Para leer/escribir archivos remotos usar `Graph.api` |
| `java.util.Calendar` / `Date` / `SimpleDateFormat` | `app.ocmobile.platform.nowMillis()`, `hhmm()`, `englishDate()`, `localMidnight()`, `zoneOffsetMinutes()` (están en `ui/screens/Messages.kt`, son `internal`) |
| `String.format("%.2f", x)` | `app.ocmobile.ui.components.Fmt.fixed(x, 2)` / `Fmt.money` / `Fmt.tokens` / `Fmt.percent` |
| `System.currentTimeMillis()` | `nowMillis()` |
| `Math.PI`, `Math.abs` | `kotlin.math.*` |
| `System.identityHashCode(x)` | usar el propio objeto como clave, o un contador |
| `@Volatile` | `@kotlin.concurrent.Volatile` |
| `putIfAbsent` | `getOrPut` |
| `ClipboardManager` de Android | `LocalClipboardManager.current.setText(AnnotatedString(t))` |
| `NotificationManager`, `AlarmManager`, `PendingIntent`, `BroadcastReceiver` | no hay: esas pantallas se omiten o el toggle queda inerte |
| `Activity.RESULT_*`, `startActivityForResult` | no hay |
| `Build.VERSION`, `Manifest.permission` | no hay |
| `SQLiteDatabase` | no hay |
| `Toast` | `Graph.chats.toasts.tryEmit("...")` |
| `BuildConfig.VERSION_NAME/CODE` | `app.ocmobile.platform.appVersion()` |
| `Build.MANUFACTURER/MODEL` | `app.ocmobile.platform.deviceLabel()` |
| `AppIcons.apply(ctx, id)` | `AppIcons.apply(id)` (sin contexto) |
| `AppIcon(...).fgRes` / `painterResource` | no hay drawable: `AppIcon` solo tiene `id`, `label`, `bg` |
| `R.drawable.*` | no hay: usar `Ic.*` o dibujar con Canvas |
| `Graph.paths.host(...)` / `Graph.paths.localSupported` | no hay: el servidor es remoto, usar `Graph.api` |
| `Graph.ocUpdater` | no hay (iOS no se auto-actualiza): la fila queda informativa |
| `coil3.compose.AsyncImage` | sí existe (está en commonMain) |

## Componentes que ya existen (usarlos, no reescribirlos)

De `app.ocmobile.ui.components`:
`Txt`, `Heading`, `Ico`, `IconBtn`, `TopBar`, `Pill`, `SmallButton`, `OcSwitch`,
`SectionLabel`, `SettingsGroup`, `SettingsRow`, `Card`, `Logo`, `Spinner`,
`PulsingLogo`, `UsageBar`, `Chip`, `OcTextField`, `OcSheet`, `OcDialog`,
`ChoiceRow`, `Segmented`, `Skeleton`, `Monogram`, `EmptyState`, `Fmt`,
`FloatMenu`, `PickerFrame`, `PickHeader`, `PickSearch`, `PickRow`,
`FileViewerHost`, `ViewSrc`, `FileView`, `trackTouches`, `ModelSheet`,
`CodeBlock`, `DiffView`, `MenuBtn`, `Avatar`,
`Modifier.tap`, `Modifier.longPress`, `copiedNote`, `rememberHaptic`.

De `app.ocmobile.ui.theme`: `Oc` (`Oc.c.bg/surface/surfaceHigh/text/textDim/border/accent/link/toggleOn/progressTrack/progressFill/danger/ok/warn/userBubble/codeBg/scrim`, `Oc.round(dp)`, `Oc.pill`, `Oc.look`, `Oc.heading`, `Oc.body`, `Oc.mono`), `Ic` (iconos: ver lista abajo), `AppIcons`, `AppIcon`, `Looks`, `screenBackdrop(variant)`, `String.kimiLike`.

De `app.ocmobile`: `Graph.prefs`, `Graph.chats`, `Graph.catalog`, `Graph.api`,
`Graph.events`, `Graph.usage`, `Graph.devices`, `Graph.runtime`, `Graph.scope`.

De `app.ocmobile.ui`: `Nav` (`push`, `pop`, `root`, `openChat`, `current`, `stack`),
`Route`.

De `app.ocmobile.platform`: `PlatformMedia` (`shareFile`, `saveFile`, `shareText`,
`openUrl`, `pickImages`, `takePhoto`, `pickFiles`, `playPcm`, `speak`,
`stopSpeaking`, `speaking`), `nowMillis()`, `rememberMotionEnabled()`,
`appVersion()`, `deviceLabel()`.

De `app.ocmobile.runtime`: `RuntimeState` y `RemoteRuntime` (la instancia es
`Graph.runtime`). En iOS `RuntimeState.Running` significa "el servidor responde";
`install/startServer/stopServer/resetRuntime` son no-ops.

De `app.ocmobile.data`: `Memory.userRules()`, `Memory.saveUserRules(text)`,
`Graph.usage.snapshot` (`UsageSnapshot` con `fiveHour/week/month/byModel/
totalMonth/tracked/daily`), `Graph.usage.refreshing`, `Graph.usage.refresh()`.
En `ui/screens/Messages.kt` (todo `internal`): `isLimitError(name, msg)`,
`costLabel(ref)`, `nextFreeModel()`, `personaOn()`, `humanSize(b)`.

Iconos disponibles en `Ic`:
Archive, ArrowUp, Back, Bell, Bot, Branch, Camera, Chart, Chat, Check, ChevronDown,
ChevronRight, Clock, Clock2, Close, Cloud, Code, Copy, Cpu, Diff, Dollar, Download,
Drop, Edit, Expand, External, Eye, FastForward, File, FilePlus, FileUp, Folder,
FontSize, Ghost, Git, GitHub, Globe, IBeam, Image, Info, Key, Laptop, ListCheck,
Mail, Menu, Mic, Mic2, MicOff, Moon, More, Music, Palette, Pause, Phone, Pin,
Play, Plus, Puzzle, Refresh, Reply, Scroll, Search, Server, Share, Shield,
Sliders, Sparkle, Stop, Sun, Terminal, TextSelect, Trash, Undo, User, Users,
Vibrate, Video, Volume, VolumeOff, Warning, Waveform, Wrench, Zap.

Si falta un icono: usar el más parecido de la lista y dejar
`// TODO(lead): icono <nombre> no existe en Ic`.

## Convenciones de estilo del repo

- Comentarios **en inglés**, frases cortas, explican el *por qué*, no el *qué*.
- Sin emojis. Sin `!!` salvo imposibilidad real.
- `internal` para lo que solo usa el módulo; `private` para lo de un archivo.
- KDoc de una o dos líneas en cada función pública, como en el resto del repo.
- Nada de `System.out`, `println`, `TODO()` sin implementar.
- Indentación 4 espacios, líneas ~120 columnas.

## Verificación antes de devolver

Desde la raíz del repo:

```
.\gradlew.bat :shared:compileKotlinJvm --configure-on-demand -q
```

Debe salir **sin salida** (exit 0). Si falla, arreglá el error: no devuelvas código
que no compila. No toques `:app` ni los tests.

Si el build falla por un archivo de **otro** subagente (se están escribiendo en
paralelo), volvé a correrlo: no edites archivos ajenos.

## Qué devolver al terminar

Un mensaje corto con:
1. Archivos creados (rutas completas).
2. Qué falta cablear en `Root.kt` (rutas nuevas, imports) y qué `TODO(lead)` dejaste.
3. Cualquier decisión donde el comportamiento iOS difiere del Android y por qué.
