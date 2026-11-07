package stud.g01.problem.npuzzle;

import core.problem.Action;

/**
 * N 数码的【动作】：把空格(0)朝某个方向移动一格。
 *
 * <p>它继承框架的 {@link core.problem.Action} 超类。框架对动作只要求两件事：</p>
 * <ul>
 *   <li>{@link #stepCost()} —— 执行这个动作要付出多少代价；</li>
 *   <li>{@link #draw()}     —— 怎么把这个动作显示出来（调试/可视化用）。</li>
 * </ul>
 *
 * <p><b>注意一个容易混淆的地方：</b>「这个动作在某个状态下是否可行」<b>不由 Action 决定</b>，
 * 而是由 {@code NPuzzleProblem.applicable(state, action)} 判断。
 * 原因很简单——同一个动作在不同盘面上的可行性不同：只有当空格紧贴某条边时，
 * "朝那条边的外侧移动"才是不可行的。所以可行性属于「状态 + 动作」组合的性质，
 * 框架把它交给了 Problem 而不是 Action。</p>
 *
 * <p><b>为什么要做实例缓存（CACHE）？</b><br>
 * N 数码一共只有 4 种动作，而且动作本身是<b>不可变</b>的（只包了一个方向枚举）。
 * 搜索过程会生成几十万到几百万个结点，每个结点都新建 4 个 Move 对象，
 * 会产生大量毫无意义的垃圾对象。既然动作不可变，就预先造好 4 个实例反复共享即可。</p>
 */
public class Move extends Action {

    /** 空格移动的方向（该动作的唯一"内容"） */
    private final Direction direction;

    /**
     * 四个方向的共享实例，下标与 {@link Direction#ordinal()} 一一对应。
     * 在静态代码块里一次性造好，之后只读不改。
     */
    private static final Move[] CACHE = new Move[Direction.values().length];

    static {
        for (Direction d : Direction.values()) {
            CACHE[d.ordinal()] = new Move(d);
        }
    }

    /**
     * 私有构造器：外界不能随便 new，只能通过 {@link #of(Direction)} 拿到共享实例。
     * 这样保证"同一个方向 ⇒ 同一个对象"，同时省去大量重复对象。
     *
     * @param direction 空格移动的方向
     */
    private Move(Direction direction) {
        this.direction = direction;
    }

    /**
     * 获取某个方向对应的动作实例（共享、不可变）。
     *
     * @param direction 空格移动的方向
     * @return 该方向对应的 Move 实例
     */
    public static Move of(Direction direction) {
        return CACHE[direction.ordinal()];
    }

    /**
     * @return 空格移动的方向
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * 进入"执行本动作后所到达的状态"需要付出的代价。
     *
     * <p>N 数码中每滑动一步的代价恒为 1，与盘面、位置都无关，所以直接返回 1。
     * 这个 1 决定了 A* 里 g(n)（已走过的实际代价）的累加速度，
     * 也保证了"解路径长度"恰好等于"实际移动步数"。</p>
     *
     * @return 固定返回 1
     */
    @Override
    public int stepCost() {
        return 1;
    }

    /**
     * 在控制台上显示本动作（调试用）。
     */
    @Override
    public void draw() {
        System.out.println(this);
    }

    /**
     * 文本形式：直接使用方向名（UP / DOWN / LEFT / RIGHT），
     * 便于在解路径输出中一眼看出空格是怎么走的。
     */
    @Override
    public String toString() {
        return direction.name();
    }

    /**
     * 两个 Move 相等 ⇔ 方向相同。
     * 因为使用了共享实例，同一方向拿到的本来就是同一个对象。
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Move)) {
            return false;
        }
        Move another = (Move) obj;
        return this.direction == another.direction;
    }

    /**
     * 与 equals 保持一致：方向相同则哈希值相同。
     * （框架的 Node.equals 只比较状态，所以 Action 的哈希在本题里用得很少，
     *   但既然重写了 equals，就必须重写 hashCode，这是 Java 的约定。）
     */
    @Override
    public int hashCode() {
        return direction.hashCode();
    }
}
