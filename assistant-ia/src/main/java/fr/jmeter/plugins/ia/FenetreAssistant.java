package fr.jmeter.plugins.ia;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.tree.TreePath;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeNode;

/**
 * Fenêtre de l'assistant IA. Non modale : on travaille dans l'arbre pendant
 * la discussion. Le contexte (arborescence, sélection, rapport du
 * vérificateur) n'est joint qu'au dernier message, pour que les ids des
 * actions désignent toujours l'état présent du plan.
 */
public class FenetreAssistant extends JDialog {

    private static final long serialVersionUID = 1L;

    private static FenetreAssistant instance;

    private ConfigAssistant config;
    private final JComboBox<Profil> profils = SansHtml.combo(new JComboBox<Profil>());
    private final JTextPane conversation = new JTextPane();
    private final JTextArea saisie = new JTextArea(4, 60);
    private final JComboBox<String> commandes = SansHtml.combo(new JComboBox<String>());
    private final JCheckBox caseArbre = new JCheckBox("Arborescence du plan", true);
    private final JCheckBox caseSelection = new JCheckBox("Éléments sélectionnés (XML)", true);
    private final JCheckBox caseVerif = new JCheckBox("Rapport du vérificateur", false);
    private final JButton envoyer = new JButton("Envoyer (Ctrl+Entrée)");
    private final JButton arreter = new JButton("Arrêter");
    private final JLabel etat = new JLabel("Prêt");
    private final ModeleActions modeleActions = new ModeleActions();
    private final JTable tableActions = SansHtml.table(new JTable(modeleActions));
    private final JTextArea detailAction = new JTextArea(4, 60);
    private final JButton appliquer = new JButton("Appliquer les actions cochées");
    private final JButton defaire = new JButton("Annuler la dernière application");
    private final JPanel panneauActions = new JPanel(new BorderLayout(4, 4));

    private final List<ClientOpenAI.Message> historique = new ArrayList<>();
    private ClientOpenAI client;
    private Contexte contexteEnvoye;
    private ApplicationActions derniere;
    private String derniereReponse = "";

    public static void ouvrir() {
        GuiPackage gp = GuiPackage.getInstance();
        if (gp == null) {
            return;
        }
        if (instance == null) {
            instance = new FenetreAssistant(gp.getMainFrame());
        }
        instance.setVisible(true);
        instance.toFront();
        instance.saisie.requestFocusInWindow();
    }

    private FenetreAssistant(Window parent) {
        super(parent, "Assistant IA", ModalityType.MODELESS);
        chargerConfig();

        JPanel haut = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        haut.add(new JLabel("Modèle :"));
        haut.add(profils);
        JButton reglages = new JButton("Réglages…");
        reglages.addActionListener(e -> ouvrirReglages());
        JButton nouvelle = new JButton("Nouvelle conversation");
        nouvelle.addActionListener(e -> nouvelleConversation());
        haut.add(reglages);
        haut.add(nouvelle);
        haut.add(etat);
        profils.addActionListener(e -> {
            Profil p = (Profil) profils.getSelectedItem();
            if (p != null) {
                config.profilActif = p.nom;
            }
        });

        conversation.setEditable(false);
        JScrollPane defilConv = new JScrollPane(conversation);
        defilConv.setPreferredSize(new Dimension(900, 380));

        // Panneau des modifications proposées (masqué tant qu'il n'y en a pas).
        tableActions.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableActions.getColumnModel().getColumn(0).setMaxWidth(30);
        tableActions.getColumnModel().getColumn(1).setPreferredWidth(90);
        tableActions.getColumnModel().getColumn(1).setMaxWidth(110);
        tableActions.getColumnModel().getColumn(2).setPreferredWidth(260);
        tableActions.getColumnModel().getColumn(3).setPreferredWidth(420);
        tableActions.getColumnModel().getColumn(4).setPreferredWidth(160);
        tableActions.getSelectionModel().addListSelectionListener(e -> montrerAction());
        detailAction.setEditable(false);
        detailAction.setLineWrap(true);
        detailAction.setFont(new Font(Font.MONOSPACED, Font.PLAIN, detailAction.getFont().getSize()));
        appliquer.addActionListener(e -> appliquer());
        defaire.addActionListener(e -> defaire());
        defaire.setEnabled(false);
        JPanel boutonsActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        boutonsActions.add(appliquer);
        boutonsActions.add(defaire);
        JScrollPane defilActions = new JScrollPane(tableActions);
        defilActions.setPreferredSize(new Dimension(900, 110));
        JPanel bas = new JPanel(new BorderLayout());
        bas.add(new JScrollPane(detailAction), BorderLayout.CENTER);
        bas.add(boutonsActions, BorderLayout.SOUTH);
        panneauActions.add(defilActions, BorderLayout.CENTER);
        panneauActions.add(bas, BorderLayout.SOUTH);
        panneauActions.setBorder(BorderFactory.createTitledBorder("Modifications proposées"));
        panneauActions.setVisible(false);

        JPanel milieu = new JPanel(new BorderLayout(4, 4));
        milieu.add(defilConv, BorderLayout.CENTER);
        milieu.add(panneauActions, BorderLayout.SOUTH);

        // Saisie.
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        options.add(new JLabel("Joindre :"));
        options.add(caseArbre);
        options.add(caseSelection);
        options.add(caseVerif);
        caseVerif.setEnabled(verificateurDisponible());
        if (!caseVerif.isEnabled()) {
            caseVerif.setToolTipText("Plugin « vérificateur de script » non installé");
        }
        options.add(new JLabel("   Commande :"));
        options.add(commandes);
        commandes.addActionListener(e -> choisirCommande());
        saisie.setLineWrap(true);
        saisie.setWrapStyleWord(true);
        saisie.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "envoyer");
        saisie.getActionMap().put("envoyer", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                envoyer();
            }
        });
        envoyer.addActionListener(e -> envoyer());
        arreter.addActionListener(e -> {
            if (client != null) {
                client.arreter();
            }
        });
        arreter.setEnabled(false);
        JButton copier = new JButton("Copier la réponse");
        copier.addActionListener(e -> {
            StringSelection s = new StringSelection(derniereReponse);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(s, s);
        });
        JPanel boutons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        boutons.add(copier);
        boutons.add(arreter);
        boutons.add(envoyer);
        JPanel panneauSaisie = new JPanel(new BorderLayout(4, 4));
        panneauSaisie.add(options, BorderLayout.NORTH);
        panneauSaisie.add(new JScrollPane(saisie), BorderLayout.CENTER);
        panneauSaisie.add(boutons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, milieu, panneauSaisie);
        split.setResizeWeight(0.8);
        JPanel contenu = new JPanel(new BorderLayout(4, 4));
        contenu.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        contenu.add(haut, BorderLayout.NORTH);
        contenu.add(split, BorderLayout.CENTER);
        setContentPane(contenu);
        setSize(1000, 820);
        setLocationRelativeTo(parent);
        ecrire("Assistant IA — modèle au format OpenAI. Sélectionnez des éléments dans l'arbre, posez une question"
                + " ou choisissez une commande. Réglages : " + ConfigAssistant.fichier() + "\n\n", Style.INFO);
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    private void chargerConfig() {
        try {
            config = ConfigAssistant.charger(ConfigAssistant.fichier());
        } catch (Exception e) {
            config = ConfigAssistant.exemple();
            JOptionPane.showMessageDialog(getOwner(), "Réglages illisibles (" + e.getMessage()
                    + ") : configuration d'exemple utilisée.", "Assistant IA", JOptionPane.WARNING_MESSAGE);
        }
        profils.setModel(new DefaultComboBoxModel<>(config.profils.toArray(new Profil[0])));
        Profil actif = config.profil(config.profilActif);
        if (actif != null) {
            profils.setSelectedItem(actif);
        }
        DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
        m.addElement("(question libre)");
        for (String k : config.commandesEffectives().keySet()) {
            m.addElement(k);
        }
        commandes.setModel(m);
    }

    private void ouvrirReglages() {
        if (FenetreReglages.editer(this, config)) {
            try {
                config.enregistrer(ConfigAssistant.fichier());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Enregistrement impossible : " + e.getMessage());
            }
            chargerConfig();
        }
    }

    private void choisirCommande() {
        int i = commandes.getSelectedIndex();
        if (i <= 0) {
            return;
        }
        String nom = (String) commandes.getSelectedItem();
        saisie.setText(config.commandesEffectives().get(nom));
        if (nom.toLowerCase().contains("vérificateur") && caseVerif.isEnabled()) {
            caseVerif.setSelected(true);
        }
        saisie.requestFocusInWindow();
    }

    // ------------------------------------------------------------------
    // Conversation
    // ------------------------------------------------------------------

    private enum Style {
        VOUS, ASSISTANT, INFO, ERREUR
    }

    private void ecrire(String texte, Style style) {
        StyledDocument d = conversation.getStyledDocument();
        SimpleAttributeSet a = new SimpleAttributeSet();
        switch (style) {
        case VOUS:
            StyleConstants.setBold(a, true);
            break;
        case INFO:
            StyleConstants.setItalic(a, true);
            StyleConstants.setForeground(a, Color.GRAY);
            break;
        case ERREUR:
            StyleConstants.setForeground(a, new Color(0xE0, 0x50, 0x50));
            break;
        default:
            break;
        }
        try {
            d.insertString(d.getLength(), texte, a);
        } catch (BadLocationException e) {
            // ne peut pas arriver en fin de document
        }
        conversation.setCaretPosition(d.getLength());
    }

    private void nouvelleConversation() {
        if (client != null) {
            client.arreter();
        }
        historique.clear();
        contexteEnvoye = null;
        conversation.setText("");
        montrerActions(Collections.<ActionIA>emptyList(), null);
        etat.setText("Prêt");
    }

    private static boolean verificateurDisponible() {
        try {
            Class.forName("fr.jmeter.plugins.verif.Verificateur");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static String rapportVerificateur(JMeterTreeNode racine) {
        try {
            Class<?> v = Class.forName("fr.jmeter.plugins.verif.Verificateur");
            Method verifier = v.getMethod("verifier", JMeterTreeNode.class);
            Method rapport = v.getMethod("rapport", List.class);
            return (String) rapport.invoke(null, verifier.invoke(null, racine));
        } catch (Exception e) {
            return "";
        }
    }

    private void envoyer() {
        String question = saisie.getText().trim();
        Profil profil = (Profil) profils.getSelectedItem();
        if (question.isEmpty() || client != null) {
            return;
        }
        if (profil == null || profil.url.trim().isEmpty()) {
            ecrire("Aucun modèle configuré : ouvrir « Réglages… ».\n", Style.ERREUR);
            return;
        }
        GuiPackage gp = GuiPackage.getInstance();
        gp.updateCurrentNode();
        JMeterTreeNode racine = (JMeterTreeNode) gp.getTreeModel().getRoot();
        Contexte ctx = Contexte.de(racine);
        String joint;
        try {
            JMeterTreeNode[] sel = gp.getTreeListener().getSelectedNodes();
            joint = ctx.texte(caseArbre.isSelected(),
                    caseSelection.isSelected() && sel != null ? Arrays.asList(sel) : null,
                    caseVerif.isSelected() ? rapportVerificateur(racine) : null, config.contexteMax);
        } catch (Exception e) {
            ecrire("Contexte impossible à construire : " + e.getMessage() + "\n", Style.ERREUR);
            return;
        }
        contexteEnvoye = ctx;
        List<ClientOpenAI.Message> messages = new ArrayList<>();
        messages.add(new ClientOpenAI.Message("system", config.prompt()));
        messages.addAll(historique);
        messages.add(new ClientOpenAI.Message("user", joint.isEmpty() ? question : question + "\n\n" + joint));
        historique.add(new ClientOpenAI.Message("user", question));

        ecrire("Vous : ", Style.VOUS);
        ecrire(question + "\n", Style.ASSISTANT);
        if (!joint.isEmpty()) {
            ecrire("(contexte joint : " + joint.length() + " caractères)\n", Style.INFO);
        }
        ecrire(profil + " : ", Style.VOUS);
        saisie.setText("");
        commandes.setSelectedIndex(0);
        montrerActions(Collections.<ActionIA>emptyList(), null);
        envoyer.setEnabled(false);
        arreter.setEnabled(true);
        etat.setText("Envoi…");

        ClientOpenAI c = new ClientOpenAI(profil);
        client = c;
        StringBuilder reponse = new StringBuilder();
        int[] raisonnement = { 0 };
        Thread t = new Thread(() -> c.discuter(messages, new ClientOpenAI.Ecouteur() {
            @Override
            public void texte(String morceau) {
                reponse.append(morceau);
                SwingUtilities.invokeLater(() -> {
                    ecrire(morceau, Style.ASSISTANT);
                    etat.setText("Réponse en cours… " + reponse.length() + " caractères");
                });
            }

            @Override
            public void raisonnement(String morceau) {
                raisonnement[0] += morceau.length();
                SwingUtilities.invokeLater(() -> etat.setText("Le modèle réfléchit… " + raisonnement[0] + " caractères"));
            }

            @Override
            public void fin(String raison) {
                SwingUtilities.invokeLater(() -> terminer(reponse.toString(), raison, null));
            }

            @Override
            public void erreur(String message) {
                SwingUtilities.invokeLater(() -> terminer(reponse.toString(), null, message));
            }
        }), "assistant-ia");
        t.setDaemon(true);
        t.start();
    }

    private void terminer(String reponse, String raison, String erreur) {
        client = null;
        envoyer.setEnabled(true);
        arreter.setEnabled(false);
        derniereReponse = reponse;
        ecrire("\n", Style.ASSISTANT);
        if (erreur != null) {
            ecrire("Erreur : " + erreur + "\n\n", Style.ERREUR);
            etat.setText("Erreur");
            historique.remove(historique.size() - 1);
            return;
        }
        if (!reponse.isEmpty()) {
            historique.add(new ClientOpenAI.Message("assistant", reponse));
        }
        if ("length".equals(raison)) {
            ecrire("(réponse coupée par la limite du serveur)\n", Style.INFO);
        }
        ecrire("\n", Style.ASSISTANT);
        ActionIA.Lecture l = ActionIA.lire(reponse);
        if (l.erreur != null) {
            ecrire("Modifications non lisibles : " + l.erreur + "\n\n", Style.ERREUR);
        }
        for (ActionIA a : l.actions) {
            verifierIds(a);
            valider(a);
        }
        montrerActions(l.actions, l.erreur);
        etat.setText("Terminé" + (raison == null || raison.isEmpty() ? "" : " (" + raison + ")")
                + (l.actions.isEmpty() ? "" : " — " + l.actions.size() + " modification(s) proposée(s)"));
    }

    private void verifierIds(ActionIA a) {
        if (a.erreur != null || contexteEnvoye == null) {
            return;
        }
        if (a.cible != null && contexteEnvoye.noeud(a.cible) == null) {
            a.erreur = "id inconnu [" + a.cible + "]";
        } else if (a.parent != null && contexteEnvoye.noeud(a.parent) == null) {
            a.erreur = "id inconnu [" + a.parent + "]";
        }
    }

    /**
     * Charge le XML proposé (refusé s'il est illisible pour JMeter) et signale
     * les propriétés que JMeter ne connaît pas.
     */
    private void valider(ActionIA a) {
        if (a.erreur != null || a.type == null) {
            return;
        }
        try {
            if (a.type == ActionIA.Type.AJOUTER || a.type == ActionIA.Type.REMPLACER) {
                List<String> av = new java.util.ArrayList<>(
                        ValidationJmx.avertissements(ApplicationActions.charger(a.xml)));
                List<String> code = ApplicationActions.codeExecutable(a.xml);
                if (!code.isEmpty()) {
                    av.add(0, "exécutera du code pendant le tir : " + String.join(", ", code)
                            + " — décoché, à relire avant de cocher");
                    a.cochee = false;
                }
                if (!av.isEmpty()) {
                    a.avertissement = String.join(" ; ", av);
                }
            } else if (a.type == ActionIA.Type.MODIFIER && contexteEnvoye != null) {
                JMeterTreeNode n = contexteEnvoye.noeud(a.cible);
                java.util.Set<String> inc = ValidationJmx.inconnues(n.getTestElement(), a.proprietes.keySet());
                if (!inc.isEmpty()) {
                    a.avertissement = "propriété(s) inconnue(s) de JMeter : " + String.join(", ", inc);
                }
                for (Object v : a.proprietes.values()) {
                    String f = ControleXml.fonctionScript(String.valueOf(v));
                    if (f != null) {
                        a.avertissement = (a.avertissement == null ? "" : a.avertissement + " ; ")
                                + "fonction " + f + " : évaluera un script pendant le tir — décoché, à relire";
                        a.cochee = false;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            String m = e.getMessage() == null ? e.toString() : e.getMessage();
            a.erreur = (e instanceof ControleXml.Refus ? "XML refusé (sécurité) : " : "XML refusé par JMeter : ")
                    + m.split("\n")[0];
            a.cochee = false;
        }
    }

    // ------------------------------------------------------------------
    // Modifications proposées
    // ------------------------------------------------------------------

    private void montrerActions(List<ActionIA> actions, String erreur) {
        modeleActions.remplir(actions);
        panneauActions.setVisible(!actions.isEmpty());
        appliquer.setEnabled(!actions.isEmpty());
        detailAction.setText("");
        revalidate();
    }

    private String chemin(String id) {
        JMeterTreeNode n = contexteEnvoye == null ? null : contexteEnvoye.noeud(id);
        if (n == null) {
            return id == null ? "" : "[" + id + "] ?";
        }
        return "[" + id + "] " + n.getName();
    }

    private void montrerAction() {
        int i = tableActions.getSelectedRow();
        if (i < 0) {
            detailAction.setText("");
            return;
        }
        ActionIA a = modeleActions.lignes.get(i);
        StringBuilder sb = new StringBuilder();
        if (a.erreur != null) {
            sb.append("Non applicable : ").append(a.erreur).append("\n\n");
        }
        if (a.avertissement != null) {
            sb.append("⚠ À vérifier : ").append(a.avertissement)
                    .append(" (la propriété ne servira à rien ; la vraie restera à sa valeur par défaut)\n\n");
        }
        if (a.xml != null) {
            sb.append(a.xml);
        } else {
            sb.append(a.resume());
        }
        detailAction.setText(sb.toString());
        detailAction.setCaretPosition(0);
    }

    private void appliquer() {
        if (contexteEnvoye == null) {
            return;
        }
        GuiPackage gp = GuiPackage.getInstance();
        gp.updateCurrentNode();
        // Se placer sur la racine : l'écran ouvert pourrait être celui d'un élément supprimé ou remplacé.
        JMeterTreeNode racine = (JMeterTreeNode) gp.getTreeModel().getRoot();
        JMeterTreeNode[] sel = gp.getTreeListener().getSelectedNodes();
        gp.getTreeListener().getJTree().setSelectionPath(new TreePath(
                racine.getChildCount() > 0 ? ((JMeterTreeNode) racine.getChildAt(0)).getPath() : racine.getPath()));
        ApplicationActions app = new ApplicationActions(gp.getTreeModel());
        ApplicationActions.Bilan b = app.appliquer(modeleActions.lignes, contexteEnvoye);
        derniere = app;
        gp.setDirty(true);
        if (sel != null) {
            List<TreePath> encore = new ArrayList<>();
            for (JMeterTreeNode n : sel) {
                if (ApplicationActions.estDansLArbre(n, racine)) {
                    encore.add(new TreePath(n.getPath()));
                }
            }
            if (!encore.isEmpty()) {
                gp.getTreeListener().getJTree().setSelectionPaths(encore.toArray(new TreePath[0]));
            }
        }
        gp.refreshCurrentGui();
        gp.getMainFrame().repaint();
        ecrire(b.appliquees + " modification(s) appliquée(s)" + (b.erreurs.isEmpty() ? "" : ", "
                + b.erreurs.size() + " en échec : " + String.join(" ; ", b.erreurs)) + "\n\n",
                b.erreurs.isEmpty() ? Style.INFO : Style.ERREUR);
        appliquer.setEnabled(false);
        defaire.setEnabled(!app.estVide());
        // Les ids envoyés ne correspondent plus au plan : il faudra en renvoyer un pour d'autres actions.
        contexteEnvoye = null;
    }

    private void defaire() {
        if (derniere == null) {
            return;
        }
        GuiPackage gp = GuiPackage.getInstance();
        gp.updateCurrentNode();
        JMeterTreeNode racine = (JMeterTreeNode) gp.getTreeModel().getRoot();
        gp.getTreeListener().getJTree().setSelectionPath(new TreePath(racine.getPath()));
        derniere.defaire();
        derniere = null;
        gp.setDirty(true);
        gp.refreshCurrentGui();
        gp.getMainFrame().repaint();
        defaire.setEnabled(false);
        ecrire("Dernière application annulée.\n\n", Style.INFO);
    }

    private final class ModeleActions extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final List<ActionIA> lignes = new ArrayList<>();

        void remplir(List<ActionIA> l) {
            lignes.clear();
            lignes.addAll(l);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return lignes.size();
        }

        @Override
        public int getColumnCount() {
            return 5;
        }

        @Override
        public String getColumnName(int c) {
            return new String[] { "✓", "Action", "Élément", "Détail", "Problème" }[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int r, int c) {
            return c == 0 && lignes.get(r).erreur == null;
        }

        @Override
        public Object getValueAt(int r, int c) {
            ActionIA a = lignes.get(r);
            switch (c) {
            case 0:
                return a.isCochee();
            case 1:
                return a.type == null ? "?" : a.type.toString().toLowerCase();
            case 2:
                return a.type == ActionIA.Type.AJOUTER ? "sous " + chemin(a.parent) : chemin(a.cible);
            case 3:
                return a.resume();
            default:
                return a.erreur != null ? a.erreur : a.avertissement != null ? "⚠ " + a.avertissement : "";
            }
        }

        @Override
        public void setValueAt(Object v, int r, int c) {
            if (c == 0) {
                lignes.get(r).cochee = Boolean.TRUE.equals(v);
                fireTableCellUpdated(r, c);
            }
        }
    }
}
