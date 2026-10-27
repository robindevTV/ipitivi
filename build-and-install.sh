#!/usr/bin/env bash
# Compile l'application, lance les tests, puis l'installe sur la TV avec les outils de cleanyourtv.
# Usage : TV_HOST=192.168.1.110 ./build-and-install.sh
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?indique l'adresse IP de la TV, par ex. TV_HOST=192.168.1.110 $0}"

CYTV="${CYTV:-$HOME/cleanyourtv}"
PY="$CYTV/tools/venv/bin/python"
[ -x "$PY" ] || { echo "ERREUR : $PY est introuvable. Dossier cleanyourtv attendu dans $CYTV (variable CYTV pour le changer)." >&2; exit 1; }

# Java 17
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -d /opt/homebrew/opt/openjdk@17 ]; then JAVA_HOME=/opt/homebrew/opt/openjdk@17
  elif /usr/libexec/java_home -v 17 >/dev/null 2>&1; then JAVA_HOME="$(/usr/libexec/java_home -v 17)"
  else echo "ERREUR : Java 17 introuvable. Installe-le avec : brew install openjdk@17" >&2; exit 1; fi
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# SDK Android
if [ -z "${ANDROID_HOME:-}" ]; then
  for d in /opt/homebrew/share/android-commandlinetools "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    if [ -d "$d" ]; then ANDROID_HOME="$d"; break; fi
  done
fi
[ -n "${ANDROID_HOME:-}" ] || { echo "ERREUR : SDK Android introuvable (voir README)." >&2; exit 1; }
export ANDROID_HOME
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties

echo "==> Tests du lecteur de playlists et du guide"
./gradlew testDebugUnitTest --console=plain -q
echo "==> Compilation"
./gradlew assembleDebug --console=plain -q
APK=app/build/outputs/apk/debug/app-debug.apk
[ -f "$APK" ] || { echo "ERREUR : l'APK n'a pas été produit." >&2; exit 1; }

echo "==> Envoi vers la TV ($TV_HOST)"
"$PY" -I "$CYTV/tools/b64push.py" "$APK" /data/local/tmp/ipitivi.apk
OUT="$("$PY" -I "$CYTV/tools/tv.py" 'pm install -r /data/local/tmp/ipitivi.apk; rm -f /data/local/tmp/ipitivi.apk')"
echo "$OUT"
case "$OUT" in
  *Success*) echo "Installé. Ouvre « Ipitivi » depuis ton écran d'accueil." ;;
  *) echo "ERREUR : l'installation a échoué (voir ci-dessus)." >&2; exit 1 ;;
esac
