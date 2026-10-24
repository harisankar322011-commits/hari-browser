package com.haricalcx.haribrowser;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Offline-first, self-contained start page. No remote scripts, fonts, pixels or images are loaded.
 * External destinations are opened only after the user taps them.
 */
final class HomePage {
    private HomePage() {}

    private static volatile String cachedDirectorySource;


    static String render(boolean dark, String engine, String bookmarkJson, boolean privateMode) {
        return renderWithDirectory("", dark, engine, bookmarkJson, privateMode, "Ocean", "en");
    }

    static String render(Context context, boolean dark, String engine, String bookmarkJson, boolean privateMode,
                         String accentName, String language) {
        return renderWithDirectory(readDirectoryAsset(context), dark, engine, bookmarkJson, privateMode,
                accentName, language);
    }

    static String render(boolean dark, String engine, String bookmarkJson, boolean privateMode,
                         String accentName, String language) {
        // Pure JVM tests use a tiny empty directory; the Android entry point loads the bundled asset.
        return renderWithDirectory("", dark, engine, bookmarkJson, privateMode, accentName, language);
    }

    private static String renderWithDirectory(String directorySource, boolean dark, String engine,
                                              String bookmarkJson, boolean privateMode,
                                              String accentName, String language) {
        String accent = "#2563eb";
        String accentText = "#ffffff";
        if ("Violet".equals(accentName)) accent = "#7c3aed";
        else if ("Mint".equals(accentName)) accent = "#059669";
        else if ("Sunset".equals(accentName)) accent = "#ea580c";
        else if ("Rose".equals(accentName)) accent = "#e11d48";
        String theme = dark
                ? "--bg:#080c19;--surface:#12192d;--surface2:#1a2540;--text:#eef3ff;--muted:#9aa9c5;--line:#2b3751;--shadow:0 18px 52px #0005"
                : "--bg:#f6f7ff;--surface:#ffffff;--surface2:#edf0ff;--text:#161f37;--muted:#657189;--line:#dce0ef;--shadow:0 18px 52px #25396515";
        theme += ";--accent:" + accent + ";--accentText:" + accentText;
        String linksJson = directoryJson(directorySource);
        String savedJson = bookmarkJson == null || bookmarkJson.trim().isEmpty() ? "[]" : forScript(bookmarkJson);
        String selectedEngine = JSONObject.quote(engine == null ? "DuckDuckGo" : engine);
        String mode = privateMode ? "PRIVATE SESSION" : "NORMAL SESSION";
        String selectedLanguage = JSONObject.quote(language == null ? "en" : language);

        StringBuilder htmlBuilder = new StringBuilder();
    htmlBuilder.append("""
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="color-scheme" content="light dark">
<title>Hari Browser — Home</title>
<style>
:root{THEME}
*{box-sizing:border-box}
html{scroll-behavior:smooth}
body{margin:0;background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,Roboto,Arial,sans-serif;font-size:14px;line-height:1.5}
button,input,select,textarea{font:inherit}
button,a,select{touch-action:manipulation}
button{cursor:pointer}
a{color:inherit}
.shell{max-width:1040px;margin:0 auto;padding:18px 15px 30px}
.top{display:flex;gap:12px;align-items:center;margin:3px 0 20px}
.logo{width:48px;height:48px;flex:0 0 48px;display:grid;place-items:center;background:linear-gradient(145deg,#7c3aed,#2563eb 58%,#06b6d4);border-radius:17px;color:white;box-shadow:0 10px 26px #4f46e544;position:relative;overflow:hidden}
 .logo:after{content:"";position:absolute;inset:5px;border:1px solid #ffffff66;border-radius:13px;pointer-events:none}
 .logo svg{width:34px;height:34px;z-index:1;filter:drop-shadow(0 2px 3px #0003)}
 .language-select{max-width:116px;margin-left:8px;border:1px solid var(--line);border-radius:11px;background:var(--surface);color:var(--text);padding:7px 8px;font-size:11px;font-weight:750}
 .creator-card{margin-top:18px;background:linear-gradient(135deg,var(--surface),var(--surface2));border-color:var(--line)}
 .creator-head{display:flex;align-items:flex-start;gap:10px}.creator-mark{width:38px;height:38px;display:grid;place-items:center;flex:0 0 38px;border-radius:13px;background:var(--accent);color:var(--accentText);font-weight:950;font-size:19px}
 .creator-head h3{margin:0 0 4px;font-size:15px}.creator-head p{margin:0;color:var(--muted);font-size:12px}
 .project-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin:12px 0}
 .project-link{display:flex;align-items:center;gap:9px;min-width:0;border:1px solid var(--line);border-radius:12px;background:var(--surface);padding:10px;text-decoration:none}
 .project-icon{width:31px;height:31px;flex:0 0 31px;display:grid;place-items:center;border-radius:10px;background:var(--surface2);font-size:16px}
 .project-link strong{display:block;font-size:11px;line-height:1.25}.project-link small{display:block;font-size:10px;color:var(--muted);margin-top:3px;overflow-wrap:anywhere}
 .creator-social{display:flex;align-items:center;gap:9px;background:var(--surface);border:1px solid var(--line);border-radius:13px;padding:12px;text-decoration:none;font-weight:850;font-size:12px}
 .creator-social .social-icon{width:32px;height:32px;display:grid;place-items:center;background:linear-gradient(135deg,#833ab4,#fd1d1d,#fcb045);color:white;border-radius:10px;font-size:18px}
 .creator-social small{display:block;color:var(--muted);font-size:10px;font-weight:500;margin-top:3px}
 @media(max-width:365px){.language-select{max-width:90px;padding:7px 5px}.top{gap:8px}.brand{letter-spacing:1px}.mode{padding:6px 7px;font-size:9px}}
.brand{font-size:14px;font-weight:900;letter-spacing:1.8px}
.brand-sub{color:var(--muted);font-size:11px;margin-top:1px}
.mode{margin-left:auto;font-size:10px;font-weight:800;letter-spacing:.5px;background:var(--surface2);border:1px solid var(--line);padding:7px 10px;border-radius:100px;white-space:nowrap}
.hero{border:1px solid var(--line);background:linear-gradient(130deg,var(--surface),var(--surface2));box-shadow:var(--shadow);border-radius:24px;padding:23px 18px 18px;overflow:hidden;position:relative}
.eyebrow{color:var(--accent);font-size:10px;font-weight:900;letter-spacing:2px;text-transform:uppercase}
h1{font-size:clamp(27px,6vw,43px);line-height:1.09;letter-spacing:-1.2px;margin:10px 0 8px;max-width:610px}
h1 .accent{color:var(--accent)}
.sub{color:var(--muted);margin:0 0 18px;max-width:620px}
.search{display:flex;gap:8px;background:var(--surface);border:1.5px solid var(--line);padding:6px;border-radius:17px;align-items:center}
.search select{max-width:116px;min-width:86px;border:0;background:transparent;color:var(--text);padding:9px 2px;outline:none;font-size:12px}
.search input{min-width:0;flex:1;border:0;background:transparent;color:var(--text);outline:none;padding:10px 5px;font-size:15px}
.primary{border:0;background:var(--accent);color:var(--accentText);border-radius:12px;padding:11px 16px;font-weight:900;white-space:nowrap}
.ai-launch{display:flex;align-items:center;gap:12px;margin-top:10px;padding:12px 14px;border:1px solid color-mix(in srgb,var(--accent) 42%,var(--line));background:linear-gradient(115deg,var(--surface),var(--surface2));border-radius:16px;text-decoration:none;color:var(--text);box-shadow:0 6px 20px #4f46e411;transition:transform .12s,border-color .12s}
.ai-launch:active{transform:scale(.99)}.ai-launch:hover{border-color:var(--accent)}
.ai-launch-icon{display:grid;place-items:center;flex:0 0 40px;height:40px;border-radius:13px;background:var(--accent);color:var(--accentText);font-size:22px;font-weight:900}
.ai-launch-copy{min-width:0;flex:1}.ai-launch-copy strong{display:block;font-size:14px;line-height:1.25}.ai-launch-copy small{display:block;color:var(--muted);font-size:11px;margin-top:3px;line-height:1.35}
.ai-launch-open{font-size:11px;color:var(--accent);font-weight:900;white-space:nowrap}
.pills{display:flex;flex-wrap:wrap;gap:7px;margin-top:12px}
.pill{border:1px solid var(--line);background:var(--surface);padding:7px 10px;border-radius:99px;font-size:11px;color:var(--muted)}
.section-head{display:flex;gap:10px;align-items:center;justify-content:space-between;margin:23px 2px 12px}
h2{font-size:17px;letter-spacing:-.3px;margin:0}
.mini{font-size:11px;color:var(--muted)}
.btn{border:1px solid var(--line);background:var(--surface);color:var(--text);border-radius:11px;padding:8px 11px;font-weight:750;font-size:12px}
.grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}
.tile{display:flex;min-width:0;gap:10px;align-items:center;text-align:left;background:var(--surface);color:var(--text);border:1px solid var(--line);border-radius:16px;padding:12px;text-decoration:none;min-height:76px;transition:transform .12s,border-color .12s}
.tile:active{transform:scale(.98)}
.tile:hover{border-color:var(--accent)}
.icon{width:36px;height:36px;display:grid;place-items:center;flex:0 0 36px;border-radius:12px;background:var(--surface2);font-size:18px}
.tile strong{display:block;font-size:12px;line-height:1.25;overflow-wrap:anywhere}
.tile small{display:block;color:var(--muted);font-size:10px;line-height:1.3;margin-top:4px;overflow-wrap:anywhere}
.panelnav{display:grid;grid-template-columns:repeat(4,1fr);gap:7px;margin:16px 0 8px}
.navbtn{padding:10px 3px;border:1px solid var(--line);border-radius:12px;background:var(--surface);color:var(--muted);font-size:12px;font-weight:800}
.navbtn.active{background:var(--accent);color:var(--accentText);border-color:var(--accent)}
.panel{display:none}.panel.active{display:block}
.bookmark-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
.bookmark{display:flex;align-items:center;gap:9px;background:var(--surface);padding:11px;border-radius:12px;border:1px solid var(--line);text-decoration:none;min-width:0}
.bookmark .fav{font-size:19px}.bookmark strong{font-size:11px;display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.bookmark small{font-size:10px;color:var(--muted);display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.empty{padding:16px;text-align:center;border:1px dashed var(--line);border-radius:14px;color:var(--muted);font-size:12px;background:var(--surface)}
.categorybar{display:flex;gap:7px;overflow:auto;padding:2px 0 10px;scrollbar-width:thin}
.category{border:1px solid var(--line);background:var(--surface);color:var(--muted);border-radius:999px;padding:8px 11px;white-space:nowrap;font-size:11px;font-weight:800}
.category.active{background:var(--accent);border-color:var(--accent);color:var(--accentText)}
.directory-search{width:100%;padding:12px;border-radius:13px;border:1px solid var(--line);background:var(--surface);color:var(--text);margin-bottom:10px;outline-color:var(--accent)}
.directory-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px}
.directory-item{padding:11px;border:1px solid var(--line);border-radius:12px;background:var(--surface);text-decoration:none;min-width:0}
.directory-item b{display:block;font-size:12px;line-height:1.3}
.directory-item small{display:block;color:var(--muted);font-size:10px;margin-top:4px;overflow-wrap:anywhere}
.tool-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}
.tool{border:1px solid var(--line);background:var(--surface);color:var(--text);border-radius:15px;padding:13px;text-align:left;min-height:86px}
.tool .ti{font-size:21px}.tool strong{display:block;margin-top:4px;font-size:12px}.tool small{display:block;font-size:10px;color:var(--muted);margin-top:2px;line-height:1.3}
.card{border:1px solid var(--line);background:var(--surface);border-radius:16px;padding:15px;margin-top:10px}
.card h3{margin:0 0 6px;font-size:14px}
.card p{margin:4px 0;color:var(--muted);font-size:12px;line-height:1.55}
.actions{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
.action{display:block;text-decoration:none;border:1px solid var(--line);background:var(--surface);border-radius:13px;padding:12px;font-size:12px;font-weight:800}
.action span{display:block;color:var(--muted);font-size:10px;font-weight:500;margin-top:4px;line-height:1.35}
.localnote{font-size:11px;color:var(--muted);text-align:center;margin-top:22px;padding:0 10px}
/* Bundled offline wallpaper gallery */
.wallpaper-heading{display:flex;align-items:center;justify-content:space-between;gap:10px;margin:18px 2px 10px}
.wallpaper-heading h2{margin:0;font-size:16px}
.wallpaper-heading p{margin:2px 0 0;color:var(--muted);font-size:11px}
.wallpaper-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8px}
.wallpaper-choice{min-width:0;overflow:hidden;border:1px solid var(--line);border-radius:13px;padding:5px;background:var(--surface);color:var(--text);font-size:11px;font-weight:800;text-align:left}
.wallpaper-choice[aria-pressed="true"]{border:2px solid var(--accent);padding:4px;box-shadow:0 0 0 2px color-mix(in srgb,var(--accent) 18%,transparent)}
.wallpaper-preview{display:block;height:52px;border-radius:8px;margin-bottom:5px;background-size:cover;background-position:center}
.wallpaper-name{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;padding:0 3px 2px}
.preview-aurora{background:linear-gradient(145deg,#051230,#0a4e6f 54%,#79399d)}
.preview-ocean{background:linear-gradient(145deg,#031631,#00658b 52%,#23cabb)}
.preview-sunset{background:linear-gradient(145deg,#30143e,#bf3f4d 52%,#ffaa4a)}
.preview-forest{background:linear-gradient(145deg,#071e1f,#125b48 54%,#97a451)}
.preview-midnight{background:linear-gradient(145deg,#060b1f,#192554 53%,#46388f)}
.preview-violet{background:linear-gradient(145deg,#180f3a,#602c93 52%,#dc57aa)}
.preview-desert{background:linear-gradient(145deg,#3d2323,#ab5c3a 54%,#f4be70)}
.preview-coral{background:linear-gradient(145deg,#2a1230,#9c2f61 54%,#ff7d7e)}
.preview-glacier{background:linear-gradient(145deg,#08233f,#2d79a2 52%,#afdeec)}
.preview-nebula{background:linear-gradient(145deg,#0c0922,#4d2b78 51%,#1e7b9a)}
.preview-original{background:linear-gradient(135deg,#ffffff,#edf0ff)}
body.wallpaper-on:not(.dark){background-image:linear-gradient(135deg,rgba(246,247,255,.18),rgba(237,240,255,.24)),var(--user-wallpaper);background-size:cover;background-position:center;background-repeat:no-repeat;background-attachment:scroll}
body.wallpaper-on.dark{background-image:linear-gradient(135deg,rgba(8,12,25,.25),rgba(18,25,45,.34)),var(--user-wallpaper);background-size:cover;background-position:center;background-repeat:no-repeat;background-attachment:scroll}
body.wallpaper-on .hero{background-image:linear-gradient(130deg,rgba(255,255,255,.34),rgba(237,240,255,.43)),var(--user-wallpaper);background-size:cover;background-position:center;background-repeat:no-repeat}
body.wallpaper-on.dark .hero{background-image:linear-gradient(130deg,rgba(18,25,45,.43),rgba(26,37,64,.52)),var(--user-wallpaper);background-size:cover;background-position:center;background-repeat:no-repeat}
@media(max-width:480px){.wallpaper-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.wallpaper-preview{height:58px}.wallpaper-heading h2{font-size:15px}}
@media(min-width:481px) and (max-width:649px){.wallpaper-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}

.modal{position:fixed;inset:0;background:#050a14b8;z-index:50;display:none;align-items:center;justify-content:center;padding:12px}
.modal.open{display:flex}
.modalbox{width:min(100%,650px);max-height:88vh;overflow:auto;border:1px solid var(--line);border-radius:20px;background:var(--bg);padding:17px;box-shadow:0 25px 90px #0006}
.modalhead{display:flex;gap:10px;align-items:center;position:sticky;top:-17px;background:var(--bg);padding:4px 0 12px;z-index:1}
.modalhead h2{flex:1}
.close{width:34px;height:34px;border-radius:10px;border:1px solid var(--line);background:var(--surface);color:var(--text);font-weight:900}
.field{display:flex;flex-direction:column;gap:5px;margin:10px 0;color:var(--muted);font-size:11px;font-weight:800}
.field input,.field select,.field textarea,.input{width:100%;padding:11px;border-radius:11px;border:1px solid var(--line);background:var(--surface);color:var(--text);outline-color:var(--accent);font-weight:500}
.field textarea{min-height:150px;resize:vertical}
.row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
.row>*{flex:1;min-width:110px}
.output{padding:12px;border:1px solid var(--line);background:var(--surface);border-radius:12px;white-space:pre-wrap;overflow-wrap:anywhere;font-family:ui-monospace,monospace;font-size:12px;min-height:44px;margin-top:9px}
.notefoot{font-size:10px;color:var(--muted);margin-top:8px}
@media(min-width:650px){.shell{padding:24px 22px 40px}.hero{padding:30px}.grid{grid-template-columns:repeat(4,minmax(0,1fr))}.tool-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.directory-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.actions{grid-template-columns:repeat(3,minmax(0,1fr))}.bookmark-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}
@media(max-width:365px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}.search select{max-width:86px;min-width:75px}.search .primary{padding:10px}.shell{padding-left:11px;padding-right:11px}}
/* Responsive header and compact-phone layout */
.brand-copy{flex:1 1 auto;min-width:0}
@media(max-width:480px){
 .shell{padding:12px 12px 26px}
 .top{flex-wrap:wrap;gap:9px;margin:2px 0 15px}
 .logo{width:42px;height:42px;flex-basis:42px;border-radius:14px}
 .brand-copy{flex:1 1 calc(100% - 55px);min-width:0}
 .brand{font-size:13px;letter-spacing:1.15px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
 .brand-sub{font-size:10px;line-height:1.35}
 .language-select{order:3;flex:1 1 115px;width:auto;max-width:none;margin:0;padding:9px 10px;font-size:12px}
 .mode{order:4;margin-left:auto;font-size:9px;padding:8px 9px;max-width:calc(100% - 130px)}
 .hero{padding:21px 15px 16px;border-radius:20px}
 h1{font-size:clamp(25px,7.3vw,34px);letter-spacing:-.8px}
 .search{gap:5px;padding:5px}
 .search select{width:92px;min-width:0;max-width:92px;font-size:11px;padding:8px 0}
 .search input{font-size:14px;padding:10px 2px}
 .primary{padding:10px 11px;font-size:12px}
 .grid{grid-template-columns:repeat(2,minmax(0,1fr))}
 .tile{gap:8px;padding:10px;min-height:68px}
 .icon{width:32px;height:32px;flex-basis:32px;font-size:16px}
 .panelnav{gap:5px}
 .navbtn{padding:9px 2px;font-size:10px}
 .actions{grid-template-columns:repeat(2,minmax(0,1fr))}
 .creator-head>div:nth-child(2){min-width:0;flex:1}
 .creator-head p{overflow-wrap:anywhere}
}
@media(max-width:365px){
 .shell{padding-left:11px;padding-right:11px}
 .search select{width:78px;max-width:78px}
 .search .primary{padding:9px 8px}
 .mode{max-width:calc(100% - 110px);font-size:8px;padding:7px}
}

/* Compact phone-first home UI: more room for the page, without tiny wallpaper targets. */
.shell{padding-top:10px;padding-bottom:18px}
.top{gap:8px;margin:0 0 10px}
.top .logo{width:36px;height:36px;flex:0 0 36px;border-radius:12px}
.top .logo:after{inset:4px;border-radius:9px}
.top .logo svg{width:25px;height:25px}
.top .brand{font-size:13px;letter-spacing:1px}
.top .brand-sub{font-size:10px;line-height:1.25}
.top .mode{padding:4px 7px;font-size:9px;letter-spacing:.2px}
.top .language-select{padding:5px 7px;font-size:10px}
.btn,.primary,.action{padding:8px 10px;font-size:11px}
.navbtn{padding:7px 3px;font-size:10px}
.search{gap:4px;padding:4px}
.search input{padding:8px 2px}
.search select{padding:7px 0}
.wallpaper-heading{margin-top:13px;margin-bottom:8px}
.wallpaper-grid{gap:6px}
.wallpaper-choice{padding:4px;font-size:10px;border-radius:10px}
.wallpaper-choice[aria-pressed="true"]{padding:3px}
.wallpaper-preview{height:46px;border-radius:7px}
@media(max-width:480px){
 .shell{padding:8px 10px 15px}
 .top{gap:6px;margin-bottom:8px}
 .top .logo{width:31px;height:31px;flex-basis:31px;border-radius:10px}
 .top .logo:after{inset:3px;border-radius:7px}
 .top .logo svg{width:22px;height:22px}
 .top .brand{font-size:12px;letter-spacing:.75px}
 .top .brand-sub{font-size:9px}
 .top .language-select{padding:5px 6px;font-size:10px}
 .top .mode{padding:4px 6px;font-size:8px}
 .hero{padding:16px 12px 13px;border-radius:16px}
 .primary{padding:8px 9px;font-size:11px}
 .navbtn{padding:7px 2px;font-size:9px}
 .wallpaper-preview{height:48px}
}


/* Hari Browser 2.1 — refreshed start page, adaptive spacing and cheaper off-screen painting */
body{background-image:radial-gradient(ellipse at 2% 0%,color-mix(in srgb,var(--accent) 11%,transparent),transparent 36%),radial-gradient(ellipse at 100% 8%,color-mix(in srgb,#06b6d4 8%,transparent),transparent 32%)}
body.dark{background-image:radial-gradient(ellipse at 2% 0%,color-mix(in srgb,var(--accent) 18%,transparent),transparent 36%),radial-gradient(ellipse at 100% 8%,#0e749022,transparent 32%)}
.shell{max-width:1120px;padding:12px 14px 20px}
.top{gap:10px;margin:0 0 12px;padding:8px 10px;min-height:52px;border:1px solid var(--line);border-radius:18px;background:linear-gradient(120deg,color-mix(in srgb,var(--surface) 91%,var(--accent) 9%),var(--surface));box-shadow:0 8px 28px #14213b10}
.top .logo{width:40px;height:40px;flex-basis:40px;border-radius:14px;box-shadow:0 7px 18px color-mix(in srgb,var(--accent) 25%,transparent)}
.top .logo:after{inset:4px;border-radius:10px}
.top .logo svg{width:27px;height:27px}
.top .brand{font-size:13px;letter-spacing:1.1px}
.top .brand-sub{font-size:10px}
.top .mode{font-size:9px;padding:6px 8px;border-radius:999px}
.top .language-select{max-width:134px;padding:7px 8px;border-radius:12px;font-size:11px}
.hero{border-radius:25px;padding:clamp(20px,4vw,34px);border-color:color-mix(in srgb,var(--accent) 22%,var(--line));background:linear-gradient(135deg,color-mix(in srgb,var(--surface) 92%,var(--accent) 8%),var(--surface2));box-shadow:0 18px 46px color-mix(in srgb,var(--text) 7%,transparent);isolation:isolate}
.hero:before{content:"";position:absolute;z-index:0;pointer-events:none;width:240px;height:240px;right:-90px;top:-125px;border:1px solid color-mix(in srgb,var(--accent) 26%,transparent);border-radius:50%;box-shadow:0 0 0 23px color-mix(in srgb,var(--accent) 5%,transparent),0 0 0 47px color-mix(in srgb,var(--accent) 4%,transparent)}
.hero>*{position:relative;z-index:1}
h1{font-size:clamp(29px,6vw,47px);line-height:1.07;letter-spacing:-1.3px;margin:12px 0 10px}
.sub{font-size:clamp(13px,2vw,15px);margin-bottom:20px}
.search{border-radius:18px;padding:6px;box-shadow:0 5px 18px color-mix(in srgb,var(--text) 5%,transparent)}
.search input{font-size:15px}
.primary{border-radius:13px;box-shadow:0 5px 13px color-mix(in srgb,var(--accent) 22%,transparent)}
.pills{gap:7px}
.pill{border-radius:999px;padding:7px 10px;background:color-mix(in srgb,var(--surface) 90%,var(--accent) 10%);border-color:color-mix(in srgb,var(--accent) 13%,var(--line))}
.panelnav{gap:8px;margin:14px 0 7px}
.navbtn{border-radius:13px;padding:10px 5px;transition:background-color .15s,border-color .15s}
.section-head{margin-top:25px;margin-bottom:12px}
.tile,.bookmark,.directory-item,.tool,.action,.card{transition:transform .14s ease,border-color .14s ease,box-shadow .14s ease}
.tile,.bookmark,.directory-item,.tool,.action{border-color:color-mix(in srgb,var(--line) 88%,var(--accent) 12%)}
.tile,.tool{box-shadow:0 4px 14px color-mix(in srgb,var(--text) 3%,transparent)}
.tile:hover,.bookmark:hover,.directory-item:hover,.tool:hover,.action:hover{transform:translateY(-1px);border-color:var(--accent);box-shadow:0 8px 20px color-mix(in srgb,var(--accent) 11%,transparent)}
.tile:focus-visible,.btn:focus-visible,.primary:focus-visible,.navbtn:focus-visible,.category:focus-visible,.wallpaper-choice:focus-visible{outline:3px solid color-mix(in srgb,var(--accent) 60%,white);outline-offset:2px}
.icon{border-radius:13px;background:linear-gradient(145deg,var(--surface2),color-mix(in srgb,var(--surface2) 70%,var(--accent) 30%))}
.card{border-radius:18px;padding:16px}
.wallpaper-choice{border-radius:13px}
.wallpaper-preview{height:58px}
.wallpaper-choice[aria-pressed="true"]{box-shadow:0 0 0 2px color-mix(in srgb,var(--accent) 18%,transparent)}
.localnote{margin-top:24px;padding:13px 10px 0;border-top:1px solid var(--line);font-size:10px;line-height:1.6}
#toolsPanel,#explorePanel,#privacyPanel{content-visibility:auto;contain-intrinsic-size:auto 620px}
/* Preserve selected local wallpapers when the refreshed theme is active. */
body.wallpaper-on:not(.dark){background-image:linear-gradient(135deg,rgba(246,247,255,.18),rgba(237,240,255,.24)),var(--user-wallpaper);background-size:cover;background-position:center;background-repeat:no-repeat;background-attachment:scroll}
body.wallpaper-on.dark{background-image:linear-gradient(135deg,rgba(8,12,25,.25),rgba(18,25,45,.34)),var(--user-wallpaper);background-size:cover;background-position:center;background-repeat:no-repeat;background-attachment:scroll}
body.wallpaper-on:not(.dark) .hero{background-image:linear-gradient(130deg,rgba(255,255,255,.34),rgba(237,240,255,.43)),var(--user-wallpaper);background-size:cover;background-position:center;background-repeat:no-repeat}
body.wallpaper-on.dark .hero{background-image:linear-gradient(130deg,rgba(18,25,45,.43),rgba(26,37,64,.52)),var(--user-wallpaper);background-size:cover;background-position:center;background-repeat:no-repeat}
@media(max-width:480px){
 .shell{padding:9px 10px 16px}
 .top{gap:7px;padding:7px;border-radius:15px;margin-bottom:10px}
 .top .logo{width:34px;height:34px;flex-basis:34px;border-radius:11px}
 .top .logo svg{width:23px;height:23px}
 .top .brand{font-size:12px;letter-spacing:.8px}
 .top .brand-sub{font-size:9px}
 .top .language-select{max-width:none;padding:6px 7px;font-size:10px}
 .top .mode{padding:5px 6px;font-size:8px}
 .hero{padding:18px 13px 15px;border-radius:19px}
 h1{font-size:clamp(26px,7.3vw,36px);letter-spacing:-.8px}
 .search{gap:5px}
 .search select{width:84px;max-width:84px;font-size:10px}
 .search input{font-size:13px}
 .panelnav{gap:5px}
 .navbtn{padding:9px 2px;font-size:10px}
 .wallpaper-preview{height:50px}
 .localnote{margin-top:18px}
}
@media(max-width:350px){
 .shell{padding-left:8px;padding-right:8px}
 .top .language-select{flex:1 1 100px;max-width:125px}
 .top .mode{max-width:105px}
 .hero{padding:15px 10px 13px}
 .search select{width:72px;max-width:72px}
 .primary{padding:8px 7px}
}
@media(prefers-reduced-motion:reduce){
 *,*::before,*::after{scroll-behavior:auto!important;transition:none!important;animation:none!important}
}

</style>
</head>
<body class="__DARKCLASS__">
<div class="shell">
<header class="top"><div class="logo" aria-label="Hari Browser logo"><svg viewBox="0 0 48 48" role="img" aria-hidden="true"><circle cx="24" cy="24" r="18" fill="none" stroke="white" stroke-width="2.2" opacity=".82"/><ellipse cx="24" cy="24" rx="8" ry="18" fill="none" stroke="white" stroke-width="1.6" opacity=".7"/><path d="M7 24h34" stroke="white" stroke-width="1.6" opacity=".7"/><path d="M17 14v20M17 24h14M31 14v20" stroke="white" stroke-width="4.2" stroke-linecap="round" stroke-linejoin="round"/></svg></div><div class="brand-copy" style="min-width:0"><div class="brand">HARI BROWSER</div><div class="brand-sub">Your web, your space — designed for privacy</div></div><select class="language-select" id="languageSelect" aria-label="Home page language"><option value="en">English (India)</option><option value="ml">മലയാളം</option><option value="hi">हिन्दी</option><option value="ta">தமிழ்</option><option value="te">తెలుగు</option><option value="kn">ಕನ್ನಡ</option><option value="bn">বাংলা</option><option value="mr">मराठी</option><option value="gu">ગુજરાતી</option><option value="pa">ਪੰਜਾਬੀ</option><option value="ur">اردو</option><option value="es">Español</option><option value="fr">Français</option><option value="ar">العربية</option></select><span class="mode">__MODE__</span></header>
<section class="hero">
  <div class="eyebrow">A BETTER START</div>
  <h1 id="heroTitle">Search. Learn.<br><span class="accent">Make it happen.</span></h1>
        """);
    htmlBuilder.append("""
  <p class="sub" id="greeting">A thoughtful home for your web.</p>
  <form id="searchForm" class="search">
    <select id="engine" aria-label="Search engine"><option>DuckDuckGo</option><option>Startpage</option><option>Brave Search</option><option>Google</option><option>Bing</option></select>
    <input id="query" type="search" placeholder="Search the web or enter an HTTPS site…" autocomplete="off" spellcheck="false" aria-label="Search or website address">
    <button class="primary" type="submit">Search ↗</button>
  </form>
  <a class="ai-launch" href="https://hari-browser-home.invalid/action/ai" aria-label="Open Hari AI assistant"><span class="ai-launch-icon" aria-hidden="true">✦</span><span class="ai-launch-copy"><strong id="hariAiTitle">Hari AI</strong><small id="hariAiDesc">Ask questions, learn faster and explore new ideas</small></span><span class="ai-launch-open" id="hariAiOpen">Open →</span></a>
  <div class="pills"><span class="pill">🔒 HTTPS-only</span><span class="pill">🧩 Local tools</span><span class="pill" id="countPill">250+ destinations</span><span class="pill" id="clock">Your local time</span></div>
</section>

<nav class="panelnav" aria-label="Home sections">
  <button class="navbtn active" data-panel="homePanel">⌂ Home</button>
  <button class="navbtn" data-panel="toolsPanel">⚒ Tools</button>
  <button class="navbtn" data-panel="explorePanel">✦ Explore</button>
  <button class="navbtn" data-panel="privacyPanel">◈ Privacy</button>
</nav>

<section id="homePanel" class="panel active">
  <div class="section-head"><h2>Quick access</h2><button class="btn" id="addShortcut">＋ Add site</button></div>
  <div id="shortcuts" class="grid"></div>
  <div class="section-head"><h2>Your bookmarks</h2><a class="mini" href="https://hari-browser-home.invalid/action/bookmarks">Manage all ↗</a></div>
  <div id="bookmarks" class="bookmark-grid"></div>
  <div class="section-head"><h2>Browser controls</h2><span class="mini">Tap to open</span></div>
  <div class="actions">
    <a class="action" href="https://hari-browser-home.invalid/action/newtab">＋ New tab<span>Start a fresh tab</span></a>
    <a class="action" href="https://hari-browser-home.invalid/action/tabs">▣ Tab switcher<span>Switch, duplicate or close tabs</span></a>
    <a class="action" href="https://hari-browser-home.invalid/action/history">◷ History<span>History is off by default</span></a>
    <a class="action" href="https://hari-browser-home.invalid/action/downloads">⇩ Downloads<span>Open downloaded files</span></a>
    <a class="action" href="https://hari-browser-home.invalid/action/private">◉ Private session<span>Separate private browsing</span></a>
    <a class="action" href="https://hari-browser-home.invalid/action/settings">⚙ Settings<span>Privacy, search and display</span></a>
    <a class="action" href="https://hari-browser-home.invalid/action/find">⌕ Find on page<span>Search text in a webpage</span></a>
    <a class="action" href="https://hari-browser-home.invalid/action/bookmark">★ Save current page<span>Add the page to encrypted bookmarks</span></a>
    <a class="action" href="https://hari-browser-home.invalid/action/qr">▦ QR code<span>Create a QR for the current URL</span></a>
    <a class="action" href="https://hari-browser-home.invalid/action/print">▤ Save as PDF<span>Use Android's print dialog</span></a>
    <a class="action" href="https://hari-browser-home.invalid/action/share">↗ Share page<span>Share the current title and address</span></a>
    <a class="action" href="https://hari-browser-home.invalid/action/security">🛡 Site security<span>Review HTTPS and browser safeguards</span></a>
  </div>
  <div class="section-head"><h2>Personal shortcuts</h2><span class="mini">Saved only on this device</span></div>
  <div class="card"><h3>Make the start page yours</h3><p>Add your favourite HTTPS websites. Shortcuts stay on this device in the browser profile; no account is needed.</p><div class="row" style="margin-top:10px"><button class="btn" id="addShortcut2">＋ Add a shortcut</button><button class="btn" id="resetShortcuts">Reset local shortcuts</button></div></div>
  <div class="wallpaper-heading"><div><h2>Offline wallpapers</h2><p>Ten built-in backgrounds. No downloads or external image requests.</p></div><span class="mini">On-device</span></div>
  <div class="wallpaper-grid" id="wallpaperGrid" aria-label="Choose a home screen wallpaper">
    <button type="button" class="wallpaper-choice" data-wallpaper="none" aria-pressed="true"><span class="wallpaper-preview preview-original"></span><span class="wallpaper-name">Original</span></button>
    <button type="button" class="wallpaper-choice" data-wallpaper="aurora" aria-pressed="false"><span class="wallpaper-preview preview-aurora"></span><span class="wallpaper-name">Aurora</span></button>
    <button type="button" class="wallpaper-choice" data-wallpaper="ocean" aria-pressed="false"><span class="wallpaper-preview preview-ocean"></span><span class="wallpaper-name">Ocean</span></button>
    <button type="button" class="wallpaper-choice" data-wallpaper="sunset" aria-pressed="false"><span class="wallpaper-preview preview-sunset"></span><span class="wallpaper-name">Sunset</span></button>
    <button type="button" class="wallpaper-choice" data-wallpaper="forest" aria-pressed="false"><span class="wallpaper-preview preview-forest"></span><span class="wallpaper-name">Forest</span></button>
    <button type="button" class="wallpaper-choice" data-wallpaper="midnight" aria-pressed="false"><span class="wallpaper-preview preview-midnight"></span><span class="wallpaper-name">Midnight</span></button>
    <button type="button" class="wallpaper-choice" data-wallpaper="violet" aria-pressed="false"><span class="wallpaper-preview preview-violet"></span><span class="wallpaper-name">Violet</span></button>
    <button type="button" class="wallpaper-choice" data-wallpaper="desert" aria-pressed="false"><span class="wallpaper-preview preview-desert"></span><span class="wallpaper-name">Desert</span></button>
    <button type="button" class="wallpaper-choice" data-wallpaper="coral" aria-pressed="false"><span class="wallpaper-preview preview-coral"></span><span class="wallpaper-name">Coral</span></button>
    <button type="button" class="wallpaper-choice" data-wallpaper="glacier" aria-pressed="false"><span class="wallpaper-preview preview-glacier"></span><span class="wallpaper-name">Glacier</span></button>
    <button type="button" class="wallpaper-choice" data-wallpaper="nebula" aria-pressed="false"><span class="wallpaper-preview preview-nebula"></span><span class="wallpaper-name">Nebula</span></button>
  </div>
  <div class="card creator-card">
    <div class="creator-head"><div class="creator-mark">H</div><div><h3 id="creatorHeading">Explore more from Hari</h3><p id="creatorBlurb">Useful tools and creative projects made by Hari. Open a project only when you choose it.</p></div></div>
    <h3 id="creatorSitesTitle" style="margin:15px 0 8px">Hari websites</h3>
    <div class="project-grid">
      <a class="project-link" href="https://haricalcx.netlify.app/" rel="noreferrer noopener"><span class="project-icon">∑</span><span><strong>Hari CalcX</strong><small>Calculator and study tools</small></span></a>
      <a class="project-link" href="https://harimentalx.netlify.app/" rel="noreferrer noopener"><span class="project-icon">✦</span><span><strong>Hari MentalX</strong><small>Mind and wellness project</small></span></a>
      <a class="project-link" href="https://harivisualx.netlify.app/" rel="noreferrer noopener"><span class="project-icon">◈</span><span><strong>Hari VisualX</strong><small>Visual and creative tools</small></span></a>
      <a class="project-link" href="https://harichemx.netlify.app/" rel="noreferrer noopener"><span class="project-icon">⚗</span><span><strong>Hari ChemX</strong><small>Chemistry learning</small></span></a>
      <a class="project-link" href="https://haribioquest.netlify.app/" rel="noreferrer noopener"><span class="project-icon">⌘</span><span><strong>Hari BioQuest</strong><small>Biology exploration</small></span></a>
      <a class="project-link" href="https://harimapx.netlify.app/" rel="noreferrer noopener"><span class="project-icon">🌍</span><span><strong>Hari MapX</strong><small>Maps and geography</small></span></a>
      <a class="project-link" href="https://haripedia.netlify.app/" rel="noreferrer noopener"><span class="project-icon">📚</span><span><strong>HariPedia</strong><small>Knowledge and facts</small></span></a>
      <a class="project-link" href="https://harieditor.netlify.app/" rel="noreferrer noopener"><span class="project-icon">✎</span><span><strong>Hari Editor</strong><small>Writing and editing</small></span></a>
      <a class="project-link" href="https://harigamex.netlify.app/" rel="noreferrer noopener"><span class="project-icon">🎮</span><span><strong>Hari GameX</strong><small>Games and challenges</small></span></a>
      <a class="project-link" href="https://github.com/harisankar322011-commits/hari-browser" rel="noreferrer noopener"><span class="project-icon">⌘</span><span><strong>Hari Browser source</strong><small>Open-source Android browser</small></span></a>
    </div>
    <a class="creator-social" id="creatorFollow" href="https://www.instagram.com/inspiring_creatives_minds/" rel="noreferrer noopener"><span class="social-icon">◎</span><span>Follow @inspiring_creatives_minds<small>Education, creativity, facts and motivation</small></span></a>
  </div>
</section>

<section id="toolsPanel" class="panel">
  <div class="section-head"><h2>Built-in toolbox</h2><span class="mini">Works locally, no account</span></div>
  <p class="mini">Start with eight everyday tools. Expand the full toolbox only when needed. Tools run locally; do not store passwords or secrets in notes.</p>
  <div id="toolGrid" class="tool-grid"></div>
  <div class="card"><h3>🔐 HARI Cryptography</h3><p>Encrypt and decrypt private text, files, and exported bookmark/history backups on this device. This tool does not encrypt live browser data or browsing traffic.</p><a class="action" style="margin-top:9px" href="https://hari-browser-home.invalid/action/cryptography">Open cryptography tool ↗</a></div>
  <div class="row" style="justify-content:center;margin-top:12px"><button class="btn" id="toggleTools" type="button" aria-expanded="false">Show all 34 tools</button></div>
  <div class="card"><h3>Browser actions</h3><p>Reader-style view, desktop mode, page translation, in-page find, QR, print to PDF, theme and data cleanup are available through native browser controls.</p><a class="action" style="margin-top:9px" href="https://hari-browser-home.invalid/action/settings">Open full browser settings ↗</a></div>
</section>

<section id="explorePanel" class="panel">
  <div class="section-head"><h2>Explore directory</h2><span class="mini" id="directoryCount">250+ sites</span></div>
  <p class="mini">A searchable library of useful destinations. Sites are opened only when tapped; this directory does not preload external pages.</p>
  <input class="directory-search" id="directorySearch" type="search" placeholder="Find a website or category…" autocomplete="off" aria-label="Find directory links">
  <div class="categorybar" id="categories"></div>
  <div id="directoryGrid" class="directory-grid"></div>
  <p class="mini" id="directoryEmpty" style="display:none;text-align:center;padding:20px">No matches. Try another search.</p>
</section>

<section id="privacyPanel" class="panel">
  <div class="section-head"><h2>Privacy dashboard</h2><span class="mini">On-device controls</span></div>
  <div class="card"><h3>🛡 Safer browsing</h3><p>HTTPS-only navigation, certificate-error blocking, Android Safe Browsing, mixed-content blocking, disabled file access, disabled geolocation, and denied site permission prompts.</p><div class="actions" style="margin-top:10px">
    <a class="action" href="https://hari-browser-home.invalid/action/protections">HARI SINGULARITY Protection Dashboard<span>Review active safeguards and honest limitations</span></a>
    <a class="action" href="https://hari-browser-home.invalid/action/security">View current site security<span>Inspect this website's connection settings</span></a>
  </div></div>
  <div class="card"><h3>🧹 Data controls</h3><p>History is disabled by default. Bookmarks use encrypted on-device storage. Private browsing uses a separate profile on supported Android versions, but it cannot hide activity from websites or your network provider.</p><div class="actions" style="margin-top:10px"><a class="action" href="https://hari-browser-home.invalid/action/clear">Clear browser data<span>Review before clearing</span></a><a class="action" href="https://hari-browser-home.invalid/action/emergency">Emergency clear<span>Confirmation required</span></a><a class="action" href="https://hari-browser-home.invalid/action/settings">Privacy settings<span>Cookies, JavaScript and trackers</span></a></div></div>
  <div class="card"><h3>What this browser does not promise</h3><p>It is not a complete anonymity system. Websites and your selected search provider still receive requests when you visit them. The local tracker blocklist is best-effort, and private mode cannot erase downloads, screenshots or network logs.</p></div>
</section>

<p class="localnote">Made for quick access. No external scripts, tracking pixels or remote fonts are loaded by this start page. Hari AI sends prompts online only when you choose to use it.</p>
</div>

<div class="modal" id="modal" role="dialog" aria-modal="true" aria-labelledby="modalTitle">
  <div class="modalbox"><div class="modalhead"><h2 id="modalTitle">Local tool</h2><button class="close" id="closeModal" aria-label="Close">×</button></div><div id="modalContent"></div></div>
</div>

<script>
'use strict';
const DIRECTORY = __DIRECTORY__;
const BOOKMARKS = __BOOKMARKS__;
const ENGINE_SETTING = __ENGINE__;
const PRIVATE_MODE = __PRIVATE__;
const LANGUAGE_SETTING = __LANGUAGE__;
const WALLPAPER_NAMES = ['aurora','ocean','sunset','forest','midnight','violet','desert','coral','glacier','nebula'];
const FEATURED = [
 {name:'Wikipedia',url:'https://www.wikipedia.org/',icon:'📚',desc:'Quick reference'},
 {name:'YouTube',url:'https://www.youtube.com/',icon:'▶️',desc:'Video and learning'},
 {name:'GitHub',url:'https://github.com/',icon:'⌘',desc:'Projects and code'},
 {name:'Khan Academy',url:'https://www.khanacademy.org/',icon:'🎓',desc:'Free lessons'},
 {name:'NASA',url:'https://www.nasa.gov/',icon:'🚀',desc:'Space and science'},
 {name:'Google Maps',url:'https://maps.google.com/',icon:'🗺️',desc:'Maps and places'},
 {name:'Gmail',url:'https://mail.google.com/',icon:'✉️',desc:'Email'},
 {name:'Google Drive',url:'https://drive.google.com/',icon:'☁️',desc:'Files and documents'},
 {name:'MDN Web Docs',url:'https://developer.mozilla.org/',icon:'🧑‍💻',desc:'Web development'},
 {name:'The Hindu',url:'https://www.thehindu.com/',icon:'📰',desc:'News'},
 {name:'Project Gutenberg',url:'https://www.gutenberg.org/',icon:'📖',desc:'Free books'},
 {name:'OpenStreetMap',url:'https://www.openstreetmap.org/',icon:'🌍',desc:'Open maps'}
];
const TOOLS = [
 {id:'calculator',name:'Smart calculator',icon:'🧮',desc:'Arithmetic, brackets, percentage and powers'},
 {id:'units',name:'Unit converter',icon:'📏',desc:'Length, mass, speed, data and temperature'},
 {id:'notes',name:'Quick notes',icon:'📝',desc:'Save simple notes locally on this device'},
 {id:'password',name:'Password generator',icon:'🔐',desc:'Cryptographically random passwords'},
 {id:'text',name:'Text toolkit',icon:'🔤',desc:'Case, sort, reverse, clean and deduplicate'},
        """);
    htmlBuilder.append("""
 {id:'json',name:'JSON formatter',icon:'{}',desc:'Validate, format and minify JSON'},
 {id:'url',name:'URL encoder',icon:'🔗',desc:'Encode and decode URL text'},
 {id:'base64',name:'Base64 utility',icon:'🔡',desc:'Encode or decode UTF-8 text'},
 {id:'pomodoro',name:'Focus timer',icon:'🍅',desc:'Work sessions and short breaks'},
 {id:'stopwatch',name:'Stopwatch',icon:'⏱️',desc:'Local elapsed-time stopwatch'},
 {id:'date',name:'Date calculator',icon:'📅',desc:'Compare dates and calculate age in days'},
 {id:'percent',name:'Percentage & discount',icon:'％',desc:'Percentage, sale price and savings'},
 {id:'loan',name:'Loan calculator',icon:'💰',desc:'Estimate monthly payments and total interest'},
 {id:'color',name:'Color lab',icon:'🎨',desc:'HEX and RGB color values'},
 {id:'wordcount',name:'Word counter',icon:'🔢',desc:'Words, characters, sentences and lines'},
 {id:'lineclean',name:'Line cleaner',icon:'🧹',desc:'Trim, sort and remove duplicate lines'},
 {id:'slug',name:'Slug maker',icon:'🏷️',desc:'Turn titles into URL-safe slugs'},
 {id:'age',name:'Age calculator',icon:'🎂',desc:'Calculate years, months and days'},
 {id:'tip',name:'Tip splitter',icon:'🍽️',desc:'Tip amount and per-person total'},
 {id:'compound',name:'Compound interest',icon:'📈',desc:'Estimate compounded growth'},
 {id:'hash',name:'Text fingerprint',icon:'🧷',desc:'Create a SHA-256 digest locally when supported'},
 {id:'colorcontrast',name:'Contrast checker',icon:'◐',desc:'Check text/background contrast ratio'},
 {id:'readme',name:'Browser feature guide',icon:'📘',desc:'Explore the built-in browser tools'},
 {id:'qr',name:'Current-page QR',icon:'▦',desc:'Use the browser QR tool for the active page'},
 {id:'uuid',name:'UUID generator',icon:'🆔',desc:'Create random version-4 identifiers locally'},
 {id:'random',name:'Random number & dice',icon:'🎲',desc:'Generate random integers or roll dice'},
 {id:'timestamp',name:'Timestamp converter',icon:'🕒',desc:'Convert dates to Unix timestamps and back'},
 {id:'baseconvert',name:'Number base converter',icon:'🔣',desc:'Convert binary, octal, decimal and hexadecimal'},
 {id:'lorem',name:'Lorem ipsum generator',icon:'📄',desc:'Create offline placeholder text'},
 {id:'readtime',name:'Reading-time estimator',icon:'📖',desc:'Estimate reading time and count words'},
 {id:'stats',name:'Statistics calculator',icon:'📊',desc:'Mean, median, mode, range and standard deviation'},
 {id:'gradient',name:'Gradient designer',icon:'🌈',desc:'Preview CSS linear gradients'},
 {id:'csvjson',name:'CSV to JSON',icon:'🗃️',desc:'Convert comma-separated data into JSON'},
 {id:'textdiff',name:'Text comparison',icon:'↔️',desc:'Show added and removed lines between two texts'}
];
const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
function validHttps(raw){try{const u=new URL(raw);return u.protocol==='https:' && !!u.hostname;}catch(e){return false;}}
function iconFor(name){return (String(name||'?').trim().match(/[\\p{Extended_Pictographic}]/u)||[String(name||'?').charAt(0).toUpperCase()])[0];}
const I18N = {
 en:{brandSub:"Your web, your space — designed for privacy",eyebrow:"A BETTER START",titleTop:"Search. Learn.",titleAccent:"Make it happen.",sub:"A thoughtful home for your web.",search:"Search",home:"⌂ Home",tools:"⚒ Tools",explore:"✦ Explore",privacy:"◈ Privacy",quick:"Quick access",addSite:"＋ Add site",bookmarks:"Your bookmarks",manage:"Manage all",controls:"Browser controls",tap:"Tap to open",shortcuts:"Personal shortcuts",personalize:"Make the start page yours",toolbox:"Built-in toolbox",directory:"Explore directory",privacyDash:"Privacy dashboard",promoTitle:"Explore more from Hari",promoText:"Useful tools and creative projects made by Hari. Open a project only when you choose it.",websites:"Hari websites",follow:"Follow @inspiring_creatives_minds",followSub:"Education, creativity, facts and motivation",morning:"Good morning",afternoon:"Good afternoon",evening:"Good evening",ready:"your shortcuts, tools and bookmarks are ready",localTime:"Local time"},
 ml:{brandSub:"നിങ്ങളുടെ വെബ് ലോകം — സ്വകാര്യതയ്ക്ക് മുൻഗണന",eyebrow:"മികച്ച തുടക്കം",titleTop:"തിരയൂ. പഠിക്കൂ.",titleAccent:"മുന്നേറൂ.",sub:"നിങ്ങളുടെ വെബിനായി ഒരുക്കിയ ഹോം പേജ്.",search:"തിരയുക",home:"⌂ ഹോം",tools:"⚒ ടൂളുകൾ",explore:"✦ പര്യവേക്ഷണം",privacy:"◈ സ്വകാര്യത",quick:"വേഗത്തിലുള്ള പ്രവേശനം",addSite:"＋ സൈറ്റ് ചേർക്കുക",bookmarks:"നിങ്ങളുടെ ബുക്ക്മാർക്കുകൾ",manage:"എല്ലാം കാണുക",controls:"ബ്രൗസർ നിയന്ത്രണങ്ങൾ",tap:"തുറക്കാൻ ടാപ്പ് ചെയ്യുക",shortcuts:"വ്യക്തിഗത കുറുക്കുവഴികൾ",personalize:"ഹോം പേജ് ക്രമീകരിക്കുക",toolbox:"ഉൾപ്പെടുത്തിയ ടൂളുകൾ",directory:"വെബ്സൈറ്റുകൾ കണ്ടെത്തുക",privacyDash:"സ്വകാര്യത ഡാഷ്ബോർഡ്",promoTitle:"ഹരിയുടെ കൂടുതൽ പ്രോജക്ടുകൾ",promoText:"ഹരി തയ്യാറാക്കിയ ഉപകാരപ്രദമായ ടൂളുകളും സൃഷ്ടിപരമായ പ്രോജക്ടുകളും.",websites:"ഹരിയുടെ വെബ്സൈറ്റുകൾ",follow:"@inspiring_creatives_minds പിന്തുടരുക",followSub:"വിദ്യാഭ്യാസം, സൃഷ്ടിപരത, അറിവ്, പ്രചോദനം",morning:"സുപ്രഭാതം",afternoon:"ശുഭ ഉച്ച",evening:"ശുഭ സായാഹ്നം",ready:"നിങ്ങളുടെ കുറുക്കുവഴികളും ടൂളുകളും ബുക്ക്മാർക്കുകളും തയ്യാറാണ്",localTime:"പ്രാദേശിക സമയം"},
 hi:{brandSub:"आपकी वेब दुनिया — गोपनीयता को प्राथमिकता",eyebrow:"एक बेहतर शुरुआत",titleTop:"खोजें। सीखें।",titleAccent:"आगे बढ़ें।",sub:"आपकी वेब दुनिया के लिए एक उपयोगी होम पेज।",search:"खोजें",home:"⌂ होम",tools:"⚒ टूल",explore:"✦ खोजें",privacy:"◈ गोपनीयता",quick:"त्वरित पहुँच",addSite:"＋ साइट जोड़ें",bookmarks:"आपके बुकमार्क",manage:"सभी देखें",controls:"ब्राउज़र नियंत्रण",tap:"खोलने के लिए टैप करें",shortcuts:"व्यक्तिगत शॉर्टकट",personalize:"होम पेज को अपनी पसंद का बनाएँ",toolbox:"अंतर्निहित टूलबॉक्स",directory:"वेबसाइटें खोजें",privacyDash:"गोपनीयता डैशबोर्ड",promoTitle:"Hari के और प्रोजेक्ट देखें",promoText:"Hari द्वारा बनाए गए उपयोगी टूल और रचनात्मक प्रोजेक्ट।",websites:"Hari की वेबसाइटें",follow:"@inspiring_creatives_minds को फ़ॉलो करें",followSub:"शिक्षा, रचनात्मकता, तथ्य और प्रेरणा",morning:"सुप्रभात",afternoon:"नमस्कार",evening:"शुभ संध्या",ready:"आपके शॉर्टकट, टूल और बुकमार्क तैयार हैं",localTime:"स्थानीय समय"},
 ta:{brandSub:"உங்கள் இணைய உலகம் — தனியுரிமைக்கு முன்னுரிமை",eyebrow:"சிறந்த தொடக்கம்",titleTop:"தேடுங்கள். கற்கவும்.",titleAccent:"சாதியுங்கள்.",sub:"உங்கள் இணையத்திற்கான பயனுள்ள முகப்புப் பக்கம்.",search:"தேடு",home:"⌂ முகப்பு",tools:"⚒ கருவிகள்",explore:"✦ ஆராய்க",privacy:"◈ தனியுரிமை",quick:"விரைவு அணுகல்",addSite:"＋ தளத்தைச் சேர்",bookmarks:"உங்கள் புக்மார்க்குகள்",manage:"அனைத்தையும் காண்க",controls:"உலாவி கட்டுப்பாடுகள்",tap:"திறக்கத் தட்டவும்",shortcuts:"தனிப்பட்ட குறுக்குவழிகள்",personalize:"முகப்புப் பக்கத்தைத் தனிப்பயனாக்கவும்",toolbox:"உள்ளமைந்த கருவிகள்",directory:"இணையதளங்களை ஆராய்க",privacyDash:"தனியுரிமை பலகை",promoTitle:"Hari-யின் மற்ற திட்டங்கள்",promoText:"Hari உருவாக்கிய பயனுள்ள கருவிகளும் படைப்புத் திட்டங்களும்.",websites:"Hari இணையதளங்கள்",follow:"@inspiring_creatives_minds-ஐ பின்தொடரவும்",followSub:"கல்வி, படைப்பாற்றல், தகவல்கள் மற்றும் ஊக்கம்",morning:"காலை வணக்கம்",afternoon:"மதிய வணக்கம்",evening:"மாலை வணக்கம்",ready:"உங்கள் குறுக்குவழிகள், கருவிகள் மற்றும் புக்மார்க்குகள் தயார்",localTime:"உள்ளூர் நேரம்"},
 te:{brandSub:"మీ వెబ్ ప్రపంచం — గోప్యతకు ప్రాధాన్యం",eyebrow:"మంచి ప్రారంభం",titleTop:"వెతకండి. నేర్చుకోండి.",titleAccent:"సాధించండి.",sub:"మీ వెబ్ కోసం ఉపయోగకరమైన హోమ్ పేజీ.",search:"వెతుకు",home:"⌂ హోమ్",tools:"⚒ సాధనాలు",explore:"✦ అన్వేషించు",privacy:"◈ గోప్యత",quick:"త్వరిత ప్రవేశం",addSite:"＋ సైట్ జోడించు",bookmarks:"మీ బుక్‌మార్క్‌లు",manage:"అన్నీ చూడండి",controls:"బ్రౌజర్ నియంత్రణలు",tap:"తెరవడానికి నొక్కండి",shortcuts:"వ్యక్తిగత షార్ట్‌కట్‌లు",personalize:"హోమ్ పేజీని అనుకూలీకరించండి",toolbox:"అంతర్నిర్మిత సాధనాలు",directory:"వెబ్‌సైట్‌లను అన్వేషించండి",privacyDash:"గోప్యత డాష్‌బోర్డ్",promoTitle:"Hari ఇతర ప్రాజెక్ట్‌లను చూడండి",promoText:"Hari రూపొందించిన ఉపయోగకరమైన సాధనాలు మరియు సృజనాత్మక ప్రాజెక్ట్‌లు.",websites:"Hari వెబ్‌సైట్‌లు",follow:"@inspiring_creatives_minds ను అనుసరించండి",followSub:"విద్య, సృజనాత్మకత, వాస్తవాలు మరియు ప్రేరణ",morning:"శుభోదయం",afternoon:"శుభ మధ్యాహ్నం",evening:"శుభ సాయంత్రం",ready:"మీ షార్ట్‌కట్‌లు, సాధనాలు మరియు బుక్‌మార్క్‌లు సిద్ధంగా ఉన్నాయి",localTime:"స్థానిక సమయం"},
 kn:{brandSub:"ನಿಮ್ಮ ವೆಬ್ ಲೋಕ — ಗೌಪ್ಯತೆಗೆ ಆದ್ಯತೆ",eyebrow:"ಉತ್ತಮ ಆರಂಭ",titleTop:"ಹುಡುಕಿ. ಕಲಿಯಿರಿ.",titleAccent:"ಸಾಧಿಸಿ.",sub:"ನಿಮ್ಮ ವೆಬ್‌ಗಾಗಿ ಉಪಯುಕ್ತ ಮುಖಪುಟ.",search:"ಹುಡುಕಿ",home:"⌂ ಮನೆ",tools:"⚒ ಉಪಕರಣಗಳು",explore:"✦ ಅನ್ವೇಷಿಸಿ",privacy:"◈ ಗೌಪ್ಯತೆ",quick:"ತ್ವರಿತ ಪ್ರವೇಶ",addSite:"＋ ತಾಣ ಸೇರಿಸಿ",bookmarks:"ನಿಮ್ಮ ಬುಕ್‌ಮಾರ್ಕ್‌ಗಳು",manage:"ಎಲ್ಲವನ್ನೂ ನೋಡಿ",controls:"ಬ್ರೌಸರ್ ನಿಯಂತ್ರಣಗಳು",tap:"ತೆರೆಯಲು ಟ್ಯಾಪ್ ಮಾಡಿ",shortcuts:"ವೈಯಕ್ತಿಕ ಶಾರ್ಟ್‌ಕಟ್‌ಗಳು",personalize:"ಮುಖಪುಟವನ್ನು ನಿಮ್ಮದಾಗಿಸಿ",toolbox:"ಅಂತರ್ನಿರ್ಮಿತ ಸಾಧನಗಳು",directory:"ವೆಬ್‌ಸೈಟ್‌ಗಳನ್ನು ಅನ್ವೇಷಿಸಿ",privacyDash:"ಗೌಪ್ಯತೆ ಡ್ಯಾಶ್‌ಬೋರ್ಡ್",promoTitle:"Hari ಅವರ ಇತರ ಪ್ರಾಜೆಕ್ಟ್‌ಗಳು",promoText:"Hari ರಚಿಸಿದ ಉಪಯುಕ್ತ ಸಾಧನಗಳು ಮತ್ತು ಸೃಜನಾತ್ಮಕ ಪ್ರಾಜೆಕ್ಟ್‌ಗಳು.",websites:"Hari ವೆಬ್‌ಸೈಟ್‌ಗಳು",follow:"@inspiring_creatives_minds ಅನ್ನು ಅನುಸರಿಸಿ",followSub:"ಶಿಕ್ಷಣ, ಸೃಜನಶೀಲತೆ, ಮಾಹಿತಿ ಮತ್ತು ಪ್ರೇರಣೆ",morning:"ಶುಭೋದಯ",afternoon:"ಶುಭ ಮಧ್ಯಾಹ್ನ",evening:"ಶುಭ ಸಂಜೆ",ready:"ನಿಮ್ಮ ಶಾರ್ಟ್‌ಕಟ್‌ಗಳು, ಸಾಧನಗಳು ಮತ್ತು ಬುಕ್‌ಮಾರ್ಕ್‌ಗಳು ಸಿದ್ಧ",localTime:"ಸ್ಥಳೀಯ ಸಮಯ"},
 bn:{brandSub:"আপনার ওয়েব, আপনার জগৎ — গোপনীয়তা আগে",eyebrow:"আরও ভালো শুরু",titleTop:"খুঁজুন। শিখুন।",titleAccent:"এগিয়ে চলুন।",sub:"আপনার ওয়েবের জন্য একটি সহজ হোম পেজ।",search:"খুঁজুন",home:"⌂ হোম",tools:"⚒ টুলস",explore:"✦ ঘুরে দেখুন",privacy:"◈ গোপনীয়তা",quick:"দ্রুত প্রবেশ",addSite:"＋ সাইট যোগ করুন",bookmarks:"আপনার বুকমার্ক",manage:"সব দেখুন",controls:"ব্রাউজার নিয়ন্ত্রণ",tap:"খুলতে ট্যাপ করুন",shortcuts:"ব্যক্তিগত শর্টকাট",personalize:"হোম পেজ নিজের মতো সাজান",toolbox:"অন্তর্নির্মিত টুলবক্স",directory:"ওয়েবসাইট খুঁজুন",privacyDash:"গোপনীয়তা ড্যাশবোর্ড",promoTitle:"Hari-এর আরও কিছু",promoText:"Hari-এর তৈরি দরকারি টুল ও সৃজনশীল প্রকল্প। পছন্দ হলে তবেই খুলুন।",websites:"Hari-এর ওয়েবসাইট",follow:"@inspiring_creatives_minds অনুসরণ করুন",followSub:"শিক্ষা, সৃজনশীলতা, তথ্য ও অনুপ্রেরণা",morning:"সুপ্রভাত",afternoon:"শুভ অপরাহ্ণ",evening:"শুভ সন্ধ্যা",ready:"আপনার শর্টকাট, টুল ও বুকমার্ক প্রস্তুত",localTime:"স্থানীয় সময়"},
 mr:{brandSub:"तुमचे वेब, तुमची जागा — गोपनीयतेला प्राधान्य",eyebrow:"एक चांगली सुरुवात",titleTop:"शोधा. शिका.",titleAccent:"पुढे चला.",sub:"तुमच्या वेब जगासाठी उपयुक्त मुख्यपृष्ठ.",search:"शोधा",home:"⌂ मुख्यपृष्ठ",tools:"⚒ साधने",explore:"✦ एक्सप्लोर",privacy:"◈ गोपनीयता",quick:"जलद प्रवेश",addSite:"＋ साइट जोडा",bookmarks:"तुमचे बुकमार्क",manage:"सर्व पहा",controls:"ब्राउझर नियंत्रणे",tap:"उघडण्यासाठी टॅप करा",shortcuts:"वैयक्तिक शॉर्टकट",personalize:"मुख्यपृष्ठ तुमच्या आवडीनुसार ठेवा",toolbox:"अंगभूत साधनपेटी",directory:"वेबसाइट शोधा",privacyDash:"गोपनीयता डॅशबोर्ड",promoTitle:"Hari चे आणखी प्रकल्प",promoText:"Hari ने तयार केलेली उपयुक्त साधने आणि सर्जनशील प्रकल्प. निवडल्यावरच उघडा.",websites:"Hari च्या वेबसाइट्स",follow:"@inspiring_creatives_minds फॉलो करा",followSub:"शिक्षण, सर्जनशीलता, तथ्ये आणि प्रेरणा",morning:"शुभ सकाळ",afternoon:"शुभ दुपार",evening:"शुभ संध्याकाळ",ready:"तुमचे शॉर्टकट, साधने आणि बुकमार्क तयार आहेत",localTime:"स्थानिक वेळ"},
 gu:{brandSub:"તમારું વેબ, તમારી જગ્યા — ગોપનીયતા પ્રથમ",eyebrow:"વધુ સારી શરૂઆત",titleTop:"શોધો. શીખો.",titleAccent:"આગળ વધો.",sub:"તમારા વેબ માટે ઉપયોગી હોમ પેજ.",search:"શોધો",home:"⌂ હોમ",tools:"⚒ સાધનો",explore:"✦ શોધખોળ",privacy:"◈ ગોપનીયતા",quick:"ઝડપી પ્રવેશ",addSite:"＋ સાઇટ ઉમેરો",bookmarks:"તમારા બુકમાર્ક્સ",manage:"બધું જુઓ",controls:"બ્રાઉઝર નિયંત્રણો",tap:"ખોલવા માટે ટૅપ કરો",shortcuts:"વ્યક્તિગત શોર્ટકટ્સ",personalize:"હોમ પેજને તમારી પસંદ મુજબ બનાવો",toolbox:"અંતર્નિર્મિત સાધનપેટી",directory:"વેબસાઇટ શોધો",privacyDash:"ગોપનીયતા ડેશબોર્ડ",promoTitle:"Hariના વધુ પ્રોજેક્ટ્સ",promoText:"Hari દ્વારા બનાવેલ ઉપયોગી સાધનો અને સર્જનાત્મક પ્રોજેક્ટ્સ. તમે પસંદ કરો ત્યારે જ ખોલો.",websites:"Hariની વેબસાઇટ્સ",follow:"@inspiring_creatives_minds ને ફોલો કરો",followSub:"શિક્ષણ, સર્જનાત્મકતા, તથ્યો અને પ્રેરણા",morning:"સુપ્રભાત",afternoon:"શુભ બપોર",evening:"શુભ સાંજ",ready:"તમારા શોર્ટકટ્સ, સાધનો અને બુકમાર્ક્સ તૈયાર છે",localTime:"સ્થાનિક સમય"},
 pa:{brandSub:"ਤੁਹਾਡਾ ਵੈੱਬ, ਤੁਹਾਡੀ ਥਾਂ — ਨਿੱਜਤਾ ਪਹਿਲਾਂ",eyebrow:"ਇੱਕ ਬਿਹਤਰ ਸ਼ੁਰੂਆਤ",titleTop:"ਖੋਜੋ। ਸਿੱਖੋ।",titleAccent:"ਅੱਗੇ ਵਧੋ।",sub:"ਤੁਹਾਡੇ ਵੈੱਬ ਲਈ ਇੱਕ ਲਾਭਦਾਇਕ ਹੋਮ ਪੇਜ।",search:"ਖੋਜੋ",home:"⌂ ਘਰ",tools:"⚒ ਟੂਲ",explore:"✦ ਵੇਖੋ",privacy:"◈ ਨਿੱਜਤਾ",quick:"ਤੁਰੰਤ ਪਹੁੰਚ",addSite:"＋ ਸਾਈਟ ਜੋੜੋ",bookmarks:"ਤੁਹਾਡੇ ਬੁੱਕਮਾਰਕ",manage:"ਸਭ ਵੇਖੋ",controls:"ਬ੍ਰਾਊਜ਼ਰ ਨਿਯੰਤਰਣ",tap:"ਖੋਲ੍ਹਣ ਲਈ ਟੈਪ ਕਰੋ",shortcuts:"ਨਿੱਜੀ ਸ਼ਾਰਟਕੱਟ",personalize:"ਹੋਮ ਪੇਜ ਆਪਣੀ ਪਸੰਦ ਅਨੁਸਾਰ ਬਣਾਓ",toolbox:"ਅੰਦਰੂਨੀ ਟੂਲਬਾਕਸ",directory:"ਵੈੱਬਸਾਈਟਾਂ ਲੱਭੋ",privacyDash:"ਨਿੱਜਤਾ ਡੈਸ਼ਬੋਰਡ",promoTitle:"Hari ਦੇ ਹੋਰ ਪ੍ਰੋਜੈਕਟ",promoText:"Hari ਵੱਲੋਂ ਬਣਾਏ ਲਾਭਦਾਇਕ ਟੂਲ ਅਤੇ ਰਚਨਾਤਮਕ ਪ੍ਰੋਜੈਕਟ। ਆਪਣੀ ਚੋਣ ਨਾਲ ਹੀ ਖੋਲ੍ਹੋ।",websites:"Hari ਦੀਆਂ ਵੈੱਬਸਾਈਟਾਂ",follow:"@inspiring_creatives_minds ਨੂੰ ਫਾਲੋ ਕਰੋ",followSub:"ਸਿੱਖਿਆ, ਰਚਨਾਤਮਕਤਾ, ਤੱਥ ਅਤੇ ਪ੍ਰੇਰਣਾ",morning:"ਸ਼ੁਭ ਸਵੇਰ",afternoon:"ਸ਼ੁਭ ਦੁਪਹਿਰ",evening:"ਸ਼ੁਭ ਸ਼ਾਮ",ready:"ਤੁਹਾਡੇ ਸ਼ਾਰਟਕੱਟ, ਟੂਲ ਅਤੇ ਬੁੱਕਮਾਰਕ ਤਿਆਰ ਹਨ",localTime:"ਸਥਾਨਕ ਸਮਾਂ"},
 ur:{brandSub:"آپ کی ویب، آپ کی جگہ — رازداری مقدم",eyebrow:"ایک بہتر آغاز",titleTop:"تلاش کریں۔ سیکھیں۔",titleAccent:"آگے بڑھیں۔",sub:"آپ کی ویب دنیا کے لیے ایک مفید ہوم پیج۔",search:"تلاش",home:"⌂ گھر",tools:"⚒ ٹولز",explore:"✦ دریافت",privacy:"◈ رازداری",quick:"فوری رسائی",addSite:"＋ سائٹ شامل کریں",bookmarks:"آپ کے بک مارکس",manage:"سب دیکھیں",controls:"براؤزر کنٹرولز",tap:"کھولنے کے لیے ٹیپ کریں",shortcuts:"ذاتی شارٹ کٹس",personalize:"ہوم پیج اپنی پسند کے مطابق بنائیں",toolbox:"بلٹ اِن ٹول باکس",directory:"ویب سائٹ تلاش کریں",privacyDash:"رازداری ڈیش بورڈ",promoTitle:"Hari کے مزید پروجیکٹس",promoText:"Hari کے بنائے مفید ٹولز اور تخلیقی پروجیکٹس۔ صرف اپنی مرضی سے کھولیں۔",websites:"Hari کی ویب سائٹس",follow:"@inspiring_creatives_minds کو فالو کریں",followSub:"تعلیم، تخلیقی صلاحیت، معلومات اور ترغیب",morning:"صبح بخیر",afternoon:"دوپہر بخیر",evening:"شام بخیر",ready:"آپ کے شارٹ کٹس، ٹولز اور بک مارکس تیار ہیں",localTime:"مقامی وقت"},
 es:{brandSub:"Tu web, tu espacio — privacidad ante todo",eyebrow:"UN MEJOR INICIO",titleTop:"Busca. Aprende.",titleAccent:"Haz que suceda.",sub:"Un inicio útil para tu mundo web.",search:"Buscar",home:"⌂ Inicio",tools:"⚒ Herramientas",explore:"✦ Explorar",privacy:"◈ Privacidad",quick:"Acceso rápido",addSite:"＋ Añadir sitio",bookmarks:"Tus marcadores",manage:"Ver todos",controls:"Controles del navegador",tap:"Toca para abrir",shortcuts:"Accesos personales",personalize:"Personaliza la página de inicio",toolbox:"Caja de herramientas",directory:"Explorar directorio",privacyDash:"Panel de privacidad",promoTitle:"Descubre más de Hari",promoText:"Herramientas útiles y proyectos creativos creados por Hari.",websites:"Sitios web de Hari",follow:"Sigue a @inspiring_creatives_minds",followSub:"Educación, creatividad, datos e inspiración",morning:"Buenos días",afternoon:"Buenas tardes",evening:"Buenas noches",ready:"Tus accesos, herramientas y marcadores están listos",localTime:"Hora local"},
 fr:{brandSub:"Votre web, votre espace — la confidentialité d’abord",eyebrow:"UN MEILLEUR DÉPART",titleTop:"Cherchez. Apprenez.",titleAccent:"Passez à l’action.",sub:"Un accueil pratique pour votre univers web.",search:"Rechercher",home:"⌂ Accueil",tools:"⚒ Outils",explore:"✦ Explorer",privacy:"◈ Confidentialité",quick:"Accès rapide",addSite:"＋ Ajouter un site",bookmarks:"Vos favoris",manage:"Tout voir",controls:"Commandes du navigateur",tap:"Touchez pour ouvrir",shortcuts:"Raccourcis personnels",personalize:"Personnalisez la page d’accueil",toolbox:"Boîte à outils intégrée",directory:"Explorer l’annuaire",privacyDash:"Tableau de confidentialité",promoTitle:"Découvrez les projets de Hari",promoText:"Des outils utiles et des projets créatifs réalisés par Hari.",websites:"Sites web de Hari",follow:"Suivre @inspiring_creatives_minds",followSub:"Éducation, créativité, faits et motivation",morning:"Bonjour",afternoon:"Bon après-midi",evening:"Bonsoir",ready:"Vos raccourcis, outils et favoris sont prêts",localTime:"Heure locale"},
        """);
    htmlBuilder.append("""
 ar:{brandSub:"عالمك على الويب — الخصوصية أولاً",eyebrow:"بداية أفضل",titleTop:"ابحث. تعلّم.",titleAccent:"حقق أهدافك.",sub:"صفحة رئيسية مفيدة لعالمك على الويب.",search:"بحث",home:"⌂ الرئيسية",tools:"⚒ الأدوات",explore:"✦ استكشف",privacy:"◈ الخصوصية",quick:"وصول سريع",addSite:"＋ أضف موقعاً",bookmarks:"إشاراتك المرجعية",manage:"عرض الكل",controls:"عناصر تحكم المتصفح",tap:"اضغط للفتح",shortcuts:"اختصارات شخصية",personalize:"خصص الصفحة الرئيسية",toolbox:"صندوق الأدوات",directory:"استكشف الدليل",privacyDash:"لوحة الخصوصية",promoTitle:"اكتشف المزيد من مشاريع Hari",promoText:"أدوات مفيدة ومشاريع إبداعية أنشأها Hari.",websites:"مواقع Hari",follow:"تابع @inspiring_creatives_minds",followSub:"التعليم والإبداع والحقائق والتحفيز",morning:"صباح الخير",afternoon:"طاب يومك",evening:"مساء الخير",ready:"اختصاراتك وأدواتك وإشاراتك المرجعية جاهزة",localTime:"التوقيت المحلي"}
};

const EXTRA_UI = {
 en: {
  https:"🔒 HTTPS-only", localTools:"🧩 Local tools", searchPlaceholder:"Search the web or enter an HTTPS site…",
  directoryPlaceholder:"Find a website or category…", noMatches:"No matches. Try another search.",
  addShortcut:"＋ Add a shortcut", resetShortcuts:"Reset local shortcuts", wallpaperTitle:"Offline wallpapers",
  wallpaperSub:"Ten built-in backgrounds. No downloads or external image requests.", deviceOnly:"On-device",
  wallpapers:["Original","Aurora","Ocean","Sunset","Forest","Midnight","Violet","Desert","Coral","Glacier","Nebula"],
  actions:[["＋ New tab","Start a fresh tab"],["▣ Tab switcher","Switch, duplicate or close tabs"],["◷ History","History is off by default"],["⇩ Downloads","Open downloaded files"],["◉ Private session","Separate private browsing"],["⚙ Settings","Privacy, search and display"],["⌕ Find on page","Search text in a webpage"],["★ Save current page","Add the page to encrypted bookmarks"],["▦ QR code","Create a QR for the current URL"],["▤ Save as PDF","Use Android's print dialog"],["↗ Share page","Share the current title and address"],["🛡 Site security","Review HTTPS and browser safeguards"]],
  emptyBookmarks:"No saved bookmarks yet. Open a website and tap ★ Save.", emptyShortcuts:"Add your first HTTPS shortcut.",
  showAll:"Show all", showFewer:"Show fewer", toolsWord:"tools", curated:"curated links", sites:"sites", links:"links",
  localNote:"The start page loads its own resources locally. Hari AI sends your prompts online only when you choose to use it."
 },
 ml: {
  https:"🔒 HTTPS മാത്രം", localTools:"🧩 പ്രാദേശിക ടൂളുകൾ", searchPlaceholder:"വെബ് തിരയുക അല്ലെങ്കിൽ HTTPS സൈറ്റ് നൽകുക…",
  directoryPlaceholder:"വെബ്സൈറ്റോ വിഭാഗമോ കണ്ടെത്തുക…", noMatches:"ഫലങ്ങളില്ല. മറ്റൊരു വാക്ക് തിരയുക.",
  addShortcut:"＋ കുറുക്കുവഴി ചേർക്കുക", resetShortcuts:"പ്രാദേശിക കുറുക്കുവഴികൾ പുനഃസജ്ജമാക്കുക", wallpaperTitle:"ഓഫ്‌ലൈൻ വാൾപേപ്പറുകൾ",
  wallpaperSub:"പത്ത് ഉൾപ്പെടുത്തിയ പശ്ചാത്തലങ്ങൾ. ഡൗൺലോഡുകളോ പുറം ഇമേജ് അഭ്യർത്ഥനകളോ ഇല്ല.", deviceOnly:"ഉപകരണത്തിൽ",
  wallpapers:["ഒറിജിനൽ","ഓറോറ","സമുദ്രം","സൂര്യാസ്തമയം","വനം","അർധരാത്രി","വയലറ്റ്","മരുഭൂമി","കോറൽ","ഹിമാനി","നെബുല"],
  actions:[["＋ പുതിയ ടാബ്","പുതിയ ടാബ് തുറക്കുക"],["▣ ടാബുകൾ","മാറ്റുക, പകർപ്പുണ്ടാക്കുക, അടയ്ക്കുക"],["◷ ചരിത്രം","ചരിത്രം ഡിഫോൾട്ടായി ഓഫ് ആണ്"],["⇩ ഡൗൺലോഡുകൾ","ഡൗൺലോഡ് ചെയ്ത ഫയലുകൾ തുറക്കുക"],["◉ സ്വകാര്യ സെഷൻ","വേറിട്ട സ്വകാര്യ ബ്രൗസിംഗ്"],["⚙ ക്രമീകരണങ്ങൾ","സ്വകാര്യത, തിരച്ചിൽ, കാഴ്ച"],["⌕ പേജിൽ കണ്ടെത്തുക","വെബ് പേജിലെ വാചകം തിരയുക"],["★ പേജ് സംരക്ഷിക്കുക","എൻക്രിപ്റ്റ് ചെയ്ത ബുക്ക്മാർക്കിൽ ചേർക്കുക"],["▦ QR കോഡ്","നിലവിലെ URL-ന് QR ഉണ്ടാക്കുക"],["▤ PDF ആയി സൂക്ഷിക്കുക","Android പ്രിന്റ് ഡയലോഗ് ഉപയോഗിക്കുക"],["↗ പേജ് പങ്കിടുക","പേജിന്റെ പേരും വിലാസവും പങ്കിടുക"],["🛡 സൈറ്റ് സുരക്ഷ","HTTPS, ബ്രൗസർ സുരക്ഷ പരിശോധിക്കുക"]],
  emptyBookmarks:"ബുക്ക്മാർക്കുകളില്ല. ഒരു സൈറ്റ് തുറന്ന് ★ അമർത്തുക.", emptyShortcuts:"ആദ്യ HTTPS കുറുക്കുവഴി ചേർക്കുക.",
  showAll:"എല്ലാ", showFewer:"കുറച്ച് മാത്രം", toolsWord:"ടൂളുകൾ കാണിക്കുക", curated:"തിരഞ്ഞെടുത്ത ലിങ്കുകൾ", sites:"സൈറ്റുകൾ", links:"ലിങ്കുകൾ",
  localNote:"ഹോം പേജിന്റെ വിഭവങ്ങൾ ഉപകരണത്തിൽ തന്നെ ലോഡ് ചെയ്യുന്നു. നിങ്ങൾ Hari AI ഉപയോഗിക്കുമ്പോൾ മാത്രമാണ് നിങ്ങളുടെ ചോദ്യങ്ങൾ ഓൺലൈനിൽ അയയ്ക്കുന്നത്."
 },
 hi: {
  https:"🔒 केवल HTTPS", localTools:"🧩 स्थानीय टूल", searchPlaceholder:"वेब खोजें या HTTPS साइट दर्ज करें…",
  directoryPlaceholder:"वेबसाइट या श्रेणी खोजें…", noMatches:"कोई परिणाम नहीं। दूसरी खोज आज़माएँ।",
  addShortcut:"＋ शॉर्टकट जोड़ें", resetShortcuts:"स्थानीय शॉर्टकट रीसेट करें", wallpaperTitle:"ऑफ़लाइन वॉलपेपर",
  wallpaperSub:"दस अंतर्निहित पृष्ठभूमियाँ। कोई डाउनलोड या बाहरी छवि अनुरोध नहीं।", deviceOnly:"डिवाइस पर",
  wallpapers:["मूल","ऑरोरा","समुद्र","सूर्यास्त","जंगल","आधी रात","बैंगनी","रेगिस्तान","कोरल","हिमनद","नीहारिका"],
  actions:[["＋ नया टैब","नया टैब शुरू करें"],["▣ टैब स्विचर","टैब बदलें, कॉपी करें या बंद करें"],["◷ इतिहास","इतिहास डिफ़ॉल्ट रूप से बंद है"],["⇩ डाउनलोड","डाउनलोड की गई फ़ाइलें खोलें"],["◉ निजी सत्र","अलग निजी ब्राउज़िंग"],["⚙ सेटिंग","गोपनीयता, खोज और प्रदर्शन"],["⌕ पेज पर खोजें","वेबपेज में टेक्स्ट खोजें"],["★ पेज सहेजें","एन्क्रिप्टेड बुकमार्क में जोड़ें"],["▦ QR कोड","वर्तमान URL का QR बनाएँ"],["▤ PDF के रूप में सहेजें","Android प्रिंट विकल्प उपयोग करें"],["↗ पेज साझा करें","वर्तमान शीर्षक और पता साझा करें"],["🛡 साइट सुरक्षा","HTTPS और ब्राउज़र सुरक्षा देखें"]],
  emptyBookmarks:"अभी कोई बुकमार्क नहीं। साइट खोलें और ★ दबाएँ।", emptyShortcuts:"अपना पहला HTTPS शॉर्टकट जोड़ें।",
  showAll:"सभी दिखाएँ", showFewer:"कम दिखाएँ", toolsWord:"टूल", curated:"चुने हुए लिंक", sites:"साइटें", links:"लिंक",
  localNote:"होम पेज की सामग्री डिवाइस पर स्थानीय रूप से लोड होती है। Hari AI आपके प्रश्न केवल तभी ऑनलाइन भेजता है जब आप उसका उपयोग चुनते हैं।"
 },
 ta: {
  https:"🔒 HTTPS மட்டும்", localTools:"🧩 உள்ளூர் கருவிகள்", searchPlaceholder:"இணையத்தில் தேடவும் அல்லது HTTPS தளத்தை உள்ளிடவும்…",
  directoryPlaceholder:"இணையதளம் அல்லது வகையைத் தேடவும்…", noMatches:"முடிவுகள் இல்லை. வேறு தேடலை முயற்சிக்கவும்.",
  addShortcut:"＋ குறுக்குவழியைச் சேர்", resetShortcuts:"உள்ளூர் குறுக்குவழிகளை மீட்டமை", wallpaperTitle:"ஆஃப்லைன் வால்பேப்பர்கள்",
  wallpaperSub:"பத்து உள்ளமைந்த பின்னணிகள். பதிவிறக்கங்களோ வெளிப்புறப் படக் கோரிக்கைகளோ இல்லை.", deviceOnly:"சாதனத்தில்",
  wallpapers:["அசல்","ஆரோரா","கடல்","சூரிய அஸ்தமனம்","காடு","நள்ளிரவு","ஊதா","பாலைவனம்","பவளம்","பனிப்பாறை","நெபுலா"],
  actions:[["＋ புதிய தாவல்","புதிய தாவலைத் தொடங்கு"],["▣ தாவல் மாற்றி","தாவல்களை மாற்று, நகலெடு அல்லது மூடு"],["◷ வரலாறு","வரலாறு இயல்பாக முடக்கப்பட்டுள்ளது"],["⇩ பதிவிறக்கங்கள்","பதிவிறக்கிய கோப்புகளைத் திற"],["◉ தனிப்பட்ட அமர்வு","தனியான தனிப்பட்ட உலாவல்"],["⚙ அமைப்புகள்","தனியுரிமை, தேடல், காட்சி"],["⌕ பக்கத்தில் தேடு","இணையப்பக்க உரையைத் தேடு"],["★ பக்கத்தைச் சேமி","குறியாக்கப்பட்ட புக்மார்க்கில் சேர்"],["▦ QR குறியீடு","தற்போதைய URL-க்கு QR உருவாக்கு"],["▤ PDF ஆகச் சேமி","Android அச்சு உரையாடலைப் பயன்படுத்து"],["↗ பக்கத்தைப் பகிர்","பக்கத் தலைப்பு மற்றும் முகவரியைப் பகிர்"],["🛡 தளப் பாதுகாப்பு","HTTPS மற்றும் உலாவிப் பாதுகாப்பைச் சரிபார்"]],
  emptyBookmarks:"புக்மார்க்குகள் இல்லை. தளத்தைத் திறந்து ★ தட்டவும்.", emptyShortcuts:"உங்கள் முதல் HTTPS குறுக்குவழியைச் சேர்க்கவும்.",
  showAll:"அனைத்தையும் காட்டு", showFewer:"குறைவாகக் காட்டு", toolsWord:"கருவிகள்", curated:"தேர்ந்தெடுத்த இணைப்புகள்", sites:"தளங்கள்", links:"இணைப்புகள்",
  localNote:"முகப்புப் பக்கத்தின் வளங்கள் சாதனத்திலேயே ஏற்றப்படுகின்றன. நீங்கள் Hari AI-ஐத் தேர்ந்தெடுத்து பயன்படுத்தும்போது மட்டுமே உங்கள் கேள்விகள் இணையத்தில் அனுப்பப்படும்."
 },
 te: {
  https:"🔒 HTTPS మాత్రమే", localTools:"🧩 స్థానిక సాధనాలు", searchPlaceholder:"వెబ్‌లో వెతకండి లేదా HTTPS సైట్ నమోదు చేయండి…",
  directoryPlaceholder:"వెబ్‌సైట్ లేదా వర్గాన్ని వెతకండి…", noMatches:"ఫలితాలు లేవు. మరోసారి వెతకండి.",
  addShortcut:"＋ షార్ట్‌కట్ జోడించు", resetShortcuts:"స్థానిక షార్ట్‌కట్‌లను రీసెట్ చేయి", wallpaperTitle:"ఆఫ్‌లైన్ వాల్‌పేపర్లు",
  wallpaperSub:"పది అంతర్నిర్మిత నేపథ్యాలు. డౌన్‌లోడ్‌లు లేదా బాహ్య చిత్ర అభ్యర్థనలు లేవు.", deviceOnly:"పరికరంలో",
  wallpapers:["అసలు","ఆరోరా","సముద్రం","సూర్యాస్తమయం","అడవి","అర్ధరాత్రి","వైలెట్","ఎడారి","కోరల్","హిమానీనదం","నెబ్యులా"],
  actions:[["＋ కొత్త ట్యాబ్","కొత్త ట్యాబ్ ప్రారంభించు"],["▣ ట్యాబ్ స్విచ్చర్","ట్యాబ్‌లను మార్చు, నకలు చేయి లేదా మూసివేయి"],["◷ చరిత్ర","చరిత్ర డిఫాల్ట్‌గా ఆఫ్‌లో ఉంటుంది"],["⇩ డౌన్‌లోడ్‌లు","డౌన్‌లోడ్ చేసిన ఫైళ్లను తెరువు"],["◉ ప్రైవేట్ సెషన్","వేరే ప్రైవేట్ బ్రౌజింగ్"],["⚙ సెట్టింగ్‌లు","గోప్యత, శోధన, ప్రదర్శన"],["⌕ పేజీలో కనుగొను","వెబ్‌పేజీలో వచనాన్ని వెతుకు"],["★ పేజీని సేవ్ చేయి","ఎన్‌క్రిప్ట్ చేసిన బుక్‌మార్క్‌లకు జోడించు"],["▦ QR కోడ్","ప్రస్తుత URL కోసం QR సృష్టించు"],["▤ PDFగా సేవ్ చేయి","Android ప్రింట్ డైలాగ్ ఉపయోగించు"],["↗ పేజీని పంచుకో","పేజీ పేరు, చిరునామాను పంచుకో"],["🛡 సైట్ భద్రత","HTTPS, బ్రౌజర్ రక్షణలను సమీక్షించు"]],
  emptyBookmarks:"బుక్‌మార్క్‌లు లేవు. సైట్ తెరిచి ★ నొక్కండి.", emptyShortcuts:"మీ మొదటి HTTPS షార్ట్‌కట్‌ను జోడించండి.",
  showAll:"అన్నీ చూపించు", showFewer:"తక్కువ చూపించు", toolsWord:"సాధనాలు", curated:"ఎంపిక చేసిన లింకులు", sites:"సైట్లు", links:"లింకులు",
  localNote:"హోమ్ పేజీ వనరులు పరికరంలోనే లోడ్ అవుతాయి. మీరు Hari AI ఉపయోగించడానికి ఎంచుకున్నప్పుడు మాత్రమే మీ ప్రశ్నలు ఆన్‌లైన్‌లో పంపబడతాయి."
 },
 kn: {
  https:"🔒 HTTPS ಮಾತ್ರ", localTools:"🧩 ಸ್ಥಳೀಯ ಸಾಧನಗಳು", searchPlaceholder:"ವೆಬ್‌ನಲ್ಲಿ ಹುಡುಕಿ ಅಥವಾ HTTPS ತಾಣವನ್ನು ನಮೂದಿಸಿ…",
  directoryPlaceholder:"ವೆಬ್‌ಸೈಟ್ ಅಥವಾ ವರ್ಗ ಹುಡುಕಿ…", noMatches:"ಫಲಿತಾಂಶಗಳಿಲ್ಲ. ಮತ್ತೊಂದು ಹುಡುಕಾಟ ಪ್ರಯತ್ನಿಸಿ.",
  addShortcut:"＋ ಶಾರ್ಟ್‌ಕಟ್ ಸೇರಿಸಿ", resetShortcuts:"ಸ್ಥಳೀಯ ಶಾರ್ಟ್‌ಕಟ್‌ಗಳನ್ನು ಮರುಹೊಂದಿಸಿ", wallpaperTitle:"ಆಫ್‌ಲೈನ್ ವಾಲ್‌ಪೇಪರ್‌ಗಳು",
  wallpaperSub:"ಹತ್ತು ಅಂತರ್ನಿರ್ಮಿತ ಹಿನ್ನೆಲೆಗಳು. ಡೌನ್‌ಲೋಡ್ ಅಥವಾ ಬಾಹ್ಯ ಚಿತ್ರ ವಿನಂತಿಗಳಿಲ್ಲ.", deviceOnly:"ಸಾಧನದಲ್ಲೇ",
  wallpapers:["ಮೂಲ","ಔರೋರಾ","ಸಮುದ್ರ","ಸೂರ್ಯಾಸ್ತ","ಕಾಡು","ಮಧ್ಯರಾತ್ರಿ","ನೇರಳೆ","ಮರುಭೂಮಿ","ಕೋರಲ್","ಹಿಮನದಿ","ನೆಬ್ಯುಲಾ"],
  actions:[["＋ ಹೊಸ ಟ್ಯಾಬ್","ಹೊಸ ಟ್ಯಾಬ್ ಪ್ರಾರಂಭಿಸಿ"],["▣ ಟ್ಯಾಬ್ ಬದಲಿಸಿ","ಟ್ಯಾಬ್ ಬದಲಿಸಿ, ನಕಲಿಸಿ ಅಥವಾ ಮುಚ್ಚಿ"],["◷ ಇತಿಹಾಸ","ಇತಿಹಾಸವು ಪೂರ್ವನಿಯೋಜಿತವಾಗಿ ಆಫ್"],["⇩ ಡೌನ್‌ಲೋಡ್‌ಗಳು","ಡೌನ್‌ಲೋಡ್ ಮಾಡಿದ ಫೈಲ್ ತೆರೆಯಿರಿ"],["◉ ಖಾಸಗಿ ಸೆಷನ್","ಪ್ರತ್ಯೇಕ ಖಾಸಗಿ ಬ್ರೌಸಿಂಗ್"],["⚙ ಸೆಟ್ಟಿಂಗ್‌ಗಳು","ಗೌಪ್ಯತೆ, ಹುಡುಕಾಟ, ಪ್ರದರ್ಶನ"],["⌕ ಪುಟದಲ್ಲಿ ಹುಡುಕಿ","ವೆಬ್‌ಪುಟದ ಪಠ್ಯ ಹುಡುಕಿ"],["★ ಪುಟ ಉಳಿಸಿ","ಎನ್‌ಕ್ರಿಪ್ಟ್ ಮಾಡಿದ ಬುಕ್‌ಮಾರ್ಕ್‌ಗೆ ಸೇರಿಸಿ"],["▦ QR ಕೋಡ್","ಪ್ರಸ್ತುತ URL ಗಾಗಿ QR ರಚಿಸಿ"],["▤ PDF ಆಗಿ ಉಳಿಸಿ","Android ಮುದ್ರಣ ಸಂವಾದ ಬಳಸಿ"],["↗ ಪುಟ ಹಂಚಿಕೊಳ್ಳಿ","ಪುಟದ ಶೀರ್ಷಿಕೆ ಮತ್ತು ವಿಳಾಸ ಹಂಚಿಕೊಳ್ಳಿ"],["🛡 ತಾಣದ ಭದ್ರತೆ","HTTPS ಮತ್ತು ಬ್ರೌಸರ್ ರಕ್ಷಣೆಯನ್ನು ಪರಿಶೀಲಿಸಿ"]],
  emptyBookmarks:"ಬುಕ್‌ಮಾರ್ಕ್‌ಗಳಿಲ್ಲ. ತಾಣ ತೆರೆಯಿರಿ ಮತ್ತು ★ ಒತ್ತಿ.", emptyShortcuts:"ನಿಮ್ಮ ಮೊದಲ HTTPS ಶಾರ್ಟ್‌ಕಟ್ ಸೇರಿಸಿ.",
  showAll:"ಎಲ್ಲ ತೋರಿಸಿ", showFewer:"ಕಡಿಮೆ ತೋರಿಸಿ", toolsWord:"ಸಾಧನಗಳು", curated:"ಆಯ್ದ ಲಿಂಕ್‌ಗಳು", sites:"ತಾಣಗಳು", links:"ಲಿಂಕ್‌ಗಳು",
  localNote:"ಮುಖಪುಟದ ಸಂಪನ್ಮೂಲಗಳು ಸಾಧನದಲ್ಲೇ ಲೋಡ್ ಆಗುತ್ತವೆ. ನೀವು Hari AI ಬಳಸಲು ಆಯ್ಕೆ ಮಾಡಿದಾಗ ಮಾತ್ರ ನಿಮ್ಮ ಪ್ರಶ್ನೆಗಳು ಆನ್‌ಲೈನ್‌ಗೆ ಕಳುಹಿಸಲಾಗುತ್ತವೆ."
 },
 es: {
  https:"🔒 Solo HTTPS", localTools:"🧩 Herramientas locales", searchPlaceholder:"Busca en la web o introduce un sitio HTTPS…",
  directoryPlaceholder:"Busca un sitio web o una categoría…", noMatches:"No hay resultados. Prueba otra búsqueda.",
  addShortcut:"＋ Añadir acceso", resetShortcuts:"Restablecer accesos locales", wallpaperTitle:"Fondos sin conexión",
  wallpaperSub:"Diez fondos integrados. Sin descargas ni solicitudes de imágenes externas.", deviceOnly:"En el dispositivo",
  wallpapers:["Original","Aurora","Océano","Atardecer","Bosque","Medianoche","Violeta","Desierto","Coral","Glaciar","Nebulosa"],
  actions:[["＋ Nueva pestaña","Abrir una pestaña nueva"],["▣ Cambiar pestaña","Cambiar, duplicar o cerrar pestañas"],["◷ Historial","El historial está desactivado por defecto"],["⇩ Descargas","Abrir archivos descargados"],["◉ Sesión privada","Navegación privada separada"],["⚙ Ajustes","Privacidad, búsqueda y visualización"],["⌕ Buscar en página","Buscar texto en una página web"],["★ Guardar página","Añadir a marcadores cifrados"],["▦ Código QR","Crear un QR para la URL actual"],["▤ Guardar como PDF","Usar el diálogo de impresión de Android"],["↗ Compartir página","Compartir título y dirección actuales"],["🛡 Seguridad del sitio","Revisar HTTPS y protecciones del navegador"]],
  emptyBookmarks:"Aún no hay marcadores. Abre un sitio y pulsa ★ Guardar.", emptyShortcuts:"Añade tu primer acceso HTTPS.",
  showAll:"Mostrar todo", showFewer:"Mostrar menos", toolsWord:"herramientas", curated:"enlaces seleccionados", sites:"sitios", links:"enlaces",
  localNote:"Los recursos de inicio se cargan localmente. Hari AI solo envía tus preguntas por internet cuando eliges utilizarlo."
 },
 fr: {
  https:"🔒 HTTPS uniquement", localTools:"🧩 Outils locaux", searchPlaceholder:"Recherchez sur le web ou saisissez un site HTTPS…",
  directoryPlaceholder:"Rechercher un site ou une catégorie…", noMatches:"Aucun résultat. Essayez une autre recherche.",
  addShortcut:"＋ Ajouter un raccourci", resetShortcuts:"Réinitialiser les raccourcis locaux", wallpaperTitle:"Fonds d’écran hors ligne",
  wallpaperSub:"Dix arrière-plans intégrés. Aucun téléchargement ni requête d’image externe.", deviceOnly:"Sur l’appareil",
  wallpapers:["Original","Aurore","Océan","Coucher de soleil","Forêt","Minuit","Violet","Désert","Corail","Glacier","Nébuleuse"],
  actions:[["＋ Nouvel onglet","Ouvrir un nouvel onglet"],["▣ Changer d’onglet","Changer, dupliquer ou fermer des onglets"],["◷ Historique","L’historique est désactivé par défaut"],["⇩ Téléchargements","Ouvrir les fichiers téléchargés"],["◉ Session privée","Navigation privée séparée"],["⚙ Paramètres","Confidentialité, recherche et affichage"],["⌕ Rechercher dans la page","Chercher du texte dans une page web"],["★ Enregistrer la page","Ajouter aux favoris chiffrés"],["▦ Code QR","Créer un QR pour l’URL actuelle"],["▤ Enregistrer en PDF","Utiliser l’impression Android"],["↗ Partager la page","Partager le titre et l’adresse actuels"],["🛡 Sécurité du site","Vérifier HTTPS et les protections du navigateur"]],
  emptyBookmarks:"Aucun favori. Ouvrez un site et touchez ★ Enregistrer.", emptyShortcuts:"Ajoutez votre premier raccourci HTTPS.",
  showAll:"Tout afficher", showFewer:"Afficher moins", toolsWord:"outils", curated:"liens sélectionnés", sites:"sites", links:"liens",
  localNote:"Les ressources de la page d’accueil se chargent localement. Hari AI n’envoie vos demandes en ligne que lorsque vous choisissez de l’utiliser."
 },
 ar: {
  https:"🔒 HTTPS فقط", localTools:"🧩 أدوات محلية", searchPlaceholder:"ابحث في الويب أو أدخل موقع HTTPS…",
  directoryPlaceholder:"ابحث عن موقع أو فئة…", noMatches:"لا توجد نتائج. جرّب بحثاً آخر.",
  addShortcut:"＋ إضافة اختصار", resetShortcuts:"إعادة ضبط الاختصارات المحلية", wallpaperTitle:"خلفيات دون اتصال",
  wallpaperSub:"عشر خلفيات مضمّنة. لا تنزيلات ولا طلبات صور خارجية.", deviceOnly:"على الجهاز",
  wallpapers:["الأصلية","الشفق","المحيط","الغروب","الغابة","منتصف الليل","بنفسجي","الصحراء","مرجاني","جليدي","سديم"],
  actions:[["＋ علامة تبويب جديدة","ابدأ علامة تبويب جديدة"],["▣ مبدّل علامات التبويب","بدّل أو انسخ أو أغلق علامات التبويب"],["◷ السجل","السجل متوقف افتراضياً"],["⇩ التنزيلات","افتح الملفات التي تم تنزيلها"],["◉ جلسة خاصة","تصفح خاص منفصل"],["⚙ الإعدادات","الخصوصية والبحث والعرض"],["⌕ البحث في الصفحة","ابحث عن نص داخل صفحة ويب"],["★ حفظ الصفحة","أضف إلى العلامات المشفرة"],["▦ رمز QR","أنشئ رمزاً للرابط الحالي"],["▤ حفظ PDF","استخدم نافذة طباعة Android"],["↗ مشاركة الصفحة","شارك العنوان والرابط الحاليين"],["🛡 أمان الموقع","راجع HTTPS وحماية المتصفح"]],
  emptyBookmarks:"لا توجد علامات محفوظة. افتح موقعاً واضغط ★ حفظ.", emptyShortcuts:"أضف أول اختصار HTTPS.",
  showAll:"عرض الكل", showFewer:"عرض أقل", toolsWord:"أدوات", curated:"روابط مختارة", sites:"مواقع", links:"روابط",
  localNote:"تُحمّل موارد الصفحة الرئيسية محليًا. لا يرسل Hari AI أسئلتك عبر الإنترنت إلا عندما تختار استخدامه."
 }
};

const INDIAN_EXTRA_UI = {
 bn:{https:"🔒 শুধু HTTPS",localTools:"🧩 স্থানীয় টুল",searchPlaceholder:"ওয়েবে খুঁজুন বা HTTPS ঠিকানা লিখুন…",directoryPlaceholder:"ওয়েবসাইট বা বিভাগ খুঁজুন…",noMatches:"কিছু পাওয়া যায়নি। অন্যভাবে খুঁজুন।",addShortcut:"＋ শর্টকাট যোগ করুন",resetShortcuts:"স্থানীয় শর্টকাট রিসেট করুন",wallpaperTitle:"অফলাইন ওয়ালপেপার",wallpaperSub:"ডিভাইসে থাকা পটভূমি; বাইরের ছবি ডাউনলোড হয় না।",deviceOnly:"ডিভাইসেই",emptyBookmarks:"এখনও বুকমার্ক নেই। সাইট খুলে ★ চাপুন।",emptyShortcuts:"আপনার প্রথম HTTPS শর্টকাট যোগ করুন।",showAll:"সব দেখুন",showFewer:"কম দেখুন",actions:[["＋ নতুন ট্যাব","নতুন ট্যাব খুলুন"],["▣ ট্যাব বদলান","ট্যাব বদলান, কপি করুন বা বন্ধ করুন"],["◷ ইতিহাস","ইতিহাস ডিফল্টভাবে বন্ধ"],["⇩ ডাউনলোড","ডাউনলোড করা ফাইল খুলুন"],["◉ ব্যক্তিগত সেশন","আলাদা ব্যক্তিগত ব্রাউজিং"],["⚙ সেটিংস","গোপনীয়তা, অনুসন্ধান ও প্রদর্শন"],["⌕ পৃষ্ঠায় খুঁজুন","ওয়েবপৃষ্ঠায় লেখা খুঁজুন"],["★ বর্তমান পৃষ্ঠা সংরক্ষণ","এনক্রিপ্ট করা বুকমার্কে যোগ করুন"],["▦ QR কোড","বর্তমান URL-এর QR তৈরি করুন"],["▤ PDF হিসেবে সংরক্ষণ","Android প্রিন্ট ডায়ালগ ব্যবহার করুন"],["↗ পৃষ্ঠা শেয়ার","শিরোনাম ও ঠিকানা শেয়ার করুন"],["🛡 সাইট সুরক্ষা","HTTPS ও ব্রাউজার সুরক্ষা দেখুন"]],localNote:"হোম পেজের সম্পদ ডিভাইসেই লোড হয়। আপনি ব্যবহার করতে চাইলে তবেই Hari AI-তে প্রশ্ন পাঠানো হয়।"},
 mr:{https:"🔒 फक्त HTTPS",localTools:"🧩 स्थानिक साधने",searchPlaceholder:"वेब शोधा किंवा HTTPS पत्ता टाका…",directoryPlaceholder:"वेबसाइट किंवा विभाग शोधा…",noMatches:"काही सापडले नाही. दुसरे शोधा.",addShortcut:"＋ शॉर्टकट जोडा",resetShortcuts:"स्थानिक शॉर्टकट रीसेट करा",wallpaperTitle:"ऑफलाइन वॉलपेपर",wallpaperSub:"डिव्हाइसमध्ये असलेली पार्श्वभूमी; बाहेरून प्रतिमा डाउनलोड होत नाहीत.",deviceOnly:"डिव्हाइसवर",emptyBookmarks:"अजून बुकमार्क नाहीत. साइट उघडा आणि ★ दाबा.",emptyShortcuts:"पहिला HTTPS शॉर्टकट जोडा.",showAll:"सर्व पहा",showFewer:"कमी पहा",actions:[["＋ नवीन टॅब","नवीन टॅब उघडा"],["▣ टॅब बदलणारा","टॅब बदला, प्रत बनवा किंवा बंद करा"],["◷ इतिहास","इतिहास डीफॉल्टने बंद आहे"],["⇩ डाउनलोड","डाउनलोड केलेल्या फाइल्स उघडा"],["◉ खाजगी सत्र","वेगळे खाजगी ब्राउझिंग"],["⚙ सेटिंग्ज","गोपनीयता, शोध आणि प्रदर्शन"],["⌕ पृष्ठावर शोधा","वेबपृष्ठावरील मजकूर शोधा"],["★ सध्याचे पृष्ठ जतन करा","एन्क्रिप्ट केलेल्या बुकमार्कमध्ये जोडा"],["▦ QR कोड","सध्याच्या URL साठी QR तयार करा"],["▤ PDF म्हणून जतन करा","Android प्रिंट संवाद वापरा"],["↗ पृष्ठ शेअर करा","सध्याचे शीर्षक आणि पत्ता शेअर करा"],["🛡 साइट सुरक्षा","HTTPS आणि ब्राउझर सुरक्षा तपासा"]],localNote:"मुख्यपृष्ठाची साधने डिव्हाइसवरच लोड होतात. तुम्ही निवडल्यावरच Hari AI ला प्रश्न ऑनलाइन पाठवले जातात."},
 gu:{https:"🔒 ફક્ત HTTPS",localTools:"🧩 સ્થાનિક સાધનો",searchPlaceholder:"વેબ શોધો અથવા HTTPS સરનામું લખો…",directoryPlaceholder:"વેબસાઇટ અથવા વિભાગ શોધો…",noMatches:"કંઈ મળ્યું નથી. બીજી રીતે શોધો.",addShortcut:"＋ શોર્ટકટ ઉમેરો",resetShortcuts:"સ્થાનિક શોર્ટકટ્સ ફરી સેટ કરો",wallpaperTitle:"ઑફલાઇન વૉલપેપર",wallpaperSub:"ડિવાઇસમાં રહેલી પૃષ્ઠભૂમિ; બહારથી છબીઓ ડાઉનલોડ થતી નથી.",deviceOnly:"ડિવાઇસ પર",emptyBookmarks:"હજુ બુકમાર્ક નથી. સાઇટ ખોલીને ★ દબાવો.",emptyShortcuts:"તમારો પહેલો HTTPS શોર્ટકટ ઉમેરો.",showAll:"બધું જુઓ",showFewer:"ઓછું જુઓ",actions:[["＋ નવી ટૅબ","નવી ટૅબ ખોલો"],["▣ ટૅબ બદલો","ટૅબ બદલો, નકલ કરો અથવા બંધ કરો"],["◷ ઇતિહાસ","ઇતિહાસ મૂળભૂત રીતે બંધ છે"],["⇩ ડાઉનલોડ","ડાઉનલોડ કરેલી ફાઇલો ખોલો"],["◉ ખાનગી સત્ર","અલગ ખાનગી બ્રાઉઝિંગ"],["⚙ સેટિંગ્સ","ગોપનીયતા, શોધ અને દેખાવ"],["⌕ પેજમાં શોધો","વેબપેજમાં લખાણ શોધો"],["★ હાલનું પેજ સાચવો","એન્ક્રિપ્ટેડ બુકમાર્ક્સમાં ઉમેરો"],["▦ QR કોડ","હાલના URL માટે QR બનાવો"],["▤ PDF તરીકે સાચવો","Android પ્રિન્ટ ડાયલોગ વાપરો"],["↗ પેજ શેર કરો","હાલનું શીર્ષક અને સરનામું શેર કરો"],["🛡 સાઇટ સુરક્ષા","HTTPS અને બ્રાઉઝર સુરક્ષા તપાસો"]],localNote:"હોમ પેજના સાધનો ડિવાઇસ પર જ લોડ થાય છે. તમે પસંદ કરો ત્યારે જ Hari AI ને પ્રશ્નો ઑનલાઇન મોકલવામાં આવે છે."},
 pa:{https:"🔒 ਸਿਰਫ਼ HTTPS",localTools:"🧩 ਸਥਾਨਕ ਟੂਲ",searchPlaceholder:"ਵੈੱਬ ਖੋਜੋ ਜਾਂ HTTPS ਪਤਾ ਲਿਖੋ…",directoryPlaceholder:"ਵੈੱਬਸਾਈਟ ਜਾਂ ਸ਼੍ਰੇਣੀ ਖੋਜੋ…",noMatches:"ਕੁਝ ਨਹੀਂ ਮਿਲਿਆ। ਹੋਰ ਤਰੀਕੇ ਨਾਲ ਖੋਜੋ।",addShortcut:"＋ ਸ਼ਾਰਟਕੱਟ ਜੋੜੋ",resetShortcuts:"ਸਥਾਨਕ ਸ਼ਾਰਟਕੱਟ ਰੀਸੈਟ ਕਰੋ",wallpaperTitle:"ਆਫ਼ਲਾਈਨ ਵਾਲਪੇਪਰ",wallpaperSub:"ਡਿਵਾਈਸ ਵਿੱਚ ਮੌਜੂਦ ਪਿਛੋਕੜ; ਬਾਹਰੋਂ ਤਸਵੀਰਾਂ ਡਾਊਨਲੋਡ ਨਹੀਂ ਹੁੰਦੀਆਂ।",deviceOnly:"ਡਿਵਾਈਸ ਉੱਤੇ",emptyBookmarks:"ਹਾਲੇ ਬੁੱਕਮਾਰਕ ਨਹੀਂ ਹਨ। ਸਾਈਟ ਖੋਲ੍ਹੋ ਅਤੇ ★ ਦਬਾਓ।",emptyShortcuts:"ਆਪਣਾ ਪਹਿਲਾ HTTPS ਸ਼ਾਰਟਕੱਟ ਜੋੜੋ।",showAll:"ਸਭ ਵੇਖੋ",showFewer:"ਘੱਟ ਵੇਖੋ",actions:[["＋ ਨਵੀਂ ਟੈਬ","ਨਵੀਂ ਟੈਬ ਖੋਲ੍ਹੋ"],["▣ ਟੈਬ ਬਦਲੋ","ਟੈਬ ਬਦਲੋ, ਨਕਲ ਕਰੋ ਜਾਂ ਬੰਦ ਕਰੋ"],["◷ ਇਤਿਹਾਸ","ਇਤਿਹਾਸ ਮੂਲ ਰੂਪ ਵਿੱਚ ਬੰਦ ਹੈ"],["⇩ ਡਾਊਨਲੋਡ","ਡਾਊਨਲੋਡ ਕੀਤੀਆਂ ਫਾਈਲਾਂ ਖੋਲ੍ਹੋ"],["◉ ਨਿੱਜੀ ਸੈਸ਼ਨ","ਵੱਖਰੀ ਨਿੱਜੀ ਬ੍ਰਾਊਜ਼ਿੰਗ"],["⚙ ਸੈਟਿੰਗਾਂ","ਨਿੱਜਤਾ, ਖੋਜ ਅਤੇ ਦਿੱਖ"],["⌕ ਪੰਨੇ ਉੱਤੇ ਲੱਭੋ","ਵੈੱਬ ਪੰਨੇ ਵਿੱਚ ਲਿਖਤ ਲੱਭੋ"],["★ ਮੌਜੂਦਾ ਪੰਨਾ ਸੰਭਾਲੋ","ਇਨਕ੍ਰਿਪਟ ਕੀਤੇ ਬੁੱਕਮਾਰਕਾਂ ਵਿੱਚ ਜੋੜੋ"],["▦ QR ਕੋਡ","ਮੌਜੂਦਾ URL ਲਈ QR ਬਣਾਓ"],["▤ PDF ਵਜੋਂ ਸੰਭਾਲੋ","Android ਪ੍ਰਿੰਟ ਡਾਇਲਾਗ ਵਰਤੋ"],["↗ ਪੰਨਾ ਸਾਂਝਾ ਕਰੋ","ਮੌਜੂਦਾ ਸਿਰਲੇਖ ਅਤੇ ਪਤਾ ਸਾਂਝਾ ਕਰੋ"],["🛡 ਸਾਈਟ ਸੁਰੱਖਿਆ","HTTPS ਅਤੇ ਬ੍ਰਾਊਜ਼ਰ ਸੁਰੱਖਿਆ ਵੇਖੋ"]],localNote:"ਹੋਮ ਪੇਜ ਦੇ ਸਰੋਤ ਡਿਵਾਈਸ ਉੱਤੇ ਹੀ ਲੋਡ ਹੁੰਦੇ ਹਨ। Hari AI ਨੂੰ ਸੁਨੇਹੇ ਤਦ ਹੀ ਭੇਜੇ ਜਾਂਦੇ ਹਨ ਜਦੋਂ ਤੁਸੀਂ ਇਸਨੂੰ ਚੁਣਦੇ ਹੋ।"},
 ur:{https:"🔒 صرف HTTPS",localTools:"🧩 مقامی ٹولز",searchPlaceholder:"ویب پر تلاش کریں یا HTTPS پتہ درج کریں…",directoryPlaceholder:"ویب سائٹ یا زمرہ تلاش کریں…",noMatches:"کچھ نہیں ملا۔ دوبارہ تلاش کریں۔",addShortcut:"＋ شارٹ کٹ شامل کریں",resetShortcuts:"مقامی شارٹ کٹس ری سیٹ کریں",wallpaperTitle:"آف لائن وال پیپر",wallpaperSub:"ڈیوائس میں موجود پس منظر؛ باہر سے تصاویر ڈاؤن لوڈ نہیں ہوتیں۔",deviceOnly:"ڈیوائس پر",emptyBookmarks:"ابھی کوئی بک مارک نہیں۔ سائٹ کھولیں اور ★ دبائیں۔",emptyShortcuts:"اپنا پہلا HTTPS شارٹ کٹ شامل کریں۔",showAll:"سب دکھائیں",showFewer:"کم دکھائیں",actions:[["＋ نیا ٹیب","نیا ٹیب کھولیں"],["▣ ٹیب تبدیل کریں","ٹیب بدلیں، نقل بنائیں یا بند کریں"],["◷ ہسٹری","ہسٹری پہلے سے بند ہے"],["⇩ ڈاؤن لوڈز","ڈاؤن لوڈ کی گئی فائلیں کھولیں"],["◉ نجی سیشن","الگ نجی براؤزنگ"],["⚙ ترتیبات","رازداری، تلاش اور ڈسپلے"],["⌕ صفحے میں تلاش","ویب صفحے کا متن تلاش کریں"],["★ موجودہ صفحہ محفوظ کریں","خفیہ کردہ بک مارکس میں شامل کریں"],["▦ QR کوڈ","موجودہ URL کا QR بنائیں"],["▤ PDF کے طور پر محفوظ کریں","Android پرنٹ ڈائیلاگ استعمال کریں"],["↗ صفحہ شیئر کریں","موجودہ عنوان اور پتہ شیئر کریں"],["🛡 سائٹ کی حفاظت","HTTPS اور براؤزر کی حفاظت دیکھیں"]],localNote:"ہوم پیج کے وسائل ڈیوائس پر لوڈ ہوتے ہیں۔ Hari AI کو سوالات صرف آپ کی اجازت سے آن لائن بھیجے جاتے ہیں۔"}
};
for (const [code,words] of Object.entries(INDIAN_EXTRA_UI)) {
 EXTRA_UI[code] = Object.assign({}, EXTRA_UI.en, words);
 EXTRA_UI[code].wallpapers = words.wallpapers || EXTRA_UI.en.wallpapers;
 EXTRA_UI[code].actions = words.actions || EXTRA_UI.en.actions;
 EXTRA_UI[code].toolsWord = EXTRA_UI.en.toolsWord;
 EXTRA_UI[code].curated = EXTRA_UI.en.curated;
 EXTRA_UI[code].sites = EXTRA_UI.en.sites;
 EXTRA_UI[code].links = EXTRA_UI.en.links;
}

const HARI_AI_UI = {
 en:{description:"Ask questions, learn faster and explore new ideas",open:"Open →"},
 ml:{description:"ചോദ്യങ്ങൾ ചോദിക്കൂ, വേഗത്തിൽ പഠിക്കൂ, പുതിയ ആശയങ്ങൾ കണ്ടെത്തൂ",open:"തുറക്കുക →"},
 hi:{description:"प्रश्न पूछें, तेज़ी से सीखें और नए विचार जानें",open:"खोलें →"},
 ta:{description:"கேள்விகள் கேட்டு, வேகமாகக் கற்று, புதிய யோசனைகளை அறியுங்கள்",open:"திறக்கவும் →"},
 te:{description:"ప్రశ్నలు అడగండి, వేగంగా నేర్చుకోండి, కొత్త ఆలోచనలను అన్వేషించండి",open:"తెరవండి →"},
 kn:{description:"ಪ್ರಶ್ನೆಗಳನ್ನು ಕೇಳಿ, ವೇಗವಾಗಿ ಕಲಿಯಿರಿ, ಹೊಸ ಆಲೋಚನೆಗಳನ್ನು ಅನ್ವೇಷಿಸಿ",open:"ತೆರೆಯಿರಿ →"},
 bn:{description:"প্রশ্ন করুন, দ্রুত শিখুন এবং নতুন ধারণা জানুন",open:"খুলুন →"},
 mr:{description:"प्रश्न विचारा, जलद शिका आणि नवीन कल्पना जाणून घ्या",open:"उघडा →"},
 gu:{description:"પ્રશ્નો પૂછો, ઝડપથી શીખો અને નવા વિચારો જાણો",open:"ખોલો →"},
 pa:{description:"ਸਵਾਲ ਪੁੱਛੋ, ਤੇਜ਼ੀ ਨਾਲ ਸਿੱਖੋ ਅਤੇ ਨਵੇਂ ਵਿਚਾਰ ਜਾਣੋ",open:"ਖੋਲ੍ਹੋ →"},
 ur:{description:"سوال پوچھیں، تیزی سے سیکھیں اور نئے خیالات دریافت کریں",open:"کھولیں →"},
 es:{description:"Haz preguntas, aprende más rápido y descubre nuevas ideas",open:"Abrir →"},
 fr:{description:"Posez des questions, apprenez plus vite et explorez de nouvelles idées",open:"Ouvrir →"},
 ar:{description:"اطرح الأسئلة وتعلّم بسرعة واستكشف أفكارًا جديدة",open:"افتح →"}
};

function extraUi(){return EXTRA_UI[currentLanguage] || EXTRA_UI.en;}
function applyExtendedLanguage(){
 const u=extraUi();
 const ai=HARI_AI_UI[currentLanguage] || HARI_AI_UI.en;
 $('hariAiTitle').textContent='Hari AI';
 $('hariAiDesc').textContent=ai.description;
 $('hariAiOpen').textContent=ai.open;
 const pills=document.querySelectorAll('.pills .pill');
 if(pills[0])pills[0].textContent=u.https;
 if(pills[1])pills[1].textContent=u.localTools;
 $('query').placeholder=u.searchPlaceholder;
 $('directorySearch').placeholder=u.directoryPlaceholder;
 $('directorySearch').setAttribute('aria-label',u.directoryPlaceholder);
 $('directoryEmpty').textContent=u.noMatches;
 $('addShortcut2').textContent=u.addShortcut;
 $('resetShortcuts').textContent=u.resetShortcuts;
 const heading=document.querySelector('.wallpaper-heading h2');
 const sub=document.querySelector('.wallpaper-heading p');
 const device=document.querySelector('.wallpaper-heading .mini');
 if(heading)heading.textContent=u.wallpaperTitle;
 if(sub)sub.textContent=u.wallpaperSub;
 if(device)device.textContent=u.deviceOnly;
 document.querySelectorAll('#wallpaperGrid .wallpaper-name').forEach((el,i)=>{if(u.wallpapers[i])el.textContent=u.wallpapers[i];});
 document.querySelectorAll('#homePanel .actions .action').forEach((el,i)=>{
  const pair=u.actions[i]; if(!pair)return;
  for(const node of Array.from(el.childNodes)){if(node.nodeType===Node.TEXT_NODE&&node.nodeValue.trim()){node.nodeValue=pair[0];break;}}
  const desc=el.querySelector('span');if(desc)desc.textContent=pair[1];
 });
 const note=document.querySelector('.localnote');if(note)note.textContent=u.localNote;
}

let currentLanguage = LANGUAGE_SETTING || 'en';
function applyLanguage(code) {
 const t = I18N[code] || I18N.en;
 currentLanguage = I18N[code] ? code : 'en';
 document.documentElement.lang = currentLanguage;
 $('languageSelect').value = currentLanguage;
 document.querySelector('.brand-sub').textContent = t.brandSub;
 document.querySelector('.eyebrow').textContent = t.eyebrow;
 $('heroTitle').innerHTML = t.titleTop + '<br><span class="accent">' + t.titleAccent + '</span>';
 document.querySelector('.sub').textContent = t.sub;
 $('searchForm').querySelector('.primary').textContent = t.search + ' ↗';
 $('query').placeholder = t.search + ' / enter an HTTPS website…';
 const nav = document.querySelectorAll('.navbtn');
 [t.home,t.tools,t.explore,t.privacy].forEach((label,i)=>{ if(nav[i]) nav[i].textContent = label; });
 const heads = document.querySelectorAll('.section-head h2');
 [t.quick,t.bookmarks,t.controls,t.shortcuts,t.toolbox,t.directory,t.privacyDash].forEach((label,i)=>{ if(heads[i]) heads[i].textContent = label; });
 $('addShortcut').textContent = t.addSite;
 const manage = document.querySelector('#homePanel .section-head a.mini');
 if (manage) manage.textContent = t.manage + ' ↗';
 const minis = document.querySelectorAll('#homePanel .section-head .mini');
 if (minis[0]) minis[0].textContent = t.tap;
 const startCard = document.querySelector('#homePanel .card:not(.creator-card) h3');
 if (startCard) startCard.textContent = t.personalize;
 $('creatorHeading').textContent = t.promoTitle;
 $('creatorBlurb').textContent = t.promoText;
 $('creatorSitesTitle').textContent = t.websites;
 $('creatorFollow').firstElementChild.nextElementSibling.childNodes[0].nodeValue = t.follow;
 $('creatorFollow').querySelector('small').textContent = t.followSub;
 document.querySelector('.shell').dir = ['ar','ur'].includes(currentLanguage) ? 'rtl' : 'ltr';
 applyExtendedLanguage();
}
function setDateTime(){
 const d=new Date(), hour=d.getHours(), t=I18N[currentLanguage]||I18N.en;
 const greeting=hour<12?t.morning:hour<17?t.afternoon:t.evening;
 $('greeting').textContent=greeting+' — '+t.ready+' '+d.toLocaleDateString(['en','ml','hi','ta','te','kn','bn','mr','gu','pa','ur'].includes(currentLanguage)?(currentLanguage==='en'?'en-IN':currentLanguage+'-IN'):currentLanguage,{weekday:'long',month:'short',day:'numeric'});
 $('clock').textContent=t.localTime+' · '+d.toLocaleTimeString(undefined,{hour:'2-digit',minute:'2-digit'});
}
$('languageSelect').value = currentLanguage;
$('languageSelect').addEventListener('change',()=>{window.location.href=internalAction('language-'+$('languageSelect').value);});
applyLanguage(currentLanguage);
setDateTime();
function applyWallpaper(name, saveChoice) {
 const choice = name === 'none' || WALLPAPER_NAMES.includes(name) ? name : 'none';
 if (choice === 'none') {
   document.body.classList.remove('wallpaper-on');
   document.documentElement.style.removeProperty('--user-wallpaper');
 } else {
   const localUrl = 'https://hari-browser-home.invalid/assets/wallpapers/' + choice + '.jpg';
   document.documentElement.style.setProperty('--user-wallpaper', 'url("' + localUrl + '")');
   document.body.classList.add('wallpaper-on');
 }
 document.querySelectorAll('.wallpaper-choice').forEach(button => {
   button.setAttribute('aria-pressed', String(button.dataset.wallpaper === choice));
 });
 if (saveChoice && !PRIVATE_MODE) {
   try {
     if (choice === 'none') localStorage.removeItem('hari.home.wallpaper.v1');
     else localStorage.setItem('hari.home.wallpaper.v1', choice);
   } catch (e) {}
 }
}
document.querySelectorAll('.wallpaper-choice').forEach(button => {
 button.addEventListener('click', () => applyWallpaper(button.dataset.wallpaper, true));
});
let startingWallpaper = 'none';
if (!PRIVATE_MODE) {
 try {
   const saved = localStorage.getItem('hari.home.wallpaper.v1');
   if (WALLPAPER_NAMES.includes(saved)) startingWallpaper = saved;
 } catch (e) {}
}
applyWallpaper(startingWallpaper, false);
$('engine').value=['DuckDuckGo','Startpage','Brave Search','Google','Bing'].includes(ENGINE_SETTING)?ENGINE_SETTING:'DuckDuckGo';
$('engine').addEventListener('change',()=>{const keys={'DuckDuckGo':'duckduckgo','Startpage':'startpage','Brave Search':'brave','Google':'google','Bing':'bing'};const key=keys[$('engine').value];if(key)window.location.href=internalAction('searchengine-'+key);});
$('searchForm').addEventListener('submit',e=>{e.preventDefault();const q=$('query').value.trim();if(!q)return;let url;if(/^https:\\/\\//i.test(q)){url=q;}else if(/^http:\\/\\//i.test(q)){alert('Insecure HTTP is blocked. Use HTTPS.');return;}else if(/^[a-z][a-z0-9+.-]*:/i.test(q)&&!/^https?:/i.test(q)){alert('Only HTTPS web addresses are allowed.');return;}else if(!q.includes(' ')&&q.includes('.')&&/^[\\w.-]+(?::\\d+)?(?:\\/.*)?$/.test(q)){url='https://'+q;}else{const engine=$('engine').value;const encoded=encodeURIComponent(q);url=engine==='Startpage'?'https://www.startpage.com/sp/search?query='+encoded:engine==='Brave Search'?'https://search.brave.com/search?q='+encoded:engine==='Google'?'https://www.google.co.in/search?q='+encoded:engine==='Bing'?'https://www.bing.com/search?q='+encoded:'https://duckduckgo.com/?q='+encoded;}if(validHttps(url))window.location.href=url;else alert('Please enter a valid HTTPS website address.');});
// The native browser preference seeds the selected provider on every home load.
function showPanel(id){document.querySelectorAll('.panel').forEach(p=>p.classList.toggle('active',p.id===id));document.querySelectorAll('.navbtn').forEach(b=>b.classList.toggle('active',b.dataset.panel===id));if(id==='explorePanel')renderDirectory();window.scrollTo(0,0);}
document.querySelectorAll('.navbtn').forEach(b=>b.addEventListener('click',()=>showPanel(b.dataset.panel)));
function internalAction(name){return 'https://hari-browser-home.invalid/action/'+name;}
function renderFeatured(){const box=$('shortcuts');box.innerHTML='';let custom=[];try{custom=JSON.parse(localStorage.getItem('hari.custom.shortcuts.v1')||'[]');if(!Array.isArray(custom))custom=[];}catch(e){}const all=[...FEATURED,...custom.filter(x=>x&&validHttps(x.url)&&x.name).slice(0,40)];const fragment=document.createDocumentFragment();all.forEach(site=>{const a=document.createElement('a');a.className='tile';a.href=site.url;a.rel='noreferrer noopener';const ic=document.createElement('span');ic.className='icon';ic.textContent=site.icon||iconFor(site.name);const body=document.createElement('span');body.innerHTML='<strong>'+esc(site.name)+'</strong><small>'+esc(site.desc||new URL(site.url).hostname)+'</small>';a.append(ic,body);fragment.appendChild(a);});box.replaceChildren(fragment);if(all.length===0)box.innerHTML='<div class="empty">'+esc(extraUi().emptyShortcuts)+'</div>';}
function renderBookmarks(){const box=$('bookmarks');box.innerHTML='';const list=(Array.isArray(BOOKMARKS)?BOOKMARKS:[]).filter(b=>b&&validHttps(b.url)).slice(-18).reverse();if(!list.length){box.innerHTML='<div class="empty" style="grid-column:1/-1">'+esc(extraUi().emptyBookmarks)+'</div>';return;}list.forEach(b=>{const a=document.createElement('a');a.className='bookmark';a.href=b.url;a.rel='noreferrer noopener';const ic=document.createElement('span');ic.className='fav';ic.textContent='★';const text=document.createElement('span');text.style.minWidth='0';text.innerHTML='<strong>'+esc(b.title||new URL(b.url).hostname)+'</strong><small>'+esc(new URL(b.url).hostname)+'</small>';a.append(ic,text);box.appendChild(a);});}
function addShortcut(){
 $('modalTitle').textContent='Add an HTTPS shortcut';
 $('modalContent').innerHTML='<p class="mini">This shortcut is stored in this browser profile on this device. Use a trusted HTTPS address.</p><label class="field">Shortcut name<input id="shortcutName" maxlength="50" placeholder="My favourite site"></label><label class="field">HTTPS address<input id="shortcutUrl" type="url" placeholder="https://example.com"></label><div class="row"><button class="primary" id="shortcutSave">Save shortcut</button><button class="btn" id="shortcutCancel">Cancel</button></div><div id="shortcutError" class="notefoot"></div>';
 $('modal').classList.add('open');
 $('shortcutCancel').addEventListener('click',closeTool);
 $('shortcutSave').addEventListener('click',()=>{
  const name=$('shortcutName').value.trim(),url=$('shortcutUrl').value.trim();
  if(!name){output('shortcutError','Enter a shortcut name.');return;}
  if(!/^https:\\/\\//i.test(url)||!validHttps(url)){output('shortcutError','Only valid HTTPS addresses are allowed.');return;}
  let list=[];try{list=JSON.parse(localStorage.getItem('hari.custom.shortcuts.v1')||'[]');if(!Array.isArray(list))list=[];}catch(e){}
  if(list.length>=40){output('shortcutError','The limit is 40 custom shortcuts. Reset shortcuts to start over.');return;}
  if(list.some(s=>s.url===url)){output('shortcutError','That shortcut is already saved.');return;}
  list.push({name:name.slice(0,50),url,icon:'☆',desc:new URL(url).hostname});
  try{localStorage.setItem('hari.custom.shortcuts.v1',JSON.stringify(list));renderFeatured();closeTool();}catch(e){output('shortcutError','Device storage is unavailable in this session.');}
 });
 $('shortcutName').focus();
}
$('addShortcut').addEventListener('click',addShortcut);$('addShortcut2').addEventListener('click',addShortcut);
$('resetShortcuts').addEventListener('click',()=>{if(confirm('Reset all custom shortcuts on this device? This does not delete bookmarks.')){try{localStorage.removeItem('hari.custom.shortcuts.v1');}catch(e){}renderFeatured();}});
renderFeatured();renderBookmarks();

const CORE_TOOL_IDS = new Set(['calculator','units','notes','password','text','json','url','wordcount']);
let allToolsShown = false;
function renderTools(){
  const grid=$('toolGrid');
  grid.innerHTML='';
  const visible=allToolsShown?TOOLS:TOOLS.filter(t=>CORE_TOOL_IDS.has(t.id));
  visible.forEach(t=>{
    const b=document.createElement('button');
    b.className='tool';
    b.type='button';
    b.innerHTML='<span class="ti">'+esc(t.icon)+'</span><strong>'+esc(t.name)+'</strong><small>'+esc(t.desc)+'</small>';
    b.addEventListener('click',()=>openTool(t.id));
    grid.appendChild(b);
  });
  const toggle=$('toggleTools');
  toggle.textContent=allToolsShown?extraUi().showFewer:extraUi().showAll+' '+TOOLS.length+' '+extraUi().toolsWord;
  toggle.setAttribute('aria-expanded',String(allToolsShown));
}
$('toggleTools').addEventListener('click',()=>{
  allToolsShown=!allToolsShown;
  renderTools();
});
renderTools();
let activeInterval=null;let timerValue=25*60;let timerMode='focus';let stopwatchStart=0;let stopwatchElapsed=0;
function closeTool(){if(activeInterval){clearInterval(activeInterval);activeInterval=null;}$('modal').classList.remove('open');}
$('closeModal').addEventListener('click',closeTool);$('modal').addEventListener('click',e=>{if(e.target===$('modal'))closeTool();});
document.addEventListener('keydown',e=>{if(e.key==='Escape')closeTool();});
function openTool(id){if(activeInterval){clearInterval(activeInterval);activeInterval=null;}const tool=TOOLS.find(t=>t.id===id);$('modalTitle').textContent=tool?tool.name:'Local tool';$('modalContent').innerHTML=toolHtml(id);$('modal').classList.add('open');wireTool(id);}
function toolHtml(id){
 switch(id){
 case 'calculator':return '<p class="mini">Supports + − × ÷, brackets, decimals, %, and ^ for powers. Only math characters are accepted.</p><label class="field">Expression<input id="calcExpr" inputmode="decimal" placeholder="(125 + 75) * 1.18" maxlength="180"></label><div class="row"><button class="primary" id="calcRun">Calculate</button><button class="btn" id="calcClear">Clear</button></div><div class="output" id="calcOut">Result appears here.</div>';
 case 'units':return '<label class="field">Category<select id="unitKind"><option value="length">Length</option><option value="mass">Mass</option><option value="speed">Speed</option><option value="data">Digital storage</option><option value="temp">Temperature</option><option value="volume">Volume</option></select></label><label class="field">Value<input id="unitValue" type="number" value="1" step="any"></label><div class="row"><label class="field">From<select id="unitFrom"></select></label><label class="field">To<select id="unitTo"></select></label></div><div class="output" id="unitOut"></div>';
 case 'notes':return '<p class="mini">These notes stay in the browser profile on this device. Avoid passwords, financial data or other secrets.</p><label class="field">Your note<textarea id="notesText" placeholder="Write a quick reminder…"></textarea></label><div class="row"><button class="primary" id="noteSave">Save note</button><button class="btn" id="noteClear">Delete note</button></div><div class="notefoot" id="noteState">Not yet saved.</div>';
        """);
    htmlBuilder.append("""
 case 'password':return '<p class="mini">Generated locally with the browser cryptographic random generator when supported. The password is not uploaded.</p><label class="field">Length (8–64)<input id="passLength" type="number" min="8" max="64" value="20"></label><label><input id="passSymbols" type="checkbox" checked> Include symbols</label><div class="row" style="margin-top:12px"><button class="primary" id="passMake">Generate</button></div><div id="passOut" class="output"></div><div class="notefoot">Copy the displayed result manually. Do not reuse passwords.</div>';
 case 'text':return '<label class="field">Text<textarea id="textInput" placeholder="Paste or type text…"></textarea></label><div class="row"><button class="btn" data-text="upper">UPPERCASE</button><button class="btn" data-text="lower">lowercase</button><button class="btn" data-text="title">Title Case</button><button class="btn" data-text="trim">Trim lines</button><button class="btn" data-text="reverse">Reverse</button><button class="btn" data-text="sort">Sort lines</button><button class="btn" data-text="unique">Unique lines</button><button class="btn" data-text="spaces">Clean spaces</button></div><div id="textStats" class="notefoot"></div>';
 case 'json':return '<label class="field">JSON text<textarea id="jsonInput" placeholder="{ &quot;hello&quot;: &quot;world&quot; }"></textarea></label><div class="row"><button class="primary" id="jsonFormat">Format / validate</button><button class="btn" id="jsonMin">Minify</button></div><div class="output" id="jsonOut"></div>';
 case 'url':return '<label class="field">Text or URL component<textarea id="urlInput" placeholder="Type text to encode or paste an encoded value…"></textarea></label><div class="row"><button class="primary" id="urlEncode">Encode</button><button class="btn" id="urlDecode">Decode</button></div><div class="output" id="urlOut"></div>';
 case 'base64':return '<label class="field">UTF-8 text<textarea id="b64Input" placeholder="Text…"></textarea></label><div class="row"><button class="primary" id="b64Encode">Encode</button><button class="btn" id="b64Decode">Decode</button></div><div class="output" id="b64Out"></div>';
 case 'pomodoro':return '<div style="text-align:center;padding:20px 0"><div id="pomoState" class="eyebrow">FOCUS SESSION</div><div id="pomoClock" style="font-size:54px;font-weight:900;letter-spacing:-2px">25:00</div><div class="row"><button class="primary" id="pomoStart">Start</button><button class="btn" id="pomoPause">Pause</button><button class="btn" id="pomoReset">Reset</button></div><div class="pills" style="justify-content:center"><button class="pill" id="pomoFocus">25 min focus</button><button class="pill" id="pomoShort">5 min break</button><button class="pill" id="pomoLong">15 min break</button></div></div>';
 case 'stopwatch':return '<div id="swClock" style="font-size:43px;text-align:center;font-weight:900;padding:22px 0">00:00.0</div><div class="row"><button class="primary" id="swStart">Start</button><button class="btn" id="swPause">Pause</button><button class="btn" id="swReset">Reset</button></div>';
 case 'date':return '<div class="row"><label class="field">Start date<input type="date" id="dateStart"></label><label class="field">End date<input type="date" id="dateEnd"></label></div><button class="primary" id="dateCalc">Calculate difference</button><div id="dateOut" class="output"></div><p class="notefoot">Dates use the device local time zone.</p>';
 case 'percent':return '<div class="row"><label class="field">Original amount<input type="number" id="pctBase" value="1000" step="any"></label><label class="field">Percent (%)<input type="number" id="pctRate" value="15" step="any"></label></div><button class="primary" id="pctCalc">Calculate percentage</button><div class="output" id="pctOut"></div><div class="card"><h3>Discount price</h3><div class="row"><label class="field">Price<input type="number" id="discPrice" value="1000"></label><label class="field">Discount %<input type="number" id="discRate" value="20"></label></div><button class="btn" id="discCalc">Calculate discount</button><div class="output" id="discOut"></div></div>';
 case 'loan':return '<div class="row"><label class="field">Loan amount<input type="number" id="loanAmount" value="100000" min="0"></label><label class="field">Annual interest (%)<input type="number" id="loanRate" value="8" min="0" step="any"></label></div><label class="field">Term in months<input type="number" id="loanMonths" value="36" min="1" max="600"></label><button class="primary" id="loanCalc">Estimate payment</button><div class="output" id="loanOut"></div><p class="notefoot">Estimate only; fees, taxes and lender terms are excluded.</p>';
 case 'color':return '<label class="field">Choose colour<input type="color" id="colorPick" value="#13b8a6"></label><label class="field">HEX<input id="colorHex" value="#13b8a6"></label><div id="colorPreview" style="height:62px;border-radius:13px;background:#13b8a6;border:1px solid var(--line)"></div><div class="output" id="colorOut"></div>';
 case 'wordcount':return '<label class="field">Text<textarea id="wcInput" placeholder="Type or paste text…"></textarea></label><div class="output" id="wcOut">0 words · 0 characters</div>';
 case 'lineclean':return '<label class="field">One item per line<textarea id="lineInput" placeholder="Paste lines here…"></textarea></label><div class="row"><button class="btn" id="lineTrim">Trim</button><button class="btn" id="lineSort">Sort</button><button class="primary" id="lineUnique">Remove duplicates</button></div><div class="output" id="lineOut"></div>';
 case 'slug':return '<label class="field">Title<input id="slugInput" placeholder="A Helpful Example Title"></label><button class="primary" id="slugMake">Make slug</button><div class="output" id="slugOut"></div>';
 case 'age':return '<label class="field">Date of birth<input type="date" id="ageInput"></label><button class="primary" id="ageCalc">Calculate age</button><div class="output" id="ageOut"></div>';
 case 'tip':return '<div class="row"><label class="field">Bill amount<input type="number" id="tipBill" value="1000"></label><label class="field">Tip (%)<input type="number" id="tipRate" value="10"></label></div><label class="field">People sharing<input type="number" id="tipPeople" min="1" value="2"></label><button class="primary" id="tipCalc">Split bill</button><div class="output" id="tipOut"></div>';
 case 'compound':return '<div class="row"><label class="field">Starting amount<input type="number" id="ciPrincipal" value="10000"></label><label class="field">Annual rate (%)<input type="number" id="ciRate" value="6"></label></div><div class="row"><label class="field">Years<input type="number" id="ciYears" value="5"></label><label class="field">Compounds / year<select id="ciN"><option value="1">Yearly</option><option value="4">Quarterly</option><option value="12" selected>Monthly</option><option value="365">Daily</option></select></label></div><button class="primary" id="ciCalc">Calculate growth</button><div class="output" id="ciOut"></div>';
 case 'hash':return '<p class="mini">Creates a SHA-256 digest locally using the Web Crypto API. A hash is not encryption and cannot be reversed.</p><label class="field">Text<textarea id="hashInput" placeholder="Text to fingerprint…"></textarea></label><button class="primary" id="hashRun">Create SHA-256</button><div class="output" id="hashOut"></div>';
 case 'colorcontrast':return '<div class="row"><label class="field">Text colour<input type="color" id="fgColor" value="#16243b"></label><label class="field">Background colour<input type="color" id="bgColor" value="#ffffff"></label></div><div id="contrastSample" style="padding:24px;text-align:center;border-radius:13px;background:white;color:#16243b;border:1px solid var(--line)">Contrast preview · Sample text</div><div id="contrastOut" class="output"></div>';
 case 'readme':return '<div class="card"><h3>Browser features already built in</h3><p>HTTPS-only navigation · Safe Browsing · SSL error rejection · mixed-content blocking · best-effort tracker blocking · third-party cookies off · site permissions denied · encrypted bookmarks · optional encrypted history · private browsing · tab switcher · duplicate/close tabs · restore recently closed tab · downloads · find in page · text zoom · desktop mode · reader-style CSS · image blocking · theme toggle · export/import bookmarks · QR code · print/save PDF · share/copy URL · translation handoff · emergency clear.</p></div><div class="card"><h3>Privacy reminder</h3><p>Private browsing does not hide traffic from websites, your network, or your internet provider. The directory contains external websites, which may have their own tracking and privacy policies.</p></div>';
 case 'qr':return '<p>This action opens the native QR tool for the active web page.</p><a class="action" href="https://hari-browser-home.invalid/action/qr">Open current-page QR ↗</a>';
   case 'uuid':return '<p class="mini">Identifiers are generated locally using the device cryptographic random generator. Results are not uploaded.</p><div class="row"><button class="primary" id="uuidMake">Generate UUID</button><button class="btn" id="uuidBatch">Generate 10</button></div><div class="output" id="uuidOut">Press generate to create an identifier.</div>';
  case 'random':return '<div class="row"><label class="field">Minimum<input type="number" id="randMin" value="1" step="1"></label><label class="field">Maximum<input type="number" id="randMax" value="100" step="1"></label></div><div class="row"><button class="primary" id="randMake">Generate number</button><button class="btn" id="randDice">Roll a die</button><button class="btn" id="randCoin">Flip coin</button></div><div class="output" id="randOut"></div><p class="notefoot">Random integer generation requires the secure browser random API.</p>';
  case 'timestamp':return '<div class="card"><h3>Current Unix timestamp</h3><div class="output" id="tsNowOut"></div><button class="btn" id="tsNow">Refresh timestamp</button></div><label class="field">Local date and time<input type="datetime-local" id="tsDate"></label><div class="row"><button class="primary" id="tsToUnix">Convert to timestamp</button><button class="btn" id="tsToDate">Use current time</button></div><div class="output" id="tsOut"></div><p class="notefoot">Unix timestamps count from 1970-01-01 UTC. The date input uses local device time.</p>';
  case 'baseconvert':return '<label class="field">Value<input id="baseValue" placeholder="1010, 42 or 2A"></label><label class="field">Input base<select id="baseKind"><option value="2">Binary (2)</option><option value="8">Octal (8)</option><option value="10" selected>Decimal (10)</option><option value="16">Hexadecimal (16)</option></select></label><button class="primary" id="baseRun">Convert number</button><div class="output" id="baseOut"></div><p class="notefoot">Whole numbers within the safe integer range are supported.</p>';
  case 'lorem':return '<label class="field">Paragraph count<select id="loremCount"><option>1</option><option>2</option><option>3</option><option>5</option><option>8</option><option>10</option></select></label><button class="primary" id="loremMake">Generate placeholder text</button><div class="output" id="loremOut"></div><p class="notefoot">Use for mock-ups and layouts, not as factual content.</p>';
  case 'readtime':return '<label class="field">Paste text<textarea id="readText" placeholder="Paste an article or passage here…"></textarea></label><label class="field">Reading speed (words per minute)<input type="number" id="readWpm" min="50" max="1000" value="220"></label><div class="output" id="readOut">Reading estimate appears here.</div>';
        """);
    htmlBuilder.append("""
  case 'stats':return '<label class="field">Numbers separated by spaces, commas or new lines<textarea id="statsInput" placeholder="12, 15, 18, 20, 20, 25"></textarea></label><button class="primary" id="statsRun">Calculate statistics</button><div class="output" id="statsOut"></div><p class="notefoot">Population standard deviation is shown. Empty or invalid values are rejected.</p>';
  case 'gradient':return '<div class="row"><label class="field">Start colour<input type="color" id="gradA" value="#13b8a6"></label><label class="field">End colour<input type="color" id="gradB" value="#4763ff"></label></div><label class="field">Direction<select id="gradDir"><option value="to right">Left to right</option><option value="to bottom">Top to bottom</option><option value="135deg">Diagonal</option><option value="to top right">Bottom-left to top-right</option></select></label><div id="gradPreview" style="height:90px;border-radius:14px;border:1px solid var(--line);background:linear-gradient(to right,#13b8a6,#4763ff)"></div><button class="primary" id="gradMake" style="margin-top:10px">Update CSS</button><div class="output" id="gradOut"></div>';
  case 'csvjson':return '<label class="field">CSV data<textarea id="csvInput" placeholder="name,city • Maya,Kochi • Arun,Thrissur"></textarea></label><button class="primary" id="csvConvert">Convert CSV to JSON</button><div class="output" id="csvOut"></div><p class="notefoot">Quoted fields and commas inside quoted cells are supported. Data stays in this page.</p>';
  case 'textdiff':return '<label class="field">Original text<textarea id="diffA" placeholder="Paste the original text…"></textarea></label><label class="field">Changed text<textarea id="diffB" placeholder="Paste the changed text…"></textarea></label><button class="primary" id="diffRun">Compare lines</button><div class="output" id="diffOut"></div><p class="notefoot">Simple line comparison; duplicate lines are grouped by membership, not exact position.</p>';
default:return '<p class="mini">Choose one of the available tools.</p>';

  if(id==='uuid'){const generate=count=>{try{if(!crypto.getRandomValues)throw new Error();const ids=[];for(let n=0;n<count;n++){const b=new Uint8Array(16);crypto.getRandomValues(b);b[6]=(b[6]&15)|64;b[8]=(b[8]&63)|128;const h=Array.from(b,x=>x.toString(16).padStart(2,'0')).join('');ids.push(h.slice(0,8)+'-'+h.slice(8,12)+'-'+h.slice(12,16)+'-'+h.slice(16,20)+'-'+h.slice(20));}output('uuidOut',ids.join(String.fromCharCode(10)));}catch(e){output('uuidOut','Secure random generation is unavailable.');}};by('uuidMake',()=>generate(1));by('uuidBatch',()=>generate(10));}
  if(id==='random'){const secureInt=(lo,hi)=>{if(!crypto.getRandomValues)throw new Error();const range=hi-lo+1;if(!Number.isSafeInteger(range)||range<1||range>1000000)throw new Error();const limit=Math.floor(4294967296/range)*range;let x;do{const bytes=new Uint32Array(1);crypto.getRandomValues(bytes);x=bytes[0];}while(x>=limit);return lo+(x%range);};by('randMake',()=>{const lo=Number($('randMin').value),hi=Number($('randMax').value);if(!Number.isSafeInteger(lo)||!Number.isSafeInteger(hi)||lo>hi){output('randOut','Enter whole numbers with minimum no greater than maximum.');return;}try{output('randOut',String(secureInt(lo,hi)));}catch(e){output('randOut','Use a valid range up to one million values; secure random is required.');}});by('randDice',()=>{try{output('randOut','Dice roll: '+secureInt(1,6));}catch(e){output('randOut','Secure random generation is unavailable.');}});by('randCoin',()=>{try{output('randOut',secureInt(0,1)?'Heads':'Tails');}catch(e){output('randOut','Secure random generation is unavailable.');}});}
  if(id==='timestamp'){const refresh=()=>{const n=Date.now();output('tsNowOut',['Seconds: '+Math.floor(n/1000),'Milliseconds: '+n].join(String.fromCharCode(10)));};const setLocal=()=>{const d=new Date();$('tsDate').value=[d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-')+'T'+String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0');};setLocal();refresh();by('tsNow',refresh);by('tsToUnix',()=>{const v=$('tsDate').value;if(!v){output('tsOut','Choose a date and time.');return;}const n=new Date(v).getTime();if(!Number.isFinite(n)){output('tsOut','Choose a valid date.');return;}output('tsOut',['Unix seconds: '+Math.floor(n/1000),'Unix milliseconds: '+n].join(String.fromCharCode(10)));});by('tsToDate',()=>{setLocal();refresh();});}
  if(id==='baseconvert'){by('baseRun',()=>{const value=$('baseValue').value.trim(),base=Number($('baseKind').value);if(!value){output('baseOut','Enter a whole number.');return;}const n=parseInt(value,base);if(!Number.isSafeInteger(n)||n.toString(base)!==value.toLowerCase()){output('baseOut','Invalid value for the selected base or outside the safe integer range.');return;}output('baseOut',['Binary: '+n.toString(2),'Octal: '+n.toString(8),'Decimal: '+n.toString(10),'Hexadecimal: '+n.toString(16).toUpperCase()].join(String.fromCharCode(10)));});}
  if(id==='lorem'){const words='lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua ut enim ad minim veniam quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat'.split(' ');by('loremMake',()=>{const count=Math.max(1,Math.min(10,Number($('loremCount').value)||1)),paras=[];for(let p=0;p<count;p++){const part=[];for(let i=0;i<56;i++){let w=words[(p*11+i)%words.length];if(i===0)w=w.charAt(0).toUpperCase()+w.slice(1);part.push(w);}paras.push(part.join(' ')+'.');}output('loremOut',paras.join(String.fromCharCode(10).repeat(2)));});}
  if(id==='readtime'){const update=()=>{const textValue=$('readText').value.trim(),words=textValue?textValue.split(' ').filter(Boolean).length+textValue.split(String.fromCharCode(10)).filter(Boolean).length-1:0,wpm=Math.max(50,Math.min(1000,Number($('readWpm').value)||220)),mins=words/wpm;output('readOut',['Words: '+Math.max(0,words),'Characters: '+$('readText').value.length,'Estimated reading time: '+(words===0?'0 minutes':mins<1?Math.max(1,Math.ceil(mins*60))+' seconds':Math.ceil(mins)+' minute(s)'),'At 130 words/minute aloud: '+Math.ceil(Math.max(0,words)/130)+' minute(s)'].join(String.fromCharCode(10)));};$('readText').addEventListener('input',update);$('readWpm').addEventListener('input',update);update();}
  if(id==='stats'){by('statsRun',()=>{const raw=$('statsInput').value.trim();if(!raw){output('statsOut','Enter at least one number.');return;}const normalized=raw.split(String.fromCharCode(10)).join(' ').split(String.fromCharCode(13)).join(' ').replaceAll(',',' ').replaceAll(';',' '),vals=normalized.split(' ').filter(Boolean).map(Number);if(!vals.length||vals.length>10000||vals.some(x=>!Number.isFinite(x))){output('statsOut','Enter up to 10,000 valid finite numbers.');return;}const sorted=[...vals].sort((a,b)=>a-b),sum=vals.reduce((a,b)=>a+b,0),mean=sum/vals.length,mid=Math.floor(sorted.length/2),median=sorted.length%2?sorted[mid]:(sorted[mid-1]+sorted[mid])/2,counts=new Map();vals.forEach(x=>counts.set(x,(counts.get(x)||0)+1));let peak=0;for(const c of counts.values())if(c>peak)peak=c;const mode=peak===1?'No repeated mode':[...counts].filter(e=>e[1]===peak).map(e=>e[0]).join(', '),variance=vals.reduce((a,b)=>a+(b-mean)*(b-mean),0)/vals.length;output('statsOut',['Count: '+vals.length,'Mean: '+fmt(mean),'Median: '+fmt(median),'Mode: '+mode,'Minimum: '+fmt(sorted[0]),'Maximum: '+fmt(sorted[sorted.length-1]),'Range: '+fmt(sorted[sorted.length-1]-sorted[0]),'Population standard deviation: '+fmt(Math.sqrt(variance))].join(String.fromCharCode(10)));});}
  if(id==='gradient'){const update=()=>{const css='linear-gradient('+$('gradDir').value+', '+$('gradA').value+', '+$('gradB').value+')';$('gradPreview').style.background=css;output('gradOut',css+';');};['gradA','gradB','gradDir'].forEach(x=>$(x).addEventListener('input',update));by('gradMake',update);update();}
  if(id==='csvjson'){by('csvConvert',()=>{const src=$('csvInput').value,nl=String.fromCharCode(10),cr=String.fromCharCode(13);if(!src.trim()){output('csvOut','Paste CSV data first.');return;}const rows=[];let row=[],cell='',quoted=false;for(let i=0;i<src.length;i++){const ch=src[i];if(ch==='"'&&quoted&&src[i+1]==='"'){cell+='"';i++;}else if(ch==='"'){quoted=!quoted;}else if(ch===','&&!quoted){row.push(cell);cell='';}else if((ch===nl||ch===cr)&&!quoted){if(ch===cr&&src[i+1]===nl)i++;row.push(cell);if(row.some(v=>v.length))rows.push(row);row=[];cell='';}else cell+=ch;}if(quoted){output('csvOut','CSV has an unclosed quoted field.');return;}if(cell.length||row.length){row.push(cell);rows.push(row);}if(rows.length<2){output('csvOut','Include a header row and at least one data row.');return;}const headers=rows.shift().map((h,i)=>h.trim()||('column_'+(i+1))),objects=rows.map(r=>Object.fromEntries(headers.map((h,i)=>[h,r[i]??''])));output('csvOut',JSON.stringify(objects,null,2));});}
  if(id==='textdiff'){by('diffRun',()=>{const nl=String.fromCharCode(10),a=$('diffA').value.split(nl),b=$('diffB').value.split(nl),setA=new Set(a),setB=new Set(b),removed=a.filter(x=>!setB.has(x)),added=b.filter(x=>!setA.has(x)),result=[];removed.forEach(x=>result.push('- '+x));added.forEach(x=>result.push('+ '+x));output('diffOut',result.length?result.join(nl):'No line changes detected.');});}

 }
}
function output(id,text){const el=$(id);if(el)el.textContent=String(text);}
function num(id){return Number($(id)?.value||0);}
function fmt(n){return Number.isFinite(n)?n.toLocaleString(undefined,{maximumFractionDigits:8}):'Invalid result';}
function wireTool(id){
 const by=(name,fn)=>{const el=$(name);if(el)el.addEventListener('click',fn);};
 if(id==='calculator'){by('calcRun',()=>{let exp=$('calcExpr').value.trim();if(!exp||exp.length>180||!/^[0-9+\\-*/().%\\s^]+$/.test(exp)){output('calcOut','Use numbers and math operators only.');return;}try{exp=exp.replace(/\\^/g,'**');const result=Function('"use strict";return ('+exp+')')();output('calcOut',fmt(result));}catch(e){output('calcOut','Invalid expression.');}});by('calcClear',()=>{$('calcExpr').value='';output('calcOut','');});}
 if(id==='units'){
   const definitions={length:{m:1,km:1000,cm:.01,mm:.001,mi:1609.344,yd:.9144,ft:.3048,in:.0254},mass:{kg:1,g:.001,mg:.000001,lb:.45359237,oz:.028349523125,tonne:1000},speed:{'m/s':1,'km/h':1/3.6,mph:.44704,'ft/s':.3048},data:{byte:1,KB:1000,MB:1e6,GB:1e9,KiB:1024,MiB:1048576,GiB:1073741824},volume:{L:1,mL:.001,'m³':1000,gal:3.785411784,cup:.2365882365},temp:{C:1,F:1,K:1}};
   function refill(){const k=$('unitKind').value;const units=Object.keys(definitions[k]);$('unitFrom').innerHTML=units.map(u=>'<option>'+esc(u)+'</option>').join('');$('unitTo').innerHTML=units.map(u=>'<option>'+esc(u)+'</option>').join('');if(units.length>1)$('unitTo').selectedIndex=1;convert();}
   function convert(){const k=$('unitKind').value,from=$('unitFrom').value,to=$('unitTo').value,v=num('unitValue');let result;if(k==='temp'){let c=$('unitFrom').value==='C'?v:$('unitFrom').value==='F'?(v-32)*5/9:v-273.15;result=$('unitTo').value==='C'?c:$('unitTo').value==='F'?c*9/5+32:c+273.15;}else result=v*definitions[k][from]/definitions[k][to];output('unitOut',fmt(result)+' '+to);} $('unitKind').addEventListener('change',refill);['unitValue','unitFrom','unitTo'].forEach(x=>$(x).addEventListener('input',convert));refill();}
 if(id==='notes'){try{$('notesText').value=localStorage.getItem('hari.quick.note.v1')||'';}catch(e){}by('noteSave',()=>{try{localStorage.setItem('hari.quick.note.v1',$('notesText').value.slice(0,12000));output('noteState','Saved on this device at '+new Date().toLocaleTimeString()+'.');}catch(e){output('noteState','Unable to save in this profile.');}});by('noteClear',()=>{try{localStorage.removeItem('hari.quick.note.v1');}catch(e){}$('notesText').value='';output('noteState','Deleted.');});}
        """);
    htmlBuilder.append("""
 if(id==='password'){by('passMake',()=>{const length=Math.max(8,Math.min(64,Math.floor(num('passLength')||20)));const symbols='!@#$%^&*()-_=+[]{}:,.?';let pool='ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';if($('passSymbols').checked)pool+=symbols;try{if(!window.crypto||!crypto.getRandomValues)throw new Error();let out='';const limit=256-(256%pool.length);const bytes=new Uint8Array(128);while(out.length<length){crypto.getRandomValues(bytes);for(const b of bytes){if(b<limit)out+=pool[b%pool.length];if(out.length>=length)break;}}output('passOut',out);}catch(e){output('passOut','Secure random generator unavailable. No password was generated.');}});}
 if(id==='text'){const inp=$('textInput');const update=()=>{const t=inp.value;output('textStats',t.split(/\\s+/).filter(Boolean).length+' words · '+t.length+' characters · '+(t? t.split(/\\n/).length:0)+' lines');};inp.addEventListener('input',update);document.querySelectorAll('[data-text]').forEach(b=>b.addEventListener('click',()=>{const t=inp.value;switch(b.dataset.text){case'upper':inp.value=t.toUpperCase();break;case'lower':inp.value=t.toLowerCase();break;case'title':inp.value=t.toLowerCase().replace(/\\b\\w/g,c=>c.toUpperCase());break;case'trim':inp.value=t.split(/\\n/).map(x=>x.trim()).join('\\n');break;case'reverse':inp.value=Array.from(t).reverse().join('');break;case'sort':inp.value=t.split(/\\n/).sort((a,b)=>a.localeCompare(b)).join('\\n');break;case'unique':inp.value=[...new Set(t.split(/\\n/))].join('\\n');break;case'spaces':inp.value=t.split(/\\n/).map(x=>x.trim().replace(/\\s+/g,' ')).join('\\n');break;}update();}));update();}
 if(id==='json'){by('jsonFormat',()=>{try{output('jsonOut',JSON.stringify(JSON.parse($('jsonInput').value),null,2));}catch(e){output('jsonOut','Invalid JSON: '+e.message);}});by('jsonMin',()=>{try{output('jsonOut',JSON.stringify(JSON.parse($('jsonInput').value)));}catch(e){output('jsonOut','Invalid JSON: '+e.message);}});}
 if(id==='url'){by('urlEncode',()=>output('urlOut',encodeURIComponent($('urlInput').value)));by('urlDecode',()=>{try{output('urlOut',decodeURIComponent($('urlInput').value));}catch(e){output('urlOut','Invalid encoded text.');}});}
 if(id==='base64'){by('b64Encode',()=>{try{output('b64Out',btoa(unescape(encodeURIComponent($('b64Input').value))));}catch(e){output('b64Out','Encoding failed.');}});by('b64Decode',()=>{try{output('b64Out',decodeURIComponent(escape(atob($('b64Input').value.trim()))));}catch(e){output('b64Out','Invalid Base64 input.');}});}
 if(id==='pomodoro'){function show(){output('pomoClock',String(Math.floor(timerValue/60)).padStart(2,'0')+':'+String(timerValue%60).padStart(2,'0'));}function choose(min,label){if(activeInterval){clearInterval(activeInterval);activeInterval=null;}timerValue=min*60;timerMode=label;output('pomoState',label.toUpperCase());show();}by('pomoFocus',()=>choose(25,'Focus session'));by('pomoShort',()=>choose(5,'Short break'));by('pomoLong',()=>choose(15,'Long break'));by('pomoReset',()=>choose(timerMode==='Short break'?5:timerMode==='Long break'?15:25,timerMode));by('pomoPause',()=>{if(activeInterval){clearInterval(activeInterval);activeInterval=null;}});by('pomoStart',()=>{if(activeInterval)return;activeInterval=setInterval(()=>{if(timerValue>0){timerValue--;show();}else{clearInterval(activeInterval);activeInterval=null;output('pomoState','SESSION COMPLETE');if(navigator.vibrate)navigator.vibrate([180,100,180]);}},1000);});show();}
 if(id==='stopwatch'){function show(){const ms=stopwatchElapsed+(activeInterval?Date.now()-stopwatchStart:0);const tenths=Math.floor(ms/100);output('swClock',String(Math.floor(tenths/600)).padStart(2,'0')+':'+String(Math.floor(tenths/10)%60).padStart(2,'0')+'.'+(tenths%10));}by('swStart',()=>{if(activeInterval)return;stopwatchStart=Date.now();activeInterval=setInterval(show,100);});by('swPause',()=>{if(activeInterval){stopwatchElapsed+=Date.now()-stopwatchStart;clearInterval(activeInterval);activeInterval=null;show();}});by('swReset',()=>{if(activeInterval){clearInterval(activeInterval);activeInterval=null;}stopwatchElapsed=0;show();});show();}
 if(id==='date'){const today=new Date();$('dateStart').value=[today.getFullYear(),String(today.getMonth()+1).padStart(2,'0'),String(today.getDate()).padStart(2,'0')].join('-');$('dateEnd').value=$('dateStart').value;by('dateCalc',()=>{const a=new Date($('dateStart').value+'T12:00:00'),b=new Date($('dateEnd').value+'T12:00:00');if(isNaN(a)||isNaN(b)){output('dateOut','Choose two valid dates.');return;}const days=Math.round((b-a)/86400000);output('dateOut',Math.abs(days)+' day(s) apart'+(days<0?' (end date is before start date)':''));});}
 if(id==='percent'){by('pctCalc',()=>output('pctOut',fmt(num('pctRate')/100*num('pctBase'))+' is '+fmt(num('pctRate'))+'% of '+fmt(num('pctBase'))));by('discCalc',()=>{const save=num('discPrice')*num('discRate')/100;output('discOut','You save '+fmt(save)+' · sale price '+fmt(num('discPrice')-save));});}
 if(id==='loan'){by('loanCalc',()=>{const p=num('loanAmount'),rate=num('loanRate')/1200,n=Math.max(1,Math.min(600,Math.floor(num('loanMonths'))));if(p<0||rate<0){output('loanOut','Enter non-negative values.');return;}const payment=rate===0?p/n:p*rate/(1-Math.pow(1+rate,-n));output('loanOut','Estimated monthly payment: '+fmt(payment)+'\\nTotal paid: '+fmt(payment*n)+'\\nEstimated interest: '+fmt(payment*n-p));});}
 if(id==='color'){function update(){let h=$('colorPick').value;$('colorHex').value=h;$('colorPreview').style.background=h;const r=parseInt(h.slice(1,3),16),g=parseInt(h.slice(3,5),16),b=parseInt(h.slice(5,7),16);output('colorOut','RGB('+r+', '+g+', '+b+')');} $('colorPick').addEventListener('input',update);$('colorHex').addEventListener('change',()=>{if(/^#[0-9a-f]{6}$/i.test($('colorHex').value)){$('colorPick').value=$('colorHex').value;update();}});update();}
 if(id==='wordcount'){const update=()=>{const t=$('wcInput').value;output('wcOut',(t.trim()?t.trim().split(/\\s+/).length:0)+' words · '+t.length+' characters · '+t.replace(/\\s/g,'').length+' without spaces · '+(t?t.split(/\\n/).length:0)+' lines · '+(t.match(/[.!?]+/g)||[]).length+' sentence endings');};$('wcInput').addEventListener('input',update);update();}
 if(id==='lineclean'){const inp=$('lineInput');const run=(fn)=>output('lineOut',fn(inp.value.split(/\\n/)).join('\\n'));by('lineTrim',()=>run(a=>a.map(x=>x.trim())));by('lineSort',()=>run(a=>a.map(x=>x.trim()).sort((a,b)=>a.localeCompare(b))));by('lineUnique',()=>run(a=>[...new Set(a.map(x=>x.trim()).filter(Boolean))]));}
 if(id==='slug'){by('slugMake',()=>output('slugOut',$('slugInput').value.normalize('NFKD').replace(/[\\u0300-\\u036f]/g,'').toLowerCase().trim().replace(/[^a-z0-9]+/g,'-').replace(/^-+|-+$/g,'')));}
 if(id==='age'){by('ageCalc',()=>{const d=new Date($('ageInput').value+'T12:00:00'),now=new Date();if(isNaN(d)||d>now){output('ageOut','Choose a valid date in the past.');return;}let y=now.getFullYear()-d.getFullYear(),m=now.getMonth()-d.getMonth(),days=now.getDate()-d.getDate();if(days<0){m--;days+=new Date(now.getFullYear(),now.getMonth(),0).getDate();}if(m<0){y--;m+=12;}output('ageOut',y+' years, '+m+' months, '+days+' days.');});}
 if(id==='tip'){by('tipCalc',()=>{const bill=num('tipBill'),rate=num('tipRate'),people=Math.max(1,Math.floor(num('tipPeople')));const tip=bill*rate/100;output('tipOut','Tip: '+fmt(tip)+'\\nTotal: '+fmt(bill+tip)+'\\nPer person: '+fmt((bill+tip)/people));});}
 if(id==='compound'){by('ciCalc',()=>{const p=num('ciPrincipal'),r=num('ciRate')/100,n=num('ciN'),years=num('ciYears');if(p<0||r<0||n<=0||years<0){output('ciOut','Enter non-negative values.');return;}const total=p*Math.pow(1+r/n,n*years);output('ciOut','Estimated final value: '+fmt(total)+'\\nGrowth: '+fmt(total-p)+'\\nStarting amount: '+fmt(p));});}
 if(id==='hash'){by('hashRun',async()=>{try{if(!crypto.subtle)throw new Error('Crypto API unavailable');const bytes=new TextEncoder().encode($('hashInput').value);const digest=await crypto.subtle.digest('SHA-256',bytes);output('hashOut',Array.from(new Uint8Array(digest)).map(b=>b.toString(16).padStart(2,'0')).join(''));}catch(e){output('hashOut','SHA-256 is not available in this WebView context.');}});}
 if(id==='colorcontrast'){const update=()=>{const fg=$('fgColor').value,bg=$('bgColor').value;$('contrastSample').style.color=fg;$('contrastSample').style.background=bg;const lum=h=>{const c=[1,3,5].map(i=>parseInt(h.slice(i,i+2),16)/255).map(v=>v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4));return .2126*c[0]+.7152*c[1]+.0722*c[2];};const a=lum(fg),b=lum(bg),ratio=(Math.max(a,b)+.05)/(Math.min(a,b)+.05);output('contrastOut','Contrast ratio: '+ratio.toFixed(2)+':1 · '+(ratio>=7?'AAA for normal text':ratio>=4.5?'AA for normal text':ratio>=3?'Large text only':'Low contrast'));};$('fgColor').addEventListener('input',update);$('bgColor').addEventListener('input',update);update();}
}
function displayDirectoryCategory(category){
 if(category !== 'India services') return category;
 const labels = {"bn":"ভারতের পরিষেবা","mr":"भारत सेवा","gu":"ભારતની સેવાઓ","pa":"ਭਾਰਤ ਦੀਆਂ ਸੇਵਾਵਾਂ","ur":"بھارت کی خدمات","ml":"ഇന്ത്യൻ സേവനങ്ങൾ","hi":"भारत सेवाएँ","ta":"இந்திய சேவைகள்","te":"భారత సేవలు","kn":"ಭಾರತದ ಸೇವೆಗಳು","en":"India services","es":"Servicios de India","fr":"Services en Inde","ar":"الخدمات الهندية"};
 return labels[currentLanguage] || labels.en;
}
function renderCategories(){const box=$('categories');box.innerHTML='';const categories=['All',...new Set(DIRECTORY.map(x=>x.category))];categories.forEach(c=>{const b=document.createElement('button');b.className='category'+(c==='All'?' active':'');b.textContent=displayDirectoryCategory(c);b.addEventListener('click',()=>{document.querySelectorAll('.category').forEach(x=>x.classList.toggle('active',x===b));renderDirectory(c);});box.appendChild(b);});}
let chosenCategory='All';
function renderDirectory(cat){if(cat)chosenCategory=cat;const q=($('directorySearch')?.value||'').trim().toLowerCase();const entries=DIRECTORY.filter(x=>(chosenCategory==='All'||x.category===chosenCategory)&&(!q||(x.name+' '+x.category+' '+x.url).toLowerCase().includes(q)));const grid=$('directoryGrid');const fragment=document.createDocumentFragment();entries.forEach(site=>{const a=document.createElement('a');a.className='directory-item';a.href=site.url;a.rel='noreferrer noopener';a.innerHTML='<b>'+esc(site.name)+'</b><small>'+esc(displayDirectoryCategory(site.category))+' · '+esc(new URL(site.url).hostname)+'</small>';fragment.appendChild(a);});grid.replaceChildren(fragment);$('directoryEmpty').style.display=entries.length?'none':'block';$('directoryCount').textContent=entries.length+' / '+DIRECTORY.length+' '+extraUi().links;}
renderCategories();let directorySearchTimer=0;$('directorySearch').addEventListener('input',()=>{if(directorySearchTimer)clearTimeout(directorySearchTimer);directorySearchTimer=setTimeout(()=>renderDirectory(),110);});
$('countPill').textContent=DIRECTORY.length+' '+extraUi().curated;
$('directoryCount').textContent=DIRECTORY.length+' '+extraUi().sites;
</script>
</body>
</html>
        """);
    String html = htmlBuilder.toString();
        return html.replace("THEME", theme)
                .replace("__DARKCLASS__", dark ? "dark" : "")
                .replace("__MODE__", mode)
                .replace("__DIRECTORY__", linksJson)
                .replace("__BOOKMARKS__", savedJson)
                .replace("__ENGINE__", selectedEngine)
                .replace("__PRIVATE__", Boolean.toString(privateMode))
                .replace("__LANGUAGE__", selectedLanguage);
    }

    /** Read the static curated directory from a bounded, local-only APK asset. */
    private static String readDirectoryAsset(Context context) {
        if (context == null) return "";
        String cached = cachedDirectorySource;
        if (cached != null) return cached;
        synchronized (HomePage.class) {
            if (cachedDirectorySource != null) return cachedDirectorySource;
            try (InputStream input = context.getAssets().open("home_directory.txt");
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                int total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 65536) {
                        cachedDirectorySource = "";
                        return cachedDirectorySource;
                    }
                    output.write(buffer, 0, count);
                }
                cachedDirectorySource = new String(output.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException unavailable) {
                // Home remains functional if a future packaging mistake drops this optional list.
                cachedDirectorySource = "";
            }
            return cachedDirectorySource;
        }
    }

    /** Parse only well-formed HTTPS entries and encode them safely for an inline script value. */
    static String directoryJson(String source) {
        JSONArray directory = new JSONArray();
        String normalized = source == null ? "" : source.trim();
        if (!normalized.isEmpty()) {
            for (String row : normalized.split("\\R")) {
                String[] parts = row.split("\\|", 3);
                if (parts.length != 3 || !parts[2].startsWith("https://")) continue;
                if (parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) continue;
                try {
                    JSONObject item = new JSONObject();
                    item.put("category", parts[0]);
                    item.put("name", parts[1]);
                    item.put("url", parts[2]);
                    directory.put(item);
                } catch (JSONException ignored) {}
            }
        }
        return forScript(directory.toString());
    }

    private static String forScript(String json) {
        // Prevent user-supplied bookmark titles/URLs from closing the containing script element.
        String slash = "\\";
        return json.replace("&", slash + "u0026")
                .replace("<", slash + "u003c")
                .replace(">", slash + "u003e");
    }
}
