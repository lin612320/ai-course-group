package stud.g01.runner;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.queue.EvaluationType;
import core.solver.queue.Frontier;
import core.solver.queue.Node;
import stud.g01.problem.npuzzle.NPuzzleProblem;
import stud.g01.problem.npuzzle.PuzzleBoard;
import stud.g01.queue.PqFrontier;

import java.util.ArrayList;

/**
 * g01 组的 EngineFeeder：整个作业的<b>总装配车间</b>。
 *
 * <h3>它是怎么被调用的？</h3>
 * <p>框架的主程序 {@code core.runner.SearchTester} 在命令行里接收的第 4 个参数
 * 就是本类的全限定名（{@code stud.g01.runner.PuzzleFeeder}），
 * 然后用<b>反射</b>把它创建出来：</p>
 * <pre>
 *   EngineFeeder feeder = (EngineFeeder) Class.forName(args[3])
 *                                     .getDeclaredConstructor().newInstance();
 * </pre>
 * <p>⚠️ 因此本类<b>必须保留一个公开的无参构造器</b>（现在用的是默认构造器，
 * 不要擅自添加带参数的构造器，否则会导致"没有无参构造器"的运行时异常）。</p>
 *
 * <h3>它要提供三样"素材"</h3>
 * <ol>
 *   <li>{@link #getProblems(ArrayList)} —— 把输入文件的内容解析成一个个问题实例；</li>
 *   <li>{@link #getFrontier(EvaluationType)} —— 造一个 Frontier（本组用 {@link PqFrontier}）；</li>
 *   <li>{@link #getPredictor(HeuristicType)} —— 交出启发函数 h(n)。</li>
 * </ol>
 * <p>拿到这三样之后，框架的 {@code EngineFeeder.getAStar(...)}（父类已实现，final 不可改）
 * 会自动组装出 A* 搜索引擎：{@code new BestFirstSearcher(getFrontier(FULL), getPredictor(h))}。
 * <b>我们只负责"提供零件"，不负责"开车"</b> —— 这就是框架的控制反转设计。</p>
 *
 * <h3>输入文件格式（resources/problems.txt / problems3.txt）</h3>
 * <p>每一行是一个完整的实例，格式为：</p>
 * <pre>
 *   size  初始状态(size×size 个数字)  目标状态(size×size 个数字)
 *
 *   例（3×3）：
 *   3 6 4 7 8 5 0 3 2 1 1 2 3 4 5 6 7 8 0
 *   └┬┘└────────┬────────┘└────────┬────────┘
 *    │          │                  └─ 目标状态：1 2 3 / 4 5 6 / 7 8 0
 *    │          └─ 初始状态：6 4 7 / 8 5 0 / 3 2 1 （0 是空格）
 *    └─ 边长
 * </pre>
 * <p>注意：<b>没有单独的"行首 size"</b>（那是寻路问题 pathfinding.txt 的格式），
 * 本文件每行自带 size。空行会被跳过。</p>
 */
public class PuzzleFeeder extends EngineFeeder {

    /**
     * 把输入文件的每一行解析成一个 {@link NPuzzleProblem}。
     *
     * @param problemLines 输入文件的全部行
     * @return 问题实例列表
     */
    @Override
    public ArrayList<Problem> getProblems(ArrayList<String> problemLines) {
        ArrayList<Problem> problems = new ArrayList<>();

        for (String line : problemLines) {
            if (line == null || line.isBlank()) {
                continue;                       // 跳过空行（文件结尾常有）
            }

            // split("\\s+")：按一个或多个空白/制表符切分
            String[] cells = line.trim().split("\\s+");

            int size = Integer.parseInt(cells[0]);   // 边长
            int n = size * size;                     // 每块盘面的数字个数

            // 前 n 个数字是初始状态，紧接着 n 个是目标状态
            PuzzleBoard initial = readBoard(cells, 1, n, size);
            PuzzleBoard goal = readBoard(cells, 1 + n, n, size);

            problems.add(new NPuzzleProblem(initial, goal, size));
        }

        return problems;
    }

    /**
     * 从切分好的字符串数组里读出一块盘面。
     *
     * @param cells 一行切分后的所有字段
     * @param from  本块盘面第一个数字在 cells 中的下标
     * @param n     数字个数（= size × size）
     * @param size  边长
     * @return 盘面对象
     */
    private PuzzleBoard readBoard(String[] cells, int from, int n, int size) {
        int[] tiles = new int[n];
        for (int i = 0; i < n; i++) {
            tiles[i] = Integer.parseInt(cells[from + i]);
        }
        // PuzzleBoard 的构造器内部会再 clone 一份，所以这里可以放心复用局部数组
        return new PuzzleBoard(tiles, size);
    }

    /**
     * 生成 Frontier。不同的 {@link EvaluationType} 代表不同的"取出顺序"：
     * FULL = f = g + h（A*）、PATH_COST = g（Dijkstra）、HEURISTIC = h（贪心）。
     * 比较器由框架的 {@code Node.evaluator(type)} 提供，我们只管把队列造出来。
     *
     * @param type 结点评估器类型
     * @return 使用该评估机制的 Frontier 实例
     */
    @Override
    public Frontier getFrontier(EvaluationType type) {
        return new PqFrontier(Node.evaluator(type));
    }

    /**
     * 交出启发函数 h(n)。
     * 具体实现放在 {@link PuzzleBoard} 里（它最了解盘面结构），
     * 这里只做一次转发，和参考实现 {@code stud.runner.WalkerFeeder} 的写法一致。
     *
     * <p>第一阶段会用到 MISPLACED（不在位将牌数）和 MANHATTAN（曼哈顿距离）；
     * DISJOINT_PATTERN 属于第三阶段，尚未实现，此处会返回 null。</p>
     *
     * @param type 启发函数类型
     * @return 对应的 Predictor
     */
    @Override
    public Predictor getPredictor(HeuristicType type) {
        return PuzzleBoard.predictor(type);
    }
}
