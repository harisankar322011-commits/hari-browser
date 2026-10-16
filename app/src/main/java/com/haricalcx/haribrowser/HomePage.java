package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Offline-first, self-contained start page. No remote scripts, fonts, pixels or images are loaded.
 * External destinations are opened only after the user taps them.
 */
final class HomePage {
    private HomePage() {}

    private static final String DIRECTORY = """
            Search & reference|DuckDuckGo|https://duckduckgo.com/
            Search & reference|Brave Search|https://search.brave.com/
            Search & reference|Startpage|https://www.startpage.com/
            Search & reference|Google Search|https://www.google.com/
            Search & reference|Bing Search|https://www.bing.com/
            Search & reference|Wikipedia|https://www.wikipedia.org/
            Search & reference|Wikidata|https://www.wikidata.org/
            Search & reference|Encyclopaedia Britannica|https://www.britannica.com/
            Search & reference|Internet Archive|https://archive.org/
            Search & reference|Wayback Machine|https://web.archive.org/
            Search & reference|WorldCat|https://www.worldcat.org/
            Search & reference|WolframAlpha|https://www.wolframalpha.com/
            Search & reference|RefSeek|https://www.refseek.com/
            Search & reference|Swisscows Search|https://swisscows.com/
            Search & reference|Dictionary.com|https://www.dictionary.com/
            Search & reference|Merriam-Webster|https://www.merriam-webster.com/
            Learning & courses|Khan Academy|https://www.khanacademy.org/
            Learning & courses|Coursera|https://www.coursera.org/
            Learning & courses|edX|https://www.edx.org/
            Learning & courses|MIT OpenCourseWare|https://ocw.mit.edu/
            Learning & courses|OpenLearn|https://www.open.edu/openlearn/
            Learning & courses|Saylor Academy|https://www.saylor.org/
            Learning & courses|OpenStax|https://openstax.org/
            Learning & courses|CK-12|https://www.ck12.org/
            Learning & courses|TED-Ed|https://ed.ted.com/
            Learning & courses|Crash Course|https://thecrashcourse.com/
            Learning & courses|GCFGlobal|https://edu.gcfglobal.org/
            Learning & courses|BBC Bitesize|https://www.bbc.co.uk/bitesize
            Learning & courses|National Geographic Education|https://education.nationalgeographic.org/
            Learning & courses|Smithsonian Learning Lab|https://learninglab.si.edu/
            Learning & courses|Duolingo|https://www.duolingo.com/
            Learning & courses|Alison Courses|https://alison.com/
            Learning & courses|Brilliant|https://brilliant.org/
            Learning & courses|Open Culture|https://www.openculture.com/
            Learning & courses|FutureLearn|https://www.futurelearn.com/
            Learning & courses|Class Central|https://www.classcentral.com/
            Coding & development|GitHub|https://github.com/
            Coding & development|GitLab|https://gitlab.com/
            Coding & development|Codeberg|https://codeberg.org/
            Coding & development|SourceForge|https://sourceforge.net/
            Coding & development|MDN Web Docs|https://developer.mozilla.org/
            Coding & development|web.dev|https://web.dev/
            Coding & development|W3Schools|https://www.w3schools.com/
            Coding & development|freeCodeCamp|https://www.freecodecamp.org/
            Coding & development|The Odin Project|https://www.theodinproject.com/
            Coding & development|Stack Overflow|https://stackoverflow.com/
            Coding & development|Super User|https://superuser.com/
            Coding & development|Server Fault|https://serverfault.com/
            Coding & development|Ask Ubuntu|https://askubuntu.com/
            Coding & development|DevDocs|https://devdocs.io/
            Coding & development|Can I Use|https://caniuse.com/
            Coding & development|npm|https://www.npmjs.com/
            Coding & development|PyPI|https://pypi.org/
            Coding & development|Maven Central|https://central.sonatype.com/
            Coding & development|crates.io|https://crates.io/
            Coding & development|Go documentation|https://go.dev/doc/
            Coding & development|Rust language|https://www.rust-lang.org/
            Coding & development|Python|https://www.python.org/
            Coding & development|Kotlin|https://kotlinlang.org/
            Coding & development|Java|https://www.java.com/
            Coding & development|Android Developers|https://developer.android.com/
            Coding & development|Microsoft Learn|https://learn.microsoft.com/
            Coding & development|Swift|https://www.swift.org/
            Coding & development|cppreference|https://en.cppreference.com/
            Coding & development|Linux Kernel|https://www.kernel.org/
            Coding & development|Open Source Guides|https://opensource.guide/
            Math & science|arXiv|https://arxiv.org/
            Math & science|PubMed|https://pubmed.ncbi.nlm.nih.gov/
            Math & science|Europe PMC|https://europepmc.org/
            Math & science|Crossref|https://www.crossref.org/
            Math & science|DOAJ|https://doaj.org/
            Math & science|CORE Research|https://core.ac.uk/
            Math & science|OpenAlex|https://openalex.org/
            Math & science|NASA|https://www.nasa.gov/
            Math & science|European Space Agency|https://www.esa.int/
            Math & science|NOAA|https://www.noaa.gov/
            Math & science|US Geological Survey|https://www.usgs.gov/
            Math & science|NIST|https://www.nist.gov/
            Math & science|CERN|https://home.cern/
            Math & science|Fermilab|https://www.fnal.gov/
            Math & science|Science.gov|https://www.science.gov/
            Math & science|Royal Society|https://royalsociety.org/
            Math & science|Nature|https://www.nature.com/
            Math & science|Science Magazine|https://www.science.org/
            Math & science|PLOS|https://plos.org/
            Math & science|iNaturalist|https://www.inaturalist.org/
            Math & science|GBIF|https://www.gbif.org/
            Math & science|Encyclopedia of Life|https://eol.org/
            Math & science|Our World in Data|https://ourworldindata.org/
            Math & science|Nobel Prize|https://www.nobelprize.org/
            Math & science|MathWorld|https://mathworld.wolfram.com/
            Math & science|Desmos|https://www.desmos.com/
            Math & science|GeoGebra|https://www.geogebra.org/
            Math & science|CK-12 Simulations|https://interactives.ck12.org/
            Books & culture|Project Gutenberg|https://www.gutenberg.org/
            Books & culture|Open Library|https://openlibrary.org/
            Books & culture|Wikisource|https://wikisource.org/
            Books & culture|LibriVox|https://librivox.org/
            Books & culture|Standard Ebooks|https://standardebooks.org/
            Books & culture|ManyBooks|https://manybooks.net/
            Books & culture|Goodreads|https://www.goodreads.com/
            Books & culture|Google Books|https://books.google.com/
            Books & culture|Poetry Foundation|https://www.poetryfoundation.org/
            Books & culture|Poetry Archive|https://poetryarchive.org/
            Books & culture|Europeana|https://www.europeana.eu/
            Books & culture|Digital Public Library of America|https://dp.la/
            Books & culture|The Met Collection|https://www.metmuseum.org/art/collection
            Books & culture|Louvre Collections|https://collections.louvre.fr/
            Books & culture|Rijksmuseum|https://www.rijksmuseum.nl/
            Productivity & documents|Google Docs|https://docs.google.com/
            Productivity & documents|Google Sheets|https://sheets.google.com/
            Productivity & documents|Google Slides|https://slides.google.com/
            Productivity & documents|Microsoft 365|https://www.microsoft365.com/
            Productivity & documents|ONLYOFFICE|https://www.onlyoffice.com/
            Productivity & documents|Zoho Writer|https://www.zoho.com/writer/
            Productivity & documents|Etherpad|https://etherpad.org/
            Productivity & documents|Trello|https://trello.com/
            Productivity & documents|Todoist|https://todoist.com/
            Productivity & documents|Asana|https://asana.com/
            Productivity & documents|ClickUp|https://clickup.com/
            Productivity & documents|Monday.com|https://monday.com/
            Productivity & documents|Calendly|https://calendly.com/
            Productivity & documents|TimeAndDate|https://www.timeanddate.com/
            Productivity & documents|Doodle|https://doodle.com/
            Productivity & documents|Excalidraw|https://excalidraw.com/
            Productivity & documents|diagrams.net|https://app.diagrams.net/
            Productivity & documents|Mermaid Live|https://mermaid.live/
            Productivity & documents|Overleaf|https://www.overleaf.com/
            Productivity & documents|Zotero|https://www.zotero.org/
            News & public information|BBC News|https://www.bbc.com/news
            News & public information|Reuters|https://www.reuters.com/
            News & public information|Associated Press|https://apnews.com/
            News & public information|The Guardian|https://www.theguardian.com/
            News & public information|DW News|https://www.dw.com/
            News & public information|Al Jazeera|https://www.aljazeera.com/
            News & public information|NPR|https://www.npr.org/
            News & public information|The Hindu|https://www.thehindu.com/
            News & public information|The Indian Express|https://indianexpress.com/
            News & public information|Times of India|https://timesofindia.indiatimes.com/
            News & public information|Economic Times|https://economictimes.indiatimes.com/
            News & public information|Press Information Bureau India|https://pib.gov.in/
            News & public information|India.gov.in|https://www.india.gov.in/
            News & public information|Open Government Data India|https://data.gov.in/
            News & public information|Lok Sabha|https://loksabha.nic.in/
            News & public information|Rajya Sabha|https://rajyasabha.nic.in/
            News & public information|Supreme Court of India|https://www.sci.gov.in/
            News & public information|Reserve Bank of India|https://www.rbi.org.in/
            News & public information|ISRO|https://www.isro.gov.in/
            News & public information|Election Commission of India|https://www.eci.gov.in/
            News & public information|UNESCO|https://www.unesco.org/
            News & public information|United Nations|https://www.un.org/
            News & public information|UNICEF|https://www.unicef.org/
            Maps & travel|Google Maps|https://maps.google.com/
            Maps & travel|OpenStreetMap|https://www.openstreetmap.org/
            Maps & travel|Bing Maps|https://www.bing.com/maps
            Maps & travel|Mapillary|https://www.mapillary.com/
            Maps & travel|Rome2Rio|https://www.rome2rio.com/
            Maps & travel|Wikivoyage|https://www.wikivoyage.org/
            Maps & travel|Lonely Planet|https://www.lonelyplanet.com/
            Maps & travel|IRCTC|https://www.irctc.co.in/
            Maps & travel|Indian Railways NTES|https://enquiry.indianrail.gov.in/
            Maps & travel|redBus|https://www.redbus.in/
            Maps & travel|MakeMyTrip|https://www.makemytrip.com/
            Maps & travel|Goibibo|https://www.goibibo.com/
            Maps & travel|Cleartrip|https://www.cleartrip.com/
            Maps & travel|Passport Seva|https://www.passportindia.gov.in/
            Maps & travel|IndiGo|https://www.goindigo.in/
            Maps & travel|Air India|https://www.airindia.com/
            Maps & travel|IRCTC eCatering|https://www.ecatering.irctc.co.in/
            Maps & travel|India Tourism|https://www.incredibleindia.gov.in/
            Maps & travel|Booking.com|https://www.booking.com/
            Maps & travel|Skyscanner|https://www.skyscanner.co.in/
            Privacy & security|Electronic Frontier Foundation|https://www.eff.org/
            Privacy & security|Privacy Guides|https://www.privacyguides.org/
            Privacy & security|Mozilla Monitor|https://monitor.mozilla.org/
            Privacy & security|Have I Been Pwned|https://haveibeenpwned.com/
            Privacy & security|Cloudflare Radar|https://radar.cloudflare.com/
            Privacy & security|BrowserLeaks|https://browserleaks.com/
            Privacy & security|Qualys SSL Labs|https://www.ssllabs.com/ssltest/
            Privacy & security|Let's Encrypt|https://letsencrypt.org/
            Privacy & security|OpenSSL|https://www.openssl.org/
            Privacy & security|GnuPG|https://gnupg.org/
            Privacy & security|Signal|https://signal.org/
            Privacy & security|Proton|https://proton.me/
            Privacy & security|Tor Project|https://www.torproject.org/
            Privacy & security|OWASP|https://owasp.org/
            Privacy & security|Security Headers|https://securityheaders.com/
            Privacy & security|Mozilla Security Blog|https://blog.mozilla.org/security/
            Cloud & communication|Gmail|https://mail.google.com/
            Cloud & communication|Outlook Mail|https://outlook.live.com/
            Cloud & communication|Proton Mail|https://mail.proton.me/
            Cloud & communication|Fastmail|https://www.fastmail.com/
            Cloud & communication|Google Drive|https://drive.google.com/
            Cloud & communication|OneDrive|https://onedrive.live.com/
            Cloud & communication|Dropbox|https://www.dropbox.com/
            Cloud & communication|Nextcloud|https://nextcloud.com/
            Cloud & communication|MEGA|https://mega.io/
            Cloud & communication|SwissTransfer|https://www.swisstransfer.com/
            Cloud & communication|WeTransfer|https://wetransfer.com/
            Cloud & communication|Telegram Web|https://web.telegram.org/
            Cloud & communication|WhatsApp Web|https://web.whatsapp.com/
            Cloud & communication|Discord|https://discord.com/app
            Cloud & communication|Slack|https://slack.com/
            Design & media tools|Figma|https://www.figma.com/
            Design & media tools|Canva|https://www.canva.com/
            Design & media tools|Adobe Color|https://color.adobe.com/
            Design & media tools|Photopea|https://www.photopea.com/
            Design & media tools|SVGOMG|https://jakearchibald.github.io/svgomg/
            Design & media tools|Squoosh|https://squoosh.app/
            Design & media tools|TinyPNG|https://tinypng.com/
            Design & media tools|Unsplash|https://unsplash.com/
            Design & media tools|Pexels|https://www.pexels.com/
            Design & media tools|Pixabay|https://pixabay.com/
            Design & media tools|Wikimedia Commons|https://commons.wikimedia.org/
            Design & media tools|Google Fonts|https://fonts.google.com/
            Design & media tools|Font Squirrel|https://www.fontsquirrel.com/
            Design & media tools|Coolors|https://coolors.co/
            Design & media tools|Krita|https://krita.org/
            Design & media tools|GIMP|https://www.gimp.org/
            Design & media tools|Inkscape|https://inkscape.org/
            Design & media tools|Blender|https://www.blender.org/
            Design & media tools|Audacity|https://www.audacityteam.org/
            Design & media tools|OBS Studio|https://obsproject.com/
            Shopping & comparison|Amazon India|https://www.amazon.in/
            Shopping & comparison|Flipkart|https://www.flipkart.com/
            Shopping & comparison|Croma|https://www.croma.com/
            Shopping & comparison|Reliance Digital|https://www.reliancedigital.in/
            Shopping & comparison|Tata CLiQ|https://www.tatacliq.com/
            Shopping & comparison|Myntra|https://www.myntra.com/
            Shopping & comparison|AJIO|https://www.ajio.com/
            Shopping & comparison|IKEA India|https://www.ikea.com/in/en/
            Shopping & comparison|Decathlon India|https://www.decathlon.in/
            Shopping & comparison|eBay|https://www.ebay.com/
            Shopping & comparison|Etsy|https://www.etsy.com/
            Shopping & comparison|Newegg|https://www.newegg.com/
            Shopping & comparison|PriceRunner|https://www.pricerunner.com/
            Shopping & comparison|GSMArena|https://www.gsmarena.com/
            Shopping & comparison|Notebookcheck|https://www.notebookcheck.net/
            Video, music & entertainment|YouTube|https://www.youtube.com/
            Video, music & entertainment|Vimeo|https://vimeo.com/
            Video, music & entertainment|Dailymotion|https://www.dailymotion.com/
            Video, music & entertainment|Twitch|https://www.twitch.tv/
            Video, music & entertainment|Spotify|https://open.spotify.com/
            Video, music & entertainment|SoundCloud|https://soundcloud.com/
            Video, music & entertainment|Bandcamp|https://bandcamp.com/
            Video, music & entertainment|IMDb|https://www.imdb.com/
            Video, music & entertainment|Rotten Tomatoes|https://www.rottentomatoes.com/
            Video, music & entertainment|Letterboxd|https://letterboxd.com/
            Video, music & entertainment|JustWatch|https://www.justwatch.com/
            Video, music & entertainment|Radio Garden|https://radio.garden/
            Video, music & entertainment|BBC Sounds|https://www.bbc.co.uk/sounds
            Video, music & entertainment|Mixcloud|https://www.mixcloud.com/
            Video, music & entertainment|Apple Music|https://music.apple.com/
            Video, music & entertainment|Crunchyroll|https://www.crunchyroll.com/
            Video, music & entertainment|Netflix|https://www.netflix.com/
            Video, music & entertainment|Disney+|https://www.disneyplus.com/
            Sports|ESPN|https://www.espn.com/
            Sports|Olympics|https://olympics.com/
            Sports|FIFA|https://www.fifa.com/
            Sports|ICC Cricket|https://www.icc-cricket.com/
            Sports|BCCI|https://www.bcci.tv/
            Sports|NBA|https://www.nba.com/
            Sports|Formula 1|https://www.formula1.com/
            Sports|Wimbledon|https://www.wimbledon.com/
            Sports|Premier League|https://www.premierleague.com/
            Sports|UEFA|https://www.uefa.com/
            Food & home|BBC Good Food|https://www.bbcgoodfood.com/
            Food & home|Allrecipes|https://www.allrecipes.com/
            Food & home|Serious Eats|https://www.seriouseats.com/
            Food & home|King Arthur Baking|https://www.kingarthurbaking.com/
            Food & home|Tasty|https://tasty.co/
            Food & home|Epicurious|https://www.epicurious.com/
            Food & home|Veg Recipes of India|https://www.vegrecipesofindia.com/
            Food & home|Hebbars Kitchen|https://hebbarskitchen.com/
            Food & home|Swasthi's Recipes|https://www.indianhealthyrecipes.com/
            Food & home|Food Network|https://www.foodnetwork.com/
            Weather & environment|India Meteorological Department|https://mausam.imd.gov.in/
            Weather & environment|National Weather Service|https://www.weather.gov/
            Weather & environment|UK Met Office|https://www.metoffice.gov.uk/
            Weather & environment|Windy|https://www.windy.com/
            Weather & environment|AccuWeather|https://www.accuweather.com/
            Weather & environment|Climate Reanalyzer|https://climatereanalyzer.org/
            Weather & environment|Global Forest Watch|https://www.globalforestwatch.org/
            Weather & environment|AirNow|https://www.airnow.gov/
            Weather & environment|IQAir|https://www.iqair.com/
            Weather & environment|Time.is|https://time.is/
            """;

    static String render(boolean dark, String engine, String bookmarkJson, boolean privateMode) {
        String theme = dark
                ? "--bg:#0b1020;--surface:#141d2e;--surface2:#1b2940;--text:#edf3ff;--muted:#a0aec4;--line:#2b3a52;--accent:#43d9c5;--accentText:#082c2a;--shadow:0 14px 40px #0003"
                : "--bg:#f4f7fb;--surface:#ffffff;--surface2:#eef5f8;--text:#16243b;--muted:#66758b;--line:#dfe7ef;--accent:#13b8a6;--accentText:#ffffff;--shadow:0 14px 40px #17304a0d";
        JSONArray directory = new JSONArray();
        for (String row : DIRECTORY.strip().split("\\R")) {
            String[] parts = row.split("\\|", 3);
            if (parts.length != 3 || !parts[2].startsWith("https://")) continue;
            try {
                JSONObject item = new JSONObject();
                item.put("category", parts[0]);
                item.put("name", parts[1]);
                item.put("url", parts[2]);
                directory.put(item);
            } catch (JSONException ignored) {}
        }
        String linksJson = forScript(directory.toString());
        String savedJson = bookmarkJson == null || bookmarkJson.trim().isEmpty() ? "[]" : forScript(bookmarkJson);
        String selectedEngine = JSONObject.quote(engine == null ? "DuckDuckGo" : engine);
        String mode = privateMode ? "PRIVATE SESSION" : "NORMAL SESSION";

        String html = """
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
.logo{width:42px;height:42px;flex:0 0 42px;display:grid;place-items:center;background:linear-gradient(145deg,#19c8b5,#177e9b);border-radius:15px;color:white;font-size:24px;font-weight:900;box-shadow:var(--shadow)}
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
</style>
</head>
<body>
<div class="shell">
<header class="top"><div class="logo">H</div><div><div class="brand">HARI BROWSER</div><div class="brand-sub">Your web, your space — designed for privacy</div></div><span class="mode">__MODE__</span></header>
<section class="hero">
  <div class="eyebrow">A BETTER START</div>
  <h1>Search. Learn.<br><span class="accent">Make it happen.</span></h1>
  <p class="sub" id="greeting">A thoughtful home for your web.</p>
  <form id="searchForm" class="search">
    <select id="engine" aria-label="Search engine"><option>DuckDuckGo</option><option>Startpage</option><option>Brave Search</option><option>Google</option><option>Bing</option></select>
    <input id="query" type="search" placeholder="Search the web or enter an HTTPS site…" autocomplete="off" spellcheck="false" aria-label="Search or website address">
    <button class="primary" type="submit">Search ↗</button>
  </form>
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
</section>

<section id="toolsPanel" class="panel">
  <div class="section-head"><h2>Built-in toolbox</h2><span class="mini">Works locally, no account</span></div>
  <p class="mini">Calculations, text utilities and notes run in this start page. Do not store passwords or sensitive secrets in notes.</p>
  <div id="toolGrid" class="tool-grid"></div>
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
  <div class="card"><h3>🛡 Safer browsing</h3><p>HTTPS-only navigation, certificate-error blocking, Android Safe Browsing, mixed-content blocking, disabled file access, disabled geolocation, and denied site permission prompts.</p><a class="action" style="margin-top:10px" href="https://hari-browser-home.invalid/action/security">View current site security ↗</a></div>
  <div class="card"><h3>🧹 Data controls</h3><p>History is disabled by default. Bookmarks use encrypted on-device storage. Private browsing uses a separate profile on supported Android versions, but it cannot hide activity from websites or your network provider.</p><div class="actions" style="margin-top:10px"><a class="action" href="https://hari-browser-home.invalid/action/clear">Clear browser data<span>Review before clearing</span></a><a class="action" href="https://hari-browser-home.invalid/action/emergency">Emergency clear<span>Confirmation required</span></a><a class="action" href="https://hari-browser-home.invalid/action/settings">Privacy settings<span>Cookies, JavaScript and trackers</span></a></div></div>
  <div class="card"><h3>What this browser does not promise</h3><p>It is not a complete anonymity system. Websites and your selected search provider still receive requests when you visit them. The local tracker blocklist is best-effort, and private mode cannot erase downloads, screenshots or network logs.</p></div>
</section>

<p class="localnote">Made for quick access. No external scripts, tracking pixels, remote fonts or AI shortcuts are loaded by this start page. External websites receive data when you choose to visit them.</p>
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
 {id:'gradient',name:'Gradient designer',icon:'🌈',desc:'Preview and copy CSS linear gradients'},
 {id:'csvjson',name:'CSV to JSON',icon:'🗃️',desc:'Convert comma-separated data into JSON'},
 {id:'textdiff',name:'Text comparison',icon:'↔️',desc:'Show added and removed lines between two texts'}
];
const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
function validHttps(raw){try{const u=new URL(raw);return u.protocol==='https:' && !!u.hostname;}catch(e){return false;}}
function iconFor(name){return (String(name||'?').trim().match(/[\\p{Extended_Pictographic}]/u)||[String(name||'?').charAt(0).toUpperCase()])[0];}
function setDateTime(){const d=new Date();const hour=d.getHours();const greeting=hour<12?'Good morning':hour<17?'Good afternoon':'Good evening';$('greeting').textContent=greeting+' — your shortcuts, tools and bookmarks are ready. '+d.toLocaleDateString(undefined,{weekday:'long',month:'short',day:'numeric'});$('clock').textContent='Local time · '+d.toLocaleTimeString(undefined,{hour:'2-digit',minute:'2-digit'});}
setDateTime();
$('engine').value=['DuckDuckGo','Startpage','Brave Search','Google','Bing'].includes(ENGINE_SETTING)?ENGINE_SETTING:'DuckDuckGo';
$('searchForm').addEventListener('submit',e=>{e.preventDefault();const q=$('query').value.trim();if(!q)return;let url;if(/^https:\\/\\//i.test(q)){url=q;}else if(/^http:\\/\\//i.test(q)){alert('Insecure HTTP is blocked. Use HTTPS.');return;}else if(/^[a-z][a-z0-9+.-]*:/i.test(q)&&!/^https?:/i.test(q)){alert('Only HTTPS web addresses are allowed.');return;}else if(!q.includes(' ')&&q.includes('.')&&/^[\\w.-]+(?::\\d+)?(?:\\/.*)?$/.test(q)){url='https://'+q;}else{const engine=$('engine').value;const encoded=encodeURIComponent(q);url=engine==='Startpage'?'https://www.startpage.com/sp/search?query='+encoded:engine==='Brave Search'?'https://search.brave.com/search?q='+encoded:engine==='Google'?'https://www.google.com/search?q='+encoded:engine==='Bing'?'https://www.bing.com/search?q='+encoded:'https://duckduckgo.com/?q='+encoded;}if(validHttps(url))window.location.href=url;else alert('Please enter a valid HTTPS website address.');});
// The native browser preference seeds the selected provider on every home load.
function showPanel(id){document.querySelectorAll('.panel').forEach(p=>p.classList.toggle('active',p.id===id));document.querySelectorAll('.navbtn').forEach(b=>b.classList.toggle('active',b.dataset.panel===id));if(id==='explorePanel')renderDirectory();window.scrollTo(0,0);}
document.querySelectorAll('.navbtn').forEach(b=>b.addEventListener('click',()=>showPanel(b.dataset.panel)));
function internalAction(name){return 'https://hari-browser-home.invalid/action/'+name;}
function renderFeatured(){const box=$('shortcuts');box.innerHTML='';let custom=[];try{custom=JSON.parse(localStorage.getItem('hari.custom.shortcuts.v1')||'[]');if(!Array.isArray(custom))custom=[];}catch(e){}const all=[...FEATURED,...custom.filter(x=>x&&validHttps(x.url)&&x.name).slice(0,40)];all.forEach(site=>{const a=document.createElement('a');a.className='tile';a.href=site.url;a.rel='noreferrer noopener';const ic=document.createElement('span');ic.className='icon';ic.textContent=site.icon||iconFor(site.name);const body=document.createElement('span');body.innerHTML='<strong>'+esc(site.name)+'</strong><small>'+esc(site.desc||new URL(site.url).hostname)+'</small>';a.append(ic,body);box.appendChild(a);});if(all.length===0)box.innerHTML='<div class="empty">Add your first HTTPS shortcut.</div>';}
function renderBookmarks(){const box=$('bookmarks');box.innerHTML='';const list=(Array.isArray(BOOKMARKS)?BOOKMARKS:[]).filter(b=>b&&validHttps(b.url)).slice(-18).reverse();if(!list.length){box.innerHTML='<div class="empty" style="grid-column:1/-1">No saved bookmarks yet. Open a website and tap ★ Save.</div>';return;}list.forEach(b=>{const a=document.createElement('a');a.className='bookmark';a.href=b.url;a.rel='noreferrer noopener';const ic=document.createElement('span');ic.className='fav';ic.textContent='★';const text=document.createElement('span');text.style.minWidth='0';text.innerHTML='<strong>'+esc(b.title||new URL(b.url).hostname)+'</strong><small>'+esc(new URL(b.url).hostname)+'</small>';a.append(ic,text);box.appendChild(a);});}
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

function renderTools(){const grid=$('toolGrid');grid.innerHTML='';TOOLS.forEach(t=>{const b=document.createElement('button');b.className='tool';b.type='button';b.innerHTML='<span class="ti">'+esc(t.icon)+'</span><strong>'+esc(t.name)+'</strong><small>'+esc(t.desc)+'</small>';b.addEventListener('click',()=>openTool(t.id));grid.appendChild(b);});}
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
function renderCategories(){const box=$('categories');box.innerHTML='';const categories=['All',...new Set(DIRECTORY.map(x=>x.category))];categories.forEach(c=>{const b=document.createElement('button');b.className='category'+(c==='All'?' active':'');b.textContent=c;b.addEventListener('click',()=>{document.querySelectorAll('.category').forEach(x=>x.classList.toggle('active',x===b));renderDirectory(c);});box.appendChild(b);});}
let chosenCategory='All';
function renderDirectory(cat){if(cat)chosenCategory=cat;const q=($('directorySearch')?.value||'').trim().toLowerCase();const entries=DIRECTORY.filter(x=>(chosenCategory==='All'||x.category===chosenCategory)&&(!q||(x.name+' '+x.category+' '+x.url).toLowerCase().includes(q)));const grid=$('directoryGrid');grid.innerHTML='';entries.forEach(site=>{const a=document.createElement('a');a.className='directory-item';a.href=site.url;a.rel='noreferrer noopener';a.innerHTML='<b>'+esc(site.name)+'</b><small>'+esc(site.category)+' · '+esc(new URL(site.url).hostname)+'</small>';grid.appendChild(a);});$('directoryEmpty').style.display=entries.length?'none':'block';$('directoryCount').textContent=entries.length+' shown / '+DIRECTORY.length+' links';}
renderCategories();$('directorySearch').addEventListener('input',()=>renderDirectory());
$('countPill').textContent=DIRECTORY.length+' curated links';
$('directoryCount').textContent=DIRECTORY.length+' sites';
</script>
</body>
</html>
""";
        return html.replace("THEME", theme)
                .replace("__MODE__", mode)
                .replace("__DIRECTORY__", linksJson)
                .replace("__BOOKMARKS__", savedJson)
                .replace("__ENGINE__", selectedEngine)
                .replace("__PRIVATE__", Boolean.toString(privateMode));
    }

    private static String forScript(String json) {
        // Prevent user-supplied bookmark titles/URLs from closing the containing script element.
        String slash = "\\";
        return json.replace("&", slash + "u0026")
                .replace("<", slash + "u003c")
                .replace(">", slash + "u003e");
    }
}
