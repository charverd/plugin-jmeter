#!/usr/bin/env bash
# Essai de l'assistant IA dans la vraie interface de JMeter, contre un faux
# serveur au format OpenAI (réponses en flux, actions sur le plan). Vérifie :
# menu, réglages (lister les modèles, tester), contexte envoyé, aperçu des
# actions, application, annulation, historique, enregistrement du plan.
# Usage : JMETER_HOME=… [JAVA_HOME=…] ./lancer.sh  (jar construit par « mvn package »)
set -euo pipefail
ici="$(cd "$(dirname "$0")" && pwd)"
module="$(dirname "$ici")"
travail="$module/target/essai-gui"
rm -rf "$travail"; mkdir -p "$travail"
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
: "${JMETER_HOME:?définir JMETER_HOME}"
rm -f "$JMETER_HOME"/lib/ext/assistant-ia-*.jar
cp "$module"/target/assistant-ia-*.jar "$JMETER_HOME/lib/ext/"

cd "$travail"
cp "$ici/plan.jmx" .
port=18780
python3 "$ici/faux_openai.py" $port & serveur=$!
trap 'kill $serveur 2>/dev/null || true' EXIT
cat > config.json <<JSON
{"profils":[{"nom":"Faux serveur","url":"http://127.0.0.1:$port/v1","modele":"faux-1","cle":"\${env:CLE_ESSAI}"}],
 "profilActif":"Faux serveur"}
JSON
sleep 1
CLE_ESSAI=sk-essai timeout 240 xvfb-run -a -s "-screen 0 1400x900x24" \
  java -Djmeter.home="$JMETER_HOME" -Duser.language=fr -Dassistant.ia.config="$travail/config.json" \
    -cp "$JMETER_HOME/bin/ApacheJMeter.jar:$JMETER_HOME/lib/*:$JMETER_HOME/lib/ext/*" \
    "$ici/EssaiAssistant.java" plan.jmx 2>&1 | grep -E '^(OK|ECHEC|TOUT|[0-9]+ ECHEC| )' || true

python3 - <<'PY'
import json
r = [json.loads(l) for l in open("requetes.jsonl")]
auth = [x["entetes"].get("Authorization", "") for x in r]
ok = all(a == "Bearer sk-essai" for a in auth)
print(("OK    " if ok else "ECHEC ") + "clé lue dans ${env:CLE_ESSAI} et envoyée en Bearer (%d requêtes)" % len(r))
x = open("plan.jmx").read()
ok2 = 'testname="Code 200"' in x and "/api/v2/commande" in x and 'testname="Connexion"' in x and "Pause inutile" not in x
print(("OK    " if ok2 else "ECHEC ") + "plan enregistré avec les modifications")
print("TOUT EST BON (fichier)" if ok and ok2 else "ECHEC (fichier)")
PY
