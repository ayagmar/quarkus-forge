package dev.ayagmar.quarkusforge.headless;

import dev.ayagmar.quarkusforge.api.CatalogData;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

interface HeadlessCatalogLoader extends AutoCloseable {
  CatalogData loadCatalogData(Duration timeout)
      throws ExecutionException, InterruptedException, TimeoutException;

  Map<String, List<String>> loadBuiltInPresets(String platformStream, Duration timeout)
      throws ExecutionException, InterruptedException, TimeoutException;

  @Override
  void close();
}
