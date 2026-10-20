package org.example.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.example.export.SvgExportSettings;
import org.example.vector.TraceOptions;

public final class IconPack {
    private final List<IconAsset> icons = new ArrayList<>();
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
