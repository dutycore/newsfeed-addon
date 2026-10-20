package dk.dutyc0re.newsfeed.core.news;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/** Safe XML parsing for feeds (no DTDs, no external entities). */
final class Xml {

  private Xml() {
  }

  /**
   * Parses raw bytes so the parser detects the encoding itself (minecraft.net sends UTF-16
   * without a byte order mark).
   */
  static Document parse(byte[] body) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setExpandEntityReferences(false);
      return factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
    } catch (ParserConfigurationException | SAXException exception) {
      throw new IOException("Could not parse feed", exception);
    }
  }
}
