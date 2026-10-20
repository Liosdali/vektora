package org.example.model;

import org.example.export.SvgExportSettings;
import org.example.vector.BackgroundSelection;
import org.example.vector.EraseStroke;
import org.example.vector.PixelCoordinate;
import org.example.vector.RegionSeed;
import org.example.vector.SubjectMask;
import org.example.vector.TraceOptions;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.UnaryOperator;

public final class IconPack {
    private static final int MAX_UNDO_STEPS = 100;

    private final List<IconAsset> icons = new ArrayList<>();
    private final Map<IconAsset, BackgroundSelection> backgroundSelections = new IdentityHashMap<>();
    private final Map<IconAsset, Deque<BackgroundSelection>> backgroundHistory = new IdentityHashMap<>();
    private final Map<IconAsset, Set<String>> hiddenLayerColors = new IdentityHashMap<>();
    private final Map<IconAsset, SubjectMask> subjectMasks = new IdentityHashMap<>();
    private final Map<IconAsset, Map<String, String>> layerColors = new IdentityHashMap<>();
    private final Map<IconAsset, Rectangle> subjectFrames = new IdentityHashMap<>();
    private TraceOptions traceOptions = TraceOptions.recommended();
    private SvgExportSettings exportSettings = new SvgExportSettings(128, 128, 1);
    private List<List<Color>> palettes = List.of();

    public List<IconAsset> icons() {
        return List.copyOf(icons);
    }

    public TraceOptions traceOptions() {
        return traceOptions;
    }

    public SvgExportSettings exportSettings() {
        return exportSettings;
    }

    public List<List<Color>> palettes() {
        return palettes;
    }

    public void addIcon(IconAsset icon) {
        icons.add(icon);
    }

    /** Forgets every icon, edit and setting, as a new package would start. */
    public synchronized void clear() {
        icons.clear();
        backgroundSelections.clear();
        backgroundHistory.clear();
        hiddenLayerColors.clear();
        subjectMasks.clear();
        layerColors.clear();
        subjectFrames.clear();
        traceOptions = TraceOptions.recommended();
        exportSettings = new SvgExportSettings(128, 128, 1);
        palettes = List.of();
    }

    /** Puts back a saved selection without recording an undo step. */
    public synchronized void restoreBackgroundSelection(IconAsset icon, BackgroundSelection selection) {
        backgroundSelections.put(icon, selection);
        backgroundHistory.remove(icon);
    }

    public synchronized void addBackgroundRemovalSeed(IconAsset icon, PixelCoordinate seed) {
        addBackgroundRegion(icon, RegionSeed.at(seed));
    }

    public synchronized void addBackgroundRegion(IconAsset icon, RegionSeed region) {
        if (!backgroundSelection(icon).regions().contains(region)) {
            editBackground(icon, selection -> selection.withSeed(region));
        }
    }

    public synchronized List<PixelCoordinate> backgroundRemovalSeeds(IconAsset icon) {
        return backgroundSelection(icon).seeds();
    }

    public synchronized void clearBackgroundRemovalSeeds(IconAsset icon) {
        if (backgroundSelection(icon).hasEdits()) {
            editBackground(icon, BackgroundSelection::withoutEdits);
        }
    }

    public synchronized void addEraseStroke(IconAsset icon, EraseStroke stroke) {
        editBackground(icon, selection -> selection.withStroke(stroke));
    }

    public synchronized List<EraseStroke> eraseStrokes(IconAsset icon) {
        return backgroundSelection(icon).strokes();
    }

    public synchronized void addProtectStroke(IconAsset icon, EraseStroke stroke) {
        editBackground(icon, selection -> selection.withProtectStroke(stroke));
    }

    public synchronized void setBackgroundTolerance(IconAsset icon, int tolerance) {
        backgroundSelections.put(icon, backgroundSelection(icon).withTolerance(tolerance));
    }

    public synchronized void setDefringe(IconAsset icon, boolean defringe) {
        backgroundSelections.put(icon, backgroundSelection(icon).withDefringe(defringe));
    }

    public synchronized boolean canUndoBackgroundEdit(IconAsset icon) {
        Deque<BackgroundSelection> history = backgroundHistory.get(icon);
        return history != null && !history.isEmpty();
    }

    public synchronized boolean undoBackgroundEdit(IconAsset icon) {
        Deque<BackgroundSelection> history = backgroundHistory.get(icon);
        if (history == null || history.isEmpty()) {
            return false;
        }
        BackgroundSelection current = backgroundSelection(icon);
        BackgroundSelection previous = history.pop();
        // Undo restores the removal edits; tolerance and defringe stay as the user last set them.
        backgroundSelections.put(icon, new BackgroundSelection(previous.regions(), previous.strokes(),
                previous.protectStrokes(), current.tolerance(), current.defringe(), previous.keepSubjectOnly()));
        return true;
    }

    public synchronized void setKeepSubjectOnly(IconAsset icon, boolean keep) {
        if (backgroundSelection(icon).keepSubjectOnly() != keep) {
            editBackground(icon, selection -> selection.withKeepSubjectOnly(keep));
        }
    }

    public synchronized void setSubjectMask(IconAsset icon, SubjectMask mask) {
        subjectMasks.put(icon, mask);
    }

    public synchronized SubjectMask subjectMask(IconAsset icon) {
        return subjectMasks.get(icon);
    }

    /** The area the user framed around the subject, or {@code null} to let the segmenter guess. */
    public synchronized void setSubjectFrame(IconAsset icon, Rectangle frame) {
        if (frame == null) {
            subjectFrames.remove(icon);
        } else {
            subjectFrames.put(icon, new Rectangle(frame));
        }
    }

    public synchronized Rectangle subjectFrame(IconAsset icon) {
        Rectangle frame = subjectFrames.get(icon);
        return frame == null ? null : new Rectangle(frame);
    }

    public synchronized BackgroundSelection backgroundSelection(IconAsset icon) {
        return backgroundSelections.getOrDefault(icon, BackgroundSelection.EMPTY);
    }

    public synchronized void setLayerHidden(IconAsset icon, String color, boolean hidden) {
        Set<String> colors = new LinkedHashSet<>(hiddenLayerColors(icon));
        if (hidden) {
            colors.add(color);
        } else {
            colors.remove(color);
        }
        hiddenLayerColors.put(icon, Set.copyOf(colors));
    }

    /** Repaints the layer traced as {@code color}; a {@code null} replacement restores it. */
    public synchronized void setLayerColor(IconAsset icon, String color, String replacement) {
        Map<String, String> colors = new LinkedHashMap<>(layerColors(icon));
        if (replacement == null || replacement.equals(color)) {
            colors.remove(color);
        } else {
            colors.put(color, replacement);
        }
        layerColors.put(icon, Map.copyOf(colors));
    }

    public synchronized Map<String, String> layerColors(IconAsset icon) {
        return layerColors.getOrDefault(icon, Map.of());
    }

    public synchronized Set<String> hiddenLayerColors(IconAsset icon) {
        return hiddenLayerColors.getOrDefault(icon, Set.of());
    }

    public void setTraceOptions(TraceOptions traceOptions) {
        this.traceOptions = traceOptions;
    }

    public void setExportSettings(SvgExportSettings exportSettings) {
        this.exportSettings = exportSettings;
    }

    public void setPalettes(List<List<Color>> palettes) {
        this.palettes = palettes.stream().map(List::copyOf).toList();
    }

    private void editBackground(IconAsset icon, UnaryOperator<BackgroundSelection> edit) {
        BackgroundSelection current = backgroundSelection(icon);
        Deque<BackgroundSelection> history =
                backgroundHistory.computeIfAbsent(icon, ignored -> new ArrayDeque<>());
        history.push(current);
        while (history.size() > MAX_UNDO_STEPS) {
            history.removeLast();
        }
        backgroundSelections.put(icon, edit.apply(current));
    }
}
