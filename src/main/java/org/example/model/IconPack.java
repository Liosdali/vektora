package org.example.model;

import org.example.export.SvgExportSettings;
import org.example.vector.BackgroundSelection;
import org.example.vector.EraseStroke;
import org.example.vector.PixelCoordinate;
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
    private TraceOptions traceOptions = TraceOptions.preserveSourceColors();
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

    public synchronized void addBackgroundRemovalSeed(IconAsset icon, PixelCoordinate seed) {
        if (!backgroundRemovalSeeds(icon).contains(seed)) {
            editBackground(icon, selection -> selection.withSeed(seed));
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
        backgroundSelections.put(icon, new BackgroundSelection(previous.seeds(), previous.strokes(),
                previous.protectStrokes(), current.tolerance(), current.defringe()));
        return true;
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
