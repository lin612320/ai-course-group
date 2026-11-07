package stud.g01.runner;

import core.problem.Problem;
import core.runner.EngineFeeder;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Node;
import stud.g01.solver.IdAStar;
import stud.queue.StackFrontier;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * 项目一【第二 / 三阶段】的实验入口：跑一批实例、对比若干启发函数，直接输出可贴进报告的数据表。
 *
 * <h3>为什么不能直接用框架的 SearchTester？</h3>
 * <p>{@code core.runner.SearchTester} 对 {@code NPUZZLE} 的处理是"写死"的
 * （见它的 {@code getHeuristicTypes}）：</p>
 * <ul>
 *   <li>{@code NPUZZLE 1} → 依次跑 <b>MISPLACED 和 MANHATTAN 两个</b>；</li>
 *   <li>{@code NPUZZLE 3} → 只跑 DISJOINT_PATTERN。</li>
 * </ul>
 * <p>问题在于：<b>"不在位将牌数"这个启发函数在 4 阶（15 数码）上会内存爆炸</b>
 * （它的估计太松，A* 要扩展天文数字的结点）。而 SearchTester 是"先跑 MISPLACED、
 * 再跑 MANHATTAN"，于是 MISPLACED 一旦 OutOfMemory，<b>整个 JVM 进程直接死掉</b>，
 * 你连 MANHATTAN 的结果都拿不到。</p>
 * <p>所以第二阶段需要一个能<b>自己指定要跑哪些启发函数</b>的入口。本类就是它。</p>
 *
 * <h3>用法</h3>
 * <pre>
 *   java stud.g01.runner.ExperimentRunner &lt;数据文件&gt; &lt;Feeder类名&gt; &lt;启发函数&gt; [&lt;启发函数&gt; ...] [--size=N]
 *
 *   例 1（第二阶段：4 阶，只跑曼哈顿）
 *     java stud.g01.runner.ExperimentRunner resources/problems_stage2.txt stud.g01.runner.PuzzleFeeder MANHATTAN
 *
 *   例 2（第一阶段：3 阶，两个启发函数对比）
 *     java stud.g01.runner.ExperimentRunner resources/problems_stage1.txt stud.g01.runner.PuzzleFeeder MISPLACED MANHATTAN
 *
 *   例 3（只跑数据文件里 3 阶的实例）
 *     java stud.g01.runner.ExperimentRunner resources/problems.txt stud.g01.runner.PuzzleFeeder MANHATTAN --size=3
 * </pre>
 *
 * <h3>输出</h3>
 * <p>先按启发函数分组，逐个实例打印"解路径长度 / 生成结点 / 扩展结点 / 耗时"；
 * 最后给出一张汇总表（总耗时、平均耗时、总生成/扩展结点），可直接用于
 * 作业报告里的"实验数据对比分析"。</p>
 *
 * <h3>附：控制 JVM 内存</h3>
 * <p>4 阶的困难实例很吃内存，建议显式给堆：{@code java -Xmx6g ...}。
 * 如果某个实例仍然 OutOfMemory，说明该实例超出了"朴素 A* + 曼哈顿距离"的能力范围，
 * 这正是第三阶段要引入<b>模式数据库</b>的原因。</p>
 */
public final class ExperimentRunner {

    /** 工具类，不需要实例化 */
    private ExperimentRunner() {
    }

    /**
     * 单个 (实例 × 启发函数) 的一次实验结果。
     */
    private static final class Result {
        /** 数据文件里第几个实例（1 起算） */
        int instance;
        /** 盘面阶数 */
        int size;
        /** 解路径长度；-1 表示无解 */
        int length = -1;
        /** 生成的结点数 */
        int generated;
        /** 扩展的结点数 */
        int expanded;
        /** 耗时（秒） */
        double seconds;
    }

    /**
     * 程序入口。
     *
     * @param args 见类注释
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            printUsage();
            return;
        }

        String dataFile = args[0];
        String feederClass = args[1];

        // ---- 解析命令行：除了前两个，其余要么是启发函数名，要么是 --size=N / --ida ----
        List<HeuristicType> heuristics = new ArrayList<>();
        int sizeFilter = 0;                       // 0 表示不过滤
        boolean useIdaStar = false;               // 是否改用 IDA*（任务二对比用）
        for (int i = 2; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--size=")) {
                sizeFilter = Integer.parseInt(a.substring("--size=".length()));
            } else if (a.equalsIgnoreCase("--ida")) {
                useIdaStar = true;
            } else {
                // 统一转成大写，这样命令行里写 manhattan、Manhattan、MANHATTAN 都能用
                try {
                    heuristics.add(HeuristicType.valueOf(a.toUpperCase()));
                } catch (IllegalArgumentException ex) {
                    // 参数写错时给友好提示，而不是甩一段 Java 堆栈
                    System.out.println("无法识别的参数：" + a);
                    printUsage();
                    return;
                }
            }
        }
        if (heuristics.isEmpty()) {
            System.out.println("错误：至少要指定一个启发函数。");
            printUsage();
            return;
        }

        // ---- 用反射创建 Feeder（与 SearchTester 的方式完全一致） ----
        EngineFeeder feeder = (EngineFeeder)
                Class.forName(feederClass).getDeclaredConstructor().newInstance();

        // ---- 读入数据文件 ----
        //     ⚠️ 相对路径是相对"工作目录"解析的，IntelliJ 默认工作目录是工程根目录，
        //     而数据文件在 WattAi/resources 下 —— DataFiles.resolve 会自动兜底。
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

        // ---- 解析成问题实例 ----
        ArrayList<Problem> problems = feeder.getProblems(lines);
        if (problems.isEmpty()) {
            System.out.println("输入文件里没有问题实例：" + dataFile);
            return;
        }

        System.out.println("数据文件：" + dataFile);
        System.out.println("实例总数：" + problems.size() + (sizeFilter > 0 ? "（只看 " + sizeFilter + " 阶）" : ""));
        System.out.println();

        // 汇总用：启发函数 → 该启发函数的全部结果
        Map<HeuristicType, List<Result>> allResults = new LinkedHashMap<>();

        for (HeuristicType heuristic : heuristics) {
            System.out.println("==================== " + (useIdaStar ? "IDA*" : "A*")
                    + " ／ 启发函数：" + heuristic + " ====================");
            List<Result> results = new ArrayList<>();
            allResults.put(heuristic, results);

            for (int i = 0; i < problems.size(); i++) {
                Problem problem = problems.get(i);
                int size = sizeOf(lines.get(i));
                if (sizeFilter > 0 && size != sizeFilter) {
                    continue;                     // 被 --size 过滤掉
                }

                Result r = new Result();
                r.instance = i + 1;
                r.size = size;

                // 每个实例都用"全新的搜索器"，避免任何状态残留影响结果
                //
                // 注意：框架的 EngineFeeder.getIdaStar(...) 是 final 的，而且内部写死了
                // stud.solver.IdAStar（框架自带的那份），所以这里不能通过 Feeder 拿到
                // 我们自己写的 stud.g01.solver.IdAStar —— 只能直接 new。
                // Frontier 对 IDA* 其实没有用（它只保存当前路径），但 AbstractSearcher
                // 要求必须持有一个，所以照框架的做法传一个 StackFrontier。
                AbstractSearcher searcher = useIdaStar
                        ? new IdAStar(new StackFrontier(), feeder.getPredictor(heuristic))
                        : feeder.getAStar(heuristic);

                if (!problem.solvable()) {
                    System.out.printf("  实例%-3d %d阶   无解（逆序数判定）%n", r.instance, size);
                    continue;
                }

                long start = System.nanoTime();
                Deque<Node> path = searcher.search(problem);
                r.seconds = (System.nanoTime() - start) / 1e9;
                r.generated = searcher.nodesGenerated();
                r.expanded = searcher.nodesExpanded();

                if (path == null) {
                    System.out.printf("  实例%-3d %d阶   No Solution   耗时 %.3fs%n",
                            r.instance, size, r.seconds);
                    results.add(r);
                    continue;
                }

                r.length = path.size();
                System.out.printf("  实例%-3d %d阶   解路径=%-4d 生成=%-10d 扩展=%-10d 耗时=%.3fs%n",
                        r.instance, size, r.length, r.generated, r.expanded, r.seconds);
                results.add(r);
            }
            System.out.println();
        }

        printSummary(allResults);
    }

    /**
     * 打印汇总表（报告里"实验数据对比分析"要用的就是它）。
     *
     * @param allResults 启发函数 → 结果列表
     */
    private static void printSummary(Map<HeuristicType, List<Result>> allResults) {
        System.out.println("==================== 汇总 ====================");
        System.out.printf("%-14s %-8s %-14s %-14s %-16s %-16s%n",
                "启发函数", "实例数", "总耗时(s)", "平均耗时(s)", "总生成结点", "总扩展结点");

        for (Map.Entry<HeuristicType, List<Result>> e : allResults.entrySet()) {
            List<Result> list = e.getValue();
            long totalGen = 0;
            long totalExp = 0;
            double totalSec = 0;
            for (Result r : list) {
                totalGen += r.generated;
                totalExp += r.expanded;
                totalSec += r.seconds;
            }
            int n = list.size();
            System.out.printf("%-14s %-8d %-14.3f %-14.4f %-16d %-16d%n",
                    e.getKey(), n, totalSec, n == 0 ? 0.0 : totalSec / n, totalGen, totalExp);
        }
        System.out.println();

        // 解路径长度一致性检查：
        // 所有启发函数都满足可采纳性（admissible），所以对同一个实例，
        // 它们求出的解路径长度必须完全相同。这一行是一道很实用的"正确性自检"。
        System.out.println("提示：若不同启发函数对同一实例给出的解路径长度不一致，");
        System.out.println("      说明某个启发函数不满足可采纳性（很可能把空格也计入了曼哈顿距离）。");
    }

    /**
     * 从数据文件的一行里读出盘面阶数（每行的第 1 个字段）。
     *
     * @param line 原始行，形如 {@code "4 8 13 0 6 ... 1 2 3 ... 0"}
     * @return 阶数（3 或 4）
     */
    private static int sizeOf(String line) {
        return Integer.parseInt(line.trim().split("\\s+")[0]);
    }

    /** 打印命令行用法 */
    private static void printUsage() {
        System.out.println("用法：java stud.g01.runner.ExperimentRunner <数据文件> <Feeder类名> <启发函数> [<启发函数> ...] [--size=N] [--ida]");
        System.out.println();
        System.out.println("示例：");
        System.out.println("  第二阶段（4 阶，A*，只跑曼哈顿）：");
        System.out.println("    java -Xmx6g stud.g01.runner.ExperimentRunner"
                + " resources/problems_stage2.txt stud.g01.runner.PuzzleFeeder MANHATTAN");
        System.out.println("  第一阶段（3 阶，两个启发函数对比）：");
        System.out.println("    java stud.g01.runner.ExperimentRunner"
                + " resources/problems_stage1.txt stud.g01.runner.PuzzleFeeder MISPLACED MANHATTAN");
        System.out.println("  任务二（用老师的测试实例跑 IDA*，与参考结果对标）：");
        System.out.println("    java stud.g01.runner.ExperimentRunner"
                + " resources/problems_idastar.txt stud.g01.runner.PuzzleFeeder MANHATTAN --ida");
        System.out.println();
        System.out.println("  可用启发函数：MISPLACED | MANHATTAN | DISJOINT_PATTERN");
        System.out.println("  --size=N  只跑 N 阶的实例（数据文件里混有 3 阶和 4 阶时很有用）");
        System.out.println("  --ida     改用 IDA*（stud.g01.solver.IdAStar）而不是框架的 A*");
    }
}
