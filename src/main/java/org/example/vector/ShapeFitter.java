package org.example.vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans up ImageTracer outlines: a closed outline that follows an axis-aligned circle or ellipse
 * becomes four exact Bézier arcs, and runs of straight segments that wander less than a third of a
 * pixel from a line become that line. Outlines keep their direction, so holes stay holes.
 */
public final class ShapeFitter {
    private static final double STRAIGHT_TOLERANCE = 0.35;
    private static final int MIN_ELLIPSE_POINTS = 12;
    private static final double MIN_ELLIPSE_RADIUS = 2.5;
    private static final double MAX_ANGLE_GAP = Math.PI / 3;
    // Control point distance that makes four cubic Béziers trace a quarter ellipse each.
    private static final double KAPPA = 0.5522847498;
    private static final Pattern PATH_DATA = Pattern.compile("(<path\\b[^>]*?\\sd=\")([^\"]*)(\")");
    private static final Pattern TOKEN = Pattern.compile("[MLQZ]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

    private ShapeFitter() {
    }

    /** Fits the outlines of every path in a traced SVG. */
    public static String fitSvg(String svg) {
        Matcher matcher = PATH_DATA.matcher(svg);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(matcher.group(1) + fit(matcher.group(2)) + matcher.group(3)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Fits one path's outlines; returns the data unchanged when nothing could be simplified. */
    public static String fit(String data) {
        List<Outline> outlines = parse(data);
        if (outlines == null) {
            return data;
        }
        boolean changed = false;
        List<String> fitted = new ArrayList<>(outlines.size());
        for (Outline outline : outlines) {
            String ellipse = outline.closed ? ellipse(outline) : null;
            if (ellipse != null) {
                fitted.add(ellipse);
                changed = true;
                continue;
            }
            Outline straight = outline.closed ? straightened(outline) : outline;
            changed |= straight != outline;
            fitted.add(straight.format());
        }
        return changed ? String.join(" ", fitted) : data;
    }

    /** A subpath: its corners in order and, per corner, the segment leaving it. */
    private static final class Outline {
        private final List<double[]> points = new ArrayList<>();
        /** {@code null} for a straight segment, else the quadratic control point. */
        private final List<double[]> controls = new ArrayList<>();
        private boolean closed;

        private String format() {
            StringBuilder data = new StringBuilder("M ").append(xy(points.getFirst()));
            int segments = closed ? points.size() : points.size() - 1;
            for (int index = 0; index < segments; index++) {
                double[] next = points.get((index + 1) % points.size());
                double[] control = controls.get(index);
                if (control == null) {
                    if (closed && index == segments - 1) {
                        break;
                    }
                    data.append(" L ").append(xy(next));
                } else {
                    data.append(" Q ").append(xy(control)).append(' ').append(xy(next));
                }
            }
            return closed ? data.append(" Z").toString() : data.toString();
        }
    }

    private static List<Outline> parse(String data) {
        List<Outline> outlines = new ArrayList<>();
        Matcher tokens = TOKEN.matcher(data);
        List<String> values = new ArrayList<>();
        while (tokens.find()) {
            values.add(tokens.group());
        }
        Outline current = null;
        int index = 0;
        try {
            while (index < values.size()) {
                String command = values.get(index++);
                switch (command) {
                    case "M" -> {
                        current = new Outline();
                        outlines.add(current);
                        current.points.add(point(values, index));
                        index += 2;
                    }
                    case "L" -> {
                        current.controls.add(null);
                        current.points.add(point(values, index));
                        index += 2;
                    }
                    case "Q" -> {
                        current.controls.add(point(values, index));
                        current.points.add(point(values, index + 2));
                        index += 4;
                    }
                    case "Z" -> closeOutline(current);
                    default -> {
                        return null;
                    }
                }
            }
        } catch (RuntimeException exception) {
            return null;
        }
        for (Outline outline : outlines) {
            if (!outline.closed) {
                outline.controls.add(null);
            }
        }
        return outlines.isEmpty() ? null : outlines;
    }

    /** Closing returns to the start: a final corner that already sits there is dropped. */
    private static void closeOutline(Outline outline) {
        outline.closed = true;
        double[] first = outline.points.getFirst();
        double[] last = outline.points.getLast();
        if (outline.points.size() > 1 && first[0] == last[0] && first[1] == last[1]) {
            // Its incoming segment now closes the loop back to the start.
            outline.points.removeLast();
        } else {
            outline.controls.add(null);
        }
    }

    private static double[] point(List<String> values, int index) {
        return new double[] {Double.parseDouble(values.get(index)), Double.parseDouble(values.get(index + 1))};
    }

    /** The outline as an ellipse, or {@code null} when it does not follow one closely enough. */
    private static String ellipse(Outline outline) {
        List<double[]> samples = new ArrayList<>();
        for (int index = 0; index < outline.points.size(); index++) {
            double[] start = outline.points.get(index);
            samples.add(start);
            double[] control = outline.controls.get(index);
            if (control != null) {
                double[] end = outline.points.get((index + 1) % outline.points.size());
                samples.add(new double[] {
                        0.25 * start[0] + 0.5 * control[0] + 0.25 * end[0],
                        0.25 * start[1] + 0.5 * control[1] + 0.25 * end[1]});
            }
        }
        if (samples.size() < MIN_ELLIPSE_POINTS) {
            return null;
        }
        double meanX = samples.stream().mapToDouble(p -> p[0]).average().orElse(0);
        double meanY = samples.stream().mapToDouble(p -> p[1]).average().orElse(0);
        // Least squares for A·u² + B·v² + C·u + D·v = 1 around the mean.
        double[][] normal = new double[4][5];
        for (double[] sample : samples) {
            double u = sample[0] - meanX;
            double v = sample[1] - meanY;
            double[] row = {u * u, v * v, u, v};
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    normal[i][j] += row[i] * row[j];
                }
                normal[i][4] += row[i];
            }
        }
        double[] solution = solve(normal);
        if (solution == null || solution[0] <= 0 || solution[1] <= 0) {
            return null;
        }
        double centerU = -solution[2] / (2 * solution[0]);
        double centerV = -solution[3] / (2 * solution[1]);
        double scale = 1 + solution[0] * centerU * centerU + solution[1] * centerV * centerV;
        if (scale <= 0) {
            return null;
        }
        double radiusX = Math.sqrt(scale / solution[0]);
        double radiusY = Math.sqrt(scale / solution[1]);
        double centerX = meanX + centerU;
        double centerY = meanY + centerV;
        double smaller = Math.min(radiusX, radiusY);
        if (smaller < MIN_ELLIPSE_RADIUS) {
            return null;
        }

        double tolerance = Math.max(0.75, 0.04 * smaller);
        List<Double> angles = new ArrayList<>();
        for (double[] sample : samples) {
            double dx = sample[0] - centerX;
            double dy = sample[1] - centerY;
            double normalized = Math.hypot(dx / radiusX, dy / radiusY);
            if (normalized == 0 || Math.abs(normalized - 1) * Math.hypot(dx, dy) / normalized > tolerance) {
                return null;
            }
            angles.add(Math.atan2(dy / radiusY, dx / radiusX));
        }
        angles.sort(Double::compare);
        double largestGap = 2 * Math.PI - (angles.getLast() - angles.getFirst());
        for (int index = 1; index < angles.size(); index++) {
            largestGap = Math.max(largestGap, angles.get(index) - angles.get(index - 1));
        }
        if (largestGap > MAX_ANGLE_GAP) {
            return null;
        }

        double[] center = {centerX, centerY};
        double[][] quadrants = {
                {centerX + radiusX, centerY}, {centerX, centerY + radiusY},
                {centerX - radiusX, centerY}, {centerX, centerY - radiusY}};
        if (Math.signum(signedArea(samples)) != Math.signum(signedArea(List.of(quadrants)))) {
            quadrants = new double[][] {quadrants[0], quadrants[3], quadrants[2], quadrants[1]};
        }
        StringBuilder data = new StringBuilder("M ").append(xy(quadrants[0]));
        for (int index = 0; index < 4; index++) {
            double[] from = quadrants[index];
            double[] to = quadrants[(index + 1) % 4];
            data.append(" C ")
                    .append(xy(new double[] {from[0] + KAPPA * (to[0] - center[0]), from[1] + KAPPA * (to[1] - center[1])}))
                    .append(' ')
                    .append(xy(new double[] {to[0] + KAPPA * (from[0] - center[0]), to[1] + KAPPA * (from[1] - center[1])}))
                    .append(' ').append(xy(to));
        }
        return data.append(" Z").toString();
    }

    /**
     * Drops corners between two straight segments that lie within the tolerance of the line
     * through their neighbors (Douglas–Peucker between fixed corners).
     */
    private static Outline straightened(Outline outline) {
        int count = outline.points.size();
        if (count < 4) {
            return outline;
        }
        boolean[] anchor = new boolean[count];
        int anchors = 0;
        for (int index = 0; index < count; index++) {
            boolean straightIn = outline.controls.get((index - 1 + count) % count) == null;
            boolean straightOut = outline.controls.get(index) == null;
            anchor[index] = !straightIn || !straightOut;
            anchors += anchor[index] ? 1 : 0;
        }
        if (anchors == 0) {
            anchor[0] = true;
            anchor[farthestFrom(outline.points, 0)] = true;
        } else if (anchors == 1) {
            int only = 0;
            while (!anchor[only]) {
                only++;
            }
            anchor[farthestFrom(outline.points, only)] = true;
        }

        boolean[] keep = anchor.clone();
        int start = 0;
        while (!anchor[start]) {
            start++;
        }
        int from = start;
        do {
            int to = (from + 1) % count;
            while (!anchor[to]) {
                to = (to + 1) % count;
            }
            simplify(outline.points, from, to, keep);
            from = to;
        } while (from != start);

        int kept = 0;
        for (boolean value : keep) {
            kept += value ? 1 : 0;
        }
        if (kept == count || kept < 3) {
            return outline;
        }
        Outline result = new Outline();
        result.closed = true;
        for (int index = 0; index < count; index++) {
            if (keep[index]) {
                result.points.add(outline.points.get(index));
                result.controls.add(outline.controls.get(index));
            }
        }
        return result;
    }

    /** Douglas–Peucker over the cyclic run from {@code first} to {@code last}, both kept. */
    private static void simplify(List<double[]> points, int first, int last, boolean[] keep) {
        int count = points.size();
        int span = (last - first + count) % count;
        if (span < 2) {
            return;
        }
        double[] a = points.get(first);
        double[] b = points.get(last);
        int farthest = -1;
        double distance = 0;
        for (int step = 1; step < span; step++) {
            int index = (first + step) % count;
            double value = distanceToSegment(points.get(index), a, b);
            if (value > distance) {
                distance = value;
                farthest = index;
            }
        }
        if (distance > STRAIGHT_TOLERANCE) {
            keep[farthest] = true;
            simplify(points, first, farthest, keep);
            simplify(points, farthest, last, keep);
        }
    }

    private static int farthestFrom(List<double[]> points, int origin) {
        int farthest = origin;
        double distance = -1;
        for (int index = 0; index < points.size(); index++) {
            double value = Math.hypot(points.get(index)[0] - points.get(origin)[0],
                    points.get(index)[1] - points.get(origin)[1]);
            if (value > distance) {
                distance = value;
                farthest = index;
            }
        }
        return farthest;
    }

    private static double distanceToSegment(double[] point, double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double length = dx * dx + dy * dy;
        double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((point[0] - a[0]) * dx + (point[1] - a[1]) * dy) / length));
        return Math.hypot(point[0] - (a[0] + t * dx), point[1] - (a[1] + t * dy));
    }

    private static double signedArea(List<double[]> points) {
        double total = 0;
        for (int index = 0; index < points.size(); index++) {
            double[] current = points.get(index);
            double[] next = points.get((index + 1) % points.size());
            total += current[0] * next[1] - next[0] * current[1];
        }
        return total;
    }

    /** Gaussian elimination with partial pivoting on an n × (n+1) augmented matrix. */
    private static double[] solve(double[][] matrix) {
        int size = matrix.length;
        for (int column = 0; column < size; column++) {
            int pivot = column;
            for (int row = column + 1; row < size; row++) {
                if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) {
                    pivot = row;
                }
            }
            if (Math.abs(matrix[pivot][column]) < 1e-12) {
                return null;
            }
            double[] swap = matrix[pivot];
            matrix[pivot] = matrix[column];
            matrix[column] = swap;
            for (int row = 0; row < size; row++) {
                if (row != column) {
                    double factor = matrix[row][column] / matrix[column][column];
                    for (int k = column; k <= size; k++) {
                        matrix[row][k] -= factor * matrix[column][k];
                    }
                }
            }
        }
        double[] solution = new double[size];
        for (int row = 0; row < size; row++) {
            solution[row] = matrix[row][size] / matrix[row][row];
        }
        return solution;
    }

    private static String xy(double[] point) {
        return number(point[0]) + " " + number(point[1]);
    }

    private static String number(double value) {
        double rounded = Math.round(value * 100) / 100.0;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        return String.format(Locale.ROOT, "%.2f", rounded).replaceAll("0+$", "");
    }
}
