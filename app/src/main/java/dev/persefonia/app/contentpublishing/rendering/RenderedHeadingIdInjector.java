package dev.persefonia.app.contentpublishing.rendering;

import dev.persefonia.contentpublishing.application.rendering.MarkdownRenderingException;
import dev.persefonia.contentpublishing.domain.content.RenderedHeading;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

final class RenderedHeadingIdInjector {
    String inject(String sanitizedHtml, List<RenderedHeading> headings) {
        try {
            Document document = Jsoup.parseBodyFragment(sanitizedHtml);
            document.outputSettings().prettyPrint(false);
            List<Element> elements = document.select("h1, h2, h3, h4, h5, h6");
            if (elements.size() != headings.size()) {
                throw new MarkdownRenderingException("Rendered heading count does not match extracted headings");
            }

            Set<String> anchors = new HashSet<>();
            for (int index = 0; index < elements.size(); index++) {
                Element element = elements.get(index);
                String anchor = headings.get(index).anchor().value();
                if (!anchors.add(anchor)) {
                    throw new MarkdownRenderingException("Rendered heading anchors must be unique");
                }
                if (element.hasAttr("id") && !element.id().equals(anchor)) {
                    throw new MarkdownRenderingException("Rendered heading contains an unexpected id");
                }
                element.attr("id", anchor);
            }
            return document.body().html();
        } catch (MarkdownRenderingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MarkdownRenderingException("Rendered heading id injection failed", exception);
        }
    }
}
