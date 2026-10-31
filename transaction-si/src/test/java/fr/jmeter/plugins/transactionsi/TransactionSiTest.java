package fr.jmeter.plugins.transactionsi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.jmeter.assertions.AssertionResult;
import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.control.TransactionSampler;
import org.apache.jmeter.engine.util.NoThreadClone;
import org.apache.jmeter.samplers.AbstractSampler;
import org.apache.jmeter.testelement.AbstractTestElement;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.threads.JMeterContext;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.threads.JMeterThread;
import org.apache.jmeter.threads.JMeterVariables;
import org.apache.jmeter.threads.SamplePackage;
import org.apache.jmeter.threads.ThreadGroup;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionSiTest {

    /** Requête de test : son nom donne son code (« ok » → 200, « ko » → 500). */
    static class Bouchon extends AbstractSampler {
        private static final long serialVersionUID = 1L;

        Bouchon(String nom) {
            setName(nom);
        }

        @Override
        public SampleResult sample(Entry e) {
            SampleResult r = new SampleResult();
            r.setSampleLabel(getName());
            r.sampleStart();
            r.sampleEnd();
            boolean ok = getName().startsWith("ok");
            r.setSuccessful(ok);
            r.setResponseCode(ok ? "200" : "500");
            r.setResponseMessage(ok ? "OK" : "Internal Server Error");
            if (getName().endsWith("assert")) {
                r.setResponseCode("200");
                r.setResponseMessage("OK");
                AssertionResult a = new AssertionResult("Réponse");
                a.setFailure(true);
                a.setFailureMessage("Test failed: text expected to contain /Bienvenue/");
                r.addAssertionResult(a);
            }
            return r;
        }
    }

    /** Écouteur qui garde les lignes de transaction émises. */
    static class Collecte extends AbstractTestElement implements SampleListener, NoThreadClone {
        private static final long serialVersionUID = 1L;

        final List<SampleResult> lignes = new ArrayList<>();

        @Override
        public void sampleOccurred(SampleEvent e) {
            lignes.add(e.getResult());
        }

        @Override
        public void sampleStarted(SampleEvent e) {
        }

        @Override
        public void sampleStopped(SampleEvent e) {
        }
    }

    private Collecte collecte;

    private static List<String> l(String... noms) {
        return java.util.Arrays.asList(noms);
    }

    @BeforeAll
    static void jmeter() throws Exception {
        File home = new File(TransactionSiTest.class.getResource("/jmeter-home/bin/saveservice.properties").toURI())
                .getParentFile().getParentFile();
        JMeterUtils.setJMeterHome(home.getAbsolutePath());
        JMeterUtils.loadJMeterProperties(new File(home, "bin/jmeter.properties").getAbsolutePath());
    }

    @BeforeEach
    void contexte() {
        collecte = new Collecte();
        JMeterContext ctx = JMeterContextService.getContext();
        JMeterVariables vars = new JMeterVariables();
        vars.putObject(JMeterThread.PACKAGE_OBJECT, new SamplePackage(Collections.emptyList(),
                Collections.singletonList(collecte), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
        ctx.setVariables(vars);
        ThreadGroup tg = new ThreadGroup();
        tg.setName("Groupe");
        ctx.setThreadGroup(tg);
    }

    private static TransactionSi transaction(String condition, String... enfants) {
        TransactionSi t = new TransactionSi();
        t.setName("T01");
        t.setCondition(condition);
        for (String e : enfants) {
            t.addTestElement(new Bouchon(e));
        }
        t.initialize();
        return demarrer(t);
    }

    /**
     * Au démarrage d'un tir, JMeter fige les propriétés : reInitialize() y revient à chaque
     * passage. Les réglages se font donc avant, et un changement en cours d'essai se fait
     * comme entre deux tirs.
     */
    private static TransactionSi regler(TransactionSi t, java.util.function.Consumer<TransactionSi> reglage) {
        t.setRunningVersion(false);
        reglage.accept(t);
        t.setRunningVersion(true);
        return t;
    }

    private static TransactionSi demarrer(TransactionSi t) {
        t.setRunningVersion(true);
        return t;
    }

    /** Une itération en mode normal, comme JMeterThread : renvoie les noms des requêtes jouées. */
    private static List<String> jouer(TransactionSi t) {
        List<String> joues = new ArrayList<>();
        Sampler s;
        while ((s = t.next()) != null) {
            if (s instanceof TransactionSampler) {
                joues.add("[" + ((TransactionSampler) s).getTransactionResult().getSampleLabel() + "]");
                continue;
            }
            joues.add(s.getName());
            t.sampleOccurred(new SampleEvent(s.sample(null), "Groupe"));
        }
        return joues;
    }

    /** Une itération en mode parent : renvoie la ligne de transaction (ou null). */
    private static SampleResult jouerParent(TransactionSi t, List<String> joues) {
        Sampler s;
        SampleResult ligne = null;
        while ((s = t.next()) != null) {
            TransactionSampler ts = (TransactionSampler) s;
            if (ts.isTransactionDone()) {
                ligne = ts.getTransactionResult();
            } else {
                Sampler sous = ts.getSubSampler();
                joues.add(sous.getName());
                ts.addSubSamplerResult(sous.sample(null));
            }
        }
        return ligne;
    }

    @Test
    void conditionVraieJoueToutEtEmetUneLigne() {
        TransactionSi t = transaction("true", "ok-1", "ok-2");
        assertEquals(l("ok-1", "ok-2"), jouer(t));
        assertEquals(1, collecte.lignes.size());
        SampleResult l = collecte.lignes.get(0);
        assertEquals("T01", l.getSampleLabel());
        assertTrue(l.isSuccessful());
        assertEquals("200", l.getResponseCode());
        assertTrue(TransactionController.isFromTransactionController(l));
        assertNull(l.getFirstAssertionFailureMessage());
    }

    @Test
    void conditionFausseNeJoueRienEtNEmetRien() {
        TransactionSi t = transaction("false", "ok-1", "ok-2");
        assertEquals(l(), jouer(t));
        assertEquals(l(), jouer(t));
        assertTrue(collecte.lignes.isEmpty());
        // la condition est réévaluée à chaque passage
        regler(t, x -> x.setCondition("TRUE"));
        assertEquals(l("ok-1", "ok-2"), jouer(t));
        assertEquals(1, collecte.lignes.size());
    }

    @Test
    void conditionVideEstVraie() {
        assertEquals(l("ok-1"), jouer(transaction("", "ok-1")));
        assertEquals(l("ok-1"), jouer(transaction("   ", "ok-1")));
    }

    @Test
    void valeurInattendueEstFausse() {
        assertEquals(l(), jouer(transaction("oui", "ok-1")));
    }

    @Test
    void javascript() {
        TransactionSi t = transaction("1 == 1", "ok-1");
        regler(t, x -> x.setUseExpression(false));
        assertEquals(l("ok-1"), jouer(t));
        TransactionSi f = transaction("'a' == 'b'", "ok-1");
        regler(f, x -> x.setUseExpression(false));
        assertEquals(l(), jouer(f));
        TransactionSi erreur = transaction("ceci n'est pas du javascript", "ok-1");
        regler(erreur, x -> x.setUseExpression(false));
        assertEquals(l(), jouer(erreur));
        TransactionSi pasBooleen = transaction("42", "ok-1");
        regler(pasBooleen, x -> x.setUseExpression(false));
        assertEquals(l(), jouer(pasBooleen));
    }

    @Test
    void echecDetailleEnModeNormal() {
        TransactionSi t = transaction("true", "ok-1", "ko-2", "ko-3");
        jouer(t);
        SampleResult l = collecte.lignes.get(0);
        assertFalse(l.isSuccessful());
        assertEquals("500", l.getResponseCode());
        assertEquals("Premier échec : ko-2 (500 Internal Server Error)", l.getFirstAssertionFailureMessage());
        assertTrue(l.getResponseMessage().startsWith("Number of samples in transaction : 3, number of failing samples : 2"));
    }

    @Test
    void echecDAssertionDetaille() {
        TransactionSi t = transaction("true", "ok-1", "ok-2-assert");
        // un échec d'assertion rend la requête en échec avant que les écouteurs la voient
        Sampler s;
        while ((s = t.next()) != null) {
            SampleResult r = s.sample(null);
            if (r.getAssertionResults().length > 0) {
                r.setSuccessful(false);
            }
            t.sampleOccurred(new SampleEvent(r, "Groupe"));
        }
        SampleResult l = collecte.lignes.get(0);
        assertEquals("200", l.getResponseCode());
        assertEquals("Premier échec : ok-2-assert (200 OK) ; Test failed: text expected to contain /Bienvenue/",
                l.getFirstAssertionFailureMessage());
    }

    @Test
    void sansDetailLaLigneEstCelleDuStandard() {
        TransactionSi t = transaction("true", "ok-1", "ko-2");
        regler(t, x -> x.setDetailFailure(false));
        jouer(t);
        SampleResult l = collecte.lignes.get(0);
        assertFalse(l.isSuccessful());
        assertEquals("", l.getResponseCode());
        assertNull(l.getFirstAssertionFailureMessage());
    }

    @Test
    void passagesIgnoresTraces() {
        TransactionSi t = transaction("false", "ok-1");
        regler(t, x -> x.setTraceIgnored(true));
        Sampler s = t.next();
        assertTrue(s instanceof PassageIgnore);
        TransactionSampler ts = (TransactionSampler) s;
        assertTrue(ts.isTransactionDone());
        assertSame(t, ts.getTransactionController());
        SampleResult r = ts.getTransactionResult();
        assertEquals("T01 (ignorée)", r.getSampleLabel());
        assertEquals("204", r.getResponseCode());
        assertEquals("Condition fausse : false", r.getResponseMessage());
        assertTrue(r.isSuccessful());
        assertTrue(r.getTime() < 50);
        assertNull(t.next());
        // passage suivant : la condition est réévaluée
        assertTrue(t.next() instanceof PassageIgnore);
        assertNull(t.next());
        regler(t, x -> x.setCondition("true"));
        assertEquals(l("ok-1"), jouer(t));
    }

    @Test
    void modeParent() {
        TransactionSi t = transaction("true", "ok-1", "ko-2");
        regler(t, x -> x.setGenerateParentSample(true));
        List<String> joues = new ArrayList<>();
        SampleResult l = jouerParent(t, joues);
        assertEquals(l("ok-1", "ko-2"), joues);
        assertNotNull(l);
        assertEquals("T01", l.getSampleLabel());
        assertFalse(l.isSuccessful());
        assertEquals("500", l.getResponseCode());
        assertEquals(2, l.getSubResults().length);
        assertEquals("Premier échec : ko-2 (500 Internal Server Error)", l.getFirstAssertionFailureMessage());
        // passage suivant, condition fausse : rien
        regler(t, x -> x.setCondition("false"));
        joues.clear();
        assertNull(jouerParent(t, joues));
        assertEquals(l(), joues);
        // puis vraie à nouveau, tout réussit
        regler(t, x -> x.setCondition("true"));
        SampleResult l2 = jouerParent(t, joues);
        assertEquals(l("ok-1", "ko-2"), joues);
        assertNotNull(l2);
    }

    @Test
    void modeParentReussi() {
        TransactionSi t = transaction("", "ok-1", "ok-2");
        regler(t, x -> x.setGenerateParentSample(true));
        SampleResult l = jouerParent(t, new ArrayList<>());
        assertTrue(l.isSuccessful());
        assertEquals("200", l.getResponseCode());
        assertNull(l.getFirstAssertionFailureMessage());
    }

    @Test
    void finDeBoucleAuMilieuDeLaTransaction() {
        TransactionSi t = transaction("true", "ok-1", "ko-2", "ok-3");
        JMeterContextService.getContext().getVariables().put(JMeterThread.LAST_SAMPLE_OK, "false");
        Sampler s = t.next();
        t.sampleOccurred(new SampleEvent(s.sample(null), "Groupe"));
        s = t.next();
        t.sampleOccurred(new SampleEvent(s.sample(null), "Groupe"));
        t.triggerEndOfLoop(); // « Start next thread loop » après ko-2
        SampleResult l = collecte.lignes.get(0);
        assertFalse(l.isSuccessful());
        assertEquals("500", l.getResponseCode());
        assertEquals("Premier échec : ko-2 (500 Internal Server Error)", l.getFirstAssertionFailureMessage());
        // l'itération suivante repart de zéro et réévalue la condition
        regler(t, x -> x.setCondition("false"));
        assertEquals(l(), jouer(t));
    }

    @Test
    void finDeBoucleEnModeParent() {
        TransactionSi t = transaction("true", "ok-1", "ko-2", "ok-3");
        regler(t, x -> x.setGenerateParentSample(true));
        TransactionSampler ts = null;
        for (int i = 0; i < 2; i++) {
            ts = (TransactionSampler) t.next();
            ts.addSubSamplerResult(ts.getSubSampler().sample(null));
        }
        t.triggerEndOfLoop();
        SampleResult l = ts.getTransactionResult();
        assertTrue(ts.isTransactionDone());
        assertEquals("500", l.getResponseCode());
        assertEquals("Premier échec : ko-2 (500 Internal Server Error)", l.getFirstAssertionFailureMessage());
    }

    @Test
    void messageTronqueEtSurUneLigne() {
        SampleResult r = new SampleResult();
        r.setSampleLabel("R");
        r.setResponseCode("500");
        StringBuilder long_ = new StringBuilder("ligne 1\nligne 2 ");
        for (int i = 0; i < 100; i++) {
            long_.append("0123456789");
        }
        r.setResponseMessage(long_.toString());
        String m = DetailEchec.message(r);
        assertEquals(DetailEchec.LONGUEUR_MAX, m.length());
        assertFalse(m.contains("\n"));
        assertTrue(m.startsWith("Premier échec : R (500 ligne 1 ligne 2 "));
    }

    @Test
    void sauvegardeEtRelecture() throws Exception {
        TransactionSi t = new TransactionSi();
        t.setName("T02_Panier");
        // posés par l'écran, comme dans un vrai plan
        t.setProperty(org.apache.jmeter.testelement.TestElement.GUI_CLASS, TransactionSiGui.class.getName());
        t.setProperty(org.apache.jmeter.testelement.TestElement.TEST_CLASS, TransactionSi.class.getName());
        t.setCondition("${__jexl3(\"${connecte}\" == \"oui\")}");
        t.setGenerateParentSample(true);
        t.setIncludeTimers(false);
        t.setTraceIgnored(true);
        t.setDetailFailure(false);
        t.setUseExpression(false);
        HashTree arbre = new HashTree();
        arbre.add(t);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SaveService.saveTree(arbre, out);
        String xml = out.toString("UTF-8");
        assertTrue(xml.contains("<fr.jmeter.plugins.transactionsi.TransactionSi "), xml);
        File f = File.createTempFile("transaction-si", ".jmx");
        f.deleteOnExit();
        java.nio.file.Files.write(f.toPath(), out.toByteArray());
        HashTree relu = SaveService.loadTree(f);
        TransactionSi r = (TransactionSi) relu.getArray()[0];
        assertEquals("T02_Panier", r.getName());
        assertEquals("${__jexl3(\"${connecte}\" == \"oui\")}", r.getCondition());
        assertTrue(r.isGenerateParentSample());
        assertFalse(r.isIncludeTimers());
        assertTrue(r.isTraceIgnored());
        assertFalse(r.isDetailFailure());
        assertFalse(r.isUseExpression());
    }

    @Test
    void valeursParDefaut() {
        TransactionSi t = new TransactionSi();
        assertTrue(t.isUseExpression());
        assertTrue(t.isDetailFailure());
        assertFalse(t.isTraceIgnored());
        assertFalse(t.isGenerateParentSample());
        assertEquals("", t.getCondition());
    }
}
