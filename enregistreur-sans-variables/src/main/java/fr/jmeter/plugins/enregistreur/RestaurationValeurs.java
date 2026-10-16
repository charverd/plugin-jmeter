package fr.jmeter.plugins.enregistreur;

import java.util.ArrayList;
import java.util.List;

import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.MultiProperty;
import org.apache.jmeter.testelement.property.NullProperty;
import org.apache.jmeter.testelement.property.PropertyIterator;
import org.apache.jmeter.testelement.property.StringProperty;
import org.apache.jmeter.testelement.property.TestElementProperty;

/**
 * Annule, sur un élément enregistré, les remplacements de valeurs par des
 * références de variables ({@code ${nom}}) qu'effectue l'enregistreur de
 * JMeter.
 *
 * <p>On compare l'élément à une copie prise avant son passage dans
 * l'enregistreur : une valeur texte qui a changé et qui contient désormais
 * {@code ${} est remise à sa valeur d'origine. Les autres changements de
 * l'enregistreur (valeurs retirées car identiques aux « HTTP Request
 * Defaults », en-tête Authorization déplacé…) ne produisent pas de
 * {@code ${} et sont donc conservés.
 */
final class RestaurationValeurs {

    private static final String DEBUT_VARIABLE = "${";

    private RestaurationValeurs() {
    }

    /**
     * @param actuel  l'élément après traitement par l'enregistreur
     * @param origine copie de l'élément prise avant ce traitement
     * @return le nombre de valeurs restaurées
     */
    static int restaurer(TestElement actuel, TestElement origine) {
        if (actuel == null || origine == null) {
            return 0;
        }
        int restaurees = 0;
        PropertyIterator it = actuel.propertyIterator();
        while (it.hasNext()) {
            JMeterProperty prop = it.next();
            JMeterProperty avant = origine.getProperty(prop.getName());
            // getProperty rend une NullProperty quand le nom est inconnu.
            if (avant != null && !(avant instanceof NullProperty)) {
                restaurees += restaurer(prop, avant);
            }
        }
        return restaurees;
    }

    private static int restaurer(JMeterProperty actuel, JMeterProperty avant) {
        if (actuel instanceof TestElementProperty && avant instanceof TestElementProperty) {
            return restaurer(((TestElementProperty) actuel).getElement(),
                    ((TestElementProperty) avant).getElement());
        }
        if (actuel instanceof MultiProperty && avant instanceof MultiProperty) {
            return restaurerCollection((MultiProperty) actuel, (MultiProperty) avant);
        }
        if (actuel instanceof StringProperty) {
            String apres = actuel.getStringValue();
            String origine = avant.getStringValue();
            if (apres != null && apres.contains(DEBUT_VARIABLE) && !apres.equals(origine)) {
                actuel.setObjectValue(origine);
                return 1;
            }
        }
        return 0;
    }

    /**
     * Les éléments d'une collection sont appariés par position quand la
     * taille n'a pas changé ; sinon (un élément a été retiré, comme l'en-tête
     * Authorization), par nom, chaque élément d'origine ne servant qu'une fois.
     */
    private static int restaurerCollection(MultiProperty actuel, MultiProperty avant) {
        List<JMeterProperty> apres = lister(actuel);
        List<JMeterProperty> origine = lister(avant);
        int restaurees = 0;
        if (apres.size() == origine.size()) {
            for (int i = 0; i < apres.size(); i++) {
                restaurees += restaurer(apres.get(i), origine.get(i));
            }
            return restaurees;
        }
        boolean[] utilise = new boolean[origine.size()];
        for (JMeterProperty p : apres) {
            for (int i = 0; i < origine.size(); i++) {
                if (!utilise[i] && cle(origine.get(i)).equals(cle(p))) {
                    utilise[i] = true;
                    restaurees += restaurer(p, origine.get(i));
                    break;
                }
            }
        }
        return restaurees;
    }

    private static List<JMeterProperty> lister(MultiProperty multi) {
        List<JMeterProperty> liste = new ArrayList<>();
        PropertyIterator it = multi.iterator();
        while (it.hasNext()) {
            liste.add(it.next());
        }
        return liste;
    }

    /** Nom de l'élément porté (en-tête, paramètre…), à défaut nom de la propriété. */
    private static String cle(JMeterProperty p) {
        if (p instanceof TestElementProperty) {
            TestElement e = ((TestElementProperty) p).getElement();
            if (e != null) {
                return e.getName() == null ? "" : e.getName();
            }
        }
        return p.getName() == null ? "" : p.getName();
    }
}
