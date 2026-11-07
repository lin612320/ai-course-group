package stud.g01.problem.npuzzle;

/**
 * N 数码中【空格(0)】可以移动的四个方向。
 *
 * <p><b>为什么要用「空格视角」而不是「将牌视角」？</b><br>
 * 两者其实是等价的：空格向上移一格，就等于把上方那块将牌滑下来。
 * 但空格在整个盘面上<b>永远只有一个</b>，用空格来描述动作，
 * 就不需要回答"到底是哪一块将牌动了"这个问题，逻辑最简单、最不容易出错。</p>
 *
 * <p>每个方向携带一个 (行偏移, 列偏移)，表示"空格走到哪一格"：
 * <pre>
 *   行号自上而下递增（0,1,2,...），所以"上移"的行偏移是 -1；
 *   列号自左而右递增（0,1,2,...），所以"左移"的列偏移是 -1。
 * </pre>
 *
 * <p>本枚举由 IDEA 使用者参考 {@code stud.problem.pathfinding.Direction} 的写法编写，
 * 属于框架缺失、需要学生自行补充的类。</p>
 */
public enum Direction {

    /** 空格上移：与「上一行、同一列」的将牌交换。行偏移 -1 */
    UP(-1, 0),

    /** 空格下移：与「下一行、同一列」的将牌交换。行偏移 +1 */
    DOWN(1, 0),

    /** 空格左移：与「同一行、左侧」的将牌交换。列偏移 -1 */
    LEFT(0, -1),

    /** 空格右移：与「同一行、右侧」的将牌交换。列偏移 +1 */
    RIGHT(0, 1);

    /** 行偏移量（负数表示向上） */
    private final int rowOffset;

    /** 列偏移量（负数表示向左） */
    private final int colOffset;

    /**
     * @param rowOffset 行偏移量
     * @param colOffset 列偏移量
     */
    Direction(int rowOffset, int colOffset) {
        this.rowOffset = rowOffset;
        this.colOffset = colOffset;
    }

    /** @return 行偏移量（空格移动后行号的变化量） */
    public int rowOffset() {
        return rowOffset;
    }

    /** @return 列偏移量（空格移动后列号的变化量） */
    public int colOffset() {
        return colOffset;
    }
}
