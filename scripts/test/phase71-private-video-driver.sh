#!/bin/sh

set -eu

_usage() {
  printf 'usage: %s snapshot <cozy-root> <case> before|after [cache-isolation-003]\n' "$0" >&2
  printf '       %s verify <cozy-root> <case> <native-build-result-json> [cache-isolation-003]\n' "$0" >&2
  exit 2
}

_error() {
  printf 'error: %s\n' "$1" >&2
  exit 1
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
      _error "parent path component is not admitted: $path"
    fi
  done
  return 0
}

_normalize_known_system_tmp_alias() {
  path=$1
  case "$path" in
    /tmp|/tmp/*)
      systemtmp=$(CDPATH= cd -P /tmp 2>/dev/null && pwd -P) || _error "cannot resolve the system /tmp alias"
      [ "$systemtmp" = /private/tmp ] || _error "the system /tmp alias does not resolve to /private/tmp"
      printf '/private/tmp%s\n' "${path#/tmp}"
      ;;
    *)
      printf '%s\n' "$path"
      ;;
  esac
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
    [ -L "$candidate" ] && _error "symlink path component is not allowed: $candidate"
    prefix=$candidate/
  done
}

_canonical_directory() {
  argument=$1
  _reject_parent_components "$argument"
  lexical=$(_absolute_lexical_path "$argument")
  _reject_symlink_ancestors "$lexical"
  [ -d "$argument" ] || _error "directory does not exist: $argument"
  (CDPATH= cd -P "$argument" 2>/dev/null && pwd -P)
}

_require_direct_file() {
  path=$(_normalize_known_system_tmp_alias "$1")
  _reject_parent_components "$path"
  lexical=$(_absolute_lexical_path "$path")
  _reject_symlink_ancestors "$lexical"
  [ -f "$path" ] && [ ! -L "$path" ] || _error "required path must be a direct regular file: $path"
}

_private_root() {
  cozyroot=$1
  private=$cozyroot/target/phase71-private-driver/simplemodeling-org
  _reject_symlink_ancestors "$private"
  [ -d "$private" ] && [ ! -L "$private" ] || _error "private project is not a direct directory: $private"
  printf '%s\n' "$private"
}

_validate_case() {
  case "$1" in
    baseline|core|intermediate|storyboard|current|missing) ;;
    *) _error "unknown case: $1" ;;
  esac
}

_snapshot_directory() {
  cases=$1
  case_name=$2
  attempt=${3-}
  case "$attempt" in
    '')
      printf '%s\n' "$cases/$case_name"
      ;;
    cache-isolation-003)
      [ "$case_name" = baseline ] || _error "corrective attempt is only admitted for baseline: $attempt"
      printf '%s\n' "$cases/$case_name/cache-isolation-003"
      ;;
    *)
      _error "unknown corrective attempt: $attempt"
      ;;
  esac
}

_snapshot_file() {
  cozyroot=$1
  case_name=$2
  edge=$3
  private=$4
  attempt=${5-}
  cases=$cozyroot/target/phase71-private-driver/cases
  snapshotdirectory=$(_snapshot_directory "$cases" "$case_name" "$attempt")
  _reject_symlink_ancestors "$snapshotdirectory"
  find "$private" -type l -print -quit | grep . >/dev/null 2>&1 && _error "private project contains a symlink"
  mkdir -p "$snapshotdirectory"
  snapshot=$snapshotdirectory/$edge.tsv
  [ ! -e "$snapshot" ] && [ ! -L "$snapshot" ] || _error "refusing to overwrite snapshot: $snapshot"
  printf 'path\tsha256\tbytes\tfiletime\n' > "$snapshot"
  find "$private" -type f -print | LC_ALL=C sort | while IFS= read -r file; do
    relative=${file#"$private"/}
    case "$relative" in
      .git|.git/*|*/.git|*/.git/*|.codex-workflow|.codex-workflow/*|*/.codex-workflow|*/.codex-workflow/*) continue ;;
    esac
    digest=$(shasum -a 256 "$file" | awk '{print $1}')
    bytes=$(wc -c < "$file" | tr -d '[:space:]')
    filetime=$(stat -f '%.9Fm' "$file")
    printf '%s\t%s\t%s\t%s\n' "$relative" "$digest" "$bytes" "$filetime"
  done >> "$snapshot"
}

_snapshot_line() {
  snapshot=$1
  relative=$2
  awk -F '\t' -v wanted="$relative" '$1 == wanted { print; found = 1 } END { if (!found) exit 1 }' "$snapshot"
}

_snapshot_value() {
  snapshot=$1
  relative=$2
  column=$3
  _snapshot_line "$snapshot" "$relative" | awk -F '\t' -v index="$column" '{ print $index }'
}

_native_stdout() {
  result=$1
  stdoutfile=$(jq -r '.stdout_file // empty' "$result")
  if [ -n "$stdoutfile" ]; then
    stdoutfile=$(_normalize_known_system_tmp_alias "$stdoutfile")
    _require_direct_file "$stdoutfile"
    cat "$stdoutfile"
  else
    jq -r '.stdout // empty' "$result"
  fi
}

_require_native_result() {
  result=$1
  private=$2
  videofile=$private/src/main/media/development-process/ai-development-harness/video/ja/video.yaml
  expected=$(jq -n --arg videofile "$videofile" '["video","build",$videofile,"--mode","final","--check-tools"]')
  jq -e --argjson expected "$expected" '
    .schema == "cozy.exact-cli-command-result.v4" and
    .command_completion == "terminal" and
    .timed_out == false and
    .cli_kind == "cozy" and
    (.argv | type == "array") and
    ((.argv == $expected) or
      ((.argv | length) == (($expected | length) + 1) and
       (.argv[0] | type == "string") and
       ((.argv[0] | endswith("/cozy")) or (.argv[0] | endswith("/cozy.exe"))) and
       (.argv[1:] == $expected))) and
    (.exit_code | type == "number" and floor == .)
  ' "$result" >/dev/null || _error "native result is not the exact terminal Cozy final-build result"
}

_native_exit_code() {
  jq -r '.exit_code' "$1"
}

_require_successful_products() {
  private=$1
  videoroot=$private/src/main/media/development-process/ai-development-harness/video/ja
  output=$videoroot/build/ai-development-harness-ja.mp4
  manifest=$videoroot/target/cozy-video/final/manifest.json
  handoff=$videoroot/target/cozy-video/storyboard/dialogue/handoff.json
  part=$videoroot/build/parts/dialogue.mp4
  partmanifest=$videoroot/target/cozy-video/final/part-artifacts/dialogue.json
  for product in "$output" "$manifest" "$handoff" "$part" "$partmanifest"; do
    _require_direct_file "$product"
    [ -s "$product" ] || _error "successful product is empty: $product"
  done
  jq -e '
    (.ffprobe.format.duration | try tonumber catch 0) as $duration |
    .schema == "cozy.video.final.v2" and
    .status == "validated" and
    .mode == "final" and
    .output.path == "build/ai-development-harness-ja.mp4" and
    ($duration | isfinite and . > 0) and
    (.ffprobe.streams | type == "array" and any(.[]; .codec_type == "video"))
  ' "$manifest" >/dev/null || _error "native final manifest is not validated"
  jq -e '
    .schema == "cozy.video.storyboard-build-handoff.v2" and
    .status == "validated" and
    .partId == "dialogue" and
    .sourcePath == "storyboard.md" and
    .storyboardSection == "explanation" and
    .storyboard.schema == "cozy.video.storyboard.v1" and
    .storyboard.version == 1 and
    (.storyboard.scenes | type == "array" and length == 9) and
    ([.storyboard.scenes[] | .id] == ["opening","speed","business-wall","convergence","harness-definition","broad-concept","responsibility-boundary","technology-context","series-roadmap"]) and
    ([.storyboard.scenes[] | .order] == [1,2,3,4,5,6,7,8,9])
  ' "$handoff" >/dev/null || _error "native dialogue handoff is not the exact validated nine-scene handoff"
}

_require_inventory_unchanged() {
  before=$1
  after=$2
  diff -u "$before" "$after" >/dev/null && return 0
  return 1
}

_product_inventory_differences() {
  left=$1
  right=$2
  awk -F '\t' '$1 != "path" && ($1 ~ /^src\/main\/media\/development-process\/ai-development-harness\/video\/ja\/build\// || $1 ~ /^src\/main\/media\/development-process\/ai-development-harness\/video\/ja\/target\/cozy-video\//) && $1 !~ /\/target\/cozy-video\/staging\// { print }' "$left" |
  while IFS= read -r line; do
    relative=${line%%	*}
    rightline=$(_snapshot_line "$right" "$relative" 2>/dev/null || true)
    [ "$line" = "$rightline" ] || printf '%s\n' "$relative"
  done
}

_require_missing_products_unchanged() {
  before=$1
  after=$2
  for relative in \
    "src/main/media/development-process/ai-development-harness/video/ja/build/ai-development-harness-ja.mp4" \
    "src/main/media/development-process/ai-development-harness/video/ja/build/parts/dialogue.mp4" \
    "src/main/media/development-process/ai-development-harness/video/ja/target/cozy-video/final/manifest.json" \
    "src/main/media/development-process/ai-development-harness/video/ja/target/cozy-video/final/part-artifacts/dialogue.json" \
    "src/main/media/development-process/ai-development-harness/video/ja/target/cozy-video/storyboard/dialogue/handoff.json"; do
    _snapshot_line "$before" "$relative" >/dev/null || _error "missing case has no prior successful product: $relative"
    _snapshot_line "$after" "$relative" >/dev/null || _error "missing case removed a successful product: $relative"
    beforeline=$(_snapshot_line "$before" "$relative")
    afterline=$(_snapshot_line "$after" "$relative")
    [ "$beforeline" = "$afterline" ] || _error "missing case changed a successful product: $relative"
  done
  differences=$(_product_inventory_differences "$before" "$after"; _product_inventory_differences "$after" "$before")
  [ -z "$differences" ] || _error "missing case changed retained inventory outside transient staging"
}

_verify() {
  cozyroot=$(_canonical_directory "$1")
  case_name=$2
  result=$(_normalize_known_system_tmp_alias "$3")
  attempt=${4-}
  _validate_case "$case_name"
  private=$(_private_root "$cozyroot")
  _require_direct_file "$result"
  snapshotdirectory=$(_snapshot_directory "$cozyroot/target/phase71-private-driver/cases" "$case_name" "$attempt")
  _reject_symlink_ancestors "$snapshotdirectory"
  before=$snapshotdirectory/before.tsv
  after=$snapshotdirectory/after.tsv
  _require_direct_file "$before"
  _require_direct_file "$after"
  _require_native_result "$result" "$private"
  stdout=$(_native_stdout "$result")
  exitcode=$(_native_exit_code "$result")
  case "$case_name" in
    baseline|core|intermediate|storyboard)
      [ "$exitcode" -eq 0 ] || _error "$case_name native build did not succeed"
      printf '%s\n' "$stdout" | grep -Fx 'cache: miss' >/dev/null || _error "$case_name native output did not report cache: miss"
      _require_successful_products "$private"
      outputrelative=src/main/media/development-process/ai-development-harness/video/ja/build/ai-development-harness-ja.mp4
      beforeline=$(_snapshot_line "$before" "$outputrelative" 2>/dev/null || true)
      afterline=$(_snapshot_line "$after" "$outputrelative" 2>/dev/null || true)
      [ -n "$afterline" ] || _error "$case_name has no final MP4 in the after snapshot"
      [ -z "$beforeline" ] || [ "$beforeline" != "$afterline" ] || _error "$case_name did not change final output bytes or FileTime"
      ;;
    current)
      [ "$exitcode" -eq 0 ] || _error "current native build did not succeed"
      printf '%s\n' "$stdout" | grep -Fx 'cache: hit' >/dev/null || _error "current native output did not report cache: hit"
      _require_successful_products "$private"
      _require_inventory_unchanged "$before" "$after" || _error "current case changed the private inventory"
      ;;
    missing)
      [ "$exitcode" -ne 0 ] || _error "missing native build unexpectedly succeeded"
      _require_missing_products_unchanged "$before" "$after"
      ;;
  esac
  printf '%s: verified\n' "$case_name"
}

[ "$#" -ge 1 ] || _usage
case "$1" in
  snapshot)
    [ "$#" -eq 4 ] || [ "$#" -eq 5 ] || _usage
    cozyroot=$(_canonical_directory "$2")
    case_name=$3
    edge=$4
    _validate_case "$case_name"
    case "$edge" in before|after) ;; *) _error "snapshot edge must be before or after" ;; esac
    private=$(_private_root "$cozyroot")
    _snapshot_file "$cozyroot" "$case_name" "$edge" "$private" "${5-}"
    printf '%s %s snapshot: recorded\n' "$case_name" "$edge"
    ;;
  verify)
    [ "$#" -eq 4 ] || [ "$#" -eq 5 ] || _usage
    _verify "$2" "$3" "$4" "${5-}"
    ;;
  *) _usage ;;
esac
