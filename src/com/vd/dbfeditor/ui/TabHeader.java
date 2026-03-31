package com.vd.dbfeditor.ui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

public final class TabHeader extends JPanel {
    private final JTabbedPane tabbedPane;
    private final IntConsumer closeHandler;
    private final JLabel titleLabel;
    private final JButton closeButton;

    public TabHeader(JTabbedPane tabbedPane, IntConsumer closeHandler, String title) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        this.tabbedPane = tabbedPane;
        this.closeHandler = closeHandler;
        setOpaque(false);

        titleLabel = new JLabel(title + " ");
        closeButton = new JButton("x");
        closeButton.setFocusable(false);
        closeButton.setOpaque(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        closeButton.setPreferredSize(new Dimension(18, 18));
        closeButton.addActionListener(e -> closeHandler.accept(tabbedPane.indexOfTabComponent(this)));

        add(titleLabel);
        add(closeButton);
    }

    public void setTitle(String title) {
        titleLabel.setText(title + " ");
    }
}
