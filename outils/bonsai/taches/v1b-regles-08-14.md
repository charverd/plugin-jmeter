Tu écris du code Java 8 (pas de `var`, pas de `List.of`, pas de `String.isBlank`) pour un plugin Apache JMeter 5.4.3. Les noms de classes, méthodes, variables et les commentaires sont en français, sans accents dans les identifiants. Livre exactement deux fichiers, chacun précédé d'une ligne `// FICHIER: <chemin>` puis d'un bloc ```java. Rien d'autre après les deux blocs.

## Ce qui existe déjà (ne pas réécrire, à utiliser tel quel)

Paquet `fr.jmeter.plugins.verif` :

```java
public enum Gravite { ERREUR, AVERTISSEMENT, INFO }

public final class Constat {
    public Constat(String regle, Gravite gravite, JMeterTreeNode noeud, String message, String conseil);
    public String getRegle(); public Gravite getGravite(); public JMeterTreeNode getNoeud();
    public String getMessage(); public String getConseil();
}

public interface Regle {
    String id();          // "V01"
    String libelle();     // ce que la règle vérifie
    void verifier(JMeterTreeNode racine, List<Constat> constats);
}

public final class Arbre {
    static boolean estActif(JMeterTreeNode n);                            // n et ses ancêtres activés
    static List<JMeterTreeNode> actifs(JMeterTreeNode racine);            // tous les nœuds actifs, ordre de l'arbre, racine comprise
    static List<JMeterTreeNode> actifsDeType(JMeterTreeNode racine, Class<?> type);   // instanceof
    static List<JMeterTreeNode> actifsDeClasse(JMeterTreeNode racine, String nomClasse); // getClass().getName() exact
    static List<JMeterTreeNode> portee(JMeterTreeNode n);   // éléments actifs qui s'appliquent à n (enfants de n et de chacun de ses ancêtres), sans n
    static boolean aDansSaPortee(JMeterTreeNode n, Class<?> type);
    static JMeterTreeNode ancetre(JMeterTreeNode n, Class<?> type);      // ancêtre le plus proche de ce type, ou null
    static String chemin(JMeterTreeNode n);                              // "Groupe > Transaction > Requête"
}
```

`org.apache.jmeter.gui.tree.JMeterTreeNode` : `new JMeterTreeNode(TestElement e, null)`, `add(JMeterTreeNode enfant)`, `getTestElement()`, `getName()`, `isEnabled()`, `children()`, `getChildCount()`, `getChildAt(i)`, `getParent()`. Activer / désactiver : `noeud.getTestElement().setEnabled(false)`.

## API JMeter 5.4.3 utile (vérifiée)

- `org.apache.jmeter.testelement.TestElement` : `getName()`, `setName(String)`, `getPropertyAsString(String)`, `setProperty(String, String)`, constante `TestElement.GUI_CLASS` (nom de la classe d'écran, propriété texte).
- `org.apache.jmeter.samplers.Sampler` (interface de toutes les requêtes).
- `org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase` : `getDomain()`, `getPath()`, `setDomain(String)`, `setPath(String)`. Pour les tests : `new org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy()`.
- `org.apache.jmeter.assertions.Assertion` (interface) ; pour les tests `new org.apache.jmeter.assertions.ResponseAssertion()`.
- `org.apache.jmeter.timers.Timer` (interface) ; pour les tests `new org.apache.jmeter.timers.ConstantTimer()`.
- `org.apache.jmeter.threads.ThreadGroup` : `getScheduler()`, `setScheduler(boolean)`, `getSamplerController()` (rend un `org.apache.jmeter.control.Controller`), `setSamplerController(LoopController)`.
- `org.apache.jmeter.control.LoopController` : `setLoops(int)`. Pour savoir si la boucle est infinie, lire `getPropertyAsString("LoopController.loops").trim()` : infinie si la valeur vaut `"-1"` ; si elle contient `${`, on ne sait pas : ne rien signaler.
- `org.apache.jmeter.control.TransactionController` : `isGenerateParentSample()`, `setGenerateParentSample(boolean)`.
- `org.apache.jmeter.config.CSVDataSet` : NE PAS utiliser `getFilename()` (vide hors exécution) ; lire `getPropertyAsString("filename")`. Pour les tests : `setProperty("filename", "...")`.
- `org.apache.jmeter.protocol.http.control.CookieManager`, `org.apache.jmeter.protocol.http.control.HeaderManager` (`size()`, `add(new org.apache.jmeter.protocol.http.control.Header(nom, valeur))`).
- `org.apache.jmeter.protocol.http.proxy.ProxyControl` (l'enregistreur HTTP(S) ; ses sous-classes aussi).
- `org.apache.jmeter.reporters.ResultCollector` (tous les récepteurs) : la nature du récepteur se lit dans `getPropertyAsString(TestElement.GUI_CLASS)`.
- `org.apache.jmeter.threads.AbstractThreadGroup` (tous les groupes d'unités), `org.apache.jmeter.testelement.TestPlan`.
- Classes désignées par leur nom (utiliser `Arbre.actifsDeClasse`, pas d'import) : `org.apache.jmeter.sampler.DebugSampler`, `org.apache.jmeter.extractor.DebugPostProcessor`.

## Fichier 1 : `verificateur-script/src/main/java/fr/jmeter/plugins/verif/ReglesB.java`

`final class ReglesB` (paquet `fr.jmeter.plugins.verif`, constructeur privé) contenant, pour chaque règle ci-dessous, une classe imbriquée `static final class Vxx implements Regle` (nom = identifiant, par exemple `V01`), et une méthode `static List<Regle> regles()` qui les rend dans l'ordre du tableau. Ne regarder que les nœuds actifs (utiliser les méthodes d'`Arbre`). Messages et conseils en français, une phrase courte chacun, sans point d'exclamation. Un constat = `new Constat(id(), gravite, noeud, message, conseil)`. Code court : pas de commentaire de plus d'une ligne.

| Id | Gravité | Condition | Nœud du constat | Contenu du message |
|---|---|---|---|---|
| V08 | AVERTISSEMENT | groupe `AbstractThreadGroup` contenant au moins un `Sampler` actif, et aucun `Sampler` actif de ce groupe n'a de `Timer` dans sa portée | le groupe | aucune pause entre les requêtes ; conseil : ajouter une minuterie pour simuler le temps de réflexion |
| V09 | INFO | `TransactionController` avec au moins un `Sampler` actif dans ses descendants, et `isGenerateParentSample()` faux | la transaction | la transaction n'est pas regroupée dans les résultats ; conseil : cocher « Generate parent sample » |
| V10 | AVERTISSEMENT | `TransactionController` sans aucun `Sampler` actif dans ses descendants | la transaction | transaction vide |
| V11 | AVERTISSEMENT | un `CookieManager` dont la portée (`Arbre.portee`) contient un autre `CookieManager` | le second, signalé une seule fois par paire | plusieurs gestionnaires de cookies se superposent ; conseil : n'en garder qu'un, au niveau du groupe |
| V12 | INFO | requêtes `HTTPSamplerBase` dont le nom commence par `/` ou par `http://` ou `https://` : regrouper par nœud parent | le nœud parent | « <n> requête(s) nommée(s) d'après leur URL » ; conseil : les renommer (onglet Renommer de la modification en masse) |
| V13 | INFO | classes d'éléments (et `GUI_CLASS` quand elle est non vide et contient un point) des nœuds actifs dont le nom ne commence pas par `org.apache.jmeter.` : ensemble trié | la racine, un seul constat s'il y en a au moins une | « plugins requis : <classes séparées par « , »> » ; conseil : les installer sur chaque injecteur et chaque poste qui ouvre le plan |
| V14 | AVERTISSEMENT | `HeaderManager` avec `size() == 0` | ce nœud | gestionnaire d'en-têtes vide ; conseil : le supprimer |

Pour « au moins un Sampler actif dans les descendants » : `Arbre.actifsDeType(noeud, Sampler.class)` (le nœud lui-même n'est pas un Sampler).

## Fichier 2 : `verificateur-script/src/test/java/fr/jmeter/plugins/verif/ReglesBTest.java`

JUnit 5 (`org.junit.jupiter.api.Test`, `Assertions.*`). Méthode utilitaire `noeud(TestElement e, JMeterTreeNode parent)` qui crée le nœud et l'ajoute au parent. Méthode `constats(String id, JMeterTreeNode racine)` qui exécute la règle d'identifiant `id` prise dans `ReglesB.regles()`. Pour chaque règle : un test positif (vérifier id, gravité et nœud du constat) et un test négatif. En plus : V11 avec deux gestionnaires de cookies ne donne qu'un constat ; V13 sans aucune classe hors `org.apache.jmeter.` ne donne aucun constat. La racine des tests est un nœud `TestPlan`, avec sous lui un `ThreadGroup` muni d'un `LoopController` à `setLoops(1)`.

Ne rédige pas le code dans ton raisonnement : décide seulement de la structure, puis écris le code directement dans la réponse.
