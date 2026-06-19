#!/usr/bin/env sh
set -eu

rm -rf bok.d bin
mkdir -p bin
cozy_version="$(cat target/cozy-version.txt)"
mkdir -p target
if ! sbt -Dcozy.version="$cozy_version" --batch "runMain cozy.Cozy help" > target/cozy-help.log 2>&1; then
  cat target/cozy-help.log >&2
  exit 1
fi
if ! grep 'bok publish-video' target/cozy-help.log >/dev/null 2>&1; then
  case "$cozy_version" in
    *-SNAPSHOT)
      echo "cozy.version=$cozy_version must expose bok publish-video." >&2
      exit 1
      ;;
    *)
      echo "Skipping bok-video-publication-smoke for released cozy.version=$cozy_version; this release does not expose bok publish-video yet."
      exit 0
      ;;
  esac
fi

cat > bin/python3 <<'PY'
#!/usr/bin/env sh
set -eu
for arg in "$@"; do
  case "$arg" in
    *render_frame.py)
      dir="$(dirname "$arg")"
      mkdir -p "$dir"
      printf 'fake-frame' > "$dir/frame.png"
      exit 0
      ;;
  esac
done
exit 0
PY
chmod +x bin/python3

cat > bin/ffmpeg <<'FFMPEG'
#!/usr/bin/env sh
set -eu
out=""
for arg in "$@"; do
  out="$arg"
done
mkdir -p "$(dirname "$out")"
printf 'fake-mp4' > "$out"
FFMPEG
chmod +x bin/ffmpeg

cat > bin/ffprobe <<'FFPROBE'
#!/usr/bin/env sh
set -eu
printf '%s\n' '{"format":{"duration":"1.000"},"streams":[]}'
FFPROBE
chmod +x bin/ffprobe

cat > bin/dox <<'DOX'
#!/usr/bin/env sh
set -eu
command="${1-}"
shift || true
case "$command" in
  antora)
    mkdir -p antora.d
    printf 'fake antora\n' > antora.d/fake.txt
    ;;
  site)
    mkdir -p doxsite.d/metadata/dashboard
    printf '<site ttl>\n' > doxsite.d/site.ttl
    cat > doxsite.d/metadata/dashboard/site.json <<'JSON'
{"counts":{"category_count":1,"article_count":1,"glossary_term_count":0,"total_item_count":1},"rdf":{"resource_count":1,"triple_count":1,"subject_count":1,"predicate_count":1},"increments":{"scale":"day","buckets":[]},"categories":[]}
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
DOX
chmod +x bin/dox

cat > bin/docker <<'DOCKER'
#!/usr/bin/env sh
set -eu
mkdir -p website.d/_/css website.d/concepts
printf 'fake css\n' > website.d/_/css/site.css
printf 'fake article with video\n' > website.d/concepts/tutorial.html
DOCKER
chmod +x bin/docker

export PATH="$PWD/bin:$PATH"

sbt -Dcozy.version="$cozy_version" --batch \
  "runMain cozy.Cozy bok create --save bok.d --name VideoBoK --url https://example.org/bok --language ja"

mkdir -p bok.d/src/main/doxsite/concepts/tutorial.video bok.d/etc bok.d/.cozy
cat > bok.d/src/main/doxsite/concepts/tutorial.video/index.dox <<'DOXARTICLE'
Tutorial
========

This is a video tutorial article.
DOXARTICLE
cat > bok.d/src/main/doxsite/concepts/tutorial.video/script.json <<'SCRIPT'
{
  "title": "Tutorial Script",
  "scenes": [
    {"id": "intro", "silent": true, "duration": 0.1}
  ]
}
SCRIPT
cat > bok.d/src/main/doxsite/concepts/tutorial.video/video.yaml <<'VIDEO'
video:
  name: tutorial
title: Tutorial Video
version: 0.1.0
toolMode: host
renderer: simple-java2d
publish:
  module: textus
  publicPath: videos/tutorial.mp4
VIDEO
cat > bok.d/.cozy/config.yaml <<'CONFIG'
bok:
  docker-image: fake-antora:latest
  warehouse: warehouse
  publication: src/main/publication
  workflow:
    upload:
      command: "etc/upload.sh"
CONFIG
cat > bok.d/etc/upload.sh <<'UPLOAD'
#!/usr/bin/env sh
set -eu
mkdir -p target
printf 'uploaded\n' > target/uploaded.txt
UPLOAD
chmod +x bok.d/etc/upload.sh

sbt -Dcozy.version="$cozy_version" --batch \
  "runMain cozy.Cozy bok publish-video bok.d --force" \
  "runMain cozy.Cozy bok publish bok.d --force --strategy production"

test -f bok.d/src/main/publication/tutorial.json
test -f bok.d/warehouse/repository/video/textus/0.1.0/tutorial-0.1.0.mp4
test -f bok.d/warehouse/repository/video/textus/0.1.0/tutorial-0.1.0.ttl
test -f bok.d/warehouse/repository/video/textus/0.1.0/tutorial-0.1.0.jsonld
test -f bok.d/warehouse/repository/video/textus/0.1.0/tutorial-0.1.0.rdf-manifest.json
test -f bok.d/warehouse/repository/video/textus/0.1.0/tutorial-0.1.0.manifest.json
test -f bok.d/website.d/index.html
test -f bok.d/target/uploaded.txt

grep 'metadata/video/tutorial/0.1.0/rdf.json' bok.d/src/main/publication/tutorial.json
grep 'repository/video/textus/0.1.0/tutorial-0.1.0.mp4' bok.d/src/main/publication/tutorial.json
grep 'repository/video/textus/0.1.0/tutorial-0.1.0.ttl' bok.d/src/main/publication/tutorial.json
! find bok.d/src/main/doxsite/concepts/tutorial.video -name '*.mp4' -o -name '*.ttl' -o -name '*.jsonld' -o -name '*.srt' | grep .
