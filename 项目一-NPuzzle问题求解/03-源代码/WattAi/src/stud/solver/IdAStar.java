package stud.solver;

import core.problem.Problem;
import core.solver.algorithm.heuristic.Predictor;
import core.solver.algorithm.searcher.AbstractSearcher;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Deque;

/**
 * 迭代加深的 A* 搜索（Iterative Deepening A*，简称 IDA*），用于对比实验。
 *
 * 与 A* 的区别：
 *   A*   用 frontier（优先队列）保存所有已生成、尚未扩展的结点；
 *   IDA* 只保存“当前这一条路径”，所以内存占用与解路径长度成正比，
 *        代价是每一轮加深都会重复搜索，时间开销更大。
 *
 * 算法流程：
 *   1. 阈值 threshold 初始化为起点的 f 值，f = g + h；
 *   2. 以 threshold 为界做深度优先搜索，凡是 f 超过阈值就剪枝；
 *      若这一轮没找到目标，就把所有被剪掉分支中最小的 f 作为新阈值，重新搜索；
 *   3. 阈值不断增大，直到找到目标；若已无分支可扩展，则说明问题无解。
 */
public final class IdAStar extends AbstractSearcher {

    private static final int FOUND = -1;                 // 找到目标的哨兵值
    private static final int INFINITY = Integer.MAX_VALUE;

    private final Predictor predictor;                   // 启发函数
    private Problem problem;                             // 当前正在求解的问题
    private Node found;                                  // 找到目标时的目标结点

    /**
     * 构造一个 IDA* 求解器。
     *
     * @param frontier  框架统一传入的 Frontier。IDA* 本身不用优先队列保存待扩展结点，
     *                  这里只调用它的 clear()，让两种算法的初始化步骤保持一致
     * @param predictor 启发函数，决定每一轮阈值的增长幅度
     */
    public IdAStar(Frontier frontier, Predictor predictor) {
        super(frontier);
        this.predictor = predictor;
    }

    /**
     * 从初始状态出发反复加深阈值，直到找到目标或者确认无解。
     *
     * @param problem 待求解的问题（这里就是 N-Puzzle 实例）
     * @return 初始结点到目标结点的完整路径；不可解或搜不到时返回 null
     */
    @Override
    public Deque<Node> search(Problem problem) {
        this.problem = problem;
        this.found = null;

        // 每次搜索前清理现场
        frontier.clear();
        explored.clear();
        nodesGenerated = 0;
        nodesExpanded = 0;

        if (!problem.solvable()) return null;

        Node root = problem.root(predictor);
        int threshold = root.getHeuristic();             // 根结点 g = 0，故 f = h

        while (true) {
            explored.clear();                            // explored 在这里当作“当前路径”使用
            int t = depthFirstSearch(root, threshold);
            if (t == FOUND) return generatePath(found);
            if (t == INFINITY) return null;              // 已无可扩展分支，无解
            threshold = t;                               // 增大阈值，进入下一轮加深
        }
    }

    /**
     * 以 threshold 为界进行深度优先搜索。
     *
     * @return FOUND：已经找到目标；
     *         其它情况返回本轮中所有被剪掉分支里最小的 f 值，作为下一轮的阈值。
     */
    private int depthFirstSearch(Node node, int threshold) {
        int f = node.evaluation();                       // f = g + h
        if (f > threshold) return f;                     // 超出阈值，剪枝

        if (problem.goal(node.getState())) {             // 到达目标状态
            found = node;
            return FOUND;
        }

        explored.add(node.getState());                   // 把当前状态压入路径
        int min = INFINITY;

        for (Node child : problem.childNodes(node, predictor)) {
            nodesGenerated++;
            // 路径上已经出现过该状态，说明是环，跳过
            if (explored.contains(child.getState()))
                continue;

            int t = depthFirstSearch(child, threshold);
            if (t == FOUND) {
                explored.remove(node.getState());
                return FOUND;
            }
            if (t < min) min = t;
        }

        explored.remove(node.getState());                // 回溯，把状态移出路径
        nodesExpanded++;
        return min;
    }
}
