#!/usr/bin/env python3
"""Prepare and publish juying Android releases.

The script deliberately reads cloud credentials from environment variables.
It never accepts credentials as command-line arguments, which keeps them out
of process listings and CI logs.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import time
from pathlib import Path
from urllib.parse import quote


SEMVER_RE = re.compile(
    r"^(?P<major>0|[1-9]\d*)\."
    r"(?P<minor>0|[1-9]\d*)\."
    r"(?P<patch>0|[1-9]\d*)"
    r"(?P<suffix>[-+][0-9A-Za-z.-]+)?$"
)
MAX_ANDROID_VERSION_CODE = 2_100_000_000
DEFAULT_APK_PREFIX = "android"
DEFAULT_MANIFEST_KEY = "api/android/update.json"
ENV_KEY_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


def load_local_env_file() -> None:
    """Load the repository-root .env for local commands only.

    CI-provided environment variables always win. The .env file is deliberately
    optional and is ignored by Git, so credentials never need to be committed.
    """

    env_path = Path(__file__).resolve().parents[2] / ".env"
    if not env_path.is_file():
        return

    for line_number, raw_line in enumerate(
        env_path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        key, separator, raw_value = line.partition("=")
        key = key.strip()
        if not separator or not ENV_KEY_RE.fullmatch(key):
            raise ValueError(
                f"invalid .env entry at {env_path}:{line_number}; expected KEY=VALUE"
            )

        value = raw_value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        os.environ.setdefault(key, value)


load_local_env_file()


def env(name: str) -> str:
    return os.environ.get(name, "").strip()


def normalize_version_name(value: str) -> str:
    version_name = value.strip().removeprefix("v")
    if not SEMVER_RE.fullmatch(version_name):
        raise ValueError(
            f"invalid version name {value!r}; expected semantic version such as 1.2.0"
        )
    return version_name


def derive_version_code(version_name: str) -> int:
    match = SEMVER_RE.fullmatch(version_name)
    if match is None:
        raise ValueError(f"cannot derive versionCode from {version_name!r}")
    code = (
        int(match.group("major")) * 1_000_000
        + int(match.group("minor")) * 1_000
        + int(match.group("patch"))
    )
    if not 1 <= code <= MAX_ANDROID_VERSION_CODE:
        raise ValueError(
            f"derived versionCode {code} is outside Android's supported range"
        )
    return code


def normalize_object_key(value: str) -> str:
    parts = [part for part in value.replace("\\", "/").split("/") if part]
    if not parts or any(part in {".", ".."} for part in parts):
        raise ValueError(f"unsafe object key: {value!r}")
    return "/".join(parts)


def normalize_base_url(value: str) -> str:
    base_url = value.strip().rstrip("/")
    if base_url and not base_url.startswith("https://"):
        raise ValueError(f"public download base URL must use HTTPS: {value!r}")
    return base_url


def aliyun_distribution_key(apk_key: str) -> str:
    """Return the neutral OSS object name used for Android package bytes.

    Aliyun rejects public .apk downloads through the default OSS endpoint with
    ApkDownloadForbidden unless a CNAME is bound. Android saves the verified
    bytes to a local .apk file, so the remote object suffix is not significant.
    """

    return apk_key[:-4] + ".bin" if apk_key.lower().endswith(".apk") else apk_key + ".bin"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_github_output(path: str, values: dict[str, str | int]) -> None:
    if not path:
        for key, value in values.items():
            print(f"{key}={value}")
        return
    with Path(path).open("a", encoding="utf-8", newline="\n") as target:
        for key, value in values.items():
            target.write(f"{key}={value}\n")


def metadata_command(args: argparse.Namespace) -> int:
    raw_version = args.version_name.strip()
    if not raw_version:
        raw_version = args.ref_name.strip()
    version_name = normalize_version_name(raw_version)

    if args.version_code.strip():
        version_code = int(args.version_code)
        if not 1 <= version_code <= MAX_ANDROID_VERSION_CODE:
            raise ValueError("versionCode must be between 1 and 2100000000")
    else:
        version_code = derive_version_code(version_name)

    values = {
        "version_name": version_name,
        "version_code": version_code,
        "tag": f"v{version_name}",
        "apk_name": f"juying-{version_name}.apk",
    }
    write_github_output(args.github_output, values)
    return 0


def manifest_command(args: argparse.Namespace) -> int:
    apk_path = Path(args.apk).resolve()
    if not apk_path.is_file():
        raise FileNotFoundError(f"APK does not exist: {apk_path}")

    version_name = normalize_version_name(args.version_name)
    version_code = int(args.version_code)
    if not 1 <= version_code <= MAX_ANDROID_VERSION_CODE:
        raise ValueError("versionCode must be between 1 and 2100000000")

    apk_key = normalize_object_key(
        f"{env('ANDROID_OBJECT_PREFIX') or DEFAULT_APK_PREFIX}/{apk_path.name}"
    )
    urls: list[str] = []
    aliyun_base_url = normalize_base_url(env("ALIYUN_OSS_PUBLIC_BASE_URL"))
    if aliyun_base_url:
        aliyun_key = aliyun_distribution_key(apk_key)
        urls.append(f"{aliyun_base_url}/{quote(aliyun_key, safe='/')}")

    tencent_base_url = normalize_base_url(env("TENCENT_COS_PUBLIC_BASE_URL"))
    if tencent_base_url:
        urls.append(f"{tencent_base_url}/{quote(apk_key, safe='/')}")

    repository = args.repository.strip().strip("/")
    tag = args.tag.strip()
    if repository and tag:
        urls.append(
            "https://github.com/"
            f"{repository}/releases/download/{quote(tag)}/{quote(apk_path.name)}"
        )

    urls = list(dict.fromkeys(urls))
    if not urls:
        raise ValueError(
            "no public APK URL can be generated; configure a cloud public base URL "
            "or provide --repository and --tag"
        )

    notes = args.notes.strip()
    if args.notes_file:
        notes_path = Path(args.notes_file)
        if notes_path.is_file():
            notes = notes_path.read_text(encoding="utf-8").strip()
    if not notes:
        notes = "性能优化、问题修复与稳定性提升。"

    manifest = {
        "versionCode": version_code,
        "versionName": version_name,
        "manifestRevision": int(time.time()),
        "title": args.title.strip() or f"juying {version_name} 更新",
        "notes": notes,
        "apkUrls": urls,
        "sha256": sha256_file(apk_path),
    }
    output = Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print(f"generated {output}")
    return 0


def configured_group(prefix: str, required: tuple[str, ...]) -> bool:
    values = {name: env(name) for name in required}
    optional_values_present = any(
        env(name)
        for name in os.environ
        if name.startswith(prefix)
    )
    if not optional_values_present:
        print(f"{prefix.rstrip('_')}: not configured, skipping")
        return False
    missing = [name for name, value in values.items() if not value]
    if missing:
        raise ValueError(
            f"{prefix.rstrip('_')} configuration is incomplete; missing "
            + ", ".join(missing)
        )
    return True


def upload_aliyun(apk_path: Path, manifest_path: Path) -> bool:
    required = (
        "ALIYUN_OSS_ACCESS_KEY_ID",
        "ALIYUN_OSS_ACCESS_KEY_SECRET",
        "ALIYUN_OSS_ENDPOINT",
        "ALIYUN_OSS_BUCKET",
        "ALIYUN_OSS_PUBLIC_BASE_URL",
    )
    if not configured_group("ALIYUN_OSS_", required):
        return False

    try:
        import oss2
    except ImportError as error:
        raise RuntimeError("install scripts/release/requirements.txt first") from error

    access_key_id = env("ALIYUN_OSS_ACCESS_KEY_ID")
    access_key_secret = env("ALIYUN_OSS_ACCESS_KEY_SECRET")
    security_token = env("ALIYUN_OSS_SECURITY_TOKEN")
    auth = (
        oss2.StsAuth(access_key_id, access_key_secret, security_token)
        if security_token
        else oss2.Auth(access_key_id, access_key_secret)
    )
    bucket = oss2.Bucket(
        auth,
        env("ALIYUN_OSS_ENDPOINT"),
        env("ALIYUN_OSS_BUCKET"),
    )
    apk_key = aliyun_distribution_key(
        normalize_object_key(
            f"{env('ANDROID_OBJECT_PREFIX') or DEFAULT_APK_PREFIX}/{apk_path.name}"
        )
    )
    manifest_key = normalize_object_key(
        env("ANDROID_UPDATE_MANIFEST_KEY") or DEFAULT_MANIFEST_KEY
    )
    bucket.put_object_from_file(
        apk_key,
        str(apk_path),
        headers={
            # The object intentionally uses .bin and a neutral content type.
            # Android stores it locally as .apk after SHA-256 verification.
            "Content-Type": "application/octet-stream",
            "Cache-Control": "public, max-age=31536000, immutable",
        },
    )
    bucket.put_object_from_file(
        manifest_key,
        str(manifest_path),
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "Cache-Control": "no-cache, no-store, must-revalidate",
        },
    )
    print(f"Aliyun OSS: uploaded {apk_key} and {manifest_key}")
    return True


def upload_tencent(apk_path: Path, manifest_path: Path) -> bool:
    required = (
        "TENCENT_COS_SECRET_ID",
        "TENCENT_COS_SECRET_KEY",
        "TENCENT_COS_REGION",
        "TENCENT_COS_BUCKET",
        "TENCENT_COS_PUBLIC_BASE_URL",
    )
    if not configured_group("TENCENT_COS_", required):
        return False

    try:
        from qcloud_cos import CosConfig, CosS3Client
    except ImportError as error:
        raise RuntimeError("install scripts/release/requirements.txt first") from error

    config = CosConfig(
        Region=env("TENCENT_COS_REGION"),
        SecretId=env("TENCENT_COS_SECRET_ID"),
        SecretKey=env("TENCENT_COS_SECRET_KEY"),
        Token=env("TENCENT_COS_TOKEN") or None,
        Scheme="https",
    )
    client = CosS3Client(config)
    apk_key = normalize_object_key(
        f"{env('ANDROID_OBJECT_PREFIX') or DEFAULT_APK_PREFIX}/{apk_path.name}"
    )
    manifest_key = normalize_object_key(
        env("ANDROID_UPDATE_MANIFEST_KEY") or DEFAULT_MANIFEST_KEY
    )
    client.upload_file(
        Bucket=env("TENCENT_COS_BUCKET"),
        LocalFilePath=str(apk_path),
        Key=apk_key,
        PartSize=10,
        MAXThread=4,
        EnableMD5=True,
        ContentType="application/vnd.android.package-archive",
        CacheControl="public, max-age=31536000, immutable",
    )
    client.upload_file(
        Bucket=env("TENCENT_COS_BUCKET"),
        LocalFilePath=str(manifest_path),
        Key=manifest_key,
        PartSize=1,
        MAXThread=2,
        EnableMD5=True,
        ContentType="application/json; charset=utf-8",
        CacheControl="no-cache, no-store, must-revalidate",
    )
    print(f"Tencent COS: uploaded {apk_key} and {manifest_key}")
    return True


def upload_command(args: argparse.Namespace) -> int:
    apk_path = Path(args.apk).resolve()
    manifest_path = Path(args.manifest).resolve()
    if not apk_path.is_file():
        raise FileNotFoundError(f"APK does not exist: {apk_path}")
    if not manifest_path.is_file():
        raise FileNotFoundError(f"manifest does not exist: {manifest_path}")
    failures: list[str] = []
    for provider_name, uploader in (
        ("Aliyun OSS", upload_aliyun),
        ("Tencent COS", upload_tencent),
    ):
        try:
            uploader(apk_path, manifest_path)
        except Exception as error:
            failures.append(f"{provider_name}: {error}")
            print(f"{provider_name}: upload failed: {error}", file=sys.stderr)
    if failures:
        raise RuntimeError("; ".join(failures))
    return 0


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest="command", required=True)

    metadata = commands.add_parser("metadata", help="validate release version")
    metadata.add_argument("--version-name", default="")
    metadata.add_argument("--version-code", default="")
    metadata.add_argument("--ref-name", default="")
    metadata.add_argument("--github-output", default="")
    metadata.set_defaults(handler=metadata_command)

    manifest = commands.add_parser("manifest", help="generate update.json")
    manifest.add_argument("--apk", required=True)
    manifest.add_argument("--output", required=True)
    manifest.add_argument("--version-name", required=True)
    manifest.add_argument("--version-code", required=True)
    manifest.add_argument("--repository", default="")
    manifest.add_argument("--tag", default="")
    manifest.add_argument("--title", default="")
    manifest.add_argument("--notes", default="")
    manifest.add_argument("--notes-file", default="")
    manifest.set_defaults(handler=manifest_command)

    upload = commands.add_parser("upload", help="upload APK and manifest")
    upload.add_argument("--apk", required=True)
    upload.add_argument("--manifest", required=True)
    upload.set_defaults(handler=upload_command)
    return root


def main() -> int:
    args = parser().parse_args()
    try:
        return int(args.handler(args))
    except Exception as error:
        print(f"release error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
