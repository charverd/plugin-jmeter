package fr.jmeter.plugins.ia;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Client minimal de l'API OpenAI « chat completions », en flux (SSE). Sans
 * dépendance : HttpURLConnection, donc les réglages de proxy de la JVM
 * (options -H / -P de JMeter) s'appliquent. Aucun max_tokens n'est envoyé,
 * sauf s'il figure dans les paramètres du profil.
 */
public class ClientOpenAI {

    /** Ce que le client signale pendant la réponse (appelé depuis le fil de la requête). */
    public interface Ecouteur {
        void texte(String morceau);

        /** Raisonnement des modèles qui l'exposent (reasoning_content / reasoning). */
        void raisonnement(String morceau);

        void fin(String raison);

        void erreur(String message);
    }

    /** Un message de la conversation. */
    public static final class Message {
        public final String role;
        public final String contenu;

        public Message(String role, String contenu) {
            this.role = role;
            this.contenu = contenu;
        }
    }

    private static final int CONNEXION_MS = 15000;
    /** Silence maximal entre deux morceaux : un modèle local peut réfléchir longtemps. */
    private static final int LECTURE_MS = 15 * 60 * 1000;

    private final Profil profil;
    private volatile HttpURLConnection enCours;
    private volatile boolean arrete;

    public ClientOpenAI(Profil profil) {
        this.profil = profil;
    }

    /** Corps JSON de la requête : modèle, messages, flux, puis les paramètres du profil. */
    String corps(List<Message> messages, boolean flux) throws IOException {
        ObjectNode c = ConfigAssistant.JSON.createObjectNode();
        c.put("model", profil.modele);
        for (Message m : messages) {
            ObjectNode o = c.withArray("messages").addObject();
            o.put("role", m.role);
            o.put("content", m.contenu);
        }
        c.put("stream", flux);
        if (profil.parametres != null) {
            for (Map.Entry<String, Object> e : profil.parametres.entrySet()) {
                c.set(e.getKey(), ConfigAssistant.JSON.valueToTree(e.getValue()));
            }
        }
        return ConfigAssistant.JSON.writeValueAsString(c);
    }

    private HttpURLConnection ouvrir(String chemin, String methode) throws IOException {
        URL url = new URL(profil.point(chemin));
        if (!"http".equalsIgnoreCase(url.getProtocol()) && !"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("URL refusée (http ou https seulement) : " + url.getProtocol());
        }
        HttpURLConnection cx = (HttpURLConnection) url.openConnection();
        // Seul le serveur du profil est contacté : une redirection (vers un autre hôte, avec la clé
        // d'API dans les en-têtes) n'est pas suivie et remonte comme une erreur HTTP 3xx.
        cx.setInstanceFollowRedirects(false);
        cx.setUseCaches(false);
        cx.setRequestMethod(methode);
        cx.setConnectTimeout(CONNEXION_MS);
        cx.setReadTimeout(LECTURE_MS);
        cx.setRequestProperty("Content-Type", "application/json");
        String cle = Profil.resoudre(profil.cle).trim();
        if (!cle.isEmpty()) {
            cx.setRequestProperty("Authorization", "Bearer " + cle);
        }
        if (profil.entetes != null) {
            for (Map.Entry<String, String> e : profil.entetes.entrySet()) {
                cx.setRequestProperty(e.getKey(), Profil.resoudre(e.getValue()));
            }
        }
        return cx;
    }

    private static String lireTout(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] b = new char[8192];
            int n;
            while ((n = r.read(b)) > 0) {
                sb.append(b, 0, n);
            }
        }
        return sb.toString();
    }

    /** Message d'erreur lisible à partir d'une réponse d'erreur OpenAI ({"error":{"message":…}}) ou brute. */
    /** Message d'une réponse en erreur ; une redirection n'est pas suivie et l'indique. */
    private static String erreurHttp(HttpURLConnection cx, int code) throws IOException {
        if (code < 400) {
            String vers = cx.getHeaderField("Location");
            return "HTTP " + code + " : redirection" + (vers == null ? "" : " vers " + vers)
                    + " non suivie — corriger l'URL du profil";
        }
        return messageErreur(code, lireTout(cx.getErrorStream()));
    }

    static String messageErreur(int code, String corps) {
        String detail = corps == null ? "" : corps.trim();
        try {
            JsonNode n = ConfigAssistant.JSON.readTree(detail);
            JsonNode m = n.path("error").path("message");
            if (m.isTextual()) {
                detail = m.asText();
            } else if (n.path("error").isTextual()) {
                detail = n.path("error").asText();
            } else if (n.path("message").isTextual()) {
                detail = n.path("message").asText();
            }
        } catch (IOException e) {
            // corps non JSON : gardé tel quel
        }
        if (detail.length() > 500) {
            detail = detail.substring(0, 500) + "…";
        }
        return "HTTP " + code + (detail.isEmpty() ? "" : " : " + detail);
    }

    /**
     * Envoie la conversation et transmet la réponse au fil de l'eau. Bloquant :
     * à appeler hors du fil de l'interface.
     */
    public void discuter(List<Message> messages, Ecouteur e) {
        arrete = false;
        try {
            HttpURLConnection cx = ouvrir("/chat/completions", "POST");
            enCours = cx;
            cx.setDoOutput(true);
            cx.setRequestProperty("Accept", "text/event-stream");
            try (OutputStream o = cx.getOutputStream()) {
                o.write(corps(messages, true).getBytes(StandardCharsets.UTF_8));
            }
            int code = cx.getResponseCode();
            if (code >= 300) {
                e.erreur(erreurHttp(cx, code));
                return;
            }
            String type = cx.getContentType() == null ? "" : cx.getContentType();
            if (type.contains("application/json")) {
                // Serveur qui ignore « stream » : réponse complète.
                lireReponseComplete(lireTout(cx.getInputStream()), e);
                return;
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(cx.getInputStream(), StandardCharsets.UTF_8))) {
                String raison = lireFlux(r, e);
                e.fin(raison);
            }
        } catch (IOException ex) {
            if (arrete) {
                e.fin("arrêt");
            } else {
                e.erreur(ex.getClass().getSimpleName() + " : " + ex.getMessage());
            }
        } finally {
            enCours = null;
        }
    }

    /** Lit un flux SSE ; rend la raison de fin (finish_reason), ou "" si le serveur n'en donne pas. */
    static String lireFlux(BufferedReader r, Ecouteur e) throws IOException {
        String raison = "";
        String ligne;
        while ((ligne = r.readLine()) != null) {
            if (!ligne.startsWith("data:")) {
                continue;
            }
            String d = ligne.substring(5).trim();
            if (d.isEmpty()) {
                continue;
            }
            if ("[DONE]".equals(d)) {
                break;
            }
            JsonNode n = ConfigAssistant.JSON.readTree(d);
            if (n.has("error")) {
                e.erreur(messageErreur(0, d));
                return "erreur";
            }
            JsonNode choix = n.path("choices").path(0);
            JsonNode delta = choix.path("delta");
            String raisonne = texte(delta, "reasoning_content", "reasoning");
            if (!raisonne.isEmpty()) {
                e.raisonnement(raisonne);
            }
            String t = texte(delta, "content");
            if (!t.isEmpty()) {
                e.texte(t);
            }
            if (choix.path("finish_reason").isTextual()) {
                raison = choix.path("finish_reason").asText();
            }
        }
        return raison;
    }

    static void lireReponseComplete(String json, Ecouteur e) throws IOException {
        JsonNode choix = ConfigAssistant.JSON.readTree(json).path("choices").path(0);
        JsonNode m = choix.path("message");
        String raisonne = texte(m, "reasoning_content", "reasoning");
        if (!raisonne.isEmpty()) {
            e.raisonnement(raisonne);
        }
        e.texte(texte(m, "content"));
        e.fin(choix.path("finish_reason").asText(""));
    }

    private static String texte(JsonNode n, String... champs) {
        for (String c : champs) {
            if (n.path(c).isTextual()) {
                return n.path(c).asText();
            }
        }
        return "";
    }

    /** Interrompt la réponse en cours. */
    public void arreter() {
        arrete = true;
        HttpURLConnection cx = enCours;
        if (cx != null) {
            cx.disconnect();
        }
    }

    /** GET /models : identifiants des modèles proposés par le serveur. */
    public List<String> listerModeles() throws IOException {
        HttpURLConnection cx = ouvrir("/models", "GET");
        cx.setReadTimeout(CONNEXION_MS);
        int code = cx.getResponseCode();
        if (code >= 300) {
            throw new IOException(erreurHttp(cx, code));
        }
        List<String> l = new ArrayList<>();
        for (JsonNode m : ConfigAssistant.JSON.readTree(lireTout(cx.getInputStream())).path("data")) {
            if (m.path("id").isTextual()) {
                l.add(m.path("id").asText());
            }
        }
        return l;
    }

    /** Petite requête de test, sans flux : rend la réponse ou lève l'erreur. */
    public String tester() throws IOException {
        List<Message> m = new ArrayList<>();
        m.add(new Message("user", "Réponds uniquement : OK"));
        HttpURLConnection cx = ouvrir("/chat/completions", "POST");
        cx.setDoOutput(true);
        try (OutputStream o = cx.getOutputStream()) {
            o.write(corps(m, false).getBytes(StandardCharsets.UTF_8));
        }
        int code = cx.getResponseCode();
        if (code >= 300) {
            throw new IOException(erreurHttp(cx, code));
        }
        final StringBuilder sb = new StringBuilder();
        lireReponseComplete(lireTout(cx.getInputStream()), new Ecouteur() {
            @Override
            public void texte(String morceau) {
                sb.append(morceau);
            }

            @Override
            public void raisonnement(String morceau) {
                // ignoré pour le test
            }

            @Override
            public void fin(String raison) {
                // rien
            }

            @Override
            public void erreur(String message) {
                sb.append(message);
            }
        });
        return sb.toString().trim();
    }

}
