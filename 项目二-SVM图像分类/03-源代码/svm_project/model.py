"""由配置构建完整 Pipeline；不读取数据文件、不执行训练。

新增特征只需两步，不改 build_pipeline 主体：
1. 在独立模块里定义特征函数，签名与 pixels / hog 一致：吃图像数组，返回二维特征矩阵；
2. 调用 register_feature("名字", 函数, settings_key=...) 注册一行。

参数从 settings 里取：settings_key="hog" 表示把 settings["hog"] 作为 kw_args 传给函数；
settings_key=None 表示该特征没有超参数。
"""
from dataclasses import dataclass
from typing import Callable

import numpy as np
from skimage.feature import hog
from sklearn.decomposition import PCA
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler, FunctionTransformer
from sklearn.svm import SVC


@dataclass(frozen=True)
class FeatureSpec:
    """一个可注册的特征：函数本体 + 参数来源。"""

    function: Callable
    settings_key: str | None = None  # 从 settings 取哪一段当 kw_args；None 表示无超参数
    description: str = ""


# 模块级字典，import 到就有内容。谁要加特征就在自己的文件里往里塞一条，
# 不用回来改这个文件，也不用跟别人抢同一个文件。
FEATURE_REGISTRY: dict[str, FeatureSpec] = {}


def register_feature(name, function, settings_key=None, description=""):
    """注册一个特征。重名直接报错，避免两个模块悄悄覆盖同一个名字。"""
    if not name:
        raise ValueError("特征名不能为空")
    if name in FEATURE_REGISTRY:
        # 两个人都叫 "hog" 却实现不一样，是那种查半天才查得出来的 bug，不如当场炸
        raise ValueError(f"特征已注册：{name}（现有：{', '.join(sorted(FEATURE_REGISTRY))}）")
    FEATURE_REGISTRY[name] = FeatureSpec(function, settings_key, description)
    return FEATURE_REGISTRY[name]


def normalize(X):
    # 只有 Fashion-MNIST 走这一步，它是 0–255 的 uint8。
    # digits 本来就在 0–16，再除 255 等于把信号压没了。
    return X.astype(np.float32) / 255


def pixels(X):
    # 不写死 64 还是 784，靠 reshape 自己推维度，两种数据集共用
    return X.reshape(len(X), -1)


def hog_features(X, orientations=9, pixels_per_cell=(4, 4),
                 cells_per_block=(2, 2), block_norm="L2-Hys"):
    # skimage 的 hog 只吃单张图，没有批量接口，所以一张张算再堆成矩阵
    # block_norm="L2-Hys" 是块内归一化，衣服这种光照不均的图不至于被亮度带跑
    return np.asarray([hog(im, orientations=orientations, pixels_per_cell=tuple(pixels_per_cell),
        cells_per_block=tuple(cells_per_block), block_norm=block_norm) for im in X])


# 内置的两个特征在这里注册。放模块底部是为了 import 完就能用，
# 不用谁再额外调用一次初始化。
register_feature("pixels", pixels, description="原始像素，展平为一维向量")
register_feature("hog", hog_features, settings_key="hog", description="HOG 方向梯度直方图")


def feature_transformer(name, settings):
    """按名字从注册表取特征，包成 FunctionTransformer。"""
    spec = FEATURE_REGISTRY.get(name)
    if spec is None:
        # 报错时把可选的特征列出来，省得回头翻代码
        raise ValueError(f"未知特征：{name}（已注册：{', '.join(sorted(FEATURE_REGISTRY))}）")
    # 包一层是为了让它进 Pipeline：joblib 存下来能直接 load，
    # 也保证标准化、PCA 只在 fit 用到的数据上拟合。
    if spec.settings_key is None:
        return FunctionTransformer(spec.function)
    return FunctionTransformer(spec.function, kw_args=settings[spec.settings_key])


def build_pipeline(parameters, settings):
    # 顺序固定：先归一化、再抽特征，标准化在前、降维在后，SVC 永远压最后。
    steps = []
    if settings["dataset"] == "fashion":
        # digits 进到这里已经是 64 维向量了，只有图像数据才需要除 255 和抽特征
        steps.append(FunctionTransformer(normalize))
        steps.append(feature_transformer(parameters["feature"], settings))
    if parameters.get("standardize", False):
        steps.append(StandardScaler())
    if parameters.get("pca") is not None:
        # pca 存的是方差保留比例（0.95 之类），不是固定维数，实际维数由数据决定；
        # full 求解器没有随机性，同样的数据算出同样结果，复现时不添乱
        steps.append(PCA(n_components=parameters["pca"], svd_solver="full"))
    # 最后一步，Pipeline 的 fit / predict 最终都落到这里。
    # gamma 缺省用 "scale"，对照实验里会显式传数值，见 tasks/task2.py 的说明。
    steps.append(SVC(kernel=parameters["kernel"], C=parameters["C"],
                     gamma=parameters.get("gamma", "scale")))
    return make_pipeline(*steps)
