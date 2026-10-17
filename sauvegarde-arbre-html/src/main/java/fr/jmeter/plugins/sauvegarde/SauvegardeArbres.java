package fr.jmeter.plugins.sauvegarde;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.ActionRouter;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Sauvegarde des arbres de résultats en HTML, puis nettoyage par les actions de JMeter. */
final class SauvegardeArbres {

    private static final Logger log = LoggerFactory.getLogger(SauvegardeArbres.class);

    /** Dossier de destination ; par défaut celui du .jmx, sinon le dossier personnel. */
    static final String PROP_DOSSIER = "sauvegarde.arbre.html.dossier";
    /** Taille maximale gardée par réponse, en caractères (0 : sans limite). */
    static final String PROP_MAX = "sauvegarde.arbre.html.max_caracteres";
    /** {@code false} : pas de message après la sauvegarde. */
    static final String PROP_MESSAGE = "sauvegarde.arbre.html.message";

    static final int MAX_DEFAUT = 1_000_000;

    enum Mode {
        /** Bouton : l'arbre affiché puis « Nettoyer », sinon tous les arbres puis « Nettoyer tout ». */
        BOUTON,
        /** Tous les arbres, puis « Nettoyer tout ». */
        TOUS_PUIS_NETTOYER_TOUT,
        /** Tous les arbres, fichier choisi, sans rien nettoyer. */
        SANS_NETTOYER
    }

    private SauvegardeArbres() {
    }

    static void executer(Mode mode, Object source) {
        GuiPackage gp = GuiPackage.getInstance();
        if (gp == null) {
            return;
        }
        Component fenetre = gp.getMainFrame();
        // Récupère la saisie de l'écran ouvert (nom du récepteur, réglages du filtre).
        gp.updateCurrentNode();

        List<CollecteArbres.Trouve> cibles;
        String nettoyage;
        String origine;
        CollecteArbres.Trouve courant = mode == Mode.BOUTON ? CollecteArbres.courant(gp) : null;
        if (courant != null) {
            cibles = Collections.singletonList(courant);
            nettoyage = ActionNames.CLEAR;
            origine = "Sauvegarde avant « " + JMeterUtils.getResString("clear") + " »";
        } else {
            cibles = CollecteArbres.tous(gp);
            nettoyage = mode == Mode.SANS_NETTOYER ? null : ActionNames.CLEAR_ALL;
            origine = mode == Mode.SANS_NETTOYER ? "Sauvegarde manuelle"
                    : "Sauvegarde avant « " + JMeterUtils.getResString("clear_all") + " »";
        }

        List<ArbreExporte> arbres = new ArrayList<>();
        int nb = 0;
        for (CollecteArbres.Trouve t : cibles) {
            if (!t.arbre.resultats.isEmpty()) {
                arbres.add(t.arbre);
                nb += t.arbre.resultats.size();
            }
        }

        File fichier = null;
        if (nb > 0) {
            fichier = mode == Mode.SANS_NETTOYER ? choisirFichier(fenetre, gp) : fichierAutomatique(gp);
            if (fichier == null) {
                return; // choix annulé
            }
            Cursor avant = fenetre == null ? null : fenetre.getCursor();
            try {
                if (fenetre != null) {
                    fenetre.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                }
                ecrire(fichier, entete(gp, origine), arbres);
            } catch (IOException | RuntimeException e) {
                log.error("Sauvegarde HTML de l'arbre de résultats impossible : {}", fichier, e);
                JOptionPane.showMessageDialog(fenetre,
                        "La sauvegarde a échoué, rien n'a été nettoyé.\n\n" + fichier + "\n" + e,
                        "Sauvegarde de l'arbre de résultats", JOptionPane.ERROR_MESSAGE);
                return;
            } finally {
                if (fenetre != null) {
                    fenetre.setCursor(avant);
                }
            }
            log.info("Arbre de résultats sauvegardé : {} ({} résultats)", fichier, nb);
        }

        if (nettoyage != null) {
            ActionRouter.getInstance().doActionNow(new ActionEvent(source == null ? fenetre : source,
                    ActionEvent.ACTION_PERFORMED, nettoyage));
        }

        if (fichier != null && JMeterUtils.getPropDefault(PROP_MESSAGE, true)) {
            informer(fenetre, fichier, nb, arbres, nettoyage != null);
        } else if (fichier == null && mode == Mode.SANS_NETTOYER) {
            JOptionPane.showMessageDialog(fenetre, "Aucun résultat à sauvegarder : les arbres de résultats sont vides.",
                    "Sauvegarde de l'arbre de résultats", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static ExportHtml.Entete entete(GuiPackage gp, String origine) {
        String plan = "Plan de test";
        try {
            plan = ((org.apache.jmeter.gui.tree.JMeterTreeNode) ((javax.swing.tree.TreeNode) gp.getTreeModel()
                    .getRoot()).getChildAt(0)).getName();
        } catch (RuntimeException e) {
            log.debug("Nom du plan illisible", e);
        }
        return new ExportHtml.Entete(plan, gp.getTestPlanFile(), new Date(), origine);
    }

    /** Écrit dans un fichier temporaire du même dossier, puis le renomme : jamais de page à moitié écrite. */
    static void ecrire(File fichier, ExportHtml.Entete entete, List<ArbreExporte> arbres) throws IOException {
        File dossier = fichier.getAbsoluteFile().getParentFile();
        if (dossier != null && !dossier.isDirectory() && !dossier.mkdirs()) {
            throw new IOException("dossier impossible à créer : " + dossier);
        }
        // Files.createTempFile : lisible par le seul propriétaire (600) sous Linux et macOS ; la page
        // garde ces droits après renommage, car elle contient les en-têtes (cookies, jetons).
        File temp = (dossier == null ? Files.createTempFile(".sauvegarde-", ".tmp")
                : Files.createTempFile(dossier.toPath(), ".sauvegarde-", ".tmp")).toFile();
        boolean ok = false;
        try {
            try (Writer w = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(temp.toPath()),
                    StandardCharsets.UTF_8), 1 << 16)) {
                new ExportHtml(JMeterUtils::getResString, JMeterUtils.getPropDefault(PROP_MAX, MAX_DEFAUT))
                        .ecrire(w, entete, arbres);
            }
            Files.move(temp.toPath(), fichier.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ok = true;
        } finally {
            if (!ok) {
                Files.deleteIfExists(temp.toPath());
            }
        }
    }

    static File fichierAutomatique(GuiPackage gp) {
        String jmx = gp.getTestPlanFile();
        String prop = JMeterUtils.getProperty(PROP_DOSSIER);
        File dossier;
        if (prop != null && !prop.trim().isEmpty()) {
            dossier = new File(prop.trim());
        } else if (jmx != null) {
            dossier = new File(jmx).getAbsoluteFile().getParentFile();
        } else {
            dossier = new File(System.getProperty("user.home"));
        }
        return nomLibre(dossier, base(jmx), new Date());
    }

    static String base(String jmx) {
        if (jmx == null) {
            return "plan";
        }
        String n = new File(jmx).getName();
        return n.toLowerCase().endsWith(".jmx") ? n.substring(0, n.length() - 4) : n;
    }

    /** « plan_arbre-resultats_20261017-153012.html », suffixé -2, -3… si le nom est pris. */
    static File nomLibre(File dossier, String base, Date date) {
        String racine = base + "_arbre-resultats_" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(date);
        File f = new File(dossier, racine + ".html");
        for (int i = 2; f.exists(); i++) {
            f = new File(dossier, racine + "-" + i + ".html");
        }
        return f;
    }

    private static File choisirFichier(Component fenetre, GuiPackage gp) {
        File proposition = fichierAutomatique(gp);
        JFileChooser choix = new JFileChooser(proposition.getParentFile());
        choix.setDialogTitle("Sauvegarder les arbres de résultats en HTML");
        choix.setFileFilter(new FileNameExtensionFilter("Page HTML", "html", "htm"));
        choix.setSelectedFile(proposition);
        if (choix.showSaveDialog(fenetre) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File f = choix.getSelectedFile();
        if (!f.getName().toLowerCase().matches(".*\\.html?")) {
            f = new File(f.getParentFile(), f.getName() + ".html");
        }
        if (f.exists() && JOptionPane.showConfirmDialog(fenetre, "Remplacer " + f + " ?", "Fichier existant",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return null;
        }
        return f;
    }

    private static void informer(Component fenetre, File fichier, int nb, List<ArbreExporte> arbres, boolean nettoye) {
        StringBuilder sb = new StringBuilder();
        sb.append(nb).append(nb > 1 ? " résultats sauvegardés" : " résultat sauvegardé");
        if (arbres.size() > 1) {
            sb.append(" (").append(arbres.size()).append(" arbres)");
        }
        sb.append(nettoye ? ", puis nettoyés." : ".").append("\n\n").append(fichier.getAbsolutePath());
        for (ArbreExporte a : arbres) {
            if (a.filtre != null && a.filtre.estActif()) {
                sb.append("\n\n« ").append(a.nom).append(" » : filtre ").append(a.filtre.description())
                        .append(".\nLa page contient aussi les résultats masqués par ce filtre.");
            }
        }
        Object[] options = { "OK", "Ouvrir", "Copier le chemin" };
        int r = JOptionPane.showOptionDialog(fenetre, sb.toString(), "Sauvegarde de l'arbre de résultats",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (r == 1) {
            try {
                Desktop.getDesktop().browse(fichier.toURI());
            } catch (IOException | RuntimeException e) {
                JOptionPane.showMessageDialog(fenetre, "Ouverture impossible : " + e.getMessage());
            }
        } else if (r == 2) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(fichier.getAbsolutePath()), null);
        }
    }
}
