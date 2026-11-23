# SVM 图像分类实验

本项目对应三个课程任务：digits 基线、SVM 对照与调参、Fashion-MNIST 特征比较。使用统一入口，分别运行验证、最终重训和测试。三个 Task 的方案仍保留在独立文件中，方便学生阅读与修改。

## 1　代码结构与阅读顺序

```text
svm_project/
├── config.py              # 种子、路径、数据规模、参数搜索范围
├── main.py                # 命令入口与阶段检查
├── data.py                # 数据读取、抽样、固定划分与索引
├── model.py               # 像素/HOG、标准化、PCA、SVM Pipeline
├── train.py               # 训练、验证选参、最终重训
├── test.py                # 加载最终模型、预测与测试评价
├── visualize.py           # 样例、混淆矩阵、错例和分类报告
├── utils.py               # 配置/CSV 保存、环境记录、指纹与指标
├── tasks/
│   ├── task1.py           # 数字线性基线实验流程
│   ├── task2.py           # 数字三组对照和搜索方案
│   └── task3.py           # 服装特征比较和扩展方案
├── checks/test_workflow.py # 关键实验边界检查
├── requirements.txt
├── VERIFICATION.md        # 本次重构的真实验证记录
├── datasets/              # 运行时生成或下载的数据、索引
└── results/               # 每个任务的实验输出
```

建议从 main.py → tasks/task1.py → model.py/train.py 开始阅读。Task 文件组织“做哪些实验”，公共模块实现“如何训练、评价与展示”。学生修改参数优先看 config.py，增加对照方案看对应 Task，增加特征处理看 model.py。

## 2　环境与数据

建议 Python 3.10 或更新版本。已存在可用虚拟环境时直接使用，不必重建。首次配置示例（Windows PowerShell）：

```powershell
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements.txt
```

下文的 python 指安装好依赖的环境；未激活环境时可替换为 `.venv/Scripts/python.exe`。无需 TensorFlow 或 GPU。digits 随 scikit-learn 安装，Fashion-MNIST 可显式下载或读取本地文件。

## 3　运行三个 Task

### Task 1　基线与验证

```powershell
python main.py --task 1 --stage validate
```

原始 8×8 数字展开为 64 维，线性 SVC、C=1。按类分层得到 1,077 张训练、360 张验证、360 张测试，种子 42。只输出验证成绩、数据样例、混淆矩阵及 5 张错例，不允许在 Task 1 运行 refit 或 test。

### Task 2　对照、选参、最终测试

按顺序执行：

```powershell
python main.py --task 2 --stage validate
python main.py --task 2 --stage refit
python main.py --task 2 --stage test
```

1. validate：复用 Task 1 的数字划分，完成核函数、标准化、PCA 三组固定条件对照，另进行 39 组验证候选搜索。按验证 Macro-F1 选方案，平分选先列出的候选。结果与实际配置写入 CSV 和 selected_config.json。
2. refit：读取已保存的模型和特征处理配置，合并训练、验证数据，共 1,437 张，重新拟合基线与所选模型。标准化/PCA 也重新拟合，但不评价测试数据。
3. test：读取重训记录和 joblib 模型，评价 360 张预留图像。该阶段无 fit，也不搜索参数。

固定条件对照中，RBF 三组使用同一个根据原始训练数据计算的数值 gamma。搜索阶段则允许比较 gamma="scale"。两张结果表分开保存，不能用调参后的提升归因于某一项改动。

### Task 3　服装分类

第一次运行，可下载官方四个 IDX.gz 文件并进行验证：

```powershell
python main.py --task 3 --stage validate --download
python main.py --task 3 --stage refit
python main.py --task 3 --stage test
```

数据默认在 datasets/fashion/。已有其他位置的数据时，在三个阶段均加相同的 `--data-dir`；例如：

```powershell
python main.py --task 3 --stage validate --data-dir "D:/datasets/fashion"
python main.py --task 3 --stage refit --data-dir "D:/datasets/fashion"
python main.py --task 3 --stage test --data-dir "D:/datasets/fashion"
```

也支持 `--npz "路径/fashion-mnist.npz"`，三个阶段传入相同文件。NPZ 须包含 x_train、y_train、x_test、y_test，图像为未归一化 uint8，保持官方 60,000/10,000 划分。不要把手写数字 MNIST 文件当作服装数据；形状检查无法辨别两者。

默认从训练集每类抽取 1,000 张，分成 8,000 张训练、2,000 张验证；官方测试集每类抽取 200 张，共 2,000 张。所有候选共用样本。流程为：

- 固定对照：像素展平＋线性 C=1，与 HOG＋线性 C=1。HOG 为 9 方向、4×4 像素单元、2×2 单元块、L2-Hys。
- 验证搜索：两种特征分别比较 C={0.1,1,10}，共 6 组。
- 可选扩展：validate 时增加 `--extended`，另外比较像素→标准化→95% PCA→RBF，C={1,10}，gamma={scale,0.01}，总共 10 组。
- 最终重训：在 10,000 张开发数据上分别重新拟合像素基线、HOG 固定参照和所选方案，再在 test 阶段评价。

扩展实验建议使用独立目录；后两步不再传 --extended，而是直接读取已选方案：

```powershell
python main.py --task 3 --stage validate --extended --output results/task3_extended
python main.py --task 3 --stage refit --output results/task3_extended
python main.py --task 3 --stage test --output results/task3_extended
```

Task 3 输出每个测试模型至少 8 张错例（不足则全部展示），并按所选模型双向错分总数，选两组混淆类别分别展示。学生依据图像写出原因，不预设哪种方案一定更好。

## 4　数据与输出约定

- 默认路径均相对于项目目录；显式提供的相对路径以当前终端目录为准。
- Task 1、2 共享 datasets/digits_indices.npz；使用 --indices-dir 可以为不同划分指定新的索引目录。已有索引与当前规则不一致会报错，而不静默覆盖。
- 每个 Task 默认写入 results/task编号/。validate、refit、test 三次必须使用相同的 --output 和数据选项。
- config.py 定义候选与默认设置；selected_config.json 保存验证阶段的实际方案及 HOG 设置。refit 不根据当前 config.py 重新猜测已选模型。
- models/ 保存最终 Pipeline，refit_manifest.json 保存使用的数据指纹、选定配置摘要、模型文件摘要与训练时间。配置或模型在重训后变化时，test 会拒绝混用。
- 新实验用新的 --output；已有最终测试结果不能作为继续调参的依据。重复运行会覆盖本阶段同名结果。
- baseline_config.json 是 Task 1 的基线记录，不是 Task 2 的搜索结果；Task 2 可独立运行。

| 主要文件 | 含义 |
|---|---|
| data_counts.csv、samples.png | Task 1/3 数据分布与图像样例 |
| validation.csv | Task 1 的验证成绩 |
| ablation_validation.csv | Task 2 的三组对照（4 行模型） |
| feature_comparison.csv | Task 3 的像素/HOG 固定对照 |
| search_validation.csv | 参数搜索及验证分数 |
| selected_config.json | 验证集选定的参数、特征设置与数据指纹 |
| refit_summary.csv、refit_manifest.json | 最终重训过程与模型记录 |
| test_results.csv | Accuracy、Macro-F1、维数和重训耗时 |
| 各模型_test/ | 分类报告、混淆矩阵、预测明细、错例及数量 |
| confusion_pairs.csv | Task 3 两组混淆类别及两个方向错分比例 |
| environment_阶段.json、run_config_阶段.json | 每个阶段的设备、依赖与运行配置 |

混淆矩阵以真实类别为行、预测类别为列；图中按真实类别归一化，CSV 保存原始计数。训练耗时包括特征处理、标准化/PCA 与 SVM 拟合，不含下载、读文件、预测和作图。test_results.csv 的 fit_seconds 引用 refit 记录，不表示测试阶段发生了训练。

## 5　从旧版迁移

旧版三个根目录 Task 入口的代码已拆到公共模块与 tasks/。新版统一使用 main.py；旧的 --phase final 对应现在依次执行 refit 和 test。本压缩包为独立新版，尚未覆盖桌面原项目。建议解压到新目录运行；原目录和环境保持原样。

不要把旧版 joblib 或 selected_config.json 直接混入新版结果目录：原模型引用的 Python 模块位置已经改变。请用新目录重新运行验证、重训与测试；原数据文件可以继续使用。

## 6　关键流程检查

```powershell
python -m unittest discover -s checks -v
```

检查内容包括：重训只用训练＋验证数据、test 不调用 fit、重训后修改选定配置会被拒绝、数据变化会被拒绝，以及 Task 1 不允许测试。完整数据的重构前后对照结果见 VERIFICATION.md。
