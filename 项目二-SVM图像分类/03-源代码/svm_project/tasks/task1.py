"""Task 1：原始像素与线性 SVM 基线，只评价验证集。"""
from data import load_dataset
from train import validation_candidate
from utils import save_json, save_table
from visualize import data_summary, write_evaluation


def run(cfg, stage):
    if stage != "validate":
        raise ValueError("Task 1 只运行 validate；最终测试安排在 Task 2")
    data = load_dataset(cfg)
    data_summary(cfg.output, [data.train, data.validation, data.test], data.names)
    parameters = {"kernel": "linear", "C": 1}
    _, prediction, row = validation_candidate(parameters, {"dataset": "digits"},
                                               data.train, data.validation, "D0")
    save_table(cfg.output / "validation.csv", [row])
    write_evaluation(cfg.output, "D0_validation", prediction, *data.validation,
                     data.names, cfg.error_count)
    save_json(cfg.output / "baseline_config.json", {
        "data_fingerprint": data.signature, "model": parameters})
