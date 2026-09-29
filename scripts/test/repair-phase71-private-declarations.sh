#!/bin/sh

set -eu

usage() {
  printf 'usage: %s <cozy-root> <private-destination>\n' "$0" >&2
  exit 2
}

_absolute_lexical_path() {
  path=$1
  case "$path" in
    /*) normalized= ;;
    *) normalized=$(pwd -L) ;;
  esac
  remaining=$path
  case "$remaining" in
    /*) remaining=${remaining#/} ;;
  esac
  while [ -n "$remaining" ]; do
    case "$remaining" in
      */*) part=${remaining%%/*}; remaining=${remaining#*/} ;;
      *) part=$remaining; remaining= ;;
    esac
    case "$part" in
      ''|.) ;;
      ..)
        case "$normalized" in
          /) ;;
          *) normalized=${normalized%/*}; [ -n "$normalized" ] || normalized=/ ;;
        esac
        ;;
      *)
        if [ "$normalized" = / ]; then
          normalized=/$part
        else
          normalized=$normalized/$part
        fi
        ;;
    esac
  done
  printf '%s\n' "$normalized"
}

_reject_parent_components() {
  path=$1
  remaining=$path
  case "$remaining" in
    /*) remaining=${remaining#/} ;;
  esac
  while [ -n "$remaining" ]; do
    case "$remaining" in
      */*) part=${remaining%%/*}; remaining=${remaining#*/} ;;
      *) part=$remaining; remaining= ;;
    esac
    if [ "$part" = .. ]; then
      printf 'error: parent path component is not admitted: %s\n' "$path" >&2
      return 1
    fi
  done
}

_reject_symlink_ancestors() {
  path=$1
  prefix=/
  remaining=${path#/}
  while [ -n "$remaining" ]; do
    case "$remaining" in
      */*) part=${remaining%%/*}; remaining=${remaining#*/} ;;
      *) part=$remaining; remaining= ;;
    esac
    [ -n "$part" ] || continue
    candidate=$prefix$part
    if [ -L "$candidate" ]; then
      printf 'error: symlink path component is not allowed: %s\n' "$candidate" >&2
      return 1
    fi
    prefix=$candidate/
  done
}

_canonical_directory() {
  argument=$1
  _reject_parent_components "$argument" || return 1
  lexical=$(_absolute_lexical_path "$argument")
  _reject_symlink_ancestors "$lexical" || return 1
  if [ ! -d "$argument" ]; then
    printf 'error: directory does not exist: %s\n' "$argument" >&2
    return 1
  fi
  (CDPATH= cd -P "$argument" 2>/dev/null && pwd -P)
}

_require_direct_file() {
  path=$1
  _reject_symlink_ancestors "$path" || return 1
  if [ ! -f "$path" ] || [ -L "$path" ]; then
    printf 'error: required path must be a direct regular file: %s\n' "$path" >&2
    return 1
  fi
}

_require_lf_terminated() {
  file=$1
  lastbyte=$(tail -c 1 "$file" | od -An -t x1 | tr -d '[:space:]')
  if [ "$lastbyte" != 0a ]; then
    printf 'error: source must be LF-terminated: %s\n' "$file" >&2
    return 1
  fi
}

_validate_descriptor() {
  file=$1
  awk '
    function _error(message) {
      printf "error: video.yaml %s\n", message > "/dev/stderr"
      invalid = 1
    }
    function _count_exact(value,    i, count) {
      count = 0
      for (i = 1; i <= NR; i++)
        if (lines[i] == value)
          count++
      return count
    }
    function _count_regex(value,    i, count) {
      count = 0
      for (i = 1; i <= NR; i++)
        if (lines[i] ~ value)
          count++
      return count
    }
    {
      if (index($0, "\r") > 0)
        _error("CR line endings are not supported")
      lines[NR] = $0
      if ($0 == "assets:")
        assetsroot++
      if ($0 == "parts:")
        partsroot++
      if ($0 == "  - id: dialogue")
        partitem++
      if ($0 ~ /^  - /)
        listitems++
      if (inassets && $0 !~ /^  / && $0 != "assets:")
        inassets = 0
      if ($0 == "assets:")
        inassets = 1
      else if (inassets && $0 ~ /^  [A-Za-z][A-Za-z0-9_-]*:$/)
        assetroles++
    }
    END {
      if (NR == 0)
        _error("descriptor is empty")
      else if (lines[NR] == "")
        _error("descriptor must use one LF-terminated record per line")
      if (assetsroot != 1 || partsroot != 1)
        _error("expected exactly one assets root and one parts root")
      if (partitem != 1 || listitems != 1)
        _error("expected exactly one video part")
      if (_count_exact("  - id: dialogue") != 1)
        _error("dialogue part id is missing or duplicated")
      if (_count_exact("    audioDir: build/audio/dialogue") != 1)
        _error("dialogue audioDir is missing or changed")
      if (_count_exact("    output: build/parts/dialogue.mp4") != 1)
        _error("dialogue output is missing or changed")
      if (_count_exact("  opening:") != 1 || _count_exact("  summary:") != 1 || _count_exact("  final-page:") != 1)
        _error("the three existing asset roles must each occur once")
      if (_count_exact("    path: assets/opening.svg") != 1 || _count_exact("    path: assets/summary.svg") != 1 || _count_exact("    path: assets/final-page.svg") != 1)
        _error("existing asset paths are missing or changed")
      assetkind = _count_exact("    kind: project-owned")
      assetrequired = _count_exact("    required: true")
      assetlicense = _count_exact("    license: LicenseRef-SimpleModeling-Org")
      assetprovenance = _count_exact("    provenance: article-media:ai-development-harness")
      if ((assetkind != 3 && assetkind != 4) || (assetrequired != 3 && assetrequired != 4) || (assetlicense != 3 && assetlicense != 4) || (assetprovenance != 3 && assetprovenance != 4))
        _error("asset metadata must be exactly the three old or four new direct blocks")
      if (_count_exact("  section-start: none") != 1)
        _error("visual-effects section-start must remain none")
      if (_count_regex("^    type: ") != 1 || _count_regex("^    script: ") > 1 || _count_regex("^    storyboard: ") > 1 || _count_regex("^    storyboardSection: ") > 1)
        _error("part source selectors are duplicated or structurally unsupported")
      if (_count_regex("^    script: ") == 1 && _count_exact("    script: script.json") != 1)
        _error("legacy script selector is not script.json")
      if (_count_regex("^    storyboard: ") == 1 && _count_exact("    storyboard: storyboard.md") != 1)
        _error("Storyboard selector is not storyboard.md")
      if (_count_regex("^    storyboardSection: ") == 1 && _count_exact("    storyboardSection: explanation") != 1)
        _error("Storyboard section selector is not explanation")
      oldstate = _count_exact("    type: dialogue") == 1 &&
        _count_exact("    script: script.json") == 1 &&
        _count_regex("^    storyboard: ") == 0 &&
        _count_regex("^    storyboardSection: ") == 0
      newstate = _count_exact("    type: storyboard") == 1 &&
        _count_regex("^    script: ") == 0 &&
        _count_exact("    storyboard: storyboard.md") == 1 &&
        _count_exact("    storyboardSection: explanation") == 1
      insertedblock = 0
      for (i = 1; i < NR; i++)
        if (lines[i] == "  section-start:" && lines[i + 1] == "    path: assets/section-start.svg" &&
            lines[i + 2] == "    kind: project-owned" && lines[i + 3] == "    required: true" &&
            lines[i + 4] == "    license: LicenseRef-SimpleModeling-Org" &&
            lines[i + 5] == "    provenance: article-media:ai-development-harness" && lines[i + 6] == "  summary:")
          insertedblock++
      if (assetroles == 3 && _count_exact("  section-start:") == 0 && insertedblock == 0 && oldstate &&
          assetkind == 3 && assetrequired == 3 && assetlicense == 3 && assetprovenance == 3) {
        print "OLD"
      } else if (assetroles == 4 && _count_exact("  section-start:") == 1 && insertedblock == 1 && newstate &&
                 _count_exact("    path: assets/section-start.svg") == 1 &&
                 assetkind == 4 && assetrequired == 4 && assetlicense == 4 && assetprovenance == 4) {
        print "NEW"
      } else if (!invalid) {
        _error("descriptor is neither the coherent old nor the coherent new declaration")
      }
      if (invalid)
        exit 1
    }
  ' "$file"
}

_validate_storyboard() {
  file=$1
  awk '
    function _error(message) {
      printf "error: storyboard.md %s\n", message > "/dev/stderr"
      invalid = 1
    }
    function _start_scene() {
      scene++
      if (scene > 9)
        _error("more than nine scenes are present")
      state = "id"
    }
    BEGIN {
      state = "header"
      expected[1] = "opening"
      expected[2] = "speed"
      expected[3] = "business-wall"
      expected[4] = "convergence"
      expected[5] = "harness-definition"
      expected[6] = "broad-concept"
      expected[7] = "responsibility-boundary"
      expected[8] = "technology-context"
      expected[9] = "series-roadmap"
      oldref[1] = "asset-refs: [\"opening\"]"
      oldref[5] = "asset-refs: [\"section-start\"]"
      oldref[6] = "asset-refs: [\"summary\"]"
      oldref[9] = "asset-refs: [\"final-page\"]"
      newref[1] = "asset-refs: [\"assets/opening.svg\"]"
      newref[5] = "asset-refs: [\"assets/section-start.svg\"]"
      newref[6] = "asset-refs: [\"assets/summary.svg\"]"
      newref[9] = "asset-refs: [\"assets/final-page.svg\"]"
    }
    {
      line = $0
      if (index(line, "\r") > 0)
        _error("CR line endings are not supported")
      if (state == "header") {
        if (line != "# Storyboard") _error("header must start with # Storyboard")
        state = "schema"
        next
      }
      if (state == "schema") {
        if (line != "schema: \"cozy.video.storyboard.v1\"") _error("schema must be cozy.video.storyboard.v1")
        state = "version"
        next
      }
      if (state == "version") {
        if (line != "version: 1") _error("version must be 1")
        state = "header-blank"
        next
      }
      if (state == "header-blank") {
        if (line != "") _error("header must be followed by one blank line")
        state = "scene-start"
        next
      }
      if (state == "scene-start") {
        if (line == "## scene") _start_scene()
        else _error("scene must start with ## scene")
        next
      }
      if (state == "id") {
        if (scene > 9 || line != "id: \"" expected[scene] "\"") _error("scene id/order is not the frozen nine-scene sequence")
        state = "order"
        next
      }
      if (state == "order") {
        if (line != "order: " scene) _error("scene order is not the frozen sequence")
        state = "section"
        next
      }
      if (state == "section") {
        if (line != "section: \"explanation\"") _error("scene section must be explanation")
        state = "speaker"
        next
      }
      if (state == "speaker") {
        if (line != "speaker: \"narrator\"") _error("scene speaker must be narrator")
        state = "role"
        next
      }
      if (state == "role") {
        if (line != "role: \"narration\"") _error("scene role must be narration")
        state = "narration-key"
        next
      }
      if (state == "narration-key") {
        if (line != "narration: |") _error("scene narration must use the restricted block form")
        state = "narration"
        next
      }
      if (state == "narration") {
        if (line == "screen:") state = "screen-heading"
        else if (line == "" || line ~ /^  /) next
        else _error("narration contains unsupported unindented structure")
        next
      }
      if (state == "screen-heading") {
        if (line !~ /^  heading: \".*\"$/) _error("screen heading is not the restricted quoted form")
        state = "screen-content-key"
        next
      }
      if (state == "screen-content-key") {
        if (line != "  content: |") _error("screen content must use the restricted block form")
        state = "screen-content"
        next
      }
      if (state == "screen-content") {
        if (line ~ /^caption: \".*\"$/) state = "duration"
        else if (line == "" || line ~ /^    /) next
        else _error("screen content contains unsupported structure")
        next
      }
      if (state == "duration") {
        if (line !~ /^duration: [0-9]+(\.[0-9]+)?s$/) _error("duration is not the restricted seconds form")
        state = "lead-silence"
        next
      }
      if (state == "lead-silence") {
        if (line !~ /^lead-silence: [0-9]+(\.[0-9]+)?s$/) _error("lead-silence is not the restricted seconds form")
        state = "transition"
        next
      }
      if (state == "transition") {
        if (line !~ /^transition: \"[A-Za-z0-9_-]+\"$/) _error("transition is unsupported")
        state = "production-inserts"
        next
      }
      if (state == "production-inserts") {
        if (line !~ /^production-inserts: \[.*\]$/) _error("production-inserts is not one restricted inline value")
        state = "diagram-refs"
        next
      }
      if (state == "diagram-refs") {
        if (line !~ /^diagram-refs: \[.*\]$/) _error("diagram-refs is not one restricted inline value")
        state = "asset-refs"
        next
      }
      if (state == "asset-refs") {
        if (scene == 1 || scene == 5 || scene == 6 || scene == 9) {
          if (line == oldref[scene]) oldroles++
          else if (line == newref[scene]) newroles++
          else _error("asset role reference is not one of the frozen old/new values")
        } else if (line != "asset-refs: []") {
          _error("unselected scene must have an empty asset reference list")
        } else {
          emptyrefs++
        }
        state = "pronunciation-notes"
        next
      }
      if (state == "pronunciation-notes") {
        if (line !~ /^pronunciation-notes: \[.*\]$/) _error("pronunciation-notes is not one restricted inline value")
        state = "direction-key"
        next
      }
      if (state == "direction-key") {
        if (line != "direction: |") _error("direction must use the restricted block form")
        state = "direction"
        next
      }
      if (state == "direction") {
        if (line == "## scene") {
          if (scene >= 9) _error("more than nine scenes are present")
          else _start_scene()
        } else if (line == "" || line ~ /^  /) {
          next
        } else {
          _error("direction contains unsupported unindented structure")
        }
        next
      }
    }
    END {
      if (state != "direction")
        _error("Storyboard ended before the final direction block")
      if (scene != 9)
        _error("Storyboard must contain exactly nine scenes")
      if (oldroles == 4 && newroles == 0 && emptyrefs == 5)
        print "OLD"
      else if (newroles == 4 && oldroles == 0 && emptyrefs == 5)
        print "NEW"
      else if (!invalid)
        _error("Storyboard has mixed, missing, duplicate, or unsupported asset references")
      if (invalid)
        exit 1
    }
  ' "$file"
}

_transform_descriptor() {
  source=$1
  destination=$2
  awk '
    {
      if ($0 == "  summary:" && !inserted) {
        print "  section-start:"
        print "    path: assets/section-start.svg"
        print "    kind: project-owned"
        print "    required: true"
        print "    license: LicenseRef-SimpleModeling-Org"
        print "    provenance: article-media:ai-development-harness"
        inserted = 1
      }
      if ($0 == "    type: dialogue")
        print "    type: storyboard"
      else if ($0 == "    script: script.json") {
        print "    storyboard: storyboard.md"
        print "    storyboardSection: explanation"
      } else
        print $0
    }
    END {
      if (!inserted)
        exit 1
    }
  ' "$source" > "$destination"
}

_transform_storyboard() {
  source=$1
  destination=$2
  awk '
    {
      if ($0 == "asset-refs: [\"opening\"]")
        print "asset-refs: [\"assets/opening.svg\"]"
      else if ($0 == "asset-refs: [\"section-start\"]")
        print "asset-refs: [\"assets/section-start.svg\"]"
      else if ($0 == "asset-refs: [\"summary\"]")
        print "asset-refs: [\"assets/summary.svg\"]"
      else if ($0 == "asset-refs: [\"final-page\"]")
        print "asset-refs: [\"assets/final-page.svg\"]"
      else
        print $0
    }
  ' "$source" > "$destination"
}

_retain_install_failure() {
  reason=$1
  printf 'error: private declaration installation failed: %s\n' "$reason" >&2
  printf 'recovery backups retained in: %s\n' "$tempdir" >&2
  printf 'descriptor backup: %s\n' "$descriptor_backup" >&2
  printf 'Storyboard backup: %s\n' "$storyboard_backup" >&2
  exit 1
}

_restore_backup() {
  backup=$1
  target=$2
  cp -p "$backup" "$target"
}

[ "$#" -eq 2 ] || usage

cozy_root=$(_canonical_directory "$1") || exit 1
_reject_parent_components "$2" || exit 1
destination=$(_absolute_lexical_path "$2")
_reject_symlink_ancestors "$destination" || exit 1
expected_destination=$cozy_root/target/phase71-private-driver/simplemodeling-org
if [ "$destination" != "$expected_destination" ]; then
  printf 'error: private destination must be %s\n' "$expected_destination" >&2
  exit 1
fi
if [ ! -d "$destination" ] || [ -L "$destination" ]; then
  printf 'error: private destination must be an existing direct directory: %s\n' "$destination" >&2
  exit 1
fi

videoroot=$destination/src/main/media/development-process/ai-development-harness/video/ja
video=$videoroot/video.yaml
storyboard=$videoroot/storyboard.md
_reject_symlink_ancestors "$videoroot" || exit 1
_require_direct_file "$video" || exit 1
_require_direct_file "$storyboard" || exit 1
_require_lf_terminated "$video" || exit 1
_require_lf_terminated "$storyboard" || exit 1

for asset in opening section-start summary final-page; do
  assetpath=$videoroot/assets/$asset.svg
  _require_direct_file "$assetpath" || exit 1
done

descriptor_state=$(_validate_descriptor "$video") || exit 1
storyboard_state=$(_validate_storyboard "$storyboard") || exit 1
if [ "$descriptor_state" != "$storyboard_state" ]; then
  printf 'error: video.yaml and storyboard.md are not a coherent old or new pair\n' >&2
  exit 1
fi

if [ "$descriptor_state" = NEW ]; then
  printf 'private declarations already repaired; reused without write: %s\n' "$videoroot"
  printf 'mode/mtime unchanged; no backup/temp output or provider activation\n'
  exit 0
fi

if ! tempdir=$(mktemp -d "$destination/.phase71-private-declarations.XXXXXX"); then
  printf 'error: unable to allocate task-private repair staging directory\n' >&2
  exit 1
fi
descriptor_backup=$tempdir/video.yaml.original
storyboard_backup=$tempdir/storyboard.md.original
descriptor_work=$tempdir/video.yaml.work
storyboard_work=$tempdir/storyboard.md.work
descriptor_stage=$tempdir/video.yaml.staged
storyboard_stage=$tempdir/storyboard.md.staged

if ! cp -p "$video" "$descriptor_backup"; then
  printf 'error: unable to back up video.yaml; retained staging directory: %s\n' "$tempdir" >&2
  exit 1
fi
if ! cp -p "$storyboard" "$storyboard_backup"; then
  printf 'error: unable to back up storyboard.md; retained staging directory: %s\n' "$tempdir" >&2
  exit 1
fi
if ! _transform_descriptor "$video" "$descriptor_work"; then
  _retain_install_failure "descriptor transform failed"
fi
if ! _transform_storyboard "$storyboard" "$storyboard_work"; then
  _retain_install_failure "Storyboard transform failed"
fi

descriptor_mode=$(/usr/bin/stat -f '%Lp' "$video")
storyboard_mode=$(/usr/bin/stat -f '%Lp' "$storyboard")
if ! chmod "$descriptor_mode" "$descriptor_work" || ! chmod "$storyboard_mode" "$storyboard_work"; then
  _retain_install_failure "unable to preserve source file modes in staged files"
fi
if ! mv "$descriptor_work" "$descriptor_stage" || ! mv "$storyboard_work" "$storyboard_stage"; then
  _retain_install_failure "unable to seal staged files"
fi

if [ "$(_validate_descriptor "$descriptor_stage")" != NEW ]; then
  _retain_install_failure "staged video.yaml did not satisfy the new declaration contract"
fi
if [ "$(_validate_storyboard "$storyboard_stage")" != NEW ]; then
  _retain_install_failure "staged storyboard.md did not satisfy the new Storyboard contract"
fi
if cmp -s "$video" "$descriptor_stage" || cmp -s "$storyboard" "$storyboard_stage"; then
  _retain_install_failure "repair unexpectedly produced unchanged source bytes"
fi

if ! cmp -s "$video" "$descriptor_backup"; then
  _retain_install_failure "video.yaml changed after observation and before installation"
fi
if ! cmp -s "$storyboard" "$storyboard_backup"; then
  _retain_install_failure "storyboard.md changed after observation and before installation"
fi

if ! mv "$descriptor_stage" "$video"; then
  _retain_install_failure "atomic video.yaml rename failed"
fi
if ! cmp -s "$storyboard" "$storyboard_backup"; then
  if ! _restore_backup "$descriptor_backup" "$video"; then
    printf 'error: rollback failed for video.yaml; backup retained at %s\n' "$descriptor_backup" >&2
  fi
  _retain_install_failure "storyboard.md changed before its atomic rename"
fi
if ! mv "$storyboard_stage" "$storyboard"; then
  if ! _restore_backup "$descriptor_backup" "$video"; then
    printf 'error: rollback failed for video.yaml; backup retained at %s\n' "$descriptor_backup" >&2
  fi
  _retain_install_failure "atomic storyboard.md rename failed"
fi

cleanup_failed=0
rm -f "$descriptor_backup" "$storyboard_backup" "$descriptor_stage" "$storyboard_stage" || cleanup_failed=1
rmdir "$tempdir" 2>/dev/null || cleanup_failed=1
if [ "$cleanup_failed" -ne 0 ]; then
  printf 'error: both declarations were installed, but exact staging cleanup failed; retained path: %s\n' "$tempdir" >&2
  exit 1
fi

printf 'repaired private declarations atomically: %s\n' "$videoroot"
printf 'video.yaml and storyboard.md are now native Storyboard declarations; no provider or renderer invoked\n'
