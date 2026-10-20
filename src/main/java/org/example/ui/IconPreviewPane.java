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
        setStyle("-fx-background-color: #111827; -fx-background-radius: 16; -fx-padding: 20;");
        heading.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; -fx-text-fill: #f3f4f6;");
        Label hint = new Label("Vektör görünümünü kontrol et");
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: #9ca3af;");
        VBox header = new VBox(4, heading, hint);
        header.setStyle("-fx-padding: 0 0 16 0;");
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
                html,body{height:100%;margin:0;background:#151b27;color:#94a3b8;font:14px system-ui}
                body{display:grid;place-items:center;text-align:center}
                </style></head><body><div><div style="font-size:48px;color:#7c6cf0">◇</div>
                Bir PNG ekle, SVG önizlemen burada görünsün.</div></body></html>
                """);
        Tab tab = new Tab("SVG", webView);
        tab.setClosable(false);
        previews.getTabs().setAll(tab);
    }

    private static String previewDocument(String svg) {
        return """
                <!doctype html><html><head><meta charset="utf-8"><style>
                html,body{height:100%%;margin:0;background:#151b27}
                body{display:grid;place-items:center;padding:20px;box-sizing:border-box}
                svg{width:min(100%%,420px);height:min(100%%,420px);filter:drop-shadow(0 16px 30px #0006)}
                </style></head><body>%s</body></html>
                """.formatted(svg);
    }
}
