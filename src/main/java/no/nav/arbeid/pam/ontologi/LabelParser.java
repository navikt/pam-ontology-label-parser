package no.nav.arbeid.pam.ontologi;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.Iterator;

public class LabelParser {

    private long nodeId  = 4000001;

    public LabelParser() {
    }

    public static void main(String[] args) {
        try {
            new LabelParser().parseLabels(args[0]);
        } catch( Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void parseLabels(String directory) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (
                FileReader reader = new FileReader(directory + "/labels.jsonl");
                BufferedReader bufferedReader = new BufferedReader(reader);
                PrintWriter writer = new PrintWriter(directory + "/labels.graphml", "UTF-8");
                PrintWriter writer2 = new PrintWriter(directory + "/labels-hookup.cypher", "UTF-8");
                PrintWriter writer3 = new PrintWriter(directory + "/labels-hookup.graphml", "UTF-8");
        ) {
            writer.println("<?xml version='1.0' encoding='utf-8'?>");
            writer.println("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">");
            writer.println("<key attr.name=\"lang\" attr.type=\"string\" for=\"node\" id=\"d0\" />");
            writer.println("<key attr.name=\"tag\" attr.type=\"string\" for=\"node\" id=\"d1\" />");
            writer.println("<key attr.name=\"value\" attr.type=\"string\" for=\"node\" id=\"d2\" />");
            writer.println("<key attr.name=\"labels\" attr.type=\"string\" for=\"node\" id=\"d3\" />");
            writer.println("<graph edgedefault=\"directed\">");

            writer3.println("<?xml version='1.0' encoding='utf-8'?>");
            writer3.println("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">");
            writer3.println("<key attr.name=\"rel\" attr.type=\"string\" for=\"edge\" id=\"d0\" />");
            writer3.println("<graph edgedefault=\"directed\">");

            String currentLine;
            while ((currentLine = bufferedReader.readLine()) != null) {
                JsonNode node = mapper.readTree(currentLine);

                if (!node.isArray()) {
                    throw new RuntimeException("A JSON Array is expected");
                }

                Iterator<JsonNode> it = node.iterator();

                int id = it.next().asInt();
                JsonNode object = it.next();
                JsonNode en = object.get("en");
                parseNode(id, "en", en, writer, writer2, writer3);
                JsonNode nn = object.get("nn");
                parseNode(id, "nn", nn, writer, writer2, writer3);
                JsonNode no = object.get("no");
                parseNode(id, "no", no, writer, writer2, writer3);
            }
            writer.println("</graph>");
            writer.println("</graphml>");

            writer3.println("</graph>");
            writer3.println("</graphml>");
            System.out.println("FERDIG!");
        }
    }

    private void parseNode(int id, String lang, JsonNode node, PrintWriter writer, PrintWriter writer2, PrintWriter writer3) {
        Iterator<JsonNode> iterator = node.iterator();
        if (iterator.hasNext()) {
            Iterator<JsonNode> anotherIterator = node.iterator();
            while (anotherIterator.hasNext()) {
                JsonNode label = anotherIterator.next();
                Iterator<JsonNode> labelIterator = label.iterator();
                String tag = labelIterator.next().asText();
                String value = labelIterator.next().asText();
                writer.printf("<node id=\"%d\">\n", nodeId);
                writer.printf("  <data key=\"d0\">%s</data>\n", lang);
                writer.printf("  <data key=\"d1\">%s</data>\n", tag);
                writer.printf("  <data key=\"d2\">%s</data>\n", encodeXML(value));
                writer.println("  <data key=\"d3\">TERM</data>");
                writer.println("</node>");

                writer3.printf("<edge source=\"%d\" target=\"%d\">\n", id, nodeId);
                writer3.printf("  <data key=\"d0\">HAS_TERM</data>\n");
                writer3.printf("</edge>\n");

                writer2.printf("MATCH (a:HASLABEL),(b:TERM)\n");
                writer2.printf("WHERE a.id = '%d' AND b.id = '%d'\n", id, nodeId++);
                writer2.printf("CREATE (a)-[r:HAS_TERM]->(b)\n");
                writer2.println("RETURN type(r);\n");

            }

        }
    }

    public static String encodeXML(CharSequence s) {
        StringBuilder sb = new StringBuilder();
        int len = s.length();
        for (int i=0;i<len;i++) {
            int c = s.charAt(i);
            if (c >= 0xd800 && c <= 0xdbff && i + 1 < len) {
                c = ((c-0xd7c0)<<10) | (s.charAt(++i)&0x3ff);    // UTF16 decode
            }
            if (c < 0x80) {      // ASCII range: test most common case first
                if (c < 0x20 && (c != '\t' && c != '\r' && c != '\n')) {
                    // Illegal XML character, even encoded. Skip or substitute
                    sb.append("&#xfffd;");   // Unicode replacement character
                } else {
                    switch(c) {
                        case '&':  sb.append("&amp;"); break;
                        case '>':  sb.append("&gt;"); break;
                        case '<':  sb.append("&lt;"); break;
                        // Uncomment next two if encoding for an XML attribute
//                  case '\''  sb.append("&apos;"); break;
//                  case '\"'  sb.append("&quot;"); break;
                        // Uncomment next three if you prefer, but not required
//                  case '\n'  sb.append("&#10;"); break;
//                  case '\r'  sb.append("&#13;"); break;
//                  case '\t'  sb.append("&#9;"); break;

                        default:   sb.append((char)c);
                    }
                }
            } else if ((c >= 0xd800 && c <= 0xdfff) || c == 0xfffe || c == 0xffff) {
                // Illegal XML character, even encoded. Skip or substitute
                sb.append("&#xfffd;");   // Unicode replacement character
            } else {
                sb.append("&#x");
                sb.append(Integer.toHexString(c));
                sb.append(';');
            }
        }
        return sb.toString();
    }
}