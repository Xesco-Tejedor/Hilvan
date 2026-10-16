#!/usr/bin/env bash
# Emulador con Android 16 (API 36): arranque, render bajo la barra de estado, puente y boton Atras en la raiz.
set -u
PKG=es.xescotejedor.hilvan
EV=${EVIDENCE:-evidence}; mkdir -p "$EV"
DBG=$1
log(){ echo "[$(date +%H:%M:%S)] $*" | tee -a "$EV/e2e36-log.txt"; }
timeout 120 adb wait-for-device; timeout 120 adb shell 'while [ -z "$(getprop sys.boot_completed)" ]; do sleep 2; done'
adb shell settings put global window_animation_scale 0; adb shell settings put global transition_animation_scale 0
log "Dispositivo: Android $(adb shell getprop ro.build.version.release) API $(adb shell getprop ro.build.version.sdk)"
adb install -r "$DBG" | tee -a "$EV/e2e36-log.txt"
log "targetSdk instalado: $(adb shell dumpsys package $PKG | grep -m1 targetSdk)"
adb shell pm grant $PKG android.permission.RECORD_AUDIO
start_app(){ for try in 1 2 3; do adb shell am force-stop $PKG; timeout 60 adb shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1; sleep 10
  PID=$(adb shell pidof $PKG | tr -d '\r'); log "pid=$PID"; [ -n "$PID" ] && break; log "la app no arranco (intento $try)"; adb logcat -d | grep -E "Killing [0-9]+:$PKG|FATAL" | tail -2 | tee -a "$EV/e2e36-log.txt"; done
  adb forward tcp:9222 localabstract:webview_devtools_remote_$PID; }
node_e2e(){ MODE=$1 EVIDENCE=$EV timeout 240 node android/e2e.mjs 2>&1 | tee -a "$EV/e2e36-log.txt"; }
start_app; sleep 8
adb exec-out screencap -p > "$EV/20-api36-inicio.png"
node_e2e api36
log "foco antes de Atras: $(adb shell dumpsys window | grep -m1 mCurrentFocus)"
adb shell input keyevent KEYCODE_BACK; sleep 3
log "tras Atras en la pagina inicial, proceso vivo: $(adb shell pidof $PKG | tr -d '\r' | wc -c) (0-1 = cerrada); foco: $(adb shell dumpsys window | grep -m1 mCurrentFocus)"
adb logcat -d -s AndroidRuntime:E | tail -10 | tee -a "$EV/e2e36-log.txt"
V=PASS; why=""
chk(){ grep -qE "$1" "$EV/e2e36-log.txt" || { V=FAIL; why="$why | falta: $2"; }; }
chk 'API 36' 'emulador Android 16'
chk 'targetSdk=34' 'targetSdk 34 declarado'
chk 'api36 cabecera visible bajo la barra de estado true' 'contenido no tapado por la barra de estado'
chk 'api36 puente function' 'puente nativo presente en API 36'
chk 'tras Atras en la pagina inicial.*foco: .*[Ll]auncher' 'Atras en la raiz cierra la app (vuelve al launcher)'
echo "$V $why" > "$EV/veredicto-api36.txt"; log "VEREDICTO API36: $V $why"
