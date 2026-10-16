#!/usr/bin/env bash
# Builds the open-source Chromium Android browser baseline.
# This is deliberately not yet Hari-branded; see CHROMIUM_PORT_PLAN.md.
set -Eeuo pipefail

if [[ "$(uname -s)" != "Linux" || "$(uname -m)" != "x86_64" ]]; then
  echo "ERROR: Chromium Android builds require an x86-64 Linux host." >&2
  exit 2
fi

: "${RUNNER_TEMP:=${TMPDIR:-/tmp}}"
: "${CHROMIUM_CHECKOUT_DIR:=${RUNNER_TEMP}/hari-chromium}"
: "${DEPOT_TOOLS_DIR:=${HOME}/depot_tools}"

available_kb="$(df -Pk "${RUNNER_TEMP}" | awk 'NR == 2 {print $4}')"
required_kb=$((120 * 1024 * 1024))
if [[ -z "${available_kb}" || "${available_kb}" -lt "${required_kb}" ]]; then
  echo "ERROR: Need at least 120 GiB free on ${RUNNER_TEMP}; current free space is ${available_kb:-unknown} KiB." >&2
  exit 2
fi

mem_total_kb="$(awk '/MemTotal:/ {print $2}' /proc/meminfo)"
minimum_kb=$((8 * 1024 * 1024))
recommended_kb=$((16 * 1024 * 1024))
if [[ -z "${mem_total_kb}" || "${mem_total_kb}" -lt "${minimum_kb}" ]]; then
  echo "ERROR: Chromium's Android build requires at least 8 GiB RAM." >&2
  exit 2
fi
if [[ "${mem_total_kb}" -lt "${recommended_kb}" ]]; then
  echo "WARNING: More than 16 GiB RAM is recommended; this build may be slow or fail." >&2
fi

command -v git >/dev/null || { echo "ERROR: git is required." >&2; exit 2; }
command -v python3 >/dev/null || { echo "ERROR: python3 is required." >&2; exit 2; }
command -v sudo >/dev/null || { echo "ERROR: sudo is required to install Chromium's Linux/Android build dependencies." >&2; exit 2; }

if [[ ! -x "${DEPOT_TOOLS_DIR}/fetch" ]]; then
  echo "Installing Chromium depot_tools in ${DEPOT_TOOLS_DIR}"
  git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git "${DEPOT_TOOLS_DIR}"
fi
export PATH="${DEPOT_TOOLS_DIR}:${PATH}"

mkdir -p "${CHROMIUM_CHECKOUT_DIR}"
if [[ ! -d "${CHROMIUM_CHECKOUT_DIR}/src/.git" ]]; then
  if [[ -e "${CHROMIUM_CHECKOUT_DIR}/src" ]]; then
    echo "ERROR: ${CHROMIUM_CHECKOUT_DIR}/src exists but is not a Chromium Git checkout." >&2
    exit 2
  fi
  cd "${CHROMIUM_CHECKOUT_DIR}"
  fetch --nohooks --no-history android
fi

cd "${CHROMIUM_CHECKOUT_DIR}/src"
sudo ./build/install-build-deps-android.sh
gclient runhooks

# Non-component ARM64 build; Chromium itself controls the Android SDK/NDK versions.
gn gen out/HariAndroid --args='target_os="android" target_cpu="arm64" is_debug=false is_component_build=false'
autoninja -C out/HariAndroid chrome_public_apk

apk_dir="out/HariAndroid/apks"
if [[ ! -d "${apk_dir}" ]] || ! compgen -G "${apk_dir}/*.apk" >/dev/null; then
  echo "ERROR: Chromium build completed but no APK was found in ${apk_dir}." >&2
  exit 1
fi
echo "Chromium baseline APK(s):"
find "${apk_dir}" -maxdepth 1 -type f -name '*.apk' -printf '%p (%s bytes)\n'
echo "NOTE: this is an unmodified Chromium baseline, not the finished Hari Browser."
