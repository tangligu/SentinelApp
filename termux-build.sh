#!/data/data/com.termux/files/usr/bin/bash
#
# 哨兵 (Sentinel) APK 一键构建脚本 - 在 Termux 中运行
# 使用方法: bash termux-build.sh
#
# 注意：Termux 中需要先安装 termux-api 和 root-repo
# 建议先运行: pkg install x11-repo root-repo
#

set -e

echo "============================================"
echo "  哨兵 (Sentinel) APK 构建工具"
echo "============================================"
echo ""

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

if [ ! -d "/data/data/com.termux" ]; then
    echo -e "${RED}错误: 此脚本必须在 Termux 中运行${NC}"
    exit 1
fi

echo -e "${YELLOW}[1/6] 更新包管理器...${NC}"
pkg update -y -o Dpkg::Options::="--force-confnew" 2>/dev/null || pkg update -y

echo -e "${YELLOW}[2/6] 安装 JDK 17...${NC}"
pkg install -y openjdk-17 2>/dev/null || {
    echo "尝试从 x11-repo 安装..."
    pkg install -y x11-repo && pkg install -y openjdk-17
}

echo -e "${YELLOW}[3/6] 安装 Gradle + Android 构建工具...${NC}"
pkg install -y gradle aapt apksigner dx ecj 2>/dev/null || true

echo -e "${YELLOW}[4/6] 配置 Android SDK...${NC}"

# 创建 Android SDK 目录
ANDROID_SDK_ROOT="$HOME/android-sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"

if [ ! -d "$ANDROID_SDK_ROOT/platforms/android-34" ]; then
    echo "正在下载 Android SDK platform 34..."
    mkdir -p "$ANDROID_SDK_ROOT"
    
    # 下载 platforms
    cd /tmp
    PLATFORM_URL="https://dl.google.com/android/repository/platform-34_r03.zip"
    echo "下载 platform..."
    curl -L -o platform.zip "$PLATFORM_URL" 2>/dev/null || {
        echo "直接下载失败，尝试使用 sdkmanager..."
        # 下载 cmdline-tools
        curl -L -o cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" 2>/dev/null
        if [ -f "cmdline-tools.zip" ]; then
            unzip -q cmdline-tools.zip
            mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
            mv cmdline-tools "$ANDROID_SDK_ROOT/cmdline-tools/latest"
            export PATH="$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin"
            yes | sdkmanager "platforms;android-34" "build-tools;34.0.0" 2>&1 | tail -3
        fi
    }
    
    if [ -f "platform.zip" ]; then
        mkdir -p "$ANDROID_SDK_ROOT/platforms"
        unzip -q platform.zip -d "$ANDROID_SDK_ROOT/platforms/" 2>/dev/null
        mv "$ANDROID_SDK_ROOT/platforms/android-34"* "$ANDROID_SDK_ROOT/platforms/android-34" 2>/dev/null || true
        rm -f platform.zip
    fi
    
    # 下载 build-tools
    BUILD_TOOLS_URL="https://dl.google.com/android/repository/build-tools_r34-linux.zip"
    curl -L -o build-tools.zip "$BUILD_TOOLS_URL" 2>/dev/null
    if [ -f "build-tools.zip" ]; then
        mkdir -p "$ANDROID_SDK_ROOT/build-tools"
        unzip -q build-tools.zip -d "$ANDROID_SDK_ROOT/build-tools/" 2>/dev/null
        mv "$ANDROID_SDK_ROOT/build-tools/android-34" "$ANDROID_SDK_ROOT/build-tools/34.0.0" 2>/dev/null || true
        rm -f build-tools.zip
    fi
    
    rm -f /tmp/cmdline-tools*
fi

echo "Android SDK 路径: $ANDROID_SDK_ROOT"
ls -la "$ANDROID_SDK_ROOT/platforms/" 2>/dev/null || echo "platforms 未安装"
ls -la "$ANDROID_SDK_ROOT/build-tools/" 2>/dev/null || echo "build-tools 未安装"

echo -e "${YELLOW}[5/6] 配置项目...${NC}"
PROJECT_DIR="$HOME/SentinelApp"

if [ -d "$PROJECT_DIR" ]; then
    echo "项目已存在，更新中..."
    cd "$PROJECT_DIR"
    git pull 2>/dev/null || true
else
    # 从手机存储或 GitHub 获取项目
    if [ -f "/storage/emulated/0/哨兵/SentinelApp.zip" ]; then
        cp "/storage/emulated/0/哨兵/SentinelApp.zip" "$HOME/"
        cd "$HOME"
        unzip -o SentinelApp.zip -d SentinelApp
    elif command -v git &> /dev/null; then
        echo "从 GitHub 克隆..."
        git clone https://github.com/tangligu/SentinelApp.git "$PROJECT_DIR"
    else
        echo -e "${RED}未找到项目文件，请下载到 /storage/emulated/0/哨兵/SentinelApp.zip${NC}"
        exit 1
    fi
    cd "$PROJECT_DIR"
fi

# 创建 local.properties（如果 Gradle 需要）
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties

echo -e "${YELLOW}[6/6] 开始构建 APK...${NC}"
echo ""
echo -e "${GREEN}正在编译... 这可能需要几分钟${NC}"
echo ""

# 构建
export ANDROID_HOME ANDROID_SDK_ROOT
if command -v gradle &> /dev/null; then
    echo "使用系统 Gradle..."
    gradle assembleDebug 2>&1 | tail -30
else
    chmod +x gradlew
    ./gradlew assembleDebug 2>&1 | tail -30
fi

echo ""
echo -e "${GREEN}============================================${NC}"
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    APK_SIZE=$(ls -lh app/build/outputs/apk/debug/app-debug.apk | awk '{print $5}')
    echo -e "${GREEN}  ✅ 构建成功！APK 大小: $APK_SIZE${NC}"
    echo -e "${GREEN}============================================${NC}"
    echo ""
    echo "安装方法:"
    cp app/build/outputs/apk/debug/app-debug.apk "/storage/emulated/0/哨兵/Sentinel.apk"
    echo "  1. APK 已复制到: /storage/emulated/0/哨兵/Sentinel.apk"
    echo "  2. 用文件管理器打开安装"
    echo "  或运行: termux-open /storage/emulated/0/哨兵/Sentinel.apk"
else
    echo -e "${RED}  ❌ 构建失败，请查看上方错误信息${NC}"
    echo -e "${GREEN}============================================${NC}"
    echo "APK 未生成，检查 build/ 目录下的日志"
fi