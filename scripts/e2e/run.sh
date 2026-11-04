#!/usr/bin/env bash
# Prueba de extremo a extremo de LockApp en un emulador Android.
# Uso: scripts/e2e/run.sh ruta/al/app-debug.apk
# Necesita adb conectado a un emulador o teléfono de pruebas y python3.
set -uo pipefail

APK="${1:?Uso: run.sh ruta/al/app-debug.apk}"
PKG=com.nihaht.lockapp
SERVICE="$PKG/$PKG.service.AppLockAccessibilityService"
OUT="${E2E_OUT:-e2e-output}"
DIR="$(cd "$(dirname "$0")" && pwd)"
MASTER_PIN=1234
# Multiplicador de esperas para emuladores lentos (sin aceleración): E2E_SLOW=4
SLOW="${E2E_SLOW:-1}"
APP_PIN=5678
STEP=0
declare -A KEYS=()

mkdir -p "$OUT"

# ---------------------------------------------------------------- utilidades

log() { echo; echo "▶ $*"; }
ok() { echo "  ✅ $*"; }

snap() {
    STEP=$((STEP + 1))
    adb exec-out screencap -p > "$OUT/$(printf '%02d' "$STEP")-$1.png" 2>/dev/null || true
}

fail() {
    echo "  ❌ FALLO: $*"
    snap "fallo"
    adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" || true
    ui_dump > "$OUT/ultima-pantalla.xml" 2>/dev/null || true
    adb logcat -d > "$OUT/logcat.txt" 2>/dev/null || true
    exit 1
}

# Volcado de la jerarquía de la interfaz (reintenta si la pantalla no está quieta).
ui_dump() {
    local i
    for i in 1 2 3 4 5 6; do
        if adb shell uiautomator dump /sdcard/ui.xml 2>&1 | grep -q "dumped"; then
            adb shell cat /sdcard/ui.xml
            return 0
        fi
        sleep 1
    done
    return 1
}

find_node() { ui_dump | python3 "$DIR/ui.py" "$1" "${2:-}"; }

# wait_for MODO VALOR [segundos]
wait_for() {
    local end=$((SECONDS + ${3:-20} * SLOW))
    while ((SECONDS < end)); do
        find_node "$1" "$2" > /dev/null 2>&1 && return 0
        sleep 1
    done
    return 1
}

# tap MODO VALOR
tap() {
    local xy end=$((SECONDS + 20 * SLOW))
    while ((SECONDS < end)); do
        if xy=$(find_node "$1" "$2" 2> /dev/null) && [[ -n "$xy" ]]; then
            adb shell input tap $xy
            sleep 0.7
            return 0
        fi
        sleep 1
    done
    fail "no se encontró en pantalla: '$2'"
}

# Lee la posición de las teclas del teclado de PIN que hay en pantalla.
read_keypad() {
    local line end=$((SECONDS + 20 * SLOW))
    KEYS=()
    while ((SECONDS < end)); do
        if line=$(ui_dump | python3 "$DIR/ui.py" keypad 2> /dev/null); then
            while read -r key x y; do KEYS[$key]="$x $y"; done <<< "$line"
            return 0
        fi
        sleep 1
    done
    fail "no se encontró el teclado de PIN"
}

# type_pin PIN [reutilizar]: escribe el PIN y pulsa Aceptar.
type_pin() {
    local pin=$1 i
    [[ "${2:-}" == "reutilizar" && ${#KEYS[@]} -ge 11 ]] || read_keypad
    for ((i = 0; i < ${#pin}; i++)); do
        adb shell input tap ${KEYS[${pin:$i:1}]}
        sleep 0.3
    done
    adb shell input tap ${KEYS[ok]}
    sleep "$(awk "BEGIN{print 2.5*$SLOW}")" # el hash PBKDF2 tarda un poco; mientras tanto el teclado no acepta pulsaciones
}

focus() { adb shell dumpsys window | grep -m1 "mCurrentFocus" | tr -d '\r'; }

# wait_focus PATRON [segundos]: espera a que la ventana con foco contenga PATRON.
wait_focus() {
    local end=$((SECONDS + ${2:-10} * SLOW))
    while ((SECONDS < end)); do
        focus | grep -q "$1" && return 0
        sleep 0.5
    done
    echo "  foco actual: $(focus)"
    return 1
}

# wait_focus_not PATRON [segundos]
wait_focus_not() {
    local end=$((SECONDS + ${2:-10} * SLOW))
    while ((SECONDS < end)); do
        local f
        f=$(focus)
        [[ -n "$f" ]] && ! grep -qE "$1" <<< "$f" && return 0
        sleep 0.5
    done
    echo "  foco actual: $(focus)"
    return 1
}

home() { adb shell input keyevent KEYCODE_HOME; sleep 1.5; }

swipe_up() { adb shell input swipe 540 1900 540 500 300; sleep 1; }

open_target() {
    adb shell monkey -p "$TARGET" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
}

# Abre LockApp y, si pide el PIN maestro, lo introduce.
open_lockapp() {
    adb shell am start -n "$PKG/.MainActivity" > /dev/null
    sleep 2
    if find_node contains "Introduce tu PIN maestro" > /dev/null 2>&1; then
        type_pin "$MASTER_PIN"
    fi
    wait_for desc "Ajustes" 20 || fail "no se llegó a la lista de apps"
}

# uiautomator suspende un momento los servicios de accesibilidad: se espera a que vuelva.
settle() { sleep $(( ${1:-3} * SLOW )); }

# -------------------------------------------------------------- preparación

log "Preparando el emulador"
adb wait-for-device
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb uninstall "$PKG" > /dev/null 2>&1 || true
adb logcat -c

log "Instalando el APK"
adb install -r -g "$APK" || fail "no se pudo instalar el APK"
# Equivale a "Batería sin restricciones" en el teléfono.
adb shell dumpsys deviceidle whitelist +"$PKG" > /dev/null 2>&1 || true
ok "APK instalado"

# App de prueba que se va a bloquear: la primera que exista en el emulador.
LAUNCHABLE=$(adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
    | tr -d '\r' | grep "/" | sed -E 's/^ +//; s#/.*##' | sort -u)
TARGET=""
for candidate in com.android.contacts com.google.android.contacts com.android.camera2 \
    com.google.android.deskclock com.android.deskclock com.google.android.calendar com.android.calendar \
    com.google.android.apps.messaging com.android.messaging com.android.chrome; do
    if grep -qx "$candidate" <<< "$LAUNCHABLE"; then TARGET=$candidate; break; fi
done
[[ -n "$TARGET" ]] || fail "no hay ninguna app de prueba disponible. Apps: $LAUNCHABLE"
ok "app que se bloqueará en la prueba: $TARGET"

# ----------------------------------------------------- 1. primer uso y PIN maestro

log "1. Primer uso: crear el PIN maestro"
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
wait_for text "Bienvenido a LockApp" 30 || fail "no aparece la pantalla de bienvenida"
snap "bienvenida"
type_pin "$MASTER_PIN"
wait_for text "Confirma el PIN" || fail "no pide confirmar el PIN maestro"
type_pin 9999 reutilizar
wait_for contains "no coinciden" || fail "no avisa de que los PIN no coinciden"
ok "detecta PIN de confirmación distinto"
type_pin "$MASTER_PIN" reutilizar
wait_for text "Confirma el PIN" || fail "no pide confirmar el PIN maestro (2.º intento)"
type_pin "$MASTER_PIN" reutilizar
wait_for text "Configura LockApp" 20 || fail "no aparece el asistente de permisos tras crear el PIN maestro"
snap "asistente-permisos"
ok "PIN maestro creado y asistente de permisos mostrado"

# --------------------------------------------------- 2. servicio de accesibilidad

log "2. Activar el servicio de accesibilidad"
adb shell settings put secure enabled_accessibility_services "$SERVICE"
adb shell settings put secure accessibility_enabled 1
bound=0
for _ in $(seq 1 20); do
    if adb shell dumpsys accessibility | grep -qE "Bound services:.*LockApp"; then bound=1; break; fi
    sleep 1
done
((bound)) || fail "el servicio de accesibilidad no se ha conectado"
ok "servicio de accesibilidad conectado"

fg=0
for _ in $(seq 1 10); do
    if adb shell dumpsys activity services "$PKG/.service.KeepAliveService" | grep -q "isForeground=true"; then fg=1; break; fi
    sleep 1
done
((fg)) || fail "KeepAliveService no está en primer plano"
ok "servicio en primer plano activo"

# El botón para continuar está al final del asistente.
swipe_up; swipe_up
tap contains "Continuar"
wait_for desc "Ajustes" 20 || fail "no se llegó a la lista de apps"
snap "lista-apps"
ok "lista de apps visible"

# ----------------------------------------------- 3. bloquear una app con su PIN

log "3. Bloquear $TARGET con su propio PIN"
tap edit
adb shell input text "$TARGET"
sleep 1.5
tap text "Sin bloquear"
wait_for contains "PIN para" 15 || fail "no se abrió el editor de bloqueo de la app"
snap "editor-app"
type_pin "$APP_PIN"
wait_for text "Confirma el PIN" || fail "no pide confirmar el PIN de la app"
type_pin "$APP_PIN" reutilizar
wait_for text "Bloqueada con su propio PIN" 20 || fail "la app no aparece como bloqueada en la lista"
snap "app-bloqueada"
ok "app bloqueada con PIN propio"

# -------------------------------------------------- 4. abrir la app bloqueada

log "4. Abrir la app bloqueada"
home
settle
open_target
wait_focus "LockActivity" 10 || fail "al abrir $TARGET no apareció la pantalla de PIN"
snap "pantalla-bloqueo"
ok "aparece la pantalla de bloqueo"

wait_for contains "Introduce el PIN de esta app" || fail "la pantalla de bloqueo no muestra el texto esperado"
type_pin "$MASTER_PIN" # el PIN maestro NO debe abrir la app
wait_for contains "PIN incorrecto" || fail "no avisa de PIN incorrecto"
wait_focus "LockActivity" 3 || fail "con el PIN maestro se abrió la app (debe usar su propio PIN)"
ok "el PIN maestro no abre la app (cada app tiene su PIN)"

type_pin "$APP_PIN" reutilizar
wait_focus_not "LockActivity" 10 || fail "el PIN correcto no desbloqueó la app"
wait_focus "$TARGET" 5 || fail "tras desbloquear no se volvió a $TARGET"
snap "app-desbloqueada"
ok "el PIN correcto abre la app"

log "4b. Volver a abrirla (periodo de gracia 0: debe pedir PIN otra vez)"
home
settle 2
open_target
wait_focus "LockActivity" 10 || fail "con gracia 0 no volvió a pedir el PIN"
ok "vuelve a pedir el PIN"

log "4c. Botón Atrás en la pantalla de PIN"
adb shell input keyevent KEYCODE_BACK
wait_focus_not "LockActivity|$TARGET" 10 || fail "Atrás no llevó a la pantalla de inicio"
ok "Atrás lleva al inicio sin mostrar la app"

# ------------------------------------------------- 5. apagar la pantalla

log "5. Apagar la pantalla dentro de la app desbloqueada"
settle 2
open_target
wait_focus "LockActivity" 10 || fail "no apareció la pantalla de PIN"
type_pin "$APP_PIN"
wait_focus "$TARGET" 10 || fail "no se desbloqueó la app"
adb shell svc power stayon false
adb shell input keyevent KEYCODE_SLEEP
sleep 3
adb shell input keyevent KEYCODE_WAKEUP
sleep 1
adb shell wm dismiss-keyguard
adb shell svc power stayon true
wait_focus "LockActivity" 10 || fail "al encender la pantalla la app estaba visible sin PIN"
snap "tras-apagar-pantalla"
ok "tras apagar la pantalla vuelve a pedir el PIN"
type_pin "$APP_PIN" reutilizar
wait_focus "$TARGET" 10 || fail "no se desbloqueó tras encender la pantalla"
home

# ---------------------------------- 6. ajustes: PIN maestro y periodo de gracia

log "6. Ajustes: periodo de gracia de 30 s y PIN maestro"
open_lockapp
tap desc "Ajustes"
wait_for text "30 segundos" || fail "no se abrieron los ajustes"
tap text "30 segundos"
snap "ajustes"
swipe_up; swipe_up
tap text "Cerrar LockApp ahora"
wait_for contains "Introduce tu PIN maestro" 10 || fail "'Cerrar LockApp ahora' no pidió el PIN maestro"
type_pin "$APP_PIN" # el PIN de una app no abre LockApp
wait_for contains "PIN incorrecto" || fail "el PIN de la app abrió LockApp"
type_pin "$MASTER_PIN" reutilizar
wait_for desc "Ajustes" 15 || fail "el PIN maestro correcto no abrió LockApp"
ok "PIN maestro verificado (y el de la app no sirve para LockApp)"
home
settle

log "6b. Con 30 s de gracia: volver enseguida NO pide PIN"
open_target
wait_focus "LockActivity" 10 || fail "no pidió PIN tras cambiar el periodo de gracia"
type_pin "$APP_PIN"
wait_focus "$TARGET" 10 || fail "no se desbloqueó la app"
home
sleep 3
open_target
sleep 3
wait_focus "$TARGET" 5 || fail "no volvió a la app"
focus | grep -q "LockActivity" && fail "pidió PIN dentro del periodo de gracia"
ok "dentro de los 30 s no pide PIN"

log "6c. Pasados 30 s sí pide PIN"
home
sleep 32
open_target
wait_focus "LockActivity" 10 || fail "pasado el periodo de gracia no pidió el PIN"
ok "pasado el periodo de gracia vuelve a pedir el PIN"

# ------------------------------------------- 7. bloqueo por intentos fallidos

log "7. Cinco intentos fallidos"
read_keypad
for attempt in 1 2 3 4 5; do
    type_pin 0000 reutilizar
done
snap "intentos-fallidos"
if wait_for contains "Demasiados intentos" 5; then
    ok "muestra la cuenta atrás"
else
    echo "  ⚠️ no se pudo leer el texto de la cuenta atrás (la pantalla cambia cada segundo)"
fi
type_pin "$APP_PIN" reutilizar
sleep 2
wait_focus "LockActivity" 2 || fail "durante la espera el PIN correcto abrió la app"
ok "durante la espera ni el PIN correcto abre la app"

adb shell input keyevent KEYCODE_BACK
home

# --------------------------------------------- 8. diagnóstico y Recientes

log "8. Pantalla de Diagnóstico"
open_lockapp
tap desc "Diagnóstico"
if wait_for text "Todo funciona correctamente" 30; then
    snap "diagnostico"
    ok "el diagnóstico dice que todo funciona"
else
    snap "diagnostico"
    fail "el diagnóstico muestra problemas"
fi
adb shell input keyevent KEYCODE_BACK
home
settle

log "8b. Recientes (comprobación informativa: depende del launcher)"
adb shell input keyevent KEYCODE_APP_SWITCH
sleep $((4 * SLOW))
snap "recientes"
if find_node contains "Recientes protegidos" > /dev/null 2>&1; then
    ok "Recientes queda tapado y pide el PIN maestro"
    tap contains "Ir al inicio"
else
    echo "  ⚠️ este launcher no se detectó como Recientes (en Samsung se comprueba en Diagnóstico)"
fi
home

# ----------------------------------------------------------- 9. comprobaciones

log "9. Comprobaciones finales"
if adb shell dumpsys package "$PKG" | grep -q "android.permission.INTERNET"; then
    fail "la app tiene el permiso INTERNET"
fi
ok "la app no tiene permiso de INTERNET"

adb logcat -d > "$OUT/logcat.txt"
if adb logcat -d -b crash | grep -q "$PKG"; then
    adb logcat -d -b crash | tail -60
    fail "la app se cerró inesperadamente (ver logcat)"
fi
ok "sin cierres inesperados"

echo
echo "🎉 Todas las pruebas en el emulador han pasado"
