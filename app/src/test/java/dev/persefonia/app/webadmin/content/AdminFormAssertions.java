package dev.persefonia.app.webadmin.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

final class AdminFormAssertions {
    private AdminFormAssertions() {}

    static Document accessibleDocument(String html) {
        Document document = Jsoup.parse(html);
        assertThat(document.select("[id]").eachAttr("id")).doesNotHaveDuplicates();
        for (var link : document.select(".field-errors a[href^='#']")) {
            String id = link.attr("href").substring(1);
            assertThat(document.select("[id='" + id + "']")).as(link.outerHtml()).hasSize(1);
        }
        for (var element : document.select("[aria-describedby]")) {
            for (String id : element.attr("aria-describedby").split("\\s+")) {
                assertThat(document.select("[id='" + id + "']")).as(element.outerHtml()).hasSize(1);
            }
        }
        return document;
    }

    static void fieldError(Document document, String field) {
        assertThat(document.select(".field-errors a[href='#" + field + "']")).hasSize(1);
        var control = document.getElementById(field);
        assertThat(control).isNotNull();
        assertThat(control.attr("aria-invalid")).isEqualTo("true");
        assertThat(control.attr("aria-describedby")).isEqualTo("error-" + field);
        assertThat(document.getElementById("error-" + field).text()).isNotBlank();
    }
}
