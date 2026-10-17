package fr.jmeter.plugins.ia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jorphan.collections.HashTree;
import org.apache.jorphan.collections.ListedHashTree;

/**
 * Contexte joint à une question : arborescence du plan (avec des ids stables
 * le temps d'un échange) et XML des éléments sélectionnés.
 */
public final class Contexte {

    /** Ids → nœuds de l'arborescence envoyée : c'est sur eux que portent les actions de la réponse. */
    private final Map<String, JMeterTreeNode> parId = new LinkedHashMap<>();
    private final Map<JMeterTreeNode, String> idDe = new IdentityHashMap<>();
    private String arborescence = "";

    /** Numérote tout l'arbre depuis la racine (« 0 », « 0.1 », « 0.1.3 »…). */
    public static Contexte de(JMeterTreeNode racine) {
        Contexte c = new Contexte();
        StringBuilder sb = new StringBuilder();
        c.numeroter(racine, "0", 0, sb);
        c.arborescence = sb.toString();
        return c;
    }

    private void numeroter(JMeterTreeNode n, String id, int profondeur, StringBuilder sb) {
        parId.put(id, n);
        idDe.put(n, id);
        for (int i = 0; i < profondeur; i++) {
            sb.append("  ");
        }
        sb.append('[').append(id).append("] ").append(decrire(n)).append('\n');
        Enumeration<?> e = n.children();
        int i = 0;
        while (e.hasMoreElements()) {
            numeroter((JMeterTreeNode) e.nextElement(), id + "." + i++, profondeur + 1, sb);
        }
    }

    /** « HTTPSamplerProxy « /login » GET 127.0.0.1/login (désactivé) ». */
    static String decrire(JMeterTreeNode n) {
        TestElement e = n.getTestElement();
        StringBuilder sb = new StringBuilder(SaveService.classToAlias(e.getClass().getName()));
        sb.append(" « ").append(e.getName()).append(" »");
        String d = details(e);
        if (!d.isEmpty()) {
            sb.append(' ').append(d);
        }
        if (!n.isEnabled()) {
            sb.append(" (désactivé)");
        }
        return sb.toString();
    }

    private static String details(TestElement e) {
        StringBuilder sb = new StringBuilder();
        ajouter(sb, e, "HTTPSampler.method", "");
        String domaine = e.getPropertyAsString("HTTPSampler.domain");
        String chemin = e.getPropertyAsString("HTTPSampler.path");
        if (!domaine.isEmpty() || !chemin.isEmpty()) {
            sb.append(sb.length() > 0 ? " " : "").append(domaine).append(chemin);
        }
        ajouter(sb, e, "ThreadGroup.num_threads", "unités=");
        ajouter(sb, e, "ThreadGroup.ramp_time", "montée=");
        ajouter(sb, e, "filename", "fichier=");
        if (e.getProperty("TransactionController.parent").getStringValue().equals("true")) {
            sb.append(sb.length() > 0 ? " " : "").append("parent=oui");
        }
        return sb.toString();
    }

    private static void ajouter(StringBuilder sb, TestElement e, String prop, String prefixe) {
        String v = e.getPropertyAsString(prop);
        if (!v.isEmpty()) {
            sb.append(sb.length() > 0 ? " " : "").append(prefixe).append(v);
        }
    }

    public String getArborescence() {
        return arborescence;
    }

    public JMeterTreeNode noeud(String id) {
        return id == null ? null : parId.get(id.trim());
    }

    public String id(JMeterTreeNode n) {
        return idDe.get(n);
    }

    public Map<String, JMeterTreeNode> getNoeuds() {
        return Collections.unmodifiableMap(parId);
    }

    /** XML JMX d'un nœud et de tout son contenu, sans l'enveloppe du fichier. */
    public static String xml(JMeterTreeNode n) throws IOException {
        HashTree arbre = new ListedHashTree();
        copier(n, arbre);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SaveService.saveTree(arbre, out);
        String s = new String(out.toByteArray(), StandardCharsets.UTF_8);
        int debut = s.indexOf("<hashTree>");
        int fin = s.lastIndexOf("</hashTree>");
        return debut >= 0 && fin > debut ? s.substring(debut + "<hashTree>".length(), fin).trim() : s;
    }

    private static void copier(JMeterTreeNode n, HashTree arbre) {
        TestElement e = n.getTestElement();
        HashTree sous = arbre.add(e);
        Enumeration<?> en = n.children();
        while (en.hasMoreElements()) {
            copier((JMeterTreeNode) en.nextElement(), sous);
        }
    }

    /** Texte joint à la question. */
    public String texte(boolean avecArborescence, List<JMeterTreeNode> selection, String rapportVerificateur,
            int maxCaracteres) throws IOException {
        StringBuilder sb = new StringBuilder();
        if (avecArborescence) {
            String a = arborescence;
            if (a.length() > maxCaracteres / 2) {
                a = a.substring(0, maxCaracteres / 2) + "\n… (arborescence tronquée : plan trop grand)\n";
            }
            sb.append("### Arborescence du plan\n").append(a).append('\n');
        }
        if (selection != null && !selection.isEmpty()) {
            sb.append("### Éléments sélectionnés\n");
            for (JMeterTreeNode n : selection) {
                sb.append("Élément [").append(id(n)).append("] :\n```xml\n").append(xml(n)).append("\n```\n");
                if (sb.length() > maxCaracteres) {
                    sb.append("… (sélection tronquée : trop volumineuse)\n");
                    break;
                }
            }
        }
        if (rapportVerificateur != null && !rapportVerificateur.isEmpty()) {
            sb.append("### Rapport du vérificateur\n").append(rapportVerificateur).append('\n');
        }
        return sb.toString();
    }
}
