package fr.jmeter.plugins.transactionsi;

import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.control.TransactionSampler;
import org.apache.jmeter.samplers.SampleResult;

/**
 * Ligne « (ignorée) » écrite quand la condition est fausse et que l'option de traçage est
 * cochée. C'est une transaction déjà terminée : {@code JMeterThread} la traite comme la fin
 * d'une transaction et l'envoie aux écouteurs qui voient le contrôleur, sans rien exécuter.
 */
final class PassageIgnore extends TransactionSampler {

    private static final long serialVersionUID = 1L;

    static final String SUFFIXE = " (ignorée)";

    PassageIgnore(TransactionController controleur, String nom, String condition) {
        super(controleur, nom + SUFFIXE);
        SampleResult r = getTransactionResult();
        r.setSampleLabel(nom + SUFFIXE);
        r.sampleEnd();
        r.setSuccessful(true);
        r.setResponseCode("204");
        r.setResponseMessage("Condition fausse : " + condition);
    }

    @Override
    public boolean isTransactionDone() {
        return true;
    }
}
