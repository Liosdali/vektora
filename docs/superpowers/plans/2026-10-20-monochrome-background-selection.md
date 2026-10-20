# Monochrome Background Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users remove selected connected background regions per PNG so single-color SVGs retain a clean filled silhouette, with the same result in preview and every export.

**Architecture:** Add deterministic connected-region removal over an ARGB copy, retaining the source image unchanged. Keep each icon's pixel seeds in `IconPack`, process them in `IconPackController` before vectorization, and provide a JavaFX source-image picker with a clear/reset action. Make automatic edge-background cleanup connected-component-based as well, so it does not erase detached artwork of a similar color.

**Tech Stack:** Java 22, JavaFX 22, AWT `BufferedImage`, JUnit 5; no new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-20-monochrome-emblem-tracing-design.md`

## Global Constraints

- Preserve the original alpha channel; never make transparent pixels opaque.
- Background selections are per icon and apply identically to preview, individual SVGs, sprites, and ZIP exports.
- Preserve existing source-color, reduced-color, multi-color palette, import, and export behavior unless directly coupled changes are required for correctness.
- Keep the workflow local; add no network service or image-processing dependency.
- Do not claim semantic recognition of named objects; automatic cleanup is conservative and manual connected-region selection is the deterministic fallback.
- Do not add the supplied badge image to the repository.
- Preserve the pre-existing uncommitted worktree changes; do not stage or commit unrelated changes.

## Review Focus

- A transparent or out-of-bounds click must not erase artwork or produce invalid coordinates — test in `BackgroundRegionProcessorTest` and `BackgroundRegionPickerPaneTest`.
- Anti-aliased pixels near the selected region must be removed without expanding the tolerance transitively — test fixed-seed color tolerance in `BackgroundRegionProcessorTest`.
- A disconnected area with the same color as a selected or automatic edge background must remain — test in `BackgroundRegionProcessorTest` and `ImageTracerVectorizerTest`.
- Selections on one imported icon must not affect another icon — test in `IconPackControllerTest`.
- Preview and all export formats must use the same selected-region result — test in `IconPackIntegrationTest`.

---

### Task 1: Implement Connected Background Region Processing

**Files:**
- Create: `src/main/java/org/example/vector/PixelCoordinate.java`
- Create: `src/main/java/org/example/vector/BackgroundRegionProcessor.java`
- Modify: `src/main/java/org/example/vector/ImageTracerVectorizer.java`
- Test: `src/test/java/org/example/vector/BackgroundRegionProcessorTest.java`
- Test: `src/test/java/org/example/vector/ImageTracerVectorizerTest.java`

**Interfaces:**
- Produces `PixelCoordinate(int x, int y)` as an immutable source-image coordinate.
- Produces `BackgroundRegionProcessor.removeSelectedRegions(BufferedImage source, List<PixelCoordinate> seeds) -> BufferedImage`.
- Produces `BackgroundRegionProcessor.removeBorderBackground(BufferedImage source) -> BufferedImage` for the existing automatic cleanup.

- [ ] **Step 1: Write failing processor and vectorizer tests** for removal of a selected enclosed region, preservation of a disconnected region with matching color, handling of multiple seeds, alpha-zero seed no-op, invalid-coordinate rejection, source-image immutability, fixed-seed RGB tolerance of 24, and preservation of a detached same-colored component during automatic edge cleanup.
- [ ] **Step 2: Run the focused tests and confirm they fail** because `PixelCoordinate` and `BackgroundRegionProcessor` do not yet exist.

Run: `mvn -Dtest=BackgroundRegionProcessorTest test`

Expected: FAIL because the processor API is missing and the existing automatic cleanup removes the detached component.

- [ ] **Step 3: Implement immutable coordinates and region removal.** Copy input to ARGB; use 4-neighbor flood fill; compare each candidate pixel against the seed pixel (not the preceding pixel) with per-channel RGB tolerance 24; set only matched nontransparent pixels' alpha to zero. Ignore transparent seeds and reject coordinates outside the image.
- [ ] **Step 4: Update automatic edge cleanup and add its regression test.** Preserve existing conservative checks and dominant-edge-color selection in `ImageTracerVectorizer`, but remove only matching pixels connected to the image boundary. Keep a detached same-colored interior component. Route this cleanup through `BackgroundRegionProcessor.removeBorderBackground`.
- [ ] **Step 5: Run focused tests and confirm they pass.**

Run: `mvn -Dtest=BackgroundRegionProcessorTest,ImageTracerVectorizerTest test`

Expected: PASS; the existing transparent margins, crop, and palette tests remain green.

### Task 2: Store Per-Icon Selections and Apply Them to Vectorization

**Files:**
- Modify: `src/main/java/org/example/model/IconPack.java`
- Modify: `src/main/java/org/example/ui/IconPackController.java`
- Test: `src/test/java/org/example/ui/IconPackControllerTest.java`
- Test: `src/test/java/org/example/IconPackIntegrationTest.java`

**Interfaces:**
- `IconPack` stores and exposes immutable `List<PixelCoordinate>` selections in an `IdentityHashMap<IconAsset, List<PixelCoordinate>>` so distinct record instances cannot share selections by value equality. It provides `addBackgroundRemovalSeed(IconAsset icon, PixelCoordinate seed)`, `backgroundRemovalSeeds(IconAsset icon)`, and `clearBackgroundRemovalSeeds(IconAsset icon)`.
- `IconPackController` provides matching methods and rejects icons not in its package using identity membership, not record equality.
- `IconPackController.createVariants(IconAsset icon)` processes a fresh copy of `icon.image()` with that icon's seeds once, then uses the processed image for every palette variant.

- [ ] **Step 1: Write failing controller and integration tests** proving a seed is retained only for its icon, repeated identical seeds do not duplicate markers, clearing one icon leaves others unchanged, an out-of-package icon is rejected, and a single-color palette applies the same selected-region geometry to preview, individual SVG, sprite, and ZIP outputs. Use two synthetic icons with multiple source colors; assert those colors become the chosen fill, the selected region is transparent, the detached same-colored component remains, and the second icon's geometry is unchanged.
- [ ] **Step 2: Run the focused tests and confirm they fail** because the per-icon selection API is missing.

Run: `mvn -Dtest=IconPackControllerTest,IconPackIntegrationTest test`

Expected: FAIL because per-icon selection methods are missing.

- [ ] **Step 3: Implement per-icon selection storage and controller methods.** Keep source `IconAsset.image()` unchanged. Before each preview/export variant is vectorized, pass the selection-processed image to the existing `SvgVectorizer` interface. The existing one-color custom palette then maps every surviving opaque source color to one selected fill while the transparent class remains transparent.
- [ ] **Step 4: Run the focused tests and confirm they pass.**

Run: `mvn -Dtest=IconPackControllerTest,IconPackIntegrationTest test`

Expected: PASS; existing import, palette, and export-settings controller tests remain green.

### Task 3: Add the Source-Image Region Picker

**Files:**
- Create: `src/main/java/org/example/ui/BackgroundRegionPickerPane.java`
- Modify: `src/main/java/org/example/ui/IconWorkspace.java`
- Test: `src/test/java/org/example/ui/BackgroundRegionPickerPaneTest.java`

**Interfaces:**
- `BackgroundRegionPickerPane.setSource(IconAsset icon, List<PixelCoordinate> seeds, Consumer<PixelCoordinate> onSeedSelected)` displays the selected PNG and existing seed markers.
- `BackgroundRegionPickerPane.sourceCoordinate(double viewX, double viewY, double imageX, double imageY, double displayedWidth, double displayedHeight, int sourceWidth, int sourceHeight) -> Optional<PixelCoordinate>` maps an in-image click to a bounded source pixel; clicks in letterboxed space return `Optional.empty()`.
- `IconWorkspace` offers a discoverable “select background region” action, a return-to-SVG action, and a clear/reset action. Selecting an icon loads only that icon's stored markers; each click adds one seed, updates markers, and refreshes the SVG preview.

- [ ] **Step 1: Write failing coordinate-mapping tests** for aspect-ratio fit, top-left/bottom-right pixel mapping, and clicks in the letterboxed margins.
- [ ] **Step 2: Run the focused tests and confirm they fail** because the picker pane and mapping function do not exist.

Run: `mvn -Dtest=BackgroundRegionPickerPaneTest test`

Expected: FAIL because the new picker API is missing.

- [ ] **Step 3: Implement the picker pane.** Render the source PNG with preserved aspect ratio, use source-pixel coordinates for clicks, visually mark selected seeds, and provide concise instructions that clicking removes the connected similar-color region.
- [ ] **Step 4: Wire picker actions into `IconWorkspace`.** Toggle between the existing SVG preview and source picker; add and clear seeds through `IconPackController`; refresh the picker and SVG preview after changes. Keep JavaFX updates on the JavaFX application thread.
- [ ] **Step 5: Run picker and existing UI tests.**

Run: `mvn -Dtest=BackgroundRegionPickerPaneTest,IconPreviewPaneTest,IconPackControllerTest test`

Expected: PASS; the SVG preview remains reachable and valid.

### Task 4: Document the Workflow and Run Full Regression Verification

**Files:**
- Modify: `README.md`

**Interfaces:**
- Documents the background picker and per-icon selection behavior from Tasks 2 and 3.

- [ ] **Step 1: Read and preserve the existing uncommitted README edits, then add concise usage documentation** for selecting an icon, clicking connected background regions, clearing selections, and the limitation that the app does not semantically recognize subjects.
- [ ] **Step 2: Run the complete verification suite.**

Run: `mvn clean verify`

Expected: BUILD SUCCESS and all tests pass, including existing source-color, reduced-color, multi-palette, PNG import, and export tests.
