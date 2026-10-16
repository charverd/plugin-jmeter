#!/usr/bin/env bash
# Essai de bout en bout dans un vrai JMeter, sous écran virtuel :
# un plan avec des variables (plan de test + User Defined Variables) est
# ouvert dans l'interface ; on enregistre de vraies requêtes à travers le
# proxy, d'abord avec l'enregistreur standard de JMeter (témoin), puis avec
# l'enregistreur sans variables, et on compare les plans obtenus.
# Usage : JMETER_HOME=/chemin/apache-jmeter-x.y.z ./lancer.sh
# (sans JMETER_HOME, JMeter $JMETER_VERSION, 5.4.3 par défaut, est téléchargé
# dans target/). Le java du PATH (ou de JAVA_HOME) est utilisé.
set -euo pipefail
ici="$(cd "$(dirname "$0")" && pwd)"
module="$(dirname "$ici")"
travail="$module/target/essai-gui"
rm -rf "$travail"; mkdir -p "$travail/site"

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
jar="$(ls "$module"/target/enregistreur-sans-variables-*.jar)"
rm -f "$JMETER_HOME"/lib/ext/enregistreur-sans-variables-*.jar
cp "$jar" "$JMETER_HOME/lib/ext/"

cd "$travail"
echo "page de l'application" > site/login.html
port_app=18765
python3 -m http.server "$port_app" --bind 127.0.0.1 --directory site >/dev/null 2>&1 &
serveur=$!
trap 'kill $serveur 2>/dev/null || true' EXIT

# Dans un .jmx, c'est la balise XML qui choisit la classe instanciée ;
# l'attribut testclass ne suffit pas.
enregistrer() { # $1 nom, $2 classe GUI, $3 classe (= balise), $4 port du proxy
  local dossier="$travail/$1"; mkdir -p "$dossier"; cd "$dossier"
  sed -e "s/@GUI@/$2/; s/@CLASSE@/$3/; s/@BALISE@/$3/g; s/@PORT_PROXY@/$4/; s/@PORT_APP@/$port_app/" \
    "$ici/plan-gabarit.jmx" > plan.jmx
  ( for i in $(seq 1 120); do [ -f proxy-pret ] && break; sleep 0.5; done
    curl -s -o /dev/null -x "http://127.0.0.1:$4" -H "X-Client: alice" \
      "http://127.0.0.1:$port_app/login.html?utilisateur=alice&jeton=XYZ789"
    curl -s -o /dev/null -x "http://127.0.0.1:$4" -H "X-Client: alice" \
      --data "utilisateur=alice&motdepasse=secret&jeton=XYZ789" "http://127.0.0.1:$port_app/login.html"
    touch requetes-envoyees ) &
  local envoi=$!
  timeout 180 xvfb-run -a -s "-screen 0 1280x800x24" \
    java -Duser.language=en -cp "$JMETER_HOME/bin/ApacheJMeter.jar:$JMETER_HOME/lib/*:$JMETER_HOME/lib/ext/*" \
      "$ici/Essai.java" plan.jmx resultat.jmx 2>/dev/null | grep '^ '
  wait "$envoi"
  # Ce qui a été enregistré : uniquement les requêtes et leurs en-têtes.
  python3 - resultat.jmx <<'PY'
import re, sys
xml = open(sys.argv[1], encoding="utf-8").read()
requetes = re.findall(r'<HTTPSamplerProxy[^>]*testname="([^"]*)"', xml)
# Valeurs enregistrées : serveur, port, paramètres et en-têtes des requêtes.
champs = re.findall(r'<stringProp name="(HTTPSampler\.domain|HTTPSampler\.port|Argument\.value|Header\.value)">([^<]*)</stringProp>', xml)
# Les définitions de variables du plan sont aussi des Argument.value : on ne
# garde que ce qui suit la première requête enregistrée.
debut = xml.find("<HTTPSamplerProxy")
enregistre = xml[debut:] if debut >= 0 else ""
vars_ = sorted(set(re.findall(r'\$\{[a-z_]+\}', enregistre)))
print("  requêtes enregistrées :", len(requetes), requetes)
vals = sorted(set(v for k, v in re.findall(r'<stringProp name="(HTTPSampler\.domain|HTTPSampler\.port|Header\.value)">([^<]*)</stringProp>', enregistre)))
print("  serveur / port / en-têtes enregistrés :", vals)
print("  références ${...} dans l'enregistrement :", vars_ if vars_ else "aucune")
open("variables.txt", "w").write(" ".join(vars_))
open("nb-requetes.txt", "w").write(str(len(requetes)))
PY
  cd "$travail"
}

echo "== Enregistreur standard de JMeter (témoin)"
enregistrer standard ProxyControlGui ProxyControl 18898
echo "== Enregistreur sans variables"
enregistrer sans-variables fr.jmeter.plugins.enregistreur.EnregistreurSansVariablesGui \
  fr.jmeter.plugins.enregistreur.EnregistreurSansVariables 18899

echo "== Verdict"
ok=1
grep -q "<fr.jmeter.plugins.enregistreur.EnregistreurSansVariables " sans-variables/resultat.jmx \
  && echo "OK    le plan enregistré garde l'enregistreur du plugin" \
  || { echo "ECHEC le plan enregistré a perdu l'enregistreur du plugin"; ok=0; }
[ -s standard/variables.txt ] && echo "OK    le témoin remplace bien par des variables : $(cat standard/variables.txt)" \
  || { echo "ECHEC le témoin n'a rien remplacé (essai non probant)"; ok=0; }
[ "$(cat sans-variables/nb-requetes.txt)" -ge 2 ] && echo "OK    requêtes enregistrées par le plugin" \
  || { echo "ECHEC aucune requête enregistrée par le plugin"; ok=0; }
[ ! -s sans-variables/variables.txt ] && echo "OK    aucune référence \${...} avec le plugin" \
  || { echo "ECHEC références trouvées avec le plugin : $(cat sans-variables/variables.txt)"; ok=0; }
# Le plan obtenu doit se rejouer en ligne de commande, enregistreur compris.
# (Le serveur web de l'essai, python http.server, répond 501 aux POST : on
# vérifie que les requêtes partent, pas leur code retour.)
if (cd sans-variables && timeout 120 "$JMETER_HOME/bin/jmeter" -n -t resultat.jmx -l rejeu.jtl -j rejeu.log >/dev/null 2>&1) \
    && [ "$(grep -c '127.0.0.1:18765' sans-variables/rejeu.jtl)" -ge 2 ] \
    && ! grep -q ' ERROR ' sans-variables/rejeu.log; then
  echo "OK    le plan enregistré se rejoue en ligne de commande ($(grep -c '127.0.0.1:18765' sans-variables/rejeu.jtl) requêtes vers 127.0.0.1:18765)"
else
  echo "ECHEC le plan enregistré ne se rejoue pas (voir sans-variables/rejeu.log)"; ok=0
fi
[ $ok = 1 ] && echo "TOUT EST BON" || { echo "ECHEC"; exit 1; }
