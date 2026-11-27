package org.dreeam.leaf.gui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

public final class GuiScrollBarUI extends BasicScrollBarUI {

    private Color hoverThumbColor;

    @Override
    protected void configureScrollBarColors() {
        super.configureScrollBarColors();
        this.hoverThumbColor = UIManager.getColor("ScrollBar.hoverThumbColor");
    }

    @Override
    protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
        graphics.setColor(this.trackColor);
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
        if (!this.scrollbar.isEnabled()) {
            return;
        }
        graphics.setColor(this.isDragging || this.isThumbRollover() ? this.hoverThumbColor : this.thumbColor);
        graphics.fillRect(bounds.x + 2, bounds.y + 2, bounds.width - 4, bounds.height - 4);
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return this.createArrowButton(orientation);
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return this.createArrowButton(orientation);
    }

    private JButton createArrowButton(int orientation) {
        BasicArrowButton button = new BasicArrowButton(orientation, this.trackColor, this.trackHighlightColor, this.thumbColor, this.trackColor) {
            @Override
            public void paint(Graphics graphics) {
                graphics.setColor(this.getModel().isPressed() || this.getModel().isRollover() ? trackHighlightColor : trackColor);
                graphics.fillRect(0, 0, this.getWidth(), this.getHeight());
                int size = Math.min(this.getWidth(), this.getHeight()) / 3;
                this.paintTriangle(graphics, (this.getWidth() - size) / 2, (this.getHeight() - size) / 2, size, this.getDirection(), this.isEnabled());
            }
        };
        button.setRolloverEnabled(true);
        return button;
    }
}
