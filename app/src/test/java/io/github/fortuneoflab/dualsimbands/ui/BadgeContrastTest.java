package io.github.fortuneoflab.dualsimbands.ui;

import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * The band badges carry a white label, or a near-black one on a light badge (RadioCard picks white when it reads at
 * 4.5:1): one of the two keeps WCAG's 4.5:1 for normal text on every badge, light and dark.
 */
public class BadgeContrastTest {
    private static final String[] BADGES = {"badge_lte", "badge_nr", "badge_nr_refarmed", "badge_nr_mmwave"};

    /**
     * The colour element's value, read as XML so comments, attributes and line breaks do not matter. A colour the
     * night resources leave out is the default one, as Android resolves it.
     */
    private static String color(String file, String name) throws Exception {
        if (!"values".equals(file) && !defines(file, name)) return color("values", name);
        org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(Paths.get("src/main/res", file, "colors.xml").toFile());
        org.w3c.dom.NodeList colors = doc.getElementsByTagName("color");
        String found = null;
        for (int i = 0; i < colors.getLength(); i++) {
            org.w3c.dom.Element e = (org.w3c.dom.Element) colors.item(i);
            if (!name.equals(e.getAttribute("name"))) continue;
            assertNull(file + " defines " + name + " once", found);
            found = e.getTextContent().trim();
        }
        assertNotNull(file + " has " + name, found);
        assertTrue(file + "/" + name + " is #RRGGBB: " + found, found.matches("#[0-9a-fA-F]{6}"));
        return found.substring(1);
    }

    private static boolean defines(String file, String name) throws Exception {
        org.w3c.dom.NodeList colors = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(Paths.get("src/main/res", file, "colors.xml").toFile()).getElementsByTagName("color");
        for (int i = 0; i < colors.getLength(); i++)
            if (name.equals(((org.w3c.dom.Element) colors.item(i)).getAttribute("name"))) return true;
        return false;
    }

    private static double luminance(String hex) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    static double contrastWithWhite(String hex) { return 1.05 / (luminance(hex) + 0.05); }

    /** RadioCard's near-black label, #1A1A1A. */
    static double contrastWithDark(String hex) { return (luminance(hex) + 0.05) / (luminance("1a1a1a") + 0.05); }

    @Test public void labelsReadOnEveryBadge() throws Exception {
        for (String dir : new String[]{"values", "values-night"})
            for (String name : BADGES) {
                String hex = color(dir, name);
                assertTrue(dir + "/" + name, contrastWithWhite(hex) >= 4.5 || contrastWithDark(hex) >= 4.5);
            }
    }

    @Test public void contrastFormula() {
        assertEquals(21.0, contrastWithWhite("000000"), 0.01);
        assertEquals(1.0, contrastWithWhite("ffffff"), 0.01);
    }
}
