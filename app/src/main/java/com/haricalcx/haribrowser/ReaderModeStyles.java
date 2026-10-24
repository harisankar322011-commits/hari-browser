package com.haricalcx.haribrowser;

import org.json.JSONObject;

/**
 * Local text-only reader extraction for the current WebView document.
 *
 * The extractor chooses a likely article/main container, copies only visible text,
 * and builds a fresh DOM with textContent (never page-provided HTML). It intentionally
 * does not execute a remote readability library or download reader assets. Extraction
 * is heuristic: some sites will yield extra text or no useful article.
 */
final class ReaderModeStyles {
    static final String CSS =
            "html{background:#fff!important;color:#172033!important}" +
            "body{box-sizing:border-box!important;width:auto!important;max-width:780px!important;" +
            "margin:0 auto!important;padding:clamp(16px,4vw,28px)!important;" +
            "font-family:system-ui,-apple-system,BlinkMacSystemFont,sans-serif!important;" +
            "font-size:19px!important;line-height:1.8!important;overflow-wrap:break-word!important;" +
            "word-break:normal!important;background:#fff!important;color:#172033!important}" +
            "body *{font-family:inherit!important;line-height:inherit!important;max-width:100%!important}" +
            "#hari-reader-article{margin:0 auto!important;max-width:780px!important}" +
            "#hari-reader-article h1{font-size:1.5em!important;line-height:1.3!important;margin:0 0 1.2em!important}" +
            "#hari-reader-text p{white-space:pre-wrap!important;margin:.8em 0!important}" +
            "img,video,iframe{max-width:100%!important;height:auto!important}";

    private ReaderModeStyles() {}

    /**
     * Extracts a bounded text-only article view and applies a local stylesheet.
     * All extracted content is inserted through textContent to prevent markup/script injection.
     */
    static String documentStyleScript() {
        return "(function(){try{" +
                "if(!document.body||document.documentElement.getAttribute('data-hari-reader-active')==='1')return;" +
                "var selectors=['article','main','[role=main]','.article-content','.post-content','.entry-content'];" +
                "var candidates=[];" +
                "selectors.forEach(function(sel){try{document.querySelectorAll(sel).forEach(function(el){" +
                "var t=(el.innerText||el.textContent||'').trim();" +
                "if(t.length>=180&&t.length<=120000){" +
                "var p=el.querySelectorAll('p').length;" +
                "var clutter=el.querySelectorAll('nav,aside,[role=navigation]').length;" +
                "var score=Math.min(t.length,50000)+Math.min(p,100)*120+(/ARTICLE/i.test(el.tagName)?700:0)-clutter*500;" +
                "candidates.push({text:t,score:score});}});}catch(ignore){}});" +
                "candidates.sort(function(a,b){return b.score-a.score;});" +
                "var source=candidates.length?candidates[0].text:(document.body.innerText||document.body.textContent||'');" +
                "source=source.replace(/\\r/g,'').replace(/[ \\t]+\\n/g,'\\n').replace(/\\n{3,}/g,'\\n\\n').trim().slice(0,60000);" +
                "if(!source)source='No readable article text could be extracted from this page.';" +
                "var title=(document.title||'Article').trim().slice(0,240)||'Article';" +
                "document.querySelectorAll('script,iframe,object,embed,form,link[rel=stylesheet],style,meta[http-equiv=refresh]').forEach(function(n){n.remove();});" +
                "var article=document.createElement('article');article.id='hari-reader-article';" +
                "var heading=document.createElement('h1');heading.textContent=title;article.appendChild(heading);" +
                "var text=document.createElement('div');text.id='hari-reader-text';" +
                "source.split(/\\n\\s*\\n/).forEach(function(block){" +
                "block=block.trim();if(!block)return;var p=document.createElement('p');p.textContent=block.slice(0,4000);text.appendChild(p);});" +
                "article.appendChild(text);document.body.replaceChildren(article);" +
                "document.documentElement.setAttribute('data-hari-reader-active','1');" +
                "var s=document.createElement('style');s.id='hari-reader-style';s.textContent=" + JSONObject.quote(CSS) + ";" +
                "(document.head||document.documentElement).appendChild(s);" +
                "}catch(e){}})();";
    }
}
