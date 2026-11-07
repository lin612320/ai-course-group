package stud.g01.problem.npuzzle;

import java.util.Arrays;

/**
 * 【第三阶段】模式数据库（Pattern Database, PDB）—— 一个模式的"抽象距离查找表"。
 *
 * <h2>一、要解决什么问题</h2>
 * <p>前两个阶段用的启发函数是"不在位将牌数"和"曼哈顿距离"。它们都是把每块将牌
 * <b>单独</b>看待，完全忽略了将牌之间的<b>相互阻挡</b>，所以估计得很松。
 * 在 4 阶的困难实例上（尤其是作业要求里那个"完全倒序"的实例），
 * A* 会因此扩展天文数字的结点，最后内存爆炸。</p>
 *
 * <p>模式数据库的思路是：<b>预先算出"某一小组将牌"需要多少步才能归位</b>，
 * 把这些值全部存进一张表，搜索时直接查表。因为小组内的将牌会互相阻挡，
 * 这个估计比"各自曼哈顿距离之和"更接近真相。</p>
 *
 * <h2>二、抽象状态怎么表示</h2>
 * <p>取一个将牌子集作为"模式"（例如 {1,2,3,4,5}）。抽象状态由两部分构成：</p>
 * <ul>
 *   <li>模式内每块将牌当前所在的格子；</li>
 *   <li><b>空格</b>当前所在的格子（空格必须参与，因为盘面是靠"空格与相邻将牌交换"来变化的）。</li>
 * </ul>
 * <p>模式之外的将牌全部被视为<b>互相无法区分的"其他块"</b>，它们只作为空格移动的"垫脚石"。</p>
 *
 * <p>状态总数（把 k 块模式将牌和 1 个空格放进 n 个格子，各不相同）为：</p>
 * <pre>
 *   n × (n-1) × … × (n-k)   其中 n = 16（4 阶），k = 模式将牌个数
 *
 *   k = 5 时：16×15×14×13×12×11 = 5,765,760     ← 本实现取 5-5-5 划分
 *   k = 4 时：16×15×14×13×12    =   524,160
 * </pre>
 *
 * <h2>三、★ 关键：为什么不能用"每步都算 1"的普通 BFS（可采纳性）</h2>
 * <p>启发函数要能保证 A* 找到<b>最优解</b>，就必须满足 <b>可采纳性（admissible）</b>：
 * 任何状态上的估计值都必须 ≤ 真实剩余步数。</p>
 *
 * <p>如果每个抽象动作都计 1 步，然后把各模式的值<b>相加</b>，会<b>高估</b>真实代价。
 * 用一个 2×2 的小例子就能看穿（目标 <code>[1,2,3,_]</code>，"_"是空格）：</p>
 * <pre>
 *   状态 [1,2,_,3] 的真实距离 = 1（空格右移一格即可）
 *
 *   模式 {1}：把空格从第 2 格挪到第 3 格 → 1 步
 *   模式 {2}：把空格从第 2 格挪到第 3 格 → 1 步
 *   模式 {3}：将牌 3 从第 3 格移到第 2 格 → 1 步
 *   三个相加 = 3  ＞ 真实距离 1     ✗ 不可采纳！
 * </pre>
 *
 * <h4>正确的做法：代价划分（cost partitioning）</h4>
 * <p>规定：<b>只有"移动本模式内的将牌"才计 1 步；空格穿过非本模式将牌（即移动别的将牌）
 * 一律计 0 步。</b>这样就有严谨的证明：</p>
 * <pre>
 *   设真实最优解有 L 步。每一步滑动一块将牌，而每块将牌恰好属于一个模式，
 *   所以把"这一步"记到它所属模式的账上，各模式的记账步数 c_P 之和恰好等于 L。
 *   对模式 P 而言，把整个解投影到 P 的抽象空间，就是一个代价不超过 c_P 的抽象路径，
 *   因此 d_P(状态) ≤ c_P。
 *   对 P 求和： Σ d_P ≤ Σ c_P = L     ✓ 可采纳，保证最优解
 * </pre>
 *
 * <p>再算一遍上面那个 2×2 例子（用 0 代价规则）：</p>
 * <pre>
 *   模式 {1}：将牌 1 已经在目标格；空格随便挪（0 代价）→ 0
 *   模式 {2}：同上 → 0
 *   模式 {3}：将牌 3 需要移动一格 → 1
 *   相加 = 1  = 真实距离       ✓ 正确
 * </pre>
 *
 * <h4>附带的好处：它一定 ≥ 曼哈顿距离</h4>
 * <p>对模式内的每块将牌，每次"被计数"的移动最多让它离目标近一格，
 * 所以 d_P ≥ 模式内各将牌的曼哈顿距离之和。把所有模式加起来，
 * 就得到 <b>启发值 ≥ 整盘的曼哈顿距离</b> —— 也就是说这个启发函数
 * <b>在任何盘面上都不会比曼哈顿距离差</b>，而因为计入了组内阻挡，通常还会更好。</p>
 *
 * <h2>四、0 代价边怎么处理？—— 分层 BFS（0-1 BFS）</h2>
 * <p>因为存在 0 代价边，普通 BFS 不再正确。本实现按"距离层"推进，每一层做两件事：</p>
 * <ol>
 *   <li><b>零代价闭包</b>：从本层状态出发，沿着"0 代价边"（空格穿过其他将牌）不断扩散，
 *       把所有新状态并入本层（用同一层列表动态增长实现）；</li>
 *   <li><b>产生下一层</b>：等本层的零代价闭包完成后，再沿"1 代价边"（移动模式内将牌）
 *       产生下一层的状态。</li>
 * </ol>
 * <p>顺序很重要：必须先把本层"铺满"，再去产生下一层，否则会把本该在 d 层的状态
 * 误判成 d+1 层，启发值偏大，可采纳性就被破坏了。</p>
 *
 * <h2>五、状态怎么编号（完美哈希 / 排名）</h2>
 * <p>把 k+1 个对象（k 块模式将牌 + 空格）依次摆放，按下式算一个混合进制排名：</p>
 * <pre>
 *   rank = 0;  mask = 0（已占用格子的位掩码）
 *   对第 i 个对象（i = 0..k）：
 *       格号 p = 该对象所在格子
 *       它前面还有多少个"空着的格子" = p - popcount(mask 中 &lt; p 的位)
 *       rank = rank × (n - i) + 上面的值
 *       mask 置上第 p 位
 * </pre>
 * <p>每一步的选择数依次是 n、n-1、…、n-k，所以 rank 恰好落在 [0, stateCount) 内，
 * <b>没有空位、没有冲突</b>，可以直接拿来当数组下标。</p>
 */
public final class PatternDatabase {

    /** 四个方向的偏移：上、下、左、右 */
    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};

    /** 盘面边长 */
    private final int size;

    /** 格子总数 size × size */
    private final int cells;

    /** 空格在"对象编号"里的编号，等于模式将牌的个数 k */
    private final int blankObject;

    /** 本模式包含的将牌编号（升序） */
    private final int[] pattern;

    /** 抽象状态总数 = n·(n-1)·…·(n-k) */
    private final int stateCount;

    /** 距离表：下标为 rank，值为抽象距离；-1 表示该抽象状态不可达 */
    private final short[] distance;

    /** valueToObject[将牌编号] = 对象编号；-1 表示该将牌不属于本模式；valueToObject[0] = blankObject */
    private final int[] valueToObject;

    /** 查询时复用的临时数组（长度 k+1），避免每次估值都分配对象。搜索是单线程的，可安全复用。 */
    private final int[] scratchCell;

    /** 目标状态下，各对象（模式将牌 + 空格）应该在的格子 */
    private final int[] goalCells;

    /** 建表耗时（毫秒），用于实验报告 */
    private long buildMillis;

    /** 建表时 BFS 访问到的抽象状态数，用于实验报告 */
    private int visitedStates;

    /**
     * 构造并<b>立刻建表</b>（反向 BFS）。
     *
     * @param size      盘面边长（本实现只支持 3 或 4）
     * @param pattern   本模式包含的将牌编号（升序，长度 k）
     * @param goalBoard 目标盘面（决定"归位"是归到哪里）
     */
    public PatternDatabase(int size, int[] pattern, PuzzleBoard goalBoard) {
        this.size = size;
        this.cells = size * size;
        this.pattern = pattern.clone();
        this.blankObject = pattern.length;

        // ---- 建立"将牌编号 → 对象编号"的映射 ----
        this.valueToObject = new int[16];
        Arrays.fill(valueToObject, -1);
        for (int i = 0; i < pattern.length; i++) {
            valueToObject[pattern[i]] = i;
        }
        valueToObject[0] = blankObject;          // 0 号（空格）固定做最后一个对象

        this.scratchCell = new int[blankObject + 1];

        // ---- 记录目标抽象状态：每块模式将牌、以及空格，各自应该在哪个格子 ----
        this.goalCells = new int[blankObject + 1];
        for (int i = 0; i < pattern.length; i++) {
            goalCells[i] = cellOfValue(goalBoard, pattern[i]);
        }
        goalCells[blankObject] = goalBoard.blankIndex();

        // ---- 算出状态总数并分配距离表 ----
        int count = 1;
        for (int i = 0; i <= blankObject; i++) {
            count *= (cells - i);
        }
        this.stateCount = count;
        this.distance = new short[count];
        Arrays.fill(distance, (short) -1);

        build();
    }

    /**
     * 在盘面上找出某块将牌所在的格子。
     *
     * @param board 盘面
     * @param value 将牌编号
     * @return 格子下标；找不到返回 -1
     */
    private static int cellOfValue(PuzzleBoard board, int value) {
        int n = board.size() * board.size();
        for (int i = 0; i < n; i++) {
            if (board.tileAtIndex(i) == value) {
                return i;
            }
        }
        return -1;
    }

    // =====================================================================
    //  打包 / 解包：把一个抽象状态塞进一个 int（每对象 4 bit）
    // =====================================================================

    /**
     * 把"对象 → 格子"数组打包成一个 int。
     * 每个对象占 4 个二进制位（格子编号 0~15），所以 k+1 ≤ 8 个对象都能装下。
     *
     * @param cellOf cellOf[i] = 第 i 个对象所在格子
     * @return 打包值
     */
    private int pack(int[] cellOf) {
        int packed = 0;
        for (int i = 0; i <= blankObject; i++) {
            packed |= cellOf[i] << (4 * i);
        }
        return packed;
    }

    /** 取打包值里第 obj 个对象所在的格子 */
    private static int cellOf(int packed, int obj) {
        return (packed >>> (4 * obj)) & 0xF;
    }

    /** 把打包值里第 obj 个对象的格子改成 cell，返回新的打包值 */
    private static int withCell(int packed, int obj, int cell) {
        int shift = 4 * obj;
        return (packed & ~(0xF << shift)) | (cell << shift);
    }

    /** 找出格 cell 上放着哪个对象；没有则返回 -1 */
    private int objectAt(int packed, int cell) {
        for (int i = 0; i <= blankObject; i++) {
            if (cellOf(packed, i) == cell) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 计算打包状态的排名（完美哈希），见类注释第五节的公式。
     *
     * @param packed 打包状态
     * @return 排名，落在 [0, stateCount)
     */
    private int rankOf(int packed) {
        int mask = 0;
        int rank = 0;
        for (int i = 0; i <= blankObject; i++) {
            int cell = cellOf(packed, i);
            // 比 cell 小、且还没被前面对象占用的格子个数 = 本对象可选的"第几号"位置
            int freeBelow = cell - Integer.bitCount(mask & ((1 << cell) - 1));
            rank = rank * (cells - i) + freeBelow;
            mask |= 1 << cell;
        }
        return rank;
    }

    // =====================================================================
    //  建表：从抽象目标状态出发的反向 0-1 BFS
    // =====================================================================

    /**
     * 反向搜索（从目标往回搜，这样一遍就能得到"任意状态到目标"的距离）。
     * 代价规则见类注释第三节：移动模式内将牌计 1，其余计 0。
     */
    private void build() {
        long startNanos = System.nanoTime();

        // 两层用的状态列表。每个抽象状态最多只会被放进列表一次，所以容量取 stateCount 就够。
        int[] current = new int[stateCount];
        int[] next = new int[stateCount];

        int goalPacked = pack(goalCells);
        distance[rankOf(goalPacked)] = 0;
        current[0] = goalPacked;
        int currentSize = 1;
        int depth = 0;
        int visited = 1;

        while (currentSize > 0) {
            // ---------- 第 1 步：把本层的"零代价闭包"铺满 ----------
            // 注意 currentSize 在循环中会增长，这正是"0 代价边继续留在本层"的实现方式
            for (int idx = 0; idx < currentSize; idx++) {
                int state = current[idx];
                int blankCell = cellOf(state, blankObject);
                int br = blankCell / size;
                int bc = blankCell % size;

                for (int dir = 0; dir < 4; dir++) {
                    int nr = br + DR[dir];
                    int nc = bc + DC[dir];
                    if (nr < 0 || nr >= size || nc < 0 || nc >= size) {
                        continue;
                    }
                    int target = nr * size + nc;
                    int obj = objectAt(state, target);

                    // 目标格上放的是"模式内的将牌" → 这是 1 代价边，留给第 2 步
                    if (obj >= 0 && obj != blankObject) {
                        continue;
                    }

                    // 否则是"其他将牌" → 0 代价边：只有空格换了位置
                    int moved = withCell(state, blankObject, target);
                    int rank = rankOf(moved);
                    if (distance[rank] == -1) {
                        distance[rank] = (short) depth;
                        current[currentSize++] = moved;
                        visited++;
                    }
                }
            }

            // ---------- 第 2 步：零代价闭包完成后，再产生下一层 ----------
            int nextSize = 0;
            for (int idx = 0; idx < currentSize; idx++) {
                int state = current[idx];
                int blankCell = cellOf(state, blankObject);
                int br = blankCell / size;
                int bc = blankCell % size;

                for (int dir = 0; dir < 4; dir++) {
                    int nr = br + DR[dir];
                    int nc = bc + DC[dir];
                    if (nr < 0 || nr >= size || nc < 0 || nc >= size) {
                        continue;
                    }
                    int target = nr * size + nc;
                    int obj = objectAt(state, target);

                    // 只有"模式内的将牌"才会进来（1 代价边）
                    if (obj < 0 || obj == blankObject) {
                        continue;
                    }

                    // 交换：空格走到 target，那块将牌退到空格原来的位置
                    int moved = withCell(state, blankObject, target);
                    moved = withCell(moved, obj, blankCell);

                    int rank = rankOf(moved);
                    if (distance[rank] == -1) {
                        distance[rank] = (short) (depth + 1);
                        next[nextSize++] = moved;
                        visited++;
                    }
                }
            }

            // 换层：把 next 当成下一轮的 current
            int[] tmp = current;
            current = next;
            next = tmp;
            currentSize = nextSize;
            depth++;
        }

        this.visitedStates = visited;
        this.buildMillis = (System.nanoTime() - startNanos) / 1_000_000;
    }

    // =====================================================================
    //  查询
    // =====================================================================

    /**
     * 查表：给出这个盘面在<b>本模式</b>下的抽象距离。
     *
     * @param board 要估值的盘面
     * @return 该模式下的最小代价（已保证 ≤ 真实剩余步数）
     */
    public int lookup(PuzzleBoard board) {
        long bits = board.bits();

        // 扫一遍盘面（O(n)），把每块模式将牌和空格的位置记到 scratchCell 里。
        // 用"值 → 对象"的映射表，避免为每个模式都写一套查找逻辑。
        for (int i = 0; i < cells; i++) {
            int value = (int) ((bits >>> (4 * i)) & 0xF);
            int obj = valueToObject[value];
            if (obj >= 0) {
                scratchCell[obj] = i;
            }
        }

        int d = distance[rankOf(pack(scratchCell))];
        // 理论上合法盘面一定可达；万一查不到（比如无解盘面），返回 0 保证不会高估
        return d < 0 ? 0 : d;
    }

    // =====================================================================
    //  静态入口：一组模式的建表 + 相加式启发函数
    // =====================================================================

    /**
     * 默认的模式划分：<b>5-5-5</b>。
     *
     * <p>把 15 块将牌分成三组、每组 5 块。之所以这么分，是因为：</p>
     * <ul>
     *   <li>三组<b>互不相交</b>，满足相加式启发函数的前提；</li>
     *   <li>每组 5 块时抽象状态数是 576 万，建表和内存都还能接受（每组约 11.5 MB）；</li>
     *   <li>每组里的将牌在目标盘面中彼此靠得比较近，组内阻挡信息更有价值。</li>
     * </ul>
     * <p>若想换成别的划分（例如 4-4-4-3、6-6-3），改这个数组即可，
     * 但注意每组 6 块会使状态数暴涨到 5766 万，建表时间和内存都会明显上升。</p>
     */
    private static final int[][] DEFAULT_PATTERNS = {
            {1, 2, 3, 4, 5},
            {6, 7, 8, 9, 10},
            {11, 12, 13, 14, 15}
    };

    /** 3 阶（8 数码）的模式划分：把 1~8 分成 3-3-2 三组 */
    private static final int[][] PATTERNS_3 = {
            {1, 2, 3},
            {4, 5, 6},
            {7, 8}
    };

    /**
     * 4 阶的<b>加强</b>划分：<b>6-6-3</b>。
     *
     * <p>每组更大 ⇒ 组内阻挡信息更完整 ⇒ 启发值更强（更接近真实距离）⇒ 搜索结点数大幅减少。</p>
     * <p>代价：每组 6 块的抽象状态数是
     * {@code 16×15×14×13×12×11×10 = 57,657,600}（是 5 块组的 10 倍），
     * 距离表从 11 MB 涨到约 115 MB，建表时间和内存都会明显上升。</p>
     * <p>用 {@code -Dnpuzzle.pdb=663} 启用。</p>
     */
    private static final int[][] PATTERNS_4_663 = {
            {1, 2, 3, 4, 5, 6},
            {7, 8, 9, 10, 11, 12},
            {13, 14, 15}
    };

    /**
     * 4 阶：按<b>目标行</b>划分 4-4-4-3。
     * <p>目标盘面是行优先的 {@code 1..15}，所以 {1,2,3,4} 正好是目标的第一行。</p>
     */
    private static final int[][] PATTERNS_4_ROWS = {
            {1, 2, 3, 4},
            {5, 6, 7, 8},
            {9, 10, 11, 12},
            {13, 14, 15}
    };

    /**
     * 4 阶：按<b>目标列</b>划分 4-4-4-3。
     *
     * <p>这是本工程实测出来的"秘密武器"：倒序实例的盘面是<b>行列都几乎完全反向</b>的，
     * 按列划分恰好抓住了 5-5-5 漏掉的纵向结构 ——
     * 在倒序实例上它单独就给出 <b>56</b>，比 5-5-5 的 52 高出 4 分。
     * 而它的表极小（每组 4 块只有 52 万状态），建表只要零点几秒。</p>
     */
    private static final int[][] PATTERNS_4_COLS = {
            {1, 5, 9, 13},
            {2, 6, 10, 14},
            {3, 7, 11, 15},
            {4, 8, 12}
    };

    /**
     * 切换 4 阶模式划分的系统属性名：
     * {@code -Dnpuzzle.pdb=663} 改用 6-6-3，默认（或不设）用 5-5-5。
     */
    public static final String PDB_MODE_PROPERTY = "npuzzle.pdb";

    /**
     * 按阶数选择该阶数要建<b>哪几套</b>划分方案。
     *
     * <p><b>为什么要多套？</b>不同划分各自擅长捕捉不同的结构：</p>
     * <ul>
     *   <li>5-5-5 三组均衡，通用性最好；</li>
     *   <li>按列 4-4-4-3 擅长纵向错位（倒序实例上比 5-5-5 高 4 分）；</li>
     *   <li>按行 4-4-4-3 擅长横向错位。</li>
     * </ul>
     * <p>它们<b>各自都是可采纳的</b>，所以取最大值依然可采纳（见 {@link #combined}），
     * 而结果一定不会比任何单独一套差。</p>
     *
     * <p><b>为什么必须分阶数？</b>4 阶的划分里有一组是 {11,12,13,14,15}，
     * 而 3 阶盘面上一共只有 1~8 号将牌 —— 这一组<b>一块将牌都不存在</b>，
     * 查表时会算出毫无意义的启发值。所以 3 阶必须另给一套划分。</p>
     *
     * @param size 盘面边长
     * @return 该阶数要建的若干套划分方案
     */
    private static int[][][] patternSetsFor(int size) {
        if (size == 3) {
            return new int[][][]{PATTERNS_3};
        }
        if (size == 4) {
            String mode = System.getProperty(PDB_MODE_PROPERTY, "555");
            if ("663".equals(mode)) {
                // 保留 6-6-3 供对比实验：实测它并不更强（详见实验报告），默认不启用
                return new int[][][]{PATTERNS_4_663};
            }
            if ("multi".equals(mode)) {
                // 建全部三套，供"多套取 max"的对比实验
                return new int[][][]{DEFAULT_PATTERNS, PATTERNS_4_COLS, PATTERNS_4_ROWS};
            }
            // 默认只用 5-5-5 这一套：
            // 实测 "max(5-5-5, 线性冲突)" 与 "max(全部三套 + 线性冲突)" 在测试实例上结果完全相同，
            // 但前者建表只要 2.4 秒 / 33 MB（后者要多建 8 张小表，且每次估值多 8 次查表），
            // 所以默认走最划算的方案。
            return new int[][][]{DEFAULT_PATTERNS};
        }
        // 其它阶数兜底：每块将牌单独成一个模式。
        // 此时各模式互不干扰，启发值恰好等于曼哈顿距离 —— 弱，但绝对安全、一定可采纳。
        int tileCount = size * size - 1;
        int[][] singles = new int[tileCount][1];
        for (int i = 1; i <= tileCount; i++) {
            singles[i - 1] = new int[]{i};
        }
        return new int[][][]{singles};
    }

    /**
     * 已建好的模式数据库缓存，<b>按阶数分开缓存</b>（下标是盘面边长）。
     *
     * <p>这样当一份数据文件里同时存在 3 阶和 4 阶实例时（例如老师的 problems3.txt），
     * 不会因为两种阶数来回切换而反复重建那 33 MB 的距离表。</p>
     */
    private static final PatternDatabase[][][] CACHE = new PatternDatabase[5][][];

    /** 各阶数缓存对应的目标盘面打包值（判断目标是否换过） */
    private static final long[] CACHE_KEY = new long[5];

    /** 缓存有效性标记（打包值可能是 0，所以不能只用 CACHE_KEY 判断） */
    private static final boolean[] CACHE_VALID = new boolean[5];

    /**
     * 按给定的划分方案建一组模式数据库。
     *
     * @param goal     目标盘面
     * @param patterns 划分方案，每个元素是一组将牌编号
     * @return 建好的一组模式数据库
     */
    public static PatternDatabase[] build(PuzzleBoard goal, int[][] patterns) {
        PatternDatabase[] databases = new PatternDatabase[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            databases[i] = new PatternDatabase(goal.size(), patterns[i], goal);
        }
        return databases;
    }

    /**
     * 取出（必要时先建好）该阶数下的<b>若干套</b>模式数据库。
     *
     * <p>建表要几秒钟（4 阶最大的一组有 576 万抽象状态），所以只在第一次用到时做，
     * 之后一直复用。方法加了 {@code synchronized}，避免多线程重复建表。</p>
     *
     * @param goal 目标盘面
     * @return 该阶数下的多套模式数据库
     */
    public static synchronized PatternDatabase[][] forGoal(PuzzleBoard goal) {
        int size = goal.size();
        long key = goal.bits();

        if (CACHE_VALID[size] && CACHE_KEY[size] == key) {
            return CACHE[size];                      // 命中缓存，开销只有一次数组访问和一次比较
        }

        int[][][] sets = patternSetsFor(size);
        System.out.println("[模式数据库] 开始建表（" + size + " 阶，共 " + sets.length + " 套划分）……");
        long start = System.nanoTime();

        PatternDatabase[][] built = new PatternDatabase[sets.length][];
        long totalBytes = 0;
        for (int s = 0; s < sets.length; s++) {
            built[s] = build(goal, sets[s]);
            for (PatternDatabase pdb : built[s]) {
                totalBytes += pdb.tableBytes();
                System.out.printf("[模式数据库]   第%d套 %-30s 状态 %,12d，耗时 %6d ms，表 %6.2f MB%n",
                        s + 1, java.util.Arrays.toString(pdb.pattern()),
                        pdb.stateCount(), pdb.buildMillis(), pdb.tableBytes() / 1024.0 / 1024.0);
            }
        }
        System.out.printf("[模式数据库] 建表完成：总耗时 %d ms，距离表共占用 %.1f MB%n",
                (System.nanoTime() - start) / 1_000_000, totalBytes / 1024.0 / 1024.0);

        CACHE[size] = built;
        CACHE_KEY[size] = key;
        CACHE_VALID[size] = true;
        return built;
    }

    /** 把一组模式数据库的值全部相加 */
    private static int sumOf(PatternDatabase[] set, PuzzleBoard state) {
        int sum = 0;
        for (PatternDatabase pdb : set) {
            sum += pdb.lookup(state);
        }
        return sum;
    }

    /**
     * <b>相加式模式数据库启发函数</b>：把同一套划分里各模式的抽象距离相加，再取各套中的最大值。
     *
     * <p>可采纳性证明见类注释第三节：因为采用了"代价划分"（只有移动本模式内将牌才计步），
     * 所以同一套里各模式之和一定 ≤ 真实剩余步数；各套都满足这一点，
     * 因此<b>取最大值仍然 ≤ 真实剩余步数</b>，A\* / IDA\* 用它依然能找到<b>最优解</b>。</p>
     *
     * @param state 当前盘面
     * @param goal  目标盘面
     * @return 启发值
     */
    public static int additive(PuzzleBoard state, PuzzleBoard goal) {
        int best = 0;
        for (PatternDatabase[] set : forGoal(goal)) {
            int sum = sumOf(set, state);
            if (sum > best) {
                best = sum;
            }
        }
        return best;
    }

    /**
     * ★★★ <b>组合启发函数</b>（第三阶段的最终版本）：取所有候选启发函数里的<b>最大值</b>。
     *
     * <p>候选包括：</p>
     * <ol>
     *   <li>{@link PuzzleBoard#linearConflict(PuzzleBoard) 线性冲突}（曼哈顿 + 2×冲突对数）——
     *       <b>纯算术、零建表成本</b>，但在"行列都反向"的盘面上异常有效；</li>
     *   <li>5-5-5 划分的相加值（通用性最好）；</li>
     *   <li>按列 4-4-4-3 划分的相加值（擅长纵向错位）；</li>
     *   <li>按行 4-4-4-3 划分的相加值（擅长横向错位）。</li>
     * </ol>
     *
     * <h4>为什么取 max 一定安全？</h4>
     * <p>可采纳性的定义是"估计值 ≤ 真实剩余步数"。上面每一项都满足这个性质，
     * 那么它们的<b>最大值也一定满足</b>（比若干个不超过 L 的数，最大者也不超过 L）。
     * 所以取 max 不会破坏最优性 —— 这是"组合启发函数"最省事也最保险的做法。</p>
     *
     * <h4>实测收益</h4>
     * <p>在作业要求的倒序实例上，启发值从 5-5-5 单独的 <b>52</b> 提升到 <b>56</b>；
     * 在其它实例上也不会比任何单项差（因为取了 max）。
     * 而多出来的成本很小：按行/按列那两套每组只有 4 块（52 万状态），建表总耗时不到 1 秒。</p>
     *
     * @param state 当前盘面
     * @param goal  目标盘面
     * @return 启发值（保证可采纳）
     */
    public static int combined(PuzzleBoard state, PuzzleBoard goal) {
        // 1) 线性冲突：不需要任何查找表，直接算
        int best = state.linearConflict(goal);

        // 2) 各套模式数据库的相加值
        for (PatternDatabase[] set : forGoal(goal)) {
            int sum = sumOf(set, state);
            if (sum > best) {
                best = sum;
            }
        }
        return best;
    }

    // =====================================================================
    //  供实验报告使用的元信息
    // =====================================================================

    /** @return 本模式包含的将牌编号 */
    public int[] pattern() {
        return pattern.clone();
    }

    /** @return 抽象状态总数 */
    public int stateCount() {
        return stateCount;
    }

    /** @return 建表时实际访问到的状态数 */
    public int visitedStates() {
        return visitedStates;
    }

    /** @return 建表耗时（毫秒） */
    public long buildMillis() {
        return buildMillis;
    }

    /** @return 距离表占用的字节数（short 数组） */
    public long tableBytes() {
        return (long) distance.length * 2;
    }
}
