"""Compare, dans le CSV d'un tir, les lignes de la Transaction Si à celles de Si > Transaction."""
import csv
import sys
from collections import defaultdict

lignes = list(csv.DictReader(open(sys.argv[1], encoding="utf-8")))
par = defaultdict(list)
for l in lignes:
    par[l["label"]].append(l)
ok = True


def verifier(quoi, cond):
    global ok
    print(("OK    " if cond else "ECHEC ") + quoi)
    ok = ok and cond


def champ(label, nom):
    return sorted({l[nom] for l in par[label]})


print("  libellé                    nb  code   succès  failureMessage")
for label in sorted(par):
    l = par[label][0]
    print(f"  {label:26s} {len(par[label]):2d}  {l['responseCode']:5s}  {l['success']:6s}  {l['failureMessage'][:70]}")

# 2 utilisateurs × 4 itérations, condition vraie aux itérations 1 et 3 : 4 passages.
for t in ("TA_standard", "TB_si", "TC_si_ok", "TE_standard_parent", "TD_si_parent", "TF_trace", "TG_assert"):
    verifier(f"{t} : 4 lignes (itérations impaires seulement)", len(par[t]) == 4)
verifier("requêtes jouées autant de fois dans la Transaction Si que dans Si > Transaction",
         len(par["A_ok"]) == len(par["B_ok"]) == 4 and len(par["A_ko"]) == len(par["B_ko"]) == 4)
verifier("même message que le standard",
         champ("TA_standard", "responseMessage") == champ("TB_si", "responseMessage")
         == ["Number of samples in transaction : 2, number of failing samples : 1"])
verifier("même succès que le standard", champ("TA_standard", "success") == champ("TB_si", "success") == ["false"])
verifier("témoin standard en échec : code vide, pas de détail",
         champ("TA_standard", "responseCode") == [""] and champ("TA_standard", "failureMessage") == [""])
verifier("Transaction Si en échec : code 500 et premier échec détaillé",
         champ("TB_si", "responseCode") == ["500"]
         and champ("TB_si", "failureMessage") == ["Premier échec : B_ko (500 Internal Server Error)"])
verifier("Transaction Si réussie : identique au standard (200, pas de détail)",
         champ("TC_si_ok", "responseCode") == ["200"] and champ("TC_si_ok", "success") == ["true"]
         and champ("TC_si_ok", "failureMessage") == [""])
# Les requêtes d'une transaction « parent » sont ses sous-résultats ; le CSV les écrit quand même
# en lignes séparées tant que jmeter.save.saveservice.subresults=true (valeur par défaut).
verifier("mode parent : requêtes écrites comme pour le standard (sous-résultats)",
         len(par["D_ok"]) == len(par["E_ok"]) == 4 and len(par["D_ko"]) == len(par["E_ko"]) == 4)
verifier("mode parent : même code et même message que le standard",
         champ("TD_si_parent", "responseCode") == champ("TE_standard_parent", "responseCode") == ["500"]
         and champ("TD_si_parent", "responseMessage") == champ("TE_standard_parent", "responseMessage"))
verifier("mode parent : premier échec détaillé",
         champ("TD_si_parent", "failureMessage") == ["Premier échec : D_ko (500 Internal Server Error)"])
verifier("passages ignorés tracés : 4 lignes « TF_trace (ignorée) », 204, réussies",
         len(par["TF_trace (ignorée)"]) == 4 and champ("TF_trace (ignorée)", "responseCode") == ["204"]
         and champ("TF_trace (ignorée)", "success") == ["true"])
verifier("pas de ligne « (ignorée) » sans l'option",
         not [l for l in par if l.endswith("(ignorée)") and l != "TF_trace (ignorée)"])
verifier("échec d'assertion : code 200 et message de l'assertion",
         champ("TG_assert", "responseCode") == ["200"]
         and len(champ("TG_assert", "failureMessage")) == 1
         and champ("TG_assert", "failureMessage")[0].startswith("Premier échec : G_ok (200 OK) ; Test failed: text expected to contain /Bienvenue/"))
print("TOUT EST BON" if ok else "ECHEC")
sys.exit(0 if ok else 1)
