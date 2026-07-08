#!/bin/bash
# ============================================================
# 封口机固定支架设备配套绑定管理系统 - 一键启动脚本
# 项目名称：qd-124
# ============================================================
set -e

# ===== 颜色定义 =====
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# ===== 项目根目录 =====
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "${PROJECT_ROOT}"

# ===== 加载 .env 变量 =====
if [ -f ".env" ]; then
    set -a; . ./.env; set +a
else
    echo -e "${RED}[错误]${NC} 未找到 .env 配置文件！"
    exit 1
fi

# ===== Banner =====
echo -e ""
echo -e "${CYAN}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║${NC}     ${BOLD}封口机固定支架设备配套绑定管理系统${NC}            ${CYAN}║${NC}"
echo -e "${CYAN}║${NC}     项目名称: ${APP_NAME}                                    ${CYAN}║${NC}"
echo -e "${CYAN}╚═══════════════════════════════════════════════════════╝${NC}"
echo -e ""

# ============================================================
#  [1/6] 端口占用检测
# ============================================================
echo -e "${BLUE}[1/6]${NC} 检测端口占用情况..."

check_port() {
    local port=$1
    local name=$2
    if lsof -nP -iTCP:${port} -sTCP:LISTEN >/dev/null 2>&1; then
        local pid_info=$(lsof -nP -iTCP:${port} -sTCP:LISTEN -t 2>/dev/null | head -1)
        local proc_name=""
        [ -n "${pid_info}" ] && proc_name=$(ps -p ${pid_info} -o comm= 2>/dev/null || echo "unknown")
        echo -e "${RED}  ✗ 端口 ${port} (${name}) 已被占用！${NC}"
        echo -e "${RED}    进程 PID: ${pid_info}, 进程名: ${proc_name}${NC}"
        echo -e "${YELLOW}    请先停止占用该端口的进程，或修改 .env 中的 ${name^^}_PORT${NC}"
        return 1
    else
        echo -e "${GREEN}  ✓ 端口 ${port} (${name}) 可用${NC}"
        return 0
    fi
}

PORT_OK=true
check_port "${FRONTEND_PORT}" "frontend" || PORT_OK=false
check_port "${BACKEND_PORT}" "backend"   || PORT_OK=false
check_port "${MYSQL_PORT}" "mysql"       || PORT_OK=false
check_port "${REDIS_PORT}" "redis"       || PORT_OK=false

if [ "${PORT_OK}" != "true" ]; then
    echo -e "\n${RED}[错误]${NC} 存在端口冲突，请先解决后再启动。"
    exit 1
fi

echo -e ""

# ============================================================
#  [2/6] 本地构建：后端 JAR（Maven）
#  利用本机 ~/.m2/repository 全局依赖缓存：
#   - pom.xml 无变更 → 跳过依赖下载，仅编译
#   - 仅 src/ 变更 → 增量编译
#   - pom.xml 有变更 → 按需下载新依赖
# ============================================================
echo -e "${BLUE}[2/6]${NC} 本地构建后端 JAR（Maven）..."

cd "${PROJECT_ROOT}/backend"

if ! command -v mvn >/dev/null 2>&1; then
    echo -e "${YELLOW}  ⚠ 本机未安装 mvn 命令，跳过本地 Maven 构建，尝试使用已有的 target/*.jar${NC}"
    if ls target/*.jar 1>/dev/null 2>&1; then
        echo -e "${GREEN}  ✓ 发现已存在的 JAR 产物，将直接使用${NC}"
    else
        echo -e "${RED}  ✗ 未找到 target/*.jar，且本机无 mvn，请先安装 Maven 或手动构建 JAR${NC}"
        exit 1
    fi
else
    # 阿里云镜像配置：若本机无 settings.xml 则使用项目内配置
    SETTINGS_ARG=""
    if [ -f "${PROJECT_ROOT}/backend/settings.xml" ]; then
        SETTINGS_ARG="-s ${PROJECT_ROOT}/backend/settings.xml"
    fi

    echo -e "  执行: mvn clean package -DskipTests (阿里云镜像)"
    if mvn ${SETTINGS_ARG} -B -q clean package -DskipTests -Dmaven.test.skip=true 2>&1; then
        JAR_COUNT=$(ls target/*.jar 2>/dev/null | wc -l | tr -d ' ')
        if [ "${JAR_COUNT}" -gt 0 ]; then
            echo -e "${GREEN}  ✓ 后端 JAR 构建成功 (${JAR_COUNT} 个文件)${NC}"
            ls -lh target/*.jar 2>/dev/null | awk '{print "    - " $9 " (" $5 ")"}'
        else
            echo -e "${RED}  ✗ 构建成功但未找到 JAR 产物${NC}"
            exit 1
        fi
    else
        echo -e "${RED}  ✗ 后端 Maven 构建失败${NC}"
        echo -e "  可尝试手动执行: cd backend && mvn clean package -DskipTests"
        exit 1
    fi
fi

cd "${PROJECT_ROOT}"
echo -e ""

# ============================================================
#  [3/6] 本地构建：前端 dist（Vite + npm ci）
#  利用本机 node_modules 缓存：
#   - package-lock.json 无变更 → npm ci 跳过安装（已在 node_modules）
#   - 仅 src/ 变更 → vite 增量构建
#   - package.json 有变更 → npm ci 按 lockfile 同步
# ============================================================
echo -e "${BLUE}[3/6]${NC} 本地构建前端 dist（Vite）..."

cd "${PROJECT_ROOT}/frontend"

if ! command -v npm >/dev/null 2>&1; then
    echo -e "${YELLOW}  ⚠ 本机未安装 npm 命令，跳过本地前端构建，尝试使用已有的 dist/${NC}"
    if [ -f "dist/index.html" ]; then
        echo -e "${GREEN}  ✓ 发现已存在的 dist/，将直接使用${NC}"
    else
        echo -e "${RED}  ✗ 未找到 dist/index.html，且本机无 npm，请先安装 Node.js 或手动构建前端${NC}"
        exit 1
    fi
else
    # 使用华为云 npm 镜像
    if [ -f ".npmrc" ]; then
        echo -e "  使用 .npmrc 中的镜像源进行依赖安装（如需要）"
    fi

    # 仅当 node_modules 不存在或 package-lock.json 变更时执行 npm ci
    NEED_INSTALL=false
    if [ ! -d "node_modules" ]; then
        NEED_INSTALL=true
        echo -e "  node_modules 不存在，执行 npm ci..."
    elif [ "package.json" -nt "node_modules/.package-lock.json" ] 2>/dev/null || \
         [ "package-lock.json" -nt "node_modules/.package-lock.json" ] 2>/dev/null; then
        NEED_INSTALL=true
        echo -e "  package.json 或 package-lock.json 有变更，执行 npm ci..."
    fi

    if [ "${NEED_INSTALL}" = "true" ]; then
        if npm ci --cache ./.npm-cache --prefer-offline 2>&1; then
            echo -e "${GREEN}  ✓ npm ci 完成${NC}"
        else
            echo -e "${YELLOW}  ⚠ npm ci 失败，尝试 npm install...${NC}"
            if npm install --cache ./.npm-cache 2>&1; then
                echo -e "${GREEN}  ✓ npm install 完成${NC}"
            else
                echo -e "${RED}  ✗ npm 依赖安装失败${NC}"
                exit 1
            fi
        fi
    else
        echo -e "  node_modules 已最新，跳过依赖安装"
    fi

    # 执行 Vite 构建
    echo -e "  执行: npx vite build"
    if npx vite build 2>&1 | tail -5; then
        if [ -f "dist/index.html" ]; then
            echo -e "${GREEN}  ✓ 前端构建完成${NC}"
            ls -lh dist/ 2>/dev/null | awk 'NR>1 {print "    - " $9 " (" $5 ")"}'
        else
            echo -e "${RED}  ✗ 构建成功但未找到 dist/index.html${NC}"
            exit 1
        fi
    else
        echo -e "${RED}  ✗ 前端 Vite 构建失败${NC}"
        exit 1
    fi
fi

cd "${PROJECT_ROOT}"
echo -e ""

# ============================================================
#  [4/6] 清理旧容器
# ============================================================
echo -e "${BLUE}[4/6]${NC} 清理旧容器..."

EXISTING=$(docker compose ps -a --format '{{.Names}}' 2>/dev/null | wc -l | tr -d ' ')
if [ "${EXISTING}" -gt 0 ]; then
    echo -e "  检测到 ${EXISTING} 个旧容器，停止并清理..."
    docker compose down --remove-orphans >/dev/null 2>&1 || true
    # 清理同名卷（避免 schema.sql 不重新执行）
    docker volume rm -f ${APP_NAME}-mysql-data ${APP_NAME}-redis-data >/dev/null 2>&1 || true
    echo -e "${GREEN}  ✓ 旧容器与数据卷已清理${NC}"
else
    echo -e "${GREEN}  ✓ 无旧容器，跳过${NC}"
fi

echo -e ""

# ============================================================
#  [5/6] Docker Compose 构建 + 启动
# ============================================================
echo -e "${BLUE}[5/6]${NC} 启动 Docker Compose（构建运行时镜像）..."
echo -e "  * 本阶段仅 COPY 预构建产物，无需下载依赖，构建极快"
echo -e ""

if docker compose up -d --build 2>&1; then
    echo -e ""
    echo -e "${GREEN}  ✓ 容器构建与启动完成${NC}"
else
    echo -e ""
    echo -e "${RED}[错误]${NC} Docker Compose 启动失败！"
    echo -e "  排查: docker compose logs -f"
    exit 1
fi

echo -e ""

# ============================================================
#  [6/6] 健康检查 + 最终输出
# ============================================================
echo -e "${BLUE}[6/6]${NC} 等待服务就绪（最多 120 秒）..."

MAX_WAIT=120
WAIT_INTERVAL=3
ELAPSED=0

check_backend() {
    curl -sS -o /dev/null -w "%{http_code}" "http://127.0.0.1:${BACKEND_PORT}/api/bracket/stats" 2>/dev/null || echo "000"
}
check_frontend() {
    curl -sS -o /dev/null -w "%{http_code}" "http://127.0.0.1:${FRONTEND_PORT}/" 2>/dev/null || echo "000"
}

while [ ${ELAPSED} -lt ${MAX_WAIT} ]; do
    BE_CODE=$(check_backend)
    FE_CODE=$(check_frontend)
    if [ "${BE_CODE}" = "200" ] && [ "${FE_CODE}" = "200" ]; then
        echo -e "  ${GREEN}✓ 后端 API 就绪 (HTTP ${BE_CODE})${NC}"
        echo -e "  ${GREEN}✓ 前端 Nginx 就绪 (HTTP ${FE_CODE})${NC}"
        break
    fi
    echo -ne "  已等待 ${ELAPSED}s... 后端:${BE_CODE} 前端:${FE_CODE}\\r"
    sleep ${WAIT_INTERVAL}
    ELAPSED=$((ELAPSED + WAIT_INTERVAL))
done

echo -e ""

# ===== 端口监听一致性验证 =====
echo -e ""
echo -e "${BLUE}端口监听一致性验证:${NC}"
echo -e "  lsof -nP 检查 4 个端口绑定地址..."

BIND_OK=true
for PORT_PAIR in "${FRONTEND_PORT}:Nginx" "${BACKEND_PORT}:SpringBoot" "${MYSQL_PORT}:MySQL" "${REDIS_PORT}:Redis"; do
    P=${PORT_PAIR%%:*}
    N=${PORT_PAIR##*:}
    LISTEN_ADDR=$(lsof -nP -iTCP:${P} -sTCP:LISTEN -Fn 2>/dev/null | grep '^n' | head -1 | sed 's/^n//' | awk -F: '{print $(NF-1)}')
    if [ "${LISTEN_ADDR}" = "127.0.0.1" ]; then
        echo -e "  ${GREEN}✓ ${N} (${P}) 仅绑定 127.0.0.1${NC}"
    else
        echo -e "  ${YELLOW}⚠ ${N} (${P}) 绑定地址: ${LISTEN_ADDR:-unknown}（建议仅 127.0.0.1）${NC}"
    fi
done

# ===== IP/域名 一致性验证（localhost 与 127.0.0.1 必须返回同一页面） =====
echo -e ""
echo -e "${BLUE}localhost 与 127.0.0.1 响应一致性验证:${NC}"
IP_TITLE=$(curl -sS "http://127.0.0.1:${FRONTEND_PORT}/" 2>/dev/null | grep -oE '<title>[^<]+</title>' || echo "")
LH_TITLE=$(curl -sS "http://localhost:${FRONTEND_PORT}/" 2>/dev/null | grep -oE '<title>[^<]+</title>' || echo "")

if [ -n "${IP_TITLE}" ] && [ "${IP_TITLE}" = "${LH_TITLE}" ]; then
    echo -e "  ${GREEN}✓ 两者返回相同: ${IP_TITLE}${NC}"
else
    echo -e "  ${IP_TITLE:+IP版本: }${IP_TITLE:-未获取到}"
    echo -e "  ${LH_TITLE:+localhost版本: }${LH_TITLE:-未获取到}"
    echo -e "  ${YELLOW}⚠ 请检查 localhost / 127.0.0.1 解析一致性${NC}"
fi

# ===== 后端 API 验证 =====
echo -e ""
echo -e "${BLUE}后端 API 快速验证:${NC}"
STATS=$(curl -sS "http://127.0.0.1:${BACKEND_PORT}/api/bracket/stats" 2>/dev/null || echo "{}")
echo -e "  GET /api/bracket/stats → ${STATS}"

# ===== 最终输出（构建成功自动输出前端地址） =====
echo -e ""
echo -e "${GREEN}╔═══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║${NC}                  ${BOLD}🎉 项目 qd-124 启动成功！${NC}                      ${GREEN}║${NC}"
echo -e "${GREEN}╚═══════════════════════════════════════════════════════════════╝${NC}"
echo -e ""
echo -e "  ${BOLD}📌 前端访问地址${NC}（两者完全等价）:"
echo -e "     ${CYAN}http://localhost:${FRONTEND_PORT}${NC}   ← 推荐使用"
echo -e "     ${CYAN}http://127.0.0.1:${FRONTEND_PORT}${NC}"
echo -e ""
echo -e "  ${BOLD}🔧 后端接口:${NC}"
echo -e "     ${CYAN}http://127.0.0.1:${BACKEND_PORT}/api/bracket/stats${NC}"
echo -e ""
echo -e "  ${BOLD}🗄️  数据库:${NC}"
echo -e "     MySQL : ${CYAN}127.0.0.1:${MYSQL_PORT}${NC}  user=root / pwd=${MYSQL_ROOT_PASSWORD}"
echo -e "     Redis : ${CYAN}127.0.0.1:${REDIS_PORT}${NC}"
echo -e ""
echo -e "  ${BOLD}📦 运行容器:${NC}"
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null | sed 's/^/    /'
echo -e ""
echo -e "  ${BOLD}🛠️  常用命令:${NC}"
echo -e "    查看日志      : ${YELLOW}docker compose logs -f${NC}"
echo -e "    重启服务      : ${YELLOW}./start.sh${NC}"
echo -e "    仅重启容器    : ${YELLOW}docker compose up -d${NC}   (业务代码已修改并本地构建后)"
echo -e "    停止+保留数据 : ${YELLOW}docker compose down${NC}"
echo -e "    停止+重置数据 : ${YELLOW}docker compose down -v${NC}"
echo -e ""
