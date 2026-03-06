package dev.ayagmar.quarkusforge;

import java.nio.file.Path;

/**
 * @deprecated Use {@link dev.ayagmar.quarkusforge.forge.ForgefileStore} instead.
 */
@Deprecated
public final class ForgefileStore {
  private ForgefileStore() {}

  public static Forgefile load(Path file) {
    return Forgefile.from(dev.ayagmar.quarkusforge.forge.ForgefileStore.load(file));
  }

  public static void save(Path file, Forgefile forgefile) {
    dev.ayagmar.quarkusforge.forge.ForgefileStore.save(file, forgefile.toForgefile());
  }
}
