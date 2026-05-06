# curl.exe API Examples

下面是这个项目当前接口的 `curl.exe` 调用示例。每段都是独立命令，不使用公共参数。

## 健康检查

```bash
curl.exe -i http://localhost:8080/actuator/health
```

## 应用信息

```bash
curl.exe -i http://localhost:8080/actuator/info
```

## 指标入口

```bash
curl.exe -i http://localhost:8080/actuator/metrics
```

## 自定义健康检查

```bash
curl.exe -i http://localhost:8080/actuator/health/transferStorage
```

## 创建迁移计划

```bash
curl.exe -i -X POST http://localhost:8080/api/transfer \
  -H "Content-Type: application/json" \
  -d '{
    "sourcePath": "C:/demo/source",
    "targetPath": "D:/demo/target",
    "verificationMode": "SIZE_AND_MTIME"
  }'
```

## 查询任务摘要

```bash
curl.exe -i http://localhost:8080/api/transfer/replace-with-task-id
```

## 查询任务指标

```bash
curl.exe -i http://localhost:8080/api/transfer/replace-with-task-id/metrics
```

## 启动任务执行

```bash
curl.exe -i -X POST http://localhost:8080/api/transfer/replace-with-task-id/execute
```

## 暂停任务

```bash
curl.exe -i -X POST http://localhost:8080/api/transfer/replace-with-task-id/pause
```

## 恢复任务

```bash
curl.exe -i -X POST http://localhost:8080/api/transfer/replace-with-task-id/resume
```

## 取消任务

```bash
curl.exe -i -X POST http://localhost:8080/api/transfer/replace-with-task-id/cancel
```

## 分页查询批次

```bash
curl.exe -i http://localhost:8080/api/transfer/replace-with-task-id/batches?page=0&size=100
```
