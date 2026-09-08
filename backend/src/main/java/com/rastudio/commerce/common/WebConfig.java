package com.rastudio.commerce.common;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final String uploadsDir;

  public WebConfig(@Value("${app.uploads-dir:./uploads}") String uploadsDir) {
    this.uploadsDir = uploadsDir;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String location = Path.of(uploadsDir).toAbsolutePath().normalize().toUri().toString();
    registry.addResourceHandler("/uploads/**").addResourceLocations(location);
  }
}
