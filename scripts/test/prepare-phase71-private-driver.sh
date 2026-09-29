#!/bin/sh

set -eu

usage() {
  printf 'usage: %s [--repair-times|--repair-context] <cozy-root> <simplemodeling-root> <private-destination>\n' "$0" >&2
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
  lexical=$(_absolute_lexical_path "$argument")
  _reject_symlink_ancestors "$lexical"
  if [ ! -d "$argument" ]; then
    printf 'error: directory does not exist: %s\n' "$argument" >&2
    return 1
  fi
  (CDPATH= cd -P "$argument" 2>/dev/null && pwd -P)
}

_repair_mtimes() {
  source_root=$1
  destination_root=$2
  find "$destination_root" -type f -exec /bin/sh -c '
    source_root=$1
    destination_root=$2
    shift 2
    for destination_file do
      relative=${destination_file#"$destination_root"/}
      source_file=$source_root/$relative
      if [ ! -f "$source_file" ]; then
        printf "error: source file does not exist: %s\n" "$source_file" >&2
        exit 1
      fi
      /usr/bin/touch -m -r "$source_file" "$destination_file" || exit 1
    done
  ' /bin/sh "$source_root" "$destination_root" {} +
}

_repair_context_files() {
  source_root=$1
  destination_root=$2

  context_config_source=$source_root/conf/cozy/config.yaml
  context_profiles_source=$source_root/conf/cozy/video/credit-profiles/simplemodeling-org.yaml
  context_config_destination=$destination_root/conf/cozy/config.yaml
  context_profiles_destination=$destination_root/conf/cozy/video/credit-profiles/simplemodeling-org.yaml

  for context_source in "$context_config_source" "$context_profiles_source"; do
    _reject_symlink_ancestors "$context_source"
    if [ ! -f "$context_source" ] || [ -L "$context_source" ]; then
      printf 'error: context source must be an ordinary regular file: %s\n' "$context_source" >&2
      return 1
    fi
  done

  for context_destination in "$context_config_destination" "$context_profiles_destination"; do
    _reject_symlink_ancestors "$context_destination"
    if [ -e "$context_destination" ] || [ -L "$context_destination" ]; then
      if [ ! -f "$context_destination" ] || [ -L "$context_destination" ]; then
        printf 'error: context destination must be an ordinary regular file: %s\n' "$context_destination" >&2
        return 1
      fi
      case "$context_destination" in
        "$context_config_destination") context_source=$context_config_source ;;
        "$context_profiles_destination") context_source=$context_profiles_source ;;
      esac
      if ! cmp -s "$context_source" "$context_destination"; then
        printf 'error: refusing to overwrite differing context destination: %s\n' "$context_destination" >&2
        return 1
      fi
    fi
  done

  if [ ! -e "$context_config_destination" ] && [ ! -L "$context_config_destination" ]; then
    mkdir -p "$(dirname "$context_config_destination")"
    cp -p "$context_config_source" "$context_config_destination"
    /usr/bin/touch -m -r "$context_config_source" "$context_config_destination"
  fi
  if [ ! -e "$context_profiles_destination" ] && [ ! -L "$context_profiles_destination" ]; then
    mkdir -p "$(dirname "$context_profiles_destination")"
    cp -p "$context_profiles_source" "$context_profiles_destination"
    /usr/bin/touch -m -r "$context_profiles_source" "$context_profiles_destination"
  fi
}

mode=create
case "${1-}" in
  --repair-times) mode=repair_times; shift ;;
  --repair-context) mode=repair_context; shift ;;
esac
[ "$#" -eq 3 ] || usage

cozy_root=$(_canonical_directory "$1")
simplemodeling_root=$(_canonical_directory "$2")
destination=$(_absolute_lexical_path "$3")

expected_destination=$cozy_root/target/phase71-private-driver/simplemodeling-org
_reject_symlink_ancestors "$destination"
if [ "$destination" != "$expected_destination" ]; then
  printf 'error: private destination must be %s\n' "$expected_destination" >&2
  exit 1
fi
case "$mode" in
  repair_times|repair_context)
    if [ ! -d "$destination" ] || [ -L "$destination" ]; then
      printf 'error: repair destination must be an existing directory: %s\n' "$destination" >&2
      exit 1
    fi
    ;;
  create)
    if [ -e "$destination" ] || [ -L "$destination" ]; then
      printf 'error: private destination already exists; refusing to overwrite: %s\n' "$destination" >&2
      exit 1
    fi
    ;;
esac

dox_source=$simplemodeling_root/src/main/doxsite/development-process/ai-development-harness.dox
media_source=$simplemodeling_root/src/main/media/development-process/ai-development-harness
_reject_symlink_ancestors "$dox_source"
_reject_symlink_ancestors "$media_source"
if [ ! -d "$dox_source" ]; then
  printf 'error: selected Document Project source does not exist: %s\n' "$dox_source" >&2
  exit 1
fi
if [ ! -d "$media_source" ]; then
  printf 'error: selected media source does not exist: %s\n' "$media_source" >&2
  exit 1
fi

dox_destination=$destination/src/main/doxsite/development-process/ai-development-harness.dox
media_destination=$destination/src/main/media/development-process/ai-development-harness
case "$mode" in
  repair_times|repair_context)
    if [ ! -d "$dox_destination" ] || [ -L "$dox_destination" ]; then
      printf 'error: repair Document Project destination must exist: %s\n' "$dox_destination" >&2
      exit 1
    fi
    if [ ! -d "$media_destination" ] || [ -L "$media_destination" ]; then
      printf 'error: repair media destination must exist: %s\n' "$media_destination" >&2
      exit 1
    fi
    ;;
esac

case "$mode" in
  repair_times)
    _repair_mtimes "$dox_source" "$dox_destination"
    _repair_mtimes "$media_source" "$media_destination"
    printf 'repaired private Document Project mtimes: %s\n' "$dox_destination"
    printf 'repaired private media project mtimes: %s\n' "$media_destination"
    ;;
  repair_context)
    _repair_context_files "$simplemodeling_root" "$destination"
    printf 'repaired private project context: %s\n' "$destination"
    ;;
  create)
    mkdir -p "$destination"
    mkdir -p "$(dirname "$dox_destination")" "$(dirname "$media_destination")"

    rsync -a \
      --exclude='target/' \
      --exclude='.git/' \
      --exclude='.codex-workflow/' \
      "$dox_source/" "$dox_destination/"
    rsync -a \
      --exclude='build/' \
      --exclude='target/' \
      --exclude='.git/' \
      "$media_source/" "$media_destination/"

    _repair_context_files "$simplemodeling_root" "$destination"
    _repair_mtimes "$dox_source" "$dox_destination"
    _repair_mtimes "$media_source" "$media_destination"
    printf 'prepared private Document Project: %s\n' "$dox_destination"
    printf 'prepared private media project: %s\n' "$media_destination"
    ;;
esac
