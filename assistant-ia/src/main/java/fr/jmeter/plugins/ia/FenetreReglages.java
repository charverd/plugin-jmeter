package fr.jmeter.plugins.ia;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import com.fasterxml.jackson.core.type.TypeReference;

/** Réglages : profils de modèles (format OpenAI), prompt système, taille du contexte. */
final class FenetreReglages extends JDialog {

    private static final long serialVersionUID = 1L;

    private final ConfigAssistant config;
    private final DefaultListModel<Profil> modeleProfils = new DefaultListModel<>();
    private final JList<Profil> liste = SansHtml.liste(new JList<>(modeleProfils));
    private final JTextField nom = new JTextField(30);
    private final JTextField url = new JTextField(30);
    private final JComboBox<String> modele = SansHtml.combo(new JComboBox<String>());
    private final JTextField cle = new JTextField(30);
    private final JTextArea entetes = new JTextArea(3, 30);
    private final JTextArea parametres = new JTextArea(4, 30);
    private final JLabel resultat = new JLabel(" ");
    private final JTextArea prompt = new JTextArea(20, 80);
    private final JSpinner contexteMax = new JSpinner(new SpinnerNumberModel(60000, 2000, 2_000_000, 1000));
    private Profil courant;
    private boolean valide;

    /** Ouvre la fenêtre (modale) ; rend vrai si l'utilisateur a enregistré. */
    static boolean editer(Window parent, ConfigAssistant config) {
        FenetreReglages f = new FenetreReglages(parent, config);
        f.setVisible(true);
        return f.valide;
    }

    private FenetreReglages(Window parent, ConfigAssistant config) {
        super(parent, "Réglages de l'assistant IA", ModalityType.APPLICATION_MODAL);
        this.config = config;
        for (Profil p : config.profils) {
            modeleProfils.addElement(p);
        }
        liste.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        liste.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                lireFormulaire();
                courant = liste.getSelectedValue();
                remplirFormulaire();
            }
        });
        modele.setEditable(true);
        entetes.setFont(new Font(Font.MONOSPACED, Font.PLAIN, entetes.getFont().getSize()));
        parametres.setFont(entetes.getFont());
        cle.setToolTipText("Clé d'API, ou ${env:NOM_DE_VARIABLE} pour la lire dans l'environnement (conseillé)");
        entetes.setToolTipText("JSON, par exemple {\"api-key\": \"${env:AZURE_OPENAI_KEY}\"} pour Azure OpenAI");
        parametres.setToolTipText("JSON ajouté au corps de chaque requête, par exemple {\"temperature\": 0.2}"
                + " ; aucun max_tokens par défaut");

        JButton ajouter = new JButton("Ajouter");
        ajouter.addActionListener(e -> {
            lireFormulaire();
            Profil p = new Profil("Nouveau profil", "http://localhost:11434/v1", "", "");
            modeleProfils.addElement(p);
            liste.setSelectedValue(p, true);
        });
        JButton dupliquer = new JButton("Dupliquer");
        dupliquer.addActionListener(e -> {
            lireFormulaire();
            if (courant != null) {
                Profil p = new Profil(courant.nom + " (copie)", courant.url, courant.modele, courant.cle);
                p.entetes.putAll(courant.entetes);
                p.parametres.putAll(courant.parametres);
                modeleProfils.addElement(p);
                liste.setSelectedValue(p, true);
            }
        });
        JButton supprimer = new JButton("Supprimer");
        supprimer.addActionListener(e -> {
            if (courant != null) {
                Profil p = courant;
                courant = null;
                modeleProfils.removeElement(p);
            }
        });
        JPanel gauche = new JPanel(new BorderLayout(4, 4));
        gauche.add(new JScrollPane(liste), BorderLayout.CENTER);
        JPanel bg = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        bg.add(ajouter);
        bg.add(dupliquer);
        bg.add(supprimer);
        gauche.add(bg, BorderLayout.SOUTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(2, 2, 2, 2);
        g.fill = GridBagConstraints.HORIZONTAL;
        ligne(form, g, 0, "Nom :", nom);
        ligne(form, g, 1, "URL (jusqu'à /v1) :", url);
        JPanel lm = new JPanel(new BorderLayout(4, 0));
        lm.add(modele, BorderLayout.CENTER);
        JButton lister = new JButton("Lister les modèles");
        lister.addActionListener(e -> lister());
        lm.add(lister, BorderLayout.EAST);
        ligne(form, g, 2, "Modèle :", lm);
        ligne(form, g, 3, "Clé d'API :", cle);
        ligne(form, g, 4, "En-têtes (JSON) :", new JScrollPane(entetes));
        ligne(form, g, 5, "Paramètres (JSON) :", new JScrollPane(parametres));
        JButton tester = new JButton("Tester");
        tester.addActionListener(e -> tester());
        JPanel lt = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        lt.add(tester);
        lt.add(resultat);
        ligne(form, g, 6, "", lt);
        JPanel droite = new JPanel(new BorderLayout());
        droite.add(form, BorderLayout.NORTH);

        JSplitPane profils = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gauche, droite);
        profils.setResizeWeight(0.25);

        prompt.setLineWrap(true);
        prompt.setWrapStyleWord(true);
        prompt.setText(config.prompt());
        JButton reinit = new JButton("Réinitialiser le prompt par défaut");
        reinit.addActionListener(e -> prompt.setText(PromptSysteme.DEFAUT));
        contexteMax.setValue(config.contexteMax);
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bp.add(reinit);
        bp.add(new JLabel("   Contexte joint au plus :"));
        bp.add(contexteMax);
        bp.add(new JLabel("caractères"));
        JPanel ongletPrompt = new JPanel(new BorderLayout(4, 4));
        ongletPrompt.add(new JScrollPane(prompt), BorderLayout.CENTER);
        ongletPrompt.add(bp, BorderLayout.SOUTH);

        JTabbedPane onglets = new JTabbedPane();
        onglets.addTab("Modèles", profils);
        onglets.addTab("Prompt système", ongletPrompt);

        JButton ok = new JButton("Enregistrer");
        ok.addActionListener(e -> enregistrer());
        JButton annuler = new JButton("Annuler");
        annuler.addActionListener(e -> dispose());
        JPanel bas = new JPanel(new BorderLayout());
        bas.add(new JLabel(" Fichier : " + ConfigAssistant.fichier()), BorderLayout.WEST);
        JPanel bb = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bb.add(ok);
        bb.add(annuler);
        bas.add(bb, BorderLayout.EAST);

        JPanel contenu = new JPanel(new BorderLayout(4, 4));
        contenu.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        contenu.add(onglets, BorderLayout.CENTER);
        contenu.add(bas, BorderLayout.SOUTH);
        setContentPane(contenu);
        setSize(980, 620);
        setLocationRelativeTo(parent);
        Profil actif = config.profil(config.profilActif);
        if (actif != null) {
            liste.setSelectedValue(actif, true);
        } else if (!modeleProfils.isEmpty()) {
            liste.setSelectedIndex(0);
        }
    }

    private static void ligne(JPanel p, GridBagConstraints g, int y, String libelle, java.awt.Component c) {
        g.gridx = 0;
        g.gridy = y;
        g.weightx = 0;
        p.add(new JLabel(libelle), g);
        g.gridx = 1;
        g.weightx = 1;
        p.add(c, g);
    }

    private void remplirFormulaire() {
        boolean actif = courant != null;
        for (java.awt.Component c : new java.awt.Component[] { nom, url, modele, cle, entetes, parametres }) {
            c.setEnabled(actif);
        }
        if (!actif) {
            return;
        }
        nom.setText(courant.nom);
        url.setText(courant.url);
        modele.removeAllItems();
        modele.setSelectedItem(courant.modele);
        cle.setText(courant.cle);
        try {
            entetes.setText(courant.entetes.isEmpty() ? "" : ConfigAssistant.JSON.writeValueAsString(courant.entetes));
            parametres.setText(courant.parametres.isEmpty() ? ""
                    : ConfigAssistant.JSON.writeValueAsString(courant.parametres));
        } catch (Exception e) {
            entetes.setText("");
        }
        resultat.setText(" ");
    }

    /** Recopie le formulaire dans le profil courant ; rend faux si un JSON est invalide. */
    private boolean lireFormulaire() {
        if (courant == null) {
            return true;
        }
        courant.nom = nom.getText().trim();
        courant.url = url.getText().trim();
        Object m = modele.getEditor().getItem();
        courant.modele = m == null ? "" : m.toString().trim();
        courant.cle = cle.getText().trim();
        try {
            courant.entetes = entetes.getText().trim().isEmpty() ? new LinkedHashMap<>()
                    : ConfigAssistant.JSON.readValue(entetes.getText(), new TypeReference<LinkedHashMap<String, String>>() {
                    });
            courant.parametres = parametres.getText().trim().isEmpty() ? new LinkedHashMap<>()
                    : ConfigAssistant.JSON.readValue(parametres.getText(),
                            new TypeReference<LinkedHashMap<String, Object>>() {
                            });
        } catch (Exception e) {
            resultat.setText("JSON invalide : " + e.getMessage().split("\n")[0]);
            return false;
        }
        liste.repaint();
        return true;
    }

    private void tester() {
        if (!lireFormulaire() || courant == null) {
            return;
        }
        Profil p = courant;
        resultat.setText("Test en cours…");
        new Thread(() -> {
            String r;
            try {
                r = "Réponse : « " + abreger(new ClientOpenAI(p).tester()) + " »";
            } catch (Exception e) {
                r = "Échec : " + e.getMessage();
            }
            String fin = r;
            SwingUtilities.invokeLater(() -> resultat.setText(fin));
        }, "assistant-ia-test").start();
    }

    private void lister() {
        if (!lireFormulaire() || courant == null) {
            return;
        }
        Profil p = courant;
        resultat.setText("Interrogation de " + p.point("/models") + "…");
        new Thread(() -> {
            try {
                List<String> l = new ClientOpenAI(p).listerModeles();
                SwingUtilities.invokeLater(() -> {
                    Object choisi = modele.getEditor().getItem();
                    modele.removeAllItems();
                    for (String s : l) {
                        modele.addItem(s);
                    }
                    modele.setSelectedItem(choisi);
                    resultat.setText(l.size() + " modèle(s) proposé(s) par le serveur");
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> resultat.setText("Échec : " + e.getMessage()));
            }
        }, "assistant-ia-modeles").start();
    }

    private static String abreger(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }

    private void enregistrer() {
        if (!lireFormulaire()) {
            JOptionPane.showMessageDialog(this, resultat.getText());
            return;
        }
        config.profils.clear();
        for (int i = 0; i < modeleProfils.size(); i++) {
            config.profils.add(modeleProfils.get(i));
        }
        if (courant != null) {
            config.profilActif = courant.nom;
        }
        String p = prompt.getText();
        config.promptSysteme = p.trim().equals(PromptSysteme.DEFAUT.trim()) ? "" : p;
        config.contexteMax = (Integer) contexteMax.getValue();
        valide = true;
        dispose();
    }
}
