#!/usr/bin/env python3
"""Offline recovery format 1. Python stdlib only; Linux production support.

Stop Core before using this tool. Its POSIX byte-range lease interoperates with
DataPathResolver's FileChannel lock. Only the canonical container /data layout is
supported; external path overrides must NOT be used with this tool.
"""
import argparse
import contextlib
import datetime
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import sqlite3
import stat
import sys
import tempfile
import uuid

FORMAT = 1
SCHEMA = 28
DB = "db/xingchen.db"
COMPONENTS = ("config", "secrets", "assets", "prompts")
MAX_FILES = 100000
MAX_METADATA = 16 * 1024 * 1024


class Rejected(Exception):
    """Safe, content-free diagnostic."""


def require(condition, message):
    if not condition:
        raise Rejected(message)


def safe_path(path, exists=True):
    original = Path(path).absolute()
    for node in (original, *original.parents):
        require(not node.is_symlink(), "symlink path is forbidden")
    p = original.resolve(strict=False)
    require(p != Path(p.anchor), "filesystem root is forbidden")
    if exists:
        require(p.exists(), "required path missing")
    return p


def private(path):
    if os.name == "posix":
        info = path.stat()
        require(info.st_uid == os.geteuid(), "unexpected file owner")
        require(not info.st_mode & 0o022, "group/world-writable input rejected")


def protect(path, directory=False):
    path.chmod(0o700 if directory else 0o600)


def sync(path):
    if path.is_dir() and os.name != "posix":
        return
    fd = os.open(path, os.O_RDONLY if os.name == "posix" else os.O_RDWR)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def write(path, data):
    with path.open("xb") as f:
        protect(path)
        f.write(data)
        f.flush()
        os.fsync(f.fileno())


def sha(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def json_bytes(value):
    return (json.dumps(value, sort_keys=True, separators=(",", ":")) + "\n").encode()


def parse(path):
    require(path.stat().st_size <= MAX_METADATA, "metadata too large")
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, "duplicate JSON key")
            result[key] = value
        return result
    try:
        return json.loads(path.read_bytes(), object_pairs_hook=pairs)
    except (ValueError, UnicodeError):
        raise Rejected("invalid JSON") from None


def files(root):
    safe_path(root)
    private(root)
    result = []
    for base, dirs, names in os.walk(root, followlinks=False):
        for name in dirs + names:
            p = Path(base) / name
            require(not p.is_symlink(), "bundle contains symlink")
            require(p.is_dir() or p.is_file(), "special file forbidden")
            if p.is_file():
                require(p.stat().st_nlink == 1, "hard-linked file forbidden")
            private(p)
        result.extend(Path(base) / name for name in names)
        require(len(result) <= MAX_FILES, "too many files")
    return sorted(result)


@contextlib.contextmanager
def lease(root):
    lock = root / "db/.xingchen-xingchen.db.lock"
    require(lock.parent.is_dir(), "canonical db directory missing")
    safe_path(lock, False)
    with lock.open("a+b") as handle:
        protect(lock)
        try:
            if os.name == "posix":
                import fcntl
                fcntl.lockf(handle, fcntl.LOCK_EX | fcntl.LOCK_NB, 1, 0)
            else:
                import msvcrt
                handle.seek(0)
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
        except OSError:
            raise Rejected("application is running or maintenance already active") from None
        try:
            yield
        finally:
            if os.name == "posix":
                fcntl.lockf(handle, fcntl.LOCK_UN, 1, 0)
            else:
                handle.seek(0)
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)


def connect_readonly(path):
    return sqlite3.connect(path.as_uri() + "?mode=ro", uri=True)


def check_db(path):
    try:
        with contextlib.closing(connect_readonly(path)) as db:
            require(db.execute("PRAGMA integrity_check").fetchone()[0] == "ok", "database integrity failure")
            require(not db.execute("PRAGMA foreign_key_check").fetchall(), "foreign key failure")
            rows = db.execute('SELECT version FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL').fetchall()
            require(rows and all(str(v[0]).isdigit() for v in rows), "unsupported schema")
            require({int(v[0]) for v in rows} == set(range(1, SCHEMA + 1)), "unsupported schema version")
            require(not db.execute('SELECT 1 FROM flyway_schema_history WHERE success=0').fetchone(), "failed schema migration")
    except sqlite3.Error:
        raise Rejected("database/schema validation failed") from None


def backup(root, destination, app_version, revision):
    root = safe_path(root)
    destination = safe_path(destination, False)
    require(not destination.is_relative_to(root), "backup cannot be inside live data")
    require(not destination.exists(), "backup destination already exists")
    require(destination.parent.is_dir(), "backup parent missing")
    private(root)
    private(destination.parent)
    with lease(root):
        require(parse(root / "recovery-layout.json") == {"canonical": True}, "external persistence overrides unsupported")
        safe_path(root / DB)
        private(root / DB)
        require((root / DB).stat().st_nlink == 1, "hard-linked database forbidden")
        check_db(root / DB)
        stage = Path(tempfile.mkdtemp(prefix=".incomplete-", dir=destination.parent))
        protect(stage, True)
        try:
            (stage / "data/db").mkdir(parents=True, mode=0o700)
            with contextlib.closing(connect_readonly(root / DB)) as source, contextlib.closing(sqlite3.connect(stage / "data" / DB)) as target:
                source.backup(target)  # SQLite Online Backup API, includes committed WAL.
                target.execute("PRAGMA journal_mode=DELETE")
            protect(stage / "data" / DB)
            for component in COMPONENTS:
                source = root / component
                if source.exists():
                    safe_path(source)
                    files(source)
                    shutil.copytree(source, stage / "data" / component)
            write(stage / "data/recovery-layout.json", json_bytes({"canonical": True}))
            for base, dirs, names in os.walk(stage):
                protect(Path(base), True)
                for name in names:
                    p = Path(base) / name
                    protect(p)
                    sync(p)
            payload = files(stage / "data")
            manifest = {"formatVersion": FORMAT, "status": "COMPLETE", "applicationVersion": app_version,
                        "gitRevision": revision, "schemaVersion": SCHEMA,
                        "createdAtUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                        "included": ["SQLite snapshot", *COMPONENTS],
                        "excluded": ["WAL/SHM", "logs", "cache", "temporary files", "maintenance lock"],
                        "checksumAlgorithm": "SHA-256",
                        "files": {str(p.relative_to(stage).as_posix()): p.stat().st_size for p in payload}}
            write(stage / "manifest.json", json_bytes(manifest))
            tracked = payload + [stage / "manifest.json"]
            write(stage / "checksums.sha256", "".join(f"{sha(p)}  {p.relative_to(stage).as_posix()}\n" for p in sorted(tracked)).encode())
            verify(stage)
            for base, _, _ in os.walk(stage, topdown=False):
                sync(Path(base))
            require(not destination.exists(), "backup destination appeared")
            os.rename(stage, destination)
            sync(destination.parent)
            return {"backup": "COMPLETE", "files": len(payload), "schema": SCHEMA}
        except Exception:
            shutil.rmtree(stage)
            raise


def relative(name):
    require(isinstance(name, str) and "\\" not in name and ":" not in name, "unsafe bundle path")
    p = PurePosixPath(name)
    require(not p.is_absolute() and name == p.as_posix() and all(v not in ("", ".", "..") for v in name.split("/")), "unsafe bundle path")
    return name


def verify(bundle):
    bundle = safe_path(bundle)
    private(bundle)
    actual = {p.relative_to(bundle).as_posix(): p for p in files(bundle)}
    require("manifest.json" in actual and "checksums.sha256" in actual, "incomplete bundle")
    manifest = parse(bundle / "manifest.json")
    require(isinstance(manifest, dict), "manifest must be an object")
    require(type(manifest.get("formatVersion")) is int and manifest.get("formatVersion") == FORMAT, "unsupported backup format")
    require(type(manifest.get("schemaVersion")) is int and manifest.get("schemaVersion") == SCHEMA, "unsupported backup schema")
    require(manifest.get("status") == "COMPLETE" and manifest.get("checksumAlgorithm") == "SHA-256", "incomplete bundle")
    declared = manifest.get("files")
    require(isinstance(declared, dict) and "data/" + DB in declared, "invalid manifest files")
    for name, size in declared.items():
        relative(name)
        require(name.startswith("data/") and type(size) is int and size >= 0, "invalid payload metadata")
        allowed = name in ("data/" + DB, "data/recovery-layout.json") or any(name.startswith("data/" + c + "/") for c in COMPONENTS)
        require(allowed, "unsupported payload component")
        require(name in actual and actual[name].stat().st_size == size, "missing or truncated payload")
    require(set(actual) == set(declared) | {"manifest.json", "checksums.sha256"}, "undeclared bundle file")
    require(actual["checksums.sha256"].stat().st_size <= MAX_METADATA, "checksum list too large")
    checked = set()
    digests = {}
    for line in actual["checksums.sha256"].read_text().splitlines():
        require(len(line) > 66 and line[64:66] == "  ", "invalid checksum line")
        digest, name = line[:64], relative(line[66:])
        require(name in actual and name != "checksums.sha256" and name not in checked, "invalid checksum coverage")
        require(sha(actual[name]) == digest, "checksum mismatch")
        checked.add(name)
        digests[name] = digest
    require(checked == set(declared) | {"manifest.json"}, "incomplete checksum coverage")
    check_db(bundle / "data" / DB)
    return {**manifest, "verifiedDigests": {name: digests[name] for name in declared}}


def restore(bundle, destination):
    bundle = safe_path(bundle)
    manifest = verify(bundle)
    destination = safe_path(destination, False)
    require(not destination.is_mount(), "cannot replace a volume mount point; restore into a new volume subdirectory")
    require(destination.parent.is_dir(), "destination parent missing")
    private(destination.parent)
    require(not Path(bundle).absolute().is_relative_to(destination), "bundle inside destination forbidden")
    require(not destination.is_relative_to(Path(bundle).absolute()), "destination inside bundle forbidden")
    if destination.exists():
        private(destination)
        require((destination / DB).is_file(), "existing destination is not canonical XingChen data")
        safe_path(destination / DB)
    with contextlib.ExitStack() as locks:
        if destination.exists():
            locks.enter_context(lease(destination))
        stage = Path(tempfile.mkdtemp(prefix=".restore-", dir=destination.parent))
        rollback = destination.with_name(destination.name + ".rollback-" + uuid.uuid4().hex)
        try:
            shutil.copytree(Path(bundle) / "data", stage, dirs_exist_ok=True)
            for name, expected in manifest["verifiedDigests"].items():
                copied = stage / name.removeprefix("data/")
                require(copied.is_file() and not copied.is_symlink() and sha(copied) == expected,
                        "payload changed during restore")
            for base, _, names in os.walk(stage):
                protect(Path(base), True)
                for name in names:
                    protect(Path(base) / name)
                    sync(Path(base) / name)
            check_db(stage / DB)
            locks.enter_context(lease(stage))
            for base, _, _ in os.walk(stage, topdown=False):
                sync(Path(base))
            if destination.exists():
                os.rename(destination, rollback)
                sync(destination.parent)
            try:
                require(not destination.exists(), "destination appeared")
                os.rename(stage, destination)
                sync(destination.parent)
            except Exception:
                if rollback.exists() and not destination.exists():
                    os.rename(rollback, destination)
                raise
            return {"restore": "COMPLETE", "rollbackRetained": rollback.exists(), "healthValidation": "REQUIRED_AFTER_START"}
        finally:
            # ExitStack releases staged lock before Windows directory cleanup.
            locks.close()
            if stage.exists():
                shutil.rmtree(stage)


def main():
    if os.name != "posix":
        print('{"status":"FAILED","diagnostic":"production recovery requires Linux POSIX locks and permissions"}', file=sys.stderr)
        return 1
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("backup")
    p.add_argument("--data", required=True)
    p.add_argument("--destination", required=True)
    p.add_argument("--application-version", required=True)
    p.add_argument("--revision", required=True)
    p = sub.add_parser("verify")
    p.add_argument("--bundle", required=True)
    p = sub.add_parser("restore")
    p.add_argument("--bundle", required=True)
    p.add_argument("--destination", required=True)
    args = parser.parse_args()
    try:
        if args.command == "backup":
            result = backup(args.data, args.destination, args.application_version, args.revision)
        elif args.command == "verify":
            manifest = verify(args.bundle)
            result = {"integrity": "PASS", "schema": manifest["schemaVersion"]}
        else:
            result = restore(args.bundle, args.destination)
        print(json.dumps(result))
    except Rejected as failure:
        print(json.dumps({"status": "FAILED", "diagnostic": str(failure)}), file=sys.stderr)
        return 1
    except (OSError, sqlite3.Error):
        # Do not print driver exceptions/paths/content which might contain secrets.
        print(json.dumps({"status": "FAILED", "diagnostic": "recovery validation or I/O failed"}), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
