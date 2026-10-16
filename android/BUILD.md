# Hilván para Android: cómo compilar

App nativa ligera (Java + WebView, sin androidx ni dependencias de terceros) que abre la web
publicada de Hilván (https://xesco-tejedor.github.io/Hilvan/) con puentes nativos:
dictado por micrófono (SpeechRecognizer), lectura en voz alta por fragmentos (TextToSpeech con
tono, velocidad y pausas), exportación a Descargas (MediaStore) e impresión/PDF (PrintManager).

## Requisitos

- JDK 17 (Temurin recomendado)
- Android SDK con `platforms;android-36` y `build-tools` (la SDK lo localiza por `ANDROID_HOME`
  o por un `local.properties` con `sdk.dir=...`, que no se incluye en el repo)
- Gradle 8.11.1
- AGP 8.9.1 (declarado en `build.gradle` raíz)

## Compilar

    cd android
    gradle --no-daemon assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
    gradle --no-daemon assembleRelease    # sin keystore: app-release-unsigned.apk

## Versión y firma de la release publicada

- `versionCode 3`, `versionName "1.2"`, `minSdk 24`, `targetSdk 34`, `compileSdk 36`
- Paquete: `es.xescotejedor.hilvan`
- La APK de la release `android-latest` se firmó localmente con un keystore privado (alias `hilvan`).
- Huella SHA-256 del certificado de firma:
  `93d5cc90f022a42f0ee528265b26b36a6ca0797e2cef331d5642e5f7c05e0a43`
- El keystore y sus contraseñas NO están en este repositorio: se custodian fuera de él
  (ver `.gitignore`). Para firmar una actualización compatible, coloca el keystore como
  `android/hilvan.keystore` y un `android/keystore.properties` con `storePassword=...` y
  `keyPassword=...`. Sin ese keystore, `assembleRelease` genera una APK sin firmar; firmarla
  con otra clave cambia la firma y Android exigiría desinstalar la versión anterior.

## Pruebas en emulador (CI)

`.github/workflows/pruebas-android.yml` (ejecución manual) compila la APK de depuración desde
estas fuentes, descarga la APK firmada publicada y verifica su huella, y prueba ambas en
emuladores reales API 33 y API 36: arranque, render, puente nativo, permiso de micrófono,
lectura TTS, ciclo de dictado, exportación a Descargas, impresión, persistencia local,
comportamiento sin conexión y humo de la release. Scripts: `e2e.sh`, `e2e.mjs`, `e2e36.sh`.
Las evidencias (capturas, logcat, veredictos) se publican en la prerelease `android-pruebas`.

## Estructura

- `app/src/main/java/es/xescotejedor/hilvan/MainActivity.java`: toda la app (WebView, puentes,
  permisos en tiempo de uso, vista de error sin conexión, barra de progreso).
- `app/src/main/res/mipmap-*`: iconos (adaptativos y clásicos) regenerados el 2026-10-16 desde el nuevo emblema aportado por el autor (dibujo completo conservado; fondo crema #f6f2e1).
- `app/src/main/AndroidManifest.xml`: permisos INTERNET, RECORD_AUDIO y WRITE_EXTERNAL_STORAGE
  (este último solo hasta API 28; desde API 29 las exportaciones usan MediaStore).
