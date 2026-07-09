#!/bin/bash
set -euo pipefail

REPO_DIR=${MODL_DEPLOY_REPO_DIR:-/home/modl/backend}
RUN_DIR=${MODL_DEPLOY_RUN_DIR:-$HOME/.modl-deploy-runs}

run_log_path() { echo "$RUN_DIR/deploy-$1.log"; }
run_status_path() { echo "$RUN_DIR/deploy-$1.status"; }

cmd_start() {
    local environment=$1
    local run_id=$2
    local log_file status_file
    log_file=$(run_log_path "$run_id")
    status_file=$(run_status_path "$run_id")

    mkdir -p "$RUN_DIR"
    find "$RUN_DIR" -maxdepth 1 -type f -name 'deploy-*' -mtime +14 -delete 2>/dev/null || true
    : > "$log_file"
    rm -f "$status_file"

    setsid bash -c '
        cd "$1" && ./scripts/deploy.sh "$2"
        echo $? > "$3"
    ' _ "$REPO_DIR" "$environment" "$status_file" >> "$log_file" 2>&1 < /dev/null &

    echo "Started $environment deployment run $run_id"
    echo "Log: $log_file"
}

cmd_status() {
    local run_id=$1
    local offset=${2:-0}
    local log_file status_file size
    log_file=$(run_log_path "$run_id")
    status_file=$(run_status_path "$run_id")

    size=0
    [[ -f "$log_file" ]] && size=$(stat -c%s "$log_file")

    if [[ "$size" -gt "$offset" ]]; then
        { tail -c "+$((offset + 1))" "$log_file" | head -c "$((size - offset))"; } || true
        echo
    fi
    echo "__MODL_OFFSET__:${size}"
    if [[ -f "$status_file" ]]; then
        echo "__MODL_EXIT__:$(cat "$status_file")"
    fi
}

case "${1:-}" in
    start)
        cmd_start "${2:?environment required}" "${3:?run id required}"
        ;;
    status)
        cmd_status "${2:?run id required}" "${3:-0}"
        ;;
    *)
        echo "usage: $0 {start <environment> <run-id>|status <run-id> [byte-offset]}" >&2
        exit 2
        ;;
esac
