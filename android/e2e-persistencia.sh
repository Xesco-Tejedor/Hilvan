#!/usr/bin/env bash
# Verificacion enfocada: guardado duradero del relato (espejo nativo) tras force-stop.
set -u
PKG=es.xescotejedor.hilvan
APK="$1"
EV=evidence; mkdir -p "$EV"
LOG="$EV/e2e-persistencia-log.txt"; : > "$LOG"
log(){ echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG"; }
arranque(){ for try in 1 2 3; do adb shell am force-stop $PKG; adb shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1; sleep 10
  PID=$(adb shell pidof $PKG | tr -d '\r'); log "pid=$PID"; [ -n "$PID" ] && break; log "no arranco (intento $try)"; done
  adb forward tcp:9222 localabstract:webview_devtools_remote_$PID; }

adb wait-for-device; adb shell 'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 2; done'
adb shell settings put global window_animation_scale 0; adb shell settings put global transition_animation_scale 0
log "Dispositivo: $(adb shell getprop ro.product.model) Android $(adb shell getprop ro.build.version.release) API $(adb shell getprop ro.build.version.sdk)"
log "== Instalo la APK de depuracion con el espejo nativo (mismo codigo que la release 1.1)"
adb install -r "$APK" 2>&1 | tee -a "$LOG"
adb shell pm grant $PKG android.permission.RECORD_AUDIO 2>/dev/null
arranque
log "== Escribo relato y reparto por la ruta real (save/rsave)"
MODE=escribe node android/e2e-persistencia.mjs 2>&1 | tee -a "$LOG"
log "== force-stop inmediato (dentro de la ventana de volcado del WebView)"
adb shell am force-stop $PKG; sleep 2
arranque
MODE=verifica node android/e2e-persistencia.mjs 2>&1 | tee -a "$LOG"
adb exec-out screencap -p > "$EV/30-restaurado.png"
log "== Nuevo relato (reset): no debe resucitar ni borrar el reparto"
MODE=borra node android/e2e-persistencia.mjs 2>&1 | tee -a "$LOG"
adb shell am force-stop $PKG; sleep 2
arranque
MODE=verifica2 node android/e2e-persistencia.mjs 2>&1 | tee -a "$LOG"
adb exec-out screencap -p > "$EV/31-tras-nuevo.png"

ok=1
for chk in 'espejo relato escrito true' 'espejo reparto escrito true' 'relato restaurado tras force-stop true' 'reparto presente true' 'espejo borrado true' 'relato NO resucita true' 'reparto conservado tras Nuevo + kill true'; do
  grep -qF "$chk" "$LOG" || { ok=0; echo "FALTA: $chk" | tee -a "$LOG"; }
done
if [ $ok -eq 1 ]; then echo 'PASS' | tee "$EV/veredicto-persistencia.txt"; else echo 'FAIL' | tee "$EV/veredicto-persistencia.txt"; fi
