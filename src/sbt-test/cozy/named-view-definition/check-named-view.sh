#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/08.a-view-definition-lab
out_dir="$script_dir/out.d"

rm -rf "$out_dir"
"$script_dir/cozy-delegate.sh" modeler-scala "$script_dir/src/main/cozy/test.dox" --save="$out_dir"

mkdir -p "$out_dir/car.d/meta" "$out_dir/entity.d"
cp "$sample_dir/car.d/meta/component-descriptor.yaml" "$out_dir/car.d/meta/component-descriptor.yaml"
cp "$sample_dir/entity.d/view-definition.yaml" "$out_dir/entity.d/view-definition.yaml"

run_command() {
  (
    cd "$out_dir"
    sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command $*"
  )
}

(
  cd "$out_dir"
  sbt --batch compile
)

help_out="$(run_command help named-view-sample.view.load-person-summary 2>&1)"
printf '%s\n' "$help_out" | grep 'named-view-sample.view.load-person-summary'

summary_load_out="$(run_command named-view-sample.view.load-person-summary --id tokyo-sales-entity-person-1742198400000-abcd1234 2>&1)"
printf '%s\n' "$summary_load_out" | grep 'name: Alice'
printf '%s\n' "$summary_load_out" | grep 'city: Tokyo'

summary_search_out="$(run_command named-view-sample.view.search-person-summary-record --city Tokyo 2>&1)"
printf '%s\n' "$summary_search_out" | grep 'name: Alice'
printf '%s\n' "$summary_search_out" | grep 'total_count: 1'

custom_query_out="$(run_command named-view-sample.view.search-person --view search_by_city --city Tokyo 2>&1)"
printf '%s\n' "$custom_query_out" | grep 'city: Tokyo'
printf '%s\n' "$custom_query_out" | grep 'total_count: 1'
if printf '%s\n' "$custom_query_out" | grep -E 'Any|Is\('; then
  echo "custom query output leaked typed condition internals" >&2
  exit 1
fi

detail_load_out="$(run_command named-view-sample.view.load-person-detail --id tokyo-sales-entity-person-1742198400000-abcd1234 2>&1)"
printf '%s\n' "$detail_load_out" | grep 'title: Reader'

meta_out="$(run_command named-view-sample.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'view_names:'
printf '%s\n' "$meta_out" | grep 'source_events:'

echo NAMED_VIEW_OK
