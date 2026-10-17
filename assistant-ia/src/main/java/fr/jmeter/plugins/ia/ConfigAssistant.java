package fr.jmeter.plugins.ia;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Réglages de l'assistant, dans un fichier JSON de l'utilisateur
 * (~/.jmeter-assistant-ia.json par défaut). Champs publics pour Jackson.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigAssistant {

    static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public List<Profil> profils = new ArrayList<>();
    /** Nom du profil utilisé à l'ouverture. */
    public String profilActif = "";
    /** Prompt système ; vide = celui par défaut ({@link PromptSysteme#DEFAUT}). */
    public String promptSysteme = "";
    /** Commandes rapides : libellé → texte envoyé. Vide = celles par défaut. */
    public Map<String, String> commandes = new LinkedHashMap<>();
    /** Taille maximale du contexte joint (caractères) ; au-delà, l'arborescence est abrégée. */
    public int contexteMax = 60000;

    /** Fichier de configuration : propriété système « assistant.ia.config », sinon ~/.jmeter-assistant-ia.json. */
    public static File fichier() {
        String p = System.getProperty("assistant.ia.config");
        return p != null && !p.isEmpty() ? new File(p)
                : new File(System.getProperty("user.home"), ".jmeter-assistant-ia.json");
    }

    /** Lit le fichier ; s'il n'existe pas, rend une configuration d'exemple (sans l'écrire). */
    public static ConfigAssistant charger(File f) throws IOException {
        if (!f.isFile()) {
            return exemple();
        }
        ConfigAssistant c = JSON.readValue(f, ConfigAssistant.class);
        if (c.profils == null) {
            c.profils = new ArrayList<>();
        }
        if (c.commandes == null) {
            c.commandes = new LinkedHashMap<>();
        }
        return c;
    }

    /** Écrit le fichier, lisible par son seul propriétaire quand le système le permet (il peut contenir des clés). */
    public void enregistrer(File f) throws IOException {
        File dossier = f.getAbsoluteFile().getParentFile();
        if (dossier != null && !dossier.isDirectory() && !dossier.mkdirs()) {
            throw new IOException("Impossible de créer " + dossier);
        }
        JSON.writeValue(f, this);
        try {
            Files.setPosixFilePermissions(f.toPath(),
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException | IOException e) {
            // Windows : droits du dossier utilisateur.
        }
    }

    public static ConfigAssistant exemple() {
        ConfigAssistant c = new ConfigAssistant();
        c.profils.add(new Profil("Ollama (local)", "http://localhost:11434/v1", "llama3.1", ""));
        c.profils.add(new Profil("LM Studio (local)", "http://localhost:1234/v1", "", ""));
        c.profils.add(new Profil("OpenAI", "https://api.openai.com/v1", "", "${env:OPENAI_API_KEY}"));
        c.profils.add(new Profil("Mistral", "https://api.mistral.ai/v1", "", "${env:MISTRAL_API_KEY}"));
        c.profils.add(new Profil("OpenRouter", "https://openrouter.ai/api/v1", "", "${env:OPENROUTER_API_KEY}"));
        c.profilActif = c.profils.get(0).nom;
        return c;
    }

    public Profil profil(String nom) {
        for (Profil p : profils) {
            if (p.nom.equals(nom)) {
                return p;
            }
        }
        return profils.isEmpty() ? null : profils.get(0);
    }

    public String prompt() {
        return promptSysteme == null || promptSysteme.trim().isEmpty() ? PromptSysteme.DEFAUT : promptSysteme;
    }

    public Map<String, String> commandesEffectives() {
        return commandes == null || commandes.isEmpty() ? PromptSysteme.COMMANDES : commandes;
    }
}
