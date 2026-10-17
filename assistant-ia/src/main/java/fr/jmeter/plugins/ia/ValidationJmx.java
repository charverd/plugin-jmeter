package fr.jmeter.plugins.ia;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.NullProperty;
import org.apache.jmeter.testelement.property.PropertyIterator;
import org.apache.jorphan.collections.HashTree;

/**
 * Repère les propriétés qu'un modèle a inventées : chaque élément proposé est
 * comparé à un élément neuf du même type, tel que l'écran de JMeter le crée.
 * Une propriété absente de l'élément neuf est inconnue de JMeter : elle ne
 * servira à rien, et la vraie propriété restera à sa valeur par défaut.
 *
 * <p>JMeter retire souvent une propriété égale à sa valeur par défaut : un
 * élément neuf ne les porte donc pas toutes. Les noms déclarés comme
 * constantes dans la classe de l'élément et dans celle de son écran
 * (« JSONPostProcessor.defaultValues »…) comptent aussi comme connus.
 */
final class ValidationJmx {

    private ValidationJmx() {
    }

    /** Avertissements pour tous les éléments d'un fragment chargé. */
    static List<String> avertissements(HashTree arbre) {
        List<String> l = new ArrayList<>();
        parcourir(arbre, l);
        return l;
    }

    private static void parcourir(HashTree arbre, List<String> l) {
        for (Object o : arbre.list()) {
            if (o instanceof TestElement) {
                Set<String> inconnues = inconnues((TestElement) o, null);
                if (!inconnues.isEmpty()) {
                    l.add(SaveService.classToAlias(o.getClass().getName()) + " : propriété(s) inconnue(s) de JMeter "
                            + String.join(", ", inconnues));
                }
            }
            parcourir(arbre.getTree(o), l);
        }
    }

    private static Class<?> classe(String nom) {
        try {
            return nom == null || nom.isEmpty() ? null : Class.forName(nom);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    /** Valeurs des constantes texte (statiques, toute visibilité) de la classe et de ses parents. */
    static Set<String> constantes(Class<?> c) {
        Set<String> s = new LinkedHashSet<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(null);
                        if (v != null) {
                            s.add((String) v);
                        }
                    } catch (Exception | LinkageError ex) {
                        // champ illisible : ignoré
                    }
                }
            }
        }
        return s;
    }

    /** Élément neuf du même type, ou null si on ne sait pas le créer. */
    static TestElement modele(TestElement e) {
        GuiPackage gp = GuiPackage.getInstance();
        if (gp == null) {
            return null;
        }
        try {
            if (e instanceof TestBean) {
                return gp.createTestElement(e.getClass().getName());
            }
            String gui = SaveService.aliasToClass(e.getPropertyAsString(TestElement.GUI_CLASS));
            return gui.isEmpty() ? null : gp.createTestElement(gui);
        } catch (RuntimeException | LinkageError ex) {
            return null;
        }
    }

    /**
     * Noms de propriétés de {@code e} (ou seulement {@code noms}, s'ils sont
     * donnés) que ni {@code e} ni un élément neuf du même type ne connaissent.
     */
    static Set<String> inconnues(TestElement e, Iterable<String> noms) {
        Set<String> s = new LinkedHashSet<>();
        TestElement neuf = modele(e);
        if (neuf == null) {
            return s;
        }
        List<String> aVerifier = new ArrayList<>();
        if (noms != null) {
            for (String n : noms) {
                aVerifier.add(n);
            }
        } else {
            PropertyIterator it = e.propertyIterator();
            while (it.hasNext()) {
                aVerifier.add(it.next().getName());
            }
        }
        Set<String> declarees = constantes(e.getClass());
        declarees.addAll(constantes(classe(SaveService.aliasToClass(e.getPropertyAsString(TestElement.GUI_CLASS)))));
        for (String n : aVerifier) {
            if (n.startsWith("TestElement.") || declarees.contains(n)) {
                continue;
            }
            boolean connueDeLElement = noms != null && !(e.getProperty(n) instanceof NullProperty);
            if (!connueDeLElement && neuf.getProperty(n) instanceof NullProperty) {
                s.add(n);
            }
        }
        return s;
    }
}
