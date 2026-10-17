package fr.jmeter.plugins.sauvegarde;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.tree.TreeNode;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.JMeterGUIComponent;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.visualizers.ViewResultsFullVisualizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrouve les arbres de résultats du plan ouvert et lit leurs résultats.
 *
 * <p>Le View Results Tree garde ses résultats dans un champ privé ({@code buffer}) : pas d'API
 * publique. L'arbre de résultats filtrable (plugin du même projet) garde <b>tous</b> les résultats
 * reçus dans {@code tous}, alors que le {@code buffer} hérité ne contient que ceux que son filtre
 * laisse passer : on lit donc {@code tous}, pour ne rien perdre de ce qui était masqué, et son
 * filtre, que la page HTML réapplique. Aucune dépendance de compilation vers ce plugin : s'il est
 * absent, ou d'une version qui ne connaîtrait pas ces champs, on retombe sur le {@code buffer}.
 */
final class CollecteArbres {

    private static final Logger log = LoggerFactory.getLogger(CollecteArbres.class);

    static final String CLASSE_FILTRABLE = "fr.jmeter.plugins.arbre.ArbreResultatsFiltrableVisualizer";

    /** Un arbre trouvé : son écran (pour le nettoyer) et ce qui sera exporté. */
    static final class Trouve {
        final ViewResultsFullVisualizer ecran;
        final ArbreExporte arbre;

        Trouve(ViewResultsFullVisualizer ecran, ArbreExporte arbre) {
            this.ecran = ecran;
            this.arbre = arbre;
        }
    }

    private CollecteArbres() {
    }

    /** Tous les arbres de résultats du plan, dans l'ordre de l'arbre de JMeter. */
    static List<Trouve> tous(GuiPackage gp) {
        List<Trouve> l = new ArrayList<>();
        Map<Object, Boolean> vus = new IdentityHashMap<>();
        for (JMeterTreeNode n : gp.getTreeModel().getNodesOfType(ResultCollector.class)) {
            JMeterGUIComponent gui = gp.getGui(n.getTestElement());
            if (gui instanceof ViewResultsFullVisualizer && vus.put(gui, Boolean.TRUE) == null) {
                l.add(lire(n, (ViewResultsFullVisualizer) gui));
            }
        }
        return l;
    }

    /** L'arbre de résultats affiché, ou {@code null} si l'élément courant n'en est pas un. */
    static Trouve courant(GuiPackage gp) {
        JMeterTreeNode n = gp.getTreeListener().getCurrentNode();
        if (n == null || !(n.getTestElement() instanceof ResultCollector)) {
            return null;
        }
        JMeterGUIComponent gui = gp.getGui(n.getTestElement());
        return gui instanceof ViewResultsFullVisualizer ? lire(n, (ViewResultsFullVisualizer) gui) : null;
    }

    static Trouve lire(JMeterTreeNode n, ViewResultsFullVisualizer ecran) {
        boolean filtrable = estFiltrable(ecran);
        List<SampleResult> resultats = null;
        Filtre filtre = null;
        if (filtrable) {
            resultats = copier(ecran, "tous");
            filtre = filtreDe(ecran, n.getTestElement());
        }
        if (resultats == null) {
            resultats = copier(ecran, "buffer");
        }
        if (resultats == null) {
            resultats = Collections.emptyList();
            log.warn("Résultats illisibles pour « {} » ({})", n.getName(), ecran.getClass().getName());
        }
        return new Trouve(ecran, new ArbreExporte(n.getName(), chemin(n), filtrable, filtre, resultats));
    }

    static boolean estFiltrable(Object ecran) {
        for (Class<?> c = ecran.getClass(); c != null; c = c.getSuperclass()) {
            if (CLASSE_FILTRABLE.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Copie, sous son verrou, la collection de résultats d'un champ privé. */
    @SuppressWarnings("unchecked")
    static List<SampleResult> copier(Object ecran, String nomChamp) {
        Field f = champ(ecran.getClass(), nomChamp);
        if (f == null) {
            return null;
        }
        try {
            Object v = f.get(ecran);
            if (!(v instanceof Collection)) {
                return null;
            }
            synchronized (v) {
                return new ArrayList<>((Collection<SampleResult>) v);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warn("Lecture de {} impossible", nomChamp, e);
            return null;
        }
    }

    /**
     * Filtre actif de l'arbre filtrable : l'objet {@code filtre} de l'écran (ce qui est appliqué),
     * à défaut les propriétés {@code ArbreFiltrable.*} enregistrées dans l'élément.
     */
    static Filtre filtreDe(Object ecran, TestElement element) {
        Field f = champ(ecran.getClass(), "filtre");
        if (f != null) {
            try {
                Object o = f.get(ecran);
                if (o != null) {
                    return new Filtre((String) appeler(o, "getMotif"), (Boolean) appeler(o, "isRegex"),
                            (Boolean) appeler(o, "isSensibleCasse"), (Boolean) appeler(o, "isExclure"),
                            (Boolean) appeler(o, "isTransactionsSeules"));
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                log.debug("Filtre de l'écran illisible, lecture des propriétés", e);
            }
        }
        if (element == null) {
            return null;
        }
        return new Filtre(element.getPropertyAsString("ArbreFiltrable.motif", ""),
                element.getPropertyAsBoolean("ArbreFiltrable.regex", false),
                element.getPropertyAsBoolean("ArbreFiltrable.sensibleCasse", false),
                element.getPropertyAsBoolean("ArbreFiltrable.exclure", false),
                element.getPropertyAsBoolean("ArbreFiltrable.transactionsSeules", false));
    }

    private static Object appeler(Object o, String methode) throws ReflectiveOperationException {
        Method m = o.getClass().getMethod(methode);
        return m.invoke(o);
    }

    static Field champ(Class<?> classe, String nom) {
        for (Class<?> c = classe; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(nom);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // classe parente
            } catch (RuntimeException e) {
                log.debug("Champ {} inaccessible", nom, e);
                return null;
            }
        }
        return null;
    }

    /** « Plan de test > Groupe > Arbre ». */
    static String chemin(JMeterTreeNode n) {
        List<String> noms = new ArrayList<>();
        // La racine (sans parent) est invisible dans JMeter : on commence au plan de test.
        for (TreeNode t = n; t != null && t.getParent() != null; t = t.getParent()) {
            if (t instanceof JMeterTreeNode) {
                noms.add(0, ((JMeterTreeNode) t).getName());
            }
        }
        return String.join(" > ", noms);
    }
}
