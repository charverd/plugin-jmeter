# SPEC — Sync time (pauses utilisateur autour des transactions)

Version de référence : **Apache JMeter 5.4.3, Java 11 (bytecode Java 8)**, comme les autres
plugins du dépôt. Statut : **brouillon à valider** (questions en §9). Rédigé le 2026-10-31,
révisé le même jour : les durées sont dans **un élément de configuration du plan**, et non
dans un fichier externe.

## 1. Le besoin

Simuler le temps de réflexion d'un utilisateur, appelé ici **sync time** (ou think time) :
la pause entre deux actions.

1. **Un élément de configuration** dans le plan contient **toutes les variables de think time,
   en secondes** : 1, 2, 3, 4, 5…
2. **Une fenêtre liste tous les contrôleurs Si et contrôleurs de transaction** du plan.
3. Pour chacun, on choisit **une durée parmi celles de l'élément de configuration** (3 s,
   5 s…) et **une position** : *avant* les requêtes ou *après*.
4. Le même choix peut s'appliquer **à toutes les transactions** d'un coup.

Les pauses posées dans le plan **font référence aux variables** de l'élément de
configuration. Changer une valeur dans cet élément change donc toutes les pauses qui
l'utilisent, sans repasser par la fenêtre.

## 2. Ce que JMeter offre (sources et essai 5.4.3)

- **Variables définies par l'utilisateur** (*User Defined Variables*, classe `Arguments`) :
  c'est l'élément de configuration standard pour une liste nom = valeur. Les valeurs sont
  évaluées **une fois, au démarrage du tir**, et visibles de tous les groupes d'unités, où
  que l'élément soit placé dans le plan. Une valeur peut elle-même lire une propriété :
  `${__P(TT_5,5)}` permet de la surcharger au lancement avec `-JTT_5=3`.
- **Flow Control Action** (`org.apache.jmeter.sampler.TestAction`), action *Pause* : c'est un
  échantillonneur qui dort pendant `ActionProcessor.duration` **millisecondes**. Il
  **n'écrit aucune ligne dans le fichier de résultats** (`sample()` renvoie `null`, vérifié par
  essai). Une durée vide ou non numérique vaut **0, avec un simple avertissement** dans
  `jmeter.log`. La pause est raccourcie si la durée programmée du tir se termine pendant.
- **Secondes → millisecondes** (essai du 2026-10-31, JMeter 5.4.3, Java 11) :
  - `${__jexl3((${TT_1_5} * 1000).longValue())}` avec `TT_1_5=1.5` donne 1500, et
    `${__jexl3(${TT_2} * 1000)}` avec `TT_2=2` donne 2000. Le tir a duré 3,55 s pour
    2 s + 1,5 s de pauses ;
  - `Math.round(…)` **ne marche pas** dans `__jexl3` (« undefined variable Math ») : la pause
    tombe silencieusement à 0. Sans `.longValue()`, une valeur décimale donne `1500.0`, que
    le Flow Control Action refuse : pause à 0 également.

  Le plugin écrit donc toujours la forme `.longValue()`, qui marche pour les entiers comme
  pour les décimales.
- **Temps de la transaction** : dans un contrôleur de transaction avec *Inclure la durée des
  minuteries…* décochée, le temps passé hors des requêtes est retiré de sa durée. Avec la case
  cochée, il est compté. La case est **cochée si la propriété est absente du .jmx** (plans
  anciens ou générés).

**Choix** : un **élément de configuration standard** (Variables définies par l'utilisateur)
et des **Flow Control Action** standard. Le plan tourne donc **sur des injecteurs qui n'ont
pas le plugin**, contrairement au contrôleur de transaction Si. Le plugin sert à créer
l'élément, à poser les pauses et à les régler.

## 3. L'élément de configuration « Sync times »

Des *Variables définies par l'utilisateur* nommées **Sync times**, placées directement sous
le plan de test, avec le commentaire `sync-time:config` qui permet au plugin de les
reconnaître :

| Nom | Valeur (secondes) |
|---|---|
| `TT_1` | `1` |
| `TT_2` | `2` |
| `TT_3` | `3` |
| `TT_5` | `5` |
| `TT_10` | `10` |

- **Création** : si le plan n'en a pas, la fenêtre propose *Créer l'élément Sync times*, avec
  la liste ci-dessus, modifiable ensuite.
- **Modification** : directement dans l'élément, avec l'écran standard de JMeter : ajouter une
  ligne `TT_8 = 8`, changer `TT_5` à `4`… La fenêtre relit l'élément à chaque ouverture. Elle
  a aussi un bouton *Ajouter une durée* pour ne pas quitter la fenêtre.
- **Valeurs acceptées** : un nombre de secondes, entier ou décimal (`1.5`), ou une expression
  JMeter (`${__P(TT_5,5)}`). Une valeur qui n'est ni un nombre ni une expression est signalée
  dans la fenêtre (⚠), puisqu'elle donnerait une pause de 0 sans erreur au tir.
- **Noms** : libres, mais uniques. Le préfixe `TT_` n'est qu'une proposition (question §9.2).
- **Plusieurs éléments marqués** dans le plan : la fenêtre demande lequel utiliser et
  signale le doublon.

## 4. La fenêtre

Menu *Outils > Sync time…* et clic droit dans l'arbre, comme *Modification en masse*. La
fenêtre n'est pas modale.

```
┌ Sync time ────────────────────────────────────────────────────────────────────────┐
│ Élément : Plan > Sync times   TT_1=1 s  TT_2=2 s  TT_3=3 s  TT_5=5 s  TT_10=10 s   │
│           [Aller à l'élément]  [Ajouter une durée…]                                │
│ Afficher : [x] Transactions  [x] Transactions Si  [x] Contrôleurs Si  Filtre : [___] │
├───┬──────────────────────────────────────┬──────────────┬──────────┬──────────┬────┤
│ ✓ │ Contrôleur                            │ Type         │ Avant    │ Après    │    │
├───┼──────────────────────────────────────┼──────────────┼──────────┼──────────┼────┤
│ ☑ │ Parcours > T01_Accueil                │ Transaction  │ —        │ TT_3 ▾   │    │
│ ☑ │ Parcours > T02_Connexion              │ Transaction  │ —        │ TT_5 ▾   │    │
│ ☐ │ Parcours > Si connecté                │ Contrôleur Si│ —        │ —        │ ⚠  │
│ ☑ │ Parcours > Si connecté > T03_Panier   │ Transaction  │ TT_2 ▾   │ TT_3 ▾   │    │
├───┴──────────────────────────────────────┴──────────────┴──────────┴──────────┴────┤
│ Pour les lignes cochées : [Après ▾]  [TT_5 (5 s) ▾]  [Appliquer]   [Retirer les pauses] │
│                              [Aperçu]   [Appliquer les modifications]   [Annuler la dernière] │
└───────────────────────────────────────────────────────────────────────────────────┘
```

- Une ligne par contrôleur, dans l'ordre du plan, avec son chemin. Les éléments désactivés
  sont affichés grisés et décochés.
- Les colonnes *Avant* et *Après* montrent la pause **déjà en place** (§5.3) et se
  modifient par une liste déroulante : « — » (aucune) puis les variables de l'élément,
  affichées `TT_5 (5 s)`.
- La barre « Pour les lignes cochées » applique d'un coup une position et une durée. Les cases
  d'en-tête cochent tout ou rien. C'est le cas « pour tous les contrôleurs de transaction ».
- **Aperçu** : la liste des insertions, changements et suppressions, avant de toucher au plan.
  **Annuler la dernière** remet le plan dans l'état d'avant. Le plan est marqué modifié ; on
  l'enregistre ensuite avec la commande normale de JMeter.
- ⚠ **Imbrication** : si un contrôleur coché est dans un autre contrôleur coché qui a une pause
  à la même position (un Si qui contient une transaction), la ligne porte ⚠ et l'aperçu le
  signale, puisque la pause serait jouée deux fois.
- Une pause qui référence une variable **absente** de l'élément (renommée ou supprimée) est
  affichée en rouge (« TT_7 : variable inconnue, pause à 0 »).

## 5. Ce qui est inséré dans le plan

### 5.1 L'élément
Un **Flow Control Action**, action *Pause*, cible *Thread courant* :
- **nom** : `Sync TT_5` (lisible dans l'arbre) ;
- **durée** : `${__jexl3((${TT_5} * 1000).longValue())}` (§2) ;
- **commentaire** : `sync-time:apres:TT_5` (ou `sync-time:avant:TT_5`). Cette marque permet au
  plugin de reconnaître ses pauses pour les afficher, les changer ou les retirer. Une pause
  posée à la main, sans marque, n'est jamais touchée.

### 5.2 Où (proposition, question §9.1)
| Contrôleur | *Avant* | *Après* | Pourquoi |
|---|---|---|---|
| Contrôleur de transaction | frère placé juste **avant** lui | frère placé juste **après** lui | Hors de la transaction : la pause n'est jamais comptée dans son temps de réponse, quelle que soit la case *Inclure la durée des minuteries*. |
| Contrôleur Si | **premier enfant** du Si | **dernier enfant** du Si | Dans le Si : la pause n'est jouée que si la condition est vraie. |
| Contrôleur de transaction Si | **premier enfant** | **dernier enfant** | Dedans, pour ne pas faire la pause quand la transaction est sautée. Le plugin vérifie que *Inclure la durée des minuteries* est décochée ; sinon la ligne porte ⚠ et l'aperçu propose de la décocher. |

### 5.3 Relecture
À l'ouverture, le plugin cherche, pour chaque contrôleur, une pause marquée à l'emplacement
prévu par §5.2 et l'affiche. Une pause marquée trouvée ailleurs (déplacée à la main) est
listée à part, dans « Pauses hors place », avec une action *Retirer*.

## 6. Hors périmètre (v1)

- Variation aléatoire de la durée (question §9.3).
- Pauses entre les requêtes **à l'intérieur** d'une transaction.
- Autres contrôleurs (simple, boucle, module…).
- Élément de configuration propre au plugin, avec son propre écran (question §9.4).

## 7. Conception technique

- Module Maven `sync-time`, paquet `fr.jmeter.plugins.synctime`, version 0.1.0. Dépendances
  `ApacheJMeter_core` et `ApacheJMeter_components` (`TestAction`, `TestActionGui`), en scope
  `provided`.
- `MenuSyncTime` : entrée dans *Outils* et dans le clic droit, sur le modèle de
  `MenuModificationMasse`.
- `ElementSyncTimes` : trouve l'élément marqué, lit ses variables (nom, valeur, secondes ou
  expression, ⚠), crée l'élément par défaut. Classe pure sur le `HashTree`, testable sans
  interface.
- `Inventaire` : liste des contrôleurs et des pauses marquées (classe pure).
- `Operations` : calcul des insertions, changements et suppressions à partir des choix de
  l'écran ; sert à l'aperçu et à l'application. L'annulation garde l'arbre d'avant, comme
  *Modification en masse*.
- `FenetreSyncTime` : l'écran du §4.
- Les éléments sont créés par leur écran standard (`ArgumentsPanel`, `TestActionGui`), puis
  insérés par `JMeterTreeModel.addComponent`. Le .jmx est ainsi identique à celui d'éléments
  ajoutés à la main.

## 8. Essais

**Unitaires**
- élément : valeurs entières, décimales, expressions `${…}`, valeurs invalides, noms en double,
  élément absent, deux éléments marqués ;
- inventaire : transactions, Si, transactions Si imbriqués, éléments désactivés, pauses
  marquées et non marquées ;
- opérations : insertion avant/après pour chaque type, changement de variable, retrait, pause
  hors place, variable inconnue, imbrication, idempotence (appliquer deux fois ne duplique
  rien).

**De bout en bout (JMeter 5.4.3 réel)**
- interface sous écran virtuel : ouvrir la fenêtre, créer l'élément Sync times, appliquer
  « Après, TT_3 » à toutes les transactions, enregistrer ; vérifier le .jmx (élément,
  nombre de Flow Control Action, emplacements, expressions, marques), rouvrir et vérifier la
  relecture ; captures pour le README ;
- tir en ligne de commande **sur un JMeter sans le plugin** : le plan s'ouvre et tourne ;
  aucune ligne de pause dans le CSV ; écart entre les transactions conforme aux pauses
  (±200 ms) ; temps de réponse des transactions identiques au même plan sans pause ;
- changer `TT_3` de 3 à 1 dans l'élément, sans toucher aux pauses : le tir suivant raccourcit
  bien les écarts ; surcharge `${__P(TT_3,3)}` avec `-JTT_3=0` : pauses supprimées.

## 9. Questions ouvertes

1. **Emplacement** (§5.2) : pause **hors** de la transaction (non comptée dans son temps),
   mais **dans** le contrôleur Si (jouée seulement si la condition est vraie). D'accord ?
2. **Noms des variables** : `TT_1`, `TT_2`, `TT_5`… (le nom dit la durée de départ), ou des
   niveaux (`TT_court`, `TT_moyen`, `TT_long`) ? Avec un nom qui contient la durée, passer
   `TT_5` à 4 rend le nom trompeur. Avec des niveaux, on ajuste toutes les pauses d'un niveau
   sans renommer.
3. **Variation aléatoire** : pause fixe (proposé), ou valeur ± un pourcentage (5 s ± 20 %) pour
   que les utilisateurs virtuels ne se synchronisent pas ?
4. **Type d'élément** : *Variables définies par l'utilisateur* standard (proposé : le plan
   reste lisible sans le plugin), ou un élément « Sync times » propre au plugin, avec un écran
   dédié (durées seules, contrôle de saisie) ? Dans ce cas, le jar devient obligatoire sur
   chaque injecteur.
5. **Nom du plugin** : « Sync time » dans les menus, module `sync-time`, ou « Temps de
   réflexion » ?
