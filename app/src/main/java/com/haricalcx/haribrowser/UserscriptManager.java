package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Small, page-context userscript manager for Android WebView.
 *
 * This deliberately implements a safe subset of Tampermonkey metadata:
 * HTTPS @match/@include/@exclude and document-start/end/idle scheduling.
 * It does not implement GM_* privileges, @require, or @resource.
 */
final class UserscriptManager {
    static final int MAX_SCRIPT_CHARS = 64 * 1024;
    static final int MAX_TOTAL_CHARS = 256 * 1024;
    static final int MAX_SCRIPTS = 20;
    private static final String START_MARKER = "// ==UserScript==";
    private static final String END_MARKER = "// ==/UserScript==";

    private UserscriptManager() {}

    static final class Script {
        final String id;
        final String name;
        final String description;
        final String source;
        final String runAt;
        final List<String> matches;
        final List<String> includes;
        final List<String> excludes;
        boolean enabled;

        Script(String id, String name, String description, String source, String runAt,
               List<String> matches, List<String> includes, List<String> excludes,
               boolean enabled) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.source = source;
            this.runAt = runAt;
            this.matches = Collections.unmodifiableList(new ArrayList<>(matches));
            this.includes = Collections.unmodifiableList(new ArrayList<>(includes));
            this.excludes = Collections.unmodifiableList(new ArrayList<>(excludes));
            this.enabled = enabled;
        }

        boolean matchesUrl(String url) {
            if (url == null || !url.regionMatches(true, 0, "https://", 0, 8)) return false;
            boolean allowed = matchesAny(matches, url) || matchesAny(includes, url);
            return allowed && !matchesAny(excludes, url);
        }
    }

    static Script parse(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("The script file is empty.");
        }
        if (source.length() > MAX_SCRIPT_CHARS) {
            throw new IllegalArgumentException("Scripts must be 64 KiB or smaller.");
        }
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        boolean started = false;
        boolean ended = false;
        String name = null;
        String description = "";
        String runAt = "document-end";
        List<String> matches = new ArrayList<>();
        List<String> includes = new ArrayList<>();
        List<String> excludes = new ArrayList<>();
        List<String> grants = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (START_MARKER.equals(line)) {
                if (started) throw new IllegalArgumentException("The userscript metadata block is malformed.");
                started = true;
                continue;
            }
            if (END_MARKER.equals(line)) {
                if (!started) throw new IllegalArgumentException("Missing userscript metadata start marker.");
                ended = true;
                break;
            }
            if (!started || !line.startsWith("//")) continue;
            String metadata = line.substring(2).trim();
            if (!metadata.startsWith("@")) continue;
            int space = firstWhitespace(metadata);
            String key = space < 0 ? metadata.substring(1) : metadata.substring(1, space);
            String value = space < 0 ? "" : metadata.substring(space).trim();
            switch (key.toLowerCase(Locale.ROOT)) {
                case "name":
                    if (name == null && !value.isEmpty()) name = value;
                    break;
                case "description":
                    if (description.isEmpty()) description = value;
                    break;
                case "match":
                    validatePattern(value);
                    matches.add(value);
                    break;
                case "include":
                    validatePattern(value);
                    includes.add(value);
                    break;
                case "exclude":
                    validatePattern(value);
                    excludes.add(value);
                    break;
                case "run-at":
                    if (!value.isEmpty()) runAt = value.toLowerCase(Locale.ROOT);
                    break;
                case "grant":
                    grants.add(value);
                    break;
                case "require":
                case "resource":
                case "connect":
                    unsupported.add("@" + key);
                    break;
                default:
                    // Informational metadata such as @version/@author has no runtime effect.
                    break;
            }
        }
        if (!started || !ended) {
            throw new IllegalArgumentException("Add a complete // ==UserScript== metadata block first.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("The script needs an @name.");
        }
        if (matches.isEmpty() && includes.isEmpty()) {
            throw new IllegalArgumentException("Add at least one explicit HTTPS @match or @include rule.");
        }
        if (!"document-start".equals(runAt) && !"document-end".equals(runAt)
                && !"document-idle".equals(runAt)) {
            throw new IllegalArgumentException("Supported @run-at values are document-start, document-end, and document-idle.");
        }
        for (String grant : grants) {
            if (!"none".equals(grant)) {
                throw new IllegalArgumentException("Privileged @grant APIs are not supported yet. Use @grant none.");
            }
        }
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException("This version cannot safely resolve " + unsupported.get(0)
                    + " dependencies or permissions. Remove that directive or use a standalone script.");
        }
        return new Script(hash(normalized), name.trim(), description.trim(), normalized, runAt,
                dedupe(matches), dedupe(includes), dedupe(excludes), false);
    }

    static List<String> allowedOrigins(Script script) {
        Set<String> origins = new LinkedHashSet<>();
        for (String pattern : script.matches) origins.add(originFor(pattern));
        for (String pattern : script.includes) origins.add(originFor(pattern));
        return new ArrayList<>(origins);
    }

    static String injectionSource(Script script) {
        JSONArray included = new JSONArray();
        for (String pattern : combined(script.matches, script.includes)) {
            included.put(globRegex(pattern));
        }
        JSONArray excluded = new JSONArray();
        for (String pattern : script.excludes) excluded.put(globRegex(pattern));

        StringBuilder wrapped = new StringBuilder(script.source.length() + 1200);
        wrapped.append("(function(){'use strict';")
                .append("var currentUrl=String(location.href);")
                .append("var includeRules=").append(included).append(";")
                .append("var excludeRules=").append(excluded).append(";")
                .append("function matchesAny(rules){return rules.some(function(rule){")
                .append("try{return new RegExp(rule,'i').test(currentUrl);}catch(e){return false;}")
                .append("});}")
                .append("if(!matchesAny(includeRules)||matchesAny(excludeRules))return;")
                .append("var runScript=function(){try{(function(){\n")
                .append(script.source)
                .append("\n}).call(window);}catch(error){try{console.warn('[Hari userscript] Script failed:',error);}catch(ignore){}}};")
                .append("var runAt=").append(JSONObject.quote(script.runAt)).append(";")
                .append("if(runAt==='document-start'){runScript();}")
                .append("else if(runAt==='document-end'){")
                .append("if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',runScript,{once:true});}")
                .append("else{setTimeout(runScript,0);}}")
                .append("else{if(document.readyState==='complete'){setTimeout(runScript,100);}")
                .append("else{window.addEventListener('load',function(){setTimeout(runScript,100);},{once:true});}}")
                .append("})();");
        return wrapped.toString();
    }

    static String encode(List<Script> scripts) {
        if (scripts == null || scripts.size() > MAX_SCRIPTS) {
            throw new IllegalArgumentException("Hari Browser can store up to 20 userscripts.");
        }
        JSONArray array = new JSONArray();
        int total = 0;
        for (Script script : scripts) {
            if (script == null) continue;
            total += script.source.length();
            if (total > MAX_TOTAL_CHARS) {
                throw new IllegalArgumentException("The combined userscript collection exceeds 256 KiB.");
            }
            JSONObject item = new JSONObject();
            try {
                item.put("source", script.source);
                item.put("enabled", script.enabled);
                array.put(item);
            } catch (JSONException impossible) {
                throw new IllegalArgumentException("Could not save the userscript collection.", impossible);
            }
        }
        return array.toString();
    }

    static List<Script> decode(String encoded) {
        List<Script> scripts = new ArrayList<>();
        if (encoded == null || encoded.trim().isEmpty()) return scripts;
        try {
            JSONArray array = new JSONArray(encoded);
            int total = 0;
            for (int i = 0; i < array.length() && scripts.size() < MAX_SCRIPTS; i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String source = item.optString("source", "");
                total += source.length();
                if (total > MAX_TOTAL_CHARS) return new ArrayList<>();
                try {
                    Script script = parse(source);
                    script.enabled = item.optBoolean("enabled", false);
                    scripts.add(script);
                } catch (IllegalArgumentException ignored) {
                    // A malformed record is ignored rather than being injected.
                }
            }
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
        return scripts;
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return i;
        }
        return -1;
    }

    private static List<String> dedupe(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static List<String> combined(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private static String originFor(String pattern) {
        int authorityStart = "https://".length();
        int slash = pattern.indexOf('/', authorityStart);
        String authority = slash < 0 ? pattern.substring(authorityStart)
                : pattern.substring(authorityStart, slash);
        return "https://" + authority.toLowerCase(Locale.ROOT);
    }

    private static void validatePattern(String pattern) {
        if (pattern == null || pattern.length() > 512 || !pattern.startsWith("https://")) {
            throw new IllegalArgumentException("Site rules must be explicit HTTPS URL patterns.");
        }
        int authorityStart = "https://".length();
        int slash = pattern.indexOf('/', authorityStart);
        if (slash < 0) throw new IllegalArgumentException("Site rules must include a path, for example https://example.com/*.");
        String authority = pattern.substring(authorityStart, slash).toLowerCase(Locale.ROOT);
        if (authority.isEmpty() || authority.contains("@") || authority.equals("*")
                || authority.equals("*.") || authority.contains("..")) {
            throw new IllegalArgumentException("Use a specific HTTPS site in each @match rule.");
        }
        String host = authority;
        int colon = authority.lastIndexOf(':');
        if (colon > 0) {
            String port = authority.substring(colon + 1);
            if (!port.matches("[0-9]{1,5}")) {
                throw new IllegalArgumentException("The HTTPS site rule contains an invalid port.");
            }
            int portNumber;
            try { portNumber = Integer.parseInt(port); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid HTTPS port."); }
            if (portNumber < 1 || portNumber > 65535) {
                throw new IllegalArgumentException("The HTTPS site rule contains an invalid port.");
            }
            host = authority.substring(0, colon);
        }
        if (host.startsWith("*.")) host = host.substring(2);
        if (host.indexOf('*') >= 0 || !host.matches("[a-z0-9.-]+")
                || host.startsWith(".") || host.endsWith(".")) {
            throw new IllegalArgumentException("Use a domain name, optionally with a leading *. wildcard.");
        }
        if (pattern.substring(slash).contains("\\")) {
            throw new IllegalArgumentException("Backslashes are not allowed in site rules.");
        }
        String path = pattern.substring(slash);
        if (path.isEmpty() || path.charAt(0) != '/') {
            throw new IllegalArgumentException("Site rules must include a valid path.");
        }
    }

    private static boolean matchesAny(List<String> patterns, String url) {
        for (String pattern : patterns) {
            if (matchesPattern(pattern, url)) return true;
        }
        return false;
    }

    private static boolean matchesPattern(String pattern, String url) {
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null) return false;
            int authorityStart = "https://".length();
            int slash = pattern.indexOf('/', authorityStart);
            if (slash < 0) return false;
            String authority = pattern.substring(authorityStart, slash).toLowerCase(Locale.ROOT);
            String patternHost = authority;
            int patternPort = -1;
            int colon = authority.lastIndexOf(':');
            if (colon > 0) {
                patternPort = Integer.parseInt(authority.substring(colon + 1));
                patternHost = authority.substring(0, colon);
            }
            String actualHost = uri.getHost().toLowerCase(Locale.ROOT);
            if (patternHost.startsWith("*.")) {
                String base = patternHost.substring(2);
                if (!actualHost.equals(base) && !actualHost.endsWith("." + base)) return false;
            } else if (!actualHost.equals(patternHost)) {
                return false;
            }
            if (patternPort >= 0) {
                int actualPort = uri.getPort() < 0 ? 443 : uri.getPort();
                if (actualPort != patternPort) return false;
            }
            String urlPath = uri.getRawPath() == null || uri.getRawPath().isEmpty()
                    ? "/" : uri.getRawPath();
            String patternPath = pattern.substring(slash);
            if (patternPath.contains("?") && uri.getRawQuery() != null) urlPath += "?" + uri.getRawQuery();
            return Pattern.compile(globRegex(patternPath), Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                    .matcher(urlPath).matches();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String globRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char ch = glob.charAt(i);
            if (ch == '*') {
                regex.append(".*");
            } else {
                if ("\\.[]{}()+-^$|?".indexOf(ch) >= 0) regex.append('\\');
                regex.append(ch);
            }
        }
        return regex.append('$').toString();
    }

    private static String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 12; i++) result.append(String.format(Locale.ROOT, "%02x", bytes[i] & 0xff));
            return result.toString();
        } catch (Exception impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
