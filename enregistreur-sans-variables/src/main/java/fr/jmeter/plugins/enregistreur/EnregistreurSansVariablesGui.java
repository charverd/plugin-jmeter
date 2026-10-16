package fr.jmeter.plugins.enregistreur;

import org.apache.jmeter.protocol.http.proxy.ProxyControl;
import org.apache.jmeter.protocol.http.proxy.gui.ProxyControlGui;

/**
 * Écran de l'enregistreur : celui de JMeter tel quel, branché sur
 * {@link EnregistreurSansVariables}.
 */
public class EnregistreurSansVariablesGui extends ProxyControlGui {

    private static final long serialVersionUID = 1L;

    @Override
    protected ProxyControl makeProxyControl() {
        return new EnregistreurSansVariables();
    }

    @Override
    public String getStaticLabel() {
        return "Enregistreur de script sans variables";
    }
}
