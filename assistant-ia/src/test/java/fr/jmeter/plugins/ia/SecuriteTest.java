package fr.jmeter.plugins.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** Garde-fous de sécurité de l'assistant : XML du modèle, redirections, rendu HTML de Swing. */
class SecuriteTest {

    static {
        EnvironnementJMeter.preparer();
    }

    private static void accepte(String fragment) throws Exception {
        ControleXml.verifier(ApplicationActions.analyser(fragment));
    }

    private static String refus(String fragment) {
        return assertThrows(ControleXml.Refus.class, () -> accepte(fragment)).getMessage();
    }

    // ------------------------------------------------------------------ liste blanche du XML

    @Test
    void fragmentsDuCatalogueAcceptes() throws Exception {
        for (String ligne : PromptSysteme.CATALOGUE) {
            accepte(ligne.substring(ligne.indexOf('<')) + "<hashTree/>");
        }
        accepte("<HTTPSamplerProxy guiclass=\"HttpTestSampleGui\" testclass=\"HTTPSamplerProxy\" testname=\"r\""
                + " enabled=\"true\"><elementProp name=\"HTTPsampler.Arguments\" elementType=\"Arguments\""
                + " guiclass=\"HTTPArgumentsPanel\" testclass=\"Arguments\"><collectionProp name=\"Arguments.arguments\">"
                + "<elementProp name=\"q\" elementType=\"HTTPArgument\"><stringProp name=\"Argument.value\">1</stringProp>"
                + "</elementProp></collectionProp></elementProp><stringProp name=\"HTTPSampler.path\">/a</stringProp>"
                + "</HTTPSamplerProxy><hashTree><ResponseAssertion guiclass=\"AssertionGui\" testclass=\"ResponseAssertion\""
                + " testname=\"c\"><intProp name=\"Assertion.test_type\">8</intProp></ResponseAssertion><hashTree/></hashTree>");
    }

    @Test
    void baliseQuiNEstPasUnElementDeTestRefusee() {
        assertTrue(refus("<java.lang.String>x</java.lang.String>").contains("n'est pas un TestElement"));
        assertTrue(refus("<ClasseQuiNExistePas/>").contains("classe inconnue"));
    }

    @Test
    void proprietesHorsListeRefusees() {
        String debut = "<ConstantTimer guiclass=\"ConstantTimerGui\" testclass=\"ConstantTimer\" testname=\"t\">";
        assertTrue(refus(debut + "<objProp><name>x</name></objProp></ConstantTimer>").contains("<objProp>"));
        assertTrue(refus(debut + "<mapProp name=\"m\"/></ConstantTimer>").contains("<mapProp>"));
        assertTrue(refus(debut + "<stringProp name=\"a\" class=\"java.lang.String\">1</stringProp></ConstantTimer>")
                .contains("attribut « class »"));
        assertTrue(refus(debut + "<stringProp name=\"a\"><b/></stringProp></ConstantTimer>").contains("que du texte"));
        assertTrue(refus("<stringProp name=\"a\">1</stringProp>").contains("hors d'un élément"));
        assertTrue(refus("<ConstantTimer guiclass=\"ConstantTimerGui\" resolves-to=\"x\"/>").contains("resolves-to"));
    }

    @Test
    void classesDesAttributsVerifiees() {
        assertTrue(refus("<ConstantTimer guiclass=\"java.lang.Object\" testclass=\"ConstantTimer\"/>")
                .contains("n'est pas un JMeterGUIComponent"));
        assertTrue(refus("<ConstantTimer guiclass=\"ConstantTimerGui\" testclass=\"java.lang.Object\"/>")
                .contains("n'est pas un TestElement"));
        assertTrue(refus("<ConstantTimer guiclass=\"ConstantTimerGui\"><elementProp name=\"e\""
                + " elementType=\"java.util.ArrayList\"/></ConstantTimer>").contains("n'est pas un TestElement"));
        assertTrue(refus("<hashTree class=\"x\"/>").contains("attribut inattendu"));
    }

    @Test
    void doctypeRefuse() {
        assertThrows(Exception.class, () -> ApplicationActions.analyser("<!DOCTYPE x [<!ENTITY e \"a\">]><a/>"));
    }

    @Test
    void codeExecutableSignale() throws Exception {
        List<String> l = ApplicationActions.codeExecutable("<JSR223Sampler guiclass=\"TestBeanGUI\" testclass=\"JSR223Sampler\""
                + " testname=\"s\"><stringProp name=\"script\">1</stringProp></JSR223Sampler><hashTree/>");
        assertEquals(1, l.size(), l.toString());
        assertTrue(l.get(0).contains("JSR223Sampler"));
        assertTrue(ApplicationActions.codeExecutable("<ConstantTimer guiclass=\"ConstantTimerGui\" testclass=\"ConstantTimer\""
                + " testname=\"t\"><stringProp name=\"ConstantTimer.delay\">${__groovy(1+1)}</stringProp></ConstantTimer>")
                .get(0).contains("__groovy"));
        assertTrue(ApplicationActions.codeExecutable(PromptSysteme.CATALOGUE[0].substring(PromptSysteme.CATALOGUE[0]
                .indexOf('<'))).isEmpty());
        assertEquals("__javaScript", ControleXml.fonctionScript("a ${__javaScript(1)}"));
        assertNull(ControleXml.fonctionScript("${__time()} ${utilisateur}"));
    }

    // ------------------------------------------------------------------ réseau

    @Test
    void redirectionNonSuivie() throws Exception {
        AtomicInteger appelsAutreHote = new AtomicInteger();
        HttpServer autre = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        autre.createContext("/", x -> {
            appelsAutreHote.incrementAndGet();
            x.sendResponseHeaders(200, -1);
            x.close();
        });
        HttpServer profil = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String cible = "http://127.0.0.1:" + autre.getAddress().getPort() + "/v1/models";
        profil.createContext("/", x -> {
            x.getResponseHeaders().add("Location", cible);
            x.sendResponseHeaders(302, -1);
            x.close();
        });
        autre.start();
        profil.start();
        try {
            ClientOpenAI c = new ClientOpenAI(new Profil("p", "http://127.0.0.1:" + profil.getAddress().getPort() + "/v1",
                    "m", "sk-secret"));
            IOException e = assertThrows(IOException.class, c::listerModeles);
            assertTrue(e.getMessage().contains("302") && e.getMessage().contains("non suivie"), e.getMessage());
            assertEquals(0, appelsAutreHote.get(), "l'autre hôte n'a reçu aucune requête (ni la clé)");
        } finally {
            profil.stop(0);
            autre.stop(0);
        }
    }

    @Test
    void schemaAutreQueHttpRefuse() {
        ClientOpenAI c = new ClientOpenAI(new Profil("p", "file:///etc", "m", ""));
        IOException e = assertThrows(IOException.class, c::listerModeles);
        assertTrue(e.getMessage().contains("http ou https"), e.getMessage());
    }

    @Test
    void configurationParDefautLocaleSeulement() {
        for (Profil p : ConfigAssistant.exemple().profils) {
            assertTrue(p.url.startsWith("http://localhost:"), p.url);
        }
    }

    // ------------------------------------------------------------------ rendu Swing

    @Test
    void pasDeRenduHtmlDansLesTables() {
        String piege = "<html><img src='http://127.0.0.1:1/x.png'>";
        JTable t = SansHtml.table(new JTable(new DefaultTableModel(new Object[][] { { piege } }, new Object[] { "a" })));
        JLabel rendu = (JLabel) t.prepareRenderer(t.getCellRenderer(0, 0), 0, 0);
        assertEquals(piege, rendu.getText());
        assertNull(rendu.getClientProperty("html"), "aucune vue HTML créée (donc aucune image chargée)");
        JLabel temoin = new JLabel(piege);
        assertTrue(temoin.getClientProperty("html") != null, "témoin : sans la protection, Swing crée une vue HTML");
    }
}
