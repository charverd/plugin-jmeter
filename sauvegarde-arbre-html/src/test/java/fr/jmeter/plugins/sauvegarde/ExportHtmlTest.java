package fr.jmeter.plugins.sauvegarde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.samplers.SampleResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ExportHtmlTest {

    private static final Function<String, String> LIBELLES = k -> "[" + k + "]";

    static SampleResult requete(String nom, String code, String corps) {
        SampleResult r = new SampleResult();
        r.setSampleLabel(nom);
        r.setResponseCode(code);
        r.setSuccessful(code.startsWith("2"));
        r.setResponseMessage("OK");
        r.setDataType(SampleResult.TEXT);
        r.setContentType("text/html; charset=UTF-8");
        r.setResponseData(corps, "UTF-8");
        r.setSamplerData("GET http://exemple/" + nom);
        r.setRequestHeaders("Accept: text/html");
        r.setResponseHeaders("HTTP/1.1 " + code + " OK\nContent-Type: text/html");
        r.setThreadName("Groupe 1-1");
        r.setStampAndTime(1_760_000_000_000L, 120);
        return r;
    }

    static SampleResult transaction(String nom, SampleResult... enfants) {
        SampleResult t = new SampleResult();
        t.setSampleLabel(nom);
        t.setSuccessful(true);
        t.setResponseCode("200");
        t.setStampAndTime(1_760_000_000_000L, 0);
        t.setResponseMessage(ExportHtml.PREFIXE_TRANSACTION + enfants.length + ", number of failing samples : 0");
        for (SampleResult e : enfants) {
            t.addSubResult(e, false); // comme TransactionSampler : pas de renommage « parent-N »
        }
        return t;
    }

    static String html(List<ArbreExporte> arbres, int max) throws Exception {
        StringWriter w = new StringWriter();
        new ExportHtml(LIBELLES, max).ecrire(w,
                new ExportHtml.Entete("Mon <plan>", "/tmp/plan.jmx", new Date(0), "Sauvegarde avant « Nettoyer tout »"),
                arbres);
        return w.toString();
    }

    static JsonNode donnees(String html) throws Exception {
        String debut = "<script id=\"donnees\" type=\"application/json\">";
        int i = html.indexOf(debut) + debut.length();
        int j = html.indexOf("</script>", i);
        return new ObjectMapper().readTree(html.substring(i, j));
    }

    @Test
    void pageAutonomeAvecArbreEtDetails() throws Exception {
        SampleResult accueil = requete("01 Accueil", "200", "<html>bonjour</html>");
        SampleResult connexion = requete("02 Connexion", "500", "erreur");
        AssertionResult a = new AssertionResult("Code 200");
        a.setFailure(true);
        a.setFailureMessage("Test failed: code expected to equal 200");
        connexion.addAssertionResult(a);
        connexion.setSuccessful(false);
        SampleResult tx = transaction("T01_Accueil", accueil, connexion);

        String h = html(Collections.singletonList(new ArbreExporte("Arbre", "Plan > Groupe > Arbre", false, null,
                Arrays.asList(tx, requete("03 Seule", "200", "x")))), 0);

        assertTrue(h.startsWith("<!DOCTYPE html>"));
        assertTrue(h.contains("<title>[view_results_tree_title] — Mon &lt;plan&gt; — "), "titre échappé");
        assertFalse(h.contains("__DONNEES__") || h.contains("__TITRE__"));
        assertFalse(h.matches("(?s).*(src|href)=\"https?:.*"), "aucune ressource externe");

        JsonNode d = donnees(h);
        assertEquals("Mon <plan>", d.path("plan").asText());
        assertEquals("[view_results_tab_sampler]", d.path("libelles").path("view_results_tab_sampler").asText());
        JsonNode res = d.path("arbres").path(0).path("resultats");
        assertEquals(2, res.size());
        JsonNode t = res.path(0);
        assertTrue(t.path("tx").asBoolean(), "transaction reconnue");
        assertEquals("01 Accueil", t.path("enf").path(0).path("l").asText(), "pas de renommage des enfants");
        JsonNode c = t.path("enf").path(1);
        assertFalse(c.path("ok").asBoolean());
        assertEquals("<html>bonjour</html>", t.path("enf").path(0).path("corps").asText());
        assertEquals("GET http://exemple/02 Connexion", c.path("req").asText());
        assertEquals("Accept: text/html", c.path("reqh").asText());
        assertTrue(c.path("resh").asText().startsWith("HTTP/1.1 500"));
        // Assertion en échec : un nœud enfant, comme dans JMeter.
        JsonNode an = c.path("enf").path(0);
        assertTrue(an.path("a").asBoolean());
        assertEquals("Code 200", an.path("l").asText());
        assertTrue(an.path("stats").asText().contains("[view_results_assertion_failure_message]Test failed"));
        assertFalse(res.path(1).has("tx"));
    }

    @Test
    void statistiquesCommeJMeter() {
        SampleResult r = requete("Accueil", "302", "");
        String[] lignes = new ExportHtml(LIBELLES, 0).statistiques(r).split("\n", -1);
        assertEquals("[view_results_thread_name]Groupe 1-1", lignes[0]);
        assertTrue(lignes[1].startsWith("[view_results_sample_start]"));
        // La page colore la 13e ligne (index 12) : le code de réponse.
        assertEquals("[view_results_response_code]302", lignes[12]);
        assertEquals("[view_results_response_message]OK", lignes[13]);
        assertEquals("SampleResult [view_results_fields]", lignes[16]);
        assertEquals("ContentType: text/html; charset=UTF-8", lignes[17]);
    }

    @Test
    void reponseQuiFermeraitLeScript() throws Exception {
        String piege = "<script>alert(1)</script><!-- </SCRIPT> --> &   fin";
        String h = html(Collections.singletonList(new ArbreExporte("A", "A", false, null,
                Collections.singletonList(requete("piège", "200", piege)))), 0);
        String debut = "<script id=\"donnees\" type=\"application/json\">";
        int i = h.indexOf(debut) + debut.length();
        int j = h.indexOf("</script>", i);
        String bloc = h.substring(i, j);
        assertFalse(bloc.contains("<") || bloc.contains(">") || bloc.contains(" "), "rien qui ferme le bloc");
        assertEquals(piege, donnees(h).path("arbres").path(0).path("resultats").path(0).path("corps").asText());
    }

    @Test
    void reponseTronqueeImageEtBinaire() throws Exception {
        SampleResult grande = requete("grande", "200", "abcdefghij");
        SampleResult image = requete("logo", "200", "");
        image.setDataType(SampleResult.BINARY);
        image.setContentType("image/png");
        image.setResponseData(new byte[] { (byte) 0x89, 'P', 'N', 'G' });
        SampleResult pdf = requete("doc", "200", "");
        pdf.setDataType(SampleResult.BINARY);
        pdf.setContentType("application/pdf");
        pdf.setResponseData(new byte[] { 1, 2, 3 });
        SampleResult grandeImage = requete("photo", "200", "");
        grandeImage.setDataType(SampleResult.BINARY);
        grandeImage.setContentType("image/jpeg");
        grandeImage.setResponseData(new byte[50]);

        JsonNode res = donnees(html(Collections.singletonList(new ArbreExporte("A", "A", false, null,
                Arrays.asList(grande, image, pdf, grandeImage))), 5)).path("arbres").path(0).path("resultats");
        assertEquals("abcde", res.path(0).path("corps").asText());
        assertTrue(res.path(0).path("note").asText().contains("5 caractères gardés sur 10"));
        assertEquals("iVBORw==", res.path(1).path("img").asText());
        assertEquals("image/png", res.path(1).path("mime").asText());
        assertTrue(res.path(2).path("note").asText().contains("3 octets (application/pdf)"));
        assertFalse(res.path(3).has("img"), "image trop grande : non embarquée");
        assertTrue(res.path(3).path("note").asText().contains("50 octets"));
    }

    @Test
    void filtreDeLArbreFiltrableTransmis() throws Exception {
        Filtre f = new Filtre("Connexion", false, false, false, true);
        JsonNode a = donnees(html(Collections.singletonList(new ArbreExporte("Arbre filtrable", "P > Arbre filtrable",
                true, f, Collections.singletonList(requete("x", "200", "")))), 0)).path("arbres").path(0);
        assertTrue(a.path("filtrable").asBoolean());
        assertEquals("Connexion", a.path("filtre").path("motif").asText());
        assertTrue(a.path("filtre").path("transactions").asBoolean());
        assertEquals("« Connexion », transactions seules", f.description());
    }

    @Test
    void plusieursArbresEtNomsDeFichier(@TempDir File dossier) throws Exception {
        JsonNode d = donnees(html(Arrays.asList(
                new ArbreExporte("A", "P > A", false, null, Collections.singletonList(requete("a", "200", ""))),
                new ArbreExporte("B", "P > B", true, null, Collections.singletonList(requete("b", "200", "")))), 0));
        assertEquals(2, d.path("arbres").size());
        assertEquals("P > B", d.path("arbres").path(1).path("chemin").asText());

        Date date = new Date(0);
        File f1 = SauvegardeArbres.nomLibre(dossier, "plan", date);
        assertTrue(f1.getName().matches("plan_arbre-resultats_\\d{8}-\\d{6}\\.html"), f1.getName());
        Files.write(f1.toPath(), new byte[0]);
        File f2 = SauvegardeArbres.nomLibre(dossier, "plan", date);
        assertTrue(f2.getName().endsWith("-2.html"), f2.getName());
        assertEquals("parcours", SauvegardeArbres.base("/x/parcours.JMX"));
        assertEquals("plan", SauvegardeArbres.base(null));
    }

    @Test
    void ecritureAtomiqueEnUtf8(@TempDir File dossier) throws Exception {
        File f = new File(dossier, "sous/dossier/page.html");
        // JMeterUtils n'a pas de propriétés dans ce test : getPropDefault rend la valeur par défaut.
        SauvegardeArbres.ecrire(f, new ExportHtml.Entete("Plan é", null, new Date(), "test"),
                Collections.singletonList(new ArbreExporte("A", "A", false, null,
                        Collections.singletonList(requete("réponse", "200", "éàü €")))));
        String h = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        assertEquals("éàü €", donnees(h).path("arbres").path(0).path("resultats").path(0).path("corps").asText());
        assertEquals(1, dossier.toPath().resolve("sous/dossier").toFile().list().length, "pas de fichier temporaire restant");
        if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(
                    Files.getPosixFilePermissions(f.toPath())), "page lisible par son seul propriétaire");
        }
    }
}
