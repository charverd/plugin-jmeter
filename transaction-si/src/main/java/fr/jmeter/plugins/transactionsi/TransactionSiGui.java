package fr.jmeter.plugins.transactionsi;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.apache.jmeter.control.gui.AbstractControllerGui;
import org.apache.jmeter.gui.util.CheckBoxPanel;
import org.apache.jmeter.gui.util.VerticalPanel;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.gui.layout.VerticalLayout;

/**
 * Écran du contrôleur de transaction Si : les deux cases du contrôleur de transaction, la
 * condition du Contrôleur Si, et ce qui est écrit dans le fichier de résultats.
 */
public class TransactionSiGui extends AbstractControllerGui {

    private static final long serialVersionUID = 1L;

    static final String TITRE = "Contrôleur de transaction Si";

    private JCheckBox parent;
    private JCheckBox minuteries;
    private JTextArea condition;
    private JCheckBox expression;
    private JCheckBox detail;
    private JCheckBox trace;

    public TransactionSiGui() {
        init();
    }

    @Override
    public TestElement createTestElement() {
        TransactionSi t = new TransactionSi();
        t.setIncludeTimers(false); // comme le contrôleur de transaction standard
        modifyTestElement(t);
        return t;
    }

    @Override
    public void configure(TestElement el) {
        super.configure(el);
        TransactionSi t = (TransactionSi) el;
        parent.setSelected(t.isGenerateParentSample());
        minuteries.setSelected(t.isIncludeTimers());
        condition.setText(t.getPropertyAsString(TransactionSi.CONDITION));
        condition.setCaretPosition(0);
        expression.setSelected(t.isUseExpression());
        detail.setSelected(t.isDetailFailure());
        trace.setSelected(t.isTraceIgnored());
    }

    @Override
    public void modifyTestElement(TestElement el) {
        configureTestElement(el);
        TransactionSi t = (TransactionSi) el;
        t.setGenerateParentSample(parent.isSelected());
        t.setIncludeTimers(minuteries.isSelected());
        t.setCondition(condition.getText());
        t.setUseExpression(expression.isSelected());
        t.setDetailFailure(detail.isSelected());
        t.setTraceIgnored(trace.isSelected());
    }

    @Override
    public void clearGui() {
        super.clearGui();
        parent.setSelected(false);
        minuteries.setSelected(false);
        condition.setText("");
        expression.setSelected(true);
        detail.setSelected(true);
        trace.setSelected(false);
    }

    @Override
    public String getLabelResource() {
        return "transaction_si";
    }

    @Override
    public String getStaticLabel() {
        return TITRE;
    }

    private void init() { // appelée par le constructeur
        setLayout(new VerticalLayout(5, VerticalLayout.BOTH, VerticalLayout.TOP));
        setBorder(makeBorder());
        add(makeTitlePanel());

        VerticalPanel transaction = new VerticalPanel();
        transaction.setBorder(BorderFactory.createTitledBorder("Transaction"));
        parent = new JCheckBox(JMeterUtils.getResString("transaction_controller_parent"));
        minuteries = new JCheckBox(JMeterUtils.getResString("transaction_controller_include_timers"));
        transaction.add(CheckBoxPanel.wrap(parent));
        transaction.add(CheckBoxPanel.wrap(minuteries));
        add(transaction);

        JPanel blocCondition = new JPanel(new BorderLayout(0, 5));
        blocCondition.setBorder(BorderFactory.createTitledBorder(
                "Condition (évaluée à l'entrée ; vide = toujours exécutée)"));
        condition = new JTextArea(4, 60);
        condition.setName("condition");
        condition.setLineWrap(true);
        blocCondition.add(new JScrollPane(condition), BorderLayout.CENTER);
        VerticalPanel sousCondition = new VerticalPanel();
        expression = new JCheckBox("Interpréter la condition comme une expression de variable"
                + " (sinon : JavaScript)", true);
        sousCondition.add(CheckBoxPanel.wrap(expression));
        JLabel exemple = new JLabel("Exemple : ${__jexl3(\"${connecte}\" == \"oui\")}");
        exemple.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        sousCondition.add(exemple);
        blocCondition.add(sousCondition, BorderLayout.SOUTH);
        add(blocCondition);

        VerticalPanel resultats = new VerticalPanel();
        resultats.setBorder(BorderFactory.createTitledBorder("Fichier de résultats"));
        detail = new JCheckBox("En cas d'échec, écrire le code et le message du premier enfant en échec", true);
        trace = new JCheckBox("Condition fausse : écrire une ligne « <nom> (ignorée) »");
        resultats.add(CheckBoxPanel.wrap(detail));
        resultats.add(CheckBoxPanel.wrap(trace));
        add(resultats);
    }
}
