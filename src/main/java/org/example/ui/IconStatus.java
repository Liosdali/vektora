package org.example.ui;

/** How far an icon has been worked on, shown in the package list. */
public enum IconStatus {
    UNTOUCHED("İşlenmedi"),
    BACKGROUND_SEPARATED("Arka planı ayrıldı"),
    LAYERS_EDITED("Katmanları düzenlendi");

    private final String label;

    IconStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
