package fr.jmeter.plugins.verif;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.jmeter.assertions.Assertion;
import org.apache.jmeter.config.CSVDataSet;
import org.apache.jmeter.control.Controller;
import org.apache.jmeter.control.LoopController;
import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.control.CookieManager;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.proxy.ProxyControl;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerBase;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.threads.AbstractThreadGroup;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.timers.Timer;

/** Les règles du vérificateur, V01 à V14. */
public final class Regles {

    private Regles() {
    }

    public static List<Regle> toutes() {
        return Collections.unmodifiableList(Arrays.<Regle>asList(new V01(), new V02(), new V03(), new V04(),
                new V05(), new V06(), new V07(), new V08(), new V09(), new V10(), new V11(), new V12(),
                new V13(), new V14()));
    }

    /** Base commune : identifiant et libellé. */
    private abstract static class Base implements Regle {
        private final String id;
        private final String libelle;

        Base(String id, String libelle) {
            this.id = id;
            this.libelle = libelle;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String libelle() {
            return libelle;
        }

        void ajouter(List<Constat> l, Gravite g, JMeterTreeNode n, String message, String conseil) {
            l.add(new Constat(id, g, n, message, conseil));
        }
    }

    static final class V01 extends Base {
        V01() {
            super("V01", "enregistreur HTTP(S) resté dans le plan");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            for (JMeterTreeNode n : Arbre.actifsDeType(racine, ProxyControl.class)) {
                ajouter(c, Gravite.ERREUR, n, "enregistreur HTTP(S) encore dans le plan",
                        "le supprimer avant de livrer : il est inutile au tir et peut empêcher"
                                + " d'ouvrir le plan sans son plugin");
            }
        }
    }

    static final class V02 extends Base {
        static final String[] CLASSES = { "org.apache.jmeter.sampler.DebugSampler",
                "org.apache.jmeter.extractor.DebugPostProcessor" };

        V02() {
            super("V02", "élément de mise au point actif");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            for (JMeterTreeNode n : Arbre.actifs(racine)) {
                if (Arrays.asList(CLASSES).contains(n.getTestElement().getClass().getName())) {
                    ajouter(c, Gravite.AVERTISSEMENT, n, "élément de mise au point actif",
                            "le désactiver avant le tir");
                }
            }
        }
    }

    static final class V03 extends Base {
        static final Set<String> GOURMANDS = new HashSet<>(Arrays.asList(
                "org.apache.jmeter.visualizers.ViewResultsFullVisualizer",
                "org.apache.jmeter.visualizers.TableVisualizer",
                "fr.jmeter.plugins.arbre.ArbreResultatsFiltrableVisualizer"));

        V03() {
            super("V03", "récepteur gourmand actif");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            for (JMeterTreeNode n : Arbre.actifsDeType(racine, ResultCollector.class)) {
                if (GOURMANDS.contains(n.getTestElement().getPropertyAsString(TestElement.GUI_CLASS))) {
                    ajouter(c, Gravite.AVERTISSEMENT, n, "récepteur gourmand actif (mémoire et processeur)",
                            "le désactiver pendant les tirs en charge");
                }
            }
        }
    }

    static final class V04 extends Base {
        private static final Pattern WINDOWS = Pattern.compile("^[A-Za-z]:[\\\\/].*");

        V04() {
            super("V04", "fichier CSV absent ou en chemin absolu");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            for (JMeterTreeNode n : Arbre.actifsDeType(racine, CSVDataSet.class)) {
                String f = n.getTestElement().getPropertyAsString("filename").trim();
                if (f.contains("${")) {
                    continue;
                }
                if (f.isEmpty()) {
                    ajouter(c, Gravite.ERREUR, n, "fichier CSV non renseigné", "indiquer le fichier de données");
                } else if (f.startsWith("/") || f.startsWith("\\") || WINDOWS.matcher(f).matches()) {
                    ajouter(c, Gravite.ERREUR, n, "chemin absolu « " + f + " »",
                            "utiliser un chemin relatif au plan, pour qu'il suive sur les injecteurs");
                }
            }
        }
    }

    static final class V05 extends Base {
        V05() {
            super("V05", "groupe d'unités sans fin");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            for (JMeterTreeNode n : Arbre.actifsDeType(racine, ThreadGroup.class)) {
                ThreadGroup g = (ThreadGroup) n.getTestElement();
                Controller ctl = g.getSamplerController();
                if (g.getScheduler() || !(ctl instanceof LoopController)) {
                    continue;
                }
                String boucles = ((LoopController) ctl).getPropertyAsString("LoopController.loops").trim();
                if ("-1".equals(boucles)) {
                    ajouter(c, Gravite.AVERTISSEMENT, n, "le groupe tourne sans fin (boucle infinie, sans durée)",
                            "fixer un nombre d'itérations ou une durée");
                }
            }
        }
    }

    static final class V06 extends Base {
        V06() {
            super("V06", "requête sans assertion");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            for (JMeterTreeNode n : Arbre.actifsDeType(racine, HTTPSamplerBase.class)) {
                if (!Arbre.aDansSaPortee(n, Assertion.class)) {
                    ajouter(c, Gravite.AVERTISSEMENT, n, "aucune assertion ne vérifie la réponse",
                            "ajouter une assertion de réponse");
                }
            }
        }
    }

    static final class V07 extends Base {
        V07() {
            super("V07", "serveur écrit en dur");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            Map<String, List<JMeterTreeNode>> parDomaine = new LinkedHashMap<>();
            for (JMeterTreeNode n : Arbre.actifsDeType(racine, HTTPSamplerBase.class)) {
                String d = ((HTTPSamplerBase) n.getTestElement()).getDomain();
                if (d != null && !d.trim().isEmpty() && !d.contains("${")) {
                    parDomaine.computeIfAbsent(d.trim(), k -> new ArrayList<>()).add(n);
                }
            }
            for (Map.Entry<String, List<JMeterTreeNode>> e : parDomaine.entrySet()) {
                ajouter(c, Gravite.INFO, e.getValue().get(0), "serveur « " + e.getKey() + " » écrit en dur dans "
                        + e.getValue().size() + " requête(s)",
                        "le remplacer par une variable (Rechercher / remplacer de la modification en masse)"
                                + " ou un HTTP Request Defaults");
            }
        }
    }

    static final class V08 extends Base {
        V08() {
            super("V08", "groupe d'unités sans minuterie");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            for (JMeterTreeNode g : Arbre.actifsDeType(racine, AbstractThreadGroup.class)) {
                List<JMeterTreeNode> requetes = Arbre.actifsDeType(g, Sampler.class);
                if (requetes.isEmpty()) {
                    continue;
                }
                boolean pause = false;
                for (JMeterTreeNode r : requetes) {
                    if (Arbre.aDansSaPortee(r, Timer.class)) {
                        pause = true;
                        break;
                    }
                }
                if (!pause) {
                    ajouter(c, Gravite.AVERTISSEMENT, g, "aucune pause entre les requêtes",
                            "ajouter une minuterie pour simuler le temps de réflexion");
                }
            }
        }
    }

    static final class V09 extends Base {
        V09() {
            super("V09", "transaction sans échantillon parent");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            for (JMeterTreeNode n : Arbre.actifsDeType(racine, TransactionController.class)) {
                TransactionController tc = (TransactionController) n.getTestElement();
                if (!tc.isGenerateParentSample() && !Arbre.actifsDeType(n, Sampler.class).isEmpty()) {
                    ajouter(c, Gravite.INFO, n, "la transaction n'est pas regroupée dans les résultats",
                            "cocher « Generate parent sample »");
                }
            }
        }
    }

    static final class V10 extends Base {
        V10() {
            super("V10", "transaction vide");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            for (JMeterTreeNode n : Arbre.actifsDeType(racine, TransactionController.class)) {
                if (Arbre.actifsDeType(n, Sampler.class).isEmpty()) {
                    ajouter(c, Gravite.AVERTISSEMENT, n, "transaction vide", "la supprimer ou y ajouter des requêtes");
                }
            }
        }
    }

    static final class V11 extends Base {
        V11() {
            super("V11", "gestionnaires de cookies superposés");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            Set<JMeterTreeNode> signales = new HashSet<>();
            for (JMeterTreeNode n : Arbre.actifsDeType(racine, CookieManager.class)) {
                for (JMeterTreeNode autre : Arbre.portee(n)) {
                    if (autre.getTestElement() instanceof CookieManager && signales.contains(autre)) {
                        continue;
                    }
                    if (autre.getTestElement() instanceof CookieManager && Arbre.estActif(autre)) {
                        ajouter(c, Gravite.AVERTISSEMENT, n, "plusieurs gestionnaires de cookies se superposent",
                                "n'en garder qu'un, au niveau du groupe d'unités");
                        signales.add(n);
                        break;
                    }
                }
            }
        }
    }

    static final class V12 extends Base {
        V12() {
            super("V12", "requêtes nommées d'après leur URL");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            Map<JMeterTreeNode, Integer> parParent = new LinkedHashMap<>();
            for (JMeterTreeNode n : Arbre.actifsDeType(racine, HTTPSamplerBase.class)) {
                String nom = n.getName();
                if (nom.startsWith("/") || nom.startsWith("http://") || nom.startsWith("https://")) {
                    parParent.merge((JMeterTreeNode) n.getParent(), 1, Integer::sum);
                }
            }
            for (Map.Entry<JMeterTreeNode, Integer> e : parParent.entrySet()) {
                ajouter(c, Gravite.INFO, e.getKey(), e.getValue() + " requête(s) nommée(s) d'après leur URL",
                        "les renommer (onglet Renommer de la modification en masse)");
            }
        }
    }

    static final class V13 extends Base {
        V13() {
            super("V13", "plugins requis");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            Set<String> classes = new TreeSet<>();
            for (JMeterTreeNode n : Arbre.actifs(racine)) {
                TestElement e = n.getTestElement();
                garder(classes, e.getClass().getName());
                garder(classes, e.getPropertyAsString(TestElement.GUI_CLASS));
            }
            if (!classes.isEmpty()) {
                ajouter(c, Gravite.INFO, racine, "plugins requis : " + String.join(", ", classes),
                        "les installer sur chaque injecteur et chaque poste qui ouvre le plan");
            }
        }

        private static void garder(Set<String> classes, String nom) {
            if (nom != null && nom.contains(".") && !nom.startsWith("org.apache.jmeter.")) {
                classes.add(nom);
            }
        }
    }

    static final class V14 extends Base {
        V14() {
            super("V14", "gestionnaire d'en-têtes vide");
        }

        @Override
        public void verifier(JMeterTreeNode racine, List<Constat> c) {
            for (JMeterTreeNode n : Arbre.actifsDeType(racine, HeaderManager.class)) {
                if (((HeaderManager) n.getTestElement()).size() == 0) {
                    ajouter(c, Gravite.AVERTISSEMENT, n, "gestionnaire d'en-têtes vide", "le supprimer");
                }
            }
        }
    }
}
