#!/usr/bin/env bash
# Essai de bout en bout dans un vrai JMeter, sous écran virtuel : sélection
# multiple de deux transactions sur trois, menu clic droit, fenêtre
# « Modification en masse » (en-tête, paramètre, rechercher / remplacer,
# recherche de transactions, renommage), puis
# enregistrement du plan par la commande « Enregistrer » de JMeter, et
# vérification du fichier.
# Usage : JMETER_HOME=/chemin/apache-jmeter-x.y.z ./lancer.sh
# (sans JMETER_HOME, JMeter $JMETER_VERSION, 5.4.3 par défaut, est téléchargé
# dans target/). Le java du PATH (ou de JAVA_HOME) est utilisé.
set -euo pipefail
ici="$(cd "$(dirname "$0")" && pwd)"
module="$(dirname "$ici")"
travail="$module/target/essai-gui"
rm -rf "$travail"; mkdir -p "$travail"

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
jar="$(ls "$module"/target/modification-en-masse-*.jar)"
rm -f "$JMETER_HOME"/lib/ext/modification-en-masse-*.jar
cp "$jar" "$JMETER_HOME/lib/ext/"

cd "$travail"
cp "$ici/plan.jmx" .
timeout 180 xvfb-run -a -s "-screen 0 1400x900x24" \
  java -Duser.language=fr -cp "$JMETER_HOME/bin/ApacheJMeter.jar:$JMETER_HOME/lib/*:$JMETER_HOME/lib/ext/*" \
    "$ici/Essai.java" plan.jmx 2>/dev/null | grep -E '^(OK|ECHEC|TOUT|[0-9]+ ECHEC| )' || true

# Le fichier enregistré par JMeter.
python3 - plan.jmx <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
ok = True
def verifier(quoi, cond):
    global ok
    print(("OK    " if cond else "ECHEC ") + quoi)
    ok = ok and cond
blocs = re.split(r'(?=<TransactionController )', xml)[1:]
par_tc = {re.search(r'testname="([^"]+)"', b).group(1): b for b in blocs}
for tc in ("Parcours_01_Connexion", "Parcours_02_Commande"):
    b = par_tc[tc]
    verifier(f"fichier : {tc} a Bearer ${{token}} sur ses 2 requêtes", b.count("Bearer ${token}") == 2)
    verifier(f"fichier : {tc} a ${{serveur}} sur ses 2 requêtes", b.count('<stringProp name="HTTPSampler.domain">${serveur}</stringProp>') == 2)
    verifier(f"fichier : {tc} garde ses X-Correlation", "abc-1" in b and "abc-2" in b)
    verifier(f"fichier : requêtes de {tc} renommées {tc}_R01 et _R02", f'testname="{tc}_R01"' in b and f'testname="{tc}_R02"' in b)
    verifier(f"fichier : {tc} a client = ${{client}} sur ses 2 requêtes", b.count('<stringProp name="Argument.value">${client}</stringProp>') == 2)
b = par_tc["TC_03_Deconnexion"]
verifier("fichier : TC_03 inchangée", "Bearer jeton3" in b and "${" not in b)
print("TOUT EST BON (fichier)" if ok else "ECHEC (fichier)")
sys.exit(0 if ok else 1)
PY
