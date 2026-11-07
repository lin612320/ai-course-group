package stud.g01.gui;

import stud.g01.problem.npuzzle.PuzzleBoard;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * 【第四阶段·可视化】只干一件事：<b>把一块 N 数码盘面画出来</b>。
 *
 * <p>这是 Swing 里最经典的"自定义绘制"写法：继承 {@link JPanel}，
 * 重写 {@link #paintComponent(Graphics)}，用 Graphics2D 自己画。
 * 面板本身<b>不保存任何动画状态</b>（不知道第几帧、也不管播放暂停），
 * 那些都由 {@link PuzzleVisualizer} 负责——职责分离，两边都简单。</p>
 *
 * <h3>它画的三种格子</h3>
 * <ul>
 *   <li><b>空格(0)</b>：画成深色的空槽，和将牌明显区分；</li>
 *   <li><b>已归位的将牌</b>：绿色系渐变（位置与目标盘面一致）；</li>
 *   <li><b>未归位的将牌</b>：蓝色系渐变。</li>
 * </ul>
 * 另外，<b>刚刚移动过来的那块将牌</b>会额外描一圈琥珀色边框，
 * 让观众一眼看清"这一步动了哪一块"。</p>
 *
 * <h3>为什么要自己算坐标，而不是用布局管理器？</h3>
 * <p>因为棋盘要<b>随窗口大小自动缩放</b>：每次重绘时，先取面板宽高中较小的那个，
 * 减掉内边距，再除以阶数，就得到当前这一帧的格子边长。
 * 这样拖大窗口时棋盘会自动变大，而且永远保持正方形、居中显示。</p>
 */
@SuppressWarnings("serial")   // Swing 组件都实现了 Serializable，但我们从不序列化窗口，不需要 serialVersionUID
public final class BoardPanel extends JPanel {

    /** 画布四周留白（像素），避免棋盘贴着窗口边缘 */
    private static final int PADDING = 14;

    /** 每块将牌之间留的缝隙（像素），形成"格子"的视觉效果 */
    private static final int GAP = 4;

    // ===================== 配色方案（深色主题） =====================

    /** 画布背景色 */
    private static final Color BG = new Color(0x1B2130);

    /** 空格（槽位）的填充色 */
    private static final Color BLANK_FILL = new Color(0x12161F);

    /** 未归位将牌的渐变色（上浅下深，做出立体感） */
    private static final Color TILE_TOP = new Color(0x5FA8E8);
    private static final Color TILE_BOTTOM = new Color(0x3A72B8);

    /** 已归位将牌的渐变色（绿色系，一眼看出"对了几块"） */
    private static final Color OK_TOP = new Color(0x59C98B);
    private static final Color OK_BOTTOM = new Color(0x2F9A63);

    /** 将牌上的数字颜色 */
    private static final Color TILE_TEXT = new Color(0xF2F6FF);

    /** 刚移动过来的那块的描边色 */
    private static final Color HIGHLIGHT = new Color(0xFFC857);

    // ===================== 当前要显示的内容 =====================

    /** 当前帧要绘制的盘面 */
    private PuzzleBoard board;

    /** 目标盘面，用来判断每块将牌是否已经归位；可以为 null（那就全部按未归位配色画） */
    private PuzzleBoard goal;

    /** 需要高亮的格子下标（-1 表示不高亮） */
    private int highlighted = -1;

    /**
     * 构造一个空面板。初始时没有盘面可画，{@code paintComponent} 会直接返回。
     */
    public BoardPanel() {
        setBackground(BG);
        // 给一个合适的首选尺寸；窗口 pack() 时会用到。
        // 但注意：真正绘制时是按"当前面板实际大小"动态算格子边长的，
        // 所以窗口缩放时棋盘会自动跟着缩放，不受这个值限制。
        setPreferredSize(new Dimension(540, 540));
    }

    /**
     * 设置当前要显示的帧。
     *
     * @param board       本帧的盘面
     * @param goal        目标盘面（用于把已归位的将牌画成绿色），可为 null
     * @param highlighted 需要高亮的格子下标（刚刚移动过来的位置），-1 表示不高亮
     */
    public void setFrame(PuzzleBoard board, PuzzleBoard goal, int highlighted) {
        this.board = board;
        this.goal = goal;
        this.highlighted = highlighted;
        // ★ Swing 规矩：改了要显示的数据后，必须调用 repaint() 请求重绘，
        //   系统会在合适的时机回调 paintComponent()。
        repaint();
    }

    /**
     * Swing 的绘制回调。窗口首次显示、被遮挡后重现、被 resize、或调用 repaint() 时都会进来。
     *
     * <p>注意：<b>不要在这个方法里修改组件状态或调用 repaint()</b>，否则会无限重绘。
     * 这里只做"读数据 → 画图"。</p>
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);                 // 先让父类用背景色填满整个面板

        if (board == null) {
            return;                              // 还没有数据，什么都不画
        }

        // Graphics2D 才有抗锯齿、渐变、粗描边这些高级能力。
        // 用 create() 拿一份副本，用完 dispose()，避免污染调用方的 Graphics 状态。
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // 打开抗锯齿：圆形/斜线/文字边缘才不会出现明显的锯齿
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int n = board.size();                                  // 阶数（3 或 4）

            // ---- 第 1 步：根据面板当前尺寸，算出格子边长和棋盘左上角坐标 ----
            int usable = Math.min(getWidth(), getHeight()) - 2 * PADDING;
            if (usable <= 0) {
                return;                                            // 窗口被拖得太小，先不画
            }
            int cell = usable / n;                                 // 每格边长（向下取整）
            int boardPx = cell * n;                                // 棋盘的像素边长（正方形）
            int x0 = (getWidth() - boardPx) / 2;                   // 居中
            int y0 = (getHeight() - boardPx) / 2;

            // ---- 第 2 步：逐格绘制 ----
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    int value = board.tileAt(r, c);
                    int idx = r * n + c;                           // 该格在一维数组中的下标

                    // 该格在画布上的左上角坐标
                    int x = x0 + c * cell;
                    int y = y0 + r * cell;

                    // 统一用圆角矩形：内缩 GAP/2，让相邻格子之间自然留出缝隙
                    float rx = x + GAP / 2.0f;
                    float ry = y + GAP / 2.0f;
                    float rw = cell - GAP;
                    float rh = cell - GAP;
                    RoundRectangle2D shape = new RoundRectangle2D.Float(rx, ry, rw, rh, 14, 14);

                    if (value == 0) {
                        // ---------- 空格：画成深色空槽 ----------
                        g2.setColor(BLANK_FILL);
                        g2.fill(shape);
                    } else {
                        // ---------- 将牌：根据"是否已在目标位置"选配色 ----------
                        boolean correct = (goal != null) && (goal.tileAt(r, c) == value);
                        Color top = correct ? OK_TOP : TILE_TOP;
                        Color bottom = correct ? OK_BOTTOM : TILE_BOTTOM;

                        // 竖直方向的线性渐变：从格子顶部到底部
                        g2.setPaint(new GradientPaint(rx, ry, top, rx, ry + rh, bottom));
                        g2.fill(shape);

                        // 数字：字号随格子大小缩放（0.42 倍边长比较协调）
                        g2.setColor(TILE_TEXT);
                        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, (int) (cell * 0.42))));

                        String text = String.valueOf(value);
                        FontMetrics fm = g2.getFontMetrics();
                        // 把文字摆到格子正中央：
                        //   水平：格子宽 - 文字宽，再取一半
                        //   垂直：格子高 - 字体行高，再取一半，最后加 ascent（基线位置）
                        int tx = (int) (rx + (rw - fm.stringWidth(text)) / 2);
                        int ty = (int) (ry + (rh - fm.getHeight()) / 2) + fm.getAscent();
                        g2.drawString(text, tx, ty);
                    }

                    // ---------- 高亮：刚移动过来的那一块 ----------
                    if (idx == highlighted) {
                        g2.setColor(HIGHLIGHT);
                        g2.setStroke(new BasicStroke(3.5f));
                        // 在格子外侧再画一个圆角框，比填充更容易被注意到
                        g2.draw(new RoundRectangle2D.Float(rx, ry, rw, rh, 14, 14));
                    }
                }
            }
        } finally {
            g2.dispose();                        // 释放副本，归还绘图资源
        }
    }
}
