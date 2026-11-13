#!/usr/bin/env bash
# Starts a throwaway Blockseq server with made-up notes and makes it reachable
# from the phone on USB as http://localhost:3970 (adb reverse). Never touches
# real notes: it uses a new temporary data folder.
#
#   tools/demo-server.sh [path to the blockseq binary]
# Server password: demo-server   Encryption password: demo-secret
set -euo pipefail
BIN="${1:-$HOME/Projects/blockseq/target/debug/blockseq}"
PORT=3970
DIR="$(mktemp -d -t blockseq-demo-XXXXXX)"
(BLOCKSEQ_DATA="$DIR" BLOCKSEQ_ADDR="127.0.0.1:$PORT" BLOCKSEQ_PASSWORD=demo-server nohup "$BIN" >"$DIR/server.log" 2>&1 &)
for _ in $(seq 40); do curl -sf "http://127.0.0.1:$PORT/api/health" >/dev/null && break; sleep 0.25; done
deno run -A "$(dirname "$0")/seed.ts" "http://127.0.0.1:$PORT" demo-server demo-secret
adb reverse tcp:$PORT tcp:$PORT >/dev/null
echo "Demo server on http://localhost:$PORT (data: $DIR). Stop it: kill \$(ss -ltnp | grep ':$PORT ' | grep -o 'pid=[0-9]*' | cut -d= -f2)"
