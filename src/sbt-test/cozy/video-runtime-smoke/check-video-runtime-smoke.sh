#!/usr/bin/env sh
set -eu

work_dir="target/video-runtime-smoke"
rm -rf "$work_dir"
mkdir -p "$work_dir"
cozy_version="$(cat target/cozy-version.txt)"

cat > "$work_dir/intro-script.json" <<'JSON'
{
  "title": "Runtime Smoke Script",
  "scenes": [
    {"id": "title", "duration": 1.0, "line": "Runtime smoke title"},
    {"id": "body", "targetDuration": 2.0, "subscenes": [
      {"id": "detail", "duration": 1.0, "line": "Runtime smoke detail"},
      {"id": "summary", "targetDuration": 1.5, "line": "Runtime smoke summary"}
    ]}
  ]
}
JSON

cat > "$work_dir/video_project.json" <<'JSON'
{
  "name": "video-runtime-smoke",
  "title": "Video Runtime Smoke",
  "renderer": {"engine": "simple-java2d"},
  "tools": {
    "toolMode": "docker",
    "dockerImage": "simplemodeling/cozy-toolchain:latest",
    "voicevoxUrl": "http://127.0.0.1:50021"
  },
  "parts": [
    {"id": "intro", "type": "dialogue", "script": "intro-script.json"},
    {"id": "web", "type": "web-demo", "script": "missing-web-script.json", "steps": "missing-steps.json"},
    {"id": "unknown", "type": "unknown-kind", "script": "intro-script.json"}
  ]
}
JSON

printf 'fake-demo-video' > "$work_dir/demo.mp4"
cat > "$work_dir/events.json" <<'JSON'
{
  "viewport": {"width": 1280, "height": 720},
  "steps": [
    {"kind": "navigate", "url": "http://example.test/"},
    {"kind": "click", "selector": "#start"},
    {"kind": "input", "selector": "#name", "text": "smoke"}
  ]
}
JSON

mkdir -p "$work_dir/build/audio/intro" "$work_dir/build/parts" "$work_dir/build/record/web"
printf 'fake-audio-title' > "$work_dir/build/audio/intro/01-title.wav"
printf 'fake-audio-detail' > "$work_dir/build/audio/intro/02-detail.wav"
printf 'fake-audio-summary' > "$work_dir/build/audio/intro/03-summary.wav"
cat > "$work_dir/build/audio/intro/manifest.json" <<'JSON'
[
  {"sceneId":"title","speaker":"narrator","file":"01-title.wav","leadSilence":0.0,"audioDuration":0.2,"targetDuration":1.0,"tailSilence":0.8},
  {"sceneId":"detail","speaker":"narrator","file":"02-detail.wav","leadSilence":0.0,"audioDuration":0.2,"targetDuration":1.0,"tailSilence":0.8},
  {"sceneId":"summary","speaker":"narrator","file":"03-summary.wav","leadSilence":0.0,"audioDuration":0.2,"targetDuration":1.5,"tailSilence":1.3}
]
JSON

cat > "$work_dir/build/parts/intro.manifest.json" <<'JSON'
{
  "partId": "intro",
  "renderer": "simple-java2d",
  "outputPath": "build/parts/intro.mp4",
  "sceneCount": 3,
  "estimatedDuration": 3.5,
  "toolMode": "docker",
  "dockerImage": "simplemodeling/cozy-toolchain:latest"
}
JSON

cat > "$work_dir/build/manifest.json" <<'JSON'
{
  "projectFile": "video_project.json",
  "outputPath": "build/final.mp4",
  "partOutputs": ["build/parts/intro.mp4"],
  "toolMode": "docker",
  "dockerImage": "simplemodeling/cozy-toolchain:latest",
  "concatListPath": "target/cozy-video/ffmpeg/concat.txt",
  "ffprobe": {"format":{"duration":"3.5"},"streams":[]}
}
JSON

if ! sbt -Dcozy.version="$cozy_version" --batch \
  "runMain cozy.Cozy video inspect $work_dir/video_project.json" \
  "runMain cozy.Cozy video inspect $work_dir/video_project.json --check-tools" \
  "runMain cozy.Cozy video build $work_dir/video_project.json --dry-run" \
  "runMain cozy.Cozy video build $work_dir/video_project.json --dry-run --check-tools" \
  "runMain cozy.Cozy video demo-script $work_dir/demo.mp4 --save $work_dir/build/demo-script.json --events $work_dir/events.json" \
  "runMain cozy.Cozy video replay $work_dir/build/demo-script.json --dry-run" \
  "runMain cozy.Cozy video rdf $work_dir/video_project.json --save $work_dir/rdf" > "$work_dir/video-runtime-smoke.log" 2>&1; then
  tail -200 "$work_dir/video-runtime-smoke.log" >&2
  exit 1
fi

grep 'Cozy Video Inspect' $work_dir/video-runtime-smoke.log
grep 'name: video-runtime-smoke' $work_dir/video-runtime-smoke.log
grep 'part\[1\]: intro' $work_dir/video-runtime-smoke.log
grep 'expandedScenes: 3' $work_dir/video-runtime-smoke.log
grep 'part\[2\]: web' $work_dir/video-runtime-smoke.log
grep 'scriptStatus: missing' $work_dir/video-runtime-smoke.log
grep 'stepsStatus: missing' $work_dir/video-runtime-smoke.log
grep 'part\[3\]: unknown' $work_dir/video-runtime-smoke.log
grep 'type: unknown-kind (unsupported)' $work_dir/video-runtime-smoke.log
grep 'artifacts:' $work_dir/video-runtime-smoke.log

grep 'tool checks:' $work_dir/video-runtime-smoke.log
grep 'docker-toolchain:' $work_dir/video-runtime-smoke.log
grep 'docker-image:' $work_dir/video-runtime-smoke.log
grep 'cozy-toolchain-image:' $work_dir/video-runtime-smoke.log
grep 'voicevox:' $work_dir/video-runtime-smoke.log

grep 'Cozy Video Build Dry-Run' $work_dir/video-runtime-smoke.log
grep 'commands:' $work_dir/video-runtime-smoke.log
grep 'part.intro.parse-script' $work_dir/video-runtime-smoke.log
grep 'project.concat' $work_dir/video-runtime-smoke.log
grep 'docker run --rm' $work_dir/video-runtime-smoke.log

grep 'Cozy Video Demo Script' $work_dir/video-runtime-smoke.log
grep 'scriptFile:' $work_dir/video-runtime-smoke.log
grep 'steps: 3' $work_dir/video-runtime-smoke.log
test -f $work_dir/build/demo-script.json
grep 'cozy.video.replay-script.v1' "$work_dir/build/demo-script.json"

grep 'Cozy Video Replay Dry-Run' $work_dir/video-runtime-smoke.log
grep 'replay.playwright' $work_dir/video-runtime-smoke.log

grep 'Cozy Video RDF' $work_dir/video-runtime-smoke.log
test -f $work_dir/rdf/video.ttl
test -f $work_dir/rdf/video.jsonld
test -f $work_dir/rdf/manifest.json
grep 'cozy-video:VideoProject' "$work_dir/rdf/video.ttl"
grep 'cozy-video:VideoPart' "$work_dir/rdf/video.ttl"
grep 'cozy-video:VideoArtifact' "$work_dir/rdf/video.ttl"
grep 'cozy-video:VideoReplay' "$work_dir/rdf/video.ttl"
grep 'cozy-video:VideoReplayStep' "$work_dir/rdf/video.ttl"
grep 'cozy-video:artifactKind "audio-manifest"' "$work_dir/rdf/video.ttl"
grep '"tripleCount"' "$work_dir/rdf/manifest.json"
grep '"resourceCount"' "$work_dir/rdf/manifest.json"

echo VIDEO_RUNTIME_SMOKE_OK
