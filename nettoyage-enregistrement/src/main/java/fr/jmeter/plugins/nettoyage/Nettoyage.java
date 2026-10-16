package fr.jmeter.plugins.nettoyage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.control.gui.TransactionControllerGui;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;

import fr.jmeter.plugins.nettoyage.AnalyseEnregistrement.Groupe;
import fr.jmeter.plugins.nettoyage.AnalyseEnregistrement.RequeteVue;

/**
 * Lit les requêtes enregistrées sous les conteneurs choisis, prépare et
 * applique le nettoyage. La logique de décision est dans
 * {@link AnalyseEnregistrement} ; ici, seulement l'arbre JMeter.
 */
final class Nettoyage {

    /** Une requête de l'arbre et ce qu'on en sait. */
    static final class Requete {
        final JMeterTreeNode noeud;
        final RequeteVue vue;

        Requete(JMeterTreeNode noeud, RequeteVue vue) {
            this.noeud = noeud;
            this.vue = vue;
        }
    }

    /** Réglages choisis dans la fenêtre. */
    static final class Reglages {
        boolean supprimerStatiques;
        Set<String> extensions = Collections.emptySet();
        Set<String> domainesASupprimer = Collections.emptySet();
        boolean regrouper;
        String modele = "T{n}_{page}";
        int chiffres = 2;
        boolean parentSample = true;
    }

    /** Ce qui sera fait, pour l'aperçu. */
    static final class Plan {
        final List<Requete> aSupprimer = new ArrayList<>();
        final List<String> raisons = new ArrayList<>();
        /** Par conteneur : les groupes de requêtes directes restantes. */
        final List<JMeterTreeNode> conteneurs = new ArrayList<>();
        final List<List<Requete>> requetesDirectes = new ArrayList<>();
        final List<List<Groupe>> groupes = new ArrayList<>();
        int dejaEnTransaction;
    }

    private Nettoyage() {
    }

    /** Requêtes HTTP actives sous les conteneurs, dans l'ordre de l'arbre, sans doublon. */
    static List<Requete> lire(List<JMeterTreeNode> conteneurs) {
        Set<JMeterTreeNode> vus = new LinkedHashSet<>();
        for (JMeterTreeNode c : conteneurs) {
            collecter(c, vus);
        }
        List<Requete> l = new ArrayList<>();
        for (JMeterTreeNode n : vus) {
            l.add(new Requete(n, vue(n)));
        }
        return l;
    }

    private static void collecter(JMeterTreeNode n, Set<JMeterTreeNode> vus) {
        if (!n.isEnabled()) {
            return;
        }
        if (n.getTestElement() instanceof HTTPSamplerBase) {
            vus.add(n);
        }
        Enumeration<?> e = n.children();
        while (e.hasMoreElements()) {
            collecter((JMeterTreeNode) e.nextElement(), vus);
        }
    }

    static RequeteVue vue(JMeterTreeNode n) {
        HTTPSamplerBase s = (HTTPSamplerBase) n.getTestElement();
        String accept = null;
        Enumeration<?> e = n.children();
        while (e.hasMoreElements()) {
            Object el = ((JMeterTreeNode) e.nextElement()).getTestElement();
            if (el instanceof HeaderManager) {
                Header h = ((HeaderManager) el).getFirstHeaderNamed("Accept");
                if (h != null) {
                    accept = h.getValue();
                }
            }
        }
        return new RequeteVue(s.getDomain(), s.getPath(), s.getMethod(), accept);
    }

    static Plan preparer(List<JMeterTreeNode> conteneurs, Reglages r) {
        Plan plan = new Plan();
        List<Requete> toutes = lire(conteneurs);
        Set<JMeterTreeNode> supprimees = new LinkedHashSet<>();
        for (Requete q : toutes) {
            String raison = null;
            if (r.supprimerStatiques && AnalyseEnregistrement.estRessourceStatique(q.vue, r.extensions)) {
                raison = "ressource statique (." + AnalyseEnregistrement.extension(q.vue.getChemin()) + ")";
            } else if (r.domainesASupprimer.contains(minuscules(q.vue.getDomaine()))) {
                raison = "domaine " + q.vue.getDomaine();
            }
            if (raison != null) {
                plan.aSupprimer.add(q);
                plan.raisons.add(raison);
                supprimees.add(q.noeud);
            }
        }
        if (r.regrouper) {
            for (JMeterTreeNode c : conteneurs) {
                if (c.getTestElement() instanceof TransactionController) {
                    continue;
                }
                List<Requete> directes = new ArrayList<>();
                Enumeration<?> e = c.children();
                while (e.hasMoreElements()) {
                    JMeterTreeNode enfant = (JMeterTreeNode) e.nextElement();
                    if (enfant.isEnabled() && enfant.getTestElement() instanceof HTTPSamplerBase
                            && !supprimees.contains(enfant)) {
                        directes.add(new Requete(enfant, vue(enfant)));
                    }
                }
                if (directes.isEmpty()) {
                    continue;
                }
                List<RequeteVue> vues = new ArrayList<>();
                for (Requete q : directes) {
                    vues.add(q.vue);
                }
                plan.conteneurs.add(c);
                plan.requetesDirectes.add(directes);
                plan.groupes.add(AnalyseEnregistrement.regrouperParPage(vues, r.extensions));
            }
            for (Requete q : toutes) {
                if (!supprimees.contains(q.noeud) && q.noeud.getParent() instanceof JMeterTreeNode
                        && ((JMeterTreeNode) q.noeud.getParent()).getTestElement() instanceof TransactionController) {
                    plan.dejaEnTransaction++;
                }
            }
        }
        return plan;
    }

    /** Applique le plan ; rend de quoi le défaire. */
    static OperationsArbre appliquer(Plan plan, Reglages r) {
        GuiPackage gp = GuiPackage.getInstance();
        JMeterTreeModel modele = gp.getTreeModel();
        OperationsArbre ops = new OperationsArbre(modele);
        for (Requete q : plan.aSupprimer) {
            ops.supprimer(q.noeud);
        }
        int numero = 0;
        for (int i = 0; i < plan.conteneurs.size(); i++) {
            JMeterTreeNode c = plan.conteneurs.get(i);
            List<Requete> directes = plan.requetesDirectes.get(i);
            for (Groupe g : plan.groupes.get(i)) {
                numero++;
                TransactionController tc = (TransactionController) gp.createTestElement(
                        TransactionControllerGui.class, TransactionController.class);
                tc.setName(AnalyseEnregistrement.nomTransaction(r.modele, numero, r.chiffres, g.getPage()));
                tc.setGenerateParentSample(r.parentSample);
                JMeterTreeNode premier = directes.get(g.getDebut()).noeud;
                JMeterTreeNode noeudTc = new JMeterTreeNode(tc, modele);
                ops.inserer(noeudTc, c, c.getIndex(premier));
                for (int k = g.getDebut(); k < g.getFin(); k++) {
                    ops.deplacer(directes.get(k).noeud, noeudTc);
                }
            }
        }
        return ops;
    }

    static int nombreDeTransactions(Plan plan) {
        int n = 0;
        for (List<Groupe> g : plan.groupes) {
            n += g.size();
        }
        return n;
    }

    static String minuscules(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
