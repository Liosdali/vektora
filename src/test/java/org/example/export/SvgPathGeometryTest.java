package org.example.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import org.junit.jupiter.api.Test;

class SvgPathGeometryTest {
    @Test
    void readsAndWritesImageTracerPathData() {
        String data = "M 1.5 1 L 9 1.5 Q 9.5 5 9 9 L 1 8.5 Z";

        assertEquals(data, SvgPathGeometry.format(SvgPathGeometry.parse(data).orElseThrow()));
    }

    @Test
    void subtractingAnInnerShapeLeavesAHole() {
        Area square = new Area(SvgPathGeometry.parse("M 0 0 L 10 0 L 10 10 L 0 10 Z").orElseThrow());
        square.subtract(new Area(SvgPathGeometry.parse("M 3 3 L 7 3 L 7 7 L 3 7 Z").orElseThrow()));

        assertTrue(square.contains(1, 1));
        assertTrue(!square.contains(5, 5));
        assertEquals(new Rectangle2D.Double(0, 0, 10, 10), square.getBounds2D());
    }

    @Test
    void refusesPathDataItCannotReadExactly() {
        assertTrue(SvgPathGeometry.parse("m 1 1 l 2 2 z").isEmpty());
        assertTrue(SvgPathGeometry.parse("M 0 0 A 5 5 0 0 1 10 10").isEmpty());
        assertTrue(SvgPathGeometry.parse("M 0 0 L 5").isEmpty());
        assertTrue(SvgPathGeometry.parse("").isEmpty());
    }
}
