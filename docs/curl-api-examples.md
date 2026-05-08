# curl.exe 接口示例

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
$bodyPath = Join-Path $env:TEMP 'transfer-request.json'
@'
{"sourcePath":"C:/test/source","targetPath":"C:/test/target","verificationMode":"SIZE_AND_MTIME"}
'@ | Set-Content -Encoding utf8 $bodyPath

curl.exe -i -X POST http://localhost:8080/api/transfer `
  -H "Content-Type: application/json" `
  --data-binary "@$bodyPath"
```

## 查询任务摘要

```bash
curl.exe -i http://localhost:8080/api/transfer/9a23ffaabf668fa6d0611cfa252e01e3
```

## 查询任务指标

```bash
curl.exe -i http://localhost:8080/api/transfer/9a23ffaabf668fa6d0611cfa252e01e3/metrics
```

## 启动任务执行

```bash
curl.exe -i -X POST http://localhost:8080/api/transfer/c3d56f4cdf82bd4a4cee2c413dd82412/execute
```

## 暂停任务

```bash
curl.exe -i -X POST http://localhost:8080/api/transfer/9a23ffaabf668fa6d0611cfa252e01e3/pause
```

## 恢复任务

```bash
curl.exe -i -X POST http://localhost:8080/api/transfer/9a23ffaabf668fa6d0611cfa252e01e3/resume
```

## 取消任务

```bash
curl.exe -i -X POST http://localhost:8080/api/transfer/9a23ffaabf668fa6d0611cfa252e01e3/cancel
```

## 分页查询批次

```bash
curl.exe -i http://localhost:8080/api/transfer/9a23ffaabf668fa6d0611cfa252e01e3/batches?page=0&size=100
```
