"""实验配置入口：修改候选范围后重新运行 validate，不要据测试结果调参。"""
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent
# 任务书指定的种子。改动它意味着报告里所有数字都要重跑，包括图。
SEED = 42
# digits 先切 20% 留作测试，剩下的再切 1/4 当验证，最后大约 6:2:2
DIGITS_TEST_RATIO = 0.20
DIGITS_VAL_RATIO_IN_DEV = 0.25
# Fashion-MNIST 每类等量抽，开发集 10000、测试集 2000，纯 CPU 跑得动
FASHION_DEV_SIZE = 10000
FASHION_TEST_SIZE = 2000
FASHION_VAL_RATIO = 0.20
# 错例张数照任务书来：Task 1、2 各 5 张，Task 3 不少于 8 张
ERROR_COUNTS = {1: 5, 2: 5, 3: 8}
# 搜索范围只在这里改。改完重跑 validate 就行，不要去动测试集。
C_VALUES = [0.1, 1, 10]
GAMMA_VALUES = ["scale", 0.01, 0.1]
PCA_VALUES = [None, 0.90, 0.95, 0.99]
FASHION_EXTENDED_C = [1, 10]
FASHION_EXTENDED_GAMMA = ["scale", 0.01]
HOG_SETTINGS = {"orientations": 9, "pixels_per_cell": [4, 4],
                "cells_per_block": [2, 2], "block_norm": "L2-Hys"}
FASHION_NAMES = ["T-shirt/top", "Trouser", "Pullover", "Dress", "Coat",
                 "Sandal", "Shirt", "Sneaker", "Bag", "Ankle boot"]


@dataclass
class ExperimentConfig:
    task: int
    dataset: str
    output: Path
    data_dir: Path = ROOT / "datasets/fashion"
    indices_dir: Path = ROOT / "datasets"
    npz: Path | None = None
    seed: int = SEED
    extended: bool = False
    download: bool = False
    digits_test_ratio: float = DIGITS_TEST_RATIO
    digits_val_ratio_in_dev: float = DIGITS_VAL_RATIO_IN_DEV
    fashion_dev_size: int = FASHION_DEV_SIZE
    fashion_test_size: int = FASHION_TEST_SIZE
    fashion_val_ratio: float = FASHION_VAL_RATIO
    error_count: int = 5
    selection_metric: str = "macro_f1"
    c_values: list = field(default_factory=lambda: list(C_VALUES))
    gamma_values: list = field(default_factory=lambda: list(GAMMA_VALUES))
    pca_values: list = field(default_factory=lambda: list(PCA_VALUES))
    hog: dict = field(default_factory=lambda: dict(HOG_SETTINGS))


def get_task_config(task, **overrides):
    if task not in (1, 2, 3):
        raise ValueError("task 必须是 1、2 或 3")
    values = dict(task=task, dataset="fashion" if task == 3 else "digits",
                  output=ROOT / "results" / f"task{task}", error_count=ERROR_COUNTS[task])
    values.update({k: v for k, v in overrides.items() if v is not None})
    return ExperimentConfig(**values)
