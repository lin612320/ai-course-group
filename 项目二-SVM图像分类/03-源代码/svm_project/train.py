"""训练与验证：唯一执行 fit 的公共模块；不评价测试集。"""
from time import perf_counter
import joblib
import numpy as np
from model import build_pipeline
from utils import (save_json, read_json, save_table, metric_row,
                   json_digest, file_digest)


def fit_model(model, X, y):
    """计时包括 Pipeline 内的特征处理和拟合，不含预测和下载。"""
    start = perf_counter()
    model.fit(X, y)
    return perf_counter() - start


def validation_candidate(parameters, settings, train_set, val_set, name):
    model = build_pipeline(parameters, settings)
    seconds = fit_model(model, *train_set)
    prediction = model.predict(val_set[0])
    row = metric_row(val_set[1], prediction, name, seconds, parameters,
                     model.steps[-1][1].n_features_in_)
    return model, prediction, row


def run_comparisons(experiments, settings, train_set, val_set):
    rows = []
    for name, parameters in experiments.items():
        _, _, row = validation_candidate(parameters, settings, train_set, val_set, name)
        rows.append(row)
    return rows


def search_parameters(candidates, settings, train_set, val_set, selection_metric="macro_f1"):
    rows = []
    for i, parameters in enumerate(candidates):
        print(f"Validation candidate {i+1}/{len(candidates)}: {parameters}", flush=True)
        _, _, row = validation_candidate(parameters, settings, train_set, val_set, f"candidate_{i}")
        rows.append(row)
    # 平分时选最早列出的候选；候选顺序固定，不使用测试结果或运行耗时破平局。
    winner = max(range(len(rows)), key=lambda i: rows[i][selection_metric])
    return rows, winner


def freeze_selection(cfg, data, candidates, rows, winner, reference_models):
    selected = {**reference_models, "selected": candidates[winner]}
    value = {"schema_version": 1, "task": cfg.task, "dataset": cfg.dataset,
             "data_fingerprint": data.signature,
             "pipeline_settings": {"dataset": cfg.dataset, "hog": cfg.hog},
             "models": selected,
             "selection_rule": f"max validation {cfg.selection_metric}, ties: first candidate",
             "validation_result": rows[winner]}
    save_json(cfg.output / "selected_config.json", value)
    print("Validation complete. Next: --stage refit, then --stage test")


def read_selection(cfg, data):
    selected = read_json(cfg.output / "selected_config.json")
    if selected.get("schema_version") != 1:
        raise ValueError("配置版本不兼容，请在新结果目录运行 validate")
    if selected["task"] != cfg.task or selected["dataset"] != cfg.dataset:
        raise ValueError("选定配置与当前 Task 不一致")
    if selected["data_fingerprint"] != data.signature:
        raise ValueError("当前数据与验证阶段不一致，不能继续重训")
    return selected


def refit_final_models(cfg, data):
    selected = read_selection(cfg, data)
    X = np.concatenate([data.train[0], data.validation[0]])
    y = np.concatenate([data.train[1], data.validation[1]])
    folder = cfg.output / "models"
    folder.mkdir(parents=True, exist_ok=True)
    records = []
    # 重训不用 config.py 中当前的模型参数/HOG 参数，而使用验证时保存的完整设置。
    for name, parameters in selected["models"].items():
        print("Refitting", name, flush=True)
        model = build_pipeline(parameters, selected["pipeline_settings"])
        seconds = fit_model(model, X, y)
        path = folder / f"{name}.joblib"
        joblib.dump(model, path)
        records.append({"model": name, "file": path.relative_to(cfg.output).as_posix(),
                        "sha256": file_digest(path), "parameters": parameters,
                        "dimensions": int(model.steps[-1][1].n_features_in_),
                        "fit_seconds": seconds})
    manifest = {"schema_version": 1, "task": cfg.task, "dataset": cfg.dataset,
                "data_fingerprint": data.signature, "selection_digest": json_digest(selected),
                "selected_config": selected, "n_train": len(y), "models": records}
    save_json(cfg.output / "refit_manifest.json", manifest)
    save_table(cfg.output / "refit_summary.csv", records)
    print("Refit complete. Test data have not been evaluated.")
