import json
import contextlib
import os
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest import mock

import recovery as r
import legacy_migrate as m

@contextlib.contextmanager
def database(path):
    db = sqlite3.connect(path)
    try:
        with db:
            yield db
    finally:
        db.close()


class RecoveryTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = Path(self.tmp.name)
        self.root = self.base / "data"
        (self.root / "db").mkdir(parents=True)
        self.db = self.root / r.DB
        with database(self.db) as db:
            # Exact current migrations, not a mock data model.
            migrations = Path(__file__).resolve().parents[1] / "src/main/resources/db/migration"
            for path in sorted(migrations.glob("V*__*.sql"), key=lambda p: int(p.name.split("__")[0][1:])):
                db.executescript(path.read_text())
            db.execute("CREATE TABLE flyway_schema_history(version TEXT, success INTEGER)")
            db.executemany("INSERT INTO flyway_schema_history VALUES(?,1)", [(str(i),) for i in range(1, 29)])
            db.execute("INSERT INTO persons VALUES('p','QQ','10001',0,1,'t','t')")
            db.execute("INSERT INTO relationship_terms VALUES('r','p','p','ADDRESS_AS','fixture','PRIVATE','p',1,1,NULL,'t','t',1)")
            db.execute("INSERT INTO memories VALUES('m','SEMANTIC','p',NULL,NULL,'fixture','PRIVATE','p',1,1,1,'t','t',NULL,NULL,'ACTIVE')")
            db.execute("INSERT INTO prompt_profiles VALUES('default','fixture','','','t','sim','persona',1)")
            db.execute("INSERT INTO prompt_versions VALUES('persona','default','PERSONA','fixture',1,'t','fixture','hash','')")
            db.execute("INSERT INTO console_access_rules(scope,platform,stable_id,effect,updated_at,updated_by) VALUES('PRIVATE','QQ','10001','ALLOW','t','fixture')")
            db.execute("INSERT INTO console_pricing VALUES('fixture','fixture','0.123456789','0','0','0','0','USD',1,'t','fixture')")
            db.execute("INSERT INTO console_configuration VALUES('Console','publicBaseUrl','\"https://console.example.invalid\"','t','fixture')")
            db.execute("INSERT INTO stickers(id,path,sha256,source,created_at) VALUES('s','fixture.png','hash','fixture','t')")
        (self.root / "config").mkdir()
        (self.root / "config/deepseek-api-key.cleared").write_bytes(b"")
        (self.root / "assets/stickers").mkdir(parents=True)
        (self.root / "assets/stickers/fixture.png").write_bytes(b"fixture-png")
        (self.root / "recovery-layout.json").write_text('{"canonical":true}')
        self.bundle = self.base / "backup"

    def tearDown(self):
        self.tmp.cleanup()

    def backup(self):
        return r.backup(self.root, self.bundle, "fixture", "fixture")

    def test_snapshot_contains_committed_wal(self):
        live = sqlite3.connect(self.db)
        live.execute("PRAGMA journal_mode=WAL")
        live.execute("UPDATE memories SET content='committed WAL'")
        live.commit()
        self.assertTrue(Path(str(self.db) + "-wal").exists())
        self.backup()
        with database(self.bundle / "data" / r.DB) as snapshot:
            self.assertEqual("committed WAL", snapshot.execute("SELECT content FROM memories").fetchone()[0])
        live.close()

    @unittest.skipUnless(os.name == "posix", "Linux atomic directory switch contract")
    def test_full_roundtrip_and_clear_tombstone(self):
        self.backup()
        before = sqlite3.connect(self.db)
        tables = ("persons", "relationship_terms", "memories", "prompt_profiles", "prompt_versions", "console_access_rules", "console_pricing", "stickers", "console_configuration")
        expected = {t: before.execute("SELECT * FROM " + t).fetchall() for t in tables}
        before.execute("UPDATE memories SET content='mutated'")
        before.commit()
        before.close()
        (self.root / "assets/stickers/fixture.png").unlink()
        destination = self.base / "restored"
        r.restore(self.bundle, destination)
        with database(destination / r.DB) as restored:
            self.assertEqual(expected, {t: restored.execute("SELECT * FROM " + t).fetchall() for t in tables})
        self.assertTrue((destination / "config/deepseek-api-key.cleared").is_file())
        self.assertEqual(b"fixture-png", (destination / "assets/stickers/fixture.png").read_bytes())

    def test_corruption_fails_closed(self):
        for filename in ("manifest.json", "data/" + r.DB, "data/assets/stickers/fixture.png"):
            self.bundle = self.base / ("b" + str(len(filename)))
            self.backup()
            p = self.bundle / filename
            p.write_bytes(p.read_bytes() + b"corrupt")
            with self.assertRaises(r.Rejected):
                r.restore(self.bundle, self.base / "not-created")
            self.assertFalse((self.base / "not-created").exists())

    def test_no_overwrite_and_failed_backup_not_published(self):
        self.backup()
        with self.assertRaises(r.Rejected):
            self.backup()
        failed = self.base / "failed"
        with mock.patch.object(r, "sync", side_effect=OSError("fixture")):
            with self.assertRaises(OSError):
                r.backup(self.root, failed, "fixture", "fixture")
        self.assertFalse(failed.exists())
        self.assertFalse(list(self.base.glob(".incomplete-*")))

    def test_future_format_schema_and_traversal(self):
        self.backup()
        manifest = r.parse(self.bundle / "manifest.json")
        for change in ({"formatVersion": 999}, {"schemaVersion": 999}, {"files": {"../escape": 1}}):
            (self.bundle / "manifest.json").write_bytes(r.json_bytes({**manifest, **change}))
            with self.assertRaises(r.Rejected):
                r.verify(self.bundle)
        for path in ("../escape", "/escape", "C:/escape", "a\\escape", "a/../escape"):
            with self.assertRaises(r.Rejected):
                r.relative(path)

    def test_external_override_rejected(self):
        (self.root / "recovery-layout.json").write_text('{"canonical":false}')
        with self.assertRaises(r.Rejected):
            self.backup()

    def test_normalized_filesystem_root_is_rejected(self):
        disguised = Path(self.base.anchor) / "tmp" / ".."
        with self.assertRaisesRegex(r.Rejected, "filesystem root"):
            r.safe_path(disguised, False)

    def test_malformed_manifest_shape_fails_closed(self):
        self.backup()
        for value in ([], {"formatVersion": True, "schemaVersion": r.SCHEMA}):
            (self.bundle / "manifest.json").write_bytes(r.json_bytes(value))
            with self.assertRaises(r.Rejected):
                r.verify(self.bundle)

    def test_source_mutation_during_restore_is_rejected(self):
        self.backup()
        original = r.shutil.copytree
        def changed_copy(source, destination, *args, **kwargs):
            if Path(source) == self.bundle / "data":
                (Path(source) / "assets/stickers/fixture.png").write_bytes(b"mutated-png")
            return original(source, destination, *args, **kwargs)
        with mock.patch.object(r.shutil, "copytree", side_effect=changed_copy):
            with self.assertRaisesRegex(r.Rejected, "changed during restore"):
                r.restore(self.bundle, self.base / "not-published")
        self.assertFalse((self.base / "not-published").exists())

    @unittest.skipUnless(os.name == "posix", "Linux atomic directory switch")
    def test_existing_destination_rollback_retained(self):
        self.backup()
        with database(self.db) as db:
            db.execute("UPDATE memories SET content='mutated'")
        result = r.restore(self.bundle, self.root)
        self.assertTrue(result["rollbackRetained"])
        rollback = next(self.base.glob("data.rollback-*"))
        with database(rollback / r.DB) as db:
            self.assertEqual("mutated", db.execute("SELECT content FROM memories").fetchone()[0])
        with database(self.db) as db:
            self.assertEqual("fixture", db.execute("SELECT content FROM memories").fetchone()[0])

    @unittest.skipUnless(os.name == "posix", "Linux ownership/symlink contract")
    def test_symlink_and_world_writable_rejected(self):
        (self.root / "assets/escape").symlink_to(self.base)
        with self.assertRaises(r.Rejected):
            self.backup()
        (self.root / "assets/escape").unlink()
        (self.root / "assets").chmod(0o777)
        with self.assertRaises(r.Rejected):
            self.backup()


class MigrationTest(unittest.TestCase):
    setUp = RecoveryTest.setUp
    tearDown = RecoveryTest.tearDown
    def source(self, **changes):
        source = self.base / "legacy.json"
        source.write_text(json.dumps({"ownerQQ": "20001", "allow": {"private": ["20001", "20001"], "groups": ["30001"]},
                                      "deny": {"private": [], "groups": []}, "allowAllWhenEmpty": False,
                                      "consoleToken": "SANITIZED", "social": {"unknown": True}, **changes}))
        return source

    def test_dryrun_and_idempotency_no_overwrite(self):
        source = self.source()
        before = r.sha(self.db)
        report = m.migrate(source, self.root)
        self.assertEqual(before, r.sha(self.db))
        self.assertEqual("DRY_RUN_PASS", report["status"])
        self.assertEqual(1, report["duplicates"])
        result = m.migrate(source, self.root, True, report["sourceFingerprint"])
        self.assertEqual("IMPORTED", result["status"])
        self.assertEqual("ALREADY_IMPORTED", m.migrate(source, self.root, True, report["sourceFingerprint"])["status"])
        with database(self.db) as db:
            self.assertEqual(1, db.execute("SELECT COUNT(*) FROM persons WHERE platform_user_id='20001'").fetchone()[0])
            self.assertEqual("fixture", db.execute("SELECT content FROM prompt_versions WHERE id='persona'").fetchone()[0])

    def test_conflicts_invalid_ambiguous_unsupported(self):
        source = self.source(ownerQQ="10001", deny={"private": ["10001"], "groups": []})
        report = m.migrate(source, self.root)
        self.assertEqual(1, report["conflicts"])
        self.assertGreater(report["unsupportedFields"], 0)
        with self.assertRaises(r.Rejected):
            m.migrate(self.source(ownerQQ="nickname"), self.root)
        source.write_text('{"ownerQQ":')
        with self.assertRaises(r.Rejected):
            m.migrate(source, self.root)
        source = self.source(deny={"private": ["20001"], "groups": []})
        self.assertEqual(1, m.migrate(source, self.root)["ambiguous"])

    def test_transaction_rollback_and_review_gate(self):
        source = self.source()
        with self.assertRaises(r.Rejected):
            m.migrate(source, self.root, True, "incorrect")
        with database(self.db) as db:
            db.execute("CREATE TRIGGER fixture_failure BEFORE INSERT ON console_access_rules BEGIN SELECT RAISE(ABORT,'fixture'); END")
        fingerprint = m.migrate(source, self.root)["sourceFingerprint"]
        with self.assertRaises(sqlite3.Error):
            m.migrate(source, self.root, True, fingerprint)
        with database(self.db) as db:
            self.assertEqual(0, db.execute("SELECT COUNT(*) FROM persons WHERE platform_user_id='20001'").fetchone()[0])
            self.assertEqual(0, db.execute("SELECT COUNT(*) FROM legacy_migration_ledger").fetchone()[0])

    def test_empty_identity_and_access_target(self):
        with database(self.db) as db:
            for table in ("relationship_terms", "memories", "persons", "console_access_rules"):
                db.execute("DELETE FROM " + table)
        source = self.source()
        report = m.migrate(source, self.root)
        self.assertEqual(3, report["importable"])
        self.assertEqual(3, m.migrate(source, self.root, True, report["sourceFingerprint"])["imported"])


if __name__ == "__main__":
    unittest.main()
