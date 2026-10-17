package fr.jmeter.plugins.ia;

import java.io.File;
import java.net.URISyntaxException;

import org.apache.jmeter.util.JMeterUtils;

/**
 * Dossier JMeter minimal pour les tests : {@code saveservice.properties} et
 * {@code upgrade.properties} de JMeter 5.4.3 (alias des balises JMX). Sans eux, la liste blanche
 * refuse toute balise, comme il se doit.
 */
final class EnvironnementJMeter {

    private static boolean pret;

    private EnvironnementJMeter() {
    }

    static synchronized void preparer() {
        if (pret) {
            return;
        }
        try {
            File home = new File(EnvironnementJMeter.class.getResource("/jmeter-home/bin/saveservice.properties").toURI())
                    .getParentFile().getParentFile();
            JMeterUtils.setJMeterHome(home.getAbsolutePath());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        pret = true;
    }
}
