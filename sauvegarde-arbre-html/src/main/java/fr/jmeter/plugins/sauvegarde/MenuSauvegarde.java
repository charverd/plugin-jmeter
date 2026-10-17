package fr.jmeter.plugins.sauvegarde;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.MenuElement;

import org.apache.jmeter.gui.plugin.MenuCreator;

/**
 * Entrées du menu « Lancer », à côté de « Nettoyer » et « Nettoyer tout ». JMeter trouve cette
 * classe seul dans lib/ext ; elle installe aussi le bouton de la barre d'outils.
 */
public class MenuSauvegarde implements MenuCreator {

    public MenuSauvegarde() {
        BoutonSauvegarde.demarrer();
    }

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location != MENU_LOCATION.RUN) {
            return new JMenuItem[0];
        }
        JMenuItem bouton = new JMenuItem("Sauvegarder en HTML puis nettoyer");
        bouton.setToolTipText(BoutonSauvegarde.INFOBULLE);
        bouton.addActionListener(e -> SauvegardeArbres.executer(SauvegardeArbres.Mode.BOUTON, e.getSource()));

        JMenuItem tout = new JMenuItem("Sauvegarder tous les arbres en HTML puis nettoyer tout");
        tout.addActionListener(
                e -> SauvegardeArbres.executer(SauvegardeArbres.Mode.TOUS_PUIS_NETTOYER_TOUT, e.getSource()));

        JMenuItem seul = new JMenuItem("Sauvegarder les arbres de résultats en HTML…");
        seul.setToolTipText("Choisir le fichier ; rien n'est nettoyé");
        seul.addActionListener(e -> SauvegardeArbres.executer(SauvegardeArbres.Mode.SANS_NETTOYER, e.getSource()));
        return new JMenuItem[] { bouton, tout, seul };
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
        // libellés en français seulement ; le bouton est réinstallé par BoutonSauvegarde
    }
}
