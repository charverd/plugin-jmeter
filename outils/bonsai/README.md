# Délégation de code à Bonsai

Essai du 2026-10-16 : faire écrire la logique des plugins de scripting par le
modèle local Bonsai (`bonsai2-27b`, llama-swap `127.0.0.1:8090`) pour
décharger Claude.

- `deleguer.py` : une tâche (fichier Markdown de spécification) → appel à
  l'API → fichiers extraits des blocs `// FICHIER: …` → ligne dans
  `journal.tsv` (durée, tokens, fichiers). `--sans-reflexion` désactive la
  phase de raisonnement (`enable_thinking=false`).
- `file.py` : file de tâches à lancer en arrière-plan ; un seul bilan à la fin,
  avancement dans `etat.txt`.
- `taches/` : les spécifications envoyées (copiées dans le wiki, `raw/prompts/bonsai/`).

**Aucune limite de tokens** n'est imposée par ces outils (consigne de l'utilisateur, 2026-10-16) :
seule la limite du serveur s'applique (`--n-predict 40960` dans la configuration llama-swap de
Bonsai au 2026-10-16).

## Bilan (voir `journal.tsv`)

| Essai | Résultat |
|---|---|
| 3 tâches complètes, avec raisonnement, budget 24 000 à 28 000 tokens | 25 à 34 min chacune, budget épuisé en raisonnement, réponse vide |
| 2 demi-tâches (7 règles chacune), budget 32 000 | 17 et 20 min, une réponse vide (arrêt en plein raisonnement), une budget épuisé |
| Une méthode de 40 lignes, sans raisonnement | 17 s, mais trois erreurs de compilation (`pass` Python, variable hors de portée, faute de frappe) |

Constat : avec raisonnement, Bonsai rédige le code **dans** sa réflexion et
n'en sort pas ; sans raisonnement, le code n'est pas fiable. Aucune ligne
produite n'a été retenue : la logique des trois plugins a été écrite par
Claude. Le parallélisme (4 emplacements sur llama-server) porte le débit total
d'environ 29 à environ 45 tokens/s, sans changer le constat.
