#!/usr/bin/env bash
# Essai de bout en bout, JMeter réel en ligne de commande : le plan met côte à côte
# Si > Transaction (témoins) et la Transaction Si, avec la même condition, contre une
# application de test (/ok = 200, /ko = 500). On compare les lignes du CSV, puis on
# génère le rapport HTML.
# Usage : [JMETER_HOME=…] ./lancer.sh   (jar construit par « mvn package » ; sans JMETER_HOME,
# JMeter $JMETER_VERSION, 5.4.3 par défaut, est téléchargé dans target/).
set -euo pipefail
ici="$(cd "$(dirname "$0")" && pwd)"
module="$(dirname "$ici")"
travail="$module/target/essai"
rm -rf "$travail"; mkdir -p "$travail"
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if [ -z "${JMETER_HOME:-}" ]; then
  version="${JMETER_VERSION:-5.4.3}"
  JMETER_HOME="$module/target/apache-jmeter-$version"
  [ -d "$JMETER_HOME" ] || curl -sfL "https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-$version.tgz" | tar xz -C "$module/target"
fi
rm -f "$JMETER_HOME"/lib/ext/transaction-si-*.jar
cp "$module"/target/transaction-si-*.jar "$JMETER_HOME/lib/ext/"

port=18791
python3 -I "$ici/serveur.py" $port & serveur=$!
trap 'kill $serveur 2>/dev/null || true' EXIT
sleep 1

cd "$travail"
sed "s/@PORT@/$port/" "$ici/plan.jmx" > plan.jmx
timeout 120 "$JMETER_HOME/bin/jmeter" -n -t plan.jmx -l resultats.csv -j jmeter.log -e -o rapport >/dev/null
if grep -q ' ERROR ' jmeter.log; then echo "ECHEC erreurs dans jmeter.log :"; grep ' ERROR ' jmeter.log; exit 1; fi
echo "OK    tir sans erreur dans jmeter.log, rapport HTML généré ($(ls rapport/index.html))"
python3 -I "$ici/verifier.py" resultats.csv

echo "== Interface graphique (écran virtuel)"
cp plan.jmx plan-gui.jmx
timeout 180 xvfb-run -a -s "-screen 0 1400x900x24" \
  java -Djmeter.home="$JMETER_HOME" -Duser.language=fr \
    -cp "$JMETER_HOME/bin/ApacheJMeter.jar:$JMETER_HOME/lib/*:$JMETER_HOME/lib/ext/*" \
    "$ici/EssaiGui.java" plan-gui.jmx "$travail" 2>/dev/null | grep -E '^(OK|ECHEC|TOUT|[0-9]+ ECHEC)'
