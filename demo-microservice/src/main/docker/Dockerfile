# syntax=docker/dockerfile:1.7
# The demo microservice as a GraalVM native executable, for linux/amd64 and linux/arm64:
#   ./gradlew docker      (docker buildx build --platform linux/amd64,linux/arm64 -f src/main/docker/Dockerfile .)
# Image builder pattern: the native compiler runs inside the builder image, on the target architecture.
FROM ghcr.io/graalvm/native-image-community:25 AS builder
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew nativeCompile --no-daemon --console=plain -x test
# A linked static library marks the binary as needing x86-64-v3 (AVX2), but the code is built for any x86-64 CPU
# (-march=compatibility). Remove the mark, so the binary also starts on old CPUs and under Rosetta.
RUN objcopy --remove-section .note.gnu.property /src/build/native/nativeCompile/demo-microservice

FROM gcr.io/distroless/cc-debian12:nonroot
# The native binary links zlib, which the distroless image does not have.
COPY --from=builder /usr/lib64/libz.so.1 /usr/lib/libz.so.1
COPY --from=builder /src/build/native/nativeCompile/demo-microservice /app/demo-microservice
EXPOSE 8080 8443
ENTRYPOINT ["/app/demo-microservice"]
