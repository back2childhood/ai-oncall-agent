# 接口响应变慢告警处理方案

## 告警名称
- **告警名**: `SlowResponse`
- **告警级别**: 警告
- **触发条件**: P95响应时间持续10分钟超过2秒

## 问题描述
接口响应变慢可能导致:
- 用户体验下降
- 请求排队
- 线程池耗尽
- 上游超时

## 排查步骤

### 步骤1: 获取当前时间
**工具**: `get_current_time`
**目的**: 确认慢响应发生时间段

### 步骤2: 查询慢请求日志
**工具**: `query_logs`
**参数要求**:
- **日志主题**: `application-logs`
- **时间范围**: 最近30分钟
- **查询条件**: `latency:>2000 OR slow OR timeout`

### 步骤3: 查询下游依赖日志
**工具**: `query_logs`
**参数要求**:
- **日志主题**: `dependency-logs`
- **时间范围**: 告警前后15分钟
- **查询条件**: `database OR redis OR payment OR timeout OR retry`

### 步骤4: 检查Prometheus指标
重点查看:
- `http_server_requests_seconds_bucket`
- `http_client_requests_seconds_bucket`
- `jdbc_connections_active`
- `hikaricp_connections_pending`
- `executor_queued_tasks`

## 常见原因
- 数据库慢查询
- 下游接口超时或重试
- Redis或缓存命中率下降
- 线程池队列堆积
- 流量突增

## 处理建议
- 如果数据库慢，定位SQL并临时降级非核心查询
- 如果下游慢，开启熔断或降级
- 如果线程池满，先扩容实例并检查阻塞点
- 如果流量突增，启用限流或缓存
- 如果是新版本导致，考虑回滚

## 验证恢复
确认:
- P95响应时间低于目标阈值
- 超时日志减少
- 线程池队列恢复正常
- 下游调用成功率恢复
