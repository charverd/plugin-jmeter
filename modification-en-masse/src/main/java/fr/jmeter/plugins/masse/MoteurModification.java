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
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
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

    /** Ce qu'on sait d'un en-tête sur l'ensemble du périmètre. */
    public static final class StatEntete {
        private final String nom;
        private int occurrences;
        private final Set<String> valeurs = new TreeSet<>();

        StatEntete(String nom) {
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
    public static List<StatEntete> entetes(Collection<JMeterTreeNode> perimetre) {
        Map<String, StatEntete> stats = new LinkedHashMap<>();
        for (JMeterTreeNode n : perimetre) {
            if (n.getTestElement() instanceof HeaderManager) {
                HeaderManager hm = (HeaderManager) n.getTestElement();
                for (int i = 0; i < hm.size(); i++) {
                    Header h = hm.get(i);
                    StatEntete s = stats.computeIfAbsent(h.getName().toLowerCase(Locale.ROOT),
                            k -> new StatEntete(h.getName()));
                    s.occurrences++;
                    s.valeurs.add(h.getValue());
                }
            }
        }
        List<StatEntete> liste = new ArrayList<>(stats.values());
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
