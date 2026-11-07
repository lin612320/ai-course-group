package stud.g01.problem.npuzzle;

import core.problem.Action;
import core.problem.State;
import core.solver.algorithm.heuristic.HeuristicType;
import core.solver.algorithm.heuristic.Predictor;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

import static core.solver.algorithm.heuristic.HeuristicType.DISJOINT_PATTERN;
import static core.solver.algorithm.heuristic.HeuristicType.MANHATTAN;
import static core.solver.algorithm.heuristic.HeuristicType.MISPLACED;

/**
 * N 数码的【状态】：一个 size × size 的盘面，用 0 表示空格。
 *
 * <p>它继承框架的 {@link core.problem.State} 超类。框架要求状态提供三件事：
 * {@link #next(Action)}（怎么走一步）、{@link #actions()}（有哪些动作可选）、
 * {@link #draw()}（怎么显示）。此外，<b>本类还必须实现 {@link #equals(Object)} 与
 * {@link #hashCode()}</b>，否则框架的 {@code explored} 去重会完全失效、搜索树指数爆炸。</p>
 *
 * <h3>★ 第二阶段的核心改进：用 64 位整数"打包"整个盘面</h3>
 * <p>第一阶段的写法是用 {@code int[]} 数组存盘面。这个写法直观，但在 15 数码上代价很高：
 * 搜索动辄生成<b>几千万个</b>状态对象，每个对象都要额外分配一个长度为 16 的 int 数组，
 * 于是：</p>
 * <ul>
 *   <li><b>内存</b>：一个 PuzzleBoard ≈ 40 字节对象头+字段，再挂一个 int[16] ≈ 80 字节，
 *       合计约 <b>128 字节</b>；</li>
 *   <li><b>哈希</b>：{@code Arrays.hashCode} 要遍历 16 个元素，而每个结点在
 *       {@code HashSet（explored）} 和 {@code HashMap（Frontier 索引）}里会被反复哈希，
 *       累计是 O(16) × 几十亿次；</li>
 *   <li><b>GC</b>：几千万个短命数组会把垃圾回收压到极限。</li>
 * </ul>
 *
 * <p><b>观察</b>：N 数码每一格的取值只有 0 ~ 15，<b>正好 4 个二进制位</b>。
 * 4×4 一共 16 格，16 × 4 = <b>64 位，恰好塞进一个 {@code long}</b>！（3×3 只需要 36 位，
 * 更是绰绰有余。）</p>
 *
 * <pre>
 *   格 0 存在第 0~3 位，格 1 存在第 4~7 位，……，格 i 存在第 (4*i) ~ (4*i+3) 位
 *
 *   例：[8,6,7, 2,5,4, 3,0,1]  →
 *       第 0 格 8  第 1 格 6  第 2 格 7  ...  第 7 格 0  ...
 *       bits = 8 | 6&lt;&lt;4 | 7&lt;&lt;8 | 2&lt;&lt;12 | 5&lt;&lt;16 | 4&lt;&lt;20 | 3&lt;&lt;24 | 0&lt;&lt;28 | 1&lt;&lt;32
 * </pre>
 *
 * <p>好处是压倒性的：</p>
 * <ul>
 *   <li>对象从"本体 + 数组"缩成<b>一个对象</b>，约 <b>48 字节</b>（原来的 ~1/2.6），
 *       而且<b>少分配几千万个数组</b>；</li>
 *   <li>{@code equals} 退化成<b>一次 long 比较</b>，O(1)；</li>
 *   <li>{@code hashCode} 可以在构造时就<b>算好存起来</b>，之后 O(1)；</li>
 *   <li>{@code next} 只是把两个 4 位段"对调"，一个减法就完成，
 *       连数组都不用分配。</li>
 * </ul>
 *
 * <h3>不可变（immutable）</h3>
 * <p>所有字段都是 final，任何操作都不修改本对象，而是<b>造一个新的盘面</b>。
 * 这是搜索算法的硬性要求：搜索树里每个 Node 都长期持有自己的 State，
 * 如果 State 能被就地修改，整棵树的数据就全乱了。</p>
 */
public class PuzzleBoard extends State {

    /** 盘面边长：3 表示 8 数码（3×3），4 表示 15 数码（4×4） */
    private final int size;

    /**
     * 打包后的盘面：每格 4 bit，格 i 存在第 (4*i) ~ (4*i+3) 位（行优先）。
     * 0 表示空格，1~15 表示将牌编号。
     */
    private final long bits;

    /** 空格所在的下标（0 起算）。缓存下来，避免每次 next() 都去扫描找空格。 */
    private final int blank;

    /**
     * 预先算好的哈希值。
     * 状态是<b>不可变</b>的，所以哈希值算一次就永远有效 ——
     * 这比每次被 HashSet/HashMap 调用时都遍历一遍数组要划算得多。
     */
    private final int hash;

    /** 惰性缓存：pos[将牌数值] = 该将牌在「本盘面」中的下标（只在启发函数里对目标盘面调用） */
    private int[] positionIndexCache;

    /** 惰性缓存：每块将牌在「本盘面（作为目标盘面）」中的行号（线性冲突用） */
    private int[] goalRowCache;

    /** 惰性缓存：每块将牌在「本盘面（作为目标盘面）」中的列号（线性冲突用） */
    private int[] goalColCache;

    /** 四个方向的共享动作实例（顺序与 {@link Direction#values()} 一致，不可变） */
    private static final List<Move> ALL_MOVES =
            Arrays.stream(Direction.values()).map(Move::of).toList();

    /**
     * 距离查表：DISTANCE[size] 是一个 size²×size² 的扁平表，
     * {@code DISTANCE[size][i*n + j]} = 格 i 到格 j 的"网格距离"（行差 + 列差）。
     *
     * <p>启发函数要被调用几百万到几千万次，而曼哈顿距离里的除法/取模很贵。
     * 把结果预先算进一张小表（4×4 只有 256 个 int = 1KB），运行时就是一次数组读取。</p>
     */
    private static final int[][] DISTANCE = new int[5][];

    /**
     * 构造一个盘面。
     *
     * @param tiles 盘面数据（行优先，0 表示空格），长度必须正好是 size × size
     * @param size  边长
     * @throws IllegalArgumentException 长度不匹配、数值越界（必须 0~15）、编号重复、或没有空格
     */
    public PuzzleBoard(int[] tiles, int size) {
        if (tiles.length != size * size) {
            throw new IllegalArgumentException(
                    "盘面数据长度必须是 size*size，实际 " + tiles.length + " != " + (size * size));
        }

        this.size = size;

        long packed = 0L;
        int blankIndex = -1;
        int seen = 0;                            // 16 位掩码：记录哪些编号已经出现过

        for (int i = 0; i < tiles.length; i++) {
            int v = tiles[i];
            if (v < 0 || v > 15) {
                throw new IllegalArgumentException("将牌取值必须在 0~15 之间，实际为 " + v
                        + "（4 位一个字节能存的最大编号是 15，所以本打包方案只支持 3 阶和 4 阶）");
            }
            // ★ 必须校验"是一个排列"：如果输入里有重复编号（比如两个 3、缺一个 4），
            //   直接构造出来的盘面在数学上根本不成立，搜索会给出莫名其妙的结果，
            //   而且很难排查。这里用位掩码做 O(1) 的重复检测，几乎不增加开销。
            if ((seen & (1 << v)) != 0) {
                throw new IllegalArgumentException("将牌编号重复：" + v
                        + "（盘面必须是 0 ~ " + (tiles.length - 1) + " 的一个排列）");
            }
            seen |= 1 << v;

            packed |= ((long) v) << (4 * i);     // 把第 i 格写进第 4*i 位
            if (v == 0) {
                blankIndex = i;
            }
        }
        if (blankIndex < 0) {
            throw new IllegalArgumentException("盘面里没有空格(0)，不是合法的 N 数码状态");
        }

        // ★ 注意：这里不需要再 clone 外部数组了 —— 数据已经被"烤"进 long 里，
        //   外部怎么改那个数组都影响不到本对象。这正是打包带来的额外好处。
        this.bits = packed;
        this.blank = blankIndex;
        this.hash = hashOf(size, packed);
    }

    /**
     * 内部专用构造器：直接从打包值构造，供 {@link #next(Action)} 使用（最快路径）。
     *
     * @param bits  打包后的盘面
     * @param size  边长
     * @param blank 空格下标
     */
    private PuzzleBoard(long bits, int size, int blank) {
        this.size = size;
        this.bits = bits;
        this.blank = blank;
        this.hash = hashOf(size, bits);
    }

    /**
     * 计算打包值的哈希。
     *
     * <p>用经典的 64 位混合（xor-shift + 乘法），好处是盘面只差一个格子时
     * 结果也会被搅得很散，能把哈希冲突降到很低。
     * 例如 {@code [1,2,3,...,0]} 和 {@code [2,1,3,...,0]} 只差两个 4 位段，
     * 如果直接用 bits 当哈希，高位差别很小，很容易撞桶。</p>
     *
     * @param size 边长
     * @param bits 打包值
     * @return 哈希值
     */
    private static int hashOf(int size, long bits) {
        long h = bits * 0x9E3779B97F4A7C15L;     // 黄金比例常数
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;                // 再乘一个大奇数
        h ^= (h >>> 32);
        return (int) h ^ (size * 31);
    }

    /**
     * 取（并惰性构建）某一阶数对应的网格距离表。
     *
     * @param size 边长
     * @return 长度为 size⁴ 的扁平表
     */
    private static int[] distanceTable(int size) {
        int[] table = DISTANCE[size];
        if (table == null) {
            int n = size * size;
            table = new int[n * n];
            for (int a = 0; a < n; a++) {
                for (int b = 0; b < n; b++) {
                    table[a * n + b] = Math.abs(a / size - b / size)
                            + Math.abs(a % size - b % size);
                }
            }
            DISTANCE[size] = table;
        }
        return table;
    }

    // =====================================================================
    //  一、State 超类要求实现的方法
    // =====================================================================

    /**
     * 在控制台上显示本状态（打印成带边框的棋盘）。
     */
    @Override
    public void draw() {
        System.out.println(toGridString());
    }

    /**
     * 当前状态执行 action 之后，进入的后继状态。
     *
     * <p>实现要点：<b>不修改本对象</b>，而是算出一个新的打包值返回。
     * 具体做法是把"空格那一格"和"目标格"两个 4 位段对调。</p>
     *
     * <pre>
     *   设 targetTile = 目标格上的将牌编号，blankShift = 4*空格下标，targetShift = 4*目标下标
     *
     *   原状态：  空格段 = 0           目标段 = targetTile
     *   新状态：  空格段 = targetTile  目标段 = 0
     *
     *   于是  newBits = bits + (targetTile &lt;&lt; blankShift) - (targetTile &lt;&lt; targetShift)
     *
     *   加/减都恰好发生在各自的 4 位段内部（结果分别是 targetTile 和 0，都落在 0~15），
     *   不会产生进位或借位，因此不会污染相邻的格子。
     * </pre>
     *
     * @param action 当前状态下要执行的动作（应为 {@link Move}）
     * @return 执行动作后的新盘面
     */
    @Override
    public State next(Action action) {
        Direction d = ((Move) action).getDirection();
        int target = indexAfterMove(d);

        int blankShift = 4 * blank;
        int targetShift = 4 * target;
        long targetTile = (bits >>> targetShift) & 0xF;      // 目标格上的将牌

        long newBits = bits
                + (targetTile << blankShift)                  // 空格那一格：0 → targetTile
                - (targetTile << targetShift);                // 目标格：targetTile → 0

        // 空格的新下标就是 target，直接传进去，省掉一次查找
        return new PuzzleBoard(newBits, size, target);
    }

    /**
     * 当前状态下所有<b>可能</b>的动作，但不一定都可行。
     *
     * <p>这里把 4 个方向全返回，把"到底能不能走"交给
     * {@code NPuzzleProblem.applicable(state, action)} 判断 ——
     * 这是框架规定的分工：<b>actions() 给候选集合，applicable() 做过滤</b>。</p>
     *
     * @return 四个方向对应的动作（共享实例，不会产生垃圾对象）
     */
    @Override
    public Iterable<? extends Action> actions() {
        return ALL_MOVES;
    }

    // =====================================================================
    //  二、equals / hashCode —— 本题最关键的两个方法
    // =====================================================================

    /**
     * 两个盘面相等 ⇔ 边长相同且打包值完全相同。
     *
     * <p><b>为什么必须认真实现它？</b>框架的 {@code AbstractSearcher} 用
     * {@code Set<State> explored} 记录"已经扩展过的状态"，用 Frontier 的索引表
     * 判断"某状态是否已在等待队列里"。这两个判断底层调用的就是 equals 和 hashCode。</p>
     *
     * <p>如果偷懒不写（沿用 Object 的"比较内存地址"默认行为），每个新 new 出来的盘面
     * 都被当成"从未见过的新状态"，同一个盘面会被反复扩展无数次，
     * 搜索树指数爆炸，表现就是<b>程序卡死、几小时跑不出 8 数码</b>。</p>
     *
     * <p>打包之后这里只剩一次 long 比较，是 O(1) 的 —— 这也是打包优化的收益之一。</p>
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof PuzzleBoard)) {
            return false;
        }
        PuzzleBoard another = (PuzzleBoard) obj;
        return this.bits == another.bits && this.size == another.size;
    }

    /**
     * 哈希值必须与 {@link #equals(Object)} 一致：两个相等的盘面必须有相同的哈希值。
     * 这里直接返回构造时算好的值，O(1)。
     */
    @Override
    public int hashCode() {
        return hash;
    }

    // =====================================================================
    //  三、供其他类使用的访问器
    // =====================================================================

    /** @return 盘面边长（3 或 4） */
    public int size() {
        return size;
    }

    /** @return 打包后的 64 位盘面数据（调试/序列化用；一般不需要直接操作） */
    public long bits() {
        return bits;
    }

    /**
     * @return 盘面数据的<b>副本</b>（行优先，0 表示空格）
     */
    public int[] tiles() {
        int n = size * size;
        int[] t = new int[n];
        for (int i = 0; i < n; i++) {
            t[i] = (int) ((bits >>> (4 * i)) & 0xF);
        }
        return t;
    }

    /** @return 空格在盘面中的下标 */
    public int blankIndex() {
        return blank;
    }

    /**
     * 读取某一格的将牌数值。
     *
     * @param row 行号（0 起算）
     * @param col 列号（0 起算）
     * @return 该格的将牌数值，0 表示空格
     */
    public int tileAt(int row, int col) {
        return (int) ((bits >>> (4 * (row * size + col))) & 0xF);
    }

    /**
     * 读取某一格（按一维下标）的将牌数值。
     *
     * @param index 格子下标（0 起算，行优先）
     * @return 该格的将牌数值，0 表示空格
     */
    public int tileAtIndex(int index) {
        return (int) ((bits >>> (4 * index)) & 0xF);
    }

    /** @return 空格所在的行号（0 起算） */
    public int blankRow() {
        return blank / size;
    }

    /** @return 空格所在的列号（0 起算） */
    public int blankCol() {
        return blank % size;
    }

    /**
     * 空格朝方向 d 移动后，会落到哪个下标。
     *
     * @param d 方向
     * @return 目标格下标；若越出盘面则返回 -1（表示这个方向走不通）
     */
    public int indexAfterMove(Direction d) {
        int row = blankRow() + d.rowOffset();
        int col = blankCol() + d.colOffset();
        if (row < 0 || row >= size || col < 0 || col >= size) {
            return -1;                      // 越界：空格贴着这条边，走不出去
        }
        return row * size + col;
    }

    /**
     * 返回「数值 → 下标」的查找表：{@code pos[v] = 将牌 v 在本盘面中的下标}。
     *
     * <p>启发函数需要反复回答"将牌 v 应该在哪一格"。如果每次都遍历盘面去找，
     * 单次估值就是 O(n²)；把这张表缓存到<b>目标盘面</b>上，之后每次查表 O(1)，
     * 整体估值降到 O(n)。搜索要调用启发函数几千万次，这点优化很值。</p>
     *
     * <p>表是惰性构建的：第一次调用时才算，之后直接复用。
     * （搜索是单线程的，不需要考虑并发。）</p>
     *
     * @return 查找表，长度 = n + 1
     */
    public int[] positionIndex() {
        if (positionIndexCache == null) {
            int n = size * size;
            int[] pos = new int[n + 1];
            for (int i = 0; i < n; i++) {
                pos[(int) ((bits >>> (4 * i)) & 0xF)] = i;
            }
            positionIndexCache = pos;
        }
        return positionIndexCache;
    }

    // =====================================================================
    //  四、启发函数 h(n)：从当前盘面到目标盘面还需要多少步的"乐观估计"
    // =====================================================================

    /**
     * 启发函数 h1：<b>不在位将牌数</b>（Misplaced Tiles）。
     *
     * <p>数一数有多少块将牌"不在它应该在的位置上"。每移动一步最多只能让一块将牌归位，
     * 所以它一定 ≤ 真实剩余步数，满足 <b>可采纳性（admissible）</b>，能保证 A* 最优。
     * 但它估计得很松，只能用于小规模问题（3 阶）。</p>
     *
     * <p>空格(0) 不计入——理由见 {@link #manhattan(PuzzleBoard)}。</p>
     *
     * @param goal 目标盘面
     * @return 位置不正确的将牌块数
     */
    public int misplaced(PuzzleBoard goal) {
        int n = size * size;
        int count = 0;
        for (int i = 0; i < n; i++) {
            long shift = 4L * i;
            int v = (int) ((bits >>> shift) & 0xF);
            if (v != 0 && v != (int) ((goal.bits >>> shift) & 0xF)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 启发函数 h2：<b>曼哈顿距离之和</b>（Manhattan Distance）。
     *
     * <p>对每一块将牌，累加「它现在的位置」到「它在目标盘面中的位置」的
     * 行差绝对值 + 列差绝对值。物理含义是"这块将牌至少要挪这么多格"，
     * 而每移动一步最多只能让一块将牌靠近 1 格，所以它同样 ≤ 真实剩余步数，
     * 满足可采纳性，而且估计得比"不在位将牌数"精确得多。</p>
     *
     * <p><b>★★ 关键：空格(0) 必须跳过，不能计入！★★</b><br>
     * 空格的移动是其他将牌移动的"副产品"，我们并不需要专门为空格花步数。
     * 如果把空格也按曼哈顿距离算进去，就会<b>高估</b>真实代价，
     * 启发函数不再 admissible，A* 会丧失最优性（解出来的可能不是最短路径）。
     * 这是本项目最常见的错误之一。</p>
     *
     * @param goal 目标盘面
     * @return 所有将牌（不含空格）的曼哈顿距离之和
     */
    public int manhattan(PuzzleBoard goal) {
        int[] goalPos = goal.positionIndex();     // pos[将牌编号] = 它在目标中的下标
        int[] distance = distanceTable(size);     // 预先算好的"格 i 到格 j"距离表
        int n = size * size;

        int sum = 0;
        for (int i = 0; i < n; i++) {
            int v = (int) ((bits >>> (4 * i)) & 0xF);
            if (v == 0) {
                continue;                         // ★ 空格不计入 ★
            }
            // i*n + goalPos[v] 就是"当前格 → 目标格"这一对在距离表里的位置
            sum += distance[i * n + goalPos[v]];
        }
        return sum;
    }

    /**
     * 启发函数 h3：<b>线性冲突</b>（Linear Conflict）= 曼哈顿距离 + 2 × 冲突对数。
     *
     * <h4>什么是线性冲突？</h4>
     * <p>如果同一行里有两块将牌<b>都已经在自己的目标行上</b>，但它们的左右顺序和目标相反，
     * 那么它们必须"绕一圈"才能交换位置 —— 至少要多花 <b>2 步</b>
     * （其中一块必须先让开，另一块才能过去，然后它再回来）。
     * 列方向同理。</p>
     *
     * <p>所以 {@code h = 曼哈顿 + 2 × 冲突对数} 依然是<b>下界</b>，
     * 满足可采纳性（这是 Heuristic Search 里的经典结论，Korf 等人有证明）。</p>
     *
     * <h4>为什么它对本题特别有用？</h4>
     * <p>它<b>不需要建任何查找表</b>，纯算术、零内存、零建表时间，
     * 但在"行列都反向"的盘面上异常有效 ——
     * 作业要求里的倒序实例是
     * <pre>
     *   15 14 13 12
     *   11 10  9  8
     *    7  6  5  4
     *    3  1  2  _
     * </pre>
     * 第 0 行和第 3 列几乎完全反序，线性冲突一眼就能看出这些"必须多绕路"的结构，
     * 实测给出的估计值是 <b>56</b>，比 5-5-5 模式数据库的 52 还高 4 分。</p>
     *
     * @param goal 目标盘面
     * @return 曼哈顿距离 + 2×冲突对数
     */
    public int linearConflict(PuzzleBoard goal) {
        // ★ 预取"每块将牌的目标行/列"两张小表（缓存在目标盘面上）。
        //   如果直接在循环里写 goalPos[v] / n、goalPos[v] % n，
        //   每个冲突对就要做 1 次除法 + 2 次取模 —— 除法要几十个时钟周期，
        //   而启发函数每秒钟要被调用几百万次，这点开销会被放大成数倍的总耗时。
        int[] goalRow = goal.goalRows();
        int[] goalCol = goal.goalCols();
        int n = size;
        int conflicts = 0;

        // ---------- 行方向 ----------
        for (int r = 0; r < n; r++) {
            for (int c1 = 0; c1 < n; c1++) {
                int v1 = (int) ((bits >>> (4 * (r * n + c1))) & 0xF);
                if (v1 == 0 || goalRow[v1] != r) {
                    continue;                      // 空格、或不在自己的目标行上 → 不参与
                }
                for (int c2 = c1 + 1; c2 < n; c2++) {
                    int v2 = (int) ((bits >>> (4 * (r * n + c2))) & 0xF);
                    if (v2 == 0 || goalRow[v2] != r) {
                        continue;
                    }
                    if (goalCol[v1] > goalCol[v2]) {
                        conflicts++;               // 两者目标列顺序与当前相反 → 一对冲突
                    }
                }
            }
        }

        // ---------- 列方向 ----------
        for (int c = 0; c < n; c++) {
            for (int r1 = 0; r1 < n; r1++) {
                int v1 = (int) ((bits >>> (4 * (r1 * n + c))) & 0xF);
                if (v1 == 0 || goalCol[v1] != c) {
                    continue;                      // 不在自己的目标列上 → 不参与
                }
                for (int r2 = r1 + 1; r2 < n; r2++) {
                    int v2 = (int) ((bits >>> (4 * (r2 * n + c))) & 0xF);
                    if (v2 == 0 || goalCol[v2] != c) {
                        continue;
                    }
                    if (goalRow[v1] > goalRow[v2]) {
                        conflicts++;               // 两者目标行顺序与当前相反 → 一对冲突
                    }
                }
            }
        }

        return manhattan(goal) + 2 * conflicts;
    }

    /**
     * @return 数组 {@code gr}，{@code gr[将牌编号]} = 该将牌在<b>本盘面（当作目标盘面）</b>中的行号。
     *         惰性构建，供 {@link #linearConflict(PuzzleBoard)} 使用。
     */
    private int[] goalRows() {
        if (goalRowCache == null) {
            int[] pos = positionIndex();
            int n = size * size;
            int[] rows = new int[n];
            for (int v = 0; v < n; v++) {
                rows[v] = pos[v] / size;
            }
            goalRowCache = rows;
        }
        return goalRowCache;
    }

    /**
     * @return 数组 {@code gc}，{@code gc[将牌编号]} = 该将牌在<b>本盘面（当作目标盘面）</b>中的列号。
     */
    private int[] goalCols() {
        if (goalColCache == null) {
            int[] pos = positionIndex();
            int n = size * size;
            int[] cols = new int[n];
            for (int v = 0; v < n; v++) {
                cols[v] = pos[v] % size;
            }
            goalColCache = cols;
        }
        return goalColCache;
    }

    /**
     * 启发函数表：HeuristicType → Predictor。
     * 写法与参考实现 {@code stud.problem.pathfinding.Position} 完全一致。
     *
     * <p>Predictor 是框架的<b>函数式接口</b>：{@code int heuristics(State state, State goal)}，
     * 所以这里直接用 lambda 把 State 强转成 PuzzleBoard 后委托给上面的方法即可。</p>
     */
    private static final EnumMap<HeuristicType, Predictor> PREDICTORS =
            new EnumMap<>(HeuristicType.class);

    static {
        // 不在位将牌数
        PREDICTORS.put(MISPLACED,
                (state, goal) -> ((PuzzleBoard) state).misplaced((PuzzleBoard) goal));
        // 曼哈顿距离
        PREDICTORS.put(MANHATTAN,
                (state, goal) -> ((PuzzleBoard) state).manhattan((PuzzleBoard) goal));
        // ★ 第三阶段：组合启发函数
        //
        //   = max( 线性冲突 , 5-5-5相加 , 按列4-4-4-3相加 , 按行4-4-4-3相加 )
        //
        //   每一项都可采纳 ⇒ 取最大值依然可采纳 ⇒ A*/IDA* 仍返回最优解。
        //   第一次被调用时会自动建表（几秒钟），之后一直复用。
        //   实测在作业要求的倒序实例上，启发值从 5-5-5 单独的 52 提升到 56。
        PREDICTORS.put(DISJOINT_PATTERN,
                (state, goal) -> PatternDatabase.combined((PuzzleBoard) state, (PuzzleBoard) goal));
    }

    /**
     * 按启发函数类型取出对应的预测器，供 {@code PuzzleFeeder.getPredictor(...)} 调用。
     *
     * @param type 启发函数类型
     * @return 对应的 Predictor；若该类型不支持则返回 null
     */
    public static Predictor predictor(HeuristicType type) {
        return PREDICTORS.get(type);
    }

    // =====================================================================
    //  五、显示
    // =====================================================================

    /**
     * 把盘面画成带边框的字符画，例如 3×3：
     * <pre>
     * +---+---+---+
     * | 8 | 6 | 7 |
     * +---+---+---+
     * | 2 | 5 | 4 |
     * +---+---+---+
     * | 3 |   | 1 |
     * +---+---+---+
     * </pre>
     * 空格显示为空白（与作业要求 PDF 里用 # 表示空格的画法是同一含义）。
     *
     * @return 多行字符串
     */
    public String toGridString() {
        int width = String.valueOf(size * size - 1).length();   // 每格内容宽度：8数码是1，15数码是2
        String bar = "+" + ("-".repeat(width + 2) + "+").repeat(size);
        String nl = System.lineSeparator();

        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < size; r++) {
            sb.append(bar).append(nl);
            for (int c = 0; c < size; c++) {
                int v = tileAt(r, c);
                String s = (v == 0) ? "" : String.valueOf(v);
                sb.append("| ")
                        .append(" ".repeat(width - s.length()))   // 右对齐，位数不同也能对齐
                        .append(s)
                        .append(' ');
            }
            sb.append('|').append(nl);
        }
        sb.append(bar);
        return sb.toString();
    }

    /**
     * 单行简写形式，形如 {@code 8 6 7 2 5 4 3 0 1}。
     * 主要用于调试（框架的 Node.toString() 会用到）。
     * 需要"好看的棋盘"请用 {@link #toGridString()}。
     */
    @Override
    public String toString() {
        int n = size * size;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append((int) ((bits >>> (4 * i)) & 0xF));
        }
        return sb.toString();
    }
}
