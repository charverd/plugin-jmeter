# Plugins JMeter

Plugins maison pour Apache JMeter, un module Maven par plugin.

| Module | Rôle |
|---|---|
| [`arbre-resultats-filtrable`](arbre-resultats-filtrable/) | Arbre de résultats avec copie des noms et filtre sur les noms des requêtes et des contrôleurs |

## Construire

Java 8 ou plus récent, Maven 3.9.

```bash
mvn package
```

Chaque module produit un jar dans son dossier `target/`. Pour l'installer :
copier ce jar dans `lib/ext/` de JMeter, puis redémarrer JMeter.

Les modules ciblent JMeter **5.6.3** (propriété `jmeter.version` du pom
parent). Les dépendances JMeter sont en scope `provided` : le jar ne contient
que le code du plugin.

## Ajouter un plugin

1. Créer un dossier de module avec un `pom.xml` dont le parent est
   `plugins-jmeter`.
2. Le déclarer dans `<modules>` du pom racine.
3. Paquet Java : `fr.charverd.jmeter.<plugin>`.
