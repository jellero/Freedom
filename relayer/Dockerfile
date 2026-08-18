FROM node:24-alpine

WORKDIR /app
RUN corepack enable && corepack prepare pnpm@11.19.0 --activate
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile --prod
COPY src ./src

ENV NODE_ENV=production
EXPOSE 8787
USER node
CMD ["node", "src/server.mjs"]
