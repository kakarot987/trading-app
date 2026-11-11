"""Offline tests: no live services, secrets, browser sessions or Docker required."""
from __future__ import annotations

import importlib.util
import json
import tempfile
from decimal import Decimal
from pathlib import Path
from unittest import TestCase, main, mock

SCRIPT = Path(__file__).resolve().parents[1] / "scripts/private_demo_acceptance.py"
spec = importlib.util.spec_from_file_location("private_demo_acceptance", SCRIPT)
assert spec is not None and spec.loader is not None
app = importlib.util.module_from_spec(spec)
spec.loader.exec_module(app)
WALLET = "5b3b1dce-6a9e-4409-bf53-765b3b2bb042"
OTHER = "00000000-0000-0000-0000-000000000001"
TOKEN = "pw_" + "X" * 43


def fixture(status="RECONCILED"):
    return {
        "balances": [{"asset": "USDT", "available": "102.50000000"}],
        "orders": [{"symbol": "BTCUSDT", "side": "BUY", "quantity": "0.02"}],
        "evaluation": {"activity": {"fillCount": 1, "paidFeesUsdt": "0.10",
                                     "quoteLedgerNetUsdt": "2.50"}, "equity": None},
        "funding-audit": {"walletId": WALLET, "status": status,
                          "recordedInitialUsdt": "100.00" if status == "RECONCILED" else None,
                          "quoteLedgerNetUsdt": "2.50", "availableUsdt": "102.50",
                          "expectedAvailableUsdt": "102.50" if status == "RECONCILED" else None},
    }


class FakeClient:
    def __init__(self, data=None):
        self.data = data or fixture()
        self.calls = []

    def get(self, path, token=None):
        self.calls.append((path, token))
        if path == "/actuator/health":
            return (200, {}, {"status": "UP"})
        if token is None or path.startswith(f"/api/v1/paper/wallets/{OTHER}/"):
            return (401, {"cache-control": "no-store"}, None)
        for key, value in self.data.items():
            suffix = "orders?limit=50" if key == "orders" else key
            if path == f"/api/v1/paper/wallets/{WALLET}/{suffix}":
                return (200, {"cache-control": "no-store"}, value)
        raise AssertionError("Unexpected HTTP request")


def check(data=None, token=TOKEN, strict=True, client=None):
    return app.live_evidence("http://127.0.0.1:8084", WALLET, token, False,
                             strict, client or FakeClient(data))


class PrivateDemoAcceptanceTest(TestCase):
    def test_rejects_non_loopback_or_embedded_credentials(self):
        for value in ["https://127.0.0.1:8084", "http://0.0.0.0:8084",
                      "http://example.com:8084", "http://127.0.0.1",
                      "http://127.0.0.1:8084/admin", "http://127.0.0.1:8084?secret=yes",
                      "http://name:password@127.0.0.1:8084"]:
            with self.subTest(value=value), self.assertRaises(app.CheckFailure):
                app.local_url(value)
        self.assertEqual(app.local_url("http://localhost:8084/"),
                         "http://localhost:8084")

    def test_no_external_redirect_or_proxy(self):
        client = app.HttpClient("http://127.0.0.1:8084")
        self.assertTrue(any(isinstance(h, app.NoRedirect)
                            for h in client.opener.handlers))
        self.assertFalse(any(isinstance(h, app.urllib.request.ProxyHandler)
                             for h in client.opener.handlers))

    def test_auth_funding_and_non_disclosure(self):
        client = FakeClient()
        report = check(client=client)
        self.assertEqual(report["summary"], {"passed": 7, "failed": 0, "not_run": 1})
        self.assertEqual(report["release_decision"], "NOT_EVALUATED")
        self.assertNotIn(TOKEN, json.dumps(report))
        self.assertNotIn(WALLET, json.dumps(report))
        observation = next(c["observation"] for c in report["checks"]
                           if c["id"] == "wallet_portfolio_reconciliation")
        self.assertEqual(observation["funding_status"], "RECONCILED")
        self.assertTrue(all(item[1] == TOKEN for item in client.calls
                            if item[0].endswith("/funding-audit") and item[1]))

    def test_no_secret_marks_live_checks_not_run(self):
        report = check(token=None, strict=False)
        self.assertEqual(report["summary"]["not_run"], 3)
        self.assertEqual(report["summary"]["failed"], 0)
        self.assertNotIn("RECONCILED", json.dumps(report))

    def test_strict_missing_wallet_secret_fails(self):
        report = check(token=None, strict=True)
        self.assertEqual(report["summary"]["failed"], 1)

    def test_cross_wallet_denied(self):
        report = check()
        self.assertEqual(next(c for c in report["checks"]
                              if c["id"] == "cross_wallet_denial")["status"], "PASS")

    def test_funding_mismatch_or_inconsistent_ledger_fails(self):
        modified = fixture()
        modified["funding-audit"]["availableUsdt"] = "999"
        self.assertEqual(check(modified)["summary"]["failed"], 1)
        modified = fixture()
        modified["funding-audit"]["quoteLedgerNetUsdt"] = "-10"
        self.assertEqual(check(modified)["summary"]["failed"], 1)
        self.assertEqual(check(fixture("MISMATCH"))["summary"]["failed"], 1)

    def test_legacy_wallet_does_not_invent_funding(self):
        report = check(fixture("LEGACY_UNVERIFIED"))
        self.assertEqual(report["summary"]["failed"], 0)
        observation = next(c["observation"] for c in report["checks"]
                           if c["id"] == "wallet_portfolio_reconciliation")
        self.assertEqual(observation["funding_status"], "LEGACY_UNVERIFIED")

    def test_money_rejects_non_finite_or_negative(self):
        for item in ["NaN", "Infinity", "-1", None, True]:
            with self.subTest(item=item), self.assertRaises(app.CheckFailure):
                app.non_negative(item, "amount")
        self.assertEqual(app.finite_decimal("0.12345678", "amount"),
                         Decimal("0.12345678"))

    def test_no_store_is_required(self):
        class MissingCacheHeader(FakeClient):
            def get(self, path, token=None):
                code, _, body = super().get(path, token)
                return code, {}, body
        self.assertEqual(check(client=MissingCacheHeader())["summary"]["failed"], 6)

    def test_docker_binding_is_loopback_only(self):
        class Done:
            def __init__(self, port):
                self.stdout = json.dumps({"Names": "historical-data-service",
                                          "Ports": port})
        with mock.patch.object(app.subprocess, "run",
                               return_value=Done("127.0.0.1:8084->8084/tcp")):
            self.assertEqual(app.docker_ports()["verified_loopback_published_ports"], 1)
        with mock.patch.object(app.subprocess, "run",
                               return_value=Done("0.0.0.0:8084->8084/tcp")):
            with self.assertRaises(app.CheckFailure):
                app.docker_ports()

    def test_offline_inventory_rejects_missing_paths(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            (root / "docs/milestone-results").mkdir(parents=True)
            for n in range(10, 16):
                (root / "docs/milestone-results" / f"M{n}-ACCEPTANCE.md").write_text("review")
            rel = "docs/milestone-results/M10-ACCEPTANCE.md"
            phases = [{"id": f"M{n}", "prs": [n], "evidencePaths": [rel],
                       "openGates": [{"id": f"m{n}-gate", "requiredEvidence": "Review"}]}
                      for n in range(1, 9)]
            manifest = root / "docs/milestone-acceptance-manifest.json"
            manifest.write_text(json.dumps({"phases": phases}))
            with mock.patch.object(app, "ROOT", root):
                self.assertEqual(app.check_manifest()["phase_references"], 8)
                phases[2]["evidencePaths"] = ["../../private.env"]
                manifest.write_text(json.dumps({"phases": phases}))
                with self.assertRaises(app.CheckFailure):
                    app.check_manifest()

    def test_report_file_permissions_and_cli(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "report.json"
            with mock.patch.object(app, "check_manifest", return_value={"phase_references": 8}), \
                    mock.patch.object(app, "git_commit", return_value="a" * 40):
                self.assertEqual(app.main(["--mode", "offline", "--report", str(report)]), 0)
            self.assertEqual(report.stat().st_mode & 0o777, 0o600)
            self.assertEqual(json.loads(report.read_text())["release_decision"], "NOT_EVALUATED")


if __name__ == "__main__":
    main()
