package org.apache.maven.plugins.checkstyle;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

public final class CheckstyleMojo extends AbstractMojo {
  @Override
  public void execute() throws MojoExecutionException {
    getLog().info("Offline checkstyle stub executed (no-op). ");
  }
}
