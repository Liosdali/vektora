package org.example.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.export.SvgExportService;
import org.example.model.IconPack;
import org.example.vector.ImageTracerVectorizer;

public final class IconPackApplication extends Application {
    @Override
    public void start(Stage stage) {
        IconPackController controller = new IconPackController(
                new IconPack(), new ImageTracerVectorizer(), new SvgExportService());
        IconWorkspace workspace = new IconWorkspace(controller);
        Scene scene = new Scene(workspace, 1360, 860);
        scene.getStylesheets().add(
                IconPackApplication.class.getResource("vektora.css").toExternalForm());
        stage.setTitle("Vectora | PNG'den SVG ikon paketi");
        stage.setMinWidth(960);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.show();
    }
}
