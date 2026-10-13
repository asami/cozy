#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Test-only comparison of retained JA/EN lossless PCM WAV with AAC candidates.

Parent selects inputs/FFmpeg, inspects this helper and owns human listening.
Invoke through managed uv; this script never makes a hearing judgment.

@since   Oct. 12, 2026
@version Oct. 12, 2026
@author  ASAMI, Tomoharu
"""

import argparse
import datetime
import json
from pathlib import Path
import subprocess
import wave


def _source(path: Path, language: str) -> dict:
    with wave.open(str(path), "rb") as audio:
        if audio.getcomptype() != "NONE" or audio.getnframes() <= 0:
            raise ValueError(f"{language} input must be nonempty lossless PCM WAV")
        return {
            "language": language, "path": str(path), "bytes": path.stat().st_size,
            "frames": audio.getnframes(), "sample_rate": audio.getframerate(),
            "channels": audio.getnchannels(), "sample_width_bytes": audio.getsampwidth(),
            "duration_seconds": audio.getnframes() / audio.getframerate(),
        }


def _process(argv: list[str]) -> dict:
    try:
        completed = subprocess.run(argv, text=True, capture_output=True, check=False)
        return {"argv": argv, "exit": completed.returncode,
                "stdout": completed.stdout, "stderr": completed.stderr}
    except OSError as error:
        return {"argv": argv, "exit": None, "stdout": "", "stderr": str(error)}


def _duration(progress: str) -> float | None:
    duration = None
    for line in progress.splitlines():
        key, separator, value = line.partition("=")
        if separator and key == "out_time_us":
            try:
                duration = int(value) / 1_000_000
            except ValueError:
                pass
    return duration


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-ja", type=Path, required=True)
    parser.add_argument("--input-en", type=Path, required=True)
    parser.add_argument("--output-root", type=Path, required=True)
    parser.add_argument("--ffmpeg", type=Path, required=True, help="Explicit selected host executable")
    args = parser.parse_args()
    repository = Path(__file__).resolve().parents[2]
    target = repository / "target"
    outputroot = args.output_root.resolve()
    if outputroot == target or not outputroot.is_relative_to(target):
        parser.error("--output-root must be a task-private directory below this repository's target/")
    ffmpeg = args.ffmpeg.resolve()
    if not ffmpeg.is_file():
        parser.error("--ffmpeg must name the selected host executable file")
    try:
        sources = [_source(args.input_ja.resolve(), "ja"), _source(args.input_en.resolve(), "en")]
    except (OSError, EOFError, wave.Error, ValueError) as error:
        parser.error(str(error))
    outputroot.mkdir(parents=True, exist_ok=True)
    records = []
    for source in sources:
        for bitrate in ("320k", "96k", "128k", "192k"):
            output = outputroot / f"{source['language']}-{bitrate}.m4a"
            encodeargv = [
                str(ffmpeg), "-nostdin", "-hide_banner", "-y", "-i", source["path"],
                "-map", "0:a:0", "-vn", "-c:a", "aac", "-b:a", bitrate,
                "-ar", "48000", "-ac", "2", "-cutoff", "18000", str(output),
            ]
            encoded = _process(encodeargv)
            # Decode the complete retained result, including failed partial
            # outputs. Full decode is evidence of execution, never of hearing.
            decodeargv = [
                str(ffmpeg), "-nostdin", "-hide_banner", "-v", "error", "-xerror",
                "-i", str(output), "-map", "0:a:0", "-vn", "-progress", "pipe:1",
                "-nostats", "-f", "null", "-",
            ]
            decoded = _process(decodeargv)
            records.append({
                "source_language": source["language"], "source_path": source["path"],
                "source_duration_seconds": source["duration_seconds"], "bitrate": bitrate,
                "role": "baseline" if bitrate == "320k" else "candidate",
                "path": str(output), "bytes": output.stat().st_size if output.is_file() else None,
                "duration_seconds": _duration(decoded["stdout"]), "sample_rate": 48000,
                "channels": 2, "cutoff_hz": 18000, "encode": encoded, "full_decode": decoded,
                "listening": "pending-human-listening",
            })
    successful = all(record["encode"]["exit"] == 0 and record["full_decode"]["exit"] == 0 for record in records)
    report = {
        "schema": "cozy.video.quality-audio-comparison.v1",
        "recorded_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "output_root": str(outputroot), "ffmpeg": str(ffmpeg), "sources": sources,
        "records": records, "measurement_status": "completed" if successful else "failed",
        "listening": "pending-human-listening",
    }
    reportpath = outputroot / "comparison.json"
    reportpath.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(reportpath)
    return 0 if successful else 1


if __name__ == "__main__":
    raise SystemExit(main())
