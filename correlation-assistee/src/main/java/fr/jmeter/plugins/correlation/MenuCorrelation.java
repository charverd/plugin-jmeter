package fr.jmeter.plugins.correlation;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.MenuElement;

import org.apache.jmeter.gui.plugin.MenuCreator;

/** « Outils > Corrélation assistée… ». JMeter trouve cette classe seul dans lib/ext. */
public class MenuCorrelation implements MenuCreator {

    static final String LIBELLE = "Corrélation assistée…";

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location != MENU_LOCATION.TOOLS) {
            return new JMenuItem[0];
        }
        JMenuItem item = new JMenuItem(LIBELLE);
        item.setToolTipText("Comparer deux enregistrements du même parcours pour extraire les valeurs dynamiques (sélectionner d'abord les deux enregistrements)");
        item.addActionListener(e -> FenetreCorrelation.ouvrir());
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
