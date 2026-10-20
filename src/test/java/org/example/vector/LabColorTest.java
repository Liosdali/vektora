package org.example.vector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LabColorTest {
    @Test
    void measuresTheCie76DistanceBetweenSrgbColors() {
        assertEquals(100, LabColor.deltaE(0xffffff, 0x000000), 0.05);
        assertEquals(8.06, LabColor.deltaE(0x646464, 0x787878), 0.05);
        assertEquals(0, LabColor.deltaE(0x1e2a44, 0x1e2a44), 1e-9);
    }

    @Test
    void weighsChannelsByHowVisibleTheirChangeIs() {
        // The same 30-step change: barely visible in red on this blue, clearly visible in blue.
        assertEquals(5.0, LabColor.deltaE(0x1e28a0, 0x3c28a0), 0.1);
        assertEquals(16.3, LabColor.deltaE(0x1e28a0, 0x1e28be), 0.1);
    }
}
