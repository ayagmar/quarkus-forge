package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

public final class MetadataSnapshotLoader {
  private static final String SNAPSHOT_RESOURCE = "metadata/platform-metadata.json";

  private MetadataSnapshotLoader() {}

  public static MetadataDto loadDefault() {
    try (InputStream inputStream =
        MetadataSnapshotLoader.class.getClassLoader().getResourceAsStream(SNAPSHOT_RESOURCE)) {
      if (inputStream == null) {
        throw new ApiContractException("Missing metadata snapshot resource: " + SNAPSHOT_RESOURCE);
      }
      String payload = new String(inputStream.readAllBytes());
      return QuarkusApiClient.parseMetadataPayload(payload, new ObjectMapper());
    } catch (IOException ioException) {
      throw new ApiContractException("Failed to read metadata snapshot resource", ioException);
    }
  }
}
