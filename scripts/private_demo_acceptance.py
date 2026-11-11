#!/usr/bin/env python3
"""Read-only evidence collector for the *private* simulated paper-trading demo.

The collector neither provisions wallets nor submits/rotates/revokes paper
orders/credentials. It cannot make release decisions or verify external claims.
"""
from __future__ import annotations

import argparse
import datetime as dt
from decimal import Decimal, InvalidOperation
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent.parent
PAPER_PATHS = ("balances", "orders?limit=50", "evaluation", "funding-audit")
MAX_RESPONSE_BYTES = 1_000_000


class CheckFailure(Exception):
    """A check failed without leaking a response body or credential."""


def status_of(value: object) -> str:
    if isinstance(value, dict):
        return str(value.get("status", ""))
    return ""


def finite_decimal(value: object, label: str) -> Decimal:
    try:
        # Decimal(float) would import floating-point rounding error.
        if isinstance(value, bool) or value is None or isinstance(value, float):
            raise InvalidOperation
        number = Decimal(str(value))
        if not number.is_finite():
            raise InvalidOperation
        return number
    except (InvalidOperation, ValueError):
        raise CheckFailure(f"{label} must be a finite decimal") from None


def non_negative(value: object, label: str) -> Decimal:
    number = finite_decimal(value, label)
    if number < 0:
        raise CheckFailure(f"{label} cannot be negative")
    return number


def local_url(raw: str) -> str:
    try:
        url = urllib.parse.urlsplit(raw)
        port = url.port
    except ValueError as exc:
        raise CheckFailure("Invalid local service URL") from exc
    if (url.scheme != "http" or url.hostname not in {"127.0.0.1", "localhost", "::1"}
            or not port or url.username or url.password or url.query or url.fragment
            or url.path not in {"", "/"}):
        raise CheckFailure("Service URL must be HTTP loopback host with explicit port and no path")
    return f"http://{url.netloc}"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: ARG002
        return None


class HttpClient:
    def __init__(self, base: str):
        self.base = local_url(base)
        # Never honor HTTP_PROXY or follow redirects, especially when a wallet
        # capability is sent in an Authorization header.
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def get(self, path: str, token: str | None = None) -> tuple[int, dict[str, str], object]:
        if not path.startswith("/") or "//" in path or ".." in path:
            raise CheckFailure("Invalid fixed API path")
        headers = {"Accept": "application/json"}
        if token is not None:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(self.base + path, headers=headers, method="GET")
        try:
            response = self.opener.open(request, timeout=12)
        except urllib.error.HTTPError as exc:
            response = exc
        except (OSError, urllib.error.URLError) as exc:
            raise CheckFailure("Local service not reachable; no live acceptance evidence") from exc
        with response:
            code = response.getcode()
            response_headers = {k.lower(): v for k, v in response.headers.items()}
            body = response.read(MAX_RESPONSE_BYTES + 1)
        if len(body) > MAX_RESPONSE_BYTES:
            raise CheckFailure("HTTP response exceeds one-megabyte evidence limit")
        if not body:
            return code, response_headers, None
        try:
            value = json.loads(body, parse_float=Decimal)
        except (UnicodeDecodeError, ValueError):
            if code == 401:
                value = None  # Nothing from a denied response is trusted.
            else:
                raise CheckFailure("Unexpected non-JSON HTTP response") from None
        return code, response_headers, value


class Evidence:
    def __init__(self, mode: str):
        self.data: dict[str, object] = {
            "schema_version": 1,
            "observed_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
            "mode": mode,
            "checked_commit": git_commit(),
            "checks": [],
            "acceptance_scope": "read-only local API observations and/or static repository inventory",
            "release_decision": "NOT_EVALUATED",
            "not_checked": [
                "production identity and human wallet ownership",
                "real exchange/news licensing and key rotation",
                "fixed-rate sustained Kafka throughput and independent host resource accounting",
                "real market-vintage, model holdout, forward-paper returns and equity marks",
                "isolated crash injection, backup recovery and incident response",
                "external security and operational release approval",
            ],
        }

    def check(self, key: str, fn, *, not_run: str | None = None):
        if not_run is not None:
            self.data["checks"].append({"id": key, "status": "NOT_RUN", "reason": not_run})
            return
        try:
            result = fn()
            item = {"id": key, "status": "PASS"}
            if result is not None:
                item["observation"] = result
            self.data["checks"].append(item)
        except (CheckFailure, AssertionError) as exc:
            # Never render the raw URL, auth header, database credentials,
            # exception repr, or HTTP body into logs or saved evidence.
            self.data["checks"].append({"id": key, "status": "FAIL", "reason": str(exc)})
        except Exception:
            self.data["checks"].append({"id": key, "status": "FAIL", "reason": "Check raised an unexpected error"})

    def finished(self) -> dict[str, object]:
        checks = self.data["checks"]
        self.data["summary"] = {
            "passed": sum(x["status"] == "PASS" for x in checks),
            "failed": sum(x["status"] == "FAIL" for x in checks),
            "not_run": sum(x["status"] == "NOT_RUN" for x in checks),
        }
        return self.data


def git_commit() -> str | None:
    try:
        done = subprocess.run(["git", "-C", str(ROOT), "rev-parse", "HEAD"],
                              capture_output=True, text=True, timeout=5, check=True)
        sha = done.stdout.strip()
        return sha if re.fullmatch(r"[0-9a-f]{40}", sha) else None
    except (OSError, subprocess.SubprocessError):
        return None


def check_manifest() -> dict[str, object]:
    manifest = json.loads((ROOT / "docs/milestone-acceptance-manifest.json").read_text(encoding="utf-8"))
    phases = manifest.get("phases", [])
    if [phase.get("id") for phase in phases] != [f"M{i}" for i in range(1, 9)]:
        raise CheckFailure("Acceptance manifest does not enumerate M1 through M8 exactly")
    paths: set[str] = set()
    requirements: set[str] = set()
    for phase in phases:
        if not phase.get("prs") or not phase.get("evidencePaths") or not phase.get("openGates"):
            raise CheckFailure("Manifest phase has missing references or review gates")
        for raw in phase["evidencePaths"]:
            if not isinstance(raw, str) or not re.fullmatch(r"[a-zA-Z0-9_./-]+", raw):
                raise CheckFailure("Invalid manifest reference path")
            rel = Path(raw)
            if rel.is_absolute() or ".." in rel.parts or not (ROOT / rel).is_file():
                raise CheckFailure("Manifest evidence path is missing or outside checkout")
            paths.add(raw)
        for gate in phase["openGates"]:
            key = gate.get("id")
            if not isinstance(key, str) or key in requirements or not gate.get("requiredEvidence"):
                raise CheckFailure("Invalid or duplicated pending evidence requirement")
            requirements.add(key)
    for version in range(10, 16):
        path = ROOT / f"docs/milestone-results/M{version}-"
        if not list(path.parent.glob(path.name + "*.md")):
            raise CheckFailure(f"M{version} acceptance document missing")
    return {"phase_references": 8, "referenced_files": len(paths),
            "separate_review_requirements": len(requirements), "recent_phase_docs": "M10-M15 present"}


def require_http(response: tuple[int, dict, object], expected_code: int, label: str):
    status, headers, data = response
    if status != expected_code:
        raise CheckFailure(f"{label}: HTTP {expected_code} expected, got {status}")
    if "no-store" not in headers.get("cache-control", "").lower():
        raise CheckFailure(f"{label}: missing Cache-Control no-store")
    return data


def paper_data(client: HttpClient, wallet: str, token: str) -> dict[str, object]:
    prefix = f"/api/v1/paper/wallets/{wallet}"
    balances = require_http(client.get(prefix + "/balances", token), 200, "balances")
    orders = require_http(client.get(prefix + "/orders?limit=50", token), 200, "orders")
    evaluation = require_http(client.get(prefix + "/evaluation", token), 200, "evaluation")
    funding = require_http(client.get(prefix + "/funding-audit", token), 200, "funding audit")
    if not isinstance(balances, list) or not isinstance(orders, list):
        raise CheckFailure("Paper balances and orders must be JSON arrays")
    if not isinstance(evaluation, dict) or not isinstance(evaluation.get("activity"), dict):
        raise CheckFailure("Evaluation activity must be a structured JSON object")
    if not isinstance(funding, dict):
        raise CheckFailure("Funding audit must be a structured JSON object")
    asset_values = {}
    for balance in balances:
        if not isinstance(balance, dict) or balance.get("asset") not in ("USDT", "BTC", "ETH"):
            raise CheckFailure("Unexpected balance asset")
        asset = balance["asset"]
        if asset in asset_values:
            raise CheckFailure("Duplicate asset balance")
        asset_values[asset] = non_negative(balance.get("available"), "available balance")
    for order in orders:
        if not isinstance(order, dict) or order.get("symbol") not in ("BTCUSDT", "ETHUSDT"):
            raise CheckFailure("Invalid paper order entry")
        if order.get("side") not in ("BUY", "SELL"):
            raise CheckFailure("Invalid paper order side")
        non_negative(order.get("quantity"), "paper order quantity")
    activity = evaluation["activity"]
    fills = activity.get("fillCount")
    if isinstance(fills, bool) or not isinstance(fills, int) or fills < 0:
        raise CheckFailure("Invalid paper fill count")
    fees = non_negative(activity.get("paidFeesUsdt"), "paper fees")
    activity_quote = finite_decimal(activity.get("quoteLedgerNetUsdt"), "quote ledger net")
    funding_status = funding.get("status")
    if funding_status not in ("RECONCILED", "MISMATCH", "LEGACY_UNVERIFIED"):
        raise CheckFailure("Unknown funding audit status")
    if funding.get("walletId") != wallet:
        raise CheckFailure("Funding audit returned another wallet's identity")
    funding_quote = finite_decimal(funding.get("quoteLedgerNetUsdt"), "funding quote ledger")
    if funding_quote != activity_quote:
        raise CheckFailure("Funding and evaluation quote ledger observations diverge")
    if funding_status == "MISMATCH":
        raise CheckFailure("Immutable paper funding does not reconcile with USDT balance")
    if funding_status == "RECONCILED":
        seed = non_negative(funding.get("recordedInitialUsdt"), "initial synthetic USDT")
        available = non_negative(funding.get("availableUsdt"), "available synthetic USDT")
        expected = non_negative(funding.get("expectedAvailableUsdt"), "expected synthetic USDT")
        if seed + funding_quote != expected or available != expected:
            raise CheckFailure("Paper funding arithmetic inconsistent with recorded balance")
        if "USDT" not in asset_values or asset_values["USDT"] != available:
            raise CheckFailure("Funding and portfolio USDT balances disagree")
    else:
        if funding.get("recordedInitialUsdt") is not None or funding.get("expectedAvailableUsdt") is not None:
            raise CheckFailure("Legacy wallet funding must not be inferred")
    # No raw order history or wallet token is written into the report.
    return {"balances_count": len(balances), "order_rows": len(orders), "fill_count": fills,
            "paid_fees_usdt": str(fees), "funding_status": funding_status,
            "equity_marks_present": evaluation.get("equity") is not None}


def docker_ports() -> dict[str, object]:
    try:
        result = subprocess.run(["docker", "ps", "--format", "{{json .}}"],
                                capture_output=True, text=True, timeout=12, check=True)
    except (OSError, subprocess.SubprocessError):
        raise CheckFailure("Docker daemon is unavailable for read-only port observation") from None
    expected = {"postgres", "kafka", "redis", "historical-data-service", "trade-processor"}
    matching = 0
    published = 0
    for line in result.stdout.splitlines():
        try:
            entry = json.loads(line)
        except ValueError:
            raise CheckFailure("Unexpected docker ps output") from None
        if entry.get("Names") not in expected:
            continue
        matching += 1
        for raw in str(entry.get("Ports", "")).split(","):
            port = raw.strip()
            if "->" not in port:
                continue
            match = re.fullmatch(r"(\[[^]]+\]|[^:]+):\d+->\d+/(tcp|udp)", port)
            if not match or match.group(1) not in ("127.0.0.1", "[::1]"):
                raise CheckFailure("Project container exposes a non-loopback host port")
            published += 1
    if matching == 0:
        raise CheckFailure("No project containers found: cannot verify live bindings")
    return {"matching_running_containers": matching, "verified_loopback_published_ports": published}


def live_evidence(base: str, wallet: str, token: str | None, check_docker: bool,
                  strict_wallet: bool, client: HttpClient | None = None) -> dict[str, object]:
    uuid.UUID(wallet)  # CLI validation occurs before a request is sent.
    client = client or HttpClient(base)
    evidence = Evidence("live-private-loopback")
    prefix = f"/api/v1/paper/wallets/{wallet}"

    def health():
        code, _, response = client.get("/actuator/health")
        if code != 200 or status_of(response) != "UP":
            raise CheckFailure("Historical service health is not UP")
        return {"health": "UP"}
    evidence.check("historical_health", health)
    for suffix in PAPER_PATHS:
        def anonymous(suffix=suffix):
            require_http(client.get(prefix + "/" + suffix), 401, "anonymous wallet request")
        evidence.check("anonymous_denial_" + suffix.split("?")[0], anonymous)
    if token:
        if not re.fullmatch(r"pw_[A-Za-z0-9_-]{43}", token):
            evidence.check("wallet_credential_format", lambda: (_ for _ in ()).throw(
                CheckFailure("Wallet credential environment variable has invalid format")))
        else:
            evidence.check("wallet_portfolio_reconciliation", lambda: paper_data(client, wallet, token))
            def cross_wallet():
                other = "00000000-0000-0000-0000-000000000001"
                if wallet == other:
                    other = "00000000-0000-0000-0000-000000000002"
                require_http(client.get(f"/api/v1/paper/wallets/{other}/balances", token),
                             401, "cross-wallet request")
            evidence.check("cross_wallet_denial", cross_wallet)
    else:
        reason = "Wallet credential not supplied; no authenticated checks were performed"
        if strict_wallet:
            evidence.check("wallet_credential_required", lambda: (_ for _ in ()).throw(CheckFailure(reason)))
        else:
            evidence.check("wallet_portfolio_reconciliation", None, not_run=reason)
            evidence.check("cross_wallet_denial", None, not_run=reason)
    evidence.check("docker_live_loopback_bindings", docker_ports,
                   not_run=None if check_docker else "Docker port inspection not requested")
    return evidence.finished()


def write_report(path: Path, report: dict[str, object]):
    path.parent.mkdir(parents=True, exist_ok=True)
    # JSON excludes all raw tokens, request headers, HTTP bodies and wallet IDs.
    # Use restrictive permissions and do not write alongside tracked source by default.
    flags = os.O_CREAT | os.O_TRUNC | os.O_WRONLY
    fd = os.open(path, flags, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        json.dump(report, stream, indent=2)
        stream.write("\n")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("offline", "live"), required=True)
    parser.add_argument("--base-url", default="http://127.0.0.1:8084")
    parser.add_argument("--wallet-id")
    parser.add_argument("--token-env", default="PRIVATE_PAPER_WALLET_TOKEN")
    parser.add_argument("--require-wallet", action="store_true")
    parser.add_argument("--check-docker", action="store_true")
    parser.add_argument("--report", type=Path, default=Path("private-demo-evidence.json"))
    args = parser.parse_args(argv)
    try:
        if args.mode == "offline":
            e = Evidence("offline-repository-references")
            e.check("repository_phase_references", check_manifest)
            report = e.finished()
        else:
            if not args.wallet_id:
                parser.error("--wallet-id is required in live mode")
            try:
                wallet = str(uuid.UUID(args.wallet_id))
            except ValueError:
                parser.error("--wallet-id must be a valid UUID")
            if not re.fullmatch(r"[A-Z][A-Z0-9_]{1,80}", args.token_env):
                parser.error("--token-env must be a safe environment variable name")
            base = local_url(args.base_url)
            report = live_evidence(base, wallet, os.environ.get(args.token_env),
                                   args.check_docker, args.require_wallet)
        write_report(args.report, report)
        for item in report["checks"]:
            print(f"[{item['status']}] {item['id']}")
        print(f"Evidence saved to {args.report}. This is not a release decision.")
        return 1 if report["summary"]["failed"] else 0
    except CheckFailure as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
