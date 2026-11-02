package com.zlyd.mpr.ui.menu;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 环形菜单的图标按钮：圆形底色 + 矢量图标 + 中文短标签，悬停/按下时高亮。
 */
public final class RingMenuButton extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LogManager.getLogger(RingMenuButton.class);
    public static final int SIZE = 56;

    private static final Color NORMAL_FILL = new Color(18, 18, 18, 200);
    private static final Color HOVER_FILL = new Color(255, 196, 0, 235);
    private static final Color PRESSED_FILL = new Color(214, 156, 0, 240);
    private static final Color NORMAL_BORDER = new Color(255, 255, 255, 110);
    private static final Color HOVER_BORDER = new Color(255, 255, 255, 230);
    private static final Color NORMAL_CONTENT = new Color(255, 255, 255);
    private static final Color ACTIVE_CONTENT = new Color(28, 28, 28);
    private static final int LABEL_BASELINE = 52;

    private final MprMenuAction action;
    private final Image defaultImage;
    private final Image hoverImage;
    private final boolean imageReady;
    private Runnable leftAction;
    private Runnable rightAction;
    private boolean hover;
    private boolean pressed;

    public RingMenuButton(MprMenuAction action) {
        this.action = action;
        this.defaultImage = loadIcon("_defort.png");
        this.hoverImage = loadIcon("_yellow.png");
        this.imageReady = defaultImage != null && hoverImage != null;
        setSize(SIZE, SIZE);
        setPreferredSize(new Dimension(SIZE, SIZE));
        setToolTipText(action.getDisplayName());
        ToolTipManager.sharedInstance().registerComponent(this);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hover = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                boolean inside = contains(event.getPoint());
                pressed = false;
                repaint();
                if (!inside) {
                    return;
                }
                if (event.getButton() == MouseEvent.BUTTON3) {
                    run(rightAction != null ? rightAction : leftAction);
                } else if (event.getButton() == MouseEvent.BUTTON1) {
                    run(leftAction);
                }
            }
        });
    }

    /**
     * 设置左键动作；右键未单独设置时沿用左键动作。
     *
     * @param action 点击回调
     */
    public void setAction(Runnable action) {
        setLeftAction(action);
    }

    public void setLeftAction(Runnable action) {
        this.leftAction = action;
    }

    public void setRightAction(Runnable action) {
        this.rightAction = action;
    }

    private static void run(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (imageReady) {
                g.drawImage(hover || pressed ? hoverImage : defaultImage, 0, 0, SIZE, SIZE, this);
                if (pressed) {
                    g.setColor(new Color(0, 0, 0, 45));
                    g.fillOval(1, 1, SIZE - 2, SIZE - 2);
                }
                return;
            }
            boolean active = hover || pressed;
            g.setColor(pressed ? PRESSED_FILL : hover ? HOVER_FILL : NORMAL_FILL);
            g.fillOval(1, 1, SIZE - 2, SIZE - 2);
            g.setColor(active ? HOVER_BORDER : NORMAL_BORDER);
            g.setStroke(new BasicStroke(1.2f));
            g.drawOval(1, 1, SIZE - 3, SIZE - 3);
            g.setColor(active ? ACTIVE_CONTENT : NORMAL_CONTENT);
            action.getGlyph().paint(g, SIZE / 2.0, 24.0, 24.0);
            drawLabel(g);
        } finally {
            g.dispose();
        }
    }

    /**
     * 读取与原 MPR2D 菜单同名的图标；缺失时退回内置矢量图标。
     */
    private BufferedImage loadIcon(String suffix) {
        String path = "/button/" + action.getIconName() + suffix;
        try (InputStream input = RingMenuButton.class.getResourceAsStream(path)) {
            if (input == null) {
                LOG.warn("缺少菜单图标资源: {}", path);
                return null;
            }
            return ImageIO.read(input);
        } catch (IOException e) {
            LOG.warn("读取菜单图标失败: {}", path, e);
            return null;
        }
    }

    private void drawLabel(Graphics2D g) {
        g.setFont(new Font("Dialog", Font.BOLD, 10));
        FontMetrics metrics = g.getFontMetrics();
        String text = action.getDisplayName();
        int x = (SIZE - metrics.stringWidth(text)) / 2;
        g.drawString(text, x, LABEL_BASELINE);
    }
}
