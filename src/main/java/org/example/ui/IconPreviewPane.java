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
    private int splitPercent = 50;

    public IconPreviewPane() {
        getStyleClass().add("canvas-panel");
        heading.getStyleClass().add("canvas-title");
        Label hint = new Label("Soldaki kaynak, sağdaki SVG · ayırıcıyı sürükle · dama deseni saydam alanları gösterir");
        hint.getStyleClass().add("muted");
        VBox header = new VBox(4, heading, hint);
        header.setStyle("-fx-padding: 0 0 12 0;");
        setTop(header);
        setCenter(previews);
        VBox.setVgrow(previews, Priority.ALWAYS);
        showEmptyState();
    }

    public void showVariants(List<IconVariant> variants) {
        showVariants(variants, null);
    }

    /** Shows each variant; with a source preview the tab compares it with the SVG side by side. */
    public void showVariants(List<IconVariant> variants, String sourceSvg) {
        splitPercent = currentSplitPercent();
        previews.getTabs().clear();
        if (variants.isEmpty()) {
            showEmptyState();
            return;
        }

        for (IconVariant variant : variants) {
            WebView webView = new WebView();
            webView.setContextMenuEnabled(false);
            webView.getEngine().loadContent(sourceSvg == null
                    ? previewDocument(variant.svg()) : previewDocument(variant.svg(), sourceSvg, splitPercent));
            Tab tab = new Tab(variant.variantName(), webView);
            tab.setClosable(false);
            previews.getTabs().add(tab);
        }
    }

    /** Keeps the divider where the user left it when the preview reloads. */
    private int currentSplitPercent() {
        Tab selected = previews.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getContent() instanceof WebView webView) {
            try {
                Object value = webView.getEngine().executeScript(
                        "document.getElementById('split') ? document.getElementById('split').value : null");
                if (value != null) {
                    return Math.max(0, Math.min(100, Integer.parseInt(value.toString())));
                }
            } catch (RuntimeException ignored) {
                // The page is still loading; keep the previous position.
            }
        }
        return splitPercent;
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

    static String previewDocument(String svg, String sourceSvg, int splitPercent) {
        return """
                <!doctype html><html><head><meta charset="utf-8"><style>
                html,body{height:100%%;margin:0;background:#151619;color:#c9cbd1;font:12px 'Segoe UI',system-ui}
                body{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;
                  padding:16px;box-sizing:border-box}
                #stage{position:relative;width:440px;height:440px;max-width:100%%;cursor:ew-resize;
                  background-color:#1b1d21;
                  background-image:linear-gradient(45deg,#24262b 25%%,transparent 25%%,transparent 75%%,#24262b 75%%),
                    linear-gradient(45deg,#24262b 25%%,transparent 25%%,transparent 75%%,#24262b 75%%);
                  background-size:20px 20px;background-position:0 0,10px 10px}
                .layer{position:absolute;top:0;left:0;width:100%%;height:100%%}
                .layer svg{width:100%%;height:100%%}
                #handle{position:absolute;top:0;bottom:0;width:2px;margin-left:-1px;background:#c8f25c}
                .tag{position:absolute;top:8px;padding:3px 7px;border-radius:5px;background:#0f1013;
                  font:11px Consolas,monospace}
                .bar{display:flex;align-items:center;gap:8px}
                button{font:12px 'Segoe UI';color:#c9cbd1;background:#1e2025;border:1px solid #3a3d45;
                  border-radius:7px;padding:5px 10px;cursor:pointer}
                button:hover{border-color:#c8f25c}
                input{accent-color:#c8f25c;width:180px}
                </style></head><body>
                <div id="stage">
                  <div class="layer">%s</div>
                  <div class="layer" id="source">%s</div>
                  <div id="handle"></div>
                  <span class="tag" style="left:8px">KAYNAK</span>
                  <span class="tag" style="right:8px;color:#c8f25c">SVG</span>
                </div>
                <div class="bar">
                  <button onclick="show(100)">Kaynak</button>
                  <input id="split" type="range" min="0" max="100" value="%d" oninput="show(this.value)"
                    aria-label="Kaynak ile SVG arasındaki ayırıcı">
                  <button onclick="show(0)">SVG</button>
                </div>
                <script>
                function show(value){
                  var stage=document.getElementById('stage'),w=stage.clientWidth,h=stage.clientHeight;
                  var x=Math.round(w*value/100);
                  document.getElementById('source').style.clip='rect(0px,'+x+'px,'+h+'px,0px)';
                  var handle=document.getElementById('handle');
                  handle.style.left=x+'px';
                  handle.style.display=(value<=0||value>=100)?'none':'block';
                  document.getElementById('split').value=value;
                }
                var dragging=false,stage=document.getElementById('stage');
                function follow(event){
                  var box=stage.getBoundingClientRect();
                  show(Math.max(0,Math.min(100,Math.round((event.clientX-box.left)*100/box.width))));
                }
                stage.onmousedown=function(event){dragging=true;follow(event);};
                document.onmousemove=function(event){if(dragging){follow(event);}};
                document.onmouseup=function(){dragging=false;};
                show(%d);
                </script></body></html>
                """.formatted(svg, sourceSvg, splitPercent, splitPercent);
    }
}
