package org.example.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.export.ExportFormat;
import org.example.export.SvgExportSettings;
import org.example.model.IconAsset;
import org.example.segmentation.ModelStore;
import org.example.segmentation.OnnxSubjectSegmenter;
import org.example.segmentation.SegmentationException;
import org.example.segmentation.SegmentationModel;
import org.example.vector.BackgroundSelection;
import org.example.vector.TraceOptions;
import org.example.vector.VectorizationException;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class IconWorkspace extends BorderPane {
    private static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    private static final String[] DETAIL_LEVELS = {"En ayrıntılı", "Ayrıntılı", "Dengeli", "Sade", "En sade"};

    private final IconPackController controller;
    private final ObservableList<IconAsset> icons = FXCollections.observableArrayList();
    private final ListView<IconAsset> iconList = new ListView<>(icons);
    private final Map<IconAsset, Image> thumbnails = new IdentityHashMap<>();
    private final IconPreviewPane previewPane = new IconPreviewPane();
    private final BackgroundRegionPickerPane backgroundPicker = new BackgroundRegionPickerPane();
    private final StackPane previewStack = new StackPane(previewPane, backgroundPicker);
    private final LayerListPane layerList = new LayerListPane();
    private final PaletteEditorPane paletteEditor;
    private final ComboBox<ModeOption> colorMode = new ComboBox<>();
    private final Spinner<Integer> colorCount = new Spinner<>(2, 256, 8, 2);
    private final TextField widthField = new TextField("128");
    private final TextField heightField = new TextField("128");
    private final TextField scaleField = new TextField("1.0");
    private final Button importButton = new Button("PNG/JPG ekle");
    private final Button undoButton = new Button("Geri al");
    private final Button selectBackgroundButton = new Button("Elle seç ve düzelt");
    private final Button detectSubjectButton = new Button("Konuyu otomatik bul");
    private final Button detectAllButton = new Button("Tüm görsellerde bul");
    private final ComboBox<SegmentationModel> subjectModel = new ComboBox<>();
    private final CheckBox keepSubjectBox = new CheckBox("Yalnızca konuyu koru");
    private final ModelStore modelStore = ModelStore.inUserHome();
    private final Map<SegmentationModel, OnnxSubjectSegmenter> segmenters = new EnumMap<>(SegmentationModel.class);
    private final Button showSvgPreviewButton = new Button("SVG önizlemesine dön");
    private final Button clearBackgroundButton = new Button("Seçimleri temizle");
    private final Slider toleranceSlider = new Slider(0, BackgroundSelection.MAX_TOLERANCE,
            BackgroundSelection.DEFAULT_TOLERANCE);
    private final Label toleranceValue = new Label(Integer.toString(BackgroundSelection.DEFAULT_TOLERANCE));
    private final CheckBox defringeBox = new CheckBox("Kenar halesini temizle");
    private final Slider detailSlider = new Slider(0, TraceOptions.MAX_SIMPLIFICATION, 0);
    private final Label detailValue = new Label(DETAIL_LEVELS[0]);
    private final MenuButton exportButton = new MenuButton("Dışa aktar");
    private final Label status = new Label("PNG ikonlarını ekleyerek yeni bir paket oluştur.");
    private final Label packageCount = new Label("0 ikon");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final PreviewRequestTracker previewRequests = new PreviewRequestTracker();
    private final VBox settingsPane = new VBox(14);
    private final FirstRunGuide firstRunGuide = FirstRunGuide.forCurrentUser();
    private boolean selectingBackground;
    private boolean syncingBackgroundControls;
    private boolean syncingTraceControls;

    public IconWorkspace(IconPackController controller) {
        this.controller = controller;
        paletteEditor = new PaletteEditorPane(this::onPalettesChanged);
        buildHeader();
        buildWorkspace();
        buildStatusBar();
        configureControls();
        syncTraceControls(controller.pack().traceOptions());
        if (firstRunGuide.shouldShow()) {
            previewStack.getChildren().add(firstRunCard());
        }
        backgroundPicker.setVisible(false);
        backgroundPicker.setManaged(false);
        progress.setPrefSize(16, 16);
        progress.setVisible(false);
        iconList.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) -> {
                    refreshPreview(selected);
                    updateBackgroundControls(selected);
                    if (selectingBackground && selected != null) {
                        loadBackgroundPicker(selected);
                    }
                });
        sceneProperty().addListener((observable, previous, scene) -> registerShortcuts(scene));
    }

    private void registerShortcuts(Scene scene) {
        if (scene != null) {
            scene.getAccelerators().put(UNDO, this::undoBackgroundEdit);
        }
    }

    private void buildHeader() {
        Label mark = new Label("V");
        mark.getStyleClass().add("brand-mark");
        Label title = new Label("Vektora");
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label("Görseli arka planından ayır, katmanlarını düzenle, SVG logo olarak paketle.");
        subtitle.getStyleClass().add("muted");
        VBox titles = new VBox(1, title, subtitle);
        HBox brand = new HBox(12, mark, titles);
        brand.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        packageCount.getStyleClass().add("mono");

        undoButton.getStyleClass().add("secondary");
        undoButton.setTooltip(new Tooltip("Son arka plan düzenlemesini geri al (Ctrl+Z)"));
        undoButton.setOnAction(event -> undoBackgroundEdit());
        importButton.getStyleClass().add("secondary");
        importButton.setOnAction(event -> choosePngs());
        configureExportMenu();
        exportButton.getStyleClass().add("primary");
        HBox actions = new HBox(10, packageCount, undoButton, importButton, exportButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox header = new HBox(16, brand, spacer, actions);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("app-header");
        setTop(header);
    }

    private void buildWorkspace() {
        Label listTitle = sectionTitle("Paket");
        Label listHint = mutedLabel("Bir görsel seçerek düzenle");
        iconList.setPlaceholder(mutedLabel("Henüz görsel yok"));
        iconList.setCellFactory(list -> new ListCell<>() {
            private final ImageView thumbnail = new ImageView();

            {
                thumbnail.setFitWidth(36);
                thumbnail.setFitHeight(36);
                thumbnail.setPreserveRatio(true);
            }

            @Override
            protected void updateItem(IconAsset item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    thumbnail.setImage(thumbnails.computeIfAbsent(item,
                            icon -> FxImages.toFxImage(icon.image())));
                    setText(item.name() + "\n" + item.image().getWidth() + " × " + item.image().getHeight());
                    setGraphic(thumbnail);
                    setGraphicTextGap(10);
                }
            }
        });
        iconList.setPrefHeight(260);
        VBox.setVgrow(iconList, Priority.ALWAYS);
        ScrollPane layerScroll = new ScrollPane(layerList);
        layerScroll.setFitToWidth(true);
        layerScroll.setPrefViewportHeight(220);
        layerScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox iconSidebar = new VBox(10, listTitle, listHint, iconList, new Separator(), layerScroll);
        iconSidebar.getStyleClass().add("panel");
        iconSidebar.setPrefWidth(280);
        iconSidebar.setMinWidth(270);

        buildSettingsPane();
        ScrollPane settingsScroll = new ScrollPane(settingsPane);
        settingsScroll.setFitToWidth(true);
        settingsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        settingsScroll.setPrefWidth(320);
        settingsScroll.setMinWidth(280);
        settingsScroll.getStyleClass().add("panel");

        HBox content = new HBox(14, iconSidebar, previewStack, settingsScroll);
        content.setPadding(new Insets(14, 16, 14, 16));
        HBox.setHgrow(previewStack, Priority.ALWAYS);
        setCenter(content);
    }

    private Region firstRunCard() {
        String[] steps = {
                "Görsel ekle: PNG veya JPG dosyalarını pakete al.",
                "Arka planı ayır: kenardaki düz zemin kendiliğinden silinir; gerekirse konuyu bul ya da elle seç.",
                "Katmanları düzenle: soldaki listeden gizle ya da yeniden renklendir.",
                "Dışa aktar: SVG dosyaları, sprite ya da ZIP paketi olarak kaydet."};
        VBox list = new VBox(8);
        for (int index = 0; index < steps.length; index++) {
            Label number = new Label(Integer.toString(index + 1));
            number.getStyleClass().add("step-number");
            number.setMinWidth(Region.USE_PREF_SIZE);
            Label text = new Label(steps[index]);
            text.setWrapText(true);
            HBox row = new HBox(10, number, text);
            row.setAlignment(Pos.TOP_LEFT);
            list.getChildren().add(row);
        }
        Button close = new Button("Anladım");
        close.getStyleClass().add("primary");
        VBox card = new VBox(12, sectionTitle("Nasıl çalışır"), list, close);
        card.getStyleClass().add("guide-card");
        card.setMaxSize(440, Region.USE_PREF_SIZE);
        StackPane.setAlignment(card, Pos.TOP_CENTER);
        StackPane.setMargin(card, new Insets(140, 24, 0, 24));
        close.setOnAction(event -> {
            firstRunGuide.dismiss();
            previewStack.getChildren().remove(card);
        });
        return card;
    }

    private void buildSettingsPane() {
        VBox background = section(sectionTitle("Arka plan"),
                mutedLabel("Kenarlardaki düz zemin otomatik silinir. Konuyu modelle bulabilir ya da "
                        + "bölgeleri elle seçebilirsin."));
        for (Button button : List.of(selectBackgroundButton, showSvgPreviewButton, clearBackgroundButton)) {
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("secondary");
        }
        detectSubjectButton.setMaxWidth(Double.MAX_VALUE);
        detectSubjectButton.getStyleClass().add("primary");
        detectAllButton.setMaxWidth(Double.MAX_VALUE);
        detectAllButton.getStyleClass().add("secondary");
        detectAllButton.setTooltip(new Tooltip("Paketteki her görselde konuyu ayırır; her biri ayrıca geri alınabilir"));
        subjectModel.getItems().setAll(SegmentationModel.values());
        subjectModel.getSelectionModel().selectFirst();
        subjectModel.setMaxWidth(Double.MAX_VALUE);
        subjectModel.setAccessibleText("Konu ayırma modeli");
        Label toleranceLabel = new Label("Renk toleransı");
        Region toleranceSpacer = new Region();
        HBox.setHgrow(toleranceSpacer, Priority.ALWAYS);
        toleranceValue.getStyleClass().add("value-label");
        HBox toleranceHeader = new HBox(toleranceLabel, toleranceSpacer, toleranceValue);
        defringeBox.setSelected(true);
        background.getChildren().addAll(detectSubjectButton, detectAllButton, subjectModel,
                mutedLabel("Yerel model: görselin bilgisayardan çıkmaz. Model ilk kullanımda bir kez indirilir."),
                keepSubjectBox, new Separator(), selectBackgroundButton, showSvgPreviewButton,
                toleranceHeader, toleranceSlider,
                mutedLabel("Göze göre fark (ΔE): 2 zor fark edilir, 10 açıkça farklıdır. "
                        + "Yüksek değer, tıklanan renge daha uzak tonları da siler."),
                defringeBox,
                mutedLabel("Silinen zemine karışmış kenar piksellerini temizler; "
                        + "logonun etrafında ince renkli çerçeve kalmaz."),
                clearBackgroundButton);

        VBox colors = section(sectionTitle("Vektör ve renk"));
        colorMode.getItems().setAll(
                new ModeOption("Kaynak renklerini koru", TraceOptions.ColorMode.PRESERVE_SOURCE),
                new ModeOption("Renk sayısını azalt", TraceOptions.ColorMode.REDUCE_SOURCE),
                new ModeOption("Özel paletleri kullan", TraceOptions.ColorMode.CUSTOM_PALETTE));
        colorMode.getSelectionModel().selectFirst();
        colorMode.setMaxWidth(Double.MAX_VALUE);
        colorCount.setEditable(false);
        colorCount.setMaxWidth(Double.MAX_VALUE);
        detailSlider.setMajorTickUnit(1);
        detailSlider.setMinorTickCount(0);
        detailSlider.setSnapToTicks(true);
        detailSlider.setBlockIncrement(1);
        Region detailSpacer = new Region();
        HBox.setHgrow(detailSpacer, Priority.ALWAYS);
        detailValue.getStyleClass().add("value-label");
        HBox detailHeader = new HBox(new Label("Detay"), detailSpacer, detailValue);
        colors.getChildren().addAll(colorMode, mutedLabel("Azami renk sayısı"), colorCount,
                mutedLabel("Yalnızca renk sayısını azaltma modunda kullanılır."),
                detailHeader, detailSlider,
                mutedLabel("\"Ayrıntılı\" ve üstü kenar yumuşatmasının ara tonlarını en yakın renge katar. "
                        + "Sadeleştikçe küçük kırıntılar atılır, kenarlar daha düz çizilir, dosya küçülür."),
                paletteEditor);

        widthField.setPromptText("Genişlik");
        heightField.setPromptText("Yükseklik");
        widthField.setPrefColumnCount(5);
        heightField.setPrefColumnCount(5);
        scaleField.setPromptText("Örn. 0.85");
        Button applySettings = new Button("Ölçü ve ölçeği uygula");
        applySettings.setMaxWidth(Double.MAX_VALUE);
        applySettings.getStyleClass().add("secondary");
        applySettings.setOnAction(event -> applySettings());
        VBox canvas = new VBox(8, sectionTitle("Tuval"), mutedLabel("SVG tuval ölçüsü"),
                new HBox(8, widthField, heightField), mutedLabel("İkon ölçeği"), scaleField,
                mutedLabel("1.0 = tuvali doldurur · 0.5 = yarı boyut"), applySettings);

        settingsPane.getChildren().setAll(background, colors, canvas);
    }

    private void configureControls() {
        selectBackgroundButton.setOnAction(event -> showBackgroundPicker());
        detectSubjectButton.setOnAction(event -> detectSubject(false));
        detectAllButton.setOnAction(event -> detectSubject(true));
        keepSubjectBox.selectedProperty().addListener((observable, oldValue, selected) -> {
            IconAsset icon = iconList.getSelectionModel().getSelectedItem();
            if (icon != null && !syncingBackgroundControls && controller.hasSubjectMask(icon)) {
                controller.setKeepSubjectOnly(icon, selected);
                backgroundChanged(icon, selected ? "Yalnızca konu korunuyor." : "Konu maskesi kapatıldı.");
            }
        });
        showSvgPreviewButton.setOnAction(event -> showSvgPreview());
        clearBackgroundButton.setOnAction(event -> clearBackgroundSelections());
        toleranceSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            toleranceValue.setText(Integer.toString((int) Math.round(newValue.doubleValue())));
            if (!toleranceSlider.isValueChanging()) {
                applyTolerance();
            }
        });
        toleranceSlider.valueChangingProperty().addListener((observable, wasChanging, changing) -> {
            if (!changing) {
                applyTolerance();
            }
        });
        defringeBox.selectedProperty().addListener((observable, oldValue, selected) -> {
            IconAsset icon = iconList.getSelectionModel().getSelectedItem();
            if (icon != null && !syncingBackgroundControls) {
                controller.setDefringe(icon, selected);
                backgroundChanged(icon, selected
                        ? "Kenar halesi temizleniyor." : "Kenar halesi temizliği kapatıldı.");
            }
        });
        colorMode.setOnAction(event -> updateTraceOptions());
        detailSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            detailValue.setText(DETAIL_LEVELS[detailLevel()]);
            if (!detailSlider.isValueChanging()) {
                applyDetailLevel();
            }
        });
        detailSlider.valueChangingProperty().addListener((observable, wasChanging, changing) -> {
            if (!changing) {
                applyDetailLevel();
            }
        });
        colorCount.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (colorMode.getValue() != null
                    && colorMode.getValue().mode() == TraceOptions.ColorMode.REDUCE_SOURCE) {
                updateTraceOptions();
            }
        });
        exportButton.setDisable(true);
        updateBackgroundControls(null);
    }

    private void applyTolerance() {
        IconAsset icon = iconList.getSelectionModel().getSelectedItem();
        int tolerance = (int) Math.round(toleranceSlider.getValue());
        if (icon == null || syncingBackgroundControls
                || controller.backgroundSelection(icon).tolerance() == tolerance) {
            return;
        }
        controller.setBackgroundTolerance(icon, tolerance);
        backgroundChanged(icon, "Renk toleransı " + tolerance + " olarak ayarlandı.");
    }

    private void buildStatusBar() {
        HBox bar = new HBox(10, progress, status);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
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
        chooser.setTitle("PNG veya JPG görselleri seç");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG ve JPG görseller", "*.png", "*.jpg", "*.jpeg"));
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
            packageCount.setText(icons.size() + " ikon");
            if (iconList.getSelectionModel().getSelectedItem() == null && !icons.isEmpty()) {
                iconList.getSelectionModel().selectFirst();
            }
            updateBackgroundControls(iconList.getSelectionModel().getSelectedItem());
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
        var selectionSnapshot = controller.backgroundRemovalSnapshot();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws IOException, VectorizationException {
                controller.export(destination, format, selectionSnapshot);
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

    /** Shows the given options in the color controls without tracing again. */
    private void syncTraceControls(TraceOptions options) {
        syncingTraceControls = true;
        try {
            colorMode.getItems().stream().filter(option -> option.mode() == options.mode()).findFirst()
                    .ifPresent(option -> colorMode.getSelectionModel().select(option));
            if (options.mode() == TraceOptions.ColorMode.REDUCE_SOURCE) {
                colorCount.getValueFactory().setValue(options.maxColors());
            }
            detailSlider.setValue(options.simplification());
            detailValue.setText(DETAIL_LEVELS[options.simplification()]);
        } finally {
            syncingTraceControls = false;
        }
    }

    private void applyRecommendedColors() {
        TraceOptions recommended = TraceOptions.recommended();
        syncTraceControls(recommended);
        updateTraceOptions();
        status.setText("Renkler " + recommended.maxColors() + " tona indirildi.");
    }

    private void updateTraceOptions() {
        ModeOption selected = colorMode.getValue();
        if (selected == null || syncingTraceControls) {
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
        controller.setTraceOptions(options.withSimplification(detailLevel()));
        refreshPreview(iconList.getSelectionModel().getSelectedItem());
    }

    private int detailLevel() {
        return (int) Math.round(detailSlider.getValue());
    }

    private void applyDetailLevel() {
        if (detailLevel() != controller.pack().traceOptions().simplification()) {
            updateTraceOptions();
        }
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
        long request = previewRequests.beginRequest();
        if (icon == null) {
            previewPane.showVariants(List.of());
            previewPane.hideAdvice();
            layerList.showLayers(List.of(), Set.of(), Map.of(), (color, hidden) -> { }, (color, next) -> { });
            exportButton.setDisable(icons.isEmpty());
            return;
        }
        Task<IconPreview> task = new Task<>() {
            @Override
            protected IconPreview call() throws Exception {
                return controller.previewWithLayers(icon);
            }
        };
        runTask(task, preview -> {
            if (previewRequests.isCurrent(request)
                    && iconList.getSelectionModel().getSelectedItem() == icon) {
                previewPane.showVariants(preview.variants(), preview.sourceSvg());
                layerList.showLayers(preview.layers(), controller.hiddenLayerColors(icon),
                        controller.layerColors(icon), (color, hidden) -> toggleLayer(icon, color, hidden),
                        (color, replacement) -> recolorLayer(icon, color, replacement));
                status.setText(preview.layers().size() + " katmanlı vektör önizlemesi hazır.");
                controller.colorReductionAdvice(preview.layers().size()).ifPresentOrElse(
                        advice -> previewPane.showAdvice(advice, "8 renge indir", this::applyRecommendedColors),
                        previewPane::hideAdvice);
            }
        }, "Vektör önizlemesi hazırlanıyor…", () -> previewRequests.isCurrent(request));
    }

    private void toggleLayer(IconAsset icon, String color, boolean hidden) {
        controller.setLayerHidden(icon, color, hidden);
        refreshPreview(icon);
        status.setText(color.toUpperCase() + (hidden ? " katmanı gizlendi." : " katmanı yeniden gösteriliyor."));
    }

    private void recolorLayer(IconAsset icon, String color, String replacement) {
        controller.setLayerColor(icon, color, replacement);
        refreshPreview(icon);
        status.setText(replacement == null
                ? color.toUpperCase() + " katmanı özgün rengine döndü."
                : color.toUpperCase() + " katmanı " + replacement.toUpperCase() + " ile boyandı.");
    }

    private void showBackgroundPicker() {
        IconAsset icon = iconList.getSelectionModel().getSelectedItem();
        if (icon == null) {
            return;
        }
        selectingBackground = true;
        previewPane.setVisible(false);
        previewPane.setManaged(false);
        backgroundPicker.setVisible(true);
        backgroundPicker.setManaged(true);
        loadBackgroundPicker(icon);
        updateBackgroundControls(icon);
    }

    private void showSvgPreview() {
        selectingBackground = false;
        backgroundPicker.setVisible(false);
        backgroundPicker.setManaged(false);
        previewPane.setVisible(true);
        previewPane.setManaged(true);
        IconAsset icon = iconList.getSelectionModel().getSelectedItem();
        updateBackgroundControls(icon);
        refreshPreview(icon);
    }

    private void loadBackgroundPicker(IconAsset icon) {
        backgroundPicker.setSource(icon, controller.backgroundSelection(icon), controller.processedSource(icon),
                controller.suggestedRegions(icon),
                seed -> {
                    controller.addBackgroundRegion(icon, seed);
                    backgroundChanged(icon, !seed.contiguous() ? "Bu renge benzeyen bütün alanlar silindi."
                            : seed.followGradient() ? "Degrade boyunca bağlı arka plan silindi."
                            : "Bağlı arka plan bölgesi silindi.");
                }, stroke -> {
                    controller.addEraseStroke(icon, stroke);
                    backgroundChanged(icon, "Boyanan alan silindi.");
                }, stroke -> {
                    controller.addProtectStroke(icon, stroke);
                    backgroundChanged(icon, "Boyanan alan korunuyor.");
                });
    }

    private void backgroundChanged(IconAsset icon, String message) {
        if (selectingBackground) {
            loadBackgroundPicker(icon);
        }
        refreshPreview(icon);
        updateBackgroundControls(icon);
        status.setText(message);
    }

    private void detectSubject(boolean wholePack) {
        IconAsset icon = iconList.getSelectionModel().getSelectedItem();
        SegmentationModel model = subjectModel.getValue();
        if (icon == null || model == null) {
            return;
        }
        List<IconAsset> targets = wholePack ? List.copyOf(icons) : List.of(icon);
        if (!modelStore.isInstalled(model.spec())) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.initOwner(getWindow());
            confirm.setTitle("Model indirilsin mi?");
            confirm.setHeaderText(model + " modeli bir kez indirilecek");
            confirm.setContentText("Kaynak: github.com/danielgatis/rembg\nKonum: " + modelStore.path(model.spec())
                    + "\n\nYalnızca model dosyası indirilir ve SHA-256 ile doğrulanır. "
                    + "Görsellerin bilgisayarından çıkmaz.");
            if (confirm.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
                return;
            }
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Path file = modelStore.install(model.spec(), (done, total) -> updateMessage(String.format(
                        "Model indiriliyor… %d / %d MB", done >> 20, total >> 20)));
                OnnxSubjectSegmenter segmenter = segmenter(model, file);
                for (int index = 0; index < targets.size(); index++) {
                    updateMessage(targets.size() == 1 ? "Konu ayrılıyor…"
                            : "Konu ayrılıyor… " + (index + 1) + " / " + targets.size());
                    controller.detectSubject(targets.get(index), segmenter);
                }
                return null;
            }
        };
        task.messageProperty().addListener((observable, oldMessage, message) -> {
            if (message != null && !message.isBlank()) {
                status.setText(message);
            }
        });
        detectSubjectButton.setDisable(true);
        detectAllButton.setDisable(true);
        runTask(task, ignored -> backgroundChanged(icon, targets.size() == 1
                ? "Konu ayrıldı; geri kalanı silinecek. Ctrl+Z ile geri alınabilir."
                : targets.size() + " görselde konu ayrıldı. Her görsel Ctrl+Z ile ayrı ayrı geri alınabilir."),
                "Konu ayırma hazırlanıyor…");
    }

    private OnnxSubjectSegmenter segmenter(SegmentationModel model, Path file) throws SegmentationException {
        synchronized (segmenters) {
            OnnxSubjectSegmenter segmenter = segmenters.get(model);
            if (segmenter == null) {
                segmenter = new OnnxSubjectSegmenter(model, file);
                segmenters.put(model, segmenter);
            }
            return segmenter;
        }
    }

    private void undoBackgroundEdit() {
        IconAsset icon = iconList.getSelectionModel().getSelectedItem();
        if (icon != null && controller.undoBackgroundEdit(icon)) {
            backgroundChanged(icon, "Son arka plan düzenlemesi geri alındı.");
        }
    }

    private void clearBackgroundSelections() {
        IconAsset icon = iconList.getSelectionModel().getSelectedItem();
        if (icon == null) {
            return;
        }
        controller.clearBackgroundRemovalSeeds(icon);
        backgroundChanged(icon, "Arka plan seçimleri temizlendi (Ctrl+Z ile geri alınabilir).");
    }

    private void updateBackgroundControls(IconAsset icon) {
        boolean hasIcon = icon != null;
        BackgroundSelection selection = hasIcon ? controller.backgroundSelection(icon) : BackgroundSelection.EMPTY;
        selectBackgroundButton.setDisable(!hasIcon || selectingBackground);
        showSvgPreviewButton.setDisable(!selectingBackground);
        clearBackgroundButton.setDisable(!selection.hasEdits());
        undoButton.setDisable(!hasIcon || !controller.canUndoBackgroundEdit(icon));
        toleranceSlider.setDisable(!hasIcon);
        defringeBox.setDisable(!hasIcon);
        detectSubjectButton.setDisable(!hasIcon);
        detectAllButton.setDisable(icons.size() < 2);
        keepSubjectBox.setDisable(!hasIcon || !controller.hasSubjectMask(icon));
        syncingBackgroundControls = true;
        try {
            toleranceSlider.setValue(selection.tolerance());
            defringeBox.setSelected(selection.defringe());
            keepSubjectBox.setSelected(selection.keepSubjectOnly());
        } finally {
            syncingBackgroundControls = false;
        }
    }

    private <T> void runTask(Task<T> task, Consumer<T> onSuccess, String busyMessage) {
        runTask(task, onSuccess, busyMessage, () -> true);
    }

    private <T> void runTask(
            Task<T> task,
            Consumer<T> onSuccess,
            String busyMessage,
            BooleanSupplier completionIsCurrent) {
        importButton.setDisable(true);
        exportButton.setDisable(true);
        progress.setVisible(true);
        status.setText(busyMessage);
        task.setOnSucceeded(event -> {
            progress.setVisible(false);
            detectSubjectButton.setDisable(iconList.getSelectionModel().getSelectedItem() == null);
            detectAllButton.setDisable(icons.isEmpty());
            importButton.setDisable(false);
            exportButton.setDisable(icons.isEmpty());
            if (completionIsCurrent.getAsBoolean()) {
                onSuccess.accept(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            progress.setVisible(false);
            detectSubjectButton.setDisable(iconList.getSelectionModel().getSelectedItem() == null);
            detectAllButton.setDisable(icons.isEmpty());
            importButton.setDisable(false);
            exportButton.setDisable(icons.isEmpty());
            if (!completionIsCurrent.getAsBoolean()) {
                return;
            }
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

    private static VBox section(Label... labels) {
        VBox section = new VBox(8, labels);
        section.getStyleClass().add("panel-section");
        return section;
    }

    private static Label sectionTitle(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Label mutedLabel(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }

    private record ModeOption(String label, TraceOptions.ColorMode mode) {
        @Override
        public String toString() {
            return label;
        }
    }
}
