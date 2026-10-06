#!/usr/bin/env python3
"""Measures the bundled packs on an iPhone, the way spec §25.4 says a measurement is taken.

Launches the installed app once per pack, reads the `AnatomyPerf` line it prints each
second, and reduces every launch to medians. The packs are interleaved and the whole round
is repeated, because the device drifts with temperature by more than the effects being
measured: only figures taken side by side inside one session can be compared.

    ./measure-packs.py --device "Bartosz's iPhone"
    ./measure-packs.py --simulator booted          # checks the plumbing, not the budget

The app must already be installed, built from a tree in which the packs were generated.
"""

import argparse
import re
import statistics
import subprocess
import sys
import threading

FIELD = re.compile(r"(\w+)=(\S+)")
NUMERIC = (
    "structures", "fps", "gpuMs", "refreshHz", "linkHz", "grantedHz",
    "maxGapMs", "refused", "residentMb", "footprintMb",
)


def launch_command(args, pack):
    arguments = [args.bundle_id, "-anatomypro.pack", pack]
    if args.simulator:
        return ["xcrun", "simctl", "launch", "--console-pty", "--terminate-running-process",
                args.simulator, *arguments]
    return ["xcrun", "devicectl", "device", "process", "launch", "--console",
            "--terminate-existing", "--device", args.device, *arguments]


def sample(args, pack):
    """Runs one pack for the configured time and returns its steady-state samples."""
    process = subprocess.Popen(
        launch_command(args, pack),
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace",
    )
    samples, other = [], []
    wanted = args.discard + args.samples
    # Reading blocks for as long as the app stays silent, so the limit has to come from
    # outside the loop: an app that never reaches the atlas prints nothing at all.
    watchdog = threading.Timer(wanted + args.grace, process.terminate)
    watchdog.start()
    try:
        for line in process.stdout:
            if "AnatomyPerf " in line:
                fields = dict(FIELD.findall(line.split("AnatomyPerf ", 1)[1]))
                samples.append({k: float(fields[k]) for k in NUMERIC if k in fields})
            else:
                other.append(line.rstrip())
            if len(samples) >= wanted:
                break
    finally:
        watchdog.cancel()
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()

    if len(samples) <= args.discard:
        tail = "\n    ".join(other[-8:])
        sys.exit(f"'{pack}' produced {len(samples)} samples, not enough to discard "
                 f"{args.discard}. The log only runs while the atlas is on screen, so finish "
                 f"onboarding on the device first. Last output:\n    {tail}")
    # The first seconds are shader compilation and the load itself, not steady state.
    return samples[args.discard:]


def p90(values):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, round(0.9 * (len(ordered) - 1)))]


def summarise(samples):
    column = lambda key: [s[key] for s in samples if key in s]
    median = lambda key: statistics.median(column(key)) if column(key) else float("nan")
    return {
        "n": len(samples),
        "structures": median("structures"),
        "fps": median("fps"),
        "gpuMs": median("gpuMs"),
        "gpuP90": p90(column("gpuMs")) if column("gpuMs") else float("nan"),
        "refreshHz": median("refreshHz"),
        "linkHz": median("linkHz"),
        "grantedHz": median("grantedHz"),
        "maxGapMs": max(column("maxGapMs"), default=float("nan")),
        "refused": sum(column("refused")),
        "residentMb": median("residentMb"),
        "footprintMb": median("footprintMb"),
    }


COLUMNS = (
    ("n", "n", "{:.0f}"), ("structures", "structs", "{:.0f}"), ("fps", "fps", "{:.0f}"),
    ("gpuMs", "gpu ms", "{:.1f}"), ("gpuP90", "gpu p90", "{:.1f}"),
    ("refreshHz", "panel Hz", "{:.0f}"), ("linkHz", "link Hz", "{:.0f}"),
    ("grantedHz", "granted Hz", "{:.0f}"), ("maxGapMs", "max gap ms", "{:.1f}"),
    ("refused", "refused", "{:.0f}"), ("residentMb", "resident MB", "{:.0f}"),
    ("footprintMb", "footprint MB", "{:.0f}"),
)


def row(label, summary):
    return "| " + " | ".join([label] + [fmt.format(summary[key]) for key, _, fmt in COLUMNS]) + " |"


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--device", help="UDID or name of a connected iPhone")
    target.add_argument("--simulator", help="simulator UDID, or 'booted'")
    parser.add_argument("--bundle-id", default="com.ptk.anatomypro.AnatomyPro",
                        help="append your TEAM_ID if Config.xcconfig sets one")
    parser.add_argument("--packs", nargs="+",
                        default=["skeletal-trunk", "muscular-trunk", "skeletal-body"])
    parser.add_argument("--rounds", type=int, default=2, help="times the whole set is repeated")
    parser.add_argument("--samples", type=int, default=21, help="steady-state seconds kept per launch")
    parser.add_argument("--discard", type=int, default=10, help="warm-up seconds dropped per launch")
    parser.add_argument("--grace", type=int, default=30, help="extra seconds allowed for launch and load")
    args = parser.parse_args()

    header = "| run | " + " | ".join(title for _, title, _ in COLUMNS) + " |"
    print(header)
    print("|" + "---|" * (len(COLUMNS) + 1))

    pooled = {pack: [] for pack in args.packs}
    for round_number in range(1, args.rounds + 1):
        for pack in args.packs:
            samples = sample(args, pack)
            pooled[pack] += samples
            print(row(f"{pack} #{round_number}", summarise(samples)), flush=True)

    for pack in args.packs:
        print(row(f"**{pack}**", summarise(pooled[pack])))


if __name__ == "__main__":
    main()
