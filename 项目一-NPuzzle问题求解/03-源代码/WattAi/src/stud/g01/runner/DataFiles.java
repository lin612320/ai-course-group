package stud.g01.runner;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 数据文件路径解析工具。
 *
 * <h3>为什么需要它？</h3>
 * <p>框架约定数据文件写成相对路径（例如 {@code resources/problems_stage2.txt}），
 * 但<b>相对路径是相对于"工作目录"解析的</b>，而工作目录由运行方式决定：</p>
 * <ul>
 *   <li>命令行 {@code cd WattAi} 之后运行 → 工作目录是 {@code WattAi}，相对路径能用；</li>
 *   <li>IntelliJ 的 Run Configuration 如果没设 Working directory →
 *       默认是<b>工程根目录</b>，这时 {@code resources/...} 就找不到了，
 *       会报 {@code FileNotFoundException: resources\xxx.txt (系统找不到指定的路径。)}。</li>
 * </ul>
 *
 * <p>这个报错对新手非常不友好（看起来像代码 bug，其实不是）。所以这里做两件事：</p>
 * <ol>
 *   <li><b>自动兜底</b>：按「原路径 → WattAi/原路径 → ../WattAi/原路径」依次尝试；</li>
 *   <li><b>友好报错</b>：都找不到时，把<b>当前工作目录</b>和<b>该怎么改</b>直接打印出来。</li>
 * </ol>
 */
public final class DataFiles {

    /** 工具类，不需要实例化 */
    private DataFiles() {
    }

    /**
     * 解析数据文件，尽最大努力把它找出来。
     *
     * @param path 命令行/运行配置里写的路径
     * @return 实际存在的文件
     * @throws FileNotFoundException 三种候选位置都找不到时抛出，消息里带排查提示
     */
    public static File resolve(String path) throws FileNotFoundException {
        // 候选 1：按写的路径直接用（工作目录 = WattAi 时命中）
        File direct = new File(path);
        if (direct.isFile()) {
            return direct;
        }

        // 候选 2：工作目录是工程根目录时，文件在 WattAi/ 下面
        File underWattAi = new File("WattAi", path);
        if (underWattAi.isFile()) {
            return underWattAi;
        }

        // 候选 3：工作目录在更深一层时，往上找 WattAi/
        File upperWattAi = new File(new File("..", "WattAi"), path);
        if (upperWattAi.isFile()) {
            return upperWattAi;
        }

        // 都找不到 → 给出可操作的提示
        String cwd = System.getProperty("user.dir");
        throw new FileNotFoundException(
                "找不到数据文件：" + path
                        + System.lineSeparator()
                        + "  当前工作目录是：" + cwd
                        + System.lineSeparator()
                        + "  文件实际位置应该是：" + new File(cwd, "WattAi/" + path).getPath()
                        + System.lineSeparator()
                        + "  解决办法（二选一）："
                        + System.lineSeparator()
                        + "    ① IntelliJ：Run → Edit Configurations → 把 Working directory 设为工程目录下的 WattAi"
                        + System.lineSeparator()
                        + "    ② 或者把参数里的路径改成：WattAi/" + path);
    }
}
