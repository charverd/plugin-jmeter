package fr.jmeter.plugins.verif;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.tree.TreeNode;

import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestPlan;

/** Outils de parcours de l'arbre JMeter communs aux règles. */
public final class Arbre {

    private Arbre() {
    }

    /** Le nœud et tous ses ancêtres sont-ils activés ? */
    public static boolean estActif(JMeterTreeNode n) {
        for (TreeNode p = n; p instanceof JMeterTreeNode; p = p.getParent()) {
            if (!((JMeterTreeNode) p).isEnabled()) {
                return false;
            }
        }
        return true;
    }

    /** Tous les nœuds actifs sous {@code racine} (elle comprise), dans l'ordre de l'arbre. */
    public static List<JMeterTreeNode> actifs(JMeterTreeNode racine) {
        List<JMeterTreeNode> l = new ArrayList<>();
        if (racine != null && estActif(racine)) {
            parcourir(racine, l);
        }
        return l;
    }

    private static void parcourir(JMeterTreeNode n, List<JMeterTreeNode> l) {
        if (!n.isEnabled()) {
            return;
        }
        l.add(n);
        Enumeration<?> e = n.children();
        while (e.hasMoreElements()) {
            parcourir((JMeterTreeNode) e.nextElement(), l);
        }
    }

    /** Nœuds actifs de {@code racine} dont l'élément est une instance de {@code type}. */
    public static List<JMeterTreeNode> actifsDeType(JMeterTreeNode racine, Class<?> type) {
        List<JMeterTreeNode> l = new ArrayList<>();
        for (JMeterTreeNode n : actifs(racine)) {
            if (type.isInstance(n.getTestElement())) {
                l.add(n);
            }
        }
        return l;
    }

    /** Nœuds actifs de {@code racine} dont la classe de l'élément porte exactement ce nom. */
    public static List<JMeterTreeNode> actifsDeClasse(JMeterTreeNode racine, String nomClasse) {
        List<JMeterTreeNode> l = new ArrayList<>();
        for (JMeterTreeNode n : actifs(racine)) {
            if (n.getTestElement().getClass().getName().equals(nomClasse)) {
                l.add(n);
            }
        }
        return l;
    }

    /**
     * Éléments actifs qui s'appliquent à {@code n} selon les règles de portée
     * de JMeter : les enfants de {@code n}, et les enfants de chacun de ses
     * ancêtres (donc ses frères, ceux de son parent, etc.). {@code n} lui-même
     * n'en fait pas partie.
     */
    public static List<JMeterTreeNode> portee(JMeterTreeNode n) {
        List<JMeterTreeNode> l = new ArrayList<>();
        for (TreeNode a = n; a instanceof JMeterTreeNode; a = a.getParent()) {
            Enumeration<?> e = a.children();
            while (e.hasMoreElements()) {
                JMeterTreeNode c = (JMeterTreeNode) e.nextElement();
                if (c != n && c.isEnabled()) {
                    l.add(c);
                }
            }
        }
        return l;
    }

    /** Un élément de ce type s'applique-t-il à {@code n} ? */
    public static boolean aDansSaPortee(JMeterTreeNode n, Class<?> type) {
        for (JMeterTreeNode c : portee(n)) {
            if (type.isInstance(c.getTestElement())) {
                return true;
            }
        }
        return false;
    }

    /** Ancêtre le plus proche dont l'élément est de ce type, ou {@code null}. */
    public static JMeterTreeNode ancetre(JMeterTreeNode n, Class<?> type) {
        for (TreeNode p = n.getParent(); p instanceof JMeterTreeNode; p = p.getParent()) {
            if (type.isInstance(((JMeterTreeNode) p).getTestElement())) {
                return (JMeterTreeNode) p;
            }
        }
        return null;
    }

    /** « Groupe > Transaction > Requête », sans le plan de test. */
    public static String chemin(JMeterTreeNode noeud) {
        List<String> noms = new ArrayList<>();
        for (TreeNode n = noeud; n instanceof JMeterTreeNode; n = n.getParent()) {
            TestElement e = ((JMeterTreeNode) n).getTestElement();
            if (e instanceof TestPlan) {
                break;
            }
            noms.add(0, e.getName());
        }
        return noms.isEmpty() ? "(plan de test)" : String.join(" > ", noms);
    }
}
