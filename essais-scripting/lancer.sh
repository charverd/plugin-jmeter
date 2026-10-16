#!/usr/bin/env bash
# Essai de bout en bout des plugins de scripting, dans la vraie interface de
# JMeter sous écran virtuel :
#   1. application de test (serveur.py) qui exige un vrai jeton CSRF et un vrai
#      numéro de commande, différents à chaque session ;
#   2. EssaiScripting.java : deux enregistrements réels du parcours (parcours.py)
#      avec l'enregistreur sans variables, nettoyage, corrélation, vérification,
#      enregistrement du plan ;
#   3. rejeu du plan obtenu en ligne de commande : il doit passer, ce qui
#      n'est possible que si la corrélation est juste.
# Usage : JMETER_HOME=… [JAVA_HOME=…] ./lancer.sh  (jars construits par « mvn package » à la racine)
set -euo pipefail
ici="$(cd "$(dirname "$0")" && pwd)"
racine="$(dirname "$ici")"
travail="$ici/target"
rm -rf "$travail"; mkdir -p "$travail"
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
: "${JMETER_HOME:?définir JMETER_HOME}"

for m in enregistreur-sans-variables nettoyage-enregistrement correlation-assistee verificateur-script; do
  rm -f "$JMETER_HOME"/lib/ext/$m-*.jar
  cp "$racine"/$m/target/$m-*.jar "$JMETER_HOME/lib/ext/"
done

port=18770
python3 "$ici/serveur.py" $port 127.0.0.1 & s1=$!
python3 "$ici/serveur.py" $port 127.0.0.2 & s2=$!
trap 'kill $s1 $s2 2>/dev/null || true' EXIT
sleep 1

cd "$travail"
cp "$ici/plan.jmx" .
timeout 300 xvfb-run -a -s "-screen 0 1400x900x24" \
  java -Djmeter.home="$JMETER_HOME" -Duser.language=fr -Dici="$ici" \
    -cp "$JMETER_HOME/bin/ApacheJMeter.jar:$JMETER_HOME/lib/*:$JMETER_HOME/lib/ext/*" \
    "$ici/EssaiScripting.java" plan.jmx $port 2>&1 | grep -E '^(OK|ECHEC|TOUT|[0-9]+ ECHEC| )' || true

echo "== Rejeu du plan corrigé, en ligne de commande, contre une session neuve"
timeout 120 "$JMETER_HOME/bin/jmeter" -n -t plan.jmx -l rejeu.jtl -j rejeu.log >/dev/null 2>&1 || true
python3 - rejeu.jtl <<'PY'
import csv, sys
lignes = list(csv.DictReader(open(sys.argv[1])))
requetes = [l for l in lignes if not l["label"].startswith("T0")]
for l in lignes:
    print(f"  {l['label']:28s} {l['responseCode']:>4} {l['success']}")
ok = len(requetes) == 3 and all(l["success"] == "true" and l["responseCode"] == "200" for l in requetes)
print(("OK    " if ok else "ECHEC ") + "le plan corrigé passe : connexion avec le jeton extrait, détail de la commande extraite")
print("TOUT EST BON (rejeu)" if ok else "ECHEC (rejeu)")
sys.exit(0 if ok else 1)
PY
