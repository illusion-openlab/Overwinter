#!/usr/bin/env bash
# 门禁 B 的锁内序列。用法：bash .spatialsdk/tools/verify-ui.sh <轮次>
#
# 为什么不是固定 sleep：第 3 轮验证时用 `adb shell sleep 6` 拍到了两张空房间——
# 窗口还没起来。改成「轮询进程 + 轮询窗口稳定」，并给首次启动更长预算。
set -u
ROUND="${1:-x}"
WS="$(cd "$(dirname "$0")/../.." && pwd)"          # 项目根
ROOT="$(cd "$WS/.." && pwd)"                       # 工作区根
LOCK="$ROOT/.claude/skills/spatial-design-first-build/scripts/device-lock.sh"
OWNER="Overwinter-gateB"
D="${PICO_DEVICE:-emulator-5554}"
PKG=tech.illusion.overwinter
ACT="$PKG/.platform.LaunchActivity"
APK="$WS/app/build/outputs/apk/debug/app-debug.apk"
OUT="$WS/.spatialsdk/ui-verify"
export PICO_HOME="${PICO_HOME:-$HOME/Library/PICO/sdk}"
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
export PICO_LOCK_MAX_WAIT="${PICO_LOCK_MAX_WAIT:-120}"

[ -f "$APK" ] || { echo "没有 APK，先跑 ./gradlew assembleDebug"; exit 2; }
mkdir -p "$OUT"

bash "$LOCK" acquire "$OWNER" 180 "$D" >/dev/null 2>&1 || {
  echo "设备被占用，UI 验证已跳过。当前：$(bash "$LOCK" status "$D")"
  echo "稍后重跑：bash .spatialsdk/tools/verify-ui.sh $ROUND"
  exit 0
}
trap 'pico-cli app stop -d "$D" "$PKG" >/dev/null 2>&1; bash "$LOCK" release "$OWNER" "$D" >/dev/null 2>&1' EXIT INT TERM
T0=$SECONDS

pico-cli app install -d "$D" -r "$APK" >/dev/null 2>&1 || { echo "install 失败"; exit 1; }

shoot() {   # $1=状态名 $2=ow_debug 值（空=默认） $3=就位后额外等待秒数
  local name="$1" dbg="$2" settle="${3:-4}" i pid
  if [ -n "$dbg" ]; then
    adb -s "$D" shell am start -S -n "$ACT" --es ow_debug "$dbg" >/dev/null 2>&1
  else
    adb -s "$D" shell am start -S -n "$ACT" >/dev/null 2>&1
  fi
  # 轮询进程就位，最多 15s
  for i in $(seq 1 15); do
    pid="$(adb -s "$D" shell pidof "$PKG" 2>/dev/null | tr -d '\r')"
    [ -n "$pid" ] && break
    adb -s "$D" shell sleep 1
  done
  [ -z "${pid:-}" ] && { echo "$name: 进程始终没起来，跳过"; return 1; }
  adb -s "$D" shell sleep "$settle"           # 等窗口出现 + 素材解码 + 首帧
  pico-cli capture screenshot -d "$D" -o "$OUT/r$ROUND-$name.png" >/dev/null 2>&1
  pico-cli app stop -d "$D" "$PKG" >/dev/null 2>&1
  echo "$name ok (${SECONDS}s)"
}

shoot start ""     8      # 首次启动要解码 18 张 PNG，给足预算
shoot cold  cold   6
shoot over  over   6
shoot invin invin  6

# live 档走真实路径：停在开始页 -> 2.5s 自动开局 -> 自动扇翅。
# 一次启动拍两张，验证 NotStarted->Playing 和 Playing->GameOver 两次重组。
# cold/over 是首次组合前设好状态的，验不到这条链路。
if [ "${WITH_LIVE:-1}" = "1" ]; then
  adb -s "$D" shell am start -S -n "$ACT" --es ow_debug live >/dev/null 2>&1
  for i in $(seq 1 15); do
    pid="$(adb -s "$D" shell pidof "$PKG" 2>/dev/null | tr -d '\r')"
    [ -n "$pid" ] && break
    adb -s "$D" shell sleep 1
  done
  adb -s "$D" shell sleep 9
  pico-cli capture screenshot -d "$D" -o "$OUT/r$ROUND-live-playing.png" >/dev/null 2>&1
  adb -s "$D" shell sleep 14
  pico-cli capture screenshot -d "$D" -o "$OUT/r$ROUND-live-over.png" >/dev/null 2>&1
  pico-cli app stop -d "$D" "$PKG" >/dev/null 2>&1
  echo "live ok (${SECONDS}s)"
fi

bash "$LOCK" release "$OWNER" "$D" >/dev/null 2>&1
trap - EXIT INT TERM
echo "锁内 $((SECONDS-T0))s（目标 90s / 硬上限 120s）"
ls -1 "$OUT" | grep "^r$ROUND-"
