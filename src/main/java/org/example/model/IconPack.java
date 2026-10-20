package org.example.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.example.export.SvgExportSettings;
import org.example.vector.PixelCoordinate;
import org.example.vector.TraceOptions;

public final class IconPack {
    private final List<IconAsset> icons = new ArrayList<>();
    private final Map<IconAsset, List<PixelCoordinate>> backgroundRemovalSeeds = new IdentityHashMap<>();
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
        List<PixelCoordinate> seeds = new ArrayList<>(backgroundRemovalSeeds(icon));
        if (!seeds.contains(seed)) {
            seeds.add(seed);
            backgroundRemovalSeeds.put(icon, List.copyOf(seeds));
        }
    }

    public synchronized List<PixelCoordinate> backgroundRemovalSeeds(IconAsset icon) {
        return backgroundRemovalSeeds.getOrDefault(icon, List.of());
    }

    public synchronized void clearBackgroundRemovalSeeds(IconAsset icon) {
        backgroundRemovalSeeds.remove(icon);
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
}
