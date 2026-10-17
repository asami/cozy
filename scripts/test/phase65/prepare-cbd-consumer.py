#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Prepare fresh task-private real CBD sources; never build or launch them."""

import argparse
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys


REQUIRED = (
    "build.sbt", "project.yaml", "README.md", "project/plugins.sbt",
    "project/ProjectYamlBuild.scala", "project/InternalModelExclusionBuild.scala",
    "src/main/cozy/textus-cbd-support.cml",
    "src/main/scala/org/simplemodeling/textus/cbdsupport/impl/ComponentFactory.scala",
    "examples/cbd-standalone-sar/subsystem-descriptor.yaml",
    "examples/cbd-sie-sar/fixtures/catalog/metadata/repository/car/index.json",
    "examples/cbd-sie-sar/fixtures/catalog/repository/catalog/car/textus-runtime.model-metadata.json",
)
PREFIXES = ("project/", "src/", "docs/spec/examples/",
            "examples/cbd-standalone-sar/", "examples/cbd-sie-sar/fixtures/")
EXCLUDED = {".git", "target", "cache", "caches", ".cache", "build", "dist",
            "node_modules", ".dart_tool", ".bloop", ".bsp", ".metals",
            ".ivy2", ".m2", ".coursier", "credentials", ".credentials",
            ".ssh", ".aws", ".gnupg", ".env"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def safe_path(raw):
    """Reject symlink ancestors before canonicalizing a supplied path."""
    path = Path(os.path.abspath(raw))
    for part in (path, *path.parents):
        require(not part.is_symlink(), f"Symlink path is not admitted: {part}")
    return path.resolve()


def regular(path):
    require(not path.is_symlink() and path.is_file(), f"Regular file required: {path}")
    for parent in path.parents:
        require(not parent.is_symlink(), f"Symlink ancestor: {parent}")


def git(source, *args):
    result = subprocess.run(["git", "-C", str(source), *args],
                            capture_output=True, timeout=30, check=True)
    return result.stdout.decode("utf-8")


def settings(cozy, launcher, workspace):
    quote = lambda value: json.dumps(str(value), ensure_ascii=False)
    return f'''
// Task-private PHASE-65/S65.4A settings; original declarations remain above.
root / cozyDelegateProjectDir := None
root / cozyDelegateCommand := Seq({quote(launcher)}, "--runtime", "0.3.3-SNAPSHOT", "--runtime-dev-dir", {quote(cozy)})

lazy val phase65WriteCncfClasspath = taskKey[File]("Export resolved PHASE-65 CNCF dependency JARs")
root / phase65WriteCncfClasspath := {{
  val entries = (root / Runtime / externalDependencyClasspath).value
  val jars = entries.map(_.data.getCanonicalFile)
  require(jars.nonEmpty && jars.forall(f => f.isFile && f.length > 0 && f.getName.endsWith(".jar")), "Nonempty regular dependency JARs required")
  val cncf = entries.filter(_.get(moduleID.key).exists(m => m.organization == "org.goldenport" && m.name == "goldenport-cncf_3"))
  require(cncf.size == 1 && cncf.head.get(moduleID.key).exists(_.revision == "0.5.3-SNAPSHOT"), "Exactly declared goldenport-cncf_3 0.5.3-SNAPSHOT required")
  require(jars.count(_.getName.startsWith("goldenport-cncf_3")) == 1, "Unrelated CNCF artifact prohibited")
  val copiedroot = (root / baseDirectory).value.getCanonicalFile.toPath
  require(jars.forall(f => !f.toPath.startsWith(copiedroot)), "Copied CBD classes/artifacts prohibited in runtime dependencies")
  val output = file({quote(workspace / 'cncf-runtime-classpath.txt')})
  IO.write(output, jars.map(_.getPath).mkString(java.io.File.pathSeparator) + "\\n", java.nio.charset.StandardCharsets.UTF_8)
  val log = streams.value.log
  jars.foreach(f => log.info("PHASE65 runtime dependency: " + f.getPath))
  output
}}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ("cozy-root", "cbd-source", "workspace", "cozy-executable"):
        parser.add_argument(f"--{flag}", required=True)
    args = parser.parse_args()
    try:
        cozy = safe_path(args.cozy_root)
        source = safe_path(args.cbd_source)
        workspace = safe_path(args.workspace)
        launcher = safe_path(args.cozy_executable)
        snapshot = safe_path(cozy / "target/phase65/cbd-runtime-source")
        require(cozy.is_dir() and source.is_dir(), "Existing repository roots required")
        regular(launcher)
        require(os.access(launcher, os.X_OK), "Executable Cozy launcher required")
        require(workspace == cozy / "target/phase65/s654", "Exact task workspace required")
        require(not workspace.exists() and not snapshot.exists(),
                "Fresh nonexistent workspace and source snapshot required; retain earlier evidence")
        require(Path(git(source, "rev-parse", "--show-toplevel").strip()).resolve() == source,
                "CBD source must be its repository root")
        head = git(source, "rev-parse", "HEAD").strip()
        dirty = git(source, "status", "--porcelain=v1", "--untracked-files=all")
        dirty_z = git(source, "status", "--porcelain=v1", "-z", "--untracked-files=all")
        tracked = git(source, "ls-files", "-z").split("\0")
        selected = []
        for name in tracked:
            if not name:
                continue
            path = PurePosixPath(name)
            require(not path.is_absolute() and ".." not in path.parts, "Escaping tracked path")
            if name not in ("build.sbt", "project.yaml", "README.md") and not name.startswith(PREFIXES):
                continue
            if any(part.lower() in EXCLUDED or "credential" in part.lower()
                   for part in path.parts):
                continue
            regular(source / name)
            selected.append(name)
        require(set(REQUIRED).issubset(selected), "Required tracked CBD build/source/fixture files missing")
        project = (source / "project.yaml").read_text(encoding="utf-8")
        require(re.search(r'^  id: [\"\']?CbdSupport[\"\']?\s*$', project, re.M),
                "Expected project identity CbdSupport")
        require(re.search(r'^  cozyVersion: "0\.3\.3-SNAPSHOT"\s*$', project, re.M),
                "Declared Cozy version changed")
        require('"org.goldenport::goldenport-cncf:0.5.3-SNAPSHOT"' in project,
                "Declared CNCF version changed")
        template = cozy / "examples/dart-cbd-consumer/consumer"
        template_files = ("pubspec.yaml", "bin/contract.dart")
        for name in template_files:
            regular(template / name)
        workspace.mkdir(parents=True)
        snapshot.mkdir()
        for name in sorted(selected):
            destination = snapshot / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source / name, destination)
        appended = settings(cozy, launcher, workspace)
        with (snapshot / "build.sbt").open("a", encoding="utf-8", newline="\n") as stream:
            stream.write(appended)
        for name in template_files:
            destination = workspace / "consumer" / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(template / name, destination)
        receipt = {
            "schema": "phase65.cbd.preparation.v1", "cozyRoot": str(cozy),
            "sourceRoot": str(source), "sourceHead": head,
            "sourcePorcelain": dirty, "sourcePorcelainNul": dirty_z,
            "sourceDirty": bool(dirty_z), "copiedPaths": sorted(selected),
            "sourceAttribution": "Actual working-tree tracked files, including observed source dirt; no immutability certificate",
            "workspace": str(workspace), "sourceCopy": str(snapshot),
            "buildFile": str(snapshot / "build.sbt"), "appendedSettings": appended,
            "cozyExecutable": str(launcher), "classpathTask": "phase65WriteCncfClasspath",
            "classpathOutput": str(workspace / "cncf-runtime-classpath.txt"),
            "carOutput": str(snapshot / "target/textus-cbd-support-0.1.0-SNAPSHOT.car"),
            "sdkOutput": str(workspace / "sdk"), "consumer": str(workspace / "consumer"),
            "executionStatus": "prepared only; no build, launch, analyzer or live call executed",
        }
        output = workspace / "preparation.json"
        output.write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"preparation": str(output), "sourceCopy": str(snapshot)}))
        return 0
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print(f"CBD preparation failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
