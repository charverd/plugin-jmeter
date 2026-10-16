package fr.jmeter.plugins.nettoyage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import fr.jmeter.plugins.nettoyage.AnalyseEnregistrement.Groupe;
import fr.jmeter.plugins.nettoyage.AnalyseEnregistrement.RequeteVue;

class AnalyseEnregistrementTest {

    private static final Set<String> EXT = AnalyseEnregistrement.lireExtensions(
            String.join(",", AnalyseEnregistrement.EXTENSIONS_STATIQUES_PAR_DEFAUT));
    private static final String HTML = "text/html,application/xhtml+xml";
    private static final String JSON = "application/json";

    private static RequeteVue r(String methode, String chemin, String accept) {
        return new RequeteVue("app.fr", chemin, methode, accept);
    }

    @Test
    void lireExtensions() {
        assertEquals(Arrays.asList("css", "js", "png"),
                new ArrayList<>(AnalyseEnregistrement.lireExtensions("CSS, .js;png")));
        assertTrue(AnalyseEnregistrement.lireExtensions("  ").isEmpty());
    }

    @Test
    void extension() {
        assertEquals("css", AnalyseEnregistrement.extension("/a/b.CSS?v=2"));
        assertEquals("", AnalyseEnregistrement.extension("/a.b/c"));
        assertEquals("", AnalyseEnregistrement.extension("/"));
        assertEquals("", AnalyseEnregistrement.extension(null));
        assertEquals("js", AnalyseEnregistrement.extension("/x/app.min.js#ancre"));
    }

    @Test
    void ressourceStatique() {
        assertTrue(AnalyseEnregistrement.estRessourceStatique(r("GET", "/s/app.js?v=1", "*/*"), EXT));
        assertFalse(AnalyseEnregistrement.estRessourceStatique(r("GET", "/api/data.json", JSON), EXT));
        assertFalse(AnalyseEnregistrement.estRessourceStatique(r("GET", "/connexion", HTML), EXT));
    }

    @Test
    void compterDomainesOrdreEtCasse() {
        List<RequeteVue> l = Arrays.asList(new RequeteVue("B.fr", "/", "GET", null),
                new RequeteVue("a.fr", "/", "GET", null), new RequeteVue("b.fr", "/x", "GET", null),
                new RequeteVue("c.fr", "/", "GET", null));
        Map<String, Integer> m = AnalyseEnregistrement.compterDomaines(l);
        assertEquals(Arrays.asList("b.fr", "a.fr", "c.fr"), new ArrayList<>(m.keySet()));
        assertEquals(2, (int) m.get("b.fr"));
    }

    @Test
    void tiersConnus() {
        assertTrue(AnalyseEnregistrement.estTiersConnu("www.google-analytics.com"));
        assertTrue(AnalyseEnregistrement.estTiersConnu("nr-data.net"));
        assertTrue(AnalyseEnregistrement.estTiersConnu("BAM.NR-DATA.NET"));
        assertFalse(AnalyseEnregistrement.estTiersConnu("analytics.google.com.exemple.fr"));
        assertFalse(AnalyseEnregistrement.estTiersConnu("exemple.fr"));
        assertFalse(AnalyseEnregistrement.estTiersConnu(null));
    }

    @Test
    void pagePrincipale() {
        assertTrue(AnalyseEnregistrement.estPagePrincipale(r("GET", "/connexion", HTML), EXT));
        assertFalse(AnalyseEnregistrement.estPagePrincipale(r("GET", "/api/x", JSON), EXT));
        assertTrue(AnalyseEnregistrement.estPagePrincipale(r("GET", "/commandes", null), EXT));
        assertFalse(AnalyseEnregistrement.estPagePrincipale(r("POST", "/login", null), EXT));
        assertFalse(AnalyseEnregistrement.estPagePrincipale(r("GET", "/s/app.js", "*/*"), EXT));
        assertFalse(AnalyseEnregistrement.estPagePrincipale(r("GET", "/s/app.js", HTML), EXT));
    }

    @Test
    void nomDePage() {
        assertEquals("commandes", AnalyseEnregistrement.nomDePage(r("GET", "/fr/mon-compte/commandes.aspx?id=3", null)));
        assertEquals("v1", AnalyseEnregistrement.nomDePage(r("GET", "/api/v1/", null)));
        assertEquals("connexion_20client", AnalyseEnregistrement.nomDePage(r("GET", "/connexion%20client", null)));
        assertEquals("accueil", AnalyseEnregistrement.nomDePage(r("GET", "/", null)));
        assertEquals("accueil", AnalyseEnregistrement.nomDePage(r("GET", "", null)));
        assertEquals("page", AnalyseEnregistrement.nomDePage(r("GET", "/%%%", null)));
    }

    @Test
    void regrouperUnParcoursRealiste() {
        List<RequeteVue> l = Arrays.asList(
                r("GET", "/static/app.js", "*/*"),
                r("GET", "/connexion", HTML),
                r("GET", "/s/a.css", "text/css"),
                r("GET", "/s/b.css", "text/css"),
                r("POST", "/api/login", JSON),
                r("GET", "/accueil", HTML),
                r("GET", "/img/logo.png", "image/png"),
                r("GET", "/api/panier", JSON),
                r("GET", "/commandes", null));
        List<Groupe> g = AnalyseEnregistrement.regrouperParPage(l, EXT);
        assertEquals(4, g.size());
        int[][] bornes = { { 0, 1 }, { 1, 5 }, { 5, 8 }, { 8, 9 } };
        String[] pages = { "app", "connexion", "accueil", "commandes" };
        int attendu = 0;
        for (int i = 0; i < 4; i++) {
            assertEquals(bornes[i][0], g.get(i).getDebut());
            assertEquals(bornes[i][1], g.get(i).getFin());
            assertEquals(pages[i], g.get(i).getPage());
            assertEquals(attendu, g.get(i).getDebut(), "groupes contigus");
            attendu = g.get(i).getFin();
        }
        assertEquals(l.size(), attendu, "couvre toute la liste");
        assertTrue(AnalyseEnregistrement.regrouperParPage(new ArrayList<RequeteVue>(), EXT).isEmpty());
    }

    @Test
    void nomTransaction() {
        assertEquals("T03_connexion", AnalyseEnregistrement.nomTransaction("T{n}_{page}", 3, 2, "connexion"));
        assertEquals("12_x", AnalyseEnregistrement.nomTransaction("", 12, 1, "x"));
        assertEquals("P0007", AnalyseEnregistrement.nomTransaction("P{n}", 7, 4, "x"));
    }
}
