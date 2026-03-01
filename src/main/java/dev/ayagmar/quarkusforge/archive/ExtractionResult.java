package dev.ayagmar.quarkusforge.archive;

import java.nio.file.Path;

public record ExtractionResult(Path extractedRoot, int entryCount, long extractedBytes) {}
