#!/usr/bin/env python3
"""File d'attente de tâches Bonsai, pensée pour être lancée en arrière-plan.

Usage : file.py [--paralleles N] [--sans-reflexion] taches/a.md taches/b.md …

Aucune limite de tokens n'est imposée : seule celle du serveur s'applique.

Lance les tâches (N à la fois, 2 par défaut, sur les emplacements libres de
llama-server), attend qu'elles soient toutes finies, puis imprime UN bilan et
se termine. Celui qui l'a lancée n'a rien à surveiller entre-temps : il est
prévenu une seule fois, à la fin. Pendant ce temps, l'avancement est écrit
dans etat.txt (lisible par un humain, à la demande).

Code de sortie : 0 si toutes les tâches ont livré au moins un fichier, 1 sinon.
"""
import argparse, os, subprocess, sys, threading, time

ICI = os.path.dirname(os.path.abspath(__file__))
ap = argparse.ArgumentParser()
ap.add_argument("taches", nargs="+")
ap.add_argument("--paralleles", type=int, default=2)
ap.add_argument("--sans-reflexion", action="store_true")
a = ap.parse_args()

etat = {t: "en attente" for t in a.taches}
verrou = threading.Lock()
jeton = threading.Semaphore(a.paralleles)
debut = time.time()

def ecrire_etat():
    with open(os.path.join(ICI, "etat.txt"), "w", encoding="utf-8") as f:
        f.write("File Bonsai démarrée il y a %d min\n" % ((time.time() - debut) // 60))
        for t, e in etat.items():
            f.write("  %-45s %s\n" % (os.path.basename(t), e))

def lancer(t):
    with jeton:
        with verrou:
            etat[t] = "en cours depuis " + time.strftime("%H:%M")
            ecrire_etat()
        cmd = [sys.executable, os.path.join(ICI, "deleguer.py"), t]
        if a.sans_reflexion:
            cmd.append("--sans-reflexion")
        p = subprocess.run(cmd, capture_output=True, text=True)
        with verrou:
            etat[t] = ("livré : " if p.returncode == 0 else "ÉCHEC : ") + " | ".join(
                l.strip() for l in p.stdout.splitlines() if l.strip())
            ecrire_etat()

fils = [threading.Thread(target=lancer, args=(t,)) for t in a.taches]
for f in fils:
    f.start()
for f in fils:
    f.join()
ecrire_etat()
print(open(os.path.join(ICI, "etat.txt"), encoding="utf-8").read())
sys.exit(0 if all(e.startswith("livré") for e in etat.values()) else 1)
