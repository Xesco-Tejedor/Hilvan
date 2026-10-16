#!/usr/bin/env bash
# Prueba en un emulador Android real (API 33) de Hilvan: arranque y render de la web dentro de la app,
# puente nativo (dictado, lectura TTS, exportar a Descargas, imprimir/PDF), persistencia local,
# comportamiento sin conexion y humo de la APK firmada publicada.
set -u
PKG=es.xescotejedor.hilvan
EV=${EVIDENCE:-evidence}; mkdir -p "$EV"
DBG=$1; REL=$2
log(){ echo "[$(date +%H:%M:%S)] $*" | tee -a "$EV/e2e-log.txt"; }
node_e2e(){ MODE=$1 EVIDENCE=$EV MARK=${2:-} timeout 300 node android/e2e.mjs 2>&1 | tee -a "$EV/e2e-log.txt" > /tmp/last_e2e.txt; }
razon_muerte(){ adb logcat -d | grep -E "Killing [0-9]+:$PKG|FATAL EXCEPTION" | tail -3 | tee -a "$EV/e2e-log.txt"; }
start_app(){ for try in 1 2 3; do adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1; sleep 10
  PID=$(adb shell pidof $PKG | tr -d '\r'); log "pid=$PID"; [ -n "$PID" ] && break; log "la app no arranco (intento $try)"; razon_muerte; done
  adb forward tcp:9222 localabstract:webview_devtools_remote_$PID; }

adb wait-for-device; adb shell 'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 2; done'
adb shell settings put global window_animation_scale 0; adb shell settings put global transition_animation_scale 0
log "Dispositivo: $(adb shell getprop ro.product.model) Android $(adb shell getprop ro.build.version.release) API $(adb shell getprop ro.build.version.sdk)"
log "WebView: $(adb shell dumpsys webviewupdate | grep -m1 -i 'current webview package')"
log "Motores TTS instalados: $(adb shell pm list packages | grep -i tts | tr '\n' ' ')"
adb logcat -c; adb logcat -v time > "$EV/logcat-completo.txt" 2>&1 &
LOGPID=$!

log "== Red del emulador: esperando conectividad"
OKR=0; for i in $(seq 1 10); do adb shell ping -c1 -W2 8.8.8.8 >/dev/null 2>&1 && { OKR=1; break; }; sleep 3; done
log "red emulador: $([ $OKR = 1 ] && echo OK || echo SIN-RED)"

log "== Instalo la APK de depuracion (mismo codigo que la release) y concedo microfono"
adb install -r "$DBG" | tee -a "$EV/e2e-log.txt"
log "versionCode: $(adb shell dumpsys package $PKG | grep -m1 versionCode)"
adb shell pm grant $PKG android.permission.RECORD_AUDIO
log "permiso: $(adb shell dumpsys package $PKG | grep -m1 'RECORD_AUDIO: granted')"
start_app; sleep 8
adb exec-out screencap -p > "$EV/01-inicio.png"
node_e2e main
if grep -qE 'CONEXION DE DEPURACION CERRADA|fetch failed|SIN PAGINA' /tmp/last_e2e.txt; then
  log "REINTENTO: la app murio durante la fase principal. Causa:"; razon_muerte
  start_app; sleep 8; node_e2e main
fi
log "proceso vivo tras dictado/TTS: $(adb shell pidof $PKG | tr -d '\r')"

log "== Exportar a Descargas e imprimir (vista de impresion del sistema)"
node_e2e exportprint
adb exec-out screencap -p > "$EV/06-impresion.png"
log "foco tras imprimir: $(adb shell dumpsys window | grep -m1 mCurrentFocus)"
adb shell input keyevent KEYCODE_BACK; sleep 2
log "exportacion: $(adb shell ls /sdcard/Download/ 2>/dev/null | tr '\n' ' ' | grep -o 'hilvan-e2e-prueba.txt' || echo NO-ESTA)"
log "exportacion contenido: $(adb shell cat /sdcard/Download/hilvan-e2e-prueba.txt 2>/dev/null)"

log "== Persistencia: marcador, cierre completo y reapertura"
node_e2e set "$(date +%s)"
adb shell am force-stop $PKG; sleep 2
start_app; sleep 8
node_e2e get

log "== Sin conexion CON cache (modo avion): la web debe abrir desde la copia guardada"
adb shell cmd connectivity airplane-mode enable; adb shell svc wifi disable; adb shell svc data disable; sleep 4
adb shell am force-stop $PKG; start_app; sleep 9
adb exec-out screencap -p > "$EV/11-sin-red-cache.png"
node_e2e offline
if grep -qE 'CONEXION DE DEPURACION CERRADA|fetch failed|SIN PAGINA' /tmp/last_e2e.txt; then
  log "REINTENTO offline: CDP no listo"; start_app; sleep 8; node_e2e offline
fi
adb exec-out screencap -p > "$EV/11b-sin-red-cache.png"
adb shell cmd connectivity airplane-mode disable; adb shell svc wifi enable; adb shell svc data enable; sleep 5

log "== Sin conexion SIN cache (pm clear, como una primera instalacion offline): vista de error con Reintentar"
adb shell pm clear $PKG >/dev/null
adb shell cmd connectivity airplane-mode enable; adb shell svc wifi disable; adb shell svc data disable; sleep 4
adb shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1
FOUND=0
for i in 1 2 3 4 5 6; do
  sleep 12
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$EV/ui-sin-red.xml" 2>/dev/null
  FOUND=$(grep -c 'REINTENTAR' "$EV/ui-sin-red.xml" 2>/dev/null || echo 0)
  [ "$FOUND" -ge 1 ] && break
done
adb exec-out screencap -p > "$EV/12-sin-red-error.png"
log "sin red sin cache: vista de error visible (REINTENTAR): $FOUND"
adb shell cmd connectivity airplane-mode disable; adb shell svc wifi enable; adb shell svc data enable; sleep 5
log "== Red recuperada: reabre y carga"
adb shell am force-stop $PKG; start_app; sleep 8
node_e2e online

adb uninstall $PKG >/dev/null
log "== Humo de la APK firmada publicada (release android-latest)"
adb logcat -c
adb install --no-incremental -r "$REL" | tee -a "$EV/e2e-log.txt"
log "versionCode release instalado: $(adb shell dumpsys package $PKG | grep -m1 versionCode)"
adb shell am start -W -n $PKG/.MainActivity | tee -a "$EV/e2e-log.txt"; sleep 12
RP=$(adb shell pidof $PKG | tr -d '\r'); log "pid release=$RP"
if [ -z "$RP" ]; then
  log "PRIMER ARRANQUE DE LA RELEASE FALLO (sin proceso). Causa en logcat-completo.txt. Reintento una vez."
  adb logcat -d | grep -E "FATAL|ANR in|am_crash|Force finishing|AndroidRuntime" | tail -20 | tee -a "$EV/e2e-log.txt"
  adb shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1; sleep 12
  RP=$(adb shell pidof $PKG | tr -d '\r'); log "pid release tras reintento=$RP"
fi
adb logcat -d -s AndroidRuntime:E | tail -20 | tee -a "$EV/e2e-log.txt"
adb exec-out screencap -p > "$EV/10-release-inicio.png"
adb shell dumpsys window | grep -m1 mCurrentFocus | tee -a "$EV/e2e-log.txt"
kill $LOGPID 2>/dev/null
log "excepciones JS en pagina: $(grep -c '\] EXC' "$EV/e2e-log.txt" || true)"

chk0(){ :; }
V=PASS; why=""
chk(){ grep -qE "$1" "$EV/e2e-log.txt" || { V=FAIL; why="$why | falta: $2"; }; }
chk 'red emulador: OK' 'conectividad inicial del emulador'
chk 'Dispositivo: .* API 33' 'emulador API 33'
chk 'RECORD_AUDIO: granted=true' 'permiso microfono concedido'
chk 'puente dictar/leer/guardar/imprimir \["function","function","function","function","function","function"\]' 'puente nativo completo'
chk 'boton dictar presente true' 'UI de voz visible en la app'
chk 'tts: callback fin de lectura true' 'lectura TTS completa el ciclo'
chk 'dictado: ciclo termina sin audio.*true' 'dictado termina el ciclo sin caerse'
chk 'proceso vivo tras dictado/TTS: [0-9]+' 'app viva tras dictado y TTS'
chk 'exportacion: hilvan-e2e-prueba.txt' 'archivo exportado en Descargas'
chk 'exportacion contenido: contenido e2e Hilv' 'contenido exportado correcto'
chk 'foco tras imprimir: .*[Pp]rint' 'vista de impresion del sistema abierta'
chk 'persistencia: marcador tras reinicio sobrevive-' 'persistencia tras cierre completo'
chk 'sin red: app renderiza desde cache true' 'sin conexion: abre desde cache'
chk 'sin red sin cache: vista de error visible \(REINTENTAR\): [1-9]' 'sin conexion sin cache: vista REINTENTAR'
chk 'red recuperada: titulo Hilv' 'recarga al volver la red'
chk 'pid release(tras reintento)?=[0-9]+' 'release firmada publicada abre'
chk 'excepciones JS en pagina: 0$' 'sin excepciones de script en la pagina'
echo "$V $why" > "$EV/veredicto.txt"; log "VEREDICTO: $V $why"
