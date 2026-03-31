package com.vd.dbfeditor.ui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.plaf.metal.MetalLookAndFeel;

public final class LookAndFeelSupport {
    private LookAndFeelSupport() {
    }

    public static List<LookAndFeelOption> availableOptions() {
        List<LookAndFeelOption> options = new ArrayList<>();
        for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            options.add(new LookAndFeelOption(info.getClassName(), info.getName(), info.getClassName()));
        }
        return options;
    }

    public static String currentClassName() {
        LookAndFeel lookAndFeel = UIManager.getLookAndFeel();
        return lookAndFeel != null ? lookAndFeel.getClass().getName() : "";
    }

    public static LookAndFeelOption findOption(String selectionId) {
        for (LookAndFeelOption option : availableOptions()) {
            if (option.id().equals(selectionId) || option.className().equals(selectionId)) {
                return option;
            }
        }
        return null;
    }

    public static void apply(LookAndFeelOption option) throws Exception {
        UIManager.setLookAndFeel(option.className());
    }

    public static void applyStartupLookAndFeel() {
        List<LookAndFeelOption> candidates = List.of(
            new LookAndFeelOption(
                UIManager.getSystemLookAndFeelClassName(),
                "System",
                UIManager.getSystemLookAndFeelClassName()
            ),
            new LookAndFeelOption(MetalLookAndFeel.class.getName(), "Metal", MetalLookAndFeel.class.getName())
        );

        for (LookAndFeelOption option : candidates) {
            if (option.className() == null || option.className().isBlank()) {
                continue;
            }
            try {
                apply(option);
                return;
            } catch (Exception e) {
                // Try the next available look and feel.
            }
        }
    }
}
