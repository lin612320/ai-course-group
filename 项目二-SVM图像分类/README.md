# 项目二 · SVM 图像分类

用支持向量机完成手写数字与服装图像分类：先建基线，再做对照实验，最后把改进方法用到 Fashion-MNIST。

## 题目要求与进度

| 任务 | 要求 | 当前状态 |
|---|---|---|
| Task 1 | digits（8×8，64 维）分层划分 6:2:2，线性核 C=1 基线，出验证准确率、混淆矩阵、≥5 张错例 | 代码就绪，待跑（#5） |
| Task 2 | 核函数 / 标准化 / PCA 三组单变量对照 + 自主策略 + 验证集选参，合并训练验证重训后评测试集 | 代码就绪，策略待做（#7 #8 #10） |
| Task 3 | Fashion-MNIST 每类抽 1000（8000/2000）+ 官方测试每类 200（共 2000），像素 vs HOG 对照 + 自主策略 + ≥8 张错例 + 两组易混类别成因 | 数据已入库，待跑（#2 #3 #4） |

项目总 DDL：**2026-12-14**（三周）。里程碑（Milestone #1）与逐人任务 issue 已挂在仓库 Issues 里，编号见上表括号。

指标口径：Accuracy 与 Macro-F1 双指标，混淆矩阵真实类别为行、预测类别为列；计时含特征处理与模型训练，不含下载；标准化与 PCA 只在训练数据上拟合，测试集只评一次。

## 四个人各做了什么

| 姓名 | 负责 | 对应 issue |
|---|---|---|
| 林子航（组长） | Task 3 全部 + 公共模块维护 + 集成统稿 | #1 #2 #3 #4 |
| 周以撒 | Task 1 全流程 + 数据来源与划分留痕 + 复核 Task 2 数字 | #5 #6 |
| 张文璟 | Task 2 三组对照、39 组选参、refit/test、公共模块回归验证 | #7 #8 #9 |
| 李行 | Task 2 自主策略 + 图表规范 + 报告排版 + 复核 Task 3 数字 | #10 #11 #12 |

完整任务拆解、验收标准、排期与风险预案见 [`04-文档/G07-P02-分工方案.md`](04-文档/G07-P02-分工方案.md)。

## 代码结构

```
svm_project/
├── config.py      种子、路径、数据规模、候选范围
├── main.py        命令入口与阶段检查（--task / --stage）
├── data.py        数据读取、分层抽样、固定划分与索引
├── model.py       像素 / HOG、标准化、PCA、SVM 的 Pipeline
├── train.py       训练、验证选参、合并重训（唯一执行 fit 的模块）
├── test.py        加载最终模型、预测、测试评价（禁止 fit）
├── visualize.py   样例、混淆矩阵、错例、分类报告
├── utils.py       配置与 CSV 记录、环境记录、指纹与指标
├── tasks/         三个 Task 各自的实验方案
└── checks/        关键流程检查（5 项）
```

设计上把「做哪些实验」（`tasks/`）与「怎么训练与评价」（公共模块）分开：改参数看 `config.py`，加对照方案看对应 Task，加特征处理看 `model.py`。

## 怎么跑

见 [`03-源代码/运行说明.md`](03-源代码/运行说明.md)。最短路径：

```
cd 03-源代码/svm_project
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements.txt
python main.py --task 1 --stage validate
```

依赖：Python 3.10+、numpy、pandas、scikit-learn、scikit-image、matplotlib、joblib。纯 CPU 即可。

## 三条硬约束

1. `validate`、`refit`、`test` 三次用同一 `--output` 与同一数据选项，否则指纹校验直接拒绝。
2. 测试集只评一次；出结果后不回头改方案。
3. 公共模块 `config.py` / `model.py` / `train.py` / `test.py` / `utils.py` / `visualize.py` 只有组长有写权限，其他人提《改动申请》。

## 目录

```
01-研究报告/   报告正文（待成稿：docx + markdown 源文件）
02-演示/       汇报用 pptx / pdf（待做）
03-源代码/     svm_project 工程 + 运行说明
04-文档/       分工方案、任务书要点
05-工具脚本/   出图样式与报告排版脚本（待补）
06-资源/数据/   digits.gz + fashion/ 四个官方 IDX.gz（已入库，免联网）
07-提交件/     G07-P02-report.pdf、contribution.xlsx、提交包（待补）
```

## 数据

数据已入库，`--data-dir "../../06-资源/数据/fashion"` 直接可用（从 `03-源代码/svm_project/` 出发的相对路径）：

| 文件 | 内容 |
|---|---|
| `06-资源/数据/digits.gz` | 1797 行，前 64 列像素（0–16）+ 末列标签，与 `load_digits()` 逐元素一致 |
| `06-资源/数据/fashion/train-images-idx3-ubyte.gz` | 60000 × 28 × 28，uint8 |
| `06-资源/数据/fashion/train-labels-idx1-ubyte.gz` | 60000，标签 0–9，每类 6000 |
| `06-资源/数据/fashion/t10k-images-idx3-ubyte.gz` | 10000 × 28 × 28，uint8 |
| `06-资源/数据/fashion/t10k-labels-idx1-ubyte.gz` | 10000，标签 0–9，每类 1000 |

来源：Fashion-MNIST 官方仓库 `zalandoresearch/fashion-mnist`。运行后 `data_source.json` 会记录实际读取路径与数据指纹；`results/`、`models/` 仍不进仓库。
