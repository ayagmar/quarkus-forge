package dev.ayagmar.quarkusforge.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MetadataSnapshotLoader {
  private static final String SNAPSHOT_RESOURCE = "metadata/platform-metadata.json";

  private MetadataSnapshotLoader() {}

  public static MetadataDto loadDefault() {
    try (InputStream inputStream =
        MetadataSnapshotLoader.class.getClassLoader().getResourceAsStream(SNAPSHOT_RESOURCE)) {
      if (inputStream == null) {
        throw new ApiContractException("Missing metadata snapshot resource: " + SNAPSHOT_RESOURCE);
      }
      String payload = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      return QuarkusApiClient.parseMetadataPayload(payload, ObjectMapperProvider.shared());
    } catch (IOException ioException) {
      throw new ApiContractException("Failed to read metadata snapshot resource", ioException);
    }
  }
}
