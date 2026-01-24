#!/bin/bash
set -e

ENVIRONMENT=${1:-staging}
IMAGE_TAG=${2:-}  # Optional: pre-built image tag from CI
APP_NAME="modl-backend"
BLUE_PORT=8080
GREEN_PORT=8081
MAX_HEALTH_RETRIES=30
HEALTH_RETRY_INTERVAL=2
NGINX_UPSTREAM_CONF="/etc/nginx/conf.d/modl-backend-upstream.conf"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

get_current_container() {
    docker ps --filter "name=${APP_NAME}" --format "{{.Names}}" | head -1
}

get_current_color() {
    local container=$(get_current_container)
    if [[ "$container" == *"-blue" ]]; then
        echo "blue"
    elif [[ "$container" == *"-green" ]]; then
        echo "green"
    else
        echo "none"
    fi
}

get_next_color() {
    local current=$(get_current_color)
    if [[ "$current" == "blue" ]]; then
        echo "green"
    else
        echo "blue"
    fi
}

get_port_for_color() {
    local color=$1
    if [[ "$color" == "blue" ]]; then
        echo $BLUE_PORT
    else
        echo $GREEN_PORT
    fi
}

wait_for_health() {
    local port=$1
    local retries=0

    log "Waiting for container on port $port to be healthy..."

    while [[ $retries -lt $MAX_HEALTH_RETRIES ]]; do
        if curl -sf "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
            log "Container on port $port is healthy"
            return 0
        fi
        retries=$((retries + 1))
        log "Health check attempt $retries/$MAX_HEALTH_RETRIES..."
        sleep $HEALTH_RETRY_INTERVAL
    done

    log "ERROR: Container on port $port failed health checks"
    return 1
}

update_nginx() {
    local active_port=$1
    log "Updating nginx to point to port $active_port..."

    echo "upstream modl_backend { server 127.0.0.1:${active_port}; keepalive 32; }" | sudo tee $NGINX_UPSTREAM_CONF > /dev/null

    sudo nginx -t && sudo systemctl reload nginx
    log "Nginx updated and reloaded"
}

log "Starting zero-downtime deployment for $ENVIRONMENT environment"

CURRENT_COLOR=$(get_current_color)
NEXT_COLOR=$(get_next_color)
CURRENT_CONTAINER="${APP_NAME}-${CURRENT_COLOR}"
NEW_CONTAINER="${APP_NAME}-${NEXT_COLOR}"
NEW_PORT=$(get_port_for_color $NEXT_COLOR)

log "Current: $CURRENT_COLOR, Deploying: $NEXT_COLOR (port $NEW_PORT)"

# Determine which image to use
if [[ -n "$IMAGE_TAG" ]]; then
    # Use pre-built image from CI
    DEPLOY_IMAGE="$IMAGE_TAG"
    log "Using pre-built image: $DEPLOY_IMAGE"
else
    # Build locally
    DEPLOY_IMAGE="${APP_NAME}:${NEXT_COLOR}"
    log "Building new Docker image..."
    docker build -t ${DEPLOY_IMAGE} .
fi

log "Stopping and removing existing $NEXT_COLOR container if exists..."
docker stop "$NEW_CONTAINER" 2>/dev/null || true
docker rm "$NEW_CONTAINER" 2>/dev/null || true

log "Starting new container: $NEW_CONTAINER on port $NEW_PORT"
docker run -d \
    --name "$NEW_CONTAINER" \
    --restart unless-stopped \
    -p ${NEW_PORT}:8080 \
    --env-file .env \
    -e SPRING_PROFILES_ACTIVE=${ENVIRONMENT} \
    ${DEPLOY_IMAGE}

if ! wait_for_health "$NEW_PORT"; then
    log "Rolling back: stopping failed container"
    docker logs "$NEW_CONTAINER" --tail 50
    docker stop "$NEW_CONTAINER" 2>/dev/null || true
    docker rm "$NEW_CONTAINER" 2>/dev/null || true
    exit 1
fi

log "Switching nginx to new container..."
update_nginx $NEW_PORT

if [[ "$CURRENT_COLOR" != "none" ]]; then
    log "Stopping old container: $CURRENT_CONTAINER"
    sleep 5  # Allow in-flight requests to complete
    docker stop "$CURRENT_CONTAINER" 2>/dev/null || true
    docker rm "$CURRENT_CONTAINER" 2>/dev/null || true
fi

log "Cleaning up old images..."
docker image prune -f

log "Deployment complete! Active: $NEW_CONTAINER on port $NEW_PORT"
docker ps --filter "name=${APP_NAME}"
