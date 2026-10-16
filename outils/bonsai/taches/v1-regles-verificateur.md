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

## Fichier 1 : `verificateur-script/src/main/java/fr/jmeter/plugins/verif/Regles.java`

`public final class Regles` avec `public static List<Regle> toutes()` qui rend les 14 règles dans l'ordre V01…V14, chacune étant une classe `static final` imbriquée qui implémente `Regle`. Ne regarder que les nœuds actifs (utiliser les méthodes d'`Arbre`). Messages et conseils en français, une phrase courte chacun, sans point d'exclamation. Un constat = `new Constat(id(), gravite, noeud, message, conseil)`.

| Id | Gravité | Condition | Nœud du constat | Contenu du message |
|---|---|---|---|---|
| V01 | ERREUR | un nœud dont l'élément est un `ProxyControl` (instanceof) | ce nœud | enregistreur HTTP(S) encore dans le plan ; conseil : le supprimer avant de livrer, il est inutile au tir et peut empêcher l'ouverture du plan sans son plugin |
| V02 | AVERTISSEMENT | Debug Sampler ou Debug PostProcessor actif | ce nœud | élément de mise au point actif ; conseil : le désactiver avant le tir |
| V03 | AVERTISSEMENT | `ResultCollector` dont le GUI_CLASS est l'un de : `org.apache.jmeter.visualizers.ViewResultsFullVisualizer`, `org.apache.jmeter.visualizers.TableVisualizer`, `fr.jmeter.plugins.arbre.ArbreResultatsFiltrableVisualizer` | ce nœud | récepteur gourmand actif ; conseil : le désactiver pendant les tirs en charge |
| V04 | ERREUR | CSVDataSet dont `filename` est vide, ou absolu (commence par `/` ou `\`, ou correspond à `^[A-Za-z]:[\\/].*`) ; ne rien signaler si la valeur contient `${` | ce nœud | deux messages distincts : fichier non renseigné / chemin absolu « <valeur> » ; conseil pour l'absolu : chemin relatif au plan, pour qu'il suive sur les injecteurs |
| V05 | AVERTISSEMENT | `ThreadGroup` sans planificateur (`getScheduler()` faux) dont le `getSamplerController()` est un `LoopController` à boucle infinie (voir ci-dessus) | le groupe | le groupe tourne sans fin ; conseil : fixer un nombre d'itérations ou une durée |
| V06 | AVERTISSEMENT | requête `HTTPSamplerBase` sans aucune `Assertion` dans sa portée (`Arbre.aDansSaPortee`) | la requête | aucune assertion ne vérifie la réponse ; conseil : ajouter une assertion de réponse |
| V07 | INFO | requêtes `HTTPSamplerBase` dont `getDomain()` est non vide et ne contient pas `${` : regrouper par valeur de domaine | la première requête de chaque domaine | « serveur « <domaine> » écrit en dur dans <n> requête(s) » ; conseil : le remplacer par une variable (onglet Rechercher / remplacer de la modification en masse) ou un HTTP Request Defaults |
| V08 | AVERTISSEMENT | groupe `AbstractThreadGroup` contenant au moins un `Sampler` actif, et aucun `Sampler` actif de ce groupe n'a de `Timer` dans sa portée | le groupe | aucune pause entre les requêtes ; conseil : ajouter une minuterie pour simuler le temps de réflexion |
| V09 | INFO | `TransactionController` avec au moins un `Sampler` actif dans ses descendants, et `isGenerateParentSample()` faux | la transaction | la transaction n'est pas regroupée dans les résultats ; conseil : cocher « Generate parent sample » |
| V10 | AVERTISSEMENT | `TransactionController` sans aucun `Sampler` actif dans ses descendants | la transaction | transaction vide |
| V11 | AVERTISSEMENT | un `CookieManager` dont la portée (`Arbre.portee`) contient un autre `CookieManager` | le second, signalé une seule fois par paire | plusieurs gestionnaires de cookies se superposent ; conseil : n'en garder qu'un, au niveau du groupe |
| V12 | INFO | requêtes `HTTPSamplerBase` dont le nom commence par `/` ou par `http://` ou `https://` : regrouper par nœud parent | le nœud parent | « <n> requête(s) nommée(s) d'après leur URL » ; conseil : les renommer (onglet Renommer de la modification en masse) |
| V13 | INFO | classes d'éléments (et `GUI_CLASS` quand elle est non vide et contient un point) des nœuds actifs dont le nom ne commence pas par `org.apache.jmeter.` : ensemble trié | la racine, un seul constat s'il y en a au moins une | « plugins requis : <classes séparées par « , »> » ; conseil : les installer sur chaque injecteur et chaque poste qui ouvre le plan |
| V14 | AVERTISSEMENT | `HeaderManager` avec `size() == 0` | ce nœud | gestionnaire d'en-têtes vide ; conseil : le supprimer |

Pour « au moins un Sampler actif dans les descendants » : `Arbre.actifsDeType(noeud, Sampler.class)` (le nœud lui-même n'est pas un Sampler).

## Fichier 2 : `verificateur-script/src/test/java/fr/jmeter/plugins/verif/ReglesTest.java`

JUnit 5 (`org.junit.jupiter.api.Test`, `Assertions.*`). Une méthode utilitaire `noeud(TestElement e, JMeterTreeNode parent)` qui crée le nœud et l'ajoute au parent. Une méthode `constats(String id, JMeterTreeNode racine)` qui exécute la règle d'identifiant `id` prise dans `Regles.toutes()` et rend ses constats. Pour chaque règle, au moins un test positif (constat attendu, vérifier l'id, la gravité et le nœud) et un test négatif (aucun constat). Ajouter : un test que `Regles.toutes()` rend 14 règles d'identifiants V01 à V14 dans l'ordre ; un test qu'un élément désactivé n'est pas signalé (V02) ; un test V04 avec `${dossier}/donnees.csv` sans constat ; un test V05 avec planificateur activé sans constat ; un test V11 qui vérifie qu'une paire ne donne qu'un constat. La racine des tests est un nœud `TestPlan`, avec sous lui un `ThreadGroup` (lui donner un `LoopController` à `setLoops(1)`).
