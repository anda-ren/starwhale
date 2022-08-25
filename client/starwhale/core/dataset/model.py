from __future__ import annotations

import typing as t
import tarfile
from abc import ABCMeta, abstractmethod
from pathlib import Path
from collections import defaultdict

import yaml
from fs import open_fs
from loguru import logger
from fs.copy import copy_fs, copy_file

from starwhale.utils import console, load_yaml
from starwhale.consts import (
    HTTPMethod,
    DefaultYAMLName,
    DEFAULT_PAGE_IDX,
    DEFAULT_PAGE_SIZE,
    DEFAULT_COPY_WORKERS,
    DEFAULT_MANIFEST_NAME,
    ARCHIVED_SWDS_META_FNAME,
)
from starwhale.base.tag import StandaloneTag
from starwhale.base.uri import URI
from starwhale.utils.fs import move_dir, ensure_dir
from starwhale.base.type import URIType, BundleType, InstanceType
from starwhale.base.cloud import CloudRequestMixed, CloudBundleModelMixin
from starwhale.utils.http import ignore_error
from starwhale.utils.load import import_cls
from starwhale.base.bundle import BaseBundle, LocalStorageBundleMixin
from starwhale.utils.error import NoSupportError
from starwhale.utils.progress import run_with_progress_bar
from starwhale.base.bundle_copy import BundleCopy

from .type import DatasetConfig, DatasetSummary
from .store import DatasetStorage
from .tabular import StandaloneTabularDataset


class Dataset(BaseBundle, metaclass=ABCMeta):
    def __str__(self) -> str:
        return f"Starwhale Dataset: {self.uri}"

    @abstractmethod
    def summary(self) -> DatasetSummary:
        raise NotImplementedError

    @classmethod
    def get_dataset(cls, uri: URI) -> Dataset:
        _cls = cls._get_cls(uri)
        return _cls(uri)

    @classmethod
    def copy(
        cls, src_uri: str, dest_uri: str, force: bool = False, with_auth: bool = False
    ) -> None:
        bc = BundleCopy(src_uri, dest_uri, URIType.DATASET, force, with_auth=with_auth)
        if bc.src_uri.instance_type == InstanceType.STANDALONE:
            with StandaloneTabularDataset.from_uri(bc.src_uri) as tds:
                tds.dump_meta(force)

        bc.do()

        if bc.src_uri.instance_type == InstanceType.CLOUD:
            with StandaloneTabularDataset(
                name=bc.bundle_name,
                version=bc.bundle_version,
                project=bc.dest_uri.project,
            ) as tds:
                tds.load_meta()

    @classmethod
    def _get_cls(  # type: ignore
        cls, uri: URI
    ) -> t.Union[t.Type[StandaloneDataset], t.Type[CloudDataset]]:
        if uri.instance_type == InstanceType.STANDALONE:
            return StandaloneDataset
        elif uri.instance_type == InstanceType.CLOUD:
            return CloudDataset
        else:
            raise NoSupportError(f"dataset uri:{uri}")


class StandaloneDataset(Dataset, LocalStorageBundleMixin):
    def __init__(self, uri: URI) -> None:
        super().__init__(uri)
        self.typ = InstanceType.STANDALONE
        self.store = DatasetStorage(uri)
        self.tag = StandaloneTag(uri)
        self._manifest: t.Dict[
            str, t.Any
        ] = {}  # TODO: use manifest class get_conda_env
        self.yaml_name = DefaultYAMLName.DATASET

    def add_tags(self, tags: t.List[str], quiet: bool = False) -> None:
        self.tag.add(tags, quiet)

    def remove_tags(self, tags: t.List[str], quiet: bool = False) -> None:
        self.tag.remove(tags, quiet)

    def history(
        self,
        page: int = DEFAULT_PAGE_IDX,
        size: int = DEFAULT_PAGE_SIZE,
    ) -> t.Tuple[t.List[t.Dict[str, t.Any]], t.Dict[str, t.Any]]:
        _r = []

        for _bf in self.store.iter_bundle_history():
            _manifest = load_yaml(_bf.path / DEFAULT_MANIFEST_NAME)

            _r.append(
                dict(
                    name=_manifest["name"],
                    version=_bf.version,
                    size=_manifest.get("dataset_byte_size", 0),
                    created_at=_manifest["created_at"],
                    tags=_bf.tags,
                    path=_bf.path,
                )
            )

        return _r, {}

    def remove(self, force: bool = False) -> t.Tuple[bool, str]:
        # TODO: remove by tag
        return move_dir(self.store.snapshot_workdir, self.store.recover_loc, force)

    def recover(self, force: bool = False) -> t.Tuple[bool, str]:
        dest_path = (
            self.store.bundle_dir / f"{self.uri.object.version}{BundleType.DATASET}"
        )
        return move_dir(self.store.recover_loc, dest_path, force)

    def info(self) -> t.Dict[str, t.Any]:
        return self._get_bundle_info()

    def summary(self) -> DatasetSummary:
        _manifest = self.store.manifest
        return DatasetSummary(**_manifest.get("dataset_summary", {}))

    @classmethod
    def list(
        cls,
        project_uri: URI,
        page: int = DEFAULT_PAGE_IDX,
        size: int = DEFAULT_PAGE_SIZE,
    ) -> t.Tuple[t.Dict[str, t.Any], t.Dict[str, t.Any]]:
        rs = defaultdict(list)

        for _bf in DatasetStorage.iter_all_bundles(
            project_uri,
            bundle_type=BundleType.DATASET,
            uri_type=URIType.DATASET,
        ):
            _mf = _bf.path / DEFAULT_MANIFEST_NAME
            if not _mf.exists():
                continue

            _manifest = load_yaml(_bf.path / DEFAULT_MANIFEST_NAME)

            rs[_bf.name].append(
                dict(
                    name=_manifest["name"],
                    version=_bf.version,
                    size=_manifest.get("dataset_byte_size", 0),
                    created_at=_manifest["created_at"],
                    is_removed=_bf.is_removed,
                    path=_bf.path,
                    tags=_bf.tags,
                )
            )

        return rs, {}

    def buildImpl(
        self, workdir: Path, yaml_name: str = DefaultYAMLName.DATASET, **kw: t.Any
    ) -> None:
        _dp = workdir / yaml_name
        _dataset_config = self._load_dataset_config(_dp)

        # TODO: design dataset layer mechanism
        # TODO: design append some new data into existed dataset
        # TODO: design uniq build steps for model build, swmp build

        operations = [
            (self._gen_version, 5, "gen version"),
            (self._prepare_snapshot, 5, "prepare snapshot"),
            (
                self._copy_src,
                15,
                "copy src",
                dict(
                    workdir=workdir,
                    yaml_name=yaml_name,
                    pkg_data=_dataset_config.pkg_data,
                    exclude_pkg_data=_dataset_config.exclude_pkg_data,
                ),
            ),
            (
                self._call_make_swds,
                30,
                "make swds",
                dict(workdir=workdir, swds_config=_dataset_config),
            ),
            (self._calculate_signature, 5, "calculate signature"),
            (
                self._render_manifest,
                5,
                "render manifest",
            ),
            (self._make_swds_meta_tar, 15, "make meta tar"),
            (self._make_latest_tag, 5, "make latest tag"),
        ]
        run_with_progress_bar("swds building...", operations)

    def _call_make_swds(self, workdir: Path, swds_config: DatasetConfig) -> None:
        from starwhale.api._impl.dataset.builder import BaseBuildExecutor

        logger.info("[step:swds]try to gen swds...")
        self._manifest["dataset_attr"] = swds_config.attr.as_dict()
        self._manifest["process"] = swds_config.process

        # TODO: add more import format support, current is module:class
        logger.info(f"[info:swds]try to import {swds_config.process} @ {workdir}")
        _cls = import_cls(workdir, swds_config.process, BaseBuildExecutor)

        with _cls(
            dataset_name=self.uri.object.name,
            dataset_version=self._version,
            project_name=self.uri.project,
            data_dir=workdir / swds_config.data_dir,
            workdir=self.store.snapshot_workdir,
            data_filter=swds_config.data_filter,
            label_filter=swds_config.label_filter,
            alignment_bytes_size=swds_config.attr.alignment_size,
            volume_bytes_size=swds_config.attr.volume_size,
        ) as _obj:
            console.print(
                f":ghost: import [red]{swds_config.process}@{workdir.resolve()}[/] to make swds..."
            )
            _summary: DatasetSummary = _obj.make_swds()
            self._manifest["dataset_summary"] = _summary.as_dict()

        console.print(f"[step:swds]finish gen swds @ {self.store.data_dir}")

    def _calculate_signature(self) -> None:
        algo = self.store.object_hash_algo
        sign_info = list()
        total_size = 0

        # TODO: _cal(self._snapshot_workdir / ARCHIVED_SWDS_META_FNAME) # add meta sign into _manifest.yaml
        for fpath in self.store.get_all_data_files():
            _size = fpath.stat().st_size
            total_size += _size
            sign_info.append(f"{_size}:{algo}:{fpath.name}")

        self._manifest["dataset_byte_size"] = total_size
        self._manifest["signature"] = sign_info
        console.print(
            f":robot: calculate signature with {algo} for {len(sign_info)} files"
        )

    def _make_swds_meta_tar(self) -> None:
        out = self.store.snapshot_workdir / ARCHIVED_SWDS_META_FNAME
        logger.info(f"[step:tar]try to tar for swmp meta(NOT INCLUDE DATASET){out}")
        with tarfile.open(out, "w:") as tar:
            tar.add(str(self.store.src_dir), arcname="src")
            tar.add(str(self.store.snapshot_workdir / DEFAULT_MANIFEST_NAME))
            tar.add(str(self.store.snapshot_workdir / DefaultYAMLName.DATASET))

        console.print(
            ":hibiscus: congratulation! you can run "
            f"[red bold blink] swcli dataset info {self.name}/version/{self._version}[/]"
        )

    def _prepare_snapshot(self) -> None:
        ensure_dir(self.store.data_dir)
        ensure_dir(self.store.src_dir)

        console.print(
            f":file_folder: swds workdir: [underline]{self.store.snapshot_workdir}[/]"
        )

    def _copy_src(
        self,
        workdir: Path,
        yaml_name: str,
        pkg_data: t.List[str],
        exclude_pkg_data: t.List[str],
    ) -> None:
        logger.info(f"[step:copy]start to copy src {workdir} -> {self.store.src_dir}")
        console.print(":thumbs_up: try to copy source code files...")
        workdir_fs = open_fs(str(workdir.absolute()))
        src_fs = open_fs(str(self.store.src_dir.absolute()))
        snapshot_fs = open_fs(str(self.store.snapshot_workdir.absolute()))

        copy_file(workdir_fs, yaml_name, src_fs, DefaultYAMLName.DATASET)
        copy_file(workdir_fs, yaml_name, snapshot_fs, DefaultYAMLName.DATASET)
        # TODO: tune copy src
        copy_fs(
            workdir_fs,
            src_fs,
            walker=self._get_src_walker(workdir, pkg_data, exclude_pkg_data),
            workers=DEFAULT_COPY_WORKERS,
        )

        logger.info("[step:copy]finish copy files")

    def _load_dataset_config(self, yaml_path: Path) -> DatasetConfig:
        self._do_validate_yaml(yaml_path)
        _config = DatasetConfig.create_by_yaml(yaml_path)

        if not (yaml_path.parent / _config.data_dir).exists():
            raise FileNotFoundError(f"dataset datadir:{_config.data_dir}")

        return _config


class CloudDataset(CloudBundleModelMixin, Dataset):
    def __init__(self, uri: URI) -> None:
        super().__init__(uri)
        self.typ = InstanceType.CLOUD

    @classmethod
    @ignore_error(({}, {}))
    def list(
        cls,
        project_uri: URI,
        page: int = DEFAULT_PAGE_IDX,
        size: int = DEFAULT_PAGE_SIZE,
    ) -> t.Tuple[t.Dict[str, t.Any], t.Dict[str, t.Any]]:
        crm = CloudRequestMixed()
        return crm._fetch_bundle_all_list(project_uri, URIType.DATASET, page, size)

    def summary(self) -> DatasetSummary:
        r = self.do_http_request(
            f"/project/{self.uri.project}/{self.uri.object.typ}/{self.uri.object.name}",
            method=HTTPMethod.GET,
            instance_uri=self.uri,
            params={"versionUrl": self.uri.object.version},
        ).json()
        _manifest: t.Dict[str, t.Any] = yaml.safe_load(r["data"].get("versionMeta", {}))
        return DatasetSummary(**_manifest.get("dataset_summary", {}))

    def buildImpl(self, workdir: Path, yaml_name: str, **kw: t.Any) -> None:
        raise NoSupportError("no support build dataset in the cloud instance")
