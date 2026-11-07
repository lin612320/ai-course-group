package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.Problem;
import core.problem.State;
import core.solver.queue.Node;
import stud.g01.gui.PuzzleVisualizer;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * N 数码问题（N-Puzzle）：把初始盘面通过滑动一步步变成目标盘面。
 *
 * <p>它继承框架的 {@link core.problem.Problem} 超类，需要实现 4 个抽象方法：</p>
 * <ul>
 *   <li>{@link #solvable()}   —— 当前问题有没有解（<b>本类最核心的算法</b>）；</li>
 *   <li>{@link #stepCost(State, Action)} —— 走一步的代价；</li>
 *   <li>{@link #applicable(State, Action)} —— 某个动作在当前状态下可不可行；</li>
 *   <li>{@link #showSolution(Deque)} —— 解路径的可视化。</li>
 * </ul>
 *
 * <p>另外，{@code goal(State)}（判断是否到达目标）<b>不需要重写</b>：框架的默认实现是
 * {@code state.equals(goal)}，只要 {@link PuzzleBoard} 正确实现了 equals 就够了。</p>
 *
 * <p>状态的搜索树展开（生成后继结点、计算 g/h、构造 Node）全部由框架的
 * {@code Problem.childNodes(...)} 完成，本类只负责回答上面那几个"具体的业务问题"，
 * 不需要（也不应该）自己写搜索循环。</p>
 */
public class NPuzzleProblem extends Problem {

    /** 换行符，避免在 Windows 上输出挤成一行 */
    private static final String NL = System.lineSeparator();

    /**
     * 两参数构造器：自动从初始盘面推断边长，然后转交给三参数构造器。
     *
     * @param initialState 初始盘面（必须是 {@link PuzzleBoard}）
     * @param goal         目标盘面
     */
    public NPuzzleProblem(State initialState, State goal) {
        this(initialState, goal, sizeOf(initialState));
    }

    /**
     * 三参数构造器：由框架的 {@code PuzzleFeeder} 调用。
     *
     * @param initialState 初始盘面
     * @param goal         目标盘面
     * @param size         边长（3 或 4）
     */
    public NPuzzleProblem(State initialState, State goal, int size) {
        super(initialState, goal, size);
    }

    /**
     * 从状态里推断盘面边长。
     *
     * @param state 状态
     * @return 边长
     * @throws IllegalArgumentException 状态不是 PuzzleBoard
     */
    private static int sizeOf(State state) {
        if (state instanceof PuzzleBoard) {
            return ((PuzzleBoard) state).size();
        }
        throw new IllegalArgumentException("NPuzzleProblem 的状态必须是 PuzzleBoard");
    }

    /**
     * @return 初始盘面（第四阶段的可视化窗口用它作为动画的第 0 帧）
     */
    public PuzzleBoard initialBoard() {
        return (PuzzleBoard) initialState;
    }

    /**
     * @return 目标盘面（可视化窗口用它把"已经归位"的将牌画成绿色）
     */
    public PuzzleBoard goalBoard() {
        return (PuzzleBoard) goal;
    }

    // =====================================================================
    //  一、有解判定：逆序数奇偶性
    // =====================================================================

    /**
     * 当前问题是否有解。
     *
     * <p>N 数码的状态空间被分成「若干个互不连通的连通分量」：从初始盘面出发，
     * 只能到达它所在分量的那些盘面。所以"有没有解"其实是一个纯粹的数学问题，
     * <b>根本不需要真的去搜索</b>（否则得把整个状态空间搜一遍才知道无解，代价无法承受）。
     * 判定依据是<b>逆序数的奇偶性</b>。</p>
     *
     * <h4>什么是逆序数？</h4>
     * <p>把盘面上的数字按行优先排成一个序列（去掉空格），
     * 数一数有多少对数字是"前后颠倒"的。以
     * <pre>[8, 6, 7, 2, 5, 4, 3, 1]</pre>
     * 为例：8 后面比它小的有 6,7,2,5,4,3,1 共 7 个；6 后面有 2,5,4,3,1 共 5 个……
     * 全部累加起来就是逆序数。</p>
     *
     * <h4>判定规则</h4>
     * <ul>
     *   <li><b>奇数阶（3×3）</b>：逆序数为<b>偶数</b> ⇔ 有解。
     *       （目标盘面自身的逆序数是 0，属于偶数的分量）</li>
     *   <li><b>偶数阶（4×4）</b>：光看逆序数不够，还要考虑空格在哪一行。
     *       记 {@code b} = 空格<b>从下往上数</b>的行号（最下面一行是 1），
     *       则 {@code (逆序数 + b)} 的奇偶性相同的两个盘面才互相可达。</li>
     * </ul>
     *
     * <p>为什么偶数阶必须多这一条？因为 4×4 的每一步滑动，逆序数会变化奇数个，
     * 而空格的行号也同时改变，两者组合起来才构成不变量。这也是"15 数码只有一半
     * 的初始局面有解"这一著名结论的来源。</p>
     *
     * @return 有解返回 true，无解返回 false
     */
    @Override
    public boolean solvable() {
        PuzzleBoard start = (PuzzleBoard) initialState;
        PuzzleBoard target = (PuzzleBoard) goal;
        int n = start.size();

        // 以"目标盘面的先后次序"为基准统计逆序数（空格不参与）
        int invStart = inversions(start, target);

        if (n % 2 == 1) {
            // 奇数阶：逆序数为偶 ⇔ 有解
            return invStart % 2 == 0;
        }

        // 偶数阶：(逆序数 + 空格从下往上的行号) 的奇偶性必须与目标盘面一致。
        // 目标盘面相对自己的逆序数恒为 0，所以右边只取决于目标盘面里空格的行号。
        return (invStart + rowFromBottom(start)) % 2 == rowFromBottom(target) % 2;
    }

    /**
     * 统计逆序数。
     *
     * <p>这里把「按<b>目标盘面</b>中的先后次序」当作标准顺序，而不是直接比较数字大小。
     * 这样写的好处是：目标盘面可以是任意排列（不局限于 1,2,3,...,n-1,0 这种教科书标准形式），
     * 判定依然正确。对我们作业里"目标恒为标准排列"的情况，两种写法结果完全一样。</p>
     *
     * @param board  要统计的盘面
     * @param target 目标盘面（提供排序基准）
     * @return 逆序数
     */
    private int inversions(PuzzleBoard board, PuzzleBoard target) {
        int[] t = board.tiles();
        int[] pos = target.positionIndex();      // pos[v] = 将牌 v 在目标盘面中的下标
        int count = 0;

        for (int i = 0; i < t.length; i++) {
            if (t[i] == 0) {
                continue;                        // 空格不参与逆序数
            }
            for (int j = i + 1; j < t.length; j++) {
                if (t[j] == 0) {
                    continue;                    // 空格不参与逆序数
                }
                // t[i] 在目标里的位置排在 t[j] 之后 ⇒ 这是一对逆序
                if (pos[t[i]] > pos[t[j]]) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 空格「从下往上数」的行号：最下面一行返回 1，往上依次 2、3、4。
     *
     * @param board 盘面
     * @return 从下往上数的行号（1 起算）
     */
    private int rowFromBottom(PuzzleBoard board) {
        // blankRow() 是 0 起算的"从上往下"行号，最大为 size-1（最下面一行）
        return board.size() - board.blankRow();
    }

    // =====================================================================
    //  二、代价与可行性
    // =====================================================================

    /**
     * 从 state 执行 action 进入后继状态所需付出的代价。
     *
     * <p>N 数码每滑动一步的代价恒为 1，与盘面内容、将牌大小都无关，
     * 所以直接忽略参数返回 1。这个常量决定了 g(n) 的累加方式，
     * 也保证"解路径长度 = 实际移动步数"。</p>
     *
     * @param state  当前状态
     * @param action 要执行的动作
     * @return 固定返回 1
     */
    @Override
    public int stepCost(State state, Action action) {
        return 1;
    }

    /**
     * 在状态 state 上，动作 action 是否可行？
     *
     * <p>判据很简单：空格朝该方向移动之后，是否还落在盘面范围内。
     * 例如空格已经在第一行，"向上移动"就越界了，不可行。</p>
     *
     * @param state  当前状态
     * @param action 待判定的动作
     * @return 可行返回 true，越界返回 false
     */
    @Override
    public boolean applicable(State state, Action action) {
        PuzzleBoard board = (PuzzleBoard) state;
        // indexAfterMove 越界时返回 -1，正好可以直接当判据
        return board.indexAfterMove(((Move) action).getDirection()) >= 0;
    }

    // =====================================================================
    //  三、解路径可视化
    // =====================================================================

    /**
     * 解路径的可视化 —— <b>阶段 4 的交付点</b>。
     *
     * <p>框架的 {@code SearchTester} 每解完一个问题就会回调本方法，所以我们只要在这里
     * 决定"怎么给用户看"，就等于完成了可视化。本方法提供两种展示方式：</p>
     *
     * <ul>
     *   <li><b>控制台文字版（默认）</b>：逐步打印棋盘，适合批量跑实验、看数据。</li>
     *   <li><b>图形窗口版</b>：给 JVM 加参数 {@code -Dnpuzzle.gui=true} 即可开启，
     *       会弹出一个动画窗口播放整个求解过程（见 {@link PuzzleVisualizer}）。</li>
     * </ul>
     *
     * <p>为什么默认不开窗口？因为 SearchTester 会遍历"每个实例 × 每个启发函数"
     * （例如 4 个实例 × 2 个启发函数 = 8 次回调），如果每次都弹一个窗口，
     * 批处理就变成"手动点 8 次关闭"，实验数据也没法自动收集。
     * 所以用系统属性作为开关，按需开启。</p>
     *
     * <p>{@code path} 由框架的 {@code AbstractSearcher.generatePath(...)} 回溯生成，
     * 从"根结点的下一个结点"开始（不含根结点，因为根结点本身就是初始状态）。
     * Deque 的头是<b>第一步</b>，按 for-each 顺序遍历即为从前到后的移动顺序。</p>
     *
     * @param path 解路径（每个结点自带 state 与 action）
     */
    @Override
    public void showSolution(Deque<Node> path) {
        if (Boolean.getBoolean(PuzzleVisualizer.GUI_PROPERTY)) {
            // —— 第四阶段：图形化窗口（会阻塞到用户关闭窗口）——
            PuzzleVisualizer.showAndWait(
                    solutionFrames(path),
                    solutionCaptions(path),
                    goalBoard(),
                    buildInfo(path));
        } else {
            // —— 第一阶段的文字版：安静、可批量 ——
            printSolution(path);
        }
    }

    /**
     * 把解路径整理成"一串盘面"，也就是动画的每一帧。
     * 第 0 帧是初始状态，之后每走一步追加一帧，所以帧数 = 解路径长度 + 1。
     *
     * @param path 解路径
     * @return 帧序列
     */
    private List<PuzzleBoard> solutionFrames(Deque<Node> path) {
        List<PuzzleBoard> frames = new ArrayList<>();
        frames.add(initialBoard());
        for (Node node : path) {
            frames.add((PuzzleBoard) node.getState());
        }
        return frames;
    }

    /**
     * 与帧一一对应的说明文字，显示在窗口标题下方。
     *
     * @param path 解路径
     * @return 说明列表，长度与 {@link #solutionFrames} 一致
     */
    private List<String> solutionCaptions(Deque<Node> path) {
        List<String> captions = new ArrayList<>();
        captions.add("初始状态");

        int step = 0;
        for (Node node : path) {
            step++;
            captions.add("第 " + step + " 步：空格向 " + node.getAction() + " 移动");
        }

        if (!path.isEmpty()) {
            int last = captions.size() - 1;
            captions.set(last, captions.get(last) + "　（到达目标状态！）");
        }
        return captions;
    }

    /**
     * 可视化窗口顶部显示的一行摘要。
     *
     * @param path 解路径
     * @return 摘要文字
     */
    private String buildInfo(Deque<Node> path) {
        return size + " × " + size + " 数码　　解路径 " + path.size() + " 步";
    }

    /**
     * 控制台文字版可视化（第一阶段的做法）：依次打印初始盘面、每一步的走法与盘面、总步数。
     *
     * <p>提示：如果只关心步数和性能数据（比如跑 15 数码的困难实例），
     * 可以把循环里的棋盘打印注释掉，只保留步数，输出会清爽很多。</p>
     *
     * @param path 解路径
     */
    private void printSolution(Deque<Node> path) {
        StringBuilder sb = new StringBuilder();

        sb.append("初始状态：").append(NL)
                .append(initialBoard().toGridString()).append(NL);

        int step = 0;
        for (Node node : path) {
            step++;
            sb.append("第 ").append(step).append(" 步：空格向 ")
                    .append(node.getAction()).append(" 移动").append(NL)
                    .append(((PuzzleBoard) node.getState()).toGridString()).append(NL);
        }

        sb.append("共 ").append(path.size()).append(" 步到达目标状态");
        System.out.println(sb);
    }
}
