package org.example.segmentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColorMixtureModelTest {
    @Test
    void separatesTwoDistinctColorsIntoTheirOwnComponents() {
        int[] samples = new int[400];
        for (int index = 0; index < samples.length; index++) {
            int jitter = index % 9 - 4;
            samples[index] = index % 2 == 0
                    ? rgb(200 + jitter, 30 - jitter, 40 + jitter)
                    : rgb(20 + jitter, 60 + jitter, 190 - jitter);
        }

        ColorMixtureModel model = ColorMixtureModel.fit(samples, samples.length);

        assertNotEquals(model.mostLikelyComponent(rgb(200, 30, 40)), model.mostLikelyComponent(rgb(20, 60, 190)));
        assertTrue(model.probability(rgb(201, 31, 39)) > 1e6 * model.probability(rgb(110, 200, 110)));
    }

    @Test
    void aModelOfRedsExplainsRedFarBetterThanBlue() {
        int[] reds = new int[200];
        for (int index = 0; index < reds.length; index++) {
            reds[index] = rgb(180 + index % 40, 20 + index % 13, 30 + index % 7);
        }

        ColorMixtureModel model = ColorMixtureModel.fit(reds, reds.length);

        assertTrue(model.probability(rgb(195, 25, 33)) > 1e6 * model.probability(rgb(30, 40, 200)));
        assertEquals(model.probability(rgb(195, 25, 33)),
                ColorMixtureModel.fit(reds, reds.length).probability(rgb(195, 25, 33)));
    }

    private static int rgb(int red, int green, int blue) {
        return red << 16 | green << 8 | blue;
    }
}
