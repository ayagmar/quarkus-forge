/**
 * Shared orchestration helpers between CLI and headless flows.
 *
 * <p>This package converts boundary inputs into domain requests and owns reusable startup-state
 * policy, including live-metadata refresh fallback behavior, without depending on Picocli or the
 * TUI.
 */
package dev.ayagmar.quarkusforge.application;
