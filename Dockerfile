# syntax=docker/dockerfile:1
FROM node:22-bookworm-slim AS dependencies
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/api/package.json apps/api/package.json
COPY apps/web/package.json apps/web/package.json
COPY packages/ui/package.json packages/ui/package.json
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca; fi; \
    corepack enable && corepack prepare pnpm@9.15.9 --activate && pnpm install --frozen-lockfile

# Spring Boot API 빌드: Gradle Wrapper로 실행 JAR 생성, 의존성 캐시는 BuildKit 캐시에 보관
FROM eclipse-temurin:25-jdk AS api-build
WORKDIR /src/apps/api
# 사내 프록시 CA가 주어지면 JDK 신뢰 저장소에 등록해 Gradle 다운로드 허용
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then keytool -importcert -noprompt -cacerts -storepass changeit -alias proxy-ca -file /run/secrets/proxy_ca; fi
COPY apps/api/gradlew apps/api/build.gradle apps/api/settings.gradle ./
COPY apps/api/gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q dependencies > /dev/null
COPY apps/api/src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q bootJar

# API 실행 이미지: 업로드 볼륨 소유자와 같은 UID 1000으로 실행
FROM eclipse-temurin:25-jre AS api
WORKDIR /app
ENV HOST=0.0.0.0 PORT=4000
# 컨테이너 healthcheck용 curl, 업로드 사진을 크기별 WebP로 바꾸는 libvips(vips 명령)
# 변환기가 빠진 이미지는 변환 없이 원본만 저장하므로 빌드에서 실행 여부를 확인
RUN apt-get update && apt-get install -y --no-install-recommends curl libvips-tools && rm -rf /var/lib/apt/lists/* \
    && vips --version
COPY --from=api-build /src/apps/api/build/libs/damdam-api.jar ./damdam-api.jar
RUN mkdir /app/uploads && chown 1000:1000 /app/uploads
USER 1000:1000
EXPOSE 4000
# 인자 없이 서버 실행, `seed`·`optimize-images`는 일회성 명령
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/damdam-api.jar"]

FROM dependencies AS web-build
COPY tsconfig.base.json ./
COPY apps/web ./apps/web
COPY packages/ui ./packages/ui
ENV API_INTERNAL_URL=http://api:4000 NEXT_PUBLIC_API_BASE_URL=/api
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca; fi; \
    pnpm --filter @damdam/web build

FROM node:22-bookworm-slim AS web
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 HOSTNAME=0.0.0.0 PORT=3000
COPY --from=web-build --chown=node:node /app/apps/web/.next/standalone ./
COPY --from=web-build --chown=node:node /app/apps/web/.next/static ./apps/web/.next/static
COPY --from=web-build --chown=node:node /app/apps/web/public ./apps/web/public
USER node
EXPOSE 3000
CMD ["node", "apps/web/server.js"]
