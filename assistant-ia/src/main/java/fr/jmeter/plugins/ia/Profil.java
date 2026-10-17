package fr.jmeter.plugins.ia;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un modèle joignable par l'API OpenAI (/v1/chat/completions) : Ollama,
 * LM Studio, llama.cpp / llama-swap, vLLM, Mistral, OpenRouter, OpenAI,
 * Azure OpenAI… Champs publics pour la sérialisation JSON.
 */
public class Profil {

    private static final Pattern ENV = Pattern.compile("\\$\\{env:([A-Za-z_][A-Za-z0-9_]*)\\}");

    /** Nom affiché. */
    public String nom = "";
    /** URL de base, jusqu'à /v1 compris : « http://localhost:11434/v1 ». */
    public String url = "";
    /** Identifiant du modèle côté serveur. */
    public String modele = "";
    /** Clé d'API, envoyée en « Authorization: Bearer ». Accepte ${env:NOM_DE_VARIABLE}. */
    public String cle = "";
    /** En-têtes ajoutés à chaque requête (par exemple « api-key » pour Azure). ${env:…} accepté. */
    public Map<String, String> entetes = new LinkedHashMap<>();
    /**
     * Champs ajoutés tels quels au corps de la requête : temperature,
     * chat_template_kwargs… Aucun max_tokens par défaut.
     */
    public Map<String, Object> parametres = new LinkedHashMap<>();

    public Profil() {
    }

    public Profil(String nom, String url, String modele, String cle) {
        this.nom = nom;
        this.url = url;
        this.modele = modele;
        this.cle = cle;
    }

    /** Remplace les ${env:NOM} par la variable d'environnement (vide si absente). */
    public static String resoudre(String valeur) {
        if (valeur == null) {
            return "";
        }
        Matcher m = ENV.matcher(valeur);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String v = System.getenv(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** URL du point d'accès, sans double barre oblique. */
    public String point(String chemin) {
        String base = url == null ? "" : url.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + chemin;
    }

    @Override
    public String toString() {
        return nom == null || nom.isEmpty() ? modele : nom;
    }
}
