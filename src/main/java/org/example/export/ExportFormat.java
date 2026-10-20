package org.example.export;

public enum ExportFormat {
    INDIVIDUAL_SVGS,
    SPRITE_SVG,
    ZIP,
    /** One SVG per icon and color layer, each holding only what shows in that color. */
    LAYER_SVGS
}
