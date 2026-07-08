# 封口机固定支架设备配套绑定管理系统

## 项目简介

包装车间金属支架档案系统，维护支架与封口生产设备的一对一配套绑定、解绑和批量绑定记录。

## 技术栈

- 前端：Vue 3、Vite 5、TypeScript、Element Plus、Axios
- 后端：Spring Boot 3、JDK 17、Spring Data JPA、Spring Data Redis、Maven
- 数据：MySQL 8、Redis 7
- 部署：Docker Compose、Nginx

## 端口说明

| 服务 | 地址 |
| --- | --- |
| 前端 | http://localhost:3124 或 http://127.0.0.1:3124 |
| 后端 API | http://127.0.0.1:8124/api |
| MySQL | 127.0.0.1:3424 |
| Redis | 127.0.0.1:6424 |

端口来自根目录 `.env`，Docker 端口只绑定 `127.0.0.1`。

## 启动方式

```bash
cd qd-124
docker compose up -d --build
```

本地拆分验证：

```bash
cd backend
mvn compile -q

cd ../frontend
npm ci
npm run build
```

## Docker 构建说明

Docker Compose 构建前端 Nginx 镜像和后端 Spring Boot 镜像，并启动 MySQL、Redis：

```bash
docker compose up -d --build
docker compose ps
```

## 常见问题

- 端口被占用：修改 `.env` 对应端口并重新启动。
- Maven 编译失败：优先检查 JDK 17、Lombok 版本和 annotation processor。
- 页面运行时接口失败：构建通过后检查后端容器日志、数据库连接和 Nginx `/api` 转发。
