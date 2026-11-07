package stud.g01.queue;

import core.problem.State;
import core.solver.queue.Frontier;
import core.solver.queue.Node;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * g01 组的 Frontier 实现：<b>二叉堆 + 状态索引表 + 惰性删除</b>。
 *
 * <h3>Frontier 是什么？</h3>
 * <p>Frontier（"待扩展集合"）存放<b>已经生成、但还没有被扩展</b>的结点，它的"取出顺序"
 * 决定了算法是谁：按 f=g+h 升序取 ⇒ A*；按 g 取 ⇒ Dijkstra；按 h 取 ⇒ 贪心最佳优先。
 * 排序规则由构造器传入的 {@link Comparator} 决定（框架用
 * {@code Node.evaluator(EvaluationType)} 提供了三种现成的比较器），
 * 这样"换比较器 = 换算法"。</p>
 *
 * <h3>框架对 offer 的契约</h3>
 * <p>见 {@code core.solver.queue.Frontier} 的注释：<b>如果队列里已经存在"状态相同"的结点，
 * 就把两者中较差的那个丢掉。</b>也就是说，同一个状态在 Frontier 里最多只能留一个结点。</p>
 *
 * <h3>★ 第二阶段的核心改进：为什么必须放弃 PriorityQueue.remove()</h3>
 * <p>第一阶段的写法是：发现同状态的旧结点时，调用 {@code PriorityQueue.remove(oldNode)}
 * 把它从堆里摘掉。问题在于——<b>{@code PriorityQueue} 只保证堆顶最小，
 * 内部没有任何"元素 → 位置"的索引，所以 remove 只能线性扫描整个堆，复杂度 O(n)。</b></p>
 *
 * <p>在 15 数码（4 阶）上这个代价是灾难性的：搜索动辄生成几千万个结点，
 * 堆里同时存在几百万个结点，而"生成一个已被见过的状态"是常态。
 * 假设有 5% 的结点需要替换，就有几十万次 O(几百万) 的扫描，
 * 计算量直接上升到 10¹¹ 量级。</p>
 *
 * <p><b>解法：惰性删除（lazy deletion）。</b>发现新结点更优时，<b>不去堆里删旧的</b>，
 * 而是：</p>
 * <ol>
 *   <li>把索引表 {@code index} 里该状态的条目<b>覆盖</b>为新的结点；</li>
 *   <li>把新结点照常压入堆；旧结点就<b>留在堆里变成"废结点"</b>；</li>
 *   <li>{@link #poll()} 取到结点时，用<b>身份比较</b>（{@code ==}）判断它是否还是该状态的当前最优：
 *       不是就直接丢弃，继续取下一个。</li>
 * </ol>
 * <p>这样 {@code offer} 与 {@code poll} 都只有 O(log n)，而"删旧"这件事被摊还到了 poll 里。
 * 代价是堆里会多存一些废结点（内存换时间），这是本阶段非常划算的一笔交易。</p>
 *
 * <h3>★ 关键细节：为什么必须重写 isEmpty() / size()</h3>
 * <p>惰性删除后，堆里可能<b>只剩下废结点</b>。此时如果沿用 {@code PriorityQueue.isEmpty()}
 * （它看的是堆数组里有没有元素），会返回 false，于是框架的
 * {@code BestFirstSearcher} 会以为还有结点可取，调用 {@code poll()}——
 * 而 {@code poll()} 把废结点全部过滤掉之后只能返回 {@code null}，
 * 紧接着框架执行 {@code problem.goal(node.getState())} 就会 <b>空指针崩溃</b>。</p>
 *
 * <p>所以这里把 {@code isEmpty()} / {@code size()} 重定义为"看索引表"：
 * 索引表里有的才是<b>存活结点</b>，其数量恰好就是 Frontier 中真正可取的结点数。
 * 索引表为空 ⇒ Frontier 语义上就是空的，框架会正确地判定"无解"，
 * 而不会傻乎乎地去 poll 一堆废结点。</p>
 *
 * <h3>索引表的不变量</h3>
 * <ul>
 *   <li>索引表里出现过的状态 ⇒ 堆里至少有一个该状态的结点（可能是废的）；</li>
 *   <li>索引表里 {@code state → node} 的那个 node，一定是该状态当前估值最优的结点；</li>
 *   <li>索引表的 size ≡ Frontier 中"存活结点"的个数。</li>
 * </ul>
 */
@SuppressWarnings("serial")   // 继承自 PriorityQueue（可序列化），但我们从不序列化 Frontier
public class PqFrontier extends PriorityQueue<Node> implements Frontier {

    /** 结点优先级比较器。框架通过 {@code Node.evaluator(EvaluationType)} 提供 */
    private final Comparator<Node> evaluator;

    /**
     * 状态 → 该状态当前最优的、仍在 Frontier 中的结点。
     * 它同时承担两个职责：<b>O(1) 查重</b>，以及<b>记录存活结点集合</b>。
     */
    private final Map<State, Node> index = new HashMap<>();

    /**
     * 压实阈值里的"宽松量"：堆元素数要超过 {@code 2 × 存活数 + COMPACT_SLACK} 才触发压实。
     * 这个常数是为了避免搜索刚开始、结点很少时频繁压实。
     */
    private static final int COMPACT_SLACK = 1024;

    /** 累计压实次数（诊断用） */
    private int compactions;

    /**
     * @param evaluator 结点比较器，决定"谁先被取出"。
     *                  例如 A* 用 {@code Node.evaluator(EvaluationType.FULL)}
     */
    public PqFrontier(Comparator<Node> evaluator) {
        // 初始容量给大一点（默认只有 11），避免搜索初期堆数组反复扩容
        super(1024, evaluator);
        this.evaluator = evaluator;
    }

    /**
     * Frontier 中是否含有与 node 状态相同的结点。
     *
     * @param node 待查找的结点
     * @return 含有返回 true
     */
    @Override
    public boolean contains(Node node) {
        return index.containsKey(node.getState());
    }

    /**
     * 把结点插入 Frontier，并维护"同状态只留最优"的契约。
     *
     * <ul>
     *   <li>状态没出现过 → 直接入堆；</li>
     *   <li>状态已存在，且新结点更优 → 覆盖索引 + 新结点入堆，<b>旧结点留在堆里当废结点</b>；</li>
     *   <li>状态已存在，但旧结点不差 → 丢弃新结点。</li>
     * </ul>
     *
     * @param node 要插入的结点
     * @return true = 结点被接受（新插入或替换了旧的）；false = 被丢弃
     */
    @Override
    public boolean offer(Node node) {
        Node old = index.get(node.getState());

        if (old == null) {
            // 全新的状态
            index.put(node.getState(), node);
            super.offer(node);
            compactIfNeeded();
            return true;
        }

        // 已有同状态的旧结点 → 比较优先级（compare < 0 表示更优）
        if (evaluator.compare(old, node) > 0) {
            // ★ 惰性删除：只改索引，不调用 super.remove(old)（那是 O(n) 的元凶）
            index.put(node.getState(), node);
            super.offer(node);
            compactIfNeeded();
            return true;
        }

        // 旧结点不比新的差 → 丢弃新结点
        return false;
    }

    /**
     * ★ 惰性删除的"配套措施"：<b>定期压实（compaction）</b>，把废结点真正扔掉。
     *
     * <h4>为什么必须做这件事？</h4>
     * <p>惰性删除的代价是"废结点只进不出"：每替换一次就多留一个永远取不到、
     * 也永远不会被回收的旧结点（它还挂在堆数组里，被强引用着，GC 收不掉）。</p>
     * <p>在 15 数码的困难实例上这个泄漏是致命的：搜索可能生成上亿个结点，
     * 堆数组会一路膨胀，最终在 {@code PriorityQueue.grow()} 里抛
     * <b>OutOfMemoryError</b>（我实测就撞上了：倒序实例跑到第 272 秒时炸掉）。</p>
     *
     * <h4>怎么压实？</h4>
     * <p>当"堆里的元素总数"明显超过"存活结点数"时，把存活结点抓出来、
     * 清空堆、再重新放回去。废结点失去引用后就能被 GC 回收，堆数组也跟着缩回合理大小。</p>
     *
     * <p>触发阈值取 {@code 堆大小 > 2 × 存活数 + 1024}：</p>
     * <ul>
     *   <li>这样堆里最多只会有约 2 倍于存活数的元素，内存有上界；</li>
     *   <li>压实本身是 O(k log k)（k = 存活数），但两次压实之间至少插入了 k 个结点，
     *       所以<b>摊还下来每次 offer 仍然只有 O(log n)</b>；</li>
     *   <li>常数 1024 是为了避免搜索刚开始、结点还很少时频繁压实。</li>
     * </ul>
     */
    private void compactIfNeeded() {
        // super.size() 是"堆里元素总数"（含废结点），index.size() 是"存活结点数"
        if (super.size() <= 2 * index.size() + COMPACT_SLACK) {
            return;
        }

        Node[] alive = index.values().toArray(new Node[0]);
        super.clear();
        for (Node n : alive) {
            // 注意：必须显式用 super.offer，直接调 PriorityQueue 的方法。
            // 如果写成 add/offer（虚方法），会绕回到本类重写过的 offer，
            // 既会重复写索引，也会造成无限递归。
            super.offer(n);
        }
        compactions++;
    }

    /** @return 本 Frontier 累计压实的次数（诊断用；次数多说明废结点产生得快） */
    public int compactions() {
        return compactions;
    }

    /**
     * 取出并删除"优先级最高的存活结点"。
     *
     * <p>循环丢弃废结点（那些已经被更优结点取代的旧结点），
     * 直到遇到一个仍然是索引表当前值的结点为止。</p>
     *
     * @return 优先级最高的存活结点；没有存活结点时返回 null
     */
    @Override
    public Node poll() {
        while (true) {
            Node node = super.poll();
            if (node == null) {
                return null;                     // 堆已经空了
            }
            State state = node.getState();
            // ★ 身份比较（==），不是 equals：
            //   只有"索引表里存的就是这个对象"才说明它是该状态的当前最优。
            if (index.get(state) == node) {
                index.remove(state);             // 它离开 Frontier（即将被扩展）
                return node;
            }
            // 否则是废结点 —— 直接丢弃，继续取下一个
        }
    }

    /**
     * Frontier 是否已经空了。
     *
     * <p><b>必须以索引表为准</b>，不能看堆数组：堆里可能只剩下一堆废结点
     * （见类注释中"为什么必须重写 isEmpty()"）。</p>
     *
     * @return 索引表为空（即没有存活结点）时返回 true
     */
    @Override
    public boolean isEmpty() {
        return index.isEmpty();
    }

    /**
     * Frontier 中<b>存活结点</b>的个数（不含堆里的废结点）。
     *
     * @return 存活结点数
     */
    @Override
    public int size() {
        return index.size();
    }

    /**
     * 清空 Frontier。框架在每次开始新搜索前都会调用它
     * （{@code BestFirstSearcher.search} 里），索引必须跟着一起清，
     * 否则会残留上一次搜索的脏数据。
     */
    @Override
    public void clear() {
        super.clear();
        index.clear();
    }
}
