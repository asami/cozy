# Cozy Toolchain Docker Image

`ghcr.io/asami/cozy-toolchain:latest` is the standard Phase 8 Docker image for Cozy BoK, SmartDox PDF, and video workflows.

The canonical registry is GitHub Container Registry under the `asami` account:

```text
ghcr.io/asami/cozy-toolchain
```

Release tags use `YYYY.MM.DD`. If a same-day rebuild is required, use
`YYYY.MM.DD-N`. The `latest` tag must point only to the latest validated release
image.

Build:

```bash
docker build \
  -t ghcr.io/asami/cozy-toolchain:2026.06.19 \
  -t ghcr.io/asami/cozy-toolchain:latest \
  docker/cozy-toolchain
```

Validate the installed dependency sets:

```bash
docker run --rm ghcr.io/asami/cozy-toolchain:2026.06.19 cozy-toolchain check all
docker run --rm ghcr.io/asami/cozy-toolchain:latest cozy-toolchain check all
docker run --rm ghcr.io/asami/cozy-toolchain:latest cozy-toolchain check kroki
docker run --rm ghcr.io/asami/cozy-toolchain:latest cozy-toolchain check bok
docker run --rm ghcr.io/asami/cozy-toolchain:latest cozy-toolchain check pdf
docker run --rm ghcr.io/asami/cozy-toolchain:latest cozy-toolchain check video
```

Release:

```bash
docker login ghcr.io
docker push ghcr.io/asami/cozy-toolchain:2026.06.19
docker push ghcr.io/asami/cozy-toolchain:latest
```

Verify the pushed image:

```bash
docker pull ghcr.io/asami/cozy-toolchain:2026.06.19
docker run --rm ghcr.io/asami/cozy-toolchain:2026.06.19 cozy-toolchain check all
docker image inspect ghcr.io/asami/cozy-toolchain:2026.06.19
```

Phase 8 release:

```text
tag: ghcr.io/asami/cozy-toolchain:2026.06.19
latest: ghcr.io/asami/cozy-toolchain:latest
digest: sha256:5948924af5a8c6ac56fbf39931acbb958ad7a5ca078f3a9b7f22700e4d8f5057
```

The image is based on the SmartDox `smartdox-pdf` dependency image line and keeps its Kroki command/server behavior:

```bash
docker run --rm -p 9609:8000 ghcr.io/asami/cozy-toolchain:latest kroki-server
```

When Cozy runs Antora through this image, the wrapper starts the bundled Kroki
server inside the container and serves it on `localhost:9609`, matching the
SmartDox Antora playbook compatibility setting. Direct SmartDox usage can still
use the existing `simplemodeling/smartdox-pdf:latest` Docker image or an
explicit Kroki server.

The wrapper passes through unknown commands, so tools remain directly runnable:

```bash
docker run --rm ghcr.io/asami/cozy-toolchain:latest antora --version
docker run --rm ghcr.io/asami/cozy-toolchain:latest ffmpeg -version
docker run --rm ghcr.io/asami/cozy-toolchain:latest python3 -c 'import PIL; print(PIL.__version__)'
```

VOICEVOX Engine is not included. Cozy checks and uses VOICEVOX as an external HTTP service.

The v1 image includes whisper.cpp and the standard model at:

```text
/opt/cozy/models/ggml-base.bin
```
