package com.rastudio.commerce.retention;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoints embedded in campaign emails. The token is a random per-message
 * capability, so these endpoints do not require a user session.
 */
@RestController
@RequestMapping("/api/email-tracking")
public class EmailTrackingController {

  private static final byte[] TRANSPARENT_GIF = new byte[] {
      0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00,
      0x01, 0x00, (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00,
      (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x21, (byte) 0xF9,
      0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2C, 0x00,
      0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
      0x02, 0x02, 0x44, 0x01, 0x00, 0x3B
  };

  private final MessageLogRepository messageLogs;
  private final CampaignRepository campaigns;

  public EmailTrackingController(MessageLogRepository messageLogs, CampaignRepository campaigns) {
    this.messageLogs = messageLogs;
    this.campaigns = campaigns;
  }

  @GetMapping(value = "/open/{token}", produces = MediaType.IMAGE_GIF_VALUE)
  @Transactional
  public ResponseEntity<byte[]> open(@PathVariable String token) {
    markOpened(token);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(TRANSPARENT_GIF);
  }

  @GetMapping("/click/{token}")
  @Transactional
  public ResponseEntity<Void> click(@PathVariable String token, @RequestParam String url) {
    Optional<MessageLog> log = messageLogs.findByTrackingToken(token);
    if (log.isPresent()) {
      MessageLog entry = log.get();
      markOpened(entry);
      if (entry.clickedAt == null) {
        entry.clickedAt = LocalDateTime.now();
        if (entry.campaignId != null) {
          campaigns.findById(entry.campaignId).ifPresent(c -> {
            c.clickedCount = c.clickedCount + 1;
            campaigns.save(c);
          });
        }
        messageLogs.save(entry);
      }
    }

    try {
      URI destination = URI.create(url);
      if (!"http".equalsIgnoreCase(destination.getScheme())
          && !"https".equalsIgnoreCase(destination.getScheme())) {
        return ResponseEntity.badRequest().build();
      }
      return ResponseEntity.status(HttpStatus.FOUND).location(destination).build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  private void markOpened(String token) {
    messageLogs.findByTrackingToken(token).ifPresent(this::markOpened);
  }

  private void markOpened(MessageLog entry) {
    if (entry.openedAt != null) return;
    entry.openedAt = LocalDateTime.now();
    if (entry.status == MessageLogStatus.DELIVERED || entry.status == MessageLogStatus.SENT) {
      entry.status = MessageLogStatus.READ;
    }
    if (entry.campaignId != null) {
      campaigns.findById(entry.campaignId).ifPresent(c -> {
        c.openedCount = c.openedCount + 1;
        campaigns.save(c);
      });
    }
    messageLogs.save(entry);
  }
}