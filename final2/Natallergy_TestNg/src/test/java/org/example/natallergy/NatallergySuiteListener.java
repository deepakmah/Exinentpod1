package org.example.natallergy;

import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.xml.XmlSuite;

import java.util.Map;

/**
 * <p><b>Why this exists:</b> values in {@code testng.xml} {@code <parameter name="..." value="..."/>} are <em>not</em>
 * the same as JVM {@code -D} flags. This listener runs when the suite starts and copies selected parameters into
 * {@link System#setProperty} so the rest of the code can keep using {@code System.getProperty(...)} as usual.</p>
 *
 * <p>Only keys starting with {@code natallergy.} plus {@code sitemapSeed} are copied, and an existing {@code -D}
 * on the command line is never overwritten.</p>
 */
public final class NatallergySuiteListener implements ISuiteListener {

    @Override
    public void onStart(ISuite suite) {
        if (suite == null) {
            return;
        }
        XmlSuite xmlSuite = suite.getXmlSuite();
        if (xmlSuite == null) {
            return;
        }
        copySuiteParametersToSystemProperties(xmlSuite.getParameters());
    }

    @Override
    public void onFinish(ISuite suite) {
        // no-op
    }

    static void copySuiteParametersToSystemProperties(Map<String, String> map) {
        if (map == null) {
            return;
        }
        for (Map.Entry<String, String> e : map.entrySet()) {
            String key = e.getKey();
            String val = e.getValue();
            if (key == null || val == null || val.isBlank()) {
                continue;
            }
            if (key.startsWith("natallergy.") || "sitemapSeed".equals(key)) {
                if (System.getProperty(key) == null) {
                    System.setProperty(key, val);
                }
            }
        }
    }
}
