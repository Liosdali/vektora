package org.example.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.io.StringReader;

class ImageTracerVectorizerTest {
    private final SvgVectorizer vectorizer = new ImageTracerVectorizer();

    @Test
    void vectorizesTransparentArtworkAsPathsWithoutEmbeddingRasterData() throws Exception {
        BufferedImage image = image(8, 8, true);
        fill(image, 2, 2, 4, 4, Color.RED);

        String svg = vectorizer.vectorize(image, TraceOptions.preserveSourceColors());
        Document document = parse(svg);

        assertTrue(document.getElementsByTagName("path").getLength() > 0);
        assertTrue(document.getElementsByTagName("image").getLength() == 0);
        assertEquals(Set.of("RGB(255,0,0)"), pathFills(document));
        assertEquals(Set.of("0.0", "1.0"), pathOpacities(document));
    }

    @Test
    void mapsArtworkToEverySelectedPaletteColor() throws Exception {
        BufferedImage image = image(8, 8, false);
        fill(image, 0, 0, 8, 8, Color.RED);
        TraceOptions options = new TraceOptions(
                TraceOptions.ColorMode.CUSTOM_PALETTE, 2, List.of(Color.BLUE, Color.GREEN));

        Document document = parse(vectorizer.vectorize(image, options));
        Set<String> fills = pathFills(document);

        assertTrue(fills.contains("RGB(0,0,255)") || fills.contains("RGB(0,255,0)"));
        assertFalse(fills.contains("RGB(255,0,0)"));
    }

    @Test
    void keepsTransparentPixelsTransparentWhenUsingASingleColorPalette() throws Exception {
        BufferedImage image = image(12, 12, true);
        fill(image, 3, 3, 6, 6, Color.RED);
        TraceOptions options = new TraceOptions(
                TraceOptions.ColorMode.CUSTOM_PALETTE, 1, List.of(Color.BLUE));

        Document document = parse(vectorizer.vectorize(image, options));

        assertEquals(Set.of("RGB(0,0,255)"), pathFills(document));
        assertTrue(pathOpacities(document).contains("0.0"));
        assertTrue(pathOpacities(document).contains("1.0"));
    }

    @Test
    void removesSolidBorderBackgroundBeforeMappingTheArtworkToOneColor() throws Exception {
        BufferedImage image = image(12, 12, false);
        fill(image, 0, 0, 12, 12, Color.WHITE);
        fill(image, 1, 1, 10, 10, new Color(248, 248, 248));
        fill(image, 3, 3, 6, 6, Color.RED);
        fill(image, 5, 5, 1, 1, Color.WHITE);
        TraceOptions options = new TraceOptions(
                TraceOptions.ColorMode.CUSTOM_PALETTE, 1, List.of(Color.BLUE));

        Document document = parse(vectorizer.vectorize(image, options));

        assertEquals(Set.of("RGB(0,0,255)"), pathFills(document));
        assertTrue(pathOpacities(document).contains("0.0"));
        assertTrue(pathOpacities(document).contains("1.0"));
    }

    @Test
    void automaticBorderCleanupPreservesDisconnectedSimilarColoredArtwork() throws Exception {
        BufferedImage image = image(12, 12, false);
        fill(image, 0, 0, 12, 12, Color.WHITE);
        fill(image, 1, 1, 10, 10, new Color(248, 248, 248));
        fill(image, 3, 3, 6, 6, Color.RED);
        image.setRGB(5, 5, Color.WHITE.getRGB());

        Set<String> fills = pathFills(parse(vectorizer.vectorize(
                image, TraceOptions.preserveSourceColors())));

        assertTrue(fills.contains("RGB(255,255,255)"));
    }

    @Test
    void cropsTransparentMarginsSoTheVectorUsesTheArtworkBounds() throws Exception {
        BufferedImage image = image(20, 20, true);
        fill(image, 6, 6, 8, 8, Color.RED);

        Document document = parse(vectorizer.vectorize(image, TraceOptions.preserveSourceColors()));

        assertEquals("0 0 10 10", document.getDocumentElement().getAttribute("viewBox"));
    }

    @Test
    void reducesSourceArtworkToTheConfiguredColorLimit() throws Exception {
        BufferedImage image = image(9, 3, false);
        fill(image, 0, 0, 3, 3, Color.RED);
        fill(image, 3, 0, 3, 3, Color.GREEN);
        fill(image, 6, 0, 3, 3, Color.BLUE);
        TraceOptions options = new TraceOptions(
                TraceOptions.ColorMode.REDUCE_SOURCE, 2, List.of());

        Set<String> fills = pathFills(parse(vectorizer.vectorize(image, options)));

        assertTrue(fills.size() <= 2);
    }

    @Test
    void rejectsNullImagesWithAnExplicitVectorizationError() {
        assertThrows(VectorizationException.class,
                () -> vectorizer.vectorize(null, TraceOptions.preserveSourceColors()));
    }

    private static BufferedImage image(int width, int height, boolean transparent) {
        return new BufferedImage(width, height,
                transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    }

    private static void fill(BufferedImage image, int x, int y, int width, int height, Color color) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                image.setRGB(column, row, color.getRGB());
            }
        }
    }

    private static Document parse(String svg) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
    }

    private static Set<String> pathFills(Document document) {
        NodeList paths = document.getElementsByTagName("path");
        return java.util.stream.IntStream.range(0, paths.getLength())
                .mapToObj(index -> (Element) paths.item(index))
                .filter(element -> !"0.0".equals(element.getAttribute("opacity")))
                .map(element -> element.getAttribute("fill").toUpperCase())
                .filter(fill -> !fill.isBlank())
                .collect(Collectors.toSet());
    }

    private static Set<String> pathOpacities(Document document) {
        NodeList paths = document.getElementsByTagName("path");
        return java.util.stream.IntStream.range(0, paths.getLength())
                .mapToObj(index -> (Element) paths.item(index))
                .map(element -> element.getAttribute("opacity"))
                .collect(Collectors.toSet());
    }
}
