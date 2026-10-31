package fr.jmeter.plugins.transactionsi;

import java.lang.reflect.Field;

import org.apache.jmeter.control.NextIsNullException;
import org.apache.jmeter.control.TransactionController;
import org.apache.jmeter.control.TransactionSampler;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testelement.ThreadListener;
import org.apache.jmeter.testelement.property.BooleanProperty;
import org.apache.jmeter.testelement.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contrôleur de transaction Si : un contrôleur de transaction ({@link TransactionController},
 * dont il hérite le calcul des durées, les cumuls, le mode parent et la fin de boucle) qui ne
 * s'exécute que si sa condition est vraie, comme s'il était placé dans un Contrôleur Si.
 * <p>
 * La condition est évaluée une fois, à l'entrée. Fausse : aucun enfant n'est joué et, si
 * l'option est cochée, une ligne « &lt;nom&gt; (ignorée) » est écrite. Vraie : la transaction
 * se comporte comme la standard ; si elle échoue et que l'option est cochée, sa ligne porte le
 * code et le détail du premier enfant en échec (voir {@link DetailEchec}).
 */
public class TransactionSi extends TransactionController implements ThreadListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(TransactionSi.class);

    static final String CONDITION = "TransactionSi.condition";
    static final String USE_EXPRESSION = "TransactionSi.useExpression";
    static final String TRACE_IGNORED = "TransactionSi.traceIgnored";
    static final String DETAIL_FAILURE = "TransactionSi.detailFailure";

    /** Champs privés du parent : le résultat en cours (mode normal) et la transaction (mode parent). */
    private static final Field RES = champ("res");
    private static final Field TRANSACTION_SAMPLER = champ("transactionSampler");

    /** Condition vraie, transaction en cours d'exécution. */
    private transient boolean enCours;
    /** Une ligne « (ignorée) » vient d'être rendue : rendre la main au prochain appel. */
    private transient boolean ignoreRendu;
    /** Premier enfant en échec (mode normal). */
    private transient SampleResult premierEchec;

    public TransactionSi() {
        super();
        setUseExpression(true);
        setDetailFailure(true);
    }

    @Override
    public Sampler next() {
        if (ignoreRendu) {
            ignoreRendu = false;
            return sauter();
        }
        if (!enCours && isFirst()) {
            String condition = getCondition();
            if (!Condition.evaluer(getName(), condition, isUseExpression())) {
                if (isTraceIgnored()) {
                    ignoreRendu = true;
                    return new PassageIgnore(this, getName(), condition);
                }
                return sauter();
            }
            enCours = true;
            premierEchec = null;
        }
        Sampler suivant = super.next();
        if (suivant == null) {
            enCours = false;
        } else if (isDetailFailure() && suivant instanceof TransactionSampler) {
            TransactionSampler ts = (TransactionSampler) suivant;
            if (ts.getTransactionController() == this && ts.isTransactionDone()) {
                completerParent(ts);
            }
        }
        return suivant;
    }

    /** Comme le Contrôleur Si quand la condition est fausse : on saute tout le sous-arbre. */
    private Sampler sauter() {
        initializeSubControllers();
        try {
            return nextIsNull();
        } catch (NextIsNullException e) {
            return null;
        }
    }

    @Override
    public void sampleOccurred(SampleEvent se) {
        super.sampleOccurred(se);
        if (enCours && premierEchec == null && !isGenerateParentSample()
                && !se.isTransactionSampleEvent() && !se.getResult().isSuccessful()) {
            SampleResult r = se.getResult();
            if (isFromTransactionController(r) && r.getSubResults().length > 0) {
                SampleResult dedans = DetailEchec.premierEchec(r);
                premierEchec = dedans != null ? dedans : r;
            } else {
                premierEchec = r;
            }
        }
    }

    @Override
    protected void notifyListeners() {
        if (isDetailFailure()) {
            DetailEchec.completer((SampleResult) lire(RES), premierEchec);
        }
        premierEchec = null;
        super.notifyListeners();
    }

    @Override
    public void triggerEndOfLoop() {
        TransactionSampler ts = isGenerateParentSample() ? (TransactionSampler) lire(TRANSACTION_SAMPLER) : null;
        enCours = false;
        ignoreRendu = false;
        super.triggerEndOfLoop();
        // Mode parent : JMeterThread émet la ligne juste après, avec ce résultat.
        if (ts != null && isDetailFailure()) {
            completerParent(ts);
        }
    }

    @Override
    public void initialize() {
        super.initialize();
        enCours = false;
        ignoreRendu = false;
        premierEchec = null;
    }

    private static void completerParent(TransactionSampler ts) {
        SampleResult r = ts.getTransactionResult();
        DetailEchec.completer(r, DetailEchec.premierEchec(r));
    }

    private Object lire(Field champ) {
        if (champ == null) {
            return null;
        }
        try {
            return champ.get(this);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static Field champ(String nom) {
        try {
            Field f = TransactionController.class.getDeclaredField(nom);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warn("TransactionController.{} introuvable : le détail des échecs est désactivé ({})", nom, e.toString());
            return null;
        }
    }

    @Override
    public void threadStarted() {
        // rien à préparer
    }

    @Override
    public void threadFinished() {
        Condition.liberer();
    }

    public String getCondition() {
        return getPropertyAsString(CONDITION).trim();
    }

    public void setCondition(String condition) {
        setProperty(new StringProperty(CONDITION, condition));
    }

    public boolean isUseExpression() {
        return getPropertyAsBoolean(USE_EXPRESSION, true);
    }

    public void setUseExpression(boolean b) {
        setProperty(new BooleanProperty(USE_EXPRESSION, b));
    }

    public boolean isTraceIgnored() {
        return getPropertyAsBoolean(TRACE_IGNORED, false);
    }

    public void setTraceIgnored(boolean b) {
        setProperty(new BooleanProperty(TRACE_IGNORED, b));
    }

    public boolean isDetailFailure() {
        return getPropertyAsBoolean(DETAIL_FAILURE, true);
    }

    public void setDetailFailure(boolean b) {
        setProperty(new BooleanProperty(DETAIL_FAILURE, b));
    }
}
