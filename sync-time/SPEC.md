# SPEC — Sync time (pauses utilisateur autour des transactions)

Version de référence : **Apache JMeter 5.4.3, Java 11 (bytecode Java 8)**, comme les autres
plugins du dépôt. Statut : **brouillon à valider** (questions en §9). Rédigé le 2026-10-31.

## 1. Le besoin

Simuler le temps de réflexion d'un utilisateur, appelé ici **sync time** : la pause entre
deux actions. Aujourd'hui, il faut ajouter une pause à la main avant ou après chaque
transaction. Le plugin doit :

1. **lister dans une fenêtre tous les contrôleurs Si et contrôleurs de transaction du plan** ;
2. permettre de choisir, pour chacun, **une durée** et **une position** : *avant* les requêtes
   ou *après* ;
3. prendre les durées proposées dans **un fichier de configuration relié au plugin** : une
   liste de durées en secondes (1, 2, 3, 4, 5…) ;
4. permettre d'appliquer **le même choix à toutes les transactions** d'un coup.

## 2. Ce que JMeter offre (sources 5.4.3)

- **Flow Control Action** (`org.apache.jmeter.sampler.TestAction`), action *Pause* : c'est un
  échantillonneur qui dort pendant `ActionProcessor.duration` millisecondes. La durée accepte
  les `${…}`. `sample()` renvoie `null` : **aucune ligne dans le fichier de résultats**. La
  pause est raccourcie si la durée programmée du tir se termine pendant
  (`TIMER_SERVICE.adjustDelay`). Une durée vide ou non numérique vaut 0, avec un
  avertissement dans `jmeter.log`.
- **Minuteries** (Constant Timer, Uniform Random Timer…) : elles s'appliquent avant **chaque**
  échantillonneur de leur portée. Placée dans une transaction, une minuterie retarde chaque
  requête, pas la transaction : ce n'est pas le bon outil ici, sauf placée sous un Flow
  Control Action.
- **Temps de la transaction** : dans un contrôleur de transaction avec *Inclure la durée des
  minuteries…* **décochée**, le temps passé hors des requêtes est retiré de la durée de la
  transaction (`TransactionController.sampleOccurred`, `pauseTime`). Une pause placée dans la
  transaction n'est donc pas comptée. Avec la case **cochée**, elle l'est. Valeur par défaut :
  décochée pour un contrôleur créé depuis l'interface, mais **cochée si la propriété est absente
  du .jmx** (plans anciens ou générés). À vérifier par essai pour le Flow Control Action, qui ne
  produit pas de résultat.

**Choix** : le plugin insère des **Flow Control Action en mode Pause**. Ce sont des éléments
JMeter standard : **le plan tourne sur des injecteurs qui n'ont pas le plugin**, contrairement
au contrôleur de transaction Si. Le plugin ne sert qu'à poser et régler les pauses.

## 3. Le fichier de configuration

Fichier texte, une durée en secondes par ligne. Les décimales sont acceptées (point ou
virgule). Les lignes vides et les commentaires `#` sont ignorés :

```
# Sync times proposés (secondes)
1
2
3
4
5
8
10
15
```

- **Relier le fichier** : bouton *Fichier des sync times…* dans la fenêtre. Le chemin est
  retenu dans les préférences de l'utilisateur, avec le même mécanisme que l'assistant IA :
  propriété système `sync.time.config`, sinon `~/.jmeter-sync-time.txt`. Le fichier est relu à
  chaque ouverture de la fenêtre.
- **Sans fichier** : liste par défaut 1, 2, 3, 5, 10 s, avec un bandeau « aucun fichier relié ».
- **Fichier invalide** : les lignes illisibles sont signalées (numéro et contenu), les autres
  restent utilisables.
- **Durée hors liste** : une pause déjà présente dans le plan avec une durée absente du fichier
  reste affichée telle quelle (« 7 s, hors liste »). Elle n'est pas modifiée tant qu'on ne la
  change pas.

## 4. L'écran

Menu *Outils > Sync time…* et clic droit dans l'arbre, comme *Modification en masse*. La
fenêtre n'est pas modale.

```
┌ Sync time ────────────────────────────────────────────────────────────────────────┐
│ Fichier : ~/sync-times.txt  [Fichier des sync times…]  1 2 3 4 5 8 10 15 s       │
│ Afficher : [x] Transactions  [x] Transactions Si  [x] Contrôleurs Si  Filtre : [___] │
├───┬──────────────────────────────────────┬──────────────┬─────────┬─────────┬─────┤
│ ✓ │ Contrôleur                            │ Type         │ Avant   │ Après   │     │
├───┼──────────────────────────────────────┼──────────────┼─────────┼─────────┼─────┤
│ ☑ │ Parcours > T01_Accueil                │ Transaction  │ —       │ 3 s  ▾  │     │
│ ☑ │ Parcours > T02_Connexion              │ Transaction  │ —       │ 5 s  ▾  │     │
│ ☐ │ Parcours > Si connecté                │ Contrôleur Si│ —       │ —       │ ⚠   │
│ ☑ │ Parcours > Si connecté > T03_Panier   │ Transaction  │ 2 s ▾   │ 3 s  ▾  │     │
├───┴──────────────────────────────────────┴──────────────┴─────────┴─────────┴─────┤
│ Pour les lignes cochées : [Après ▾]  [5 s ▾]  [Appliquer]   [Retirer les pauses]  │
│                              [Aperçu]   [Appliquer les modifications]   [Annuler la dernière] │
└───────────────────────────────────────────────────────────────────────────────────┘
```

- Une ligne par contrôleur, dans l'ordre du plan, avec son chemin. Les éléments désactivés
  sont affichés grisés et décochés.
- Les colonnes *Avant* et *Après* montrent la pause **déjà en place** (lue dans le plan, §5.3)
  et se modifient par une liste déroulante : « — » (aucune) puis les durées du fichier.
- La barre « Pour les lignes cochées » applique d'un coup une position et une durée. Les
  cases d'en-tête cochent tout ou rien. C'est le cas « pour tous les contrôleurs de
  transaction ».
- **Aperçu** : la liste des insertions, modifications et suppressions avant de toucher au
  plan, comme dans *Modification en masse*. **Annuler la dernière** remet le plan dans l'état
  d'avant. Le plan est marqué modifié ; on l'enregistre ensuite avec la commande normale de
  JMeter.
- ⚠ **Imbrication** : si un contrôleur coché est dans un autre contrôleur coché qui a une pause
  à la même position (un Si qui contient une transaction, par exemple), la ligne porte ⚠ et
  l'aperçu le signale, puisque la pause serait jouée deux fois.

## 5. Ce qui est inséré dans le plan

### 5.1 L'élément
Un **Flow Control Action**, action *Pause*, cible *Thread courant* :
- **nom** : `Sync 5 s` (lisible dans l'arbre et dans l'arbre de résultats) ;
- **durée** : `5000` (millisecondes ; voir §9.2 et §9.3 pour les variantes) ;
- **commentaire** : `sync-time:apres:5` (ou `sync-time:avant:5`). C'est cette marque qui permet
  au plugin de reconnaître ses pauses, pour les afficher, les modifier ou les retirer. Une
  pause posée à la main, sans marque, n'est jamais touchée.

### 5.2 Où (proposition, question §9.1)
| Contrôleur | *Avant* | *Après* | Pourquoi |
|---|---|---|---|
| Contrôleur de transaction | frère placé juste **avant** lui | frère placé juste **après** lui | Hors de la transaction : la pause n'est jamais comptée dans son temps de réponse, quelle que soit la case *Inclure la durée des minuteries*. |
| Contrôleur Si | **premier enfant** du Si | **dernier enfant** du Si | Dans le Si : la pause n'est jouée que si la condition est vraie. |
| Contrôleur de transaction Si | **premier enfant** | **dernier enfant** | Dedans, pour ne pas faire la pause quand la transaction est sautée. Le plugin vérifie que *Inclure la durée des minuteries* est décochée, sinon la ligne porte ⚠ et l'aperçu propose de la décocher. |

### 5.3 Relecture
À l'ouverture, le plugin cherche, pour chaque contrôleur, une pause marquée à l'emplacement
prévu par §5.2 et l'affiche. Une pause marquée trouvée ailleurs (déplacée à la main) est
listée à part, dans « Pauses hors place », avec une action *Retirer*.

## 6. Hors périmètre (v1)

- Variation aléatoire de la durée (question §9.4).
- Pauses entre les requêtes **à l'intérieur** d'une transaction.
- Autres contrôleurs (simple, boucle, module…). Ils pourront être ajoutés à la liste plus tard.

## 7. Conception technique

- Module Maven `sync-time`, paquet `fr.jmeter.plugins.synctime`, version 0.1.0. Dépendances
  `ApacheJMeter_core` et `ApacheJMeter_components` (pour `TestAction` et sa GUI
  `TestActionGui`), en scope `provided`.
- `MenuSyncTime` : entrée dans *Outils* et dans le clic droit, sur le modèle de
  `MenuModificationMasse`.
- `FichierSyncTimes` : lecture, analyse et signalement des erreurs du fichier (classe pure,
  testable sans GUI).
- `Inventaire` : parcours de l'arbre (`JMeterTreeModel`), liste des contrôleurs et des pauses
  marquées (classe pure sur le `HashTree`).
- `Plan` : calcul des opérations (insérer, changer la durée, retirer) à partir des choix faits
  à l'écran ; sert à l'aperçu et à l'application. L'annulation garde l'arbre d'avant, comme
  *Modification en masse*.
- `FenetreSyncTime` : l'écran du §4.
- Insertion par `JMeterTreeModel.addComponent` avec un `TestAction` créé par sa GUI
  (`TestActionGui.createTestElement()`, puis `setAction(PAUSE)`, `setTarget(THREAD)`,
  `setDuration(...)`), pour que le .jmx soit identique à un Flow Control Action ajouté à la
  main.

## 8. Essais

**Unitaires**
- fichier : lignes valides, décimales `1.5` et `1,5`, commentaires, lignes vides, doublons,
  lignes invalides, fichier absent ;
- inventaire : plan avec transactions, Si, transactions Si imbriqués, éléments désactivés,
  pauses marquées et non marquées ;
- opérations : insertion avant/après pour chaque type, changement de durée, retrait, pause
  hors place, détection d'imbrication, idempotence (appliquer deux fois ne duplique rien).

**De bout en bout (JMeter 5.4.3 réel)**
- interface sous écran virtuel : ouvrir la fenêtre, relier un fichier, appliquer « Après,
  3 s » à toutes les transactions, enregistrer ; vérifier le .jmx (nombre de Flow Control
  Action, emplacements, durées, marques), puis rouvrir et vérifier que les pauses sont
  relues ; captures pour le README ;
- tir en ligne de commande **sur un JMeter sans le plugin** : le plan s'ouvre et tourne ;
  aucune ligne de pause dans le CSV ; écart entre les horodatages des transactions conforme
  aux pauses (à ±200 ms) ; temps de réponse des transactions inchangés par rapport au même
  plan sans pause ;
- même essai avec *Inclure la durée des minuteries* cochée sur une transaction Si, pour
  confirmer §2 (pause comptée ou non).

## 9. Questions ouvertes

1. **Emplacement** (§5.2) : pause **hors** de la transaction (non comptée dans son temps) mais
   **dans** le contrôleur Si (jouée seulement si la condition est vraie). D'accord ?
   « Avant / Après » s'entend donc pour une transaction : juste avant ou juste après elle.
2. **Lien avec le fichier pendant le tir** :
   - **A (proposé)** : le fichier ne sert qu'à l'écran, comme liste de choix. La durée est
     écrite en dur dans le plan (`5000`). Le plan est autonome. Changer le fichier ne change
     pas les pauses déjà posées ; on rouvre la fenêtre et on réapplique.
   - **B** : le plan référence le fichier. Chaque pause vaut, par exemple,
     `${__P(sync.T02_Connexion,5)}`, et on change les durées sans toucher au plan, en
     modifiant un fichier passé avec `-q`. C'est plus souple, mais le fichier doit suivre le
     plan sur chaque injecteur, et un fichier oublié fait tomber les pauses à la valeur par
     défaut sans prévenir.
3. **Interrupteur global** : faut-il un réglage unique, par exemple `-Jsync_facteur=0`, pour
   couper toutes les pauses (débogage, tir de vérification) ou les doubler (×2) sans toucher
   au plan ? Cela change la durée écrite : une expression au lieu de `5000`.
4. **Variation aléatoire** : pause fixe (proposé), ou durée ± un pourcentage (par exemple
   5 s ± 20 %) pour éviter que tous les utilisateurs virtuels se synchronisent ?
5. **Nom du plugin** : « Sync time » dans les menus, module `sync-time`. D'accord, ou un nom
   français (« Temps de réflexion ») ?
