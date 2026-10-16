Tu écris du code Java 8 (pas de `var`, pas de `List.of`, pas de `Map.of`, pas de `String.isBlank`, pas de `String.repeat`). Les noms de classes, méthodes, variables et les commentaires sont en français, sans accents dans les identifiants. Aucune dépendance autre que le JDK et JUnit 5. Livre exactement quatre fichiers, chacun précédé d'une ligne `// FICHIER: <chemin>` puis d'un bloc ```java. Rien d'autre après les blocs.

Contexte : un plugin JMeter de « corrélation assistée ». L'utilisateur enregistre deux fois le même parcours (enregistrements A et B). Les valeurs qui diffèrent entre A et B (jeton CSRF, identifiant de session, ViewState…) sont dynamiques : il faut les extraire d'une réponse précédente au lieu de les rejouer en dur. L'interface (déjà prévue, ne pas l'écrire) lit les requêtes de l'arbre JMeter en objets `Requete`, appelle tes classes, puis crée les extracteurs et remplace les valeurs elle-même. Tes classes ne connaissent pas JMeter. Paquet `fr.jmeter.plugins.correlation`, répertoire `correlation-assistee/src/main/java/fr/jmeter/plugins/correlation/`.

## Fichier 1 : `Requete.java`

```java
public final class Requete {
    /** Où se trouve une valeur dans la requête. */
    public enum TypeChamp { PARAMETRE, ENTETE, CHEMIN, CORPS }

    /** Un champ porteur de valeur. Pour CHEMIN, nom = "segment <i>" (i à partir de 0) ; pour CORPS, nom = "corps". */
    public static final class Champ {
        public Champ(TypeChamp type, String nom, String valeur);
        public TypeChamp getType(); public String getNom(); public String getValeur();
    }

    public Requete(String nom, String methode, String chemin, List<Champ> champs);
    public String getNom(); public String getMethode(); public String getChemin(); public List<Champ> getChamps();

    /** Clé d'alignement : méthode en majuscules + " " + chemin sans query string, où chaque segment
        composé uniquement de chiffres, ou qui est un UUID (8-4-4-4-12 hexadécimaux), ou une suite
        hexadécimale d'au moins 16 caractères, est remplacé par "*".
        Exemple : méthode "get", chemin "/cmd/12345/detail?x=1" -> "GET /cmd/*/detail". */
    public String cle();

    /** Champs construits à partir du chemin : un Champ CHEMIN par segment non vide, query string retirée. */
    public static List<Champ> segmentsDuChemin(String chemin);
}
```

## Fichier 2 : `Correlation.java`

`public final class Correlation` (constructeur privé, méthodes statiques) avec :

```java
/** Paires (indice dans A, indice dans B) des requêtes alignées, dans l'ordre. */
public static List<int[]> aligner(List<Requete> a, List<Requete> b);
```
Plus longue sous-suite commune sur `cle()` (programmation dynamique classique), pour tolérer des requêtes en plus ou en moins d'un côté.

```java
/** Une valeur dynamique trouvée, avec tous les endroits de A où elle est envoyée. */
public static final class Candidat {
    public String getValeurA(); public String getValeurB();
    public String getNomPropose();              // nom de variable proposé
    public Nature getNature();
    public List<Usage> getUsages();             // dans l'ordre de A
    public int premierUsage();                  // indice dans A de la première requête qui l'envoie
}
public static final class Usage {
    public Usage(int requete, Requete.TypeChamp type, String nom);
    public int getRequete(); public Requete.TypeChamp getType(); public String getNom();
}
public enum Nature { JETON, UUID, NOMBRE, HORODATAGE, TEXTE }

public static List<Candidat> detecter(List<Requete> a, List<Requete> b);
```

Règles de `detecter` :
- pour chaque paire alignée, comparer les champs de même type et même nom (si un nom apparaît plusieurs fois dans une requête, les apparier dans l'ordre) ;
- une différence `valeurA != valeurB` donne une valeur dynamique si `valeurA` n'est ni vide, ni de longueur < 4 (sauf si elle est entièrement numérique et de longueur >= 4), et si le champ n'est pas un ENTETE nommé (sans casse) `Cookie`, `Content-Length`, `Host`, `Referer`, `User-Agent`, `Accept-Language`, `If-Modified-Since`, `If-None-Match` ;
- pour le CORPS : ne pas comparer le corps entier ; découper chaque corps en jetons de valeur (expression `[A-Za-z0-9_\-+/=.%]{4,}`), puis comparer les deux listes de jetons dans l'ordre (même position) ; chaque jeton différent est une valeur dynamique, avec usage `CORPS`, nom `corps` ;
- regrouper les valeurs dynamiques par `valeurA` : un seul Candidat par valeurA (garder la première valeurB vue) ;
- puis ajouter aux usages TOUTES les requêtes de A qui envoient cette valeurA dans un champ quelconque (égalité de champ, ou contenue dans le CORPS), même hors paires alignées ; usages triés par indice de requête puis dans l'ordre des champs, sans doublon (même requête, type, nom) ;
- `getNature` : UUID si la valeur est un UUID ; HORODATAGE si 10 ou 13 chiffres commençant par 1 ; NOMBRE si uniquement des chiffres ; JETON si longueur >= 16 et uniquement `[A-Za-z0-9_\-+/=%]` ; sinon TEXTE ;
- `getNomPropose` : à partir du nom du champ du premier usage : pour PARAMETRE et ENTETE, le nom en minuscules avec tout caractère hors `[a-z0-9_]` remplacé par `_`, `_` multiples réduits, `_` en bord retirés ; pour CHEMIN, `"id_chemin"` ; pour CORPS, `"valeur_corps"` ; préfixer par `c_` si le résultat commence par un chiffre ou est vide (`"c_valeur"`) ; si le nom est déjà pris par un candidat précédent, suffixer `_2`, `_3`… ;
- ordre des candidats : par `premierUsage()` croissant, puis valeurA.

## Fichier 3 : `Bornes.java`

`public final class Bornes` : résultat immuable `getGauche()`, `getDroite()` et méthode statique :

```java
/**
 * Bornes gauche et droite pour extraire `valeur` de `reponse` (texte de la réponse, en-têtes compris).
 * Rend null si la valeur n'y est pas.
 */
public static Bornes calculer(String reponse, String valeur);
```
Règles : prendre la PREMIÈRE occurrence de `valeur` dans `reponse`. Borne droite : le premier caractère qui suit la valeur, plus les suivants tant qu'ils appartiennent à `"'<>&;,) \t\r\n` (au plus 3 caractères au total) ; si la valeur est en fin de texte, borne droite vide. Borne gauche : les caractères qui précèdent, en partant de 3 et en augmentant d'un caractère à la fois jusqu'à 40 au plus, jusqu'à ce que la première occurrence de la borne gauche dans `reponse` soit exactement celle qui précède notre valeur (pour que l'extracteur trouve la bonne) ; borne gauche ne commençant pas par un espace ou un retour à la ligne (en retirer les blancs de tête). Si aucune longueur <= 40 ne convient, prendre 40 caractères (ou moins en début de texte).

## Fichier 4 : `correlation-assistee/src/test/java/fr/jmeter/plugins/correlation/CorrelationTest.java`

JUnit 5. Construire deux enregistrements réalistes de 5 requêtes : 1) `GET /` ; 2) `GET /login` ; 3) `POST /login` avec paramètres `user=alice`, `csrf=Zk81Hq2LmP0aXw9eR4tY` (B : `csrf=Qm27Lp9XcV3bN8wE1rTz`) et en-tête `Cookie: SID=aaaa` (B : `SID=bbbb`) ; 4) `GET /commande/48151623/detail` (B : `/commande/42424242/detail`) avec en-tête `X-Request-Id: 6f1c2a9e-0b3d-4e5f-8a7b-1c2d3e4f5a6b` (B : autre UUID) ; 5) `POST /api/panier` avec un CORPS `{"csrf":"Zk81Hq2LmP0aXw9eR4tY","ts":1792177725718}` (B : `{"csrf":"Qm27Lp9XcV3bN8wE1rTz","ts":1792177799999}`). Ajouter dans B seulement une requête supplémentaire `GET /favicon.ico` entre 2 et 3. Vérifier : `aligner` rend 5 paires et saute la requête en plus ; `cle()` de la requête 4 vaut `GET /commande/*/detail` ; `detecter` trouve le csrf (nature JETON, nom proposé `csrf`, usages : requête 2 PARAMETRE et requête 4 CORPS, premierUsage 2), l'identifiant 48151623 (CHEMIN, NOMBRE, nom `id_chemin`), l'UUID (nature UUID, nom `x_request_id`), l'horodatage 1792177725718 (HORODATAGE) ; et ne signale PAS `SID=aaaa` (en-tête Cookie) ni `alice`. `Bornes` : sur `<input type="hidden" name="csrf" value="Zk81Hq2LmP0aXw9eR4tY"/>`, gauche se termine par `value="` et droite vaut `"/>` ; sur un texte où `value="` apparaît d'abord pour un autre champ, la borne gauche s'allonge jusqu'à être unique ; valeur absente → null ; valeur en fin de texte → droite vide.
