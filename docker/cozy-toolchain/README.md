# Cozy Toolchain Docker Image

`simplemodeling/cozy-toolchain:latest` is the standard Phase 8 Docker image for Cozy BoK, SmartDox PDF, and video workflows.

Build:

```bash
docker build -t simplemodeling/cozy-toolchain:latest docker/cozy-toolchain
```

Validate the installed dependency sets:

```bash
docker run --rm simplemodeling/cozy-toolchain:latest cozy-toolchain check all
docker run --rm simplemodeling/cozy-toolchain:latest cozy-toolchain check bok
docker run --rm simplemodeling/cozy-toolchain:latest cozy-toolchain check pdf
docker run --rm simplemodeling/cozy-toolchain:latest cozy-toolchain check video
```

The image is based on the SmartDox `smartdox-pdf` dependency image line and keeps its Kroki command/server behavior:

```bash
docker run --rm -p 9609:8000 simplemodeling/cozy-toolchain:latest kroki-server
```

The wrapper passes through unknown commands, so tools remain directly runnable:

```bash
docker run --rm simplemodeling/cozy-toolchain:latest antora --version
docker run --rm simplemodeling/cozy-toolchain:latest ffmpeg -version
docker run --rm simplemodeling/cozy-toolchain:latest python3 -c 'import PIL; print(PIL.__version__)'
```

VOICEVOX Engine is not included. Cozy checks and uses VOICEVOX as an external HTTP service.

The v1 image includes whisper.cpp and the standard model at:

```text
/opt/cozy/models/ggml-base.bin
```
