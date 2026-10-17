package fr.jmeter.plugins.ia;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.tree.TreeNode;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.BooleanProperty;
import org.apache.jmeter.testelement.property.IntegerProperty;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.LongProperty;
import org.apache.jmeter.testelement.property.NullProperty;
import org.apache.jorphan.collections.HashTree;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Applique des {@link ActionIA} à l'arbre JMeter. Les cibles sont résolues
 * d'abord, toutes sur l'arborescence envoyée au modèle ; chaque opération est
 * enregistrée pour pouvoir tout défaire.
 */
final class ApplicationActions {

    /** Bilan d'une application. */
    static final class Bilan {
        int appliquees;
        final List<String> erreurs = new ArrayList<>();
    }

    private final JMeterTreeModel modele;
    private final List<Runnable> annulations = new ArrayList<>();

    ApplicationActions(JMeterTreeModel modele) {
        this.modele = modele;
    }

    boolean estVide() {
        return annulations.isEmpty();
    }

    Bilan appliquer(List<ActionIA> actions, Contexte ctx) {
        Bilan b = new Bilan();
        // Résolution préalable : les ids désignent l'état d'avant les actions.
        Map<ActionIA, JMeterTreeNode[]> cibles = new IdentityHashMap<>();
        for (ActionIA a : actions) {
            if (!a.isCochee()) {
                continue;
            }
            JMeterTreeNode c = a.cible == null ? null : ctx.noeud(a.cible);
            JMeterTreeNode p = a.parent == null ? null : ctx.noeud(a.parent);
            if (a.cible != null && c == null) {
                b.erreurs.add(a.type + " : id inconnu [" + a.cible + "]");
                continue;
            }
            if (a.parent != null && p == null) {
                b.erreurs.add(a.type + " : id inconnu [" + a.parent + "]");
                continue;
            }
            cibles.put(a, new JMeterTreeNode[] { c, p });
        }
        for (ActionIA a : actions) {
            JMeterTreeNode[] cp = cibles.get(a);
            if (cp == null) {
                continue;
            }
            try {
                executer(a, cp[0], cp[1]);
                b.appliquees++;
            } catch (Exception e) {
                b.erreurs.add(a.type + (a.cible != null ? " [" + a.cible + "]" : "") + " : " + e.getMessage());
            }
        }
        return b;
    }

    private void executer(ActionIA a, JMeterTreeNode cible, JMeterTreeNode parent) throws Exception {
        switch (a.type) {
        case MODIFIER:
            modifier(cible, a.proprietes);
            break;
        case RENOMMER: {
            TestElement e = cible.getTestElement();
            String avant = e.getName();
            e.setName(a.nom);
            cible.nameChanged();
            annulations.add(() -> {
                e.setName(avant);
                cible.nameChanged();
            });
            break;
        }
        case ACTIVER:
        case DESACTIVER: {
            boolean avant = cible.isEnabled();
            cible.setEnabled(a.type == ActionIA.Type.ACTIVER);
            modele.nodeChanged(cible);
            annulations.add(() -> {
                cible.setEnabled(avant);
                modele.nodeChanged(cible);
            });
            break;
        }
        case SUPPRIMER:
            supprimer(cible);
            break;
        case AJOUTER:
            inserer(a.xml, parent, a.position);
            break;
        case REMPLACER: {
            JMeterTreeNode p = (JMeterTreeNode) cible.getParent();
            int index = p.getIndex(cible);
            inserer(a.xml, p, index);
            supprimer(cible);
            break;
        }
        case DEPLACER: {
            if (estAncetre(cible, parent)) {
                throw new IllegalArgumentException("impossible de déplacer un élément dans son propre contenu");
            }
            JMeterTreeNode ancien = (JMeterTreeNode) cible.getParent();
            int index = ancien.getIndex(cible);
            modele.removeNodeFromParent(cible);
            int pos = a.position == null ? parent.getChildCount() : Math.max(0, Math.min(a.position, parent.getChildCount()));
            modele.insertNodeInto(cible, parent, pos);
            annulations.add(() -> {
                modele.removeNodeFromParent(cible);
                modele.insertNodeInto(cible, ancien, Math.min(index, ancien.getChildCount()));
            });
            break;
        }
        default:
            throw new IllegalArgumentException("action non prise en charge");
        }
    }

    private static boolean estAncetre(JMeterTreeNode a, JMeterTreeNode n) {
        for (TreeNode p = n; p != null; p = p.getParent()) {
            if (p == a) {
                return true;
            }
        }
        return false;
    }

    /** Propriétés simples ; le type existant (booléen, entier, long) est conservé. */
    private void modifier(JMeterTreeNode n, Map<String, String> proprietes) {
        TestElement e = n.getTestElement();
        for (Map.Entry<String, String> p : proprietes.entrySet()) {
            String nom = p.getKey();
            String v = p.getValue();
            JMeterProperty avant = e.getProperty(nom);
            JMeterProperty copie = avant instanceof NullProperty ? null : avant.clone();
            if (avant instanceof BooleanProperty) {
                e.setProperty(nom, Boolean.parseBoolean(v.trim()));
            } else if (avant instanceof IntegerProperty && v.trim().matches("-?\\d+")) {
                e.setProperty(nom, Integer.parseInt(v.trim()));
            } else if (avant instanceof LongProperty && v.trim().matches("-?\\d+")) {
                e.setProperty(nom, Long.parseLong(v.trim()));
            } else {
                e.setProperty(nom, v);
            }
            annulations.add(() -> {
                if (copie == null) {
                    e.removeProperty(nom);
                } else {
                    e.setProperty(copie);
                }
            });
        }
        n.nameChanged();
    }

    private void supprimer(JMeterTreeNode n) {
        JMeterTreeNode parent = (JMeterTreeNode) n.getParent();
        int index = parent.getIndex(n);
        modele.removeNodeFromParent(n);
        annulations.add(() -> modele.insertNodeInto(n, parent, Math.min(index, parent.getChildCount())));
    }

    /** Charge le fragment JMX et l'insère sous {@code parent}, à {@code position} (fin par défaut). */
    private void inserer(String xml, JMeterTreeNode parent, Integer position) throws Exception {
        HashTree arbre = charger(xml);
        int avant = parent.getChildCount();
        modele.addSubTree(arbre, parent);
        List<JMeterTreeNode> nouveaux = new ArrayList<>();
        for (int i = avant; i < parent.getChildCount(); i++) {
            nouveaux.add((JMeterTreeNode) parent.getChildAt(i));
        }
        if (position != null) {
            int pos = Math.max(0, Math.min(position, avant));
            for (int k = 0; k < nouveaux.size(); k++) {
                JMeterTreeNode n = nouveaux.get(k);
                modele.removeNodeFromParent(n);
                modele.insertNodeInto(n, parent, pos + k);
            }
        }
        annulations.add(() -> {
            for (JMeterTreeNode n : nouveaux) {
                modele.removeNodeFromParent(n);
            }
        });
    }

    /** Fragment JMX → arbre d'éléments, en ajoutant les <hashTree/> oubliés après chaque élément de premier niveau. */
    static HashTree charger(String fragment) throws Exception {
        File f = Files.createTempFile("assistant-ia", ".jmx").toFile(); // 600 sous Linux et macOS
        try {
            Files.write(f.toPath(), envelopper(fragment).getBytes(StandardCharsets.UTF_8));
            return SaveService.loadTree(f);
        } finally {
            Files.deleteIfExists(f.toPath());
        }
    }

    /** Fragment lu dans une enveloppe {@code <hashTree>}, sans DTD ni entité externe. */
    static Element analyser(String fragment) throws Exception {
        DocumentBuilderFactory fab = DocumentBuilderFactory.newInstance();
        fab.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        fab.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        fab.setExpandEntityReferences(false);
        fab.setXIncludeAware(false);
        Document d = fab.newDocumentBuilder()
                .parse(new InputSource(new StringReader("<hashTree>" + fragment.trim() + "</hashTree>")));
        return d.getDocumentElement();
    }

    /** Ce qui, dans le fragment, exécutera du code pendant un tir (voir {@link ControleXml#codeExecutable}). */
    static List<String> codeExecutable(String fragment) throws Exception {
        return ControleXml.codeExecutable(analyser(fragment));
    }

    /**
     * Contrôle le fragment par liste blanche ({@link ControleXml}), puis l'enveloppe en plan complet
     * en ajoutant les {@code <hashTree/>} manquants. Rien n'est passé à JMeter sans ce contrôle.
     */
    static String envelopper(String fragment) throws Exception {
        Element racine = analyser(fragment);
        ControleXml.verifier(racine);
        Document d = racine.getOwnerDocument();
        List<Node> enfants = new ArrayList<>();
        for (Node c = racine.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNodeType() == Node.ELEMENT_NODE) {
                enfants.add(c);
            }
        }
        for (int i = 0; i < enfants.size(); i++) {
            Node c = enfants.get(i);
            boolean estArbre = "hashTree".equals(c.getNodeName());
            boolean suiviDArbre = i + 1 < enfants.size() && "hashTree".equals(enfants.get(i + 1).getNodeName());
            if (!estArbre && !suiviDArbre) {
                racine.insertBefore(d.createElement("hashTree"), c.getNextSibling());
            }
        }
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter w = new StringWriter();
        t.transform(new DOMSource(racine), new StreamResult(w));
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<jmeterTestPlan version=\"1.2\" properties=\"5.0\" jmeter=\"5.4.3\">"
                + w + "</jmeterTestPlan>";
    }

    void defaire() {
        for (int i = annulations.size() - 1; i >= 0; i--) {
            annulations.get(i).run();
        }
        annulations.clear();
    }

    /** Pour le rafraîchissement : la sélection courante est-elle encore dans l'arbre ? */
    static boolean estDansLArbre(JMeterTreeNode n, JMeterTreeNode racine) {
        for (TreeNode p = n; p != null; p = p.getParent()) {
            if (p == racine) {
                return true;
            }
        }
        return false;
    }

    static GuiPackage gp() {
        return GuiPackage.getInstance();
    }
}
