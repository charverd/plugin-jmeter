package fr.jmeter.plugins.sauvegarde;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.MainFrame;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.util.JMeterToolBar;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jmeter.util.LocaleChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bouton « Sauvegarder en HTML puis nettoyer », posé dans la barre d'outils juste après
 * « Nettoyer » / « Nettoyer tout ».
 *
 * <p>La barre d'outils de JMeter n'a pas de point d'extension sans modifier les fichiers de
 * propriétés de l'utilisateur : le bouton est inséré dans la barre une fois la fenêtre créée, et
 * réinséré quand JMeter la reconstruit (changement de langue).
 */
final class BoutonSauvegarde {

    private static final Logger log = LoggerFactory.getLogger(BoutonSauvegarde.class);

    static final String NOM = "fr.jmeter.plugins.sauvegarde.bouton";
    static final String INFOBULLE = "<html><b>Sauvegarder en HTML puis nettoyer</b><br>"
            + "Arbre de résultats affiché : sauvegardé puis nettoyé (comme « Nettoyer »).<br>"
            + "Autre élément affiché : tous les arbres de résultats, puis « Nettoyer tout ».<br>"
            + "Si la sauvegarde échoue, rien n'est nettoyé.</html>";

    private static boolean demarre;
    private static final LocaleChangeListener REINSTALLATION = e -> SwingUtilities.invokeLater(BoutonSauvegarde::installer);

    private BoutonSauvegarde() {
    }

    /** Attend la fenêtre principale (la barre de menus est construite avant elle), puis installe. */
    static synchronized void demarrer() {
        if (demarre) {
            return;
        }
        demarre = true;
        JMeterUtils.addLocaleChangeListener(REINSTALLATION);
        Timer attente = new Timer(300, null);
        long fin = System.currentTimeMillis() + 120_000L;
        attente.addActionListener(e -> {
            if (installer() || System.currentTimeMillis() > fin) {
                attente.stop();
            }
        });
        attente.start();
    }

    /** @return vrai si le bouton est en place (ou déjà présent) */
    static boolean installer() {
        GuiPackage gp = GuiPackage.getInstance();
        MainFrame f = gp == null ? null : gp.getMainFrame();
        if (f == null) {
            return false;
        }
        JMeterToolBar barre = chercher(f.getContentPane());
        if (barre == null) {
            return false;
        }
        int apres = -1;
        Icon modele = null;
        for (int i = 0; i < barre.getComponentCount(); i++) {
            Component c = barre.getComponent(i);
            if (NOM.equals(c.getName())) {
                return true;
            }
            if (c instanceof JButton) {
                String cmd = ((JButton) c).getActionCommand();
                if (ActionNames.CLEAR_ALL.equals(cmd) || ActionNames.CLEAR.equals(cmd) && apres < 0) {
                    apres = i;
                    modele = ((JButton) c).getIcon();
                }
            }
        }
        JButton b = new JButton(new IconeSauvegarde(modele));
        b.setName(NOM);
        b.setToolTipText(INFOBULLE);
        b.addActionListener(e -> SauvegardeArbres.executer(SauvegardeArbres.Mode.BOUTON, e.getSource()));
        barre.add(b, apres < 0 ? -1 : apres + 1);
        barre.revalidate();
        barre.repaint();
        log.info("Bouton « Sauvegarder en HTML puis nettoyer » ajouté à la barre d'outils");
        return true;
    }

    private static JMeterToolBar chercher(Container c) {
        for (Component e : c.getComponents()) {
            if (e instanceof JMeterToolBar) {
                return (JMeterToolBar) e;
            }
            if (e instanceof Container) {
                JMeterToolBar t = chercher((Container) e);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    /** L'icône de « Nettoyer tout », avec une petite disquette en bas à droite. */
    static final class IconeSauvegarde implements Icon {
        private final Icon fond;
        private final int largeur;
        private final int hauteur;

        IconeSauvegarde(Icon fond) {
            this.fond = fond;
            this.largeur = fond == null ? 22 : fond.getIconWidth();
            this.hauteur = fond == null ? 22 : fond.getIconHeight();
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            if (fond != null) {
                fond.paintIcon(c, g, x, y);
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int t = Math.max(8, Math.round(Math.min(largeur, hauteur) * 0.55f));
                int px = x + largeur - t;
                int py = y + hauteur - t;
                g2.setColor(new Color(0x1F, 0x5F, 0xBF));
                g2.fillRoundRect(px, py, t, t, 3, 3);
                g2.setColor(Color.WHITE);
                int m = Math.max(2, t / 5);
                g2.fillRect(px + m, py + 1, t - 2 * m, Math.max(2, t / 3));
                g2.setColor(new Color(0xDDE6F3));
                g2.fillRect(px + m, py + t - Math.max(3, t / 3) - 1, t - 2 * m, Math.max(3, t / 3));
                g2.setColor(new Color(0x10, 0x3A, 0x7A));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(px, py, t - 1, t - 1, 3, 3);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return largeur;
        }

        @Override
        public int getIconHeight() {
            return hauteur;
        }
    }
}
