# Plugins JMeter

Plugins maison pour Apache JMeter, un module Maven par plugin.

| Module | Rôle |
|---|---|
| [`arbre-resultats-filtrable`](arbre-resultats-filtrable/) | Arbre de résultats avec copie des noms et filtre sur les noms des requêtes et des contrôleurs |
| [`enregistreur-sans-variables`](enregistreur-sans-variables/) | Enregistreur HTTP(S) qui garde les valeurs capturées brutes, sans les remplacer par des `${variables}` |
| [`modification-en-masse`](modification-en-masse/) | Modifier d'un coup les en-têtes HTTP, paramètres de requête, noms et autres champs des éléments sélectionnés ou trouvés par leur nom (aperçu, annulation) |

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
