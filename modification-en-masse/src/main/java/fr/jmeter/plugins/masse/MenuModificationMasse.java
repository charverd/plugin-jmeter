package fr.jmeter.plugins.masse;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.plugin.MenuCreator;

/**
 * Branche « Modification en masse… » dans JMeter : menu Outils, et menu
 * clic droit de l'arbre.
 *
 * <p>Pas dans le menu Édition : JMeter le remplace à chaque sélection par le
 * menu contextuel de l'élément courant (JMeterMenuBar.setEditMenu), ce qui
 * efface les entrées des plugins.
 *
 * <p>JMeter trouve cette classe tout seul (interface {@link MenuCreator}).
 * Le menu clic droit de l'arbre, lui, est construit par chaque type
 * d'élément et n'offre pas de point d'extension : on complète le menu une
 * fois que JMeter l'a affiché.
 */
public class MenuModificationMasse implements MenuCreator {

    static final String LIBELLE = "Modification en masse…";

    private static boolean arbreBranche;

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location != MENU_LOCATION.TOOLS) {
            return new JMenuItem[0];
        }
        brancherArbre();
        return new JMenuItem[] { nouvelItem() };
    }

    @Override
    public JMenu[] getTopLevelMenus() {
        return new JMenu[0];
    }

    @Override
    public boolean localeChanged(MenuElement menu) {
        return false;
    }

    @Override
    public void localeChanged() {
        // libellé en français seulement
    }

    private static JMenuItem nouvelItem() {
        JMenuItem item = new JMenuItem(LIBELLE);
        item.setToolTipText("Modifier d'un coup les requêtes et en-têtes des éléments sélectionnés"
                + " (Ctrl+clic ou Maj+clic dans l'arbre)");
        item.addActionListener(e -> FenetreModificationMasse.ouvrir());
        return item;
    }

    /** L'arbre n'existe pas encore quand JMeter construit la barre de menus : on l'attend. */
    private static synchronized void brancherArbre() {
        if (arbreBranche) {
            return;
        }
        arbreBranche = true;
        Timer attente = new Timer(500, null);
        attente.addActionListener(e -> {
            GuiPackage gp = GuiPackage.getInstance();
            JTree arbre = gp == null || gp.getTreeListener() == null ? null : gp.getTreeListener().getJTree();
            if (arbre == null) {
                return;
            }
            attente.stop();
            arbre.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent ev) {
                    if (SwingUtilities.isRightMouseButton(ev) || ev.isPopupTrigger()) {
                        // JMeter affiche son menu par invokeLater : on passe après lui.
                        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(
                                () -> completerMenu(arbre)));
                    }
                }
            });
        });
        attente.start();
    }

    private static void completerMenu(JTree arbre) {
        for (MenuElement m : MenuSelectionManager.defaultManager().getSelectedPath()) {
            if (m instanceof JPopupMenu) {
                ajouterSiAbsent((JPopupMenu) m, arbre);
                return;
            }
        }
        // Selon le look and feel, le menu n'est pas encore dans le chemin de
        // sélection : on le cherche parmi les composants visibles.
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            JPopupMenu p = chercherPopup(w);
            if (p != null) {
                ajouterSiAbsent(p, arbre);
                return;
            }
        }
    }

    private static JPopupMenu chercherPopup(Component c) {
        if (c instanceof JPopupMenu && c.isShowing()) {
            return (JPopupMenu) c;
        }
        if (c instanceof java.awt.Container) {
            for (Component e : ((java.awt.Container) c).getComponents()) {
                JPopupMenu p = chercherPopup(e);
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }

    private static void ajouterSiAbsent(JPopupMenu menu, JTree arbre) {
        if (menu.getInvoker() != arbre) {
            return;
        }
        for (Component c : menu.getComponents()) {
            if (c instanceof JMenuItem && LIBELLE.equals(((JMenuItem) c).getText())) {
                return;
            }
        }
        menu.addSeparator();
        menu.add(nouvelItem());
        menu.pack();
    }
}
