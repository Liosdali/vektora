package org.example.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** WCAG 2.2 AA: body text needs at least 4.5:1 against every surface it sits on, in both themes. */
class ThemeContrastTest {
    private static final List<String> TEXT = List.of("-vk-text", "-vk-text-soft", "-vk-muted", "-vk-dim", "-vk-accent-text");
    private static final List<String> SURFACES = List.of("-vk-bg", "-vk-panel", "-vk-raised");

    @Test
    void darkThemeTextIsReadable() throws IOException {
        assertReadable(tokens(".root {"));
    }

    @Test
    void lightThemeTextIsReadable() throws IOException {
        Map<String, String> light = tokens(".root {");
        light.putAll(tokens(".root.light {"));
        assertReadable(light);
    }

    private static void assertReadable(Map<String, String> tokens) {
        for (String text : TEXT) {
            for (String surface : SURFACES) {
                double ratio = contrast(tokens.get(text), tokens.get(surface));
                assertTrue(ratio >= 4.5, text + " on " + surface + " is " + ratio);
            }
        }
        double onAccent = contrast(tokens.get("-vk-on-accent"), tokens.get("-vk-accent"));
        assertTrue(onAccent >= 4.5, "-vk-on-accent on -vk-accent is " + onAccent);
    }

    private static Map<String, String> tokens(String block) throws IOException {
        String css;
        try (InputStream input = IconWorkspace.class.getResourceAsStream("vektora.css")) {
            css = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        int start = css.indexOf(block);
        assertTrue(start >= 0, "missing " + block);
        String body = css.substring(start, css.indexOf('}', start));
        Map<String, String> tokens = new HashMap<>();
        Matcher matcher = Pattern.compile("(-vk-[a-z-]+):\s*(#[0-9a-fA-F]{6})").matcher(body);
        while (matcher.find()) {
            tokens.put(matcher.group(1), matcher.group(2));
        }
        return tokens;
    }

    private static double contrast(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double[] channels = {(rgb >> 16 & 0xff) / 255.0, (rgb >> 8 & 0xff) / 255.0, (rgb & 0xff) / 255.0};
        for (int index = 0; index < 3; index++) {
            double value = channels[index];
            channels[index] = value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }
}
