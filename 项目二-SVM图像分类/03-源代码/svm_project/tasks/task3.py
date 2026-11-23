"""Task 3：像素与 HOG 对照、候选选参和服装错误分析。"""
from itertools import product
from config import FASHION_EXTENDED_C, FASHION_EXTENDED_GAMMA
from data import load_dataset
from train import search_parameters, freeze_selection, refit_final_models
from test import evaluate_test
from utils import save_table
from visualize import data_summary


def validate(cfg, data):
    baseline = {"feature": "pixels", "kernel": "linear", "C": 1}
    hog_baseline = {**baseline, "feature": "hog"}
    # 前两行固定 C=1，体现只改变特征的公平对照。
    candidates = [baseline, hog_baseline]
    candidates += [{"feature": f, "kernel": "linear", "C": c}
                   for f, c in product(["pixels", "hog"], [c for c in cfg.c_values if c != 1])]
    if cfg.extended:
        candidates += [{"feature": "pixels", "kernel": "rbf", "C": c,
                        "gamma": gamma, "standardize": True, "pca": .95}
                       for c, gamma in product(FASHION_EXTENDED_C, FASHION_EXTENDED_GAMMA)]
    rows, winner = search_parameters(candidates, {"dataset": cfg.dataset, "hog": cfg.hog},
                                     data.train, data.validation, cfg.selection_metric)
    save_table(cfg.output / "feature_comparison.csv", rows[:2])
    save_table(cfg.output / "search_validation.csv", rows)
    freeze_selection(cfg, data, candidates, rows, winner,
                     {"baseline": baseline, "hog_reference": hog_baseline})


def run(cfg, stage):
    data = load_dataset(cfg)
    if stage == "validate":
        data_summary(cfg.output, [data.train, data.validation, data.test], data.names)
        validate(cfg, data)
    elif stage == "refit":
        refit_final_models(cfg, data)
    else:
        evaluate_test(cfg, data)
