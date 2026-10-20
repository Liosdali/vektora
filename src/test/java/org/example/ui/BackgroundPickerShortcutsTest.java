package org.example.ui;

import static org.example.ui.FxTestSupport.field;
import static org.example.ui.FxTestSupport.onFx;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

class BackgroundPickerShortcutsTest {
    @Test
    void lettersPickTheToolsAndBReturnsToTheLastBrush() throws Exception {
        FxTestSupport.startToolkit();
        BackgroundRegionPickerPane picker = onFx(BackgroundRegionPickerPane::new);

        assertTrue(onFx(() -> picker.handleShortcut(KeyCode.P) && selected(picker, "protectTool")));
        assertTrue(onFx(() -> picker.handleShortcut(KeyCode.W) && selected(picker, "regionTool")));
        assertTrue(onFx(() -> picker.handleShortcut(KeyCode.B) && selected(picker, "protectTool")));
        assertTrue(onFx(() -> picker.handleShortcut(KeyCode.E) && selected(picker, "eraseTool")));
        assertTrue(onFx(() -> picker.handleShortcut(KeyCode.F) && selected(picker, "frameTool")));
        assertTrue(onFx(() -> !picker.handleShortcut(KeyCode.Q)));
    }

    private static boolean selected(BackgroundRegionPickerPane picker, String tool) throws ReflectiveOperationException {
        return ((ToggleButton) field(picker, tool)).isSelected();
    }
}
