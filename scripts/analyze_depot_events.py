#!/usr/bin/env python3
"""
Analyze ColonisationConstructionDepot and ColonisationContribution events
from an Elite Dangerous journal file to see how amounts change after deliveries.

Usage:
    python3 analyze_depot_events.py [path_to_journal.log]
"""

import json
import sys
from pathlib import Path


DEFAULT_JOURNAL = (
    "/mnt/c/Users/Father/Saved Games/Frontier Developments/"
    "Elite Dangerous/Journal.2026-06-27T235133.01.log"
)


def load_events(path: str):
    events = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
                if obj.get("event") in (
                    "ColonisationConstructionDepot",
                    "ColonisationContribution",
                ):
                    events.append(obj)
            except json.JSONDecodeError:
                pass
    return events


def depot_snapshot(event) -> dict:
    """Return {name: (required, provided)} from a Depot event."""
    return {
        r["Name_Localised"]: (r["RequiredAmount"], r["ProvidedAmount"])
        for r in event.get("ResourcesRequired", [])
    }


def diff_snapshots(before: dict, after: dict) -> list:
    """Return rows where provided amount changed."""
    changes = []
    for name in after:
        req_b, prov_b = before.get(name, (0, 0))
        req_a, prov_a = after[name]
        if prov_a != prov_b or req_a != req_b:
            changes.append((name, req_b, prov_b, req_a, prov_a))
    return changes


def main():
    journal_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_JOURNAL
    if not Path(journal_path).exists():
        print(f"File not found: {journal_path}")
        sys.exit(1)

    events = load_events(journal_path)
    print(f"Loaded {len(events)} relevant events from {journal_path}\n")

    last_depot: dict | None = None
    last_depot_time: str = ""
    contribution_pending: list = []

    for ev in events:
        ts = ev.get("timestamp", "")
        etype = ev["event"]

        if etype == "ColonisationContribution":
            contribs = {
                c["Name"]: c["Amount"] for c in ev.get("Contributions", [])
            }
            contribution_pending.append((ts, contribs))

        elif etype == "ColonisationConstructionDepot":
            snap = depot_snapshot(ev)
            progress = ev.get("ConstructionProgress", 0.0)

            if last_depot is None:
                print(f"[{ts}] First Depot snapshot — progress {progress:.2%}")
                print(f"  Market ID: {ev.get('MarketID')}")
                _print_snapshot(snap)
            else:
                changes = diff_snapshots(last_depot, snap)
                if changes or contribution_pending:
                    print()
                    print(f"[{ts}] Depot update — progress {progress:.2%}")

                    if contribution_pending:
                        print("  Contributions since last depot:")
                        for c_ts, contribs in contribution_pending:
                            for name, amount in contribs.items():
                                print(f"    [{c_ts}] {name}: +{amount}")
                        contribution_pending.clear()

                    if changes:
                        print("  Amount changes:")
                        print(f"  {'Commodity':<30} {'Req before':>10} {'Prov before':>11} {'Req after':>10} {'Prov after':>11} {'Delta prov':>10}")
                        print("  " + "-" * 88)
                        for name, rb, pb, ra, pa in changes:
                            delta = pa - pb
                            flag = "  *** DOUBLED ***" if ra == rb * 2 or (pa == pb * 2 and pb > 0) else ""
                            print(f"  {name:<30} {rb:>10} {pb:>11} {ra:>10} {pa:>11} {delta:>+10}{flag}")
                    else:
                        print("  (no amount changes, but contributions were noted above)")

            last_depot = snap
            last_depot_time = ts

    if contribution_pending:
        print("\nContributions after last depot event (no subsequent Depot to compare):")
        for c_ts, contribs in contribution_pending:
            for name, amount in contribs.items():
                print(f"  [{c_ts}] {name}: +{amount}")

    print("\n--- Final snapshot ---")
    if last_depot:
        _print_snapshot(last_depot)


def _print_snapshot(snap: dict):
    print(f"  {'Commodity':<30} {'Required':>10} {'Provided':>10} {'Remaining':>10}")
    print("  " + "-" * 64)
    for name, (req, prov) in snap.items():
        remaining = max(req - prov, 0)
        done = " (DONE)" if remaining == 0 else ""
        print(f"  {name:<30} {req:>10} {prov:>10} {remaining:>10}{done}")


if __name__ == "__main__":
    main()
