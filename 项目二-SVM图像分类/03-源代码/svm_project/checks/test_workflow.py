"""关键流程检查：重训的数据范围、测试不训练、配置变化后拒绝旧模型。"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import tempfile
import unittest
from unittest.mock import patch
import joblib
import numpy as np
from sklearn.datasets import load_digits
from config import get_task_config
from data import DatasetBundle
from train import freeze_selection, refit_final_models
from test import evaluate_test
from utils import fingerprint, read_json, save_json
from main import parse_args


class WorkflowChecks(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.cfg = get_task_config(2, output=Path(self.tmp.name))
        X, y = load_digits(return_X_y=True)
        self.data = DatasetBundle((X[:200], y[:200]), (X[200:240], y[200:240]),
                                   (X[240:260], y[240:260]), fingerprint(X, y),
                                   list(map(str, range(10))))
        self.parameters = {"kernel": "linear", "C": 1, "standardize": True}
        freeze_selection(self.cfg, self.data, [self.parameters], [{"macro_f1": .5}],
                         0, {"baseline": self.parameters})

    def tearDown(self):
        self.tmp.cleanup()

    def test_refit_uses_development_data_only(self):
        refit_final_models(self.cfg, self.data)
        model = joblib.load(self.cfg.output / "models/selected.joblib")
        expected = np.concatenate([self.data.train[0], self.data.validation[0]])
        np.testing.assert_allclose(model.steps[0][1].mean_, expected.mean(axis=0))
        self.assertEqual(model.steps[-1][1].shape_fit_[0], 240)
        self.assertEqual(read_json(self.cfg.output / "refit_manifest.json")["n_train"], 240)

    def test_test_stage_never_fits(self):
        refit_final_models(self.cfg, self.data)
        with patch("sklearn.svm.SVC.fit", side_effect=AssertionError("测试阶段禁止拟合")), \
             patch("test.write_evaluation"):
            evaluate_test(self.cfg, self.data)
        self.assertTrue((self.cfg.output / "test_results.csv").exists())

    def test_changed_selection_rejects_old_model(self):
        refit_final_models(self.cfg, self.data)
        path = self.cfg.output / "selected_config.json"
        changed = read_json(path)
        changed["models"]["selected"]["C"] = 10
        save_json(path, changed)
        with self.assertRaisesRegex(ValueError, "重训后发生改变"):
            evaluate_test(self.cfg, self.data)

    def test_changed_dataset_rejected(self):
        self.data.signature = "different-data"
        with self.assertRaisesRegex(ValueError, "数据与验证阶段不一致"):
            refit_final_models(self.cfg, self.data)

    def test_task1_cannot_test(self):
        with self.assertRaises(SystemExit):
            parse_args(["--task", "1", "--stage", "test"])


if __name__ == "__main__":
    unittest.main()
