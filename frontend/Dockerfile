# Frontend image: Node builds the production bundle, nginx serves it and forwards /api/* to the services
# (the production twin of proxy.conf.json), so the browser talks to one origin and no CORS is needed.

# ---- build stage ----
FROM node:24-alpine AS build
WORKDIR /app
# dependencies first: this layer is reused until package*.json changes
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
# the generated API client is committed (src/app/api), so no Java or running backend is needed here
COPY . .
RUN npx ng build

# ---- runtime stage ----
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/claims-platform/browser /usr/share/nginx/html
EXPOSE 80
