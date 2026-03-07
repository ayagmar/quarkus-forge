package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class NativeBuildConfigurationTest {

  @Test
  void nativeProfileEnablesSharedArenaSupport() throws Exception {
    Document document = parsePom(Path.of("pom.xml"));

    Element nativeProfile = profileById(document, "native");
    NodeList buildArgs = nativeProfile.getElementsByTagName("buildArg");

    assertThat(textContents(buildArgs)).contains("-H:+SharedArenaSupport");
  }

  private static Document parsePom(Path pomPath) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    factory.setNamespaceAware(false);
    try (InputStream inputStream = Files.newInputStream(pomPath)) {
      return factory.newDocumentBuilder().parse(inputStream);
    }
  }

  private static Element profileById(Document document, String profileId) throws IOException {
    NodeList profiles = document.getElementsByTagName("profile");
    for (int i = 0; i < profiles.getLength(); i++) {
      Element profile = (Element) profiles.item(i);
      NodeList ids = profile.getElementsByTagName("id");
      if (ids.getLength() > 0 && profileId.equals(ids.item(0).getTextContent().trim())) {
        return profile;
      }
    }
    throw new IOException("profile not found: " + profileId);
  }

  private static java.util.List<String> textContents(NodeList nodeList) {
    java.util.ArrayList<String> values = new java.util.ArrayList<>();
    for (int i = 0; i < nodeList.getLength(); i++) {
      values.add(nodeList.item(i).getTextContent().trim());
    }
    return values;
  }
}
