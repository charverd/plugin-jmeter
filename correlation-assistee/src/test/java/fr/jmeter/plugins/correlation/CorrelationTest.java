package fr.jmeter.plugins.correlation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.jmeter.plugins.correlation.Correlation.Candidat;
import fr.jmeter.plugins.correlation.Correlation.Nature;
import fr.jmeter.plugins.correlation.Requete.Champ;
import fr.jmeter.plugins.correlation.Requete.TypeChamp;

class CorrelationTest {

    private static Requete req(String methode, String chemin, Champ... champs) {
        List<Champ> l = new ArrayList<>(Requete.segmentsDuChemin(chemin));
        l.addAll(Arrays.asList(champs));
        return new Requete(methode + " " + chemin, methode, chemin, l);
    }

    private static Champ p(String nom, String v) {
        return new Champ(TypeChamp.PARAMETRE, nom, v);
    }

    private static Champ h(String nom, String v) {
        return new Champ(TypeChamp.ENTETE, nom, v);
    }

    private static Champ corps(String v) {
        return new Champ(TypeChamp.CORPS, "corps", v);
    }

    private static List<Requete> enregistrement(String csrf, String sid, String commande, String uuid, String ts,
            boolean favicon) {
        List<Requete> l = new ArrayList<>();
        l.add(req("GET", "/"));
        l.add(req("GET", "/login"));
        if (favicon) {
            l.add(req("GET", "/favicon.ico"));
        }
        l.add(req("POST", "/login", p("user", "alice"), p("csrf", csrf), h("Cookie", "SID=" + sid)));
        l.add(req("GET", "/commande/" + commande + "/detail", h("X-Request-Id", uuid)));
        l.add(req("POST", "/api/panier", corps("{\"csrf\":\"" + csrf + "\",\"ts\":" + ts + "}")));
        return l;
    }

    private final List<Requete> a = enregistrement("Zk81Hq2LmP0aXw9eR4tY", "aaaa", "48151623",
            "6f1c2a9e-0b3d-4e5f-8a7b-1c2d3e4f5a6b", "1792177725718", false);
    private final List<Requete> b = enregistrement("Qm27Lp9XcV3bN8wE1rTz", "bbbb", "42424242",
            "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", "1792177799999", true);

    private static Candidat trouver(List<Candidat> l, String valeur) {
        for (Candidat c : l) {
            if (c.getValeurA().equals(valeur)) {
                return c;
            }
        }
        return null;
    }

    @Test
    void cle() {
        assertEquals("GET /commande/*/detail", a.get(3).cle());
        assertEquals("GET /cmd/*/detail", req("get", "/cmd/12345/detail?x=1").cle());
        assertEquals("GET /o/*", req("GET", "/o/6f1c2a9e-0b3d-4e5f-8a7b-1c2d3e4f5a6b").cle());
        assertEquals("GET /o/*", req("GET", "/o/0123456789abcdef0123").cle());
        assertEquals("GET /", req("GET", "/").cle());
    }

    @Test
    void segmentsDuChemin() {
        List<Champ> s = Requete.segmentsDuChemin("/a/b/c?x=1");
        assertEquals(3, s.size());
        assertEquals("segment 2", s.get(2).getNom());
        assertEquals("c", s.get(2).getValeur());
    }

    @Test
    void alignerSauteLaRequeteEnPlus() {
        List<int[]> p = Correlation.aligner(a, b);
        assertEquals(5, p.size());
        assertEquals(2, p.get(2)[0]);
        assertEquals(3, p.get(2)[1], "le favicon de B est sauté");
    }

    @Test
    void detecterLesValeursDynamiques() {
        List<Candidat> c = Correlation.detecter(a, b);

        Candidat csrf = trouver(c, "Zk81Hq2LmP0aXw9eR4tY");
        assertNotNull(csrf);
        assertEquals(Nature.JETON, csrf.getNature());
        assertEquals("csrf", csrf.getNomPropose());
        assertEquals(2, csrf.premierUsage());
        assertEquals(2, csrf.getUsages().size());
        assertEquals(TypeChamp.PARAMETRE, csrf.getUsages().get(0).getType());
        assertEquals(4, csrf.getUsages().get(1).getRequete());
        assertEquals(TypeChamp.CORPS, csrf.getUsages().get(1).getType());
        assertEquals("Qm27Lp9XcV3bN8wE1rTz", csrf.getValeurB());

        Candidat cmd = trouver(c, "48151623");
        assertNotNull(cmd);
        assertEquals(Nature.NOMBRE, cmd.getNature());
        assertEquals("id_chemin", cmd.getNomPropose());
        assertEquals(TypeChamp.CHEMIN, cmd.getUsages().get(0).getType());

        Candidat uuid = trouver(c, "6f1c2a9e-0b3d-4e5f-8a7b-1c2d3e4f5a6b");
        assertNotNull(uuid);
        assertEquals(Nature.UUID, uuid.getNature());
        assertEquals("x_request_id", uuid.getNomPropose());

        Candidat ts = trouver(c, "1792177725718");
        assertNotNull(ts);
        assertEquals(Nature.HORODATAGE, ts.getNature());

        assertNull(trouver(c, "SID=aaaa"), "en-tête Cookie ignoré");
        assertNull(trouver(c, "alice"));
        assertEquals(4, c.size());
        // Ordre : par première utilisation.
        assertEquals("Zk81Hq2LmP0aXw9eR4tY", c.get(0).getValeurA());
    }

    @Test
    void nomsProposesUniques() {
        List<Requete> x = Arrays.asList(req("GET", "/p", p("id", "AAAA1111"), p("ID", "BBBB2222")));
        List<Requete> y = Arrays.asList(req("GET", "/p", p("id", "CCCC3333"), p("ID", "DDDD4444")));
        List<Candidat> c = Correlation.detecter(x, y);
        assertEquals(2, c.size());
        assertEquals("id", c.get(0).getNomPropose());
        assertEquals("id_2", c.get(1).getNomPropose());
    }

    @Test
    void natures() {
        assertEquals(Nature.HORODATAGE, Correlation.nature("1792177725"));
        assertEquals(Nature.NOMBRE, Correlation.nature("2792177725"));
        assertEquals(Nature.TEXTE, Correlation.nature("abc def"));
    }

    @Test
    void bornesSurUnChampCache() {
        Bornes b = Bornes.calculer("<input type=\"hidden\" name=\"csrf\" value=\"Zk81Hq2LmP0aXw9eR4tY\"/>",
                "Zk81Hq2LmP0aXw9eR4tY");
        assertNotNull(b);
        assertEquals("value=\"", b.getGauche());
        assertEquals("\"", b.getDroite());
    }

    @Test
    void borneGaucheAllongeeJusquALUnicite() {
        String rep = "<input name=\"a\" value=\"x\"/><input name=\"csrf\" value=\"JETON123456\"/>";
        Bornes b = Bornes.calculer(rep, "JETON123456");
        assertNotNull(b);
        assertTrue(b.getGauche().length() > "value=\"".length(), b.getGauche());
        assertEquals(rep.indexOf(b.getGauche()) + b.getGauche().length(), rep.indexOf("JETON123456"));
        assertFalse(Character.isWhitespace(b.getGauche().charAt(0)));
    }

    @Test
    void bornesCasLimites() {
        assertNull(Bornes.calculer("rien ici", "JETON"));
        Bornes fin = Bornes.calculer("{\"commande\": \"12345678", "12345678");
        assertNotNull(fin);
        assertEquals("", fin.getDroite());
        Bornes json = Bornes.calculer("{\"commande\": \"48151623\", \"statut\": \"ok\"}", "48151623");
        assertEquals("commande\": \"", json.getGauche());
        assertTrue(json.getDroite().startsWith("\","), json.getDroite());
    }
}
