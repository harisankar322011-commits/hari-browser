package com.haricalcx.haribrowser;

import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Runs the bundled Mozilla Readability parser on a clone of the currently displayed page.
 * Only bounded plain-text article metadata is returned to native UI; extracted HTML is never rendered.
 */
final class ReaderArticleExtractor {
    static final int MAX_TEXT_CHARS = 60_000;
    static final int MAX_TITLE_CHARS = 240;
    static final int MAX_BYLINE_CHARS = 180;
    static final int MAX_LIBRARY_BYTES = 120_000;
    private static final int MIN_TEXT_CHARS = 32;
    private static final String LIBRARY_ASSET = "readability/Readability.js";

    static final class Article {
        final String title;
        final String byline;
        final String text;
        final String url;
        final String language;
        final String direction;

        Article(String title, String byline, String text, String url,
                String language, String direction) {
            this.title = title;
            this.byline = byline;
            this.text = text;
            this.url = url;
            this.language = language;
            this.direction = direction;
        }
    }

    private ReaderArticleExtractor() {}

    static String loadLibrary(AssetManager assets) throws IOException {
        try (InputStream input = assets.open(LIBRARY_ASSET);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_LIBRARY_BYTES) {
                    throw new IOException("The bundled article reader is larger than expected.");
                }
                output.write(buffer, 0, count);
            }
            String source = new String(output.toByteArray(), StandardCharsets.UTF_8);
            if (source.trim().isEmpty() || !source.contains("function Readability")) {
                throw new IOException("The bundled article reader could not be validated.");
            }
            return source;
        }
    }

    static String buildExtractionScript(String readabilitySource) {
        if (readabilitySource == null || readabilitySource.trim().isEmpty()
                || readabilitySource.length() > MAX_LIBRARY_BYTES) {
            throw new IllegalArgumentException("A valid bundled Readability source is required.");
        }

        StringBuilder script = new StringBuilder(readabilitySource.length() + 1800);
        // Keep the parser and its optional CommonJS export local; do not add globals to the website.
        script.append("(function(){try{var module;\n").append(readabilitySource).append("\n")
                .append("var article=new Readability(document.cloneNode(true),")
                .append("{debug:false,maxElemsToParse:40000,charThreshold:300,disableJSONLD:true}).parse();")
                .append("if(!article)return null;")
                .append("var parsed=new DOMParser().parseFromString(article.content||'', 'text/html');")
                .append("var body=parsed.body;")
                .append("var selector='h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,figcaption,td,th';")
                .append("var nodes=body.querySelectorAll(selector);var blocks=[];")
                .append("for(var i=0;i<nodes.length;i++){var node=nodes[i],parent=node.parentElement,inside=false;")
                .append("while(parent&&parent!==body){if(parent.matches&&parent.matches(selector)){inside=true;break;}")
                .append("parent=parent.parentElement;}if(inside)continue;")
                .append("var value=(node.innerText||node.textContent||'').replace(/[ \\t]+/g,' ').trim();")
                .append("if(value)blocks.push(value);}")
                .append("var text=blocks.join('\\n\\n');")
                .append("if(text.trim().length<").append(MIN_TEXT_CHARS)
                .append(")text=article.textContent||'';")
                .append("text=text.replace(/\\u00a0/g,' ').replace(/\\r\\n?/g,'\\n').trim();")
                .append("if(text.length>").append(MAX_TEXT_CHARS)
                .append(")text=text.slice(0,").append(MAX_TEXT_CHARS).append(");")
                .append("if(text.length<").append(MIN_TEXT_CHARS).append(")return null;")
                .append("return JSON.stringify({title:article.title||document.title||'',")
                .append("byline:article.byline||'',text:text,url:location.href,")
                .append("lang:article.lang||document.documentElement.lang||'',dir:article.dir||''});")
                .append("}catch(error){return null;}})();");
        return script.toString();
    }

    static Article parseJavaScriptResult(String encodedResult) {
        if (encodedResult == null || encodedResult.length() > MAX_TEXT_CHARS * 2 + 4096) return null;

        try {
            JSONArray wrapper = new JSONArray("[" + encodedResult + "]");
            Object raw = wrapper.opt(0);
            if (!(raw instanceof String)) return null;
            JSONObject json = new JSONObject((String) raw);
            String title = cleanSingleLine(json.optString("title", ""), MAX_TITLE_CHARS);
            String byline = cleanSingleLine(json.optString("byline", ""), MAX_BYLINE_CHARS);
            String text = cleanArticleText(json.optString("text", ""), MAX_TEXT_CHARS);
            String url = json.optString("url", "").trim();
            String language = cleanSingleLine(json.optString("lang", ""), 35);
            String direction = json.optString("dir", "");
            if (!isSafeHttpsUrl(url) || text.length() < MIN_TEXT_CHARS) return null;
            if (title.isEmpty()) title = safeHost(url);
            if (!"rtl".equals(direction) && !"ltr".equals(direction) && !"auto".equals(direction)) {
                direction = "";
            }
            return new Article(title, byline, text, url, language, direction);
        } catch (JSONException invalid) {
            return null;
        }
    }

    static boolean isSafeHttpsUrl(String value) {
        if (value == null || value.isEmpty() || value.length() > 8192) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isEmpty()
                    && uri.getRawUserInfo() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String cleanSingleLine(String value, int maximum) {
        if (value == null) return "";
        String clean = value.replace('\u0000', ' ').replace('\r', ' ').replace('\n', ' ')
                .replaceAll("[\\t ]+", " ").trim();
        if (clean.length() > maximum) clean = clean.substring(0, maximum);
        return removeTrailingHighSurrogate(clean);
    }

    private static String cleanArticleText(String value, int maximum) {
        if (value == null) return "";
        String clean = value.replace('\u0000', ' ').replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n").trim();
        if (clean.length() > maximum) clean = clean.substring(0, maximum);
        return removeTrailingHighSurrogate(clean);
    }

    private static String removeTrailingHighSurrogate(String value) {
        if (!value.isEmpty() && Character.isHighSurrogate(value.charAt(value.length() - 1))) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String safeHost(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost() == null ? "Article" : uri.getHost();
        } catch (Exception ignored) {
            return "Article";
        }
    }
}
