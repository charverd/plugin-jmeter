#!/usr/bin/env bash
# Essai de la sauvegarde HTML de l'arbre de résultats dans la vraie interface de JMeter, avec
# l'arbre de résultats filtrable installé à côté : bouton dans la barre d'outils, tir réel contre
# une petite application locale, sauvegarde puis nettoyage (arbre affiché, puis tous), échec
# d'écriture sans nettoyage, changement de langue. Puis la page produite est vérifiée dans
# Chromium (filtre, onglets, image, piège </script>), si Playwright est disponible.
# Usage : JMETER_HOME=… [JAVA_HOME=…] [PLAYWRIGHT=…/node_modules/playwright CHROMIUM=… NODE=…] ./lancer.sh
#         (jars construits par « mvn package » à la racine du projet)
set -euo pipefail
ici="$(cd "$(dirname "$0")" && pwd)"
module="$(dirname "$ici")"
projet="$(dirname "$module")"
travail="$module/target/essai-gui"
rm -rf "$travail"; mkdir -p "$travail"
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
: "${JMETER_HOME:?définir JMETER_HOME}"
rm -f "$JMETER_HOME"/lib/ext/sauvegarde-arbre-html-*.jar "$JMETER_HOME"/lib/ext/arbre-resultats-filtrable-*.jar
cp "$module"/target/sauvegarde-arbre-html-*.jar "$projet"/arbre-resultats-filtrable/target/arbre-resultats-filtrable-*.jar "$JMETER_HOME/lib/ext/"

cd "$travail"
cp "$ici/plan.jmx" .
python3 "$ici/serveur.py" 18790 & serveur=$!
trap 'kill $serveur 2>/dev/null || true' EXIT
sleep 1
timeout 300 xvfb-run -a -s "-screen 0 1400x900x24" \
  java -Djmeter.home="$JMETER_HOME" -Duser.language=fr \
    -cp "$JMETER_HOME/bin/ApacheJMeter.jar:$JMETER_HOME/lib/*:$JMETER_HOME/lib/ext/*" \
    "$ici/EssaiSauvegarde.java" plan.jmx 2>&1 | grep -E '^(OK|ECHEC|TOUT|[0-9]+ ECHEC| )' || true

if [ -n "${PLAYWRIGHT:-}" ] && [ -n "${CHROMIUM:-}" ]; then
  "${NODE:-node}" "$ici/verif_page.cjs" "$PLAYWRIGHT" "$CHROMIUM" page-filtrable.html page-tous.html
else
  echo "(page non vérifiée dans un navigateur : PLAYWRIGHT et CHROMIUM non définis)"
fi
