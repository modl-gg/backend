#!/bin/bash
set -e

APP_NAME="modl-backend"
BLUE_PORT=8080
GREEN_PORT=8081
NGINX_UPSTREAM_CONF="/etc/nginx/conf.d/modl-backend-upstream.conf"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

get_current_color() {
    local container=$(docker ps --filter "name=${APP_NAME}" --format "{{.Names}}" | head -1)
    if [[ "$container" == *"-blue" ]]; then
        echo "blue"
    elif [[ "$container" == *"-green" ]]; then
        echo "green"
    else
        echo "none"
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

update_nginx() {
    local active_port=$1
    log "Updating nginx to point to port $active_port..."
    echo "upstream modl_backend { server 127.0.0.1:${active_port}; keepalive 32; }" | sudo tee $NGINX_UPSTREAM_CONF > /dev/null
    sudo nginx -t && sudo systemctl reload nginx
    log "Nginx updated and reloaded"
}

CURRENT_COLOR=$(get_current_color)

if [[ "$CURRENT_COLOR" == "blue" ]]; then
    ROLLBACK_COLOR="green"
elif [[ "$CURRENT_COLOR" == "green" ]]; then
    ROLLBACK_COLOR="blue"
else
    log "ERROR: No running container found"
    exit 1
fi

CURRENT_CONTAINER="${APP_NAME}-${CURRENT_COLOR}"
ROLLBACK_CONTAINER="${APP_NAME}-${ROLLBACK_COLOR}"
ROLLBACK_PORT=$(get_port_for_color $ROLLBACK_COLOR)

if ! docker image inspect ${APP_NAME}:${ROLLBACK_COLOR} &>/dev/null; then
    log "ERROR: No previous image found for rollback (${APP_NAME}:${ROLLBACK_COLOR})"
    exit 1
fi

log "Rolling back from $CURRENT_COLOR to $ROLLBACK_COLOR (port $ROLLBACK_PORT)"

log "Starting rollback container: $ROLLBACK_CONTAINER"
docker run -d \
    --name "$ROLLBACK_CONTAINER" \
    --restart unless-stopped \
    -p ${ROLLBACK_PORT}:8080 \
    --env-file .env \
    -e SPRING_PROFILES_ACTIVE=staging \
    ${APP_NAME}:${ROLLBACK_COLOR}

log "Waiting for rollback container to be healthy..."
sleep 30

if curl -sf "http://localhost:${ROLLBACK_PORT}/actuator/health" > /dev/null 2>&1; then
    log "Rollback container is healthy"
    update_nginx $ROLLBACK_PORT
    sleep 5
    log "Stopping current container: $CURRENT_CONTAINER"
    docker stop "$CURRENT_CONTAINER" 2>/dev/null || true
    docker rm "$CURRENT_CONTAINER" 2>/dev/null || true
    log "Rollback complete!"
else
    log "ERROR: Rollback container failed health check"
    docker stop "$ROLLBACK_CONTAINER" 2>/dev/null || true
    docker rm "$ROLLBACK_CONTAINER" 2>/dev/null || true
    exit 1
fi
