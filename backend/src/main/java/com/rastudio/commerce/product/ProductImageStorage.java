package com.rastudio.commerce.product;

import com.rastudio.commerce.common.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/*
 * Minimal local-filesystem image storage for product photos. Files are
 * saved under app.uploads-dir/products with a random opaque filename (never
 * the original filename or any tenant/product id) and served back publicly
 * via /uploads/products/** (see WebConfig) — product photos are not
 * sensitive data, and this keeps Phase 6 self-contained without requiring a
 * cloud object-storage integration that isn't specified anywhere in the
 * roadmap. product.image_path stores the public path, e.g.
 * "/uploads/products/<uuid>.jpg".
 */
@Component
public class ProductImageStorage {

  private final Path root;

  public ProductImageStorage(@Value("${app.uploads-dir:./uploads}") String uploadsDir) {
    this.root = Path.of(uploadsDir, "products").toAbsolutePath().normalize();
    try {
      Files.createDirectories(root);
    } catch (IOException e) {
      throw new IllegalStateException("Could not create uploads directory: " + root, e);
    }
  }

  public String store(MultipartFile file) {
    String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
    String ext = "";
    int dot = original.lastIndexOf('.');
    if (dot >= 0 && dot < original.length() - 1) ext = original.substring(dot).toLowerCase();
    if (!ext.matches("\\.[a-z0-9]{2,5}")) ext = "";
    String filename = UUID.randomUUID() + ext;
    Path target = root.resolve(filename).normalize();
    if (!target.startsWith(root)) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid file name");
    }
    try {
      file.transferTo(target);
    } catch (IOException e) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_STORE_FAILED", "Could not save the uploaded image");
    }
    return "/uploads/products/" + filename;
  }

  public void delete(String imagePath) {
    if (imagePath == null || !imagePath.startsWith("/uploads/products/")) return;
    String filename = imagePath.substring("/uploads/products/".length());
    Path target = root.resolve(filename).normalize();
    if (!target.startsWith(root)) return;
    try {
      Files.deleteIfExists(target);
    } catch (IOException ignored) {
      // best-effort cleanup; not worth failing the request over
    }
  }
}
