package org.example.ui;

import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import org.example.export.IconVariant;

public final class IconPreviewPane extends BorderPane {
    private final TabPane previews = new TabPane();
    private final Label heading = new Label("Önizleme");

    public IconPreviewPane() {
        getStyleClass().add("canvas-panel");
        heading.getStyleClass().add("canvas-title");
        Label hint = new Label("SVG çıktısı · dama deseni saydam alanları gösterir");
        hint.getStyleClass().add("muted");
        VBox header = new VBox(4, heading, hint);
        header.setStyle("-fx-padding: 0 0 12 0;");
        setTop(header);
        setCenter(previews);
        VBox.setVgrow(previews, Priority.ALWAYS);
        showEmptyState();
    }

    public void showVariants(List<IconVariant> variants) {
        previews.getTabs().clear();
        if (variants.isEmpty()) {
            showEmptyState();
            return;
        }

        for (IconVariant variant : variants) {
            WebView webView = new WebView();
            webView.setContextMenuEnabled(false);
            webView.getEngine().loadContent(previewDocument(variant.svg()));
            Tab tab = new Tab(variant.variantName(), webView);
            tab.setClosable(false);
            previews.getTabs().add(tab);
        }
    }

    private void showEmptyState() {
        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.getEngine().loadContent("""
                <!doctype html><html><head><style>
                html,body{height:100%;margin:0;background:#151619;color:#9a9ea8;font:14px 'Segoe UI',system-ui}
                body{display:grid;place-items:center;text-align:center}
                </style></head><body><div><div style="font-size:48px;color:#c8f25c">◇</div>
                Bir PNG ekle, SVG önizlemen burada görünsün.</div></body></html>
                """);
        Tab tab = new Tab("SVG", webView);
        tab.setClosable(false);
        previews.getTabs().setAll(tab);
    }

    private static String previewDocument(String svg) {
        return """
                <!doctype html><html><head><meta charset="utf-8"><style>
                html,body{height:100%%;margin:0;background:#151619}
                body{display:grid;place-items:center;padding:20px;box-sizing:border-box;background-color:#1b1d21;
                  background-image:linear-gradient(45deg,#24262b 25%%,transparent 25%%,transparent 75%%,#24262b 75%%),
                    linear-gradient(45deg,#24262b 25%%,transparent 25%%,transparent 75%%,#24262b 75%%);
                  background-size:20px 20px;background-position:0 0,10px 10px}
                svg{width:min(100%%,440px);height:min(100%%,440px)}
                </style></head><body>%s</body></html>
                """.formatted(svg);
    }
}
