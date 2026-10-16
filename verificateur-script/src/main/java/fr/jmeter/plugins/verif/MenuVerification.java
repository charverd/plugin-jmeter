package fr.jmeter.plugins.verif;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.MenuElement;

import org.apache.jmeter.gui.plugin.MenuCreator;

/** « Outils > Vérifier le plan… ». JMeter trouve cette classe seul dans lib/ext. */
public class MenuVerification implements MenuCreator {

    static final String LIBELLE = "Vérifier le plan…";

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location != MENU_LOCATION.TOOLS) {
            return new JMenuItem[0];
        }
        JMenuItem item = new JMenuItem(LIBELLE);
        item.setToolTipText("Signaler les défauts courants du plan avant de le livrer ou de le lancer");
        item.addActionListener(e -> FenetreVerification.ouvrir());
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
