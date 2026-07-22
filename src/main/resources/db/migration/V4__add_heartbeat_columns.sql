-- =====================================================================
-- V4: M2 阶段为 devices 表新增心跳相关字段
-- =====================================================================
--
-- 新增字段:
--   battery_level         设备电量百分比 (0-100)
--   last_known_location   设备最后已知位置(GeoJSON 字符串或简单 "lat,lon" 格式)
--
-- 注意:last_seen_at 字段在 V2 已经存在,M2 直接复用,会被心跳处理逻辑频繁更新。
--
-- 新增索引:
--   idx_devices_status_last_seen:支持离线检测定时任务高效扫描
--     "找出 status=ONLINE 但 last_seen_at < 阈值的设备"
-- =====================================================================

ALTER TABLE devices
    ADD COLUMN battery_level INTEGER,
    ADD COLUMN last_known_location VARCHAR(255);

-- 离线检测专用复合索引:WHERE status = 'ONLINE' AND last_seen_at < ?
-- 使用部分索引(WHERE status='ONLINE'),只索引在线设备,索引体积小、扫描快
CREATE INDEX idx_devices_status_last_seen
    ON devices(last_seen_at)
    WHERE status = 'ONLINE';

-- 查询某租户最近活跃设备(按心跳时间倒序)
CREATE INDEX idx_devices_tenant_last_seen
    ON devices(tenant_id, last_seen_at DESC);

COMMENT ON COLUMN devices.battery_level IS 'Battery percentage 0-100, updated by heartbeat';
COMMENT ON COLUMN devices.last_known_location IS 'Last reported location, format "lat,lon" or GeoJSON';
