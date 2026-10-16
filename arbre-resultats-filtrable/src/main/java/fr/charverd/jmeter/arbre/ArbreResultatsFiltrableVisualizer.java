package fr.charverd.jmeter.arbre;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.text.JTextComponent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jmeter.visualizers.ViewResultsFullVisualizer;

/**
 * « View Results Tree » enrichi : copie des noms de résultats (requêtes et
 * contrôleurs) et filtre de l'arbre sur ces noms.
 *
 * <p>Le composant d'origine garde tout en privé ; on le réutilise tel quel
 * (rendus, onglets, recherche) et on se greffe dessus :
 * <ul>
 * <li>tous les résultats reçus sont conservés ici, et seuls ceux que le filtre
 * garde sont transmis à l'arbre d'origine ;</li>
 * <li>changer le filtre vide l'arbre d'origine et lui retransmet les résultats
 * conservés ;</li>
 * <li>la barre de filtre et le menu contextuel sont posés sur le {@link JTree}
 * retrouvé dans la hiérarchie de composants.</li>
 * </ul>
 */
public class ArbreResultatsFiltrableVisualizer extends ViewResultsFullVisualizer {

    private static final long serialVersionUID = 1L;

    private static final String PROP_MOTIF = "ArbreFiltrable.motif";
    private static final String PROP_REGEX = "ArbreFiltrable.regex";
    private static final String PROP_CASSE = "ArbreFiltrable.sensibleCasse";
    private static final String PROP_EXCLURE = "ArbreFiltrable.exclure";
    private static final String PROP_TRANSACTIONS = "ArbreFiltrable.transactionsSeules";

    private static final String SEPARATEUR_CHEMIN = " > ";

    /** Même plafond que l'arbre d'origine (0 ou moins : illimité). */
    private final int maxResultats = JMeterUtils.getPropDefault("view.results.tree.max_results", 500);

    /** Tous les résultats de premier niveau reçus, filtrés ou non. */
    private final Deque<SampleResult> tous = new ArrayDeque<>();
    private int affiches;

    private volatile FiltreNoms filtre = FiltreNoms.aucun();
    private volatile boolean nomsAJour;

    private transient JTree arbre;
    private JComboBox<String> champFiltre;
    private JCheckBox caseRegex;
    private JCheckBox caseCasse;
    private JCheckBox caseExclure;
    private JCheckBox caseTransactions;
    private JLabel etat;
    private Timer reportFiltre;
    private Timer rafraichissementEtat;
    /** Empêche configure() de relancer le filtre à chaque case cochée. */
    private boolean enConfiguration;

    public ArbreResultatsFiltrableVisualizer() {
        super();
        arbre = trouverArbre(this);
        if (arbre == null) {
            // Structure de l'arbre d'origine inattendue : on reste un arbre standard.
            return;
        }
        installerBarreFiltre();
        installerCopie();
    }

    @Override
    public String getStaticLabel() {
        return "Arbre de résultats filtrable";
    }

    // ------------------------------------------------------------------
    // Données
    // ------------------------------------------------------------------

    @Override
    public void add(SampleResult resultat) {
        boolean garde;
        synchronized (tous) {
            tous.addLast(resultat);
            if (maxResultats > 0 && tous.size() > maxResultats) {
                SampleResult retire = tous.removeFirst();
                if (filtre.garder(retire)) {
                    affiches--;
                }
            }
            garde = filtre.garder(resultat);
            if (garde) {
                affiches++;
            }
            nomsAJour = false;
        }
        if (garde) {
            super.add(resultat);
        }
    }

    @Override
    public void clearData() {
        synchronized (tous) {
            tous.clear();
            affiches = 0;
            nomsAJour = false;
        }
        super.clearData();
    }

    /** Reconstruit l'arbre d'origine à partir des résultats conservés. */
    private void reappliquerFiltre() {
        List<SampleResult> gardes = new ArrayList<>();
        synchronized (tous) {
            for (SampleResult r : tous) {
                if (filtre.garder(r)) {
                    gardes.add(r);
                }
            }
            affiches = gardes.size();
        }
        super.clearData();
        for (SampleResult r : gardes) {
            super.add(r);
        }
        majEtat();
    }

    /** Noms distincts de tous les résultats conservés, triés. */
    private Set<String> nomsConnus(boolean transactionsSeules) {
        Set<String> noms = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        synchronized (tous) {
            for (SampleResult r : tous) {
                FiltreNoms.collecterNoms(r, transactionsSeules, noms);
            }
        }
        return noms;
    }

    // ------------------------------------------------------------------
    // Barre de filtre
    // ------------------------------------------------------------------

    private void installerBarreFiltre() {
        champFiltre = new JComboBox<>();
        champFiltre.setEditable(true);
        champFiltre.setPrototypeDisplayValue("Nom de transaction assez long");
        champFiltre.setToolTipText("Filtre sur le nom des requêtes et des contrôleurs ;"
                + " la flèche liste les noms déjà reçus");
        // La liste des noms n'est recalculée qu'à l'ouverture, pour ne pas
        // perturber la saisie en cours.
        champFiltre.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                remplirListeNoms();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                // rien
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                // rien
            }
        });
        editeur().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                programmerFiltre();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                programmerFiltre();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                programmerFiltre();
            }
        });

        caseRegex = new JCheckBox("Regex");
        caseCasse = new JCheckBox("Casse");
        caseCasse.setToolTipText("Respecter majuscules et minuscules");
        caseExclure = new JCheckBox("Exclure");
        caseExclure.setToolTipText("Masquer les résultats dont le nom correspond");
        caseTransactions = new JCheckBox("Transactions seules");
        caseTransactions.setToolTipText("Ne chercher que parmi les noms de Transaction Controller");
        for (JCheckBox c : new JCheckBox[] { caseRegex, caseCasse, caseExclure, caseTransactions }) {
            c.addItemListener(e -> {
                if (!enConfiguration) {
                    appliquerFiltre();
                }
            });
        }

        JButton boutonNoms = new JButton("Noms…");
        boutonNoms.setToolTipText("Lister, copier et filtrer les noms reçus");
        boutonNoms.addActionListener(e -> ouvrirListeNoms());
        JButton boutonEffacer = new JButton("Effacer");
        boutonEffacer.addActionListener(e -> champFiltre.getEditor().setItem(""));

        etat = new JLabel(" ");

        JPanel ligne1 = new JPanel(new BorderLayout(4, 0));
        ligne1.add(new JLabel("Filtre :"), BorderLayout.WEST);
        ligne1.add(champFiltre, BorderLayout.CENTER);
        JPanel ligne1Boutons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        ligne1Boutons.add(boutonEffacer);
        ligne1Boutons.add(boutonNoms);
        ligne1.add(ligne1Boutons, BorderLayout.EAST);

        JPanel ligne2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ligne2.add(caseRegex);
        ligne2.add(caseCasse);
        ligne2.add(caseExclure);
        ligne2.add(caseTransactions);
        ligne2.add(etat);

        JPanel barre = new JPanel();
        barre.setLayout(new BoxLayout(barre, BoxLayout.Y_AXIS));
        ligne1.setAlignmentX(LEFT_ALIGNMENT);
        ligne2.setAlignmentX(LEFT_ALIGNMENT);
        barre.add(ligne1);
        barre.add(Box.createVerticalStrut(2));
        barre.add(ligne2);
        barre.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));

        // On remplace le JScrollPane de l'arbre par un panneau barre + arbre,
        // à la même place et avec la même contrainte de mise en page.
        JScrollPane defilement = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, arbre);
        Container parent = defilement.getParent();
        LayoutManager layout = parent.getLayout();
        Object contrainte = layout instanceof BorderLayout
                ? ((BorderLayout) layout).getConstraints(defilement) : null;
        int index = indexDans(parent, defilement);
        parent.remove(defilement);
        JPanel enveloppe = new JPanel(new BorderLayout());
        enveloppe.add(barre, BorderLayout.NORTH);
        enveloppe.add(defilement, BorderLayout.CENTER);
        parent.add(enveloppe, contrainte, index);

        reportFiltre = new Timer(300, e -> appliquerFiltre());
        reportFiltre.setRepeats(false);
        // Le compteur suit l'arrivée des résultats pendant un tir.
        rafraichissementEtat = new Timer(1000, e -> majEtat());
        rafraichissementEtat.start();
        majEtat();
    }

    private JTextComponent editeur() {
        return (JTextComponent) champFiltre.getEditor().getEditorComponent();
    }

    private String motifSaisi() {
        return editeur().getText();
    }

    private void programmerFiltre() {
        if (!enConfiguration) {
            reportFiltre.restart();
        }
    }

    private void appliquerFiltre() {
        reportFiltre.stop();
        FiltreNoms nouveau = new FiltreNoms(motifSaisi(), caseRegex.isSelected(),
                caseCasse.isSelected(), caseExclure.isSelected(), caseTransactions.isSelected());
        JTextComponent ed = editeur();
        if (nouveau.getErreur() != null) {
            ed.setBackground(new Color(255, 205, 205));
            ed.setToolTipText("Expression invalide : " + nouveau.getErreur());
        } else {
            ed.setBackground(UIManager.getColor("TextField.background"));
            ed.setToolTipText(null);
        }
        filtre = nouveau;
        reappliquerFiltre();
    }

    private void remplirListeNoms() {
        String saisi = motifSaisi();
        DefaultComboBoxModel<String> modele = new DefaultComboBoxModel<>();
        for (String nom : nomsConnus(caseTransactions.isSelected())) {
            modele.addElement(nom);
        }
        enConfiguration = true;
        try {
            champFiltre.setModel(modele);
            champFiltre.getEditor().setItem(saisi);
        } finally {
            enConfiguration = false;
        }
    }

    private void majEtat() {
        if (etat == null) {
            return;
        }
        int total;
        int vus;
        synchronized (tous) {
            total = tous.size();
            vus = affiches;
        }
        etat.setText(filtre.estActif() ? vus + " / " + total + " résultats affichés"
                : total + " résultats");
    }

    /** Filtre sur une liste exacte de noms (menu contextuel, fenêtre des noms). */
    private void filtrerSur(Collection<String> noms, boolean exclure) {
        if (noms.isEmpty()) {
            return;
        }
        enConfiguration = true;
        try {
            caseRegex.setSelected(true);
            caseExclure.setSelected(exclure);
            champFiltre.getEditor().setItem(FiltreNoms.regexPourNoms(noms));
        } finally {
            enConfiguration = false;
        }
        appliquerFiltre();
    }

    // ------------------------------------------------------------------
    // Copie
    // ------------------------------------------------------------------

    private void installerCopie() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Copier le nom", () -> copier(nomDe(noeudSelectionne()))));
        menu.add(item("Copier le chemin", () -> copier(cheminDe(noeudSelectionne()))));
        menu.add(item("Copier les noms de la branche", () -> {
            Set<String> noms = new LinkedHashSet<>();
            collecterNomsBranche(noeudSelectionne(), noms);
            copier(String.join("\n", noms));
        }));
        menu.add(item("Copier tous les noms affichés", () -> {
            Set<String> noms = new LinkedHashSet<>();
            TreeNode racine = (TreeNode) arbre.getModel().getRoot();
            for (int i = 0; i < racine.getChildCount(); i++) {
                collecterNomsBranche((DefaultMutableTreeNode) racine.getChildAt(i), noms);
            }
            copier(String.join("\n", noms));
        }));
        menu.addSeparator();
        menu.add(item("Filtrer sur ce nom",
                () -> filtrerSur(Collections.singletonList(nomDe(noeudSelectionne())), false)));
        menu.add(item("Exclure ce nom",
                () -> filtrerSur(Collections.singletonList(nomDe(noeudSelectionne())), true)));

        arbre.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                montrer(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                montrer(e);
            }

            private void montrer(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                TreePath chemin = arbre.getPathForLocation(e.getX(), e.getY());
                if (chemin == null) {
                    return;
                }
                arbre.setSelectionPath(chemin);
                menu.show(arbre, e.getX(), e.getY());
            }
        });

        // Ctrl+C (Cmd+C sur Mac) copie le nom du nœud sélectionné.
        @SuppressWarnings("deprecation")
        int masque = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        arbre.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_C, masque), "copierNom");
        arbre.getActionMap().put("copierNom", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                copier(nomDe(noeudSelectionne()));
            }
        });
    }

    private static JMenuItem item(String libelle, Runnable action) {
        JMenuItem item = new JMenuItem(libelle);
        item.addActionListener(e -> action.run());
        return item;
    }

    private DefaultMutableTreeNode noeudSelectionne() {
        return (DefaultMutableTreeNode) arbre.getLastSelectedPathComponent();
    }

    static String nomDe(DefaultMutableTreeNode noeud) {
        if (noeud == null) {
            return "";
        }
        Object objet = noeud.getUserObject();
        if (objet instanceof SampleResult) {
            return ((SampleResult) objet).getSampleLabel();
        }
        if (objet instanceof AssertionResult) {
            return ((AssertionResult) objet).getName();
        }
        return String.valueOf(objet);
    }

    /** « Transaction > Requête > Assertion », sans la racine invisible. */
    static String cheminDe(DefaultMutableTreeNode noeud) {
        if (noeud == null) {
            return "";
        }
        List<String> noms = new ArrayList<>();
        for (TreeNode n = noeud; n != null && n.getParent() != null; n = n.getParent()) {
            noms.add(0, nomDe((DefaultMutableTreeNode) n));
        }
        return String.join(SEPARATEUR_CHEMIN, noms);
    }

    private static void collecterNomsBranche(DefaultMutableTreeNode noeud, Set<String> noms) {
        if (noeud == null) {
            return;
        }
        if (noeud.getUserObject() instanceof SampleResult) {
            noms.add(nomDe(noeud));
        }
        for (int i = 0; i < noeud.getChildCount(); i++) {
            collecterNomsBranche((DefaultMutableTreeNode) noeud.getChildAt(i), noms);
        }
    }

    private static void copier(String texte) {
        if (texte == null || texte.isEmpty()) {
            return;
        }
        StringSelection selection = new StringSelection(texte);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
    }

    // ------------------------------------------------------------------
    // Fenêtre « Noms… »
    // ------------------------------------------------------------------

    private void ouvrirListeNoms() {
        Window fenetre = SwingUtilities.getWindowAncestor(this);
        JDialog dialogue = new JDialog(fenetre, "Noms reçus", java.awt.Dialog.ModalityType.MODELESS);

        JCheckBox transactions = new JCheckBox("Transactions seules", caseTransactions.isSelected());
        JTextField recherche = new JTextField();
        DefaultListModel<String> modele = new DefaultListModel<>();
        JList<String> liste = new JList<>(modele);
        JLabel compte = new JLabel(" ");

        Runnable remplir = () -> {
            String r = recherche.getText().toLowerCase(java.util.Locale.ROOT);
            modele.clear();
            for (String nom : nomsConnus(transactions.isSelected())) {
                if (nom.toLowerCase(java.util.Locale.ROOT).contains(r)) {
                    modele.addElement(nom);
                }
            }
            compte.setText(modele.size() + " noms");
        };
        recherche.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                remplir.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                remplir.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                remplir.run();
            }
        });
        transactions.addItemListener(e -> remplir.run());

        JButton copier = new JButton("Copier");
        copier.setToolTipText("Copie la sélection, ou toute la liste si rien n'est sélectionné");
        copier.addActionListener(e -> {
            List<String> choisis = liste.getSelectedValuesList();
            if (choisis.isEmpty()) {
                choisis = Collections.list(modele.elements());
            }
            copier(String.join("\n", choisis));
        });
        JButton filtrer = new JButton("Filtrer sur la sélection");
        filtrer.addActionListener(e -> {
            caseTransactions.setSelected(transactions.isSelected());
            filtrerSur(liste.getSelectedValuesList(), false);
        });
        JButton fermer = new JButton("Fermer");
        fermer.addActionListener(e -> dialogue.dispose());

        JPanel haut = new JPanel(new BorderLayout(4, 4));
        haut.add(new JLabel("Chercher :"), BorderLayout.WEST);
        haut.add(recherche, BorderLayout.CENTER);
        haut.add(transactions, BorderLayout.EAST);

        JPanel bas = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bas.add(compte);
        bas.add(copier);
        bas.add(filtrer);
        bas.add(fermer);

        JPanel contenu = new JPanel(new BorderLayout(4, 4));
        contenu.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        contenu.add(haut, BorderLayout.NORTH);
        contenu.add(new JScrollPane(liste), BorderLayout.CENTER);
        contenu.add(bas, BorderLayout.SOUTH);

        remplir.run();
        dialogue.setContentPane(contenu);
        dialogue.setSize(520, 420);
        dialogue.setLocationRelativeTo(this);
        dialogue.setVisible(true);
    }

    // ------------------------------------------------------------------
    // Sauvegarde des réglages dans le plan de test
    // ------------------------------------------------------------------

    @Override
    public void modifyTestElement(TestElement element) {
        super.modifyTestElement(element);
        if (champFiltre == null) {
            return;
        }
        element.setProperty(PROP_MOTIF, motifSaisi());
        element.setProperty(PROP_REGEX, caseRegex.isSelected());
        element.setProperty(PROP_CASSE, caseCasse.isSelected());
        element.setProperty(PROP_EXCLURE, caseExclure.isSelected());
        element.setProperty(PROP_TRANSACTIONS, caseTransactions.isSelected());
    }

    @Override
    public void configure(TestElement element) {
        super.configure(element);
        if (champFiltre == null) {
            return;
        }
        enConfiguration = true;
        try {
            champFiltre.getEditor().setItem(element.getPropertyAsString(PROP_MOTIF, ""));
            caseRegex.setSelected(element.getPropertyAsBoolean(PROP_REGEX, false));
            caseCasse.setSelected(element.getPropertyAsBoolean(PROP_CASSE, false));
            caseExclure.setSelected(element.getPropertyAsBoolean(PROP_EXCLURE, false));
            caseTransactions.setSelected(element.getPropertyAsBoolean(PROP_TRANSACTIONS, false));
        } finally {
            enConfiguration = false;
        }
        FiltreNoms nouveau = new FiltreNoms(motifSaisi(), caseRegex.isSelected(),
                caseCasse.isSelected(), caseExclure.isSelected(), caseTransactions.isSelected());
        if (!memesReglages(nouveau, filtre)) {
            appliquerFiltre();
        }
    }

    private static boolean memesReglages(FiltreNoms a, FiltreNoms b) {
        return a.getMotif().equals(b.getMotif()) && a.isRegex() == b.isRegex()
                && a.isSensibleCasse() == b.isSensibleCasse() && a.isExclure() == b.isExclure()
                && a.isTransactionsSeules() == b.isTransactionsSeules();
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    /**
     * L'arbre des résultats est le seul {@link JTree} écouté par le
     * visualiseur ; les rendus de droite peuvent en contenir d'autres.
     */
    private JTree trouverArbre(Container conteneur) {
        for (Component c : conteneur.getComponents()) {
            if (c instanceof JTree
                    && Arrays.asList(((JTree) c).getTreeSelectionListeners()).contains(this)) {
                return (JTree) c;
            }
            if (c instanceof Container) {
                JTree trouve = trouverArbre((Container) c);
                if (trouve != null) {
                    return trouve;
                }
            }
        }
        return null;
    }

    private static int indexDans(Container parent, Component enfant) {
        for (int i = 0; i < parent.getComponentCount(); i++) {
            if (parent.getComponent(i) == enfant) {
                return i;
            }
        }
        return -1;
    }
}
