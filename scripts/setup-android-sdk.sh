#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
CMDLINE_TOOLS_VERSION="11076708"
BUILD_TOOLS="35.0.0"
PLATFORM="android-35"

echo "Instalando Android SDK em: $SDK_ROOT"

mkdir -p "$SDK_ROOT/cmdline-tools"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Baixando Android command-line tools..."
  curl -fsSL \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
    -o "$TMP_DIR/cmdtools.zip"
  unzip -q "$TMP_DIR/cmdtools.zip" -d "$TMP_DIR"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mkdir -p "$SDK_ROOT/cmdline-tools/latest"
  cp -r "$TMP_DIR/cmdline-tools/." "$SDK_ROOT/cmdline-tools/latest/"
fi

export ANDROID_HOME="$SDK_ROOT"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"

echo "Aceitando licenças do SDK..."
yes | sdkmanager --licenses >/dev/null

echo "Instalando pacotes necessários..."
sdkmanager "platform-tools" "platforms;${PLATFORM}" "build-tools;${BUILD_TOOLS}"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
echo "sdk.dir=$SDK_ROOT" > "$PROJECT_ROOT/local.properties"

echo ""
echo "SDK configurado com sucesso."
echo "local.properties criado em: $PROJECT_ROOT/local.properties"
echo ""
echo "Próximo passo:"
echo "  cd \"$PROJECT_ROOT\" && ./gradlew assembleDebug"
