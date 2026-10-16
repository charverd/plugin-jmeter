package fr.jmeter.plugins.enregistreur;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.jmeter.engine.util.ValueReplacer;
import org.apache.jmeter.protocol.http.control.Header;
import org.apache.jmeter.protocol.http.control.HeaderManager;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.JMeterProperty;
import org.apache.jmeter.testelement.property.PropertyIterator;
import org.apache.jmeter.testelement.property.TestElementProperty;
import org.apache.jmeter.testelement.property.MultiProperty;
import org.junit.jupiter.api.Test;

/**
 * Rejoue sur des éléments HTTP le remplacement que fait l'enregistreur de
 * JMeter ({@link ValueReplacer#reverseReplace}), puis vérifie que la
 * restauration rend les valeurs d'origine.
 */
class RestaurationValeursTest {

    private static HTTPSamplerProxy requete() {
        HTTPSamplerProxy s = new HTTPSamplerProxy();
        s.setName("/api/login-12");
        s.setProtocol("https");
        s.setDomain("app.exemple.fr");
        s.setPort(8443);
        s.setPath("/api/login");
        s.setMethod("POST");
        s.addArgument("utilisateur", "alice");
        s.addArgument("motdepasse", "secret42");
        s.addArgument("gabarit", "Bonjour ${prenom}");
        return s;
    }

    private static HeaderManager entetes() {
        HeaderManager hm = new HeaderManager();
        hm.add(new Header("Host", "app.exemple.fr"));
        hm.add(new Header("X-Client", "alice"));
        hm.add(new Header("Authorization", "Basic YWxpY2U6c2VjcmV0NDI="));
        return hm;
    }

    private static ValueReplacer remplaceur(String... nomsValeurs) {
        Map<String, String> vars = new HashMap<>();
        for (int i = 0; i < nomsValeurs.length; i += 2) {
            vars.put(nomsValeurs[i], nomsValeurs[i + 1]);
        }
        ValueReplacer r = new ValueReplacer();
        r.addVariables(vars);
        return r;
    }

    /** Toutes les valeurs texte de l'élément, sous-éléments compris. */
    private static String toutLeTexte(TestElement e) {
        StringBuilder sb = new StringBuilder();
        collecter(e.propertyIterator(), sb);
        return sb.toString();
    }

    private static void collecter(PropertyIterator it, StringBuilder sb) {
        while (it.hasNext()) {
            JMeterProperty p = it.next();
            if (p instanceof TestElementProperty) {
                collecter(((TestElementProperty) p).getElement().propertyIterator(), sb);
            } else if (p instanceof MultiProperty) {
                collecter(((MultiProperty) p).iterator(), sb);
            } else {
                sb.append(p.getName()).append('=').append(p.getStringValue()).append('\n');
            }
        }
    }

    @Test
    void lesValeursRemplaceesRedeviennentBrutes() throws Exception {
        HTTPSamplerProxy s = requete();
        TestElement copie = (TestElement) s.clone();
        String attendu = toutLeTexte(s);

        remplaceur("hote", "app.exemple.fr", "user", "alice", "port", "8443").reverseReplace(s, false);
        String remplace = toutLeTexte(s);
        assertTrue(remplace.contains("${hote}"), remplace);
        assertTrue(remplace.contains("${user}"), remplace);

        int n = RestaurationValeurs.restaurer(s, copie);

        assertTrue(n >= 3, "restaurées : " + n);
        assertEquals(attendu, toutLeTexte(s));
        assertEquals("app.exemple.fr", s.getDomain());
        assertEquals(8443, s.getPort());
    }

    @Test
    void unDollarDejaPresentDansLaRequeteResteTelQuel() throws Exception {
        HTTPSamplerProxy s = requete();
        TestElement copie = (TestElement) s.clone();
        remplaceur("user", "alice").reverseReplace(s, false);
        RestaurationValeurs.restaurer(s, copie);
        String texte = toutLeTexte(s);
        assertTrue(texte.contains("Argument.value=Bonjour ${prenom}"), texte);
    }

    @Test
    void enTetesAvecAuthorizationRetireeApresRemplacement() throws Exception {
        HeaderManager hm = entetes();
        TestElement copie = (TestElement) hm.clone();

        remplaceur("hote", "app.exemple.fr", "user", "alice").reverseReplace(hm, false);
        // L'enregistreur retire ensuite l'en-tête Authorization pour en faire
        // un Authorization Manager : la collection rétrécit.
        hm.remove(2);
        assertTrue(toutLeTexte(hm).contains("${hote}"));

        RestaurationValeurs.restaurer(hm, copie);

        assertEquals(2, hm.size());
        assertEquals("app.exemple.fr", hm.get(0).getValue());
        assertEquals("alice", hm.get(1).getValue());
        assertFalse(toutLeTexte(hm).contains("${"));
    }

    @Test
    void modeRegexDeLEnregistreur() throws Exception {
        HTTPSamplerProxy s = requete();
        TestElement copie = (TestElement) s.clone();
        String attendu = toutLeTexte(s);

        remplaceur("hote", "app\\.exemple\\.[a-z]+").reverseReplace(s, true);
        assertTrue(toutLeTexte(s).contains("${hote}"));

        RestaurationValeurs.restaurer(s, copie);
        assertEquals(attendu, toutLeTexte(s));
    }

    @Test
    void lesValeursVidesOuSansVariableNeSontPasTouchees() throws Exception {
        HTTPSamplerProxy s = requete();
        TestElement copie = (TestElement) s.clone();
        // Ce que fait l'enregistreur avec les « HTTP Request Defaults » :
        // une valeur identique aux défauts est vidée. Cela doit rester.
        s.setDomain("");
        assertEquals(0, RestaurationValeurs.restaurer(s, copie));
        assertEquals("", s.getDomain());
    }

    @Test
    void lElementEnregistreSeSauvegardeSansVariable() throws Exception {
        HTTPSamplerProxy s = requete();
        TestElement copie = (TestElement) s.clone();
        remplaceur("hote", "app.exemple.fr", "mdp", "secret42").reverseReplace(s, false);
        RestaurationValeurs.restaurer(s, copie);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        SaveService.saveElement(s, out);
        String xml = out.toString("UTF-8");
        assertTrue(xml.contains("app.exemple.fr"), xml);
        assertFalse(xml.contains("${hote}") || xml.contains("${mdp}"), xml);
    }
}
