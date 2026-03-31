package com.vd.dbfeditor.ui;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public final class TabHeader extends JPanel {
    private static final int BUTTON_SIZE = 16;
    private static final int TITLE_SIDE_PADDING = 6;
    private static final int RIGHT_PADDING = 8;

    private final JTabbedPane tabbedPane;
    private final IntConsumer closeHandler;
    private final BiConsumer<TabHeader, MouseEvent> popupHandler;
    private final Consumer<TabHeader> hoverHandler;
    private final Runnable hoverExitHandler;
    private final JLabel titleLabel;
    private final JButton closeButton;

    public TabHeader(
        JTabbedPane tabbedPane,
        IntConsumer closeHandler,
        BiConsumer<TabHeader, MouseEvent> popupHandler,
        Consumer<TabHeader> hoverHandler,
        Runnable hoverExitHandler,
        String title
    ) {
        this.tabbedPane = tabbedPane;
        this.closeHandler = closeHandler;
        this.popupHandler = popupHandler;
        this.hoverHandler = hoverHandler;
        this.hoverExitHandler = hoverExitHandler;
        setLayout(null);
        setOpaque(false);

        titleLabel = new JLabel(title + " ");
        closeButton = new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getForeground());
                    g2.setStroke(new BasicStroke(1.6f));
                    int size = Math.min(getWidth(), getHeight());
                    int inset = 4;
                    int startX = (getWidth() - size) / 2 + inset;
                    int startY = (getHeight() - size) / 2 + inset;
                    int endX = (getWidth() + size) / 2 - inset - 1;
                    int endY = (getHeight() + size) / 2 - inset - 1;
                    g2.drawLine(startX, startY, endX, endY);
                    g2.drawLine(startX, endY, endX, startY);
                } finally {
                    g2.dispose();
                }
            }
        };
        closeButton.setFocusable(false);
        closeButton.setOpaque(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setBorderPainted(false);
        closeButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        closeButton.setPreferredSize(new Dimension(BUTTON_SIZE, BUTTON_SIZE));
        closeButton.setVisible(false);
        closeButton.addActionListener(e -> closeHandler.accept(tabbedPane.indexOfTabComponent(this)));

        installMouseBehavior();

        add(closeButton);
        add(titleLabel);
    }

    public void setTitle(String title) {
        titleLabel.setText(title + " ");
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension titleSize = titleLabel.getPreferredSize();
        int width = Math.max(titleSize.width + (TITLE_SIDE_PADDING * 2), BUTTON_SIZE) + BUTTON_SIZE + RIGHT_PADDING;
        int height = Math.max(titleSize.height, BUTTON_SIZE);
        return new Dimension(width, height);
    }

    @Override
    public void doLayout() {
        int width = getWidth();
        int height = getHeight();
        int buttonY = Math.max(0, (height - BUTTON_SIZE) / 2);
        closeButton.setBounds(0, buttonY, BUTTON_SIZE, BUTTON_SIZE);

        Dimension titleSize = titleLabel.getPreferredSize();
        int titleX = Math.max(TITLE_SIDE_PADDING, (width - titleSize.width) / 2);
        int maxTitleWidth = Math.max(0, width - (TITLE_SIDE_PADDING * 2));
        int titleY = Math.max(0, (height - titleSize.height) / 2);
        titleLabel.setBounds(titleX, titleY, Math.min(titleSize.width, maxTitleWidth), titleSize.height);
    }

    private void installMouseBehavior() {
        MouseAdapter mouseListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                notifyHover();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                notifyHover();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (isPointerInsideHeader()) {
                    notifyHover();
                } else {
                    notifyHoverExit();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                selectTabIfNeeded(e);
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                selectTabIfNeeded(e);
                maybeShowPopup(e);
            }
        };

        addMouseListener(mouseListener);
        addMouseMotionListener(mouseListener);
        titleLabel.addMouseListener(mouseListener);
        titleLabel.addMouseMotionListener(mouseListener);
        closeButton.addMouseListener(mouseListener);
        closeButton.addMouseMotionListener(mouseListener);
    }

    public void setCloseButtonVisible(boolean visible) {
        closeButton.setVisible(visible);
        repaint();
    }

    private void maybeShowPopup(MouseEvent event) {
        if (popupHandler != null && event.isPopupTrigger()) {
            popupHandler.accept(this, event);
        }
    }

    private void notifyHover() {
        if (hoverHandler != null) {
            hoverHandler.accept(this);
        }
    }

    private void notifyHoverExit() {
        if (hoverExitHandler != null) {
            hoverExitHandler.run();
        }
    }

    private void selectTabIfNeeded(MouseEvent event) {
        if (SwingUtilities.isLeftMouseButton(event) && !event.isPopupTrigger()) {
            int index = tabbedPane.indexOfTabComponent(this);
            if (index >= 0 && tabbedPane.getSelectedIndex() != index) {
                tabbedPane.setSelectedIndex(index);
            }
        }
    }

    private boolean isPointerInsideHeader() {
        if (!isShowing() || MouseInfo.getPointerInfo() == null) {
            return false;
        }
        Point pointerLocation = MouseInfo.getPointerInfo().getLocation();
        Point localPoint = SwingUtilities.convertPoint(null, pointerLocation, this);
        return contains(localPoint);
    }
}
