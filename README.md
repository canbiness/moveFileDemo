# File Transfer System

一个基于 Spring Boot 3.4.4 与 Java 21 的大规模本地文件迁移服务，面向“源目录 -> 目标目录”的超大批量文件传输场景。项目核心目标不是简单复制几个文件，而是在长时间运行、海量文件、可恢复、可观测、可调度的前提下稳定完成迁移任务。

## 1. 项目定位

适用场景：

- 百万级到千万级文件迁移
- 超大目录树批量复制
- 需要暂停、恢复、取消的长时任务
- 需要断点续传、运行指标、批次调度的服务化迁移

核心能力：

- 异步规划：`POST /api/transfer` 立即返回，后台持续扫描源目录
- 批次切分：按文件数、字节数对文件集合切批
- 分层调度：基于 `HOT / WARM / COLD` 与优先级进行批次派发
- 断点续传：使用 `.part` 文件和持久化进度进行恢复
- 执行控制：支持 `execute / pause / resume / cancel`
- 可观测性：提供 `summary / metrics / actuator health / metrics`
- 可扩展状态层：支持内存模式，也支持启用 Redis 作为热状态存储

## 2. 技术栈

- Java 21
- Spring Boot 3.4.4
- Spring Web
- Spring Data JPA
- Spring Validation
- Spring Boot Actuator
- PostgreSQL
- Redis（可选）

## 3. 快速开始

### 3.1 环境要求

- JDK 21+
- Maven 3.9+
- PostgreSQL 15+

### 3.2 本地构建

```powershell
mvn clean test
mvn clean package
```

### 3.3 本地运行

```powershell
mvn spring-boot:run
```

默认访问地址：

```text
http://localhost:8080
```

健康检查：

```text
http://localhost:8080/actuator/health
```

## 4. 核心接口

基础路径：

```text
http://localhost:8080/api/transfer
```

### 4.1 创建迁移计划

`POST /api/transfer`

示例：

```json
{
  "sourcePath": "C:/demo/source",
  "targetPath": "D:/demo/target",
  "verificationMode": "SIZE_AND_MTIME"
}
```

行为说明：

- 接口返回 `202 Accepted`
- 任务先进入 `PLANNING`
- 客户端应轮询 `GET /api/transfer/{id}`，直到状态变为 `PLANNED`

### 4.2 查询任务摘要

`GET /api/transfer/{id}`

### 4.3 查询运行指标

`GET /api/transfer/{id}/metrics`

### 4.4 启动执行

`POST /api/transfer/{id}/execute`

### 4.5 暂停任务

`POST /api/transfer/{id}/pause`

### 4.6 恢复任务

`POST /api/transfer/{id}/resume`

### 4.7 取消任务

`POST /api/transfer/{id}/cancel`

### 4.8 分页查询批次

`GET /api/transfer/{id}/batches?page=0&size=100`

## 5. 任务状态流转

```text
CREATED -> PLANNING -> PLANNED -> RUNNING -> COMPLETED
                               -> PAUSED -> RUNNING
                               -> FAILED -> RUNNING
                               -> CANCELED
```

当前约束：

- `pause` 和 `cancel` 在 `PLANNING` 阶段不允许执行
- 已 `COMPLETED` 或 `CANCELED` 的任务不可再次执行

## 6. 可观测性

应用暴露了以下运维能力：

- `X-Request-Id` 透传与回显
- 请求耗时日志
- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- 自定义 `transferStorage` 健康检查

日志输出会同时写入控制台和滚动文件目录 `./logs`，默认包含：

- 时间戳
- 日志级别
- 线程名
- `requestId`
- Logger 名称
- 日志正文

## 7. 配置说明

主要配置位于 [application.yml](/C:/Users/小楠同学/Documents/demo/src/main/resources/application.yml:1)。

关键参数包括：

- `transfer.max-concurrency`：执行线程池大小
- `transfer.scalable-batch-file-count`：单批次最大文件数
- `transfer.scalable-batch-bytes`：单批次最大字节数
- `transfer.query-default-page-size`：批次分页默认大小
- `transfer.query-max-page-size`：批次分页最大大小
- `transfer.progress-save-interval-bytes`：批次进度落库间隔
- `transfer.redis-enabled`：是否启用 Redis 热状态层

## 8. 文档索引

- [用户使用文档](C:/Users/小楠同学/Documents/demo/docs/用户使用文档.md)
- [项目说明文档](C:/Users/小楠同学/Documents/demo/docs/项目说明文档.md)
- [构建与运维手册](C:/Users/小楠同学/Documents/demo/docs/构建与运维手册.md)

## 9. 测试

当前测试覆盖重点：

- 文件校验逻辑
- 调度策略逻辑
- 异步规划流程
- 端到端执行、暂停、恢复、取消
- Request ID 回显
- Actuator 健康与指标暴露

运行测试：

```powershell
mvn test
```
