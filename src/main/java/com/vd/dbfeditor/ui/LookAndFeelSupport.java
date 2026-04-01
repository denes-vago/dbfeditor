package com.vd.dbfeditor.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.plaf.metal.MetalLookAndFeel;

public final class LookAndFeelSupport {
    private LookAndFeelSupport() {
    }

    public static List<LookAndFeelOption> availableOptions() {
        Map<String, LookAndFeelOption> options = new LinkedHashMap<>();
        addOptionalFlatLafOptions(options);
        for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            options.putIfAbsent(info.getClassName(), new LookAndFeelOption(info.getClassName(), info.getName(), info.getClassName()));
        }
        return new ArrayList<>(options.values());
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
            new LookAndFeelOption("flatlaf-light", "Flat Light", "com.formdev.flatlaf.FlatLightLaf"),
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

    private static void addOptionalFlatLafOptions(Map<String, LookAndFeelOption> options) {
        addOptionalOption(options, "flatlaf-light", "Flat Light", "com.formdev.flatlaf.FlatLightLaf");
        addOptionalOption(options, "flatlaf-dark", "Flat Dark", "com.formdev.flatlaf.FlatDarkLaf");
        addOptionalOption(options, "flatlaf-intellij", "Flat IntelliJ", "com.formdev.flatlaf.FlatIntelliJLaf");
        addOptionalOption(options, "flatlaf-darcula", "Flat Darcula", "com.formdev.flatlaf.FlatDarculaLaf");
    }

    private static void addOptionalOption(Map<String, LookAndFeelOption> options, String id, String displayName, String className) {
        if (classExists(className)) {
            options.putIfAbsent(className, new LookAndFeelOption(id, displayName, className));
        }
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className, false, LookAndFeelSupport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
