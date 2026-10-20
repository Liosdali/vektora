package org.example.export;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.Shape;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts the absolute M/L/Q/C/Z path data that ImageTracer writes to and from Java2D shapes. */
final class SvgPathGeometry {
    private static final Pattern TOKEN = Pattern.compile(
            "[MLQCZmlqczHhVvAaSsTt]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

    private SvgPathGeometry() {
    }

    /** Parses path data, or returns empty when it uses commands this class does not read. */
    static Optional<Path2D> parse(String data) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        Matcher tokens = TOKEN.matcher(data);
        char command = 0;
        double[] values = new double[6];
        int count = 0;
        boolean hasSegments = false;
        while (tokens.find()) {
            String token = tokens.group();
            char first = token.charAt(0);
            if (Character.isLetter(first)) {
                if ("MLQCZ".indexOf(first) < 0) {
                    return Optional.empty();
                }
                if (count != 0) {
                    return Optional.empty();
                }
                command = first;
                if (command == 'Z') {
                    path.closePath();
                }
                continue;
            }
            values[count++] = Double.parseDouble(token);
            int needed = switch (command) {
                case 'M', 'L' -> 2;
                case 'Q' -> 4;
                case 'C' -> 6;
                default -> -1;
            };
            if (needed < 0) {
                return Optional.empty();
            }
            if (count < needed) {
                continue;
            }
            switch (command) {
                case 'M' -> {
                    path.moveTo(values[0], values[1]);
                    command = 'L';
                }
                case 'L' -> path.lineTo(values[0], values[1]);
                case 'Q' -> path.quadTo(values[0], values[1], values[2], values[3]);
                default -> path.curveTo(values[0], values[1], values[2], values[3], values[4], values[5]);
            }
            hasSegments = true;
            count = 0;
        }
        return hasSegments && count == 0 ? Optional.of(path) : Optional.empty();
    }

    static String format(Shape shape) {
        StringBuilder data = new StringBuilder();
        double[] values = new double[6];
        for (PathIterator iterator = shape.getPathIterator(null); !iterator.isDone(); iterator.next()) {
            switch (iterator.currentSegment(values)) {
                case PathIterator.SEG_MOVETO -> append(data, "M", values, 2);
                case PathIterator.SEG_LINETO -> append(data, "L", values, 2);
                case PathIterator.SEG_QUADTO -> append(data, "Q", values, 4);
                case PathIterator.SEG_CUBICTO -> append(data, "C", values, 6);
                case PathIterator.SEG_CLOSE -> data.append(data.isEmpty() ? "Z" : " Z");
                default -> throw new IllegalStateException("Unknown path segment");
            }
        }
        return data.toString();
    }

    static String fillRule(Shape shape) {
        return shape.getPathIterator(null).getWindingRule() == PathIterator.WIND_EVEN_ODD ? "evenodd" : "nonzero";
    }

    static boolean intersects(Rectangle2D first, Rectangle2D second) {
        // Strokes reach half a unit past the outline, so touching boxes count as overlapping.
        return first.getMinX() <= second.getMaxX() + 1 && second.getMinX() <= first.getMaxX() + 1
                && first.getMinY() <= second.getMaxY() + 1 && second.getMinY() <= first.getMaxY() + 1;
    }

    private static void append(StringBuilder data, String command, double[] values, int count) {
        if (!data.isEmpty()) {
            data.append(' ');
        }
        data.append(command);
        for (int index = 0; index < count; index++) {
            data.append(' ').append(number(values[index]));
        }
    }

    private static String number(double value) {
        double rounded = Math.round(value * 100) / 100.0;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        return String.format(Locale.ROOT, "%.2f", rounded).replaceAll("0+$", "");
    }
}
