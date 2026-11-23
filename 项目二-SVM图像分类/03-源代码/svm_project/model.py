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


FEATURE_REGISTRY: dict[str, FeatureSpec] = {}


def register_feature(name, function, settings_key=None, description=""):
    """注册一个特征。重名直接报错，避免两个模块悄悄覆盖同一个名字。"""
    if not name:
        raise ValueError("特征名不能为空")
    if name in FEATURE_REGISTRY:
        raise ValueError(f"特征已注册：{name}（现有：{', '.join(sorted(FEATURE_REGISTRY))}）")
    FEATURE_REGISTRY[name] = FeatureSpec(function, settings_key, description)
    return FEATURE_REGISTRY[name]


def normalize(X):
    return X.astype(np.float32) / 255


def pixels(X):
    return X.reshape(len(X), -1)


def hog_features(X, orientations=9, pixels_per_cell=(4, 4),
                 cells_per_block=(2, 2), block_norm="L2-Hys"):
    return np.asarray([hog(im, orientations=orientations, pixels_per_cell=tuple(pixels_per_cell),
        cells_per_block=tuple(cells_per_block), block_norm=block_norm) for im in X])


register_feature("pixels", pixels, description="原始像素，展平为一维向量")
register_feature("hog", hog_features, settings_key="hog", description="HOG 方向梯度直方图")


def feature_transformer(name, settings):
    """按名字从注册表取特征，包成 FunctionTransformer。"""
    spec = FEATURE_REGISTRY.get(name)
    if spec is None:
        raise ValueError(f"未知特征：{name}（已注册：{', '.join(sorted(FEATURE_REGISTRY))}）")
    if spec.settings_key is None:
        return FunctionTransformer(spec.function)
    return FunctionTransformer(spec.function, kw_args=settings[spec.settings_key])


def build_pipeline(parameters, settings):
    steps = []
    if settings["dataset"] == "fashion":
        steps.append(FunctionTransformer(normalize))
        steps.append(feature_transformer(parameters["feature"], settings))
    if parameters.get("standardize", False):
        steps.append(StandardScaler())
    if parameters.get("pca") is not None:
        steps.append(PCA(n_components=parameters["pca"], svd_solver="full"))
    steps.append(SVC(kernel=parameters["kernel"], C=parameters["C"],
                     gamma=parameters.get("gamma", "scale")))
    return make_pipeline(*steps)
