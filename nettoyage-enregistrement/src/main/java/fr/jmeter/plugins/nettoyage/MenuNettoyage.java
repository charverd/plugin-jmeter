package fr.jmeter.plugins.nettoyage;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.MenuElement;

import org.apache.jmeter.gui.plugin.MenuCreator;

/** « Outils > Nettoyer un enregistrement… ». JMeter trouve cette classe seul dans lib/ext. */
public class MenuNettoyage implements MenuCreator {

    static final String LIBELLE = "Nettoyer un enregistrement…";

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location != MENU_LOCATION.TOOLS) {
            return new JMenuItem[0];
        }
        JMenuItem item = new JMenuItem(LIBELLE);
        item.setToolTipText("Retirer ressources statiques et domaines tiers, regrouper en transactions (sélectionner d'abord l'enregistrement)");
        item.addActionListener(e -> FenetreNettoyage.ouvrir());
        return new JMenuItem[] { item };
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
}
