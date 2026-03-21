# ============================================================
# Stage 1: Build React app
# ============================================================
FROM node:20-alpine AS builder

WORKDIR /app

# Install dependencies first (cached layer)
COPY package.json package-lock.json* ./
RUN npm ci --frozen-lockfile

# Copy source and build
COPY . .
RUN npm run build

# ============================================================
# Stage 2: Serve with Nginx
# ============================================================
FROM nginx:alpine

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy our nginx config
COPY nginx.conf /etc/nginx/conf.d/

# Copy built assets
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
