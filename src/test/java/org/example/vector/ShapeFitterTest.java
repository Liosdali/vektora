package org.example.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ShapeFitterTest {
    @Test
    void turnsATracedCircleIntoFourCurvesOfTheSameSize() {
        String traced = polygon(ellipse(50, 50, 20, 20, 64, 0.2));

        String fitted = ShapeFitter.fit(traced);

        assertEquals(4, count(fitted, 'C'), fitted);
        assertEquals(0, count(fitted, 'L'), fitted);
        assertEquals(Math.PI * 400, area(fitted), Math.PI * 400 * 0.02);
        assertEquals(Math.signum(signedArea(traced)), Math.signum(signedArea(fitted)));
    }

    @Test
    void fitsAxisAlignedEllipses() {
        String fitted = ShapeFitter.fit(polygon(ellipse(60, 40, 30, 15, 72, 0.2)));

        assertEquals(4, count(fitted, 'C'), fitted);
        Rectangle2D bounds = parse(fitted).getBounds2D();
        assertEquals(30, bounds.getMinX(), 0.6);
        assertEquals(90, bounds.getMaxX(), 0.6);
        assertEquals(25, bounds.getMinY(), 0.6);
        assertEquals(55, bounds.getMaxY(), 0.6);
    }

    @Test
    void straightensNearlyStraightEdgesAndKeepsCorners() {
        StringBuilder jagged = new StringBuilder("M 0 0");
        for (int x = 2; x <= 40; x += 2) {
            jagged.append(" L ").append(x).append(' ').append(x % 4 == 0 ? "0.15" : "-0.15");
        }
        jagged.append(" L 40 20 L 0 20 Z");

        String fitted = ShapeFitter.fit(jagged.toString());

        assertTrue(count(fitted, 'L') <= 4, fitted);
        Rectangle2D bounds = parse(fitted).getBounds2D();
        assertEquals(0, bounds.getMinX(), 0.01);
        assertEquals(40, bounds.getMaxX(), 0.01);
        assertEquals(20, bounds.getMaxY(), 0.01);
    }

    @Test
    void leavesAStarAlone() {
        double[][] star = new double[10][];
        for (int index = 0; index < 10; index++) {
            double angle = Math.PI * index / 5;
            double radius = index % 2 == 0 ? 30 : 12;
            star[index] = new double[] {50 + radius * Math.cos(angle), 50 + radius * Math.sin(angle)};
        }
        String traced = polygon(star);

        assertEquals(traced, ShapeFitter.fit(traced));
    }

    @Test
    void keepsARingsHoleAHole() {
        String ring = polygon(ellipse(50, 50, 30, 30, 80, 0.15)) + " " + polygon(reversed(ellipse(50, 50, 18, 18, 60, 0.15)));

        String fitted = ShapeFitter.fit(ring);

        assertEquals(8, count(fitted, 'C'), fitted);
        assertEquals(Math.PI * (900 - 324), area(fitted), Math.PI * (900 - 324) * 0.03);
    }

    @Test
    void fitsEveryPathOfATracedSvg() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"><path fill=\"rgb(1,2,3)\" d=\""
                + polygon(ellipse(50, 50, 20, 20, 64, 0.2)) + "\"/></svg>";

        String fitted = ShapeFitter.fitSvg(svg);

        assertTrue(fitted.contains(" C "), fitted);
        assertTrue(fitted.contains("fill=\"rgb(1,2,3)\""));
    }

    @Test
    void mirrorsTheLeftHalfForVerticalSymmetry() {
        BufferedImage image = new BufferedImage(7, 3, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 1, Color.RED.getRGB());
        image.setRGB(5, 1, Color.BLUE.getRGB());

        BufferedImage mirrored = ImageTracerVectorizer.symmetric(image, TraceOptions.Symmetry.VERTICAL);

        assertEquals(Color.RED.getRGB(), mirrored.getRGB(6, 1));
        assertEquals(0, mirrored.getRGB(5, 1) >>> 24);
        assertEquals(Color.RED.getRGB(), mirrored.getRGB(0, 1));
    }

    private static double[][] ellipse(double cx, double cy, double rx, double ry, int points, double jitter) {
        double[][] result = new double[points][];
        for (int index = 0; index < points; index++) {
            double angle = 2 * Math.PI * index / points;
            double wobble = (index % 3 - 1) * jitter;
            result[index] = new double[] {cx + (rx + wobble) * Math.cos(angle), cy + (ry + wobble) * Math.sin(angle)};
        }
        return result;
    }

    private static double[][] reversed(double[][] points) {
        double[][] result = new double[points.length][];
        for (int index = 0; index < points.length; index++) {
            result[index] = points[points.length - 1 - index];
        }
        return result;
    }

    private static String polygon(double[][] points) {
        StringBuilder data = new StringBuilder();
        for (int index = 0; index < points.length; index++) {
            data.append(index == 0 ? "M " : " L ")
                    .append(String.format(Locale.ROOT, "%.2f %.2f", points[index][0], points[index][1]));
        }
        return data.append(" Z").toString();
    }

    private static long count(String data, char command) {
        return data.chars().filter(character -> character == command).count();
    }

    private static Path2D parse(String data) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        String[] tokens = data.trim().split("\\s+");
        for (int index = 0; index < tokens.length; ) {
            switch (tokens[index++]) {
                case "M" -> path.moveTo(Double.parseDouble(tokens[index++]), Double.parseDouble(tokens[index++]));
                case "L" -> path.lineTo(Double.parseDouble(tokens[index++]), Double.parseDouble(tokens[index++]));
                case "Q" -> path.quadTo(Double.parseDouble(tokens[index++]), Double.parseDouble(tokens[index++]),
                        Double.parseDouble(tokens[index++]), Double.parseDouble(tokens[index++]));
                case "C" -> path.curveTo(Double.parseDouble(tokens[index++]), Double.parseDouble(tokens[index++]),
                        Double.parseDouble(tokens[index++]), Double.parseDouble(tokens[index++]),
                        Double.parseDouble(tokens[index++]), Double.parseDouble(tokens[index++]));
                case "Z" -> path.closePath();
                default -> throw new IllegalArgumentException(data);
            }
        }
        return path;
    }

    /** Area by sampling, honoring the nonzero rule. */
    private static double area(String data) {
        Area shape = new Area(parse(data));
        Rectangle2D bounds = shape.getBounds2D();
        int inside = 0;
        int samples = 0;
        for (double y = bounds.getMinY(); y < bounds.getMaxY(); y += 0.25) {
            for (double x = bounds.getMinX(); x < bounds.getMaxX(); x += 0.25) {
                samples++;
                inside += shape.contains(x, y) ? 1 : 0;
            }
        }
        return bounds.getWidth() * bounds.getHeight() * inside / samples;
    }

    private static double signedArea(String data) {
        double total = 0;
        double[] values = new double[6];
        double lastX = 0;
        double lastY = 0;
        for (PathIterator iterator = parse(data).getPathIterator(null, 0.1); !iterator.isDone(); iterator.next()) {
            int type = iterator.currentSegment(values);
            if (type == PathIterator.SEG_LINETO) {
                total += lastX * values[1] - values[0] * lastY;
            }
            if (type != PathIterator.SEG_CLOSE) {
                lastX = values[0];
                lastY = values[1];
            }
        }
        return total / 2;
    }
}
