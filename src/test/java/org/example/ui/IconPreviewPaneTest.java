package org.example.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class IconPreviewPaneTest {
    @Test
    void buildsPreviewDocumentForSvgContainingTheInlineArtwork() throws Exception {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="128" height="128" viewBox="0 0 8 8">
                  <path fill="rgb(255,0,0)" d="M 1 1 L 7 7 Z"/>
                </svg>
                """;
        Method documentBuilder = IconPreviewPane.class.getDeclaredMethod("previewDocument", String.class);
        documentBuilder.setAccessible(true);

        String document = (String) documentBuilder.invoke(null, svg);

        assertTrue(document.contains("height:100%;"));
        assertTrue(document.contains("<path fill=\"rgb(255,0,0)\""));
    }
}
