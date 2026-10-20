# Monochrome Emblem Tracing

## Intent

When a user chooses one color for a PNG emblem, produce a recognizable monochrome vector rather than filling the entire opaque badge as one undifferentiated shape. Preserve the emblem's main subject and defining contours while reducing dense interior detail.

## User context

The supplied example is a circular squadron badge with transparency outside the badge, a colored checkerboard field, a bird and sword, a circular border, and lettering. Mapping every opaque pixel to a single palette color makes the whole badge read as a solid disk. The user wants the bird and sword to read as the filled main silhouette, with the outer ring and primary wing boundaries retained, while removing small feather divisions, the inner grid, and lettering contours.

The behavior must also be useful for other PNG icon types, not only this badge. The user approved automatic background cleanup plus a way to click a background region when automatic detection is insufficient.

## Goals

- Make single-color custom-palette output legible as a monochrome emblem with transparent background where selected.
- Retain the central foreground silhouette and a small number of defining high-contrast contours; suppress busy micro-detail.
- Continue automatic removal of transparent margins and clearly detectable flat edge backgrounds.
- Let users click one or more connected background regions in the selected source image to remove enclosed backgrounds without deleting disconnected regions of the same color, such as a separated outer ring.
- Apply the same per-icon background removals and tracing decisions to preview, individual SVGs, sprites, and ZIP exports.
- Keep SVG output path-based and allow the user-selected palette color to style the resulting artwork.

## User-approved decisions

- Use the filled-emblem direction: primary subject/sword as filled shapes, with fewer internal lines than the first filled mockup.
- Preserve the outer badge ring and main wing boundaries; remove small feather lines, inner grid, and lettering contours where identified as background/detail.
- Optimize for different types of PNGs rather than hard-coding the supplied badge.
- Automatically clean up backgrounds where possible and provide click-to-remove connected background regions as a user-controlled fallback.

## Behavior and constraints

- Background-removal choices are per icon; a seed selected on one source image must not affect other icons in a batch.
- A click identifies a source-image pixel. Remove the connected region of sufficiently similar pixels containing that seed, preserving other disconnected areas of similar color. Support multiple seeds and a clear/reset action.
- Preserve original alpha. Never make a transparent pixel opaque merely because the palette has one color.
- The automatic remover should avoid deleting disconnected same-colored artwork. Keep its detection conservative; manual region selection is the deterministic fallback.
- Apply monochrome simplification only in the single-color custom-palette case. Preserve existing source-color and color-reduction modes and multi-color palette behavior unless a directly coupled change is required for correctness.
- Preview and every export format must use the same processed source and settings.
- Do not introduce a network service or image-processing dependency; retain the local Java/JavaFX workflow.

## Non-goals

- Semantic AI recognition of named objects such as birds, swords, text, or heraldry.
- Guaranteeing clean automatic separation for photographs, gradients, or every enclosed background. Such cases remain user-correctable by selecting connected regions.
- Generating raster output.
- Rebuilding the application layout beyond the controls needed to inspect a source image and remove background regions.

## Reference repository findings

- `visioncortex/vtracer` separates segmentation from path fitting. Its binary frontend selects dark foreground pixels using either a fixed grayscale threshold or Bradley–Roth adaptive local thresholding, then forms connected components and filters small clusters. Its color frontend instead hierarchically clusters similar colors; its speckle threshold participates in deciding whether small patches merge into neighbors or survive as layers. These are useful mechanisms for thresholding, connected-region handling, and reducing detail, but none identify a bird, sword, lettering, or which region is the intended emblem.
- VTracer's `bw` preset selects binary clustering; it does not mean “make the source image a single-color palette.” Its default fixed threshold treats pixels darker than 128 as foreground, so a dark badge and dark details in the checkerboard can both become foreground. Adaptive thresholding addresses uneven lighting, not semantic foreground/background separation.
- VTracer's contour fitters and simplification controls (`filter_speckle`, corner/length thresholds, spline/polygon fitting, and curve simplification) control geometric detail after or during segmentation. They cannot recover the intended subject when the entire opaque badge has already been assigned to one foreground class.
- `sserada/image-to-svg` calls VTracer directly. Its quality presets configure color mode, speckle filtering, color precision, layer difference, and path-fitting parameters; the wrapper validates and forwards these settings rather than adding a separate subject-recognition or background-selection algorithm.
- Both projects therefore support the chosen direction as implementation references for deterministic segmentation and simpler paths, not as a turnkey fix. The essential missing behavior here is user-directed connected-region removal (with alpha preserved), followed by restrained monochrome silhouette tracing. Automatic threshold/cleanup can assist, but must not be presented as semantic recognition.
- References: [VTracer binary frontend](https://github.com/visioncortex/vtracer/blob/master/crates/vtracer/src/frontend/binary.rs), [VTracer color frontend](https://github.com/visioncortex/vtracer/blob/master/crates/vtracer/src/frontend/color_cluster.rs), [VTracer configuration and presets](https://github.com/visioncortex/vtracer/blob/master/crates/vtracer/src/config.rs), [image-to-svg converter](https://github.com/sserada/image-to-svg/blob/main/backend/modules/converter.py), [image-to-svg presets](https://github.com/sserada/image-to-svg/blob/main/backend/modules/presets.py).

## Acceptance criteria

1. A synthetic opaque badge with a colored enclosed field and a distinct foreground no longer becomes one solid filled canvas in one-color mode after the field is selected for removal.
2. Clicking/removing one connected background region leaves a disconnected same-colored region intact.
3. Transparent pixels remain transparent with a one-color palette.
4. A one-color trace keeps the principal silhouette and selected major contours but omits selected background areas and does not reproduce every small source-color boundary as a line.
5. Background selections are isolated per icon and the selected icon's preview matches its individual SVG, sprite, and ZIP output.
6. Existing source-color, reduced-color, multi-color palette, import, and export tests continue to pass.

## Implementation choices left to the builder

- Choose a deterministic local contour/simplification technique that meets the visual criteria without pretending to infer object semantics.
- Choose JavaFX controls and source-image preview interaction that make region picking discoverable and accessible.
- Choose a bounded per-icon tolerance strategy for anti-aliased edges, with conservative defaults and clear/reset controls.
- Add focused unit and integration fixtures using synthetic artwork; do not add the user's supplied badge image to the repository.
