# 封口机固定支架设备配套绑定管理系统

## 项目简介

包装车间金属支架档案系统，维护支架与封口生产设备的一对一配套绑定、解绑和批量绑定记录。

支持为每台封口机维护配套规则：最大支架数量、允许型号、长度/宽度范围。单个绑定与批量绑定前先按规则逐项校验，
展示通过项、冲突项及冲突原因，确认后仅绑定通过项。规则修改立即生效于后续绑定，不改动已有绑定。

车间换线时支持「换线改挂」：选择源设备上已挂的支架与目标封口机，先按目标机现行型号、长宽与容量规则逐项预检，
确认后通过项在同一事务内直接改挂到目标机（全程保持已绑定，不经过先解绑再绑定的未绑定中间态），冲突项仍留在源设备。
改挂完成后支架档案、设备清单占用与未绑定统计刷新一致。


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
mvn test

cd ../frontend
npm ci
npm run build
npm test
```

后端测试（JUnit 5 + Mockito）覆盖热门型号 Redis 缓存在支架新增、改名、删除后的失效逻辑，以及 Redis 异常不影响支架保存的降级路径；另含换线改挂端到端测试（预检按目标机规则分项、确认后通过项一次改挂且冲突项留在源设备、全程无未绑定中间态）。前端测试（Vitest + Vue Test Utils）回归支架档案操作后型号建议刷新、绑定/规则页型号建议实时性，以及改挂预检请求、确认后设备占用刷新。

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
