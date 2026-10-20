package org.example.export;

public enum ExportFormat {
    INDIVIDUAL_SVGS,
    SPRITE_SVG,
    ZIP,
    /** One SVG per icon and color layer, each holding only what shows in that color. */
    LAYER_SVGS,
    /** Transparent PNGs at one, two and four times the canvas size. */
    PNG,
    /** Square PNGs from 16 to 512 pixels plus a 16/32/48 pixel favicon.ico. */
    ICON_SET
}
