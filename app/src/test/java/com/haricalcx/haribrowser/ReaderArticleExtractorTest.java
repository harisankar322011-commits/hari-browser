package com.haricalcx.haribrowser;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public final class ReaderArticleExtractorTest {
    private static String result(String url, String text) throws Exception {
        return JSONObject.quote(new JSONObject()
                .put("title", "  A useful article  ")
                .put("byline", " Author Name ")
                .put("text", text)
                .put("url", url)
                .put("lang", "en")
                .put("dir", "ltr")
                .toString());
    }

    @Test
    public void decodesArticleResultWithoutAcceptingHtmlAsMarkup() throws Exception {
        String text = "First paragraph.\n\nSecond paragraph with <script>text</script> shown literally.";
        ReaderArticleExtractor.Article article = ReaderArticleExtractor.parseJavaScriptResult(
                result("https://example.com/story", text));
        assertNotNull(article);
        assertEquals("A useful article", article.title);
        assertEquals("Author Name", article.byline);
        assertTrue(article.text.contains("<script>text</script>"));
        assertEquals("https://example.com/story", article.url);
    }

    @Test
    public void rejectsMalformedEmptyAndNonHttpsResults() throws Exception {
        assertNull(ReaderArticleExtractor.parseJavaScriptResult("undefined"));
        assertNull(ReaderArticleExtractor.parseJavaScriptResult(JSONObject.quote("not-json")));
        assertNull(ReaderArticleExtractor.parseJavaScriptResult(result("http://example.com/story",
                "This is a sufficiently long readable article text that should not be accepted from HTTP.")));
        assertNull(ReaderArticleExtractor.parseJavaScriptResult(result("https://example.com/story", "  ")));
    }

    @Test
    public void boundsContentAndMetadata() throws Exception {
        String text = "x".repeat(ReaderArticleExtractor.MAX_TEXT_CHARS + 50);
        ReaderArticleExtractor.Article article = ReaderArticleExtractor.parseJavaScriptResult(
                result("https://example.com/story", text));
        assertNotNull(article);
        assertEquals(ReaderArticleExtractor.MAX_TEXT_CHARS, article.text.length());
        assertTrue(article.title.length() <= ReaderArticleExtractor.MAX_TITLE_CHARS);
        assertTrue(article.byline.length() <= ReaderArticleExtractor.MAX_BYLINE_CHARS);
    }

    @Test
    public void extractionScriptClonesDocumentAndReturnsPlainTextFields() {
        String script = ReaderArticleExtractor.buildExtractionScript("function Readability() {}");
        assertTrue(script.contains("document.cloneNode(true)"));
        assertTrue(script.contains("new Readability"));
        assertTrue(script.contains("DOMParser"));
        assertTrue(script.contains("JSON.stringify"));
        assertTrue(script.contains("article.textContent"));
        assertFalse(script.contains("document.body.innerHTML ="));
    }
}
