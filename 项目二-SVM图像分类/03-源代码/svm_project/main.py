"""统一入口：python main.py --task 1 --stage validate。"""
import argparse
from importlib import import_module
from pathlib import Path
from config import get_task_config
from utils import record_environment


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--task", type=int, choices=[1, 2, 3], required=True)
    parser.add_argument("--stage", choices=["validate", "refit", "test"], default="validate")
    parser.add_argument("--output", type=Path, help="本实验目录，三个阶段须保持一致")
    parser.add_argument("--data-dir", type=Path, help="Fashion-MNIST 官方 IDX.gz 文件目录")
    parser.add_argument("--npz", type=Path, help="Fashion-MNIST 原始 uint8 数据 NPZ")
    parser.add_argument("--indices-dir", type=Path, help="数字任务共用的划分索引目录")
    parser.add_argument("--download", action="store_true", help="显式下载官方 Fashion-MNIST")
    parser.add_argument("--extended", action="store_true", help="Task 3 增加 PCA+RBF 候选")
    args = parser.parse_args(argv)
    if args.task == 1 and args.stage != "validate":
        parser.error("Task 1 只运行 validate，最终测试在 Task 2 完成")
    if args.task != 3 and (args.download or args.extended or args.npz or args.data_dir):
        parser.error("--download、--extended、--npz、--data-dir 仅用于 Task 3")
    if args.npz and args.download:
        parser.error("--npz 与 --download 不能同时使用")
    if args.extended and args.stage != "validate":
        parser.error("--extended 只用于 validate，后续阶段读取已保存的配置")
    return args


def main(argv=None):
    args = parse_args(argv)
    cfg = get_task_config(args.task, output=args.output, data_dir=args.data_dir,
                          indices_dir=args.indices_dir, npz=args.npz,
                          extended=args.extended, download=args.download)
    # 先检查所需产物，避免跳过阶段或误用旧格式的结果目录。
    required = {"refit": "selected_config.json", "test": "refit_manifest.json"}
    if args.stage in required and not (cfg.output / required[args.stage]).exists():
        raise FileNotFoundError(f"请先完成前一阶段，缺少：{cfg.output / required[args.stage]}")
    record_environment(cfg, args.stage)
    task = import_module(f"tasks.task{args.task}")
    task.run(cfg, args.stage)
    print(f"Task {args.task} / {args.stage} complete: {cfg.output}")


if __name__ == "__main__":
    main()
