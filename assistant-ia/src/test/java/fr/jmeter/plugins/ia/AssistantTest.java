package fr.jmeter.plugins.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class AssistantTest {

    /** Écouteur qui note tout. */
    static final class Note implements ClientOpenAI.Ecouteur {
        final StringBuilder texte = new StringBuilder();
        final StringBuilder raisonnement = new StringBuilder();
        String fin;
        String erreur;

        @Override
        public void texte(String m) {
            texte.append(m);
        }

        @Override
        public void raisonnement(String m) {
            raisonnement.append(m);
        }

        @Override
        public void fin(String r) {
            fin = r;
        }

        @Override
        public void erreur(String m) {
            erreur = m;
        }
    }

    // ------------------------------------------------------------------ flux SSE

    @Test
    void fluxOpenAIAvecRaisonnement() throws Exception {
        String sse = String.join("\n",
                ": commentaire ignoré",
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}",
                "",
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"Je réfléchis\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"Bon\"}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"jour\"},\"finish_reason\":null}]}",
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "data: [DONE]",
                "data: {\"choices\":[{\"delta\":{\"content\":\"après la fin\"}}]}");
        Note n = new Note();
        String raison = ClientOpenAI.lireFlux(new BufferedReader(new StringReader(sse)), n);
        assertEquals("Bonjour", n.texte.toString());
        assertEquals("Je réfléchis", n.raisonnement.toString());
        assertEquals("stop", raison);
        assertNull(n.erreur);
    }

    @Test
    void fluxAvecChampReasoningEtErreur() throws Exception {
        Note n = new Note();
        ClientOpenAI.lireFlux(new BufferedReader(new StringReader(
                "data: {\"choices\":[{\"delta\":{\"reasoning\":\"r\"}}]}\n"
                        + "data: {\"error\":{\"message\":\"quota dépassé\"}}\n")), n);
        assertEquals("r", n.raisonnement.toString());
        assertTrue(n.erreur.contains("quota dépassé"), n.erreur);
    }

    @Test
    void reponseCompleteSansFlux() throws Exception {
        Note n = new Note();
        ClientOpenAI.lireReponseComplete("{\"choices\":[{\"message\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}", n);
        assertEquals("OK", n.texte.toString());
        assertEquals("stop", n.fin);
    }

    @Test
    void messagesDErreurLisibles() {
        assertEquals("HTTP 401 : Incorrect API key", ClientOpenAI.messageErreur(401,
                "{\"error\":{\"message\":\"Incorrect API key\",\"type\":\"invalid_request_error\"}}"));
        assertEquals("HTTP 404 : model not found", ClientOpenAI.messageErreur(404, "{\"error\":\"model not found\"}"));
        assertEquals("HTTP 502 : Bad Gateway", ClientOpenAI.messageErreur(502, "Bad Gateway"));
    }

    // ------------------------------------------------------------------ requête

    @Test
    void corpsSansMaxTokensEtAvecParametres() throws Exception {
        Profil p = new Profil("t", "http://h/v1/", "mon-modele", "");
        p.parametres.put("temperature", 0.2);
        java.util.Map<String, Object> kw = new java.util.LinkedHashMap<>();
        kw.put("enable_thinking", false);
        p.parametres.put("chat_template_kwargs", kw);
        String c = new ClientOpenAI(p).corps(Arrays.asList(new ClientOpenAI.Message("system", "S"),
                new ClientOpenAI.Message("user", "Q")), true);
        JsonNode n = ConfigAssistant.JSON.readTree(c);
        assertEquals("mon-modele", n.path("model").asText());
        assertTrue(n.path("stream").asBoolean());
        assertEquals("system", n.path("messages").path(0).path("role").asText());
        assertEquals("Q", n.path("messages").path(1).path("content").asText());
        assertFalse(n.has("max_tokens"), "aucune limite de tokens par défaut");
        assertEquals(0.2, n.path("temperature").asDouble());
        assertFalse(n.path("chat_template_kwargs").path("enable_thinking").asBoolean(true));
        assertEquals("http://h/v1/chat/completions", p.point("/chat/completions"));
    }

    @Test
    void variablesDEnvironnement() {
        String path = System.getenv("PATH");
        assertEquals("x" + path + "y", Profil.resoudre("x${env:PATH}y"));
        assertEquals("", Profil.resoudre("${env:VARIABLE_QUI_N_EXISTE_PAS_12345}"));
        assertEquals("sk-123", Profil.resoudre("sk-123"));
    }

    // ------------------------------------------------------------------ configuration

    @Test
    void configurationEnregistreeEtRelue() throws Exception {
        File f = File.createTempFile("assistant", ".json");
        f.deleteOnExit();
        ConfigAssistant c = ConfigAssistant.exemple();
        c.profils.get(0).entetes.put("api-key", "${env:CLE}");
        c.promptSysteme = "Prompt perso";
        c.enregistrer(f);
        ConfigAssistant r = ConfigAssistant.charger(f);
        assertEquals(c.profils.size(), r.profils.size());
        assertEquals("${env:CLE}", r.profils.get(0).entetes.get("api-key"));
        assertEquals("Prompt perso", r.prompt());
        assertTrue(new String(Files.readAllBytes(f.toPath())).contains("\"profils\""));
    }

    @Test
    void configurationParDefaut() throws Exception {
        ConfigAssistant c = ConfigAssistant.charger(new File("/n/existe/pas.json"));
        assertFalse(c.profils.isEmpty());
        assertEquals(PromptSysteme.DEFAUT, c.prompt());
        assertEquals(PromptSysteme.COMMANDES, c.commandesEffectives());
        assertTrue(c.prompt().contains("```" + PromptSysteme.BALISE));
    }

    // ------------------------------------------------------------------ actions

    @Test
    void lireUnBlocDActions() {
        String rep = "Je change le chemin et j'ajoute une assertion.\n\n```jmeter-actions\n[\n"
                + "  {\"action\":\"modifier\",\"cible\":\"0.0.2\",\"proprietes\":{\"HTTPSampler.path\":\"/v2\"}},\n"
                + "  {\"action\":\"ajouter\",\"parent\":\"0.0.2\",\"position\":0,\"xml\":\"<ConstantTimer/>\"},\n"
                + "  {\"action\":\"renommer\",\"cible\":\"0.0\",\"nom\":\"Clients\"},\n"
                + "]\n```\nVoilà.";
        ActionIA.Lecture l = ActionIA.lire(rep);
        assertTrue(l.blocTrouve);
        assertNull(l.erreur);
        assertEquals(3, l.actions.size());
        assertEquals(ActionIA.Type.MODIFIER, l.actions.get(0).getType());
        assertEquals("/v2", l.actions.get(0).getProprietes().get("HTTPSampler.path"));
        assertEquals(Integer.valueOf(0), l.actions.get(1).position);
        assertTrue(l.actions.get(2).isCochee());
        assertEquals("→ « Clients »", l.actions.get(2).resume());
    }

    @Test
    void retourALaLigneBrutDansLeXml() {
        String rep = "```jmeter-actions\n[{\"action\":\"remplacer\",\"cible\":\"0.1\",\"xml\":\"<HeaderManager>\n"
                + "<collectionProp name=\\\"HeaderManager.headers\\\"/>\n</HeaderManager>\"}]\n```";
        ActionIA.Lecture l = ActionIA.lire(rep);
        assertNull(l.erreur);
        assertTrue(l.actions.get(0).getXml().contains("HeaderManager.headers"));
    }

    @Test
    void blocJsonDeSecoursEtObjetSeul() {
        ActionIA.Lecture l = ActionIA.lire("```json\n{\"action\":\"supprimer\",\"cible\":\"0.3\"}\n```");
        assertEquals(1, l.actions.size());
        assertEquals(ActionIA.Type.SUPPRIMER, l.actions.get(0).getType());
        assertFalse(ActionIA.lire("```json\n{\"a\":1}\n```").blocTrouve, "un JSON quelconque n'est pas un bloc d'actions");
        assertFalse(ActionIA.lire("Simple réponse.").blocTrouve);
    }

    @Test
    void actionsInvalidesSignalees() {
        ActionIA.Lecture l = ActionIA.lire("```jmeter-actions\n[{\"action\":\"exploser\",\"cible\":\"0\"},"
                + "{\"action\":\"modifier\",\"cible\":\"0.1\"},{\"action\":\"ajouter\",\"xml\":\"<a/>\"},"
                + "{\"action\":\"désactiver\",\"cible\":\"0.2\"}]\n```");
        assertEquals(4, l.actions.size());
        assertTrue(l.actions.get(0).getErreur().contains("inconnue"));
        assertEquals("aucune propriété à modifier", l.actions.get(1).getErreur());
        assertEquals("parent manquant", l.actions.get(2).getErreur());
        assertFalse(l.actions.get(0).isCochee());
        assertNull(l.actions.get(3).getErreur(), "« désactiver » accentué est accepté");
        assertEquals(ActionIA.Type.DESACTIVER, l.actions.get(3).getType());
    }

    @Test
    void blocIllisible() {
        ActionIA.Lecture l = ActionIA.lire("```jmeter-actions\n[{\"action\": }]\n```");
        assertTrue(l.blocTrouve);
        assertNotNull(l.erreur);
    }

    // ------------------------------------------------------------------ XML

    @Test
    void hashTreeAjouteApresChaqueElement() throws Exception {
        String x = ApplicationActions.envelopper("<ConstantTimer testname=\"a\"/><HeaderManager testname=\"b\"/><hashTree/>");
        assertTrue(x.startsWith("<?xml"));
        assertTrue(x.contains("<jmeterTestPlan"));
        List<String> balises = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<(\\w+)").matcher(x);
        while (m.find()) {
            balises.add(m.group(1));
        }
        assertEquals(Arrays.asList("jmeterTestPlan", "hashTree", "ConstantTimer", "hashTree", "HeaderManager",
                "hashTree"), balises);
    }
}
