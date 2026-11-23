"""Task 2：定义数字对照关系，调用公共训练与测试模块。"""
from itertools import product
from data import load_dataset
from train import run_comparisons, search_parameters, freeze_selection, refit_final_models
from test import evaluate_test
from utils import save_table


def validate(cfg, data):
    X, _ = data.train
    # 这就是 sklearn 里 gamma="scale" 在原始像素上算出来的值：1 / (特征数 × 方差)。
    # 三组对照共用这一个数，标准化和 PCA 之后也不重算。任务书要求"固定参数"，
    # 代价是标准化会让这个 gamma 显得偏大，D2 掉分多半就出在这儿，报告里要解释清楚。
    g = float(1 / (X.shape[1] * X.var()))
    baseline = {"kernel": "linear", "C": 1}
    rbf = {"kernel": "rbf", "C": 1, "gamma": g}
    experiments = {"D0": baseline, "D1": rbf,
                   "D2": {**rbf, "standardize": True},
                   "D3": {**rbf, "standardize": True, "pca": .95}}
    settings = {"dataset": cfg.dataset, "hog": cfg.hog}
    rows = run_comparisons(experiments, settings, data.train, data.validation)
    save_table(cfg.output / "ablation_validation.csv", rows)

    # 搜索与单变量对照分开：不能把搜索后的总提升归因于其中某个因素。
    candidates = [baseline]
    candidates += [{"kernel": "linear", "C": c} for c in cfg.c_values if c != 1]
    candidates += [{"kernel": "rbf", "C": c, "gamma": gamma,
                    "standardize": True, "pca": pca}
                   for c, gamma, pca in product(cfg.c_values, cfg.gamma_values, cfg.pca_values)]
    rows, winner = search_parameters(candidates, settings, data.train, data.validation,
                                     cfg.selection_metric)
    save_table(cfg.output / "search_validation.csv", rows)
    freeze_selection(cfg, data, candidates, rows, winner, {"baseline": baseline})


def run(cfg, stage):
    data = load_dataset(cfg)
    if stage == "validate":
        validate(cfg, data)
    elif stage == "refit":
        refit_final_models(cfg, data)
    else:
        evaluate_test(cfg, data)
