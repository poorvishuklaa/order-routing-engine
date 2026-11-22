# Build stage: VITE_API_URL is the backend's public URL, baked in at build time
FROM node:22-alpine AS build
WORKDIR /app
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Serve stage: nginx serves the static files and falls back to index.html for client routes
FROM nginx:1.27-alpine
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV PORT=80
