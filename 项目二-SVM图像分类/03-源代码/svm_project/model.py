"""由配置构建完整 Pipeline；不读取数据文件、不执行训练。"""
import numpy as np
from skimage.feature import hog
from sklearn.decomposition import PCA
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler, FunctionTransformer
from sklearn.svm import SVC


def normalize(X):
    return X.astype(np.float32) / 255


def pixels(X):
    return X.reshape(len(X), -1)


def hog_features(X, orientations=9, pixels_per_cell=(4, 4),
                 cells_per_block=(2, 2), block_norm="L2-Hys"):
    return np.asarray([hog(im, orientations=orientations, pixels_per_cell=tuple(pixels_per_cell),
        cells_per_block=tuple(cells_per_block), block_norm=block_norm) for im in X])


def build_pipeline(parameters, settings):
    steps = []
    if settings["dataset"] == "fashion":
        steps.append(FunctionTransformer(normalize))
        feature = parameters["feature"]
        if feature == "pixels":
            steps.append(FunctionTransformer(pixels))
        elif feature == "hog":
            steps.append(FunctionTransformer(hog_features, kw_args=settings["hog"]))
        else:
            raise ValueError(f"未知特征：{feature}")
    if parameters.get("standardize", False):
        steps.append(StandardScaler())
    if parameters.get("pca") is not None:
        steps.append(PCA(n_components=parameters["pca"], svd_solver="full"))
    steps.append(SVC(kernel=parameters["kernel"], C=parameters["C"],
                     gamma=parameters.get("gamma", "scale")))
    return make_pipeline(*steps)
