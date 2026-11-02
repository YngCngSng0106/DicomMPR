package com.zlyd.mpr.ui.menu;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 环形菜单的矢量图标。
 *
 * <p>不依赖外部图片资源：所有图标按 24×24 逻辑坐标绘制，再按按钮尺寸整体缩放，
 * 颜色沿用调用方设置的画笔颜色，因此可直接适配默认态与高亮态。</p>
 */
public enum MenuGlyph {

    CONTRAST, ZOOM, MOVE, CROSSHAIR, INVERT, RESET, LINE, ANGLE, SAVE, PEN,
    LUNG, MEDIASTINUM, ABDOMEN, BRAIN, BONE, DOTS, GEAR, DOCUMENT_J, DOCUMENT_B,
    FOLDER, ARROW, POLYGON, POLYLINE, CLOSED_POLYLINE, CIRCLE, RECT, TEXT, CLEAR;

    private static final double LOGICAL_SIZE = 24.0;

    /**
     * 把图标绘制到指定中心点。
     *
     * @param graphics 目标画布（颜色已由调用方设置）
     * @param centerX  图标中心 X
     * @param centerY  图标中心 Y
     * @param size     图标外接正方形边长
     */
    public void paint(Graphics2D graphics, double centerX, double centerY, double size) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(centerX - size / 2.0, centerY - size / 2.0);
            g.scale(size / LOGICAL_SIZE, size / LOGICAL_SIZE);
            g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            paintShape(g);
        } finally {
            g.dispose();
        }
    }

    private void paintShape(Graphics2D g) {
        switch (this) {
            case CONTRAST:
                paintContrast(g);
                break;
            case ZOOM:
                g.draw(new Ellipse2D.Double(4.0, 4.0, 11.0, 11.0));
                g.draw(new Line2D.Double(14.0, 14.0, 20.0, 20.0));
                break;
            case MOVE:
                paintMove(g);
                break;
            case CROSSHAIR:
                paintCrosshair(g);
                break;
            case INVERT:
                g.draw(new Rectangle2D.Double(4.0, 5.0, 16.0, 14.0));
                g.fill(new Rectangle2D.Double(4.0, 5.0, 8.0, 14.0));
                break;
            case RESET:
                paintReset(g);
                break;
            case LINE:
                paintLineMeasurement(g);
                break;
            case ANGLE:
                paintAngle(g);
                break;
            case SAVE:
                paintSave(g);
                break;
            case PEN:
                paintPen(g);
                break;
            case LUNG:
                paintLung(g);
                break;
            case MEDIASTINUM:
                paintMediastinum(g);
                break;
            case ABDOMEN:
                paintAbdomen(g);
                break;
            case BRAIN:
                paintBrain(g);
                break;
            case BONE:
                paintBone(g);
                break;
            case DOTS:
                paintDots(g);
                break;
            case GEAR:
                paintGear(g);
                break;
            case DOCUMENT_J:
                paintDocument(g, "J");
                break;
            case DOCUMENT_B:
                paintDocument(g, "B");
                break;
            case FOLDER:
                paintFolder(g);
                break;
            case ARROW:
                paintArrow(g);
                break;
            case POLYGON:
                paintPolygon(g);
                break;
            case POLYLINE:
                paintPolyline(g);
                break;
            case CLOSED_POLYLINE:
                paintClosedPolyline(g);
                break;
            case CIRCLE:
                g.draw(new Ellipse2D.Double(4.0, 4.0, 16.0, 16.0));
                break;
            case RECT:
                g.draw(new Rectangle2D.Double(4.0, 6.0, 16.0, 12.0));
                break;
            case TEXT:
                g.draw(new Line2D.Double(6.0, 5.0, 18.0, 5.0));
                g.draw(new Line2D.Double(12.0, 5.0, 12.0, 19.0));
                break;
            case CLEAR:
                paintClear(g);
                break;
            default:
                break;
        }
    }

    private static void paintContrast(Graphics2D g) {
        g.draw(new Ellipse2D.Double(9.0, 9.0, 6.0, 6.0));
        for (int index = 0; index < 8; index++) {
            double angle = Math.PI / 4.0 * index;
            double startX = 12.0 + Math.cos(angle) * 8.0;
            double startY = 12.0 + Math.sin(angle) * 8.0;
            double endX = 12.0 + Math.cos(angle) * 10.5;
            double endY = 12.0 + Math.sin(angle) * 10.5;
            g.draw(new Line2D.Double(startX, startY, endX, endY));
        }
    }

    private static void paintMove(Graphics2D g) {
        g.draw(new Line2D.Double(12.0, 3.0, 12.0, 21.0));
        g.draw(new Line2D.Double(3.0, 12.0, 21.0, 12.0));
        g.draw(new Line2D.Double(12.0, 3.0, 9.0, 6.0));
        g.draw(new Line2D.Double(12.0, 3.0, 15.0, 6.0));
        g.draw(new Line2D.Double(12.0, 21.0, 9.0, 18.0));
        g.draw(new Line2D.Double(12.0, 21.0, 15.0, 18.0));
        g.draw(new Line2D.Double(3.0, 12.0, 6.0, 9.0));
        g.draw(new Line2D.Double(3.0, 12.0, 6.0, 15.0));
        g.draw(new Line2D.Double(21.0, 12.0, 18.0, 9.0));
        g.draw(new Line2D.Double(21.0, 12.0, 18.0, 15.0));
    }

    private static void paintCrosshair(Graphics2D g) {
        g.draw(new Line2D.Double(12.0, 2.0, 12.0, 9.0));
        g.draw(new Line2D.Double(12.0, 15.0, 12.0, 22.0));
        g.draw(new Line2D.Double(2.0, 12.0, 9.0, 12.0));
        g.draw(new Line2D.Double(15.0, 12.0, 22.0, 12.0));
        g.draw(new Ellipse2D.Double(9.5, 9.5, 5.0, 5.0));
    }

    private static void paintReset(Graphics2D g) {
        g.draw(new Arc2D.Double(4.0, 4.0, 16.0, 16.0, 45.0, 300.0, Arc2D.OPEN));
        g.draw(new Line2D.Double(18.5, 4.5, 20.0, 9.5));
        g.draw(new Line2D.Double(18.5, 4.5, 14.0, 3.5));
    }

    private static void paintLineMeasurement(Graphics2D g) {
        g.draw(new Line2D.Double(4.0, 20.0, 20.0, 4.0));
        g.draw(new Line2D.Double(4.0, 20.0, 4.0, 16.0));
        g.draw(new Line2D.Double(4.0, 20.0, 8.0, 20.0));
        g.draw(new Line2D.Double(20.0, 4.0, 16.0, 4.0));
        g.draw(new Line2D.Double(20.0, 4.0, 20.0, 8.0));
    }

    private static void paintAngle(Graphics2D g) {
        g.draw(new Line2D.Double(4.0, 20.0, 20.0, 20.0));
        g.draw(new Line2D.Double(4.0, 20.0, 20.0, 5.0));
        g.draw(new Arc2D.Double(-2.0, 14.0, 12.0, 12.0, 0.0, 41.0, Arc2D.OPEN));
    }

    private static void paintSave(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(4.0, 4.0, 16.0, 16.0, 2.0, 2.0));
        g.draw(new Rectangle2D.Double(8.0, 4.0, 7.0, 5.0));
        g.draw(new Rectangle2D.Double(7.0, 13.0, 10.0, 7.0));
    }

    private static void paintPen(Graphics2D g) {
        Path2D pen = new Path2D.Double();
        pen.moveTo(5.0, 19.0);
        pen.lineTo(7.0, 13.0);
        pen.lineTo(16.0, 4.0);
        pen.lineTo(20.0, 8.0);
        pen.lineTo(11.0, 17.0);
        pen.closePath();
        g.draw(pen);
        g.draw(new Line2D.Double(7.0, 13.0, 11.0, 17.0));
        g.draw(new Line2D.Double(16.0, 4.0, 20.0, 8.0));
    }

    private static void paintLung(Graphics2D g) {
        g.draw(new Ellipse2D.Double(4.5, 4.5, 6.0, 15.0));
        g.draw(new Ellipse2D.Double(13.5, 4.5, 6.0, 15.0));
        g.draw(new Line2D.Double(12.0, 4.0, 12.0, 10.0));
    }

    private static void paintMediastinum(Graphics2D g) {
        g.draw(new RoundRectangle2D.Double(10.0, 3.0, 4.0, 18.0, 2.0, 2.0));
        g.draw(new Ellipse2D.Double(4.5, 5.0, 4.0, 13.0));
        g.draw(new Ellipse2D.Double(15.5, 5.0, 4.0, 13.0));
    }

    private static void paintAbdomen(Graphics2D g) {
        g.draw(new Ellipse2D.Double(4.0, 4.0, 16.0, 16.0));
        g.draw(new Arc2D.Double(6.0, 4.0, 12.0, 12.0, 200.0, 140.0, Arc2D.OPEN));
        g.draw(new Arc2D.Double(6.0, 8.0, 12.0, 12.0, 200.0, 140.0, Arc2D.OPEN));
    }

    private static void paintBrain(Graphics2D g) {
        g.draw(new Ellipse2D.Double(4.0, 4.0, 16.0, 16.0));
        Path2D upper = new Path2D.Double();
        upper.moveTo(7.0, 9.0);
        upper.quadTo(9.0, 7.0, 11.0, 9.0);
        upper.quadTo(13.0, 11.0, 15.0, 9.0);
        upper.quadTo(17.0, 7.0, 18.0, 9.0);
        g.draw(upper);
        Path2D lower = new Path2D.Double();
        lower.moveTo(7.0, 14.0);
        lower.quadTo(9.0, 12.0, 11.0, 14.0);
        lower.quadTo(13.0, 16.0, 15.0, 14.0);
        lower.quadTo(17.0, 12.0, 18.0, 14.0);
        g.draw(lower);
    }

    private static void paintBone(Graphics2D g) {
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(7.0, 17.0, 17.0, 7.0));
        g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(3.5, 16.5, 5.0, 5.0));
        g.draw(new Ellipse2D.Double(15.5, 2.5, 5.0, 5.0));
    }

    private static void paintDots(Graphics2D g) {
        g.fill(new Ellipse2D.Double(4.5, 10.5, 3.5, 3.5));
        g.fill(new Ellipse2D.Double(10.5, 10.5, 3.5, 3.5));
        g.fill(new Ellipse2D.Double(16.5, 10.5, 3.5, 3.5));
    }

    private static void paintGear(Graphics2D g) {
        g.draw(new Ellipse2D.Double(7.0, 7.0, 10.0, 10.0));
        g.draw(new Ellipse2D.Double(10.0, 10.0, 4.0, 4.0));
        for (int index = 0; index < 8; index++) {
            double angle = Math.PI / 4.0 * index;
            double startX = 12.0 + Math.cos(angle) * 6.0;
            double startY = 12.0 + Math.sin(angle) * 6.0;
            double endX = 12.0 + Math.cos(angle) * 9.0;
            double endY = 12.0 + Math.sin(angle) * 9.0;
            g.draw(new Line2D.Double(startX, startY, endX, endY));
        }
    }

    private static void paintDocument(Graphics2D g, String letter) {
        Path2D page = new Path2D.Double();
        page.moveTo(5.0, 3.0);
        page.lineTo(15.0, 3.0);
        page.lineTo(19.0, 7.0);
        page.lineTo(19.0, 21.0);
        page.lineTo(5.0, 21.0);
        page.closePath();
        g.draw(page);
        g.draw(new Line2D.Double(15.0, 3.0, 15.0, 7.0));
        g.draw(new Line2D.Double(15.0, 7.0, 19.0, 7.0));
        g.setFont(new Font("Dialog", Font.BOLD, 9));
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(letter, 12.0f - metrics.stringWidth(letter) / 2.0f, 17.5f);
    }

    private static void paintFolder(Graphics2D g) {
        Path2D folder = new Path2D.Double();
        folder.moveTo(3.0, 7.0);
        folder.lineTo(9.0, 7.0);
        folder.lineTo(11.0, 9.0);
        folder.lineTo(21.0, 9.0);
        folder.lineTo(21.0, 19.0);
        folder.lineTo(3.0, 19.0);
        folder.closePath();
        g.draw(folder);
        g.draw(new Line2D.Double(3.0, 10.0, 21.0, 10.0));
    }

    private static void paintArrow(Graphics2D g) {
        g.draw(new Line2D.Double(4.0, 20.0, 19.0, 5.0));
        g.draw(new Line2D.Double(19.0, 5.0, 18.0, 11.0));
        g.draw(new Line2D.Double(19.0, 5.0, 13.0, 6.0));
    }

    private static void paintPolygon(Graphics2D g) {
        Path2D polygon = new Path2D.Double();
        polygon.moveTo(12.0, 3.0);
        polygon.lineTo(20.0, 9.0);
        polygon.lineTo(17.0, 19.0);
        polygon.lineTo(7.0, 19.0);
        polygon.lineTo(4.0, 9.0);
        polygon.closePath();
        g.draw(polygon);
    }

    private static void paintPolyline(Graphics2D g) {
        Path2D polyline = new Path2D.Double();
        polyline.moveTo(4.0, 18.0);
        polyline.lineTo(8.0, 10.0);
        polyline.lineTo(13.0, 15.0);
        polyline.lineTo(20.0, 5.0);
        g.draw(polyline);
        g.fill(new Ellipse2D.Double(2.8, 16.8, 2.4, 2.4));
        g.fill(new Ellipse2D.Double(6.8, 8.8, 2.4, 2.4));
        g.fill(new Ellipse2D.Double(11.8, 13.8, 2.4, 2.4));
        g.fill(new Ellipse2D.Double(18.8, 3.8, 2.4, 2.4));
    }

    private static void paintClosedPolyline(Graphics2D g) {
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(12.0, 4.0);
        triangle.lineTo(20.0, 19.0);
        triangle.lineTo(4.0, 19.0);
        triangle.closePath();
        g.draw(triangle);
    }

    private static void paintClear(Graphics2D g) {
        g.draw(new Line2D.Double(4.0, 6.0, 20.0, 6.0));
        g.draw(new Line2D.Double(9.0, 6.0, 9.0, 4.0));
        g.draw(new Line2D.Double(9.0, 4.0, 15.0, 4.0));
        g.draw(new Line2D.Double(15.0, 4.0, 15.0, 6.0));
        Path2D body = new Path2D.Double();
        body.moveTo(6.0, 6.0);
        body.lineTo(7.5, 20.0);
        body.lineTo(16.5, 20.0);
        body.lineTo(18.0, 6.0);
        g.draw(body);
        g.draw(new Line2D.Double(10.0, 9.0, 10.0, 17.0));
        g.draw(new Line2D.Double(14.0, 9.0, 14.0, 17.0));
    }
}
