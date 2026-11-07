package stud.g01.gui;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.runner.DataFiles;
import stud.g01.solver.IdAStar;
import stud.queue.StackFrontier;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

/**
 * 【第四阶段】N 数码求解过程的<b>可视化窗口</b>。
 *
 * <p>本类负责"动画"这件事：把解路径拆成一串盘面（每一帧一张），
 * 用 {@link javax.swing.Timer} 定时逐帧推进，交给 {@link BoardPanel} 画出来。
 * 它<b>不负责搜索、也不负责画格子</b>——搜索由框架的 A* 完成，画格子由 BoardPanel 完成。</p>
 *
 * <h3>怎么用？两种方式</h3>
 *
 * <h4>方式一：独立运行（推荐，演示最干净）</h4>
 * <pre>
 * 参数与 SearchTester 前 4 位完全一致，另可追加两个可选参数：
 *   java stud.g01.gui.PuzzleVisualizer &lt;数据文件&gt; NPUZZLE &lt;阶段&gt; &lt;Feeder类名&gt; [启发函数] [实例范围]
 *
 * 实例范围可写： 3（只演示第 3 个） / 2-6（依次演示第 2~6 个） / all（全部）
 *
 * 例 1（阶段二演示：4 阶 15 数码，依次播放 7 个实例里的前 6 个）：
 *   java -cp out/production/WattAi stud.g01.gui.PuzzleVisualizer resources/problems_stage2.txt NPUZZLE 2 stud.g01.runner.PuzzleFeeder MANHATTAN 1-6
 *
 * 例 2（单个实例：problems.txt 里第 4 个）：
 *   java -cp out/production/WattAi stud.g01.gui.PuzzleVisualizer resources/problems.txt NPUZZLE 1 stud.g01.runner.PuzzleFeeder MANHATTAN 4
 * </pre>
 * <p>批量演示时，<b>关掉当前窗口会自动弹出下一个</b>，全部放完后控制台打印一张汇总表。</p>
 *
 * <h4>方式二：让 SearchTester 直接弹窗</h4>
 * <p>给 JVM 加上系统属性 {@code -Dnpuzzle.gui=true}，那么
 * {@code NPuzzleProblem.showSolution(...)} 就会改为弹窗展示（而不是打印字符画）。
 * 注意：SearchTester 会遍历"每个实例 × 每个启发函数"，所以窗口是<b>一个接一个</b>弹出的，
 * 必须关掉当前这个才会继续跑下一个（{@link #showAndWait} 会阻塞）。</p>
 *
 * <h3>操作方式</h3>
 * <ul>
 *   <li>鼠标：点按钮「上一步 / 播放 / 下一步 / 到末尾 / 重置」</li>
 *   <li>键盘：<b>空格</b>=播放/暂停，<b>← →</b>=单步，<b>Home</b>=重置，<b>End</b>=跳到末尾</li>
 *   <li>滑块：拖动调节播放速度</li>
 * </ul>
 */
@SuppressWarnings("serial")   // 同上：Swing 窗口不参与序列化
public final class PuzzleVisualizer extends JFrame {

    /** 系统属性名：设置 {@code -Dnpuzzle.gui=true} 可让 showSolution 改为弹窗 */
    public static final String GUI_PROPERTY = "npuzzle.gui";

    /** 最快的每帧间隔（毫秒） */
    private static final int DELAY_FAST = 60;

    /** 最慢的每帧间隔（毫秒） */
    private static final int DELAY_SLOW = 900;

    // ===================== 数据 =====================

    /** 动画的每一帧：frames[0] 是初始盘面，frames[k] 是第 k 步之后的盘面 */
    private final List<PuzzleBoard> frames;

    /** 与 frames 一一对应的文字说明 */
    private final List<String> captions;

    /** 目标盘面（用于把已归位的将牌画成绿色），可为 null */
    private final PuzzleBoard goal;

    /** 当前正在显示第几帧 */
    private int index;

    // ===================== 动画驱动 =====================

    /**
     * Swing 定时器。它在 <b>EDT（事件分发线程）</b> 上按固定间隔触发 actionPerformed，
     * 所以用它推进动画天然是线程安全的——不需要自己开线程、也不需要加锁。
     */
    private final Timer timer;

    // ===================== 界面组件 =====================

    /** 画盘面的面板（见 BoardPanel） */
    private final BoardPanel boardPanel = new BoardPanel();

    /** 顶部：实验数据摘要 */
    private final JLabel infoLabel = new JLabel(" ", SwingConstants.CENTER);

    /** 顶部：当前这一步的说明 */
    private final JLabel captionLabel = new JLabel(" ", SwingConstants.CENTER);

    /** 底部：第几帧 / 共几帧 */
    private final JLabel progressLabel = new JLabel(" ", SwingConstants.CENTER);

    /** 播放/暂停按钮（文字会随状态切换） */
    private final JButton playButton = new JButton();

    /** 速度滑块：0 = 最慢，100 = 最快。默认偏快一点，方便演示 15 数码那种几十步的长解路径 */
    private final JSlider speedSlider = new JSlider(0, 100, 70);

    /**
     * 构造窗口。注意：Swing 组件只能在 EDT 上创建和操作，
     * 所以外部不要直接 new 本类，请用 {@link #showAndWait}。
     *
     * @param frames   动画帧序列（至少 1 帧）
     * @param captions 与帧对应的说明文字
     * @param goal     目标盘面，可为 null
     * @param info     显示在窗口顶部的一行摘要
     */
    private PuzzleVisualizer(List<PuzzleBoard> frames, List<String> captions, PuzzleBoard goal, String info) {
        super("N 数码求解可视化 —— 项目一 第四阶段");

        this.frames = frames;
        this.captions = captions;
        this.goal = goal;
        this.index = 0;

        // 关闭窗口时"销毁"而不是"隐藏"，
        // 这样 WindowListener 才能收到 windowClosed 事件（showAndWait 靠它解除阻塞）。
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 6));

        add(buildHeader(info), BorderLayout.NORTH);
        add(boardPanel, BorderLayout.CENTER);
        add(buildControls(), BorderLayout.SOUTH);

        // ★ 定时器必须先于第一次 update() 创建（update 里会用 stopTimer()）
        this.timer = new Timer(delayFromSlider(), e -> advance());

        // 拖动滑块时实时改变播放速度
        speedSlider.addChangeListener(e -> timer.setDelay(delayFromSlider()));

        bindKeys();
        update();                                    // 先渲染第 0 帧

        setMinimumSize(new Dimension(430, 500));
        pack();                                      // 按各组件首选尺寸自动确定窗口大小
        setLocationRelativeTo(null);                 // 传 null = 在屏幕正中显示
    }

    // =====================================================================
    //  一、界面搭建
    // =====================================================================

    /**
     * 顶部信息区：一行实验数据摘要 + 一行当前步骤说明。
     * 用 BoxLayout 的纵向排列，让两行标签各自居中。
     */
    private JPanel buildHeader(String info) {
        infoLabel.setText(info);
        infoLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        infoLabel.setForeground(new Color(0x5A6B85));
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        captionLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        captionLabel.setForeground(new Color(0x1D2A3A));
        captionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        panel.add(infoLabel);
        panel.add(Box.createVerticalStrut(5));       // 两行之间留点空隙
        panel.add(captionLabel);
        return panel;
    }

    /**
     * 底部控制区：进度文字 + 一排按钮 + 一行速度滑块。
     */
    private JPanel buildControls() {
        progressLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        progressLabel.setForeground(new Color(0x5A6B85));
        progressLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // ---- 按钮行 ----
        JButton prevButton = new JButton("◀ 上一步");
        JButton nextButton = new JButton("下一步 ▶");
        JButton lastButton = new JButton("⏭ 到末尾");
        JButton resetButton = new JButton("↺ 重置");

        prevButton.addActionListener(e -> stepBack());
        playButton.addActionListener(e -> togglePlay());
        nextButton.addActionListener(e -> stepForward());
        lastButton.addActionListener(e -> jumpToEnd());
        resetButton.addActionListener(e -> reset());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        buttonRow.add(prevButton);
        buttonRow.add(playButton);
        buttonRow.add(nextButton);
        buttonRow.add(lastButton);
        buttonRow.add(resetButton);
        buttonRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        // ---- 速度行 ----
        speedSlider.setPreferredSize(new Dimension(170, speedSlider.getPreferredSize().height));
        speedSlider.setToolTipText("拖动调节播放速度（每帧停留时间 60ms ~ 900ms）");

        JPanel speedRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        speedRow.add(new JLabel("慢"));
        speedRow.add(speedSlider);
        speedRow.add(new JLabel("快"));
        speedRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 12, 10, 12));
        panel.add(progressLabel);
        panel.add(Box.createVerticalStrut(6));
        panel.add(buttonRow);
        panel.add(Box.createVerticalStrut(4));
        panel.add(speedRow);
        return panel;
    }

    /**
     * 绑定键盘快捷键。
     *
     * <p>用 Swing 的 InputMap/ActionMap 机制（而不是 KeyListener）：
     * {@code WHEN_IN_FOCUSED_WINDOW} 表示只要窗口是活动窗口就响应，
     * 不要求某个具体组件获得焦点，而且不受按钮焦点切换影响。</p>
     */
    private void bindKeys() {
        bind("SPACE", this::togglePlay);   // 空格：播放 / 暂停
        bind("RIGHT", this::stepForward);  // 右方向键：下一步
        bind("LEFT", this::stepBack);      // 左方向键：上一步
        bind("HOME", this::reset);         // Home：回到初始状态
        bind("END", this::jumpToEnd);      // End：跳到目标状态
    }

    /**
     * 把一个按键和一段动作绑起来。
     *
     * @param key    按键名（如 "SPACE"、"LEFT"）
     * @param action 按下时要执行的动作
     */
    private void bind(String key, Runnable action) {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
        root.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // =====================================================================
    //  二、动画控制
    // =====================================================================

    /**
     * 滑块位置 → 每帧间隔（毫秒）。
     * 滑块值 0（最左）对应最慢，100（最右）对应最快，所以这里做了反向映射。
     *
     * @return 每帧间隔毫秒数
     */
    private int delayFromSlider() {
        int v = speedSlider.getValue();                                  // 0 ~ 100
        return DELAY_SLOW - (DELAY_SLOW - DELAY_FAST) * v / 100;
    }

    /** 播放 / 暂停切换 */
    private void togglePlay() {
        if (timer.isRunning()) {
            stopTimer();
        } else {
            startTimer();
        }
    }

    /** 开始播放。如果已经播到最后一帧，则从头再来一遍。 */
    private void startTimer() {
        if (index >= frames.size() - 1) {
            index = 0;
            update();
        }
        timer.setDelay(delayFromSlider());
        timer.start();
        playButton.setText("⏸  暂停");
    }

    /** 停止播放，并把按钮文字恢复成"播放" */
    private void stopTimer() {
        timer.stop();
        playButton.setText("▶  播放");
    }

    /**
     * 前进一帧。到达最后一帧后自动停止（不会循环，方便停下来观察终态）。
     */
    private void advance() {
        if (index < frames.size() - 1) {
            index++;
            update();
        } else {
            stopTimer();
        }
    }

    /** 后退一帧（并暂停） */
    private void stepBack() {
        stopTimer();
        if (index > 0) {
            index--;
            update();
        }
    }

    /** 前进一帧（并暂停） */
    private void stepForward() {
        stopTimer();
        advance();
    }

    /** 回到初始状态 */
    private void reset() {
        stopTimer();
        index = 0;
        update();
    }

    /** 直接跳到最后一帧（目标状态） */
    private void jumpToEnd() {
        stopTimer();
        index = frames.size() - 1;
        update();
    }

    /**
     * 把当前帧刷新到界面上。
     * 三个按钮/标签的文字、以及画布内容都在这里统一更新。
     */
    private void update() {
        PuzzleBoard current = frames.get(index);

        // 找出"刚刚滑过来的那一块将牌"，用来画高亮边框：
        // 上一帧是空格、这一帧是将牌的那个位置，就是它落点。
        int highlight = (index > 0) ? movedTileIndex(frames.get(index - 1), current) : -1;

        boardPanel.setFrame(current, goal, highlight);
        captionLabel.setText(captions.get(index));
        progressLabel.setText("第 " + (index + 1) + " / " + frames.size() + " 帧");

        if (index >= frames.size() - 1) {
            stopTimer();                    // 已经在终态，把"暂停/播放"状态复位
        }
    }

    /**
     * 比较相邻两帧，找出刚刚移动过来的那块将牌所在的下标。
     *
     * @param prev 上一帧
     * @param cur  当前帧
     * @return 落点下标；找不到返回 -1
     */
    private static int movedTileIndex(PuzzleBoard prev, PuzzleBoard cur) {
        int n = cur.size();
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (prev.tileAt(r, c) == 0 && cur.tileAt(r, c) != 0) {
                    return r * n + c;       // 这一格"由空变满"，就是新滑进来的将牌
                }
            }
        }
        return -1;
    }

    // =====================================================================
    //  三、对外入口
    // =====================================================================

    /**
     * 打开窗口并<b>阻塞</b>当前线程，直到用户把窗口关掉。
     *
     * <p>为什么用 invokeLater + CountDownLatch 而不是 invokeAndWait？<br>
     * invokeAndWait 只等"窗口显示完成"就返回了，用户还没看呢。
     * 这里用一个倒计时门闩（CountDownLatch）挂住调用线程，
     * 由窗口的 windowClosed 事件来解锁，从而实现"关掉窗口才继续"。</p>
     *
     * @param frames   动画帧序列
     * @param captions 与帧对应的说明
     * @param goal     目标盘面，可为 null
     * @param info     顶部摘要文字
     */
    public static void showAndWait(List<PuzzleBoard> frames, List<String> captions,
                                   PuzzleBoard goal, String info) {
        if (GraphicsEnvironment.isHeadless()) {
            // 没有图形环境（例如在服务器/某些沙箱里运行），优雅降级而不是抛异常崩溃
            System.out.println("[提示] 当前环境没有图形界面（headless），本次跳过窗口展示。");
            return;
        }

        CountDownLatch closed = new CountDownLatch(1);

        // 所有 Swing 组件的创建与显示都必须放在 EDT 上执行
        SwingUtilities.invokeLater(() -> {
            PuzzleVisualizer window = new PuzzleVisualizer(frames, captions, goal, info);
            window.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    closed.countDown();     // 窗口被关闭 → 放行调用线程
                }
            });
            window.setVisible(true);
        });

        try {
            closed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // 恢复中断标记，这是处理中断的标准做法
        }
    }

    // =====================================================================
    //  四、独立启动入口（演示用）
    // =====================================================================

    /**
     * 独立运行入口：搜索实例并把解路径用窗口动画播放出来。
     *
     * <p>支持三种"实例范围"写法（第 6 个参数）：</p>
     * <ul>
     *   <li>{@code 3} —— 只演示第 3 个实例；</li>
     *   <li>{@code 2-6} —— 依次演示第 2 到第 6 个实例（<b>阶段二演示推荐</b>）；</li>
     *   <li>{@code all} —— 演示文件里的全部实例。</li>
     * </ul>
     * <p>批量演示时，<b>关掉当前窗口就会自动弹出下一个</b>，全部放完后控制台打印一张汇总表。</p>
     *
     * @param args 见类注释中的"方式一"
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            printUsage();
            return;
        }

        String dataFile = args[0];                                    // 数据文件
        String feederClass = args[3];                                 // Feeder 全限定名

        // ---- 从第 5 个参数开始可以任意顺序给：启发函数名 / 实例范围 / --ida ----
        HeuristicType heuristic = HeuristicType.MANHATTAN;            // 默认用曼哈顿距离
        String rangeArg = "1";                                        // 默认演示第 1 个实例
        boolean useIdaStar = false;                                   // 默认用框架的 A*

        for (int i = 4; i < args.length; i++) {
            String arg = args[i];
            if (arg.equalsIgnoreCase("--ida")) {
                useIdaStar = true;                                    // 改用 IDA*（阶段三需要）
            } else if (isHeuristicName(arg)) {
                heuristic = HeuristicType.valueOf(arg.toUpperCase());
            } else {
                rangeArg = arg;                                       // 其余当作实例范围
            }
        }

        // ---- 1) 用和 SearchTester 完全一样的方式（反射）创建 Feeder ----
        EngineFeeder feeder = (EngineFeeder)
                Class.forName(feederClass).getDeclaredConstructor().newInstance();

        // ---- 2) 读入数据文件的全部行 ----
        //     ⚠️ 相对路径是相对"工作目录"解析的。IntelliJ 的运行配置如果没设 Working directory，
        //     默认是工程根目录，而数据文件在 WattAi/resources 下，
        //     直接 new File("resources/xxx.txt") 会报 FileNotFoundException。
        //     DataFiles.resolve 会自动尝试几个候选位置，彻底找不到时给出可操作的提示。
        ArrayList<String> lines = new ArrayList<>();
        File source;
        try {
            source = DataFiles.resolve(dataFile);
        } catch (FileNotFoundException ex) {
            System.out.println(ex.getMessage());
            return;
        }
        try (Scanner scanner = new Scanner(source)) {
            while (scanner.hasNext()) {
                lines.add(scanner.nextLine());
            }
        }

        // ---- 3) 解析成问题实例 ----
        ArrayList<Problem> problems = feeder.getProblems(lines);
        if (problems.isEmpty()) {
            System.out.println("输入文件里没有问题实例：" + dataFile);
            return;
        }

        // ---- 4) 解析实例范围："3" / "2-6" / "all" ----
        int from;
        int to;
        if (rangeArg.equalsIgnoreCase("all") || rangeArg.equals("0")) {
            from = 0;
            to = problems.size() - 1;
        } else if (rangeArg.contains("-")) {
            String[] parts = rangeArg.split("-");
            from = Integer.parseInt(parts[0].trim()) - 1;
            to = Integer.parseInt(parts[1].trim()) - 1;
        } else {
            from = Integer.parseInt(rangeArg.trim()) - 1;
            to = from;
        }
        if (from < 0 || to >= problems.size() || from > to) {
            System.out.println("实例范围不合法：" + rangeArg + "（文件里共 " + problems.size() + " 个实例）");
            return;
        }

        System.out.println("数据文件：" + dataFile);
        System.out.println("搜索算法：" + algorithmName(useIdaStar) + "　　启发函数：" + heuristic
                + "　　演示范围：第 " + (from + 1) + " ~ " + (to + 1) + " 个（共 " + problems.size() + " 个）");
        System.out.println("窗口操作：空格=播放/暂停，← →=单步，Home=重置，End=跳到末尾。");
        if (to > from) {
            System.out.println("关闭当前窗口会自动继续下一个实例。");
        }
        System.out.println();

        // 汇总表用
        List<String> summary = new ArrayList<>();

        for (int index = from; index <= to; index++) {
            System.out.println("---------- 实例 " + (index + 1) + " / " + problems.size() + " ----------");
            summary.add(showOne(feeder, heuristic, useIdaStar, problems.get(index), index + 1, problems.size()));
        }

        // ---- 5) 全部演示完后打印汇总表（顺便就是阶段二的性能数据）----
        if (summary.size() > 1) {
            System.out.println();
            System.out.println("==================== 演示汇总 ====================");
            for (String s : summary) {
                System.out.println(s);
            }
        }
    }

    /**
     * 求解并演示<b>一个</b>实例（阻塞到窗口被关闭）。
     *
     * @param feeder    素材工厂
     * @param heuristic 启发函数
     * @param problem   待演示的问题（必须是 NPuzzleProblem）
     * @param no        这是第几个实例（1 起算，仅用于显示）
     * @param total     文件里一共几个实例（仅用于显示）
     * @return 一行摘要，供最后的汇总表使用
     */
    /**
     * 判断一个命令行参数是不是启发函数名（用来把"启发函数"和"实例范围"区分开）。
     *
     * @param s 参数
     * @return 是启发函数名返回 true
     */
    private static boolean isHeuristicName(String s) {
        for (HeuristicType t : HeuristicType.values()) {
            if (t.name().equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param useIdaStar 是否使用 IDA*
     * @return 显示用的算法名（加权时附带权重）
     */
    private static String algorithmName(boolean useIdaStar) {
        if (!useIdaStar) {
            return "A*";
        }
        String w = System.getProperty(IdAStar.WEIGHT_PROPERTY, "1.0");
        return "1.0".equals(w) ? "IDA*" : ("IDA* (w=" + w + ")");
    }

    /**
     * 求解并演示<b>一个</b>实例（阻塞到窗口被关闭）。
     *
     * @param feeder     素材工厂
     * @param heuristic  启发函数
     * @param useIdaStar true = 用 IDA*，false = 用框架的 A*
     * @param problem    待演示的问题（必须是 NPuzzleProblem）
     * @param no         这是第几个实例（1 起算，仅用于显示）
     * @param total      文件里一共几个实例（仅用于显示）
     * @return 一行摘要，供最后的汇总表使用
     */
    private static String showOne(EngineFeeder feeder, HeuristicType heuristic, boolean useIdaStar,
                                 Problem problem, int no, int total) {
        NPuzzleProblem npuzzle = (NPuzzleProblem) problem;      // 本窗口专用于 N 数码
        int size = npuzzle.initialBoard().size();

        if (!problem.solvable()) {
            String s = String.format("实例%-3d %d 阶   无解（逆序数判定）", no, size);
            System.out.println(s);
            return s;
        }

        // ---- 选择搜索器并求解 ----
        // A*  ：框架的 BestFirstSearcher（用我们自己的 PqFrontier 当待扩展表）
        // IDA*：我们自己的 stud.g01.solver.IdAStar
        //       阶段三那个倒序实例必须用 IDA* —— 因为 A* + 模式数据库在那个实例上
        //       会把 12 GB 内存吃光仍然 OOM，而 IDA* 只占 O(路径长度) 的内存。
        //       加权通过 -Dnpuzzle.weight=1.2 传入（见 IdAStar）。
        AbstractSearcher searcher = useIdaStar
                ? new IdAStar(new StackFrontier(), feeder.getPredictor(heuristic))
                : feeder.getAStar(heuristic);
        long startNanos = System.nanoTime();
        Deque<Node> path;
        try {
            path = searcher.search(problem);
        } catch (OutOfMemoryError e) {
            // 困难实例（比如 15 数码的倒序实例）可能把内存撑爆，
            // 演示程序不该因此崩掉，捕获后跳过、继续演示下一个。
            String s = String.format("实例%-3d %d 阶   内存不足（OutOfMemoryError），跳过演示", no, size);
            System.out.println(s);
            return s;
        }
        double seconds = (System.nanoTime() - startNanos) / 1e9;

        if (path == null) {
            String s = String.format("实例%-3d %d 阶   No Solution", no, size);
            System.out.println(s);
            return s;
        }

        // ---- 把解路径拆成动画帧：第 0 帧是初始盘面，之后每走一步加一帧 ----
        List<PuzzleBoard> frames = new ArrayList<>();
        List<String> captions = new ArrayList<>();

        frames.add(npuzzle.initialBoard());
        captions.add("初始状态");

        int step = 0;
        for (Node node : path) {
            step++;
            frames.add((PuzzleBoard) node.getState());
            captions.add("第 " + step + " 步：空格向 " + node.getAction() + " 移动");
        }
        if (!path.isEmpty()) {
            int last = captions.size() - 1;
            captions.set(last, captions.get(last) + "　（到达目标状态！）");
        }

        // ---- 控制台摘要：既方便写报告，也会显示在窗口顶部 ----
        String info = "实例 " + no + " / " + total
                + "　　" + size + " 阶"
                + "　　" + algorithmName(useIdaStar) + " + " + heuristic
                + "　　解路径 " + path.size() + " 步"
                + "　　耗时 " + String.format("%.3f", seconds) + " s"
                + "　　生成 " + searcher.nodesGenerated() + " 结点"
                + " / 扩展 " + searcher.nodesExpanded() + " 结点";
        System.out.println(info);

        // ---- 弹窗播放（会阻塞到窗口关闭）----
        showAndWait(frames, captions, npuzzle.goalBoard(), info);

        return info;
    }

    /** 打印命令行用法 */
    private static void printUsage() {
        System.out.println("用法：java stud.g01.gui.PuzzleVisualizer <数据文件> NPUZZLE <阶段> <Feeder类名>"
                + " [启发函数] [实例范围] [--ida]");
        System.out.println();
        System.out.println("  启发函数：MISPLACED | MANHATTAN | DISJOINT_PATTERN，默认 MANHATTAN");
        System.out.println("  实例范围：3      = 只演示第 3 个实例");
        System.out.println("            2-6    = 依次演示第 2 ~ 6 个实例");
        System.out.println("            all    = 演示全部实例（不写则默认第 1 个）");
        System.out.println("  --ida    ：改用 IDA*（阶段三的倒序实例必须用它，A* 会内存溢出）");
        System.out.println("  加权      ：在 VM options 里加 -Dnpuzzle.weight=1.2（阶段三达标用；1.0 为最优解）");
        System.out.println();
        System.out.println("示例：");
        System.out.println("  【阶段二演示 · 4 阶，依次播放 7 个实例中的前 6 个】");
        System.out.println("    java -cp out/production/WattAi stud.g01.gui.PuzzleVisualizer"
                + " resources/problems_stage2.txt NPUZZLE 2 stud.g01.runner.PuzzleFeeder"
                + " DISJOINT_PATTERN 1-6");
        System.out.println("  【阶段三演示 · 倒序实例（70 步最优解，加权后 2.6 秒解出 72 步）】");
        System.out.println("    java -Xmx4g -Dnpuzzle.weight=1.2 -cp out/production/WattAi"
                + " stud.g01.gui.PuzzleVisualizer resources/problems_stage3.txt NPUZZLE 3"
                + " stud.g01.runner.PuzzleFeeder DISJOINT_PATTERN --ida");
    }
}
