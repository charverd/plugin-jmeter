# Plugins JMeter

Plugins maison pour Apache JMeter, un module Maven par plugin.

| Module | Rôle |
|---|---|
| [`arbre-resultats-filtrable`](arbre-resultats-filtrable/) | Arbre de résultats avec copie des noms et filtre sur les noms des requêtes et des contrôleurs |
| [`enregistreur-sans-variables`](enregistreur-sans-variables/) | Enregistreur HTTP(S) qui garde les valeurs capturées brutes, sans les remplacer par des `${variables}` |
| [`modification-en-masse`](modification-en-masse/) | Modifier d'un coup les en-têtes HTTP, paramètres de requête, noms et autres champs des éléments sélectionnés ou trouvés par leur nom (aperçu, annulation) |
| [`verificateur-script`](verificateur-script/) | Signale les défauts courants d'un plan avant de le livrer ou de le lancer (14 règles) |
| [`nettoyage-enregistrement`](nettoyage-enregistrement/) | Après un enregistrement : retirer ressources statiques et domaines tiers, regrouper en transactions par page |
| [`correlation-assistee`](correlation-assistee/) | Compare deux enregistrements du même parcours, crée les extracteurs et remplace les valeurs dynamiques par des variables |
| [`assistant-ia`](assistant-ia/) | Assistant IA dans JMeter : n'importe quel modèle à l'API OpenAI, prompt adapté à JMeter, modifications du plan proposées avec aperçu et annulation |

## Construire

Java 11 (ou 8+), Maven 3.9.

```bash
mvn package
```

Chaque module produit un jar dans son dossier `target/`. Pour l'installer :
copier ce jar dans `lib/ext/` de JMeter, puis redémarrer JMeter.

Les modules sont compilés contre JMeter **5.4.3**, la plus ancienne version
visée (propriété `jmeter.version` du pom parent), en bytecode Java 8 : ils
tournent sur JMeter 5.4.3 et suivants, sous Java 8 à 21. Les dépendances JMeter sont en scope `provided` : le jar ne contient
que le code du plugin.

## Ajouter un plugin

1. Créer un dossier de module avec un `pom.xml` dont le parent est
   `plugins-jmeter`, et sa propre `<version>`.
2. Le déclarer dans `<modules>` du pom racine.
3. Paquet Java : `fr.jmeter.plugins.<plugin>`.

## Essais communs

`essais-scripting/lancer.sh` : essai de bout en bout des plugins de scripting
dans la vraie interface de JMeter (application de test qui exige un vrai jeton
CSRF et un vrai numéro de commande, deux enregistrements réels, nettoyage,
corrélation, vérification, puis rejeu du plan corrigé).

## Outils

`outils/bonsai/` : délégation de tâches de code au modèle local Bonsai
(voir son README pour le bilan de l'essai).

