# 内存使用率过高告警处理方案

## 告警名称
- **告警名**: `HighMemoryUsage`
- **告警级别**: 严重
- **触发条件**: 内存使用率持续5分钟超过85%

## 问题描述
当服务内存使用率过高时，可能导致:
- JVM频繁Full GC
- 容器被OOM Kill
- 请求延迟升高
- 服务实例重启

## 排查步骤

### 步骤1: 获取当前时间
**工具**: `get_current_time`
**目的**: 确认告警窗口，避免查询错误时间段

### 步骤2: 查询内存相关日志
**工具**: `query_logs`
**参数要求**:
- **日志主题**: `application-logs`
- **时间范围**: 最近30分钟
- **查询条件**: `OutOfMemoryError OR OOM OR Full GC OR memory`

### 步骤3: 检查Prometheus指标
重点查看:
- `jvm_memory_used_bytes`
- `jvm_memory_committed_bytes`
- `jvm_gc_pause_seconds_count`
- `container_memory_working_set_bytes`
- `container_memory_usage_bytes`

### 步骤4: 判断是否发生内存泄漏
观察:
- 内存是否持续上涨且不回落
- Full GC后内存是否仍然很高
- 是否有新增版本或新功能上线
- 是否有大对象缓存或批量查询

## 常见原因
- 缓存无限增长
- 大批量数据一次性加载
- 对象引用未释放
- 请求体或响应体过大
- JVM堆大小配置不合理

## 处理建议
- 如果服务已接近OOM，先扩容或重启单个异常实例
- 如果是缓存问题，清理缓存或降低缓存容量
- 如果是批量任务，暂停任务并降低批大小
- 如果是新版本引入，考虑回滚
- 保留堆转储用于后续分析

## 验证恢复
确认:
- 内存使用率低于75%
- Full GC频率下降
- 实例没有继续重启
- 错误日志不再出现OOM或内存告警
