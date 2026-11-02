package com.zlyd.mpr.ui.menu;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * MPR 右键环形菜单：透明浮层，围绕鼠标点显示一圈图标按钮。
 *
 * <p>主菜单 10 项；窗宽窗位、保存影像、测量/标记各有一圈子菜单。
 * 菜单只负责导航与界面，具体功能通过 {@link MprMenuListener} 回调给宿主。
 * 支持点击空白处或按 Esc 收起。</p>
 *
 * <p>VTK 画布是重量级 AWT Canvas，轻量 Swing 浮层会被其遮挡，因此菜单托管在
 * 无边框透明 {@link JWindow} 中显示。</p>
 */
public final class MprRingMenu extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LogManager.getLogger(MprRingMenu.class);
    public static final int MENU_WIDTH = 350;
    public static final int MENU_HEIGHT = 340;

    private final Map<MprMenuAction, RingMenuButton> mainButtons =
            new EnumMap<>(MprMenuAction.class);
    private final List<RingMenuButton> windowButtons = new ArrayList<>();
    private final List<RingMenuButton> saveButtons = new ArrayList<>();
    private final List<RingMenuButton> surveyButtons = new ArrayList<>();

    private MprMenuListener listener;
    private AWTEventListener dismissWatchdog;
    private JWindow window;

    public MprRingMenu() {
        super(null);
        setOpaque(false);
        setFocusable(true);
        setSize(MENU_WIDTH, MENU_HEIGHT);
        setPreferredSize(new Dimension(MENU_WIDTH, MENU_HEIGHT));
        buildMainMenu();
        buildWindowMenu();
        buildSaveMenu();
        buildSurveyMenu();
    }

    public void setListener(MprMenuListener listener) {
        this.listener = listener;
    }

    /**
     * 以屏幕坐标点为中心显示主菜单，并收敛到可用屏幕范围。
     *
     * @param host          宿主组件（用于确定浮层窗口的 owner）
     * @param screenLocation 鼠标点的屏幕坐标
     */
    public void showAt(Component host, Point screenLocation) {
        initWindow(host);
        showMainMenu();

        Rectangle bounds = usableScreenBounds(host);
        int maxX = Math.max(bounds.x, bounds.x + bounds.width - MENU_WIDTH);
        int maxY = Math.max(bounds.y, bounds.y + bounds.height - MENU_HEIGHT);
        int x = Math.max(bounds.x, Math.min(maxX, screenLocation.x - MENU_WIDTH / 2));
        int y = Math.max(bounds.y, Math.min(maxY, screenLocation.y - MENU_HEIGHT / 2));
        window.setLocation(x, y);
        window.setVisible(true);
        window.toFront();
        installDismissWatchdog();
        repaint();
    }

    /**
     * 收起菜单。再次右键时会重新回到主菜单。
     */
    public void hideMenu() {
        if (window != null) {
            window.setVisible(false);
        }
        uninstallDismissWatchdog();
    }

    private void initWindow(Component host) {
        if (window != null) {
            return;
        }
        Window owner = host == null ? null : SwingUtilities.getWindowAncestor(host);
        window = new JWindow(owner);
        try {
            window.setBackground(new Color(0, 0, 0, 0));
        } catch (UnsupportedOperationException e) {
            LOG.warn("当前系统不支持透明浮层，环形菜单将使用深色背景", e);
            window.setBackground(new Color(32, 36, 44));
        }
        window.setFocusableWindowState(false);
        window.setContentPane(this);
        window.setSize(MENU_WIDTH, MENU_HEIGHT);
    }

    /**
     * 宿主所在屏幕的可用区域（扣除任务栏），用于菜单边界收敛。
     */
    private static Rectangle usableScreenBounds(Component host) {
        GraphicsConfiguration configuration = host == null
                ? null : host.getGraphicsConfiguration();
        if (configuration == null) {
            return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        }
        Rectangle bounds = configuration.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        return new Rectangle(bounds.x + insets.left, bounds.y + insets.top,
                bounds.width - insets.left - insets.right,
                bounds.height - insets.top - insets.bottom);
    }

    public void showMainMenu() {
        setGroupVisible(mainButtons.values(), true);
        setGroupVisible(windowButtons, false);
        setGroupVisible(saveButtons, false);
        setGroupVisible(surveyButtons, false);
    }

    public void showWindowMenu() {
        setGroupVisible(mainButtons.values(), false);
        setGroupVisible(windowButtons, true);
        setGroupVisible(saveButtons, false);
        setGroupVisible(surveyButtons, false);
    }

    public void showSaveMenu() {
        setGroupVisible(mainButtons.values(), false);
        setGroupVisible(windowButtons, false);
        setGroupVisible(saveButtons, true);
        setGroupVisible(surveyButtons, false);
    }

    public void showSurveyMenu() {
        setGroupVisible(mainButtons.values(), false);
        setGroupVisible(windowButtons, false);
        setGroupVisible(saveButtons, false);
        setGroupVisible(surveyButtons, true);
    }

    private void buildMainMenu() {
        addMainButton(MprMenuAction.WINDOWING, 124, 69);
        addMainButton(MprMenuAction.MOVE, 176, 69);
        addMainButton(MprMenuAction.ZOOM, 81, 100);
        addMainButton(MprMenuAction.DISTANCE_MEASUREMENT, 219, 100);
        addMainButton(MprMenuAction.ORIGINAL, 65, 150);
        addMainButton(MprMenuAction.ANGLE_MEASUREMENT, 235, 150);
        addMainButton(MprMenuAction.CROSSHAIR, 81, 200);
        addMainButton(MprMenuAction.SURVEY_SIGN, 219, 200);
        addMainButton(MprMenuAction.INVERT, 124, 231);
        addMainButton(MprMenuAction.SAVE_IMAGE, 176, 231);
    }

    private void buildWindowMenu() {
        addSubButton(windowButtons, MprMenuAction.WINDOW_OTHER, 124, 7);
        addSubButton(windowButtons, MprMenuAction.WINDOW_ABDOMEN, 74, 33);
        addSubButton(windowButtons, MprMenuAction.WINDOW_ORIGINAL, 174, 33);
        addSubButton(windowButtons, MprMenuAction.WINDOW_LUNG, 54, 82);
        addSubButton(windowButtons, MprMenuAction.WINDOW_SET, 194, 82);
        addSubButton(windowButtons, MprMenuAction.WINDOW_BONE, 74, 131);
        addSubButton(windowButtons, MprMenuAction.WINDOW_BRAIN, 174, 131);
        addSubButton(windowButtons, MprMenuAction.WINDOW_MEDIASTINUM, 124, 153);
    }

    private void buildSaveMenu() {
        addSubButton(saveButtons, MprMenuAction.SAVE_JPG, 176, 99);
        addSubButton(saveButtons, MprMenuAction.SAVE_BMP, 110, 195);
        addSubButton(saveButtons, MprMenuAction.SAVE_PATH, 242, 195);
    }

    private void buildSurveyMenu() {
        addSubButton(surveyButtons, MprMenuAction.SURVEY_ARROW, 219, 130);
        addSubButton(surveyButtons, MprMenuAction.SURVEY_POLYGON, 169, 150);
        addSubButton(surveyButtons, MprMenuAction.SURVEY_CLOSED_POLYLINE, 269, 150);
        addSubButton(surveyButtons, MprMenuAction.SURVEY_POLYLINE, 149, 200);
        addSubButton(surveyButtons, MprMenuAction.SURVEY_CLEAR_ALL, 289, 200);
        addSubButton(surveyButtons, MprMenuAction.SURVEY_CIRCLE, 169, 250);
        addSubButton(surveyButtons, MprMenuAction.SURVEY_RECT, 269, 250);
        addSubButton(surveyButtons, MprMenuAction.SURVEY_TEXT, 219, 270);
    }

    private void addMainButton(MprMenuAction action, int x, int y) {
        RingMenuButton button = createButton(action, x, y);
        button.setLeftAction(() -> handleMainLeft(action));
        button.setRightAction(() -> handleMainRight(action));
        mainButtons.put(action, button);
    }

    private void addSubButton(List<RingMenuButton> group, MprMenuAction action, int x, int y) {
        RingMenuButton button = createButton(action, x, y);
        button.setAction(() -> execute(action));
        group.add(button);
    }

    private RingMenuButton createButton(MprMenuAction action, int x, int y) {
        RingMenuButton button = new RingMenuButton(action);
        button.setBounds(x, y, RingMenuButton.SIZE, RingMenuButton.SIZE);
        button.setVisible(false);
        add(button);
        return button;
    }

    /**
     * 主菜单左键：测量/标记直接展开子菜单，其余项回调业务（窗宽窗位、保存影像的左键
     * 后续接入"进入对应操作模式 / 直接保存"）。
     */
    private void handleMainLeft(MprMenuAction action) {
        if (action == MprMenuAction.SURVEY_SIGN) {
            showSurveyMenu();
            return;
        }
        execute(action);
    }

    /**
     * 主菜单右键：窗宽窗位展开预设、保存影像展开保存方式、测量/标记展开工具；其余同左键。
     */
    private void handleMainRight(MprMenuAction action) {
        switch (action) {
            case WINDOWING:
                showWindowMenu();
                break;
            case SAVE_IMAGE:
                showSaveMenu();
                break;
            case SURVEY_SIGN:
                showSurveyMenu();
                break;
            default:
                execute(action);
                break;
        }
    }

    private void execute(MprMenuAction action) {
        if (listener != null) {
            listener.onMenuAction(action);
        }
        hideMenu();
    }

    private static void setGroupVisible(Iterable<RingMenuButton> buttons, boolean visible) {
        for (RingMenuButton button : buttons) {
            button.setVisible(visible);
        }
    }

    private void installDismissWatchdog() {
        if (dismissWatchdog != null) {
            return;
        }
        dismissWatchdog = event -> {
            if (window == null || !window.isVisible()) {
                return;
            }
            if (event.getID() == MouseEvent.MOUSE_PRESSED) {
                if (!isInside((MouseEvent) event)) {
                    hideMenu();
                }
            } else if (event.getID() == KeyEvent.KEY_PRESSED
                    && ((KeyEvent) event).getKeyCode() == KeyEvent.VK_ESCAPE) {
                hideMenu();
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(dismissWatchdog,
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
    }

    private void uninstallDismissWatchdog() {
        if (dismissWatchdog == null) {
            return;
        }
        Toolkit.getDefaultToolkit().removeAWTEventListener(dismissWatchdog);
        dismissWatchdog = null;
    }

    private boolean isInside(MouseEvent event) {
        Component source = event.getComponent();
        if (source == null || !source.isShowing()) {
            return false;
        }
        Point point = SwingUtilities.convertPoint(source, event.getPoint(), this);
        return contains(point);
    }
}
