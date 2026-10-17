package fr.jmeter.plugins.ia;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jmeter.gui.JMeterGUIComponent;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.NameUpdater;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Liste blanche du XML proposé par le modèle, appliquée <b>avant</b> que JMeter ne le lise.
 *
 * <p>JMeter 5.4.3 lit les plans avec XStream en acceptant n'importe quelle classe Java
 * ({@code JMeterUtils.setupXStreamSecurityPolicy} : {@code AnyTypePermission.ANY}). Un XML
 * malveillant pourrait donc faire instancier des classes arbitraires dès la lecture, c'est-à-dire
 * dès l'aperçu. On n'accepte ici que la structure d'un plan : {@code hashTree}, des éléments dont
 * la balise désigne une classe {@link TestElement}, et les propriétés standard. Les classes sont
 * vérifiées sans être initialisées.
 */
final class ControleXml {

    /** Refus, avec la raison lisible. */
    static final class Refus extends Exception {
        private static final long serialVersionUID = 1L;

        Refus(String message) {
            super(message);
        }
    }

    static final Set<String> SIMPLES = new HashSet<>(Arrays.asList("stringProp", "boolProp", "intProp",
            "longProp", "floatProp", "doubleProp"));
    static final Set<String> ATTRIBUTS_ELEMENT = new HashSet<>(Arrays.asList("guiclass", "testclass", "testname",
            "enabled"));
    static final Set<String> ATTRIBUTS_PROPRIETE = new HashSet<>(Arrays.asList("name", "elementType", "guiclass",
            "testclass", "testname", "enabled"));

    /** Éléments qui exécutent du code ou des commandes pendant un tir. */
    private static final Pattern CLASSE_EXECUTABLE = Pattern.compile(
            "\\.(JSR223\\w*|BeanShell\\w*|SystemSampler|JUnitSampler|JavaSampler)$");
    /** Fonctions JMeter qui évaluent un script ou une expression. */
    private static final Pattern FONCTION_SCRIPT = Pattern.compile(
            "\\$\\{__(groovy|javaScript|BeanShell|jexl[23]?|eval|evalVar)\\b", Pattern.CASE_INSENSITIVE);

    private ControleXml() {
    }

    /** @param racine l'enveloppe {@code <hashTree>} qui contient le fragment */
    static void verifier(Element racine) throws Refus {
        arbre(racine);
    }

    private static void arbre(Element e) throws Refus {
        attributsVides(e);
        for (Element c : enfants(e)) {
            if ("hashTree".equals(c.getNodeName())) {
                arbre(c);
            } else {
                element(c);
            }
        }
    }

    private static void element(Element e) throws Refus {
        String balise = e.getNodeName();
        if (SIMPLES.contains(balise) || "elementProp".equals(balise) || "collectionProp".equals(balise)) {
            throw new Refus("propriété <" + balise + "> hors d'un élément");
        }
        verifierClasse(balise, TestElement.class, "élément <" + balise + ">");
        attributs(e, ATTRIBUTS_ELEMENT);
        for (Element c : enfants(e)) {
            propriete(c);
        }
    }

    private static void propriete(Element e) throws Refus {
        String balise = e.getNodeName();
        if (SIMPLES.contains(balise)) {
            attributs(e, ATTRIBUTS_PROPRIETE);
            if (!enfants(e).isEmpty()) {
                throw new Refus("<" + balise + "> ne doit contenir que du texte");
            }
            return;
        }
        if ("collectionProp".equals(balise)) {
            attributs(e, ATTRIBUTS_PROPRIETE);
        } else if ("elementProp".equals(balise)) {
            attributs(e, ATTRIBUTS_PROPRIETE);
            String type = e.getAttribute("elementType");
            if (type.isEmpty()) {
                throw new Refus("<elementProp> sans elementType");
            }
            verifierClasse(type, TestElement.class, "elementType « " + type + " »");
        } else {
            throw new Refus("balise <" + balise + "> non autorisée dans un élément (seules les propriétés"
                    + " stringProp, boolProp, intProp, longProp, floatProp, doubleProp, elementProp et"
                    + " collectionProp le sont)");
        }
        for (Element c : enfants(e)) {
            propriete(c);
        }
    }

    private static void attributsVides(Element e) throws Refus {
        if (e.getAttributes().getLength() > 0) {
            throw new Refus("attribut inattendu sur <" + e.getNodeName() + ">");
        }
    }

    private static void attributs(Element e, Set<String> permis) throws Refus {
        NamedNodeMap a = e.getAttributes();
        for (int i = 0; i < a.getLength(); i++) {
            String nom = a.item(i).getNodeName();
            if (!permis.contains(nom)) {
                throw new Refus("attribut « " + nom + " » non autorisé sur <" + e.getNodeName() + ">");
            }
        }
        if (e.hasAttribute("guiclass")) {
            verifierClasse(e.getAttribute("guiclass"), JMeterGUIComponent.class, "guiclass « "
                    + e.getAttribute("guiclass") + " »");
        }
        if (e.hasAttribute("testclass")) {
            verifierClasse(e.getAttribute("testclass"), TestElement.class, "testclass « "
                    + e.getAttribute("testclass") + " »");
        }
    }

    /** Nom de classe complet, comme JMeter le résout (alias de saveservice.properties, puis renommages). */
    static String classe(String nom) {
        return NameUpdater.getCurrentName(SaveService.aliasToClass(nom));
    }

    private static void verifierClasse(String nom, Class<?> attendu, String quoi) throws Refus {
        String c = classe(nom);
        Class<?> k;
        try {
            // false : la classe n'est pas initialisée (aucun bloc statique exécuté).
            k = Class.forName(c, false, ControleXml.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw new Refus(quoi + " : classe inconnue de JMeter");
        }
        if (!attendu.isAssignableFrom(k)) {
            throw new Refus(quoi + " : " + c + " n'est pas un " + attendu.getSimpleName());
        }
    }

    private static List<Element> enfants(Element e) {
        List<Element> l = new ArrayList<>();
        for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c.getNodeType() == Node.ELEMENT_NODE) {
                l.add((Element) c);
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ code exécuté pendant un tir

    /**
     * Éléments ou fonctions qui exécuteront du code pendant un tir (scripts, commandes) : pas un
     * risque à la lecture, mais à signaler et à faire valider par l'utilisateur.
     */
    static List<String> codeExecutable(Element racine) {
        Set<String> l = new LinkedHashSet<>();
        parcourirCode(racine, l);
        return new ArrayList<>(l);
    }

    private static void parcourirCode(Element e, Set<String> l) {
        String balise = e.getNodeName();
        if (!"hashTree".equals(balise) && !SIMPLES.contains(balise) && !"elementProp".equals(balise)
                && !"collectionProp".equals(balise)) {
            String c = classe(balise);
            if (CLASSE_EXECUTABLE.matcher(c).find()) {
                l.add("élément " + c.substring(c.lastIndexOf('.') + 1) + " (exécute du code ou une commande)");
            }
        }
        if (SIMPLES.contains(balise)) {
            String f = fonctionScript(e.getTextContent());
            if (f != null) {
                l.add("fonction " + f + " (évalue un script)");
            }
        }
        for (Element c : enfants(e)) {
            parcourirCode(c, l);
        }
    }

    /** Nom de la première fonction de script trouvée dans la valeur, ou {@code null}. */
    static String fonctionScript(String valeur) {
        if (valeur == null) {
            return null;
        }
        Matcher m = FONCTION_SCRIPT.matcher(valeur);
        return m.find() ? "__" + m.group(1).toLowerCase(Locale.ROOT).replace("javascript", "javaScript")
                .replace("beanshell", "BeanShell") : null;
    }
}
