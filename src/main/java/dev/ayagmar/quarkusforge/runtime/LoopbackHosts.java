package dev.ayagmar.quarkusforge.runtime;

import java.net.InetAddress;
import java.util.List;
import java.util.Locale;

final class LoopbackHosts {
  private LoopbackHosts() {}

  static boolean isLoopbackHost(String host) {
    String normalizedHost = host.toLowerCase(Locale.ROOT);
    if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
      normalizedHost = normalizedHost.substring(1, normalizedHost.length() - 1);
    }
    return normalizedHost.equals("localhost")
        || normalizedHost.equals("::1")
        || isIpv4LoopbackHost(normalizedHost)
        || resolvesToLoopbackAddress(normalizedHost);
  }

  static boolean isIpv4LoopbackHost(String host) {
    List<String> octets = List.of(host.split("\\.", -1));
    if (octets.size() != 4 || !octets.getFirst().equals("127")) {
      return false;
    }
    for (int index = 1; index < octets.size(); index++) {
      if (!isIpv4Octet(octets.get(index))) {
        return false;
      }
    }
    return true;
  }

  static boolean isIpv4Octet(String value) {
    if (value.isEmpty()) {
      return false;
    }
    for (int index = 0; index < value.length(); index++) {
      if (!Character.isDigit(value.charAt(index))) {
        return false;
      }
    }
    try {
      int octet = Integer.parseInt(value);
      return octet >= 0 && octet <= 255;
    } catch (NumberFormatException ignored) {
      return false;
    }
  }

  private static boolean resolvesToLoopbackAddress(String host) {
    if (!host.contains(":")) {
      return false;
    }
    try {
      return InetAddress.getByName(host).isLoopbackAddress();
    } catch (RuntimeException | java.net.UnknownHostException ignored) {
      return false;
    }
  }
}
