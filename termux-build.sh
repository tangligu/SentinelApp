#!/data/data/com.termux/files/usr/bin/bash
#
# 哨兵 APK 一键构建脚本 - 在 Termux 中运行
# 使用方法: bash termux-build.sh
#

set -e

echo "============================================"
echo "  哨兵 (Sentinel) APK 构建工具"
echo "============================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# 检查是否在 Termux 中运行
if [ ! -d "/data/data/com.termux" ]; then
    echo -e "${RED}错误: 此脚本必须在 Termux 中运行${NC}"
    echo "请在手机上安装 Termux 后运行"
    exit 1
fi

echo -e "${YELLOW}[1/6] 更新包管理器...${NC}"
pkg update -y

echo -e "${YELLOW}[2/6] 安装 JDK 17...${NC}"
pkg install -y openjdk-17

echo -e "${YELLOW}[3/6] 安装 Gradle...${NC}"
pkg install -y gradle

echo -e "${YELLOW}[4/6] 安装 Android SDK...${NC}"
# 安装 Android SDK 所需的工具
pkg install -y android-sdk-26 aapt apksigner dx

# 设置 Android SDK 环境变量
export ANDROID_HOME=$PREFIX/lib/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME

echo -e "${YELLOW}[5/6] 配置项目...${NC}"
# 复制项目文件
PROJECT_DIR="$HOME/SentinelApp"
if [ -d "$PROJECT_DIR" ]; then
    echo "项目已存在，更新中..."
    rm -rf "$PROJECT_DIR"
fi

# 从手机存储复制项目
if [ -f "/storage/emulated/0/哨兵/SentinelApp.zip" ]; then
    cp "/storage/emulated/0/哨兵/SentinelApp.zip" "$HOME/"
    cd "$HOME"
    unzip -o SentinelApp.zip -d SentinelApp
    cd SentinelApp
else
    echo -e "${RED}未找到项目文件，请将 SentinelApp.zip 放到 /storage/emulated/0/哨兵/ 目录${NC}"
    exit 1
fi

# 创建 local.properties 指向 Android SDK
echo "sdk.dir=$ANDROID_HOME" > local.properties

echo -e "${YELLOW}[6/6] 开始构建 APK...${NC}"
echo ""
echo -e "${GREEN}正在编译... 这可能需要几分钟${NC}"
echo ""

# 给 gradlew 执行权限
chmod +x gradlew

# 构建 Debug APK
./gradlew assembleDebug

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  ✅ 构建完成！${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "APK 文件位置:"
echo "  $PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "安装方法:"
echo "  1. 在 Termux 中运行:"
echo "     cp $PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk /storage/emulated/0/哨兵/"
echo "  2. 用文件管理器安装 APK"
echo ""