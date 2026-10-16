#!/usr/bin/env bash
# Essai de bout en bout dans un vrai JMeter, sous écran virtuel :
#   1. tir non graphique de plan.jmx (transactions + Debug Samplers) ;
#   2. ouverture du visualiseur, chargement du JTL, vérification du filtre,
#      de la copie et de la fenêtre « Noms… », captures PNG.
# Usage : JMETER_HOME=/chemin/apache-jmeter-x.y.z ./lancer.sh
# (sans JMETER_HOME, JMeter $JMETER_VERSION, 5.4.3 par défaut, est téléchargé
# dans target/). Le java du PATH (ou de JAVA_HOME) sert aux deux étapes.
set -euo pipefail
ici="$(cd "$(dirname "$0")" && pwd)"
module="$(dirname "$ici")"
travail="$module/target/essai-gui"
mkdir -p "$travail"

if [ -n "${JAVA_HOME:-}" ]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [ -z "${JMETER_HOME:-}" ]; then
  version="${JMETER_VERSION:-5.4.3}"
  JMETER_HOME="$module/target/apache-jmeter-$version"
  if [ ! -d "$JMETER_HOME" ]; then
    curl -sfL "https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-$version.tgz" \
      | tar xz -C "$module/target"
  fi
fi
export JMETER_HOME

jar="$(ls "$module"/target/arbre-resultats-filtrable-*.jar)"
rm -f "$JMETER_HOME"/lib/ext/arbre-resultats-filtrable-*.jar
cp "$jar" "$JMETER_HOME/lib/ext/"

cd "$travail"
cp "$ici/plan.jmx" .
rm -f resultats.jtl
timeout 120 "$JMETER_HOME/bin/jmeter" -n -t plan.jmx -j jmeter.log \
  -Jjmeter.save.saveservice.output_format=xml -Jjmeter.save.saveservice.subresults=true >/dev/null

timeout 120 xvfb-run -a -s "-screen 0 1280x800x24" \
  java -cp "$JMETER_HOME/lib/*:$JMETER_HOME/lib/ext/*" "$ici/Essai.java" 2>/dev/null \
  | grep -E '^(OK|ECHEC|TOUT| )'
