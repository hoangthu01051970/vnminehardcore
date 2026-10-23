#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
server_dir="$script_dir/server"
paper_jar="$server_dir/paper.jar"
config_file="$script_dir/playit.env"
TEST_DIR="$script_dir"
PROJECT_DIR="$(dirname -- "$script_dir")"

if [[ ! -f "$config_file" ]]; then
    printf 'Missing Playit config: %s\n' "$config_file" >&2
    exit 1
fi

source "$config_file"

playitd="$script_dir/playit-linux-amd64"
playit_cli="$script_dir/playit-cli-linux-amd64"
playit_log="$script_dir/playit.log"

if [[ ! -s "$PLAYIT_SECRET_PATH" ]]; then
    printf 'Missing Playit secret file: %s\n' "$PLAYIT_SECRET_PATH" >&2
    printf 'Claim/setup the agent once, then keep this secret file for future starts.\n' >&2
    exit 1
fi

if [[ ! "$PLAYIT_LOCAL_PORT" =~ ^[0-9]{1,5}$ ]] || (( PLAYIT_LOCAL_PORT < 1 || PLAYIT_LOCAL_PORT > 65535 )); then
    printf 'Invalid local tunnel port: %s\n' "$PLAYIT_LOCAL_PORT" >&2
    exit 1
fi

if [[ ! -x "$playitd" || ! -x "$playit_cli" ]]; then
    printf 'Missing Playit daemon or CLI under %s\n' "$script_dir" >&2
    exit 1
fi

if [[ ! -f "$paper_jar" ]]; then
    printf 'Missing Paper server JAR. Place it at: %s\n' "$paper_jar" >&2
    exit 1
fi

plugin_found=false
for plugin_jar in "$server_dir/plugins"/VnMineHardcore*.jar; do
    if [[ -f "$plugin_jar" ]]; then
        plugin_found=true
        break
    fi
done

if [[ "$plugin_found" != true || ! -f "$server_dir/plugins/ProtocolLib.jar" ]]; then
    printf 'Missing VnMineHardcore JAR or ProtocolLib under %s/plugins\n' "$server_dir" >&2
    exit 1
fi

if [[ -f "$server_dir/server.properties" ]]; then
    server_port="$(sed -n 's/^server-port=//p' "$server_dir/server.properties" | tail -n 1)"
    if [[ -n "$server_port" && "$server_port" != "$PLAYIT_LOCAL_PORT" ]]; then
        printf 'Tunnel port %s does not match Paper server-port %s.\n' \
            "$PLAYIT_LOCAL_PORT" "$server_port" >&2
        exit 1
    fi
fi

if command -v ss >/dev/null 2>&1 && ss -H -ltn "sport = :$PLAYIT_LOCAL_PORT" | grep -q .; then
    printf 'Port %s is already in use. Stop the existing server before starting another.\n' \
        "$PLAYIT_LOCAL_PORT" >&2
    exit 1
fi

cd "$server_dir"
playit_status="$("$playit_cli" --socket-path "$PLAYIT_SOCKET_PATH" status 2>&1 || true)"
if [[ "$playit_status" != *"Phase: running"* ]]; then
    if [[ -S "$PLAYIT_SOCKET_PATH" ]]; then
        rm -f "$PLAYIT_SOCKET_PATH"
    fi
    nohup "$playitd" --socket-path "$PLAYIT_SOCKET_PATH" --secret-path "$PLAYIT_SECRET_PATH" \
        >>"$playit_log" 2>&1 </dev/null &
    for attempt in {1..20}; do
        playit_status="$("$playit_cli" --socket-path "$PLAYIT_SOCKET_PATH" status 2>&1 || true)"
        if [[ "$playit_status" == *"Phase: running"* ]]; then
            break
        fi
        sleep 0.2
    done
    if [[ "$playit_status" != *"Phase: running"* ]]; then
        printf 'Playit agent did not start. Check %s\n' "$playit_log" >&2
        exit 1
    fi
fi

printf 'Playit tunnel target: %s -> %s:%s\n' \
    "$PLAYIT_TUNNEL_HOST" "$PLAYIT_LOCAL_HOST" "$PLAYIT_LOCAL_PORT"
exec java -Xms1G -Xmx3G -jar "$paper_jar" --nogui