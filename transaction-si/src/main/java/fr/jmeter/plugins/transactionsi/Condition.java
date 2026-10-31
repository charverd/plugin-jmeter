package fr.jmeter.plugins.transactionsi;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;

import org.apache.jmeter.util.JMeterUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Évaluation de la condition, avec les règles du Contrôleur Si de JMeter 5.4.3
 * ({@code IfController}, dont les méthodes sont privées) :
 * <ul>
 * <li>expression de variable : vraie si la valeur vaut {@code true}, sans tenir compte de la casse ;</li>
 * <li>JavaScript : Nashorn, ou Rhino si {@code javascript.use_rhino=true} ou si le JDK n'a plus
 * Nashorn (Java 15 et plus) ; le résultat doit être {@code true} ou {@code false}, sinon la
 * condition est fausse et l'erreur est journalisée.</li>
 * </ul>
 * Seule différence voulue : une condition vide est vraie.
 */
final class Condition {

    private static final Logger log = LoggerFactory.getLogger(Condition.class);

    private static final String NASHORN = "nashorn";

    private static final ScriptEngineManager MANAGER = new ScriptEngineManager();

    private static final boolean RHINO = JMeterUtils.getPropDefault("javascript.use_rhino", false)
            || MANAGER.getEngineByName(NASHORN) == null;

    private static final ThreadLocal<ScriptEngine> MOTEUR_NASHORN =
            ThreadLocal.withInitial(() -> MANAGER.getEngineByName(NASHORN));

    private Condition() {
    }

    static boolean evaluer(String nomElement, String condition, boolean expression) {
        String c = condition == null ? "" : condition.trim();
        if (c.isEmpty()) {
            return true;
        }
        if (expression) {
            return c.equalsIgnoreCase("true");
        }
        try {
            String resultat = RHINO ? rhino(c) : nashorn(c);
            switch (resultat) {
                case "true":
                    return true;
                case "false":
                    return false;
                default:
                    throw new IllegalArgumentException(" BAD CONDITION :: " + c + " :: expected true or false");
            }
        } catch (Exception e) {
            log.error("{}: error while processing [{}]", nomElement, c, e);
            return false;
        }
    }

    /** Libère le moteur Nashorn du thread (fin de thread JMeter). */
    static void liberer() {
        MOTEUR_NASHORN.remove();
    }

    private static String rhino(String condition) {
        Context cx = Context.enter();
        try {
            Scriptable portee = cx.initStandardObjects(null);
            return Context.toString(cx.evaluateString(portee, condition, "<cmd>", 1, null));
        } finally {
            Context.exit();
        }
    }

    private static String nashorn(String condition) throws Exception {
        ScriptEngine moteur = MOTEUR_NASHORN.get();
        ScriptContext contexte = new SimpleScriptContext();
        contexte.setBindings(moteur.createBindings(), ScriptContext.ENGINE_SCOPE);
        return String.valueOf(moteur.eval(condition, contexte));
    }
}
