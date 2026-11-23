"""原始数据、固定划分和数据来源；不在这里拟合标准化或 PCA。"""
from dataclasses import dataclass
import gzip
import struct
from urllib.request import urlretrieve
import numpy as np
from sklearn.datasets import load_digits
from sklearn.model_selection import train_test_split
from config import FASHION_NAMES
from utils import fingerprint, save_json


@dataclass
class DatasetBundle:
    train: tuple
    validation: tuple
    test: tuple
    signature: str
    names: list


def save_indices(path, **indices):
    """划分结果落盘；第二次跑如果对不上就直接报错，不会悄悄换一份数据。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        with np.load(path, allow_pickle=False) as f:
            if set(f.files) != set(indices) or any(not np.array_equal(f[k], v)
                                                   for k, v in indices.items()):
                raise ValueError(f"已有划分不一致，请改用新的输出/索引目录：{path}")
    else:
        np.savez_compressed(path, **indices)


def load_digits_data(cfg):
    X, y = load_digits(return_X_y=True)
    # 先把 test 切出去，后面 refit、test 都别再碰它
    dev, test = train_test_split(np.arange(len(y)), test_size=cfg.digits_test_ratio,
                                stratify=y, random_state=cfg.seed)
    train, val = train_test_split(dev, test_size=cfg.digits_val_ratio_in_dev,
                                 stratify=y[dev], random_state=cfg.seed)
    # Task 1 和 Task 2 使用同一索引文件；改变划分参数应换一个 indices-dir。
    save_indices(cfg.indices_dir / "digits_indices.npz", train=train, val=val, test=test)
    save_indices(cfg.output / "indices.npz", train=train, val=val, test=test)
    signature = fingerprint(X, y, train, val, test)
    return DatasetBundle(*[(X[i], y[i]) for i in (train, val, test)],
                         signature, list(map(str, range(10))))


def download_fashion(directory):
    base = "https://raw.githubusercontent.com/zalandoresearch/fashion-mnist/master/data/fashion/"
    directory.mkdir(parents=True, exist_ok=True)
    for name in ["train-images-idx3-ubyte.gz", "train-labels-idx1-ubyte.gz",
                 "t10k-images-idx3-ubyte.gz", "t10k-labels-idx1-ubyte.gz"]:
        target = directory / name
        if not target.exists():
            print("Downloading", name, flush=True)
            temporary = target.with_suffix(".partial")
            urlretrieve(base + name, temporary)
            temporary.replace(target)


def read_idx(path):
    with gzip.open(path, "rb") as f:
        magic = struct.unpack(">I", f.read(4))[0]
        if magic not in (2049, 2051):
            raise ValueError(f"IDX 文件格式错误：{path}")
        dims = 1 if magic == 2049 else 3
        shape = struct.unpack(">" + "I" * dims, f.read(4 * dims))
        return np.frombuffer(f.read(), dtype=np.uint8).reshape(shape)


def load_fashion_data(cfg):
    if cfg.download:
        download_fashion(cfg.data_dir)
    if cfg.npz:
        with np.load(cfg.npz, allow_pickle=False) as f:
            X, y, Xt, yt = [f[k] for k in ["x_train", "y_train", "x_test", "y_test"]]
        source = str(cfg.npz.resolve())
    else:
        directory = cfg.data_dir
        X = read_idx(directory / "train-images-idx3-ubyte.gz")
        y = read_idx(directory / "train-labels-idx1-ubyte.gz")
        Xt = read_idx(directory / "t10k-images-idx3-ubyte.gz")
        yt = read_idx(directory / "t10k-labels-idx1-ubyte.gz")
        source = str(directory.resolve())
    for images, labels, count in [(X, y, 60000), (Xt, yt, 10000)]:
        if images.shape != (count, 28, 28) or labels.shape != (count,):
            raise ValueError("需要官方 60000/10000 划分的原始 28×28 Fashion-MNIST 数据")
        if images.dtype != np.uint8 or not np.array_equal(np.unique(labels), np.arange(10)):
            raise ValueError("输入图像须为未归一化 uint8；类别为 0–9")
    if cfg.fashion_dev_size % 10 or cfg.fashion_test_size % 10:
        raise ValueError("按类等量抽样时总样本数须为 10 的倍数")
    rng = np.random.default_rng(cfg.seed)
    # 每类抽同样多。类别一旦不均，Macro-F1 会被小类拖着走，对照就不公平了。
    def sample(labels, n):
        ids = np.concatenate([rng.choice(np.flatnonzero(labels == k), n, replace=False)
                              for k in range(10)])
        rng.shuffle(ids)
        return ids
    dev = sample(y, cfg.fashion_dev_size // 10)
    test = sample(yt, cfg.fashion_test_size // 10)
    train, val = train_test_split(dev, test_size=cfg.fashion_val_ratio,
                                 stratify=y[dev], random_state=cfg.seed)
    save_indices(cfg.output / "indices.npz", train=train, val=val, test=test)
    signature = fingerprint(X[dev], y[dev], Xt[test], yt[test], train, val, test)
    save_json(cfg.output / "data_source.json", {
        "file": source, "dataset": "Fashion-MNIST (verify local NPZ provenance)",
        "official": "https://github.com/zalandoresearch/fashion-mnist",
        "fingerprint": signature, "names": FASHION_NAMES})
    return DatasetBundle((X[train], y[train]), (X[val], y[val]), (Xt[test], yt[test]),
                         signature, FASHION_NAMES)


def load_dataset(cfg):
    cfg.output.mkdir(parents=True, exist_ok=True)
    return load_fashion_data(cfg) if cfg.dataset == "fashion" else load_digits_data(cfg)
