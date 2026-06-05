# 服务不可用告警处理方案

## 告警名称
- **告警名**: `ServiceUnavailable`
- **告警级别**: 严重
- **触发条件**: 健康检查失败或5xx错误率持续升高

## 问题描述
服务不可用通常表现为:
- 健康检查失败
- HTTP 500/502/503增加
- 上游网关无法转发
- 用户请求失败

## 排查步骤

### 步骤1: 获取当前时间
**工具**: `get_current_time`
**目的**: 锁定不可用窗口

### 步骤2: 查询服务错误日志
**工具**: `query_logs`
**参数要求**:
- **日志主题**: `application-logs`
- **时间范围**: 最近20分钟
- **查询条件**: `status:500 OR status:502 OR status:503 OR exception OR unavailable`

### 步骤3: 查询部署和重启日志
**工具**: `query_logs`
**参数要求**:
- **日志主题**: `deployment-events`
- **时间范围**: 最近1小时
- **查询条件**: `deploy OR restart OR rollback OR crash`

### 步骤4: 检查Prometheus指标
重点查看:
- `up`
- `http_server_requests_seconds_count`
- `http_server_requests_seconds_sum`
- `kube_pod_container_status_restarts_total`
- `kube_deployment_status_replicas_available`

## 常见原因
- 新版本发布引入异常
- 依赖服务不可用
- 数据库连接池耗尽
- 容器频繁重启
- 配置错误或密钥过期

## 处理建议
- 如果最近刚发布，优先考虑回滚
- 如果依赖服务异常，切换降级策略
- 如果连接池耗尽，检查慢查询和连接泄漏
- 如果容器重启，查看启动日志和探针配置
- 如果是配置问题，恢复上一版配置

## 验证恢复
确认:
- 健康检查恢复成功
- 5xx错误率回到正常水平
- 可用副本数恢复
- 用户请求成功率恢复
