#!/usr/bin/env python3
"""Délègue une tâche de code au modèle Bonsai local (llama-swap, API OpenAI).

Usage : deleguer.py taches/<tache>.md [--racine <dossier du dépôt>] [--max-tokens N]

Le prompt demande à Bonsai de livrer chaque fichier dans un bloc
    // FICHIER: chemin/relatif/Depuis/La/Racine.java
    ```java
    ...
    ```
Les fichiers trouvés sont écrits sous la racine (le dépôt), la réponse brute
est gardée dans sorties/, et une ligne est ajoutée à journal.tsv (durée,
tokens) pour mesurer ce que Bonsai prend en charge.
"""
import argparse, json, os, re, sys, time, urllib.request

URL = "http://127.0.0.1:8090/v1/chat/completions"
MODELE = "bonsai2-27b"
ICI = os.path.dirname(os.path.abspath(__file__))

ap = argparse.ArgumentParser()
ap.add_argument("tache")
ap.add_argument("--racine", default=os.path.dirname(os.path.dirname(ICI)))
ap.add_argument("--max-tokens", type=int, default=16384)
ap.add_argument("--sans-reflexion", action="store_true",
                help="désactive la phase de raisonnement (enable_thinking=false) : rapide, code moins sûr")
a = ap.parse_args()

nom = os.path.splitext(os.path.basename(a.tache))[0]
prompt = open(a.tache, encoding="utf-8").read()
corps = {"model": MODELE, "messages": [{"role": "user", "content": prompt}],
         "max_tokens": a.max_tokens, "stream": False}
if a.sans_reflexion:
    corps["chat_template_kwargs"] = {"enable_thinking": False}
debut = time.time()
req = urllib.request.Request(URL, data=json.dumps(corps).encode(), headers={"Content-Type": "application/json"})
with urllib.request.urlopen(req, timeout=7200) as r:
    rep = json.load(r)
duree = time.time() - debut
msg = rep["choices"][0]["message"]
contenu = msg.get("content") or ""
raisonnement = msg.get("reasoning_content") or ""
usage = rep.get("usage", {})
fin = rep["choices"][0].get("finish_reason")
os.makedirs(os.path.join(ICI, "sorties"), exist_ok=True)
with open(os.path.join(ICI, "sorties", nom + ".md"), "w", encoding="utf-8") as f:
    f.write(contenu)
with open(os.path.join(ICI, "sorties", nom + ".raisonnement.md"), "w", encoding="utf-8") as f:
    f.write(raisonnement)

ecrits = []
for m in re.finditer(r"//\s*FICHIER:\s*(\S+)\s*\n```[a-zA-Z]*\n(.*?)\n```", contenu, re.S):
    chemin, code = m.group(1), m.group(2)
    if chemin.startswith("/") or ".." in chemin.split("/"):
        print("chemin refusé :", chemin, file=sys.stderr)
        continue
    cible = os.path.join(a.racine, chemin)
    os.makedirs(os.path.dirname(cible), exist_ok=True)
    with open(cible, "w", encoding="utf-8") as f:
        f.write(code.rstrip() + "\n")
    ecrits.append((chemin, code.count("\n") + 1))

journal = os.path.join(ICI, "journal.tsv")
neuf = not os.path.exists(journal)
with open(journal, "a", encoding="utf-8") as f:
    if neuf:
        f.write("date\ttache\tduree_s\ttokens_prompt\ttokens_generes\tcar_raisonnement\tcar_reponse\tfin\tfichiers\tlignes\n")
    f.write("\t".join(map(str, [time.strftime("%Y-%m-%d %H:%M"), nom, round(duree), usage.get("prompt_tokens"),
        usage.get("completion_tokens"), len(raisonnement), len(contenu), fin, len(ecrits),
        sum(n for _, n in ecrits)])) + "\n")
print(f"{nom} : {round(duree)} s, {usage.get('completion_tokens')} tokens générés, fin={fin}")
for c, n in ecrits:
    print(f"  écrit {c} ({n} lignes)")
if not ecrits:
    print("  aucun fichier extrait ; voir sorties/" + nom + ".md")
    sys.exit(1)
