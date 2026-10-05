#!/usr/bin/env python3
"""Fail-closed qq-bridge 0.1.7 config importer. No credentials or chat logs.

Scope is deliberately limited to fields established by forensic inventory:
ownerQQ and allow/deny stable IDs. Other social state is never guessed.
"""
import argparse
import datetime
import json
from pathlib import Path
import re
import sqlite3
import sys
import uuid

from recovery import Rejected, require, safe_path, private, parse, sha, lease, check_db, DB


def stable(value):
    require(type(value) in (int, str), "stable identity must be numeric")
    result = str(value)
    require(re.fullmatch(r"[1-9][0-9]{4,19}", result) is not None, "invalid stable identity")
    return result


def plan(source):
    source = safe_path(source)
    private(source)
    require(source.is_file() and source.stat().st_nlink == 1, "legacy source must be an ordinary private file")
    config = parse(source)
    require(isinstance(config, dict), "invalid legacy source")
    require({"ownerQQ", "allow", "deny", "allowAllWhenEmpty"} <= config.keys(), "incomplete legacy config")
    require(type(config["allowAllWhenEmpty"]) is bool, "invalid legacy access mode")
    owner = stable(config["ownerQQ"])
    rules = []
    duplicates = 0
    seen = set()
    for category, effect in (("allow", "ALLOW"), ("deny", "DENY")):
        values = config[category]
        require(isinstance(values, dict) and set(values) == {"private", "groups"}, "invalid access structure")
        for field, scope in (("private", "PRIVATE"), ("groups", "GROUP")):
            require(isinstance(values[field], list), "invalid access list")
            for entry in values[field]:
                rule = (scope, stable(entry), effect)
                if rule in seen:
                    duplicates += 1
                else:
                    seen.add(rule)
                    rules.append(rule)
    conflicting = {(scope, identity) for scope, identity, _ in rules
                   if (scope, identity, "ALLOW") in seen and (scope, identity, "DENY") in seen}
    rules = [rule for rule in rules if rule[:2] not in conflicting]
    secrets = sum(k in config for k in ("consoleToken", "dsh", "snowluma"))
    supported = {"ownerQQ", "allow", "deny", "allowAllWhenEmpty", "consoleToken", "dsh", "snowluma", "pricing"}
    return owner, rules, {"sourceFingerprint": sha(Path(source)), "sourceType": "qq-bridge-0.1.7-config",
                          "discovered": 1 + len(seen), "duplicates": duplicates,
                          "ambiguous": len(conflicting), "unsupportedFields": len(set(config) - supported),
                          "secretSectionsSkipped": secrets,
                          "SKIPPED_AMBIGUOUS": len(conflicting) + int("pricing" in config),
                          "SKIPPED_UNSUPPORTED": len(set(config) - supported) + 1,
                          "SECRET_SKIP": secrets,
                          "openAccessSkipped": config["allowAllWhenEmpty"]}


def migrate(source, root, apply=False, expected_fingerprint=None):
    owner, rules, report = plan(source)
    root = safe_path(root)
    private(root)
    safe_path(root / DB)
    private(root / DB)
    check_db(root / DB)
    require(not apply or expected_fingerprint == report["sourceFingerprint"], "successful reviewed dry-run fingerprint required")
    now = datetime.datetime.now(datetime.timezone.utc).isoformat()
    with lease(root):
        # mode=ro means dry-run cannot create journals or write ledger markers.
        url = (root / DB).as_uri() + ("?mode=rw" if apply else "?mode=ro")
        db = sqlite3.connect(url, uri=True)
        try:
            db.execute("PRAGMA foreign_keys=ON")
            if apply:
                db.execute("BEGIN IMMEDIATE")
            if db.execute("SELECT 1 FROM legacy_migration_ledger WHERE source_fingerprint=?", (report["sourceFingerprint"],)).fetchone():
                return {**report, "status": "ALREADY_IMPORTED", "importable": 0, "imported": 0, "conflicts": 0}
            importable = 0
            conflicts = 0
            people = [owner] + [identity for scope, identity, _ in rules if scope == "PRIVATE"]
            for identity in dict.fromkeys(people):
                existing = db.execute("SELECT is_owner FROM persons WHERE platform='QQ' AND platform_user_id=?", (identity,)).fetchone()
                if existing:
                    if identity == owner and not existing[0]:
                        conflicts += 1
                    continue
                importable += 1
                if apply:
                    db.execute("INSERT INTO persons(id,platform,platform_user_id,is_self,is_owner,created_at,updated_at) VALUES(?,'QQ',?,0,?,?,?)",
                               (str(uuid.uuid4()), identity, int(identity == owner), now, now))
            for scope, identity, effect in rules:
                existing = db.execute("SELECT effect FROM console_access_rules WHERE scope=? AND platform='QQ' AND stable_id=?", (scope, identity)).fetchone()
                if existing:
                    conflicts += int(existing[0] != effect)
                    continue
                importable += 1
                if apply:
                    db.execute("INSERT INTO console_access_rules(scope,platform,stable_id,effect,updated_at,updated_by) VALUES(?,'QQ',?,?,?,?)",
                               (scope, identity, effect, now, "legacy-migration:qq-bridge-0.1.7:" + report["sourceFingerprint"][:16]))
            if apply:
                db.execute("INSERT INTO legacy_migration_ledger VALUES(?,?,?,?)", (report["sourceFingerprint"], report["sourceType"], now, importable))
                if rules:
                    db.execute("UPDATE console_access_meta SET revision=revision+1 WHERE singleton=1")
                db.commit()
            return {**report, "status": "IMPORTED" if apply else "DRY_RUN_PASS", "importable": importable,
                    "imported": importable if apply else 0, "conflicts": conflicts}
        except Exception:
            if apply:
                db.rollback()
            raise
        finally:
            db.close()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--source", required=True)
    p.add_argument("--data", required=True)
    p.add_argument("--apply", action="store_true")
    p.add_argument("--expected-fingerprint")
    args = p.parse_args()
    try:
        print(json.dumps(migrate(args.source, args.data, args.apply, args.expected_fingerprint)))
        return 0
    except Rejected as failure:
        print(json.dumps({"status": "FAILED", "diagnostic": str(failure)}), file=sys.stderr)
        return 1
    except (OSError, sqlite3.Error):
        print(json.dumps({"status": "FAILED", "diagnostic": "migration validation or transaction failed"}), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
