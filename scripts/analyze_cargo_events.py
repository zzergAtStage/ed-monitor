#!/usr/bin/env python3
"""
Analyze Loadout and Cargo events from Elite Dangerous journal to track cargo bay capacity changes.

Usage:
    python3 analyze_cargo_events.py [path_to_journal.log]

This script traces:
1. Loadout events - when ship configuration changes (includes cargo capacity)
2. Cargo events - full snapshot of current cargo inventory
3. CargoTransfer events - individual cargo transfers
4. MarketBuy/MarketSell events - commodity market transactions
"""

import json
import sys
from pathlib import Path
from datetime import datetime


def get_latest_journal():
    """Find the most recent journal file."""
    journal_dir = Path(
        r"C:\Users\Father\Saved Games\Frontier Developments\Elite Dangerous"
    )
    journal_files = list(journal_dir.glob("Journal.*.log"))
    if not journal_files:
        return None
    return max(journal_files, key=lambda p: p.stat().st_mtime)


def load_events(path: str, event_types: list[str]):
    """Load events of specific types from journal file."""
    events = []
    try:
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    obj = json.loads(line)
                    if obj.get("event") in event_types:
                        events.append(obj)
                except json.JSONDecodeError:
                    pass
    except FileNotFoundError:
        print(f"ERROR: File not found: {path}")
        sys.exit(1)
    return events


def print_loadout_event(event):
    """Print detailed Loadout event information."""
    print(f"\n{'='*80}")
    print(f"[{event.get('timestamp', 'N/A')}] LOADOUT EVENT")
    print(f"{'='*80}")
    print(f"Ship: {event.get('Ship', 'N/A')} (ID: {event.get('ShipID', 'N/A')})")
    print(f"Ship Name: {event.get('ShipName', 'N/A')}")
    print(f"Cargo Capacity: {event.get('CargoCapacity', 'N/A')} tons")
    print(f"Fuel Capacity: {event.get('FuelCapacity', {}).get('Main', 0):.1f} tons")

    # Show all modules
    modules = event.get("Modules", [])
    if modules:
        print(f"\nEquipped Modules ({len(modules)}):")
        for module in modules:
            slot = module.get("Slot", "N/A")
            item = module.get("Item", "N/A")
            engineering = module.get("Engineering", {})
            if engineering:
                eng_name = engineering.get("Engineer", "")
                print(f"  {slot:<20} {item:<40} (Engineer: {eng_name})")
            else:
                print(f"  {slot:<20} {item}")


def print_cargo_event(event):
    """Print detailed Cargo event information (full inventory snapshot)."""
    print(f"\n{'='*80}")
    print(f"[{event.get('timestamp', 'N/A')}] CARGO EVENT (Full Inventory)")
    print(f"{'='*80}")

    inventory = event.get("Inventory", [])
    print(f"Total items in cargo: {len(inventory)}")

    if inventory:
        print(f"\n{'Commodity':<30} {'Count':>8} {'Stolen':>8}")
        print(f"{'-'*48}")
        total_units = 0
        for item in inventory:
            name = item.get("Name_Localised", item.get("Name", "N/A"))
            count = item.get("Count", 0)
            stolen = item.get("Stolen", 0)
            total_units += count
            print(f"{name:<30} {count:>8} {stolen:>8}")
        print(f"{'-'*48}")
        print(f"{'TOTAL UNITS':<30} {total_units:>8}")
    else:
        print("Cargo hold is EMPTY")


def print_cargo_transfer_event(event):
    """Print CargoTransfer event."""
    print(f"\n[{event.get('timestamp', 'N/A')}] CARGO TRANSFER")
    direction = event.get("Direction", "N/A")
    commodity = event.get("Name_Localised", event.get("Name", "N/A"))
    amount = event.get("Count", 0)
    print(f"  Direction: {direction}")
    print(f"  Commodity: {commodity}")
    print(f"  Amount: {amount} units")


def print_market_event(event):
    """Print MarketBuy or MarketSell event."""
    etype = event.get("event", "N/A")
    print(f"\n[{event.get('timestamp', 'N/A')}] {etype}")
    commodity = event.get("Name_Localised", event.get("Name", "N/A"))
    count = event.get("Count", 0)
    price = event.get("Price", 0)
    total = event.get("TotalCost", count * price)
    print(f"  Commodity: {commodity}")
    print(f"  Count: {count}")
    print(f"  Price per unit: {price:,}")
    print(f"  Total: {total:,} Cr")


def main():
    # Determine journal path
    if len(sys.argv) > 1:
        journal_path = sys.argv[1]
    else:
        latest = get_latest_journal()
        if not latest:
            print("ERROR: Could not find journal file")
            sys.exit(1)
        journal_path = str(latest)

    print(f"\nAnalyzing Elite Dangerous journal: {journal_path}\n")

    # Load all relevant event types
    event_types = [
        "Loadout",
        "Cargo",
        "CargoTransfer",
        "MarketBuy",
        "MarketSell",
        "ColonisationContribution",
    ]

    events = load_events(journal_path, event_types)
    print(f"Found {len(events)} relevant events\n")

    if not events:
        print("No relevant events found in journal.")
        sys.exit(0)

    # Group by type
    by_type = {}
    for event in events:
        etype = event.get("event")
        if etype not in by_type:
            by_type[etype] = []
        by_type[etype].append(event)

    # Print summary
    print("Event Summary:")
    print("-" * 80)
    for etype in sorted(by_type.keys()):
        count = len(by_type[etype])
        print(f"  {etype:<20} {count:>3} events")

    # Print detailed information
    print("\n" + "=" * 80)
    print("DETAILED EVENT TRACE")
    print("=" * 80)

    # Sort all events by timestamp
    sorted_events = sorted(events, key=lambda e: e.get("timestamp", ""))

    for event in sorted_events:
        etype = event.get("event")

        if etype == "Loadout":
            print_loadout_event(event)
        elif etype == "Cargo":
            print_cargo_event(event)
        elif etype == "CargoTransfer":
            print_cargo_transfer_event(event)
        elif etype in ("MarketBuy", "MarketSell"):
            print_market_event(event)
        elif etype == "ColonisationContribution":
            print(f"\n[{event.get('timestamp', 'N/A')}] COLONISATION CONTRIBUTION")
            contributions = event.get("Contributions", [])
            for contrib in contributions:
                name = contrib.get("Name", "N/A")
                amount = contrib.get("Amount", 0)
                print(f"  {name}: +{amount}")

    print("\n" + "=" * 80)
    print("ANALYSIS SUMMARY")
    print("=" * 80)

    # Show key findings
    if "Loadout" in by_type and by_type["Loadout"]:
        latest_loadout = by_type["Loadout"][-1]
        print(f"\nLatest Ship Configuration (Loadout):")
        print(f"  Ship: {latest_loadout.get('Ship')}")
        print(f"  Cargo Capacity: {latest_loadout.get('CargoCapacity')} tons")

    if "Cargo" in by_type and by_type["Cargo"]:
        latest_cargo = by_type["Cargo"][-1]
        inventory = latest_cargo.get("Inventory", [])
        total_units = sum(item.get("Count", 0) for item in inventory)
        print(f"\nLatest Cargo Snapshot:")
        print(f"  Timestamp: {latest_cargo.get('timestamp')}")
        print(f"  Items in hold: {len(inventory)}")
        print(f"  Total units: {total_units}")


if __name__ == "__main__":
    main()
