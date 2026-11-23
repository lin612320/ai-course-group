"""通用记录与文件操作；不定义数据划分或实验方案。"""
from dataclasses import asdict
from datetime import datetime
import hashlib
from importlib.metadata import version
import json
import platform
import sys
from pathlib import Path
import numpy as np
import pandas as pd
from sklearn.metrics import accuracy_score, f1_score


def save_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, default=str), encoding="utf-8")


def read_json(path):
    if not Path(path).is_file():
        raise FileNotFoundError(f"缺少文件，请先完成前一阶段：{path}")
    return json.loads(Path(path).read_text(encoding="utf-8"))


def save_table(path, rows):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    pd.DataFrame(rows).to_csv(path, index=False, encoding="utf-8-sig")


def fingerprint(*arrays):
    h = hashlib.sha256()
    for a in arrays:
        a = np.ascontiguousarray(a)
        h.update(str((a.shape, str(a.dtype))).encode())
        h.update(a.tobytes())
    return h.hexdigest()


def json_digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False).encode()).hexdigest()


def file_digest(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def metric_row(y_true, y_pred, name, seconds, parameters, dimensions):
    return {"model": name, "parameters": json.dumps(parameters, sort_keys=True),
            "dimensions": int(dimensions), "accuracy": accuracy_score(y_true, y_pred),
            "macro_f1": f1_score(y_true, y_pred, average="macro", zero_division=0),
            "fit_seconds": seconds}


def record_environment(cfg, stage):
    cfg.output.mkdir(parents=True, exist_ok=True)
    save_json(cfg.output / f"environment_{stage}.json", {
        "time": datetime.now().isoformat(), "python": sys.version,
        "platform": platform.platform(), "processor": platform.processor(),
        "stage": stage, "seed": cfg.seed,
        "libraries": {p: version(p) for p in ["numpy", "pandas", "scikit-learn",
                                             "scikit-image", "matplotlib", "joblib"]},
        "timing": "wall time: feature processing + fitting; excludes I/O and prediction"})
    save_json(cfg.output / f"run_config_{stage}.json", asdict(cfg))
