#!/usr/bin/env sh
set -eu

rm -rf bok.d bin
mkdir -p bin

cat > bin/dox <<'EOF'
#!/usr/bin/env sh
set -eu

command="${1-}"
shift || true

case "$command" in
  antora)
    mkdir -p antora.d
    printf '%s\n' "fake antora source" > antora.d/fake-antora.txt
    ;;
  site)
    mkdir -p doxsite.d/ja doxsite.d/en doxsite.d/architecture doxsite.d/metadata/dashboard
    printf '%s\n' "stale ja output" > doxsite.d/ja/index.html
    printf '%s\n' "stale en output" > doxsite.d/en/index.html
    printf '%s\n' "architecture output" > doxsite.d/architecture/index.html
    cat > doxsite.d/metadata/dashboard/site.json <<'JSON'
{
  "counts": {
    "category_count": 1,
    "article_count": 1,
    "glossary_term_count": 2,
    "total_item_count": 3
  },
  "rdf": {
    "resource_count": 3,
    "triple_count": 42,
    "subject_count": 12,
    "predicate_count": 9
  },
  "increments": {
    "scale": "day",
    "buckets": [
      {"label": "2026-06-04", "start_date": "2026-06-04", "end_date": "2026-06-04", "count": 1, "article_count": 1, "glossary_term_count": 0},
      {"label": "2026-06-05", "start_date": "2026-06-05", "end_date": "2026-06-05", "count": 2, "article_count": 0, "glossary_term_count": 2}
    ]
  },
  "categories": [
    {
      "name": "architecture",
      "title": "Architecture",
      "counts": {
        "category_count": 0,
        "article_count": 1,
        "glossary_term_count": 2,
        "total_item_count": 3
      },
      "increments": {
        "scale": "day",
        "buckets": [
          {"label": "2026-06-04", "start_date": "2026-06-04", "end_date": "2026-06-04", "count": 1, "article_count": 1, "glossary_term_count": 0},
          {"label": "2026-06-05", "start_date": "2026-06-05", "end_date": "2026-06-05", "count": 2, "article_count": 0, "glossary_term_count": 2}
        ]
      }
    }
  ]
}
JSON
    ;;
  site-mark)
    mkdir -p target/fake-site-mark
    printf '%s\n' "$*" > target/fake-site-mark/args.txt
    ;;
  *)
    echo "Unsupported fake dox command: $command" >&2
    exit 1
    ;;
esac
EOF
chmod +x bin/dox

cat > bin/docker <<'EOF'
#!/usr/bin/env sh
set -eu

mkdir -p website.d/_/css
mkdir -p website.d/history website.d/manual website.d/glossary/architecture
printf '%s\n' "/* fake antora css */" > website.d/_/css/site.css
printf '%s\n' "smartdox-generated-history-year" > website.d/history/2026.html
printf '%s\n' "smartdox-generated-manual" > website.d/manual/index.html
printf '%s\n' "smartdox-generated-glossary-term" > website.d/glossary/architecture/runtime-smoke.html
printf '%s\n' "smartdox-generated-glossary-cloud" > website.d/glossary/architecture/cloud.html
printf '%s\n' "$*" > website.d/fake-docker-args.txt
EOF
chmod +x bin/docker

export PATH="$PWD/bin:$PATH"
cozy_version="$(cat target/cozy-version.txt)"

sbt -Dcozy.version="$cozy_version" --batch \
  "runMain cozy.Cozy bok create --save bok.d --name RuntimeSmokeBoK --url https://example.org/bok --language ja" \
  "runMain cozy.Cozy bok create-category architecture --project bok.d --title Architecture --description Architecture-category --article overview:Overview:Runtime-smoke-article --term glossary/runtime-smoke:RuntimeSmoke:Runtime-smoke-term:らんたいむ --term glossary/cloud:Cloud:Cloud-term" \
  "runMain cozy.Cozy bok build bok.d --strategy wip --docker-image fake-antora:latest"

test -f bok.d/README.md
test -f bok.d/STRUCTURE.md
test -f bok.d/conf/cozy/config.yaml
test -f bok.d/src/main/doxsite/site.conf
test -f bok.d/src/main/doxsite/index.dox
test -f bok.d/src/main/doxsite/glossary/category.yaml
test ! -f bok.d/src/main/doxsite/glossary/index.dox
test -f bok.d/src/main/doxsite/history/category.yaml
test -f bok.d/src/main/doxsite/history/index.dox
test -f bok.d/src/main/doxsite/manual/local-rules.dox
test -f bok.d/src/main/doxsite/architecture/category.yaml
test -f bok.d/src/main/doxsite/architecture/index.dox
test -f bok.d/src/main/doxsite/architecture/overview.dox
test -f bok.d/src/main/doxsite/glossary/architecture/runtime-smoke.dox
test -f bok.d/src/main/doxsite/glossary/architecture/cloud.dox
grep 'reading=らんたいむ' bok.d/src/main/doxsite/glossary/architecture/runtime-smoke.dox
test -f bok.d/src/main/antora-ui/build/ui-bundle.zip
test -f bok.d/website.d/index.html
test -f bok.d/website.d/glossary/index.html
test -f bok.d/website.d/history/2026.html
test -f bok.d/website.d/manual/index.html
test -f bok.d/website.d/glossary/architecture/runtime-smoke.html
test -f bok.d/website.d/glossary/architecture/cloud.html
test -f bok.d/website.d/ja/glossary/index.html
test -f bok.d/website.d/en/glossary/index.html
test -f bok.d/website.d/architecture/index.html
test -f bok.d/website.d/_/css/site.css
test -f bok.d/website.d/fake-docker-args.txt

grep 'RuntimeSmokeBoK' bok.d/website.d/index.html
grep 'Architecture' bok.d/website.d/index.html
grep '<strong>42</strong><em>RDFトリプル</em>' bok.d/website.d/index.html
grep 'aria-label="BoK項目分布"' bok.d/website.d/index.html
grep 'data-chart="distribution-ratio"' bok.d/website.d/index.html
grep 'data-chart="cumulative-date"' bok.d/website.d/index.html
grep 'bok-cumulative-line' bok.d/website.d/index.html
grep 'bok-cumulative-line-articles' bok.d/website.d/index.html
grep 'bok-cumulative-line-terms' bok.d/website.d/index.html
grep 'bok-cumulative-marker-articles' bok.d/website.d/index.html
grep 'bok-cumulative-marker-terms' bok.d/website.d/index.html
grep '2026-06-04 - 2026-06-05' bok.d/website.d/index.html
grep 'glossary/index.html' bok.d/website.d/index.html
grep 'history/2026.html' bok.d/website.d/index.html
grep 'manual/index.html' bok.d/website.d/index.html
! grep 'href="glossary/architecture/runtime-smoke.html"' bok.d/website.d/glossary/index.html
grep '../ja/glossary/index.html' bok.d/website.d/glossary/index.html
grep '../en/glossary/index.html' bok.d/website.d/glossary/index.html
grep '日本語索引ページ' bok.d/website.d/glossary/index.html
grep '英語索引ページ' bok.d/website.d/glossary/index.html
grep '日本語用語索引' bok.d/website.d/ja/glossary/index.html
grep '英語用語索引' bok.d/website.d/en/glossary/index.html
grep 'id="recent-terms"' bok.d/website.d/glossary/index.html
grep 'bok-special-links' bok.d/website.d/glossary/index.html
grep 'smartdox-generated-history-year' bok.d/website.d/history/2026.html
grep 'BoKマニュアル' bok.d/website.d/manual/index.html
grep 'Local Rules' bok.d/website.d/manual/index.html
! grep 'Lexicon' bok.d/website.d/index.html
grep 'bok-dashboard-chart' bok.d/website.d/architecture/index.html
grep 'data-chart="cumulative-date"' bok.d/website.d/architecture/index.html
grep 'bok-cumulative-line-articles' bok.d/website.d/architecture/index.html
grep 'bok-cumulative-line-terms' bok.d/website.d/architecture/index.html
grep '../history/2026.html' bok.d/website.d/architecture/index.html
grep '../manual/index.html' bok.d/website.d/architecture/index.html
! grep 'Lexicon' bok.d/website.d/architecture/index.html
grep 'fake-antora:latest' bok.d/website.d/fake-docker-args.txt

test ! -d bok.d/doxsite.d/ja
test ! -d bok.d/doxsite.d/en
test ! -f bok.d/doxsite-cache-work-in-progress.d/stale.error_msg
