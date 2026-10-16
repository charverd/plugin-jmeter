package fr.jmeter.plugins.masse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.tree.TreeNode;

import org.apache.jmeter.config.Argument;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.protocol.http.util.HTTPArgument;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.PropertyIterator;

/**
 * Calcule et applique des modifications sur un ensemble d'éléments de
 * l'arbre JMeter. Ne dépend pas de l'interface : la fenêtre ne fait que
 * l'appeler.
 */
public final class MoteurModification {

    /** Les champs que « Rechercher / remplacer » peut toucher. */
    public enum Champ {
        ENTETES("Valeurs des en-têtes"),
        PARAMETRES("Paramètres et corps"),
        CHEMIN("Chemin"),
        SERVEUR("Serveur, port, protocole"),
        NOMS("Noms des éléments");

        private final String libelle;

        Champ(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }
    }

    /** Ce que la fenêtre peut modifier par nom : un en-tête HTTP ou un paramètre de requête. */
    public enum Cible {
        ENTETE("En-tête HTTP"),
        PARAMETRE("Paramètre de requête");

        private final String libelle;

        Cible(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }
    }

    /** Ce qu'on sait d'un en-tête ou d'un paramètre sur l'ensemble du périmètre. */
    public static final class StatNom {
        private final String nom;
        private int occurrences;
        private final Set<String> valeurs = new TreeSet<>();

        StatNom(String nom) {
            this.nom = nom;
        }

        public String getNom() {
            return nom;
        }

        public int getOccurrences() {
            return occurrences;
        }

        public Set<String> getValeurs() {
            return valeurs;
        }
    }

    private static final String SEPARATEUR = " > ";

    private MoteurModification() {
    }

    // ------------------------------------------------------------------
    // Périmètre
    // ------------------------------------------------------------------

    /**
     * Les nœuds sélectionnés et, si demandé, tout leur contenu, dans l'ordre
     * de l'arbre et sans doublon (un nœud sélectionné avec son parent ne
     * compte qu'une fois).
     */
    public static List<JMeterTreeNode> perimetre(JMeterTreeNode[] selection, boolean contenu,
            boolean ignorerDesactives) {
        Set<JMeterTreeNode> vus = new LinkedHashSet<>();
        if (selection != null) {
            for (JMeterTreeNode n : selection) {
                ajouter(n, contenu, ignorerDesactives, vus);
            }
        }
        return new ArrayList<>(vus);
    }

    private static void ajouter(JMeterTreeNode n, boolean contenu, boolean ignorerDesactives,
            Set<JMeterTreeNode> vus) {
        if (n == null || (ignorerDesactives && !n.isEnabled())) {
            return;
        }
        vus.add(n);
        if (contenu) {
            Enumeration<?> enfants = n.children();
            while (enfants.hasMoreElements()) {
                ajouter((JMeterTreeNode) enfants.nextElement(), true, ignorerDesactives, vus);
            }
        }
    }

    public static int compter(Collection<JMeterTreeNode> perimetre, Class<?> type) {
        int n = 0;
        for (JMeterTreeNode noeud : perimetre) {
            if (type.isInstance(noeud.getTestElement())) {
                n++;
            }
        }
        return n;
    }

    /** « TC_01 > /login-3 > HTTP Header Manager », sans le plan de test. */
    public static String chemin(JMeterTreeNode noeud) {
        List<String> noms = new ArrayList<>();
        for (TreeNode n = noeud; n instanceof JMeterTreeNode; n = n.getParent()) {
            TestElement e = ((JMeterTreeNode) n).getTestElement();
            if (e instanceof TestPlan) {
                break;
            }
            noms.add(0, e.getName());
        }
        return String.join(SEPARATEUR, noms);
    }

    // ------------------------------------------------------------------
    // En-têtes
    // ------------------------------------------------------------------

    /** En-têtes présents dans le périmètre, regroupés sans tenir compte de la casse du nom. */
    public static List<StatNom> entetes(Collection<JMeterTreeNode> perimetre) {
        Map<String, StatNom> stats = new LinkedHashMap<>();
        for (JMeterTreeNode n : perimetre) {
            if (n.getTestElement() instanceof HeaderManager) {
                HeaderManager hm = (HeaderManager) n.getTestElement();
                for (int i = 0; i < hm.size(); i++) {
                    Header h = hm.get(i);
                    StatNom s = stats.computeIfAbsent(h.getName().toLowerCase(Locale.ROOT),
                            k -> new StatNom(h.getName()));
                    s.occurrences++;
                    s.valeurs.add(h.getValue());
                }
            }
        }
        List<StatNom> liste = new ArrayList<>(stats.values());
        liste.sort((a, b) -> a.getNom().compareToIgnoreCase(b.getNom()));
        return liste;
    }

    /**
     * Donne la valeur {@code valeur} à l'en-tête {@code nom} dans tous les
     * gestionnaires d'en-têtes du périmètre.
     */
    public static List<Modification> definirEntete(Collection<JMeterTreeNode> perimetre, String nom,
            String valeur, boolean ajouterSiAbsent) {
        List<Modification> modifs = new ArrayList<>();
        for (JMeterTreeNode n : perimetre) {
            if (!(n.getTestElement() instanceof HeaderManager)) {
                continue;
            }
            HeaderManager hm = (HeaderManager) n.getTestElement();
            boolean trouve = false;
            for (int i = 0; i < hm.size(); i++) {
                Header h = hm.get(i);
                if (h.getName().equalsIgnoreCase(nom)) {
                    trouve = true;
                    if (!h.getValue().equals(valeur)) {
                        String avant = h.getValue();
                        modifs.add(new Modification(n, chemin(n), "En-tête " + h.getName(), avant, valeur,
                                h::getValue, () -> h.setValue(valeur), () -> h.setValue(avant)));
                    }
                }
            }
            if (!trouve && ajouterSiAbsent) {
                Header nouveau = new Header(nom, valeur);
                modifs.add(new Modification(n, chemin(n), "En-tête " + nom, Modification.ABSENT, valeur,
                        () -> hm.getFirstHeaderNamed(nom) == null ? null : "", // jamais à jour si ajouté entre-temps
                        () -> hm.add(nouveau), () -> retirer(hm, nouveau)));
            }
        }
        return modifs;
    }

    /** Retire l'en-tête {@code nom} de tous les gestionnaires d'en-têtes du périmètre. */
    public static List<Modification> supprimerEntete(Collection<JMeterTreeNode> perimetre, String nom) {
        List<Modification> modifs = new ArrayList<>();
        for (JMeterTreeNode n : perimetre) {
            if (!(n.getTestElement() instanceof HeaderManager)) {
                continue;
            }
            HeaderManager hm = (HeaderManager) n.getTestElement();
            for (int i = 0; i < hm.size(); i++) {
                Header h = hm.get(i);
                if (h.getName().equalsIgnoreCase(nom)) {
                    modifs.add(new Modification(n, chemin(n), "En-tête " + h.getName(), h.getValue(),
                            Modification.SUPPRIME, () -> present(hm, h) ? h.getValue() : null,
                            () -> retirer(hm, h), () -> hm.add(h)));
                }
            }
        }
        return modifs;
    }

    private static boolean present(HeaderManager hm, Header h) {
        for (int i = 0; i < hm.size(); i++) {
            if (hm.get(i) == h) {
                return true;
            }
        }
        return false;
    }

    private static void retirer(HeaderManager hm, Header h) {
        for (int i = 0; i < hm.size(); i++) {
            if (hm.get(i) == h) {
                hm.remove(i);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Par cible : en-tête HTTP ou paramètre de requête
    // ------------------------------------------------------------------

    /** Noms présents dans le périmètre pour la cible, avec leurs valeurs. */
    public static List<StatNom> noms(Collection<JMeterTreeNode> perimetre, Cible cible) {
        return cible == Cible.ENTETE ? entetes(perimetre) : parametres(perimetre);
    }

    /** Donne {@code valeur} à toutes les occurrences de {@code nom}. */
    public static List<Modification> definir(Collection<JMeterTreeNode> perimetre, Cible cible, String nom,
            String valeur, boolean ajouterSiAbsent) {
        return cible == Cible.ENTETE ? definirEntete(perimetre, nom, valeur, ajouterSiAbsent)
                : definirParametre(perimetre, nom, valeur, ajouterSiAbsent);
    }

    /** Retire toutes les occurrences de {@code nom}. */
    public static List<Modification> supprimer(Collection<JMeterTreeNode> perimetre, Cible cible, String nom) {
        return cible == Cible.ENTETE ? supprimerEntete(perimetre, nom) : supprimerParametre(perimetre, nom);
    }

    // ------------------------------------------------------------------
    // Paramètres de requête
    // ------------------------------------------------------------------

    /**
     * Requêtes HTTP du périmètre qui ont des paramètres : celles dont le
     * corps est brut (« Body Data ») n'en ont pas, leur unique argument est
     * le corps.
     */
    private static List<JMeterTreeNode> requetesAParametres(Collection<JMeterTreeNode> perimetre) {
        List<JMeterTreeNode> l = new ArrayList<>();
        for (JMeterTreeNode n : perimetre) {
            if (n.getTestElement() instanceof HTTPSamplerBase
                    && !((HTTPSamplerBase) n.getTestElement()).getPostBodyRaw()) {
                l.add(n);
            }
        }
        return l;
    }

    /** Paramètres présents dans le périmètre ; le nom est comparé en respectant la casse. */
    public static List<StatNom> parametres(Collection<JMeterTreeNode> perimetre) {
        Map<String, StatNom> stats = new LinkedHashMap<>();
        for (JMeterTreeNode n : requetesAParametres(perimetre)) {
            Arguments args = ((HTTPSamplerBase) n.getTestElement()).getArguments();
            for (int i = 0; i < args.getArgumentCount(); i++) {
                Argument a = args.getArgument(i);
                if (a.getName() == null || a.getName().isEmpty()) {
                    continue;
                }
                StatNom s = stats.computeIfAbsent(a.getName(), StatNom::new);
                s.occurrences++;
                s.valeurs.add(a.getValue());
            }
        }
        List<StatNom> liste = new ArrayList<>(stats.values());
        liste.sort((a, b) -> a.getNom().compareToIgnoreCase(b.getNom()));
        return liste;
    }

    /** Donne {@code valeur} au paramètre {@code nom} dans toutes les requêtes du périmètre. */
    public static List<Modification> definirParametre(Collection<JMeterTreeNode> perimetre, String nom,
            String valeur, boolean ajouterSiAbsent) {
        List<Modification> modifs = new ArrayList<>();
        for (JMeterTreeNode n : requetesAParametres(perimetre)) {
            Arguments args = ((HTTPSamplerBase) n.getTestElement()).getArguments();
            boolean trouve = false;
            for (int i = 0; i < args.getArgumentCount(); i++) {
                Argument a = args.getArgument(i);
                if (nom.equals(a.getName())) {
                    trouve = true;
                    if (!valeur.equals(a.getValue())) {
                        String avant = a.getValue();
                        modifs.add(new Modification(n, chemin(n), "Paramètre " + nom, avant, valeur,
                                a::getValue, () -> a.setValue(valeur), () -> a.setValue(avant)));
                    }
                }
            }
            if (!trouve && ajouterSiAbsent) {
                // Comme un paramètre ajouté à la main : encodé dans l'URL à l'envoi.
                HTTPArgument nouveau = new HTTPArgument(nom, valeur);
                modifs.add(new Modification(n, chemin(n), "Paramètre " + nom, Modification.ABSENT, valeur,
                        () -> args.getArgumentsAsMap().containsKey(nom) ? "" : null,
                        () -> args.addArgument(nouveau), () -> retirer(args, nouveau)));
            }
        }
        return modifs;
    }

    /** Retire le paramètre {@code nom} de toutes les requêtes du périmètre. */
    public static List<Modification> supprimerParametre(Collection<JMeterTreeNode> perimetre, String nom) {
        List<Modification> modifs = new ArrayList<>();
        for (JMeterTreeNode n : requetesAParametres(perimetre)) {
            Arguments args = ((HTTPSamplerBase) n.getTestElement()).getArguments();
            for (int i = 0; i < args.getArgumentCount(); i++) {
                Argument a = args.getArgument(i);
                if (nom.equals(a.getName())) {
                    modifs.add(new Modification(n, chemin(n), "Paramètre " + nom, a.getValue(),
                            Modification.SUPPRIME, () -> present(args, a) ? a.getValue() : null,
                            () -> retirer(args, a), () -> args.addArgument(a)));
                }
            }
        }
        return modifs;
    }

    private static boolean present(Arguments args, Argument a) {
        for (int i = 0; i < args.getArgumentCount(); i++) {
            if (args.getArgument(i) == a) {
                return true;
            }
        }
        return false;
    }

    /** Par identité : Arguments.removeArgument(Argument) retirerait aussi les doublons égaux. */
    private static void retirer(Arguments args, Argument a) {
        for (int i = 0; i < args.getArgumentCount(); i++) {
            if (args.getArgument(i) == a) {
                args.removeArgument(i);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Rechercher / remplacer
    // ------------------------------------------------------------------

    /**
     * Prépare le remplacement de {@code recherche} par {@code remplacement}
     * dans les champs choisis des éléments du périmètre.
     *
     * <p>Dans le remplacement, {@code ${...}} est toujours pris à la lettre
     * (référence de variable JMeter), même en mode regex ; {@code $1},
     * {@code $2}… y désignent les groupes capturés.
     *
     * @throws java.util.regex.PatternSyntaxException si la regex est invalide
     */
    public static List<Modification> remplacer(Collection<JMeterTreeNode> perimetre, String recherche,
            String remplacement, boolean regex, boolean sensibleCasse, Set<Champ> champs) {
        List<Modification> modifs = new ArrayList<>();
        if (recherche == null || recherche.isEmpty()) {
            return modifs;
        }
        int options = sensibleCasse ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        Pattern motif = Pattern.compile(regex ? recherche : Pattern.quote(recherche), options);
        String rempl = regex ? remplacement.replace("${", "\\${") : Matcher.quoteReplacement(remplacement);
        Set<Champ> c = champs == null || champs.isEmpty() ? EnumSet.noneOf(Champ.class) : EnumSet.copyOf(champs);

        for (JMeterTreeNode n : perimetre) {
            TestElement e = n.getTestElement();
            String ch = chemin(n);
            if (c.contains(Champ.NOMS)) {
                proposer(modifs, n, ch, "Nom", motif, rempl, e::getName, e::setName);
            }
            if (e instanceof HeaderManager && c.contains(Champ.ENTETES)) {
                HeaderManager hm = (HeaderManager) e;
                for (int i = 0; i < hm.size(); i++) {
                    Header h = hm.get(i);
                    proposer(modifs, n, ch, "En-tête " + h.getName(), motif, rempl, h::getValue, h::setValue);
                }
            }
            if (e instanceof HTTPSamplerBase) {
                HTTPSamplerBase s = (HTTPSamplerBase) e;
                if (c.contains(Champ.SERVEUR)) {
                    propriete(modifs, n, ch, "Protocole", s, HTTPSamplerBase.PROTOCOL, motif, rempl);
                    propriete(modifs, n, ch, "Serveur", s, HTTPSamplerBase.DOMAIN, motif, rempl);
                    propriete(modifs, n, ch, "Port", s, HTTPSamplerBase.PORT, motif, rempl);
                }
                if (c.contains(Champ.CHEMIN)) {
                    propriete(modifs, n, ch, "Chemin", s, HTTPSamplerBase.PATH, motif, rempl);
                }
                if (c.contains(Champ.PARAMETRES)) {
                    boolean corps = s.getPostBodyRaw();
                    PropertyIterator it = s.getArguments().iterator();
                    while (it.hasNext()) {
                        JMeterProperty p = it.next();
                        if (!(p.getObjectValue() instanceof Argument)) {
                            continue;
                        }
                        Argument a = (Argument) p.getObjectValue();
                        if (corps) {
                            proposer(modifs, n, ch, "Corps", motif, rempl, a::getValue, a::setValue);
                        } else {
                            proposer(modifs, n, ch, "Nom du paramètre " + a.getName(), motif, rempl,
                                    a::getName, a::setName);
                            proposer(modifs, n, ch, "Paramètre " + a.getName(), motif, rempl,
                                    a::getValue, a::setValue);
                        }
                    }
                }
            }
        }
        return modifs;
    }

    private static void propriete(List<Modification> modifs, JMeterTreeNode n, String ch, String champ,
            TestElement e, String nomPropriete, Pattern motif, String rempl) {
        // Écriture directe de la propriété : setPath() & co. ont des effets de bord.
        proposer(modifs, n, ch, champ, motif, rempl, () -> e.getPropertyAsString(nomPropriete),
                v -> e.setProperty(nomPropriete, v));
    }

    private static void proposer(List<Modification> modifs, JMeterTreeNode n, String ch, String champ,
            Pattern motif, String rempl, Supplier<String> lire, Consumer<String> ecrire) {
        String avant = lire.get();
        if (avant == null || avant.isEmpty()) {
            return;
        }
        Matcher m = motif.matcher(avant);
        if (!m.find()) {
            return;
        }
        String apres = m.replaceAll(rempl);
        if (!apres.equals(avant)) {
            modifs.add(new Modification(n, ch, champ, avant, apres, lire,
                    () -> ecrire.accept(apres), () -> ecrire.accept(avant)));
        }
    }

    // ------------------------------------------------------------------
    // Chercher des transactions dans tout le plan
    // ------------------------------------------------------------------

    /**
     * Contrôleurs de transaction de tout l'arbre dont le nom correspond,
     * dans l'ordre de l'arbre. Un motif vide les rend tous.
     *
     * @throws java.util.regex.PatternSyntaxException si la regex est invalide
     */
    public static List<JMeterTreeNode> chercherTransactions(JMeterTreeNode racine, String motif,
            boolean regex) {
        List<JMeterTreeNode> trouves = new ArrayList<>();
        Pattern p = motif == null || motif.isEmpty() ? null
                : Pattern.compile(regex ? motif : Pattern.quote(motif),
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        parcourir(racine, n -> {
            if (n.getTestElement() instanceof TransactionController
                    && (p == null || p.matcher(n.getName()).find())) {
                trouves.add(n);
            }
        });
        return trouves;
    }

    private static void parcourir(JMeterTreeNode n, Consumer<JMeterTreeNode> action) {
        if (n == null) {
            return;
        }
        action.accept(n);
        Enumeration<?> enfants = n.children();
        while (enfants.hasMoreElements()) {
            parcourir((JMeterTreeNode) enfants.nextElement(), action);
        }
    }

    /** Nombre de Module Controller du plan : ils désignent leur cible par son nom. */
    public static int compterModuleControllers(JMeterTreeNode racine) {
        int[] n = { 0 };
        parcourir(racine, x -> {
            if ("org.apache.jmeter.control.ModuleController".equals(x.getTestElement().getClass().getName())) {
                n[0]++;
            }
        });
        return n[0];
    }

    // ------------------------------------------------------------------
    // Renommer
    // ------------------------------------------------------------------

    /** Les éléments que l'onglet « Renommer » sait viser. */
    public enum TypeRenomme {
        TRANSACTIONS("Contrôleurs de transaction"),
        REQUETES("Requêtes (échantillonneurs)");

        private final String libelle;

        TypeRenomme(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }

        boolean vise(TestElement e) {
            return this == TRANSACTIONS ? e instanceof TransactionController : e instanceof Sampler;
        }
    }

    /** Réglages de l'onglet « Renommer ». */
    public static final class Renommage {
        /** Remplacé par le nom (après rechercher / remplacer). */
        public static final String NOM = "{nom}";
        /** Remplacé par le numéro, sur {@link #chiffres} chiffres. */
        public static final String NUMERO = "{n}";
        /** Remplacé par le nom (éventuellement nouveau) de l'élément parent. */
        public static final String PARENT = "{parent}";

        Set<TypeRenomme> types = EnumSet.noneOf(TypeRenomme.class);
        String recherche = "";
        String remplacement = "";
        boolean regex;
        boolean sensibleCasse;
        String modele = NOM;
        int debut = 1;
        int chiffres = 2;
        boolean numeroterParTransaction;

        public Renommage types(Set<TypeRenomme> t) {
            types = t.isEmpty() ? EnumSet.noneOf(TypeRenomme.class) : EnumSet.copyOf(t);
            return this;
        }

        public Renommage remplacer(String r, String par, boolean rx, boolean casse) {
            recherche = r == null ? "" : r;
            remplacement = par == null ? "" : par;
            regex = rx;
            sensibleCasse = casse;
            return this;
        }

        public Renommage modele(String m) {
            modele = m == null || m.trim().isEmpty() ? NOM : m;
            return this;
        }

        public Renommage numerotation(int depart, int nbChiffres, boolean parTransaction) {
            debut = depart;
            chiffres = Math.max(1, nbChiffres);
            numeroterParTransaction = parTransaction;
            return this;
        }
    }

    /**
     * Prépare le renommage des éléments visés du périmètre, dans l'ordre de
     * l'arbre. Chaque type a sa propre numérotation ; avec
     * {@code numeroterParTransaction}, celle des requêtes repart à
     * {@code debut} dans chaque contrôleur de transaction. Les transactions
     * sont renommées d'abord, pour que {@code {parent}} donne leur nouveau nom.
     *
     * @throws java.util.regex.PatternSyntaxException si la regex est invalide
     */
    public static List<Modification> renommer(Collection<JMeterTreeNode> perimetre, Renommage r) {
        Pattern motif = null;
        String rempl = null;
        if (!r.recherche.isEmpty()) {
            int options = r.sensibleCasse ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            motif = Pattern.compile(r.regex ? r.recherche : Pattern.quote(r.recherche), options);
            rempl = r.regex ? r.remplacement.replace("${", "\\${") : Matcher.quoteReplacement(r.remplacement);
        }
        Map<JMeterTreeNode, String> nouveaux = new LinkedHashMap<>();
        for (TypeRenomme type : new TypeRenomme[] { TypeRenomme.TRANSACTIONS, TypeRenomme.REQUETES }) {
            if (!r.types.contains(type)) {
                continue;
            }
            Map<Object, Integer> compteurs = new LinkedHashMap<>();
            for (JMeterTreeNode n : perimetre) {
                if (!type.vise(n.getTestElement())) {
                    continue;
                }
                Object cle = type == TypeRenomme.REQUETES && r.numeroterParTransaction ? transactionParente(n) : type;
                int numero = compteurs.merge(cle == null ? type : cle, 1, Integer::sum) - 1 + r.debut;
                String nom = n.getName();
                if (motif != null) {
                    nom = motif.matcher(nom).replaceAll(rempl);
                }
                JMeterTreeNode parent = (JMeterTreeNode) n.getParent();
                String nomParent = parent == null ? "" : nouveaux.getOrDefault(parent, parent.getName());
                String apres = r.modele.replace(Renommage.NOM, nom)
                        .replace(Renommage.NUMERO, numeroter(numero, r.chiffres))
                        .replace(Renommage.PARENT, nomParent);
                nouveaux.put(n, apres);
            }
        }
        List<Modification> modifs = new ArrayList<>();
        // Ordre de l'arbre pour l'aperçu.
        for (JMeterTreeNode n : perimetre) {
            String apres = nouveaux.get(n);
            if (apres != null && !apres.equals(n.getName())) {
                TestElement e = n.getTestElement();
                String avant = e.getName();
                modifs.add(new Modification(n, chemin(n), "Nom", avant, apres, e::getName,
                        () -> e.setName(apres), () -> e.setName(avant)));
            }
        }
        return modifs;
    }

    private static JMeterTreeNode transactionParente(JMeterTreeNode n) {
        for (TreeNode p = n.getParent(); p instanceof JMeterTreeNode; p = p.getParent()) {
            if (((JMeterTreeNode) p).getTestElement() instanceof TransactionController) {
                return (JMeterTreeNode) p;
            }
        }
        return null;
    }

    private static String numeroter(int n, int chiffres) {
        String s = Integer.toString(n);
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < chiffres; i++) {
            sb.append('0');
        }
        return sb.append(s).toString();
    }

    // ------------------------------------------------------------------
    // Application
    // ------------------------------------------------------------------

    /** Résultat d'une application : ce qui a été écrit, ce qui a été écarté. */
    public static final class Bilan {
        private final List<Modification> appliquees = new ArrayList<>();
        private int perimees;

        public List<Modification> getAppliquees() {
            return appliquees;
        }

        /** Modifications écartées parce que l'élément a changé depuis l'aperçu. */
        public int getPerimees() {
            return perimees;
        }

        public Set<JMeterTreeNode> noeudsTouches() {
            Set<JMeterTreeNode> noeuds = new LinkedHashSet<>();
            for (Modification m : appliquees) {
                noeuds.add(m.getNoeud());
            }
            return noeuds;
        }
    }

    /**
     * Écrit les modifications cochées. Une modification dont l'élément ne
     * contient plus la valeur vue à l'aperçu est écartée, pas forcée.
     */
    public static Bilan appliquer(List<Modification> modifs) {
        Bilan bilan = new Bilan();
        for (Modification m : modifs) {
            if (!m.isCochee()) {
                continue;
            }
            if (!m.estAJour()) {
                bilan.perimees++;
                continue;
            }
            m.ecrire();
            bilan.appliquees.add(m);
        }
        return bilan;
    }

    /** Défait un bilan, dans l'ordre inverse. */
    public static void defaire(Bilan bilan) {
        List<Modification> liste = bilan.getAppliquees();
        for (int i = liste.size() - 1; i >= 0; i--) {
            liste.get(i).defaire();
        }
    }
}
