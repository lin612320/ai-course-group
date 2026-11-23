"""可视化与报告：接收已得到的预测，不训练模型。"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from sklearn.metrics import classification_report, confusion_matrix, ConfusionMatrixDisplay
from utils import save_json, save_table

def data_summary(out, sets, names):
    rows = [{"split": name, "class": k, "label": names[k], "count": int(sum(y == k))}
            for name, (_, y) in zip(["train", "validation", "test"], sets) for k in range(10)]
    save_table(out / "data_counts.csv", rows)
    X, y = sets[0]
    ids = [np.flatnonzero(y == k)[0] for k in range(10)]
    plot_samples(out / "samples.png", X, y, ids, names)


def plot_samples(path, X, y, ids, names, pred=None):
    if not len(ids):
        return
    cols = 4 if len(ids) > 6 else 3
    fig, axes = plt.subplots(int(np.ceil(len(ids) / cols)), cols,
                             figsize=(cols * 3.5, int(np.ceil(len(ids) / cols)) * 3))
    for ax in np.asarray(axes).flat:
        ax.axis("off")
    for ax, i in zip(np.asarray(axes).flat, ids):
        side = int(np.sqrt(X[i].size))
        ax.imshow(X[i].reshape(side, side), cmap="gray")
        title = f"True: {names[int(y[i])]}"
        if pred is not None:
            title += f"\nPred: {names[int(pred[i])]}"
        ax.set_title(title, fontsize=9)
    fig.tight_layout()
    fig.savefig(path, dpi=130)
    plt.close(fig)


def write_evaluation(out, name, pred, X, y, names, n_errors):
    folder = out / name
    folder.mkdir(parents=True, exist_ok=True)
    labels = np.arange(10)
    report = classification_report(y, pred, labels=labels, target_names=names,
                                   digits=4, zero_division=0)
    (folder / "classification_report.txt").write_text(report, encoding="utf-8")
    # 图按真实类别归一化，看的是"这一类里错了多少"；CSV 存原始计数，方便回查具体张数
    cm = confusion_matrix(y, pred, labels=labels)
    np.savetxt(folder / "confusion_counts.csv", cm, delimiter=",", fmt="%d")
    fig, ax = plt.subplots(figsize=(10, 8))
    ConfusionMatrixDisplay.from_predictions(y, pred, labels=labels, display_labels=names,
        normalize="true", values_format=".2f", xticks_rotation=45, ax=ax)
    ax.set_title(name)
    fig.tight_layout()
    fig.savefig(folder / "confusion_matrix.png", dpi=130)
    plt.close(fig)
    wrong = np.flatnonzero(y != pred)
    shown = wrong[:n_errors]
    plot_samples(folder / "errors.png", X, y, shown, names, pred)
    save_json(folder / "errors.json", {"total": len(wrong), "shown": len(shown),
                                       "indices_in_evaluation_set": shown.tolist()})
    save_table(folder / "predictions.csv", [{"index": i, "true": int(a), "predicted": int(b)}
                                       for i, (a, b) in enumerate(zip(y, pred))])
    return pred


def plot_confusion_pairs(out, pred, X, y, names):
    cm = confusion_matrix(y, pred, labels=np.arange(10))
    # 按两个方向的错分加起来排序：只看单向会漏掉"这一对互相认错"的情况
    pairs = sorted([(int(cm[a,b] + cm[b,a]), a, b) for a in range(10)
                    for b in range(a+1,10)], reverse=True)
    rows = []
    for count, a, b in pairs[:2]:
        rows.append({"class_a": names[a], "class_b": names[b], "both_directions": count,
                     "a_to_b_rate": float(cm[a,b] / sum(y == a)),
                     "b_to_a_rate": float(cm[b,a] / sum(y == b))})
        ids = np.flatnonzero(((y == a) & (pred == b)) | ((y == b) & (pred == a)))[:8]
        plot_samples(out / f"confusion_pair_{a}_{b}.png", X, y, ids, names, pred)
    save_table(out / "confusion_pairs.csv", rows)
