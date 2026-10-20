package org.example.ui;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.example.export.ExportFormat;
import org.example.export.IconVariant;
import org.example.export.SvgExportSettings;
import org.example.model.IconAsset;
import org.example.vector.TraceOptions;
import org.example.vector.VectorizationException;

public final class IconWorkspace extends BorderPane {
    private final IconPackController controller;
    private final ObservableList<IconAsset> icons = FXCollections.observableArrayList();
    private final ListView<IconAsset> iconList = new ListView<>(icons);
    private final IconPreviewPane previewPane = new IconPreviewPane();
    private final PaletteEditorPane paletteEditor;
    private final ComboBox<ModeOption> colorMode = new ComboBox<>();
    private final Spinner<Integer> colorCount = new Spinner<>(2, 256, 16, 2);
    private final TextField widthField = new TextField("128");
    private final TextField heightField = new TextField("128");
    private final TextField scaleField = new TextField("1.0");
    private final Button importButton = new Button("PNG ekle");
    private final MenuButton exportButton = new MenuButton("Dışa aktar");
    private final Label status = new Label("PNG ikonlarını ekleyerek yeni bir paket oluştur.");
    private final Label packageCount = new Label("0 ikon");
    private final ProgressIndicator progress = new ProgressIndicator();

    public IconWorkspace(IconPackController controller) {
        this.controller = controller;
        paletteEditor = new PaletteEditorPane(this::onPalettesChanged);
        setStyle("-fx-background-color: #0b0e14; -fx-font-family: 'Segoe UI';");
        buildHeader();
        buildWorkspace();
        buildStatusBar();
        configureControls();
        progress.setPrefSize(18, 18);
        progress.setVisible(false);
        iconList.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> refreshPreview(selected));
    }

    private void buildHeader() {
        Label mark = new Label("V");
        mark.setStyle("-fx-background-color: #7667f3; -fx-text-fill: white; -fx-font-size: 15px; "
                + "-fx-font-weight: 800; -fx-padding: 8 12; -fx-background-radius: 10;");
        Label title = new Label("Vektora");
        title.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 20px; -fx-font-weight: 800;");
        Label subtitle = new Label("PNG ikonlarını vektörleştir, renklerini düzenle ve paketle.");
        subtitle.setStyle("-fx-text-fill: #929bad; -fx-font-size: 12px;");
        VBox titles = new VBox(3, title, subtitle);
        HBox brand = new HBox(12, mark, titles);
        brand.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        packageCount.setStyle("-fx-text-fill: #aab3c2; -fx-font-size: 12px;");

        importButton.setStyle(primaryButtonStyle());
        importButton.setOnAction(event -> choosePngs());
        configureExportMenu();
        exportButton.setStyle(primaryButtonStyle());
        HBox actions = new HBox(10, packageCount, importButton, exportButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox header = new HBox(16, brand, spacer, actions);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(22, 28, 20, 28));
        header.setStyle("-fx-background-color: #111621; -fx-border-color: transparent transparent #252c39 transparent;");
        setTop(header);
    }

    private void buildWorkspace() {
        Label listTitle = sectionTitle("İkonlar");
        Label listHint = mutedLabel("Paketteki PNG dosyaları");
        iconList.setPlaceholder(new Label("Henüz ikon yok"));
        iconList.setPrefWidth(220);
        iconList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(IconAsset item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.name() + "\n" + item.image().getWidth() + " × " + item.image().getHeight());
                }
                setStyle("-fx-text-fill: #e2e8f0; -fx-padding: 12 10; -fx-font-size: 12px;");
            }
        });
        VBox iconSidebar = new VBox(10, listTitle, listHint, iconList);
        iconSidebar.setPadding(new Insets(18));
        iconSidebar.setPrefWidth(250);
        iconSidebar.setStyle("-fx-background-color: #111621; -fx-background-radius: 14;");
        VBox.setVgrow(iconList, Priority.ALWAYS);

        buildSettingsPane();
        HBox content = new HBox(16, iconSidebar, previewPane, settingsPane);
        content.setPadding(new Insets(20, 24, 16, 24));
        HBox.setHgrow(previewPane, Priority.ALWAYS);
        setCenter(content);
    }

    private final VBox settingsPane = new VBox(12);

    private void buildSettingsPane() {
        settingsPane.setPadding(new Insets(16));
        settingsPane.setPrefWidth(310);
        settingsPane.setMinWidth(270);
        settingsPane.setStyle("-fx-background-color: #111621; -fx-background-radius: 14;");

        Label title = sectionTitle("Paket ayarları");
        Label dimensionsLabel = mutedLabel("SVG tuval ölçüsü");
        widthField.setPromptText("Genişlik");
        heightField.setPromptText("Yükseklik");
        HBox dimensions = new HBox(8, widthField, heightField);
        widthField.setPrefColumnCount(5);
        heightField.setPrefColumnCount(5);
        styleField(widthField);
        styleField(heightField);

        Label scaleLabel = mutedLabel("İkon ölçeği");
        styleField(scaleField);
        scaleField.setPromptText("Örn. 0.85");
        Label scaleHint = mutedLabel("1.0 = tuvali doldurur · 0.5 = yarı boyut");

        Button applySettings = new Button("Ölçü ve ölçeği uygula");
        applySettings.setMaxWidth(Double.MAX_VALUE);
        applySettings.setStyle(secondaryButtonStyle());
        applySettings.setOnAction(event -> applySettings());

        Separator separator = new Separator();
        Label colorsTitle = sectionTitle("Vektör ve renk");
        colorMode.getItems().setAll(
                new ModeOption("Kaynak renklerini koru", TraceOptions.ColorMode.PRESERVE_SOURCE),
                new ModeOption("Renk sayısını azalt", TraceOptions.ColorMode.REDUCE_SOURCE),
                new ModeOption("Özel paletleri kullan", TraceOptions.ColorMode.CUSTOM_PALETTE));
        colorMode.getSelectionModel().selectFirst();
        colorMode.setMaxWidth(Double.MAX_VALUE);
        colorMode.setStyle("-fx-background-color: #1d2533; -fx-text-fill: #e2e8f0;");

        Label colorCountLabel = mutedLabel("Azami renk sayısı");
        colorCount.setEditable(false);
        colorCount.setMaxWidth(Double.MAX_VALUE);
        colorCount.setStyle("-fx-background-color: #1d2533;");
        Label colorsHint = mutedLabel("Yalnızca kaynak renklerini azaltma modunda kullanılır.");

        ScrollPane palettesScroll = new ScrollPane(paletteEditor);
        palettesScroll.setFitToWidth(true);
        palettesScroll.setPrefViewportHeight(250);
        palettesScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        settingsPane.getChildren().addAll(
                title, dimensionsLabel, dimensions, scaleLabel, scaleField, scaleHint, applySettings,
                separator, colorsTitle, colorMode, colorCountLabel, colorCount, colorsHint, palettesScroll);
        VBox.setVgrow(palettesScroll, Priority.ALWAYS);
    }

    private void configureControls() {
        colorMode.setOnAction(event -> updateTraceOptions());
        colorCount.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (colorMode.getValue() != null
                    && colorMode.getValue().mode() == TraceOptions.ColorMode.REDUCE_SOURCE) {
                updateTraceOptions();
            }
        });
        exportButton.setDisable(true);
    }

    private void buildStatusBar() {
        HBox bar = new HBox(10, progress, status);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 28, 16, 28));
        progress.setVisible(false);
        setBottom(bar);
    }

    private void configureExportMenu() {
        MenuItem individual = new MenuItem("SVG dosyaları…");
        individual.setOnAction(event -> export(ExportFormat.INDIVIDUAL_SVGS));
        MenuItem sprite = new MenuItem("SVG sprite…");
        sprite.setOnAction(event -> export(ExportFormat.SPRITE_SVG));
        MenuItem zip = new MenuItem("ZIP paketi…");
        zip.setOnAction(event -> export(ExportFormat.ZIP));
        exportButton.getItems().setAll(individual, sprite, zip);
    }

    private void choosePngs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("PNG ikonlarını seç");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG görseller", "*.png"));
        List<File> files = chooser.showOpenMultipleDialog(getWindow());
        if (files == null || files.isEmpty()) {
            return;
        }
        List<Path> paths = files.stream().map(File::toPath).toList();
        Task<List<ImportIssue>> task = new Task<>() {
            @Override
            protected List<ImportIssue> call() {
                return controller.addImages(paths);
            }
        };
        runTask(task, issues -> {
            icons.setAll(controller.pack().icons());
            packageCount.setText(icons.size() + (icons.size() == 1 ? " ikon" : " ikon"));
            if (iconList.getSelectionModel().getSelectedItem() == null && !icons.isEmpty()) {
                iconList.getSelectionModel().selectFirst();
            }
            status.setText(icons.size() + " ikon pakete eklendi.");
            if (!issues.isEmpty()) {
                String details = issues.stream()
                        .map(issue -> issue.path().getFileName() + ": " + issue.message())
                        .reduce((first, next) -> first + "\n" + next)
                        .orElse("");
                showAlert(Alert.AlertType.WARNING, "Bazı PNG dosyaları eklenemedi", details);
            }
        }, "PNG dosyaları okunuyor…");
    }

    private void export(ExportFormat format) {
        if (icons.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Paket boş", "Önce en az bir PNG ikonu ekle.");
            return;
        }
        Path destination = chooseExportDestination(format);
        if (destination == null) {
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws IOException, VectorizationException {
                controller.export(destination, format);
                return null;
            }
        };
        runTask(task, ignored -> status.setText("Paket dışa aktarıldı: " + destination),
                "SVG ikon paketi hazırlanıyor…");
    }

    private Path chooseExportDestination(ExportFormat format) {
        Window window = getWindow();
        if (format == ExportFormat.INDIVIDUAL_SVGS) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("SVG dosyalarının kaydedileceği klasörü seç");
            File directory = chooser.showDialog(window);
            return directory == null ? null : directory.toPath();
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(format == ExportFormat.ZIP ? "ZIP ikon paketini kaydet" : "SVG sprite kaydet");
        chooser.getExtensionFilters().add(format == ExportFormat.ZIP
                ? new FileChooser.ExtensionFilter("ZIP arşivi", "*.zip")
                : new FileChooser.ExtensionFilter("SVG dosyası", "*.svg"));
        chooser.setInitialFileName(format == ExportFormat.ZIP ? "ikon-paketi.zip" : "ikon-sprite.svg");
        File file = chooser.showSaveDialog(window);
        return file == null ? null : file.toPath();
    }

    private void applySettings() {
        try {
            SvgExportSettings settings = new SvgExportSettings(
                    Integer.parseInt(widthField.getText().trim()),
                    Integer.parseInt(heightField.getText().trim()),
                    Double.parseDouble(scaleField.getText().trim()));
            controller.setExportSettings(settings);
            status.setText("SVG tuval ölçüsü ve ölçeği güncellendi.");
            refreshPreview(iconList.getSelectionModel().getSelectedItem());
        } catch (NumberFormatException exception) {
            showAlert(Alert.AlertType.ERROR, "Ölçüler geçersiz", "Genişlik, yükseklik ve ölçek sayısal olmalı.");
        } catch (IllegalArgumentException exception) {
            showAlert(Alert.AlertType.ERROR, "Ölçüler geçersiz", exception.getMessage());
        }
    }

    private void updateTraceOptions() {
        ModeOption selected = colorMode.getValue();
        if (selected == null) {
            return;
        }
        List<List<Color>> palettes = paletteEditor.palettes();
        if (selected.mode() == TraceOptions.ColorMode.CUSTOM_PALETTE && palettes.isEmpty()) {
            paletteEditor.addDefaultPalette();
            return;
        }
        TraceOptions options = switch (selected.mode()) {
            case PRESERVE_SOURCE -> TraceOptions.preserveSourceColors();
            case REDUCE_SOURCE -> new TraceOptions(
                    TraceOptions.ColorMode.REDUCE_SOURCE, colorCount.getValue(), List.of());
            case CUSTOM_PALETTE -> new TraceOptions(
                    TraceOptions.ColorMode.CUSTOM_PALETTE, palettes.getFirst().size(), palettes.getFirst());
        };
        controller.setTraceOptions(options);
        refreshPreview(iconList.getSelectionModel().getSelectedItem());
    }

    private void onPalettesChanged(List<List<Color>> palettes) {
        controller.setPalettes(palettes);
        if (palettes.isEmpty() && colorMode.getValue() != null
                && colorMode.getValue().mode() == TraceOptions.ColorMode.CUSTOM_PALETTE) {
            colorMode.getSelectionModel().selectFirst();
        }
        updateTraceOptions();
    }

    private void refreshPreview(IconAsset icon) {
        if (icon == null) {
            previewPane.showVariants(List.of());
            exportButton.setDisable(icons.isEmpty());
            return;
        }
        Task<List<IconVariant>> task = new Task<>() {
            @Override
            protected List<IconVariant> call() throws Exception {
                return controller.preview(icon);
            }
        };
        runTask(task, variants -> {
            if (iconList.getSelectionModel().getSelectedItem() == icon) {
                previewPane.showVariants(variants);
            }
        }, "Vektör önizlemesi hazırlanıyor…");
    }

    private <T> void runTask(Task<T> task, Consumer<T> onSuccess, String busyMessage) {
        importButton.setDisable(true);
        exportButton.setDisable(true);
        progress.setVisible(true);
        status.setText(busyMessage);
        task.setOnSucceeded(event -> {
            progress.setVisible(false);
            importButton.setDisable(false);
            exportButton.setDisable(icons.isEmpty());
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            progress.setVisible(false);
            importButton.setDisable(false);
            exportButton.setDisable(icons.isEmpty());
            Throwable failure = task.getException();
            showAlert(Alert.AlertType.ERROR, "İşlem tamamlanamadı",
                    failure == null || failure.getMessage() == null
                            ? "Beklenmeyen bir hata oluştu." : failure.getMessage());
            status.setText("İşlem başarısız oldu.");
        });
        Thread worker = new Thread(task, "icon-pack-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.initOwner(getWindow());
        alert.show();
    }

    private Window getWindow() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private static Label sectionTitle(String value) {
        Label label = new Label(value);
        label.setStyle("-fx-text-fill: #f1f5f9; -fx-font-size: 14px; -fx-font-weight: 700;");
        return label;
    }

    private static Label mutedLabel(String value) {
        Label label = new Label(value);
        label.setStyle("-fx-text-fill: #8d98aa; -fx-font-size: 11px;");
        return label;
    }

    private static void styleField(TextField field) {
        field.setStyle("-fx-background-color: #1d2533; -fx-text-fill: #e5e7eb; "
                + "-fx-prompt-text-fill: #697386; -fx-background-radius: 7;");
    }

    private static String primaryButtonStyle() {
        return "-fx-background-color: #7667f3; -fx-text-fill: white; -fx-background-radius: 8; "
                + "-fx-padding: 9 14; -fx-font-weight: 700;";
    }

    private static String secondaryButtonStyle() {
        return "-fx-background-color: #242d3b; -fx-text-fill: #e5e7eb; -fx-background-radius: 8; "
                + "-fx-padding: 9 10; -fx-font-size: 11px;";
    }

    private record ModeOption(String label, TraceOptions.ColorMode mode) {
        @Override
        public String toString() {
            return label;
        }
    }
}
