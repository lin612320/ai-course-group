"""最终测试：只加载最终模型与 predict，禁止在这里拟合模型或搜索参数。"""
import joblib
from utils import read_json, save_json, save_table, json_digest, file_digest, metric_row
from visualize import write_evaluation, plot_confusion_pairs


def evaluate_test(cfg, data):
    manifest = read_json(cfg.output / "refit_manifest.json")
    # 下面几道校验分别防三件事：换了数据、跑完 test 又回头改配置、模型文件被调包
    if manifest.get("schema_version") != 1:
        raise ValueError("重训记录版本不兼容，请重新运行 refit")
    if manifest["task"] != cfg.task or manifest["dataset"] != cfg.dataset:
        raise ValueError("模型记录与当前 Task 不一致")
    if manifest["data_fingerprint"] != data.signature:
        raise ValueError("当前数据与模型训练时不一致")
    current_selection = read_json(cfg.output / "selected_config.json")
    if json_digest(current_selection) != manifest["selection_digest"]:
        raise ValueError("验证配置在重训后发生改变，请重新 refit，不能测试旧模型")
    paths = []
    for record in manifest["models"]:
        path = (cfg.output / record["file"]).resolve()
        if not path.is_relative_to((cfg.output / "models").resolve()):
            raise ValueError("模型路径必须位于本实验的 models 目录")
        if file_digest(path) != record["sha256"]:
            raise ValueError("模型文件与重训记录不一致，请重新 refit")
        paths.append(path)
    X, y = data.test
    rows = []
    for record, path in zip(manifest["models"], paths):
        # 仅加载本项目自己训练的模型；测试阶段绝不调用 fit。
        model = joblib.load(path)
        prediction = model.predict(X)
        name = record["model"]
        rows.append(metric_row(y, prediction, name, record["fit_seconds"],
                               record["parameters"], record["dimensions"]))
        write_evaluation(cfg.output, name + "_test", prediction, X, y, data.names, cfg.error_count)
        if cfg.task == 3 and name == "selected":
            plot_confusion_pairs(cfg.output, prediction, X, y, data.names)
    save_table(cfg.output / "test_results.csv", rows)
    save_json(cfg.output / "test_manifest.json", {
        "task": cfg.task, "data_fingerprint": data.signature, "n_test": len(y),
        "refit_manifest_digest": json_digest(manifest),
        "note": "fit_seconds comes from refit; no fitting during test"})
    print("Test complete:", cfg.output / "test_results.csv")
