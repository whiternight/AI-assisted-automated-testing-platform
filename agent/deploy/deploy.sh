#!/bin/bash
# agent 生产环境部署脚本（Alibaba Cloud Linux 3）
# 使用方法：
#   1. 将代码上传到 /opt/agent
#   2. 将 .env.production 复制为 /opt/agent/.env 并检查密钥
#   3. cd /opt/agent && sudo bash deploy/deploy.sh

set -e

APP_DIR="/opt/agent"
SERVICE_NAME="agent"
USER_NAME="agent"

echo "=== 开始部署 agent ==="

# 1. 安装系统依赖
echo "[1/7] 安装系统依赖..."
dnf update -y
dnf install -y python3.12 python3.12-pip python3.12-devel gcc gcc-c++ make git curl

# 2. 安装 uv
echo "[2/7] 安装 uv..."
if ! command -v uv &> /dev/null; then
    curl -LsSf https://astral.sh/uv/install.sh | sh
    # 确保当前 shell 能找到 uv
    export PATH="$HOME/.local/bin:$PATH"
fi
uv --version

# 3. 创建运行用户
echo "[3/7] 创建运行用户..."
if ! id -u "$USER_NAME" &> /dev/null; then
    useradd -r -s /bin/false -d "$APP_DIR" "$USER_NAME"
fi

# 4. 准备目录和权限
echo "[4/7] 准备目录..."
mkdir -p "$APP_DIR"/chroma_db "$APP_DIR"/sessions "$APP_DIR"/data "$APP_DIR"/uploads "$APP_DIR"/logs
chown -R "$USER_NAME:$USER_NAME" "$APP_DIR"

# 5. 安装 Python 依赖
echo "[5/7] 安装 Python 依赖..."
cd "$APP_DIR"
# 使用项目自带的 Python 3.12
uv python pin 3.12
uv sync --no-dev

# 6. 注册 systemd 服务
echo "[6/7] 注册 systemd 服务..."
cp "$APP_DIR/deploy/agent.service" /etc/systemd/system/agent.service
systemctl daemon-reload
systemctl enable agent

# 7. 启动服务
echo "[7/7] 启动 agent 服务..."
systemctl restart agent
sleep 2
systemctl status agent --no-pager

echo ""
echo "=== agent 部署完成 ==="
echo "服务状态：systemctl status agent"
echo "查看日志：journalctl -u agent -f"
echo "健康检查：curl http://127.0.0.1:8000/api/agent/health"
