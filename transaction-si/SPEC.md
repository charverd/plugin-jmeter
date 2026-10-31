# SPEC — Contrôleur de transaction Si

Version de référence : **Apache JMeter 5.4.3, Java 11 (bytecode Java 8)**, comme les autres
plugins du dépôt. Statut : **brouillon à valider** (questions ouvertes en §8). Rédigé le
2026-10-31.

## 1. Le besoin

Aujourd'hui, pour qu'une transaction ne s'exécute que sous condition et apparaisse quand même
dans le fichier de résultats CSV, il faut imbriquer deux éléments :

```
Contrôleur Si (If Controller)            condition : ${__jexl3("${connecte}" == "oui")}
└── Contrôleur de transaction            nom : T03_Panier
    ├── GET /panier
    └── POST /panier/valider
```

Un seul élément doit remplacer les deux :

```
Contrôleur de transaction Si             nom : T03_Panier
│                                        condition : ${__jexl3("${connecte}" == "oui")}
├── GET /panier
└── POST /panier/valider
```

Si la condition est vraie, le contrôleur produit **la même ligne dans le CSV** que la
transaction standard, avec le libellé `T03_Panier`. Si elle est fausse, les enfants sont
sautés (§4.3).

Ce qu'on y gagne : un niveau d'arborescence en moins, la condition visible au même endroit que
la transaction, et plus d'erreurs du type « condition posée sur le mauvais Si » ou
« transaction sortie de son Si » quand on réorganise un script.

## 2. Comportement des deux contrôleurs standard (sources 5.4.3)

Lu dans `org.apache.jmeter.control.IfController` et `TransactionController` (tag
`rel/v5.4.3`).

**If Controller**
- `IfController.condition` : une chaîne. Avec `useExpression=true` (case « Interpréter la
  condition comme une expression de variable », recommandée), elle est vraie si sa valeur
  après substitution vaut `true` (sans tenir compte de la casse), fausse sinon. Avec
  `useExpression=false`, elle est évaluée en JavaScript (Nashorn, ou Rhino si
  `javascript.use_rhino=true`), qui doit renvoyer `true` ou `false`. Toute autre valeur ou
  une exception est journalisée en erreur et vaut **faux**.
- `evaluateAll` : par défaut la condition est évaluée une seule fois, à l'entrée
  (`isFirst()`). Si on coche l'option, elle est réévaluée avant chaque enfant.
- Si la condition est fausse, `initializeSubControllers()` puis `nextIsNull()` : aucun
  enfant ne s'exécute et le contrôleur rend la main.

**Transaction Controller**
- **Mode par défaut** (`TransactionController.parent=false`) : à l'entrée, un
  `SampleResult` est créé avec le libellé = nom du contrôleur. Les enfants exécutés y sont
  cumulés (`sampleOccurred`) : octets reçus et envoyés, latence, temps de connexion,
  échec si un enfant échoue. À la sortie, une ligne est envoyée aux écouteurs avec le message
  `Number of samples in transaction : N, number of failing samples : M`. Le CSV contient donc
  **les lignes des requêtes et une ligne de transaction**.
- **Mode « Generate parent sample »** (`parent=true`) : les requêtes deviennent des
  sous-résultats de la transaction. Dans un CSV, qui ne garde pas les sous-résultats, **seule
  la ligne de transaction reste**.
- `includeTimers` (vrai par défaut) : si l'option est décochée, le temps des minuteries et des
  pré/post-processeurs est soustrait de la durée (il passe dans `idle_time`).
- `triggerEndOfLoop` (par exemple « Start next thread loop » sur erreur) : la transaction en
  cours est clôturée et sa ligne est quand même émise.

## 3. Périmètre de la version 1

**Dedans**
- Un nouveau contrôleur logique « Contrôleur de transaction Si », avec ces options :
  - les deux du Transaction Controller : *Générer un échantillon parent*, *Inclure la durée
    des minuteries et pré/post-processeurs* ;
  - celles du If Controller : *Condition*, *Interpréter comme une expression de variable*
    (cochée par défaut, contrairement au Si standard, parce que c'est la pratique
    recommandée et que c'est bien plus rapide que JavaScript).
- La ligne de transaction dans le CSV (et le XML) quand la condition est vraie, identique à
  celle de l'imbrication Si > Transaction.
- Un comportement explicite quand la condition est fausse (§4.3).
- Un README sur le modèle des autres plugins : besoin, installation, utilisation,
  avertissement sur les injecteurs, limites.

**Dehors (v1)**
- *Évaluer pour tous les enfants* (`evaluateAll`) : réévaluer la condition au milieu d'une
  transaction donnerait une transaction tronquée dont la durée ne veut plus rien dire. Option
  écartée. Si quelqu'un en a besoin, il garde un Si standard à l'intérieur.
- La conversion automatique des imbrications Si > Transaction existantes d'un plan. Elle
  pourrait plus tard rejoindre *Modification en masse*.

## 4. Comportement détaillé

### 4.1 Évaluation de la condition
- Une seule évaluation, **à l'entrée** du contrôleur, à chaque passage (chaque itération,
  chaque tour d'une boucle englobante).
- Les règles sont exactement celles du If Controller (§2) : expression de variable ou
  JavaScript, et toute valeur autre que `true`/`false` ou toute exception vaut **faux**, avec
  le même message d'erreur dans `jmeter.log` (préfixé du nom du contrôleur).
- Une **condition vide** vaut **vrai**. C'est la seule différence avec le Si standard, où
  une condition vide est une erreur qui vaut faux. Un contrôleur de transaction Si sans
  condition se comporte donc comme une transaction normale. (À confirmer, question 8.3.)

### 4.2 Condition vraie
Le contrôleur se comporte **exactement** comme un `TransactionController` avec les mêmes
options : même libellé, mêmes cumuls, même message, mêmes codes, même traitement de
`triggerEndOfLoop` et des transactions imbriquées.

Critère d'acceptation : pour un même plan joué avec la même graine, la ligne de transaction
produite par « Transaction Si » et celle produite par Si > Transaction ont les mêmes colonnes
`label`, `responseCode`, `responseMessage`, `success`, `bytes`, `sentBytes`, `grpThreads`,
`allThreads`, et des valeurs cohérentes pour `elapsed`, `Latency`, `Connect`, `IdleTime`.

### 4.3 Condition fausse
Proposition par défaut, identique à l'imbrication Si > Transaction : **aucun enfant
exécuté, aucune ligne dans le CSV**. Le contrôleur rend la main au parent.

Option proposée (décochée par défaut) : **« Tracer les passages ignorés »**. Elle écrit
une ligne quand la condition est fausse, pour qu'on voie dans le CSV qu'une transaction a été
sautée et combien de fois :
- libellé `<nom> (ignorée)`, un libellé distinct pour ne pas fausser les statistiques de la
  vraie transaction dans le rapport HTML ;
- `elapsed=0`, `success=true`, `responseCode=204`,
  `responseMessage=Condition fausse : <condition après substitution>`, octets à 0.

Cette ligne compte dans le débit global du rapport HTML. Pour l'en écarter, on règle
`jmeter.reportgenerator.exclude_controllers` ou l'expression `sample_filter`. Le README devra
le dire.

### 4.4 Nom du contrôleur
Le libellé de la ligne est le nom de l'élément, avec les `${…}` substitués à l'exécution comme
pour le Transaction Controller standard (exemple : `T03_Panier_${profil}`).

## 5. Conception technique

- Module Maven `transaction-si`, paquet `fr.jmeter.plugins.transactionsi`, déclaré dans
  `<modules>` du pom racine, avec sa propre `<version>` 0.1.0.
- **`TransactionSi extends TransactionController`**, pour hériter tel quel du calcul des durées,
  des cumuls, de `triggerEndOfLoop` et du mode parent, sans rien recopier. Elle redéfinit
  `next()` :
  ```java
  @Override
  public Sampler next() {
      if (isFirst() && !conditionVraie()) {
          // comme IfController : on saute tout le sous-arbre
          if (isTracerIgnorees()) emettreLigneIgnoree();
          initializeSubControllers();
          reInitialize();
          return null;          // (à vérifier par essai : nextIsNull() vs reInitialize())
      }
      return super.next();
  }
  ```
  La condition est évaluée **avant** `super.next()`. Le `SampleResult` ou le
  `TransactionSampler` n'est donc jamais créé quand la condition est fausse, et aucune ligne
  vide n'est émise.
- **Évaluation** : on ne peut pas appeler les méthodes privées de `IfController`. On reprend
  sa logique dans une petite classe `Condition` : expression de variable, et JavaScript par
  `javax.script` avec Nashorn ou Rhino selon `javascript.use_rhino`, un moteur par thread
  (`ThreadLocal`), libéré dans `threadFinished()`. Les mêmes messages d'erreur sont produits.
- **Propriétés enregistrées dans le .jmx** : celles du Transaction Controller
  (`TransactionController.parent`, `TransactionController.includeTimers`), plus
  `TransactionSi.condition`, `TransactionSi.useExpression` et `TransactionSi.traceIgnored`.
  Ce sont des clés propres au plugin : un plan qui l'utilise n'est de toute façon pas lisible
  sans le plugin (§6).
- **GUI** : `TransactionSiGui extends AbstractControllerGui`, rangée dans *Ajouter >
  Contrôleur logique*. Le cadre « Transaction » reprend les deux cases du Transaction
  Controller, le cadre « Condition » a une zone de texte multiligne et deux cases. Libellés en
  français, comme les autres plugins.
- **Icône et aide** : l'icône du Transaction Controller. Pas de page d'aide JMeter, le README
  en tient lieu.

## 6. Points d'attention à documenter

- **Injecteurs** : le .jmx contient la classe `fr.jmeter.plugins.transactionsi.TransactionSi`.
  Un JMeter sans le jar refuse d'ouvrir le plan, **y compris en ligne de commande**
  (`CannotResolveClassException`). Le jar doit donc être copié dans `lib/ext` de **chaque
  injecteur**. Contrairement à l'enregistreur sans variables, on ne peut pas le retirer
  avant le tir, puisqu'il fait partie du scénario.
- **Retour arrière** : documenter comment revenir à Si > Transaction, à la main ou plus tard
  avec *Modification en masse*, pour un client qui ne peut pas installer de plugin.
- **Rapport HTML** : la ligne de transaction est reconnue comme telle (message `Number of
  samples in transaction`), comme pour le standard, ce qui compte pour l'option « exclure les
  transactions ».

## 7. Essais

**Unitaires (JUnit 5, sans GUI)**
- condition `true` / `false` / vide / `TRUE` / `abc` / JavaScript `1 == 1` / JavaScript
  en erreur ;
- condition fausse : aucun enfant appelé, aucun `SampleResult` émis, puis le contrôleur
  rejoue normalement au passage suivant (vrai) ;
- les deux modes (parent ou non) × `includeTimers` : mêmes résultats qu'un
  `TransactionController` témoin ;
- transaction Si imbriquée dans une transaction standard, et l'inverse ;
- `triggerEndOfLoop` au milieu de la transaction ;
- sauvegarde puis rechargement du .jmx avec toutes les propriétés.

**De bout en bout (JMeter 5.4.3 réel, non-GUI)**, sur le modèle de `essais-scripting/` :
- petit serveur local, un plan avec, côte à côte, Si > Transaction et Transaction Si sur la
  même condition (variable qui alterne `oui`/`non` selon l'itération) ;
- `jmeter -n -t plan.jmx -l resultats.csv`, puis comparaison des lignes : mêmes libellés,
  mêmes nombres de lignes, mêmes colonnes ; avec l'option de traçage, présence des lignes
  `(ignorée)` ;
- génération du rapport HTML (`-e -o`) sans erreur ;
- essai GUI avec capture d'écran pour le README (`docs/`), comme les autres plugins.

## 8. Questions ouvertes

1. **« Si »** : la spec suppose que le « contrôleur de transaction SI » est le *Contrôleur Si*
   de JMeter (If Controller). Si c'était autre chose, la spec est à revoir.
2. **Condition fausse** : faut-il seulement sauter la transaction (comme aujourd'hui), ou
   voir quelque chose dans le CSV ? Si oui, l'option « Tracer les passages ignorés » de §4.3
   convient-elle (libellé `<nom> (ignorée)`, durée 0) ?
3. **Condition vide** : vraie (proposé) ou fausse (comme le Si standard) ?
4. **Nom** : « Contrôleur de transaction Si » dans le menu, module `transaction-si`. Un autre
   nom ?
5. **Mode par défaut** : faut-il cocher *Générer un échantillon parent* par défaut, pour
   n'avoir dans le CSV que la ligne de transaction sans les requêtes ? (Le standard ne le
   coche pas.)
