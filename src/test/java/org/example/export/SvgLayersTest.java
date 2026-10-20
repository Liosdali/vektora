package org.example.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SvgLayersTest {
    private static final String SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
              <path fill="rgb(0,0,0)" opacity="0.0" d="M 0 0 L 10 0 Z"/>
              <path fill="rgb(30,42,68)" opacity="1.0" d="M 1 1 L 2 2 Z"/>
              <path fill="#F3EBD8" d="M 3 3 L 4 4 Z"/>
              <path fill="rgb(30, 42, 68)" d="M 5 5 L 6 6 Z"/>
            </svg>
            """;

    @Test
    void listsVisibleFillColorsInDrawingOrderWithTheirPathCounts() throws Exception {
        assertEquals(List.of(new SvgLayer("#1e2a44", 2), new SvgLayer("#f3ebd8", 1)),
                SvgLayers.layers(SVG));
    }

    @Test
    void marksOnlyPathsOfTheRemovedColorsAsTransparent() throws Exception {
        String result = SvgLayers.withColorsRemoved(SVG, Set.of("#1e2a44"));

        assertEquals(List.of(new SvgLayer("#f3ebd8", 1)), SvgLayers.layers(result));
        assertTrue(result.contains("M 1 1"));
        assertFalse(result.contains("opacity=\"1.0\""));
    }

    @Test
    void returnsTheSourceUnchangedWhenNothingIsHidden() throws Exception {
        assertSame(SVG, SvgLayers.withColorsRemoved(SVG, Set.of()));
    }
}
