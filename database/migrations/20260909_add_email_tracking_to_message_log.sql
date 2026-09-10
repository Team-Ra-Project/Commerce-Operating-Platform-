ALTER TABLE message_log
  ADD COLUMN tracking_token VARCHAR(64) NULL AFTER provider_status_detail,
  ADD COLUMN delivered_at DATETIME NULL AFTER tracking_token,
  ADD COLUMN opened_at DATETIME NULL AFTER delivered_at,
  ADD COLUMN clicked_at DATETIME NULL AFTER opened_at,
  ADD UNIQUE KEY uq_mlog_tracking_token (tracking_token);