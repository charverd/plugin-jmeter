package fr.jmeter.plugins.ia;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Empêche Swing d'interpréter comme du HTML un texte qui commence par {@code <html>}.
 *
 * <p>Les textes affichés ici viennent du plan, du trafic enregistré ou d'un modèle d'IA : un
 * rendu HTML pourrait charger une image distante, donc faire un appel réseau, à la simple
 * ouverture de la fenêtre. Propriété Swing {@code html.disable}.
 */
final class SansHtml {

    static final String PROPRIETE = "html.disable";

    private SansHtml() {
    }

    static <T extends JComponent> T neutraliser(T c) {
        c.putClientProperty(PROPRIETE, Boolean.TRUE);
        return c;
    }

    /** Rendu texte par défaut de la table (colonnes d'objets et de chaînes), sans HTML. */
    static JTable table(JTable t) {
        DefaultTableCellRenderer r = neutraliser(new DefaultTableCellRenderer());
        t.setDefaultRenderer(Object.class, r);
        t.setDefaultRenderer(String.class, r);
        return t;
    }

    static <E> JList<E> liste(JList<E> l) {
        l.setCellRenderer(rendu(l.getCellRenderer()));
        return l;
    }

    static <E> JComboBox<E> combo(JComboBox<E> c) {
        c.setRenderer(rendu(c.getRenderer()));
        return c;
    }

    /** Garde le rendu existant s'il est déjà neutralisable, sinon un rendu de liste standard. */
    static <E> ListCellRenderer<? super E> rendu(ListCellRenderer<? super E> existant) {
        if (existant instanceof JComponent) {
            neutraliser((JComponent) existant);
            return existant;
        }
        return neutraliser(new DefaultListCellRenderer());
    }
}
