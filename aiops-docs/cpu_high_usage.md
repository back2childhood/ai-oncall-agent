# CPU使用率过高告警处理方案

## 告警名称
- **告警名**: `HighCPUUsage`
- **告警级别**: 严重
- **触发条件**: CPU使用率持续5分钟超过80%

## 问题描述
当服务器或容器的CPU使用率持续超过80%时，可能导致:
- 应用响应变慢
- 请求超时增加
- 系统负载过高
- 可能触发级联故障

## 排查步骤

### 步骤1: 获取当前时间
**工具**: `get_current_time`
**目的**: 确定告警发生的时间范围，用于后续日志查询

### 步骤2: 查询系统日志
**工具**: `query_logs`
**参数要求**:
- **日志主题**: `system-metrics`
- **时间范围**: 最近30分钟
- **查询条件**: `level:ERROR OR cpu_usage:>80`

**查询示例**:
```text
查询最近30分钟 system-metrics 中 CPU 使用率超过 80% 或 ERROR 级别的日志
```

### 步骤3: 查询应用日志
**工具**: `query_logs`
**参数要求**:
- **日志主题**: `application-logs`
- **时间范围**: 告警前后15分钟
- **查询条件**: `timeout OR slow OR thread OR gc OR cpu`

### 步骤4: 检查Prometheus指标
重点查看:
- `container_cpu_usage_seconds_total`
- `process_cpu_usage`
- `system_load_average_1m`
- `http_server_requests_seconds_count`
- `jvm_gc_pause_seconds_count`

## 常见原因
- 流量突增导致请求量过高
- 某个接口存在死循环或高复杂度计算
- GC频繁导致CPU消耗升高
- 线程池耗尽或线程争用
- 批处理任务与在线服务抢占CPU

## 处理建议
- 如果是流量突增，先扩容实例或提高副本数
- 如果是单接口异常，定位接口并临时限流
- 如果是GC导致，检查内存压力和对象分配速率
- 如果是批处理任务导致，暂停或错峰执行任务
- 如果CPU持续超过90%，优先保护核心链路

## 验证恢复
确认以下条件:
- CPU使用率低于70%并持续10分钟
- 请求延迟恢复到正常范围
- 错误率没有继续上升
- 新日志中不再出现CPU相关错误
