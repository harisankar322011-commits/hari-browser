# Hari Browser 2.3.0

- Add optional Teen Protection with a parent PIN.
- Block a starter list of explicit adult search phrases, selected real-money gambling searches and known adult/gambling domains.
- Force strict SafeSearch URL parameters on Google, Bing, Brave Search and DuckDuckGo when enabled.
- Apply protection to both standard and private browser sessions.
- Add unit tests for blocked phrases, query extraction, SafeSearch rewriting and parent-PIN verification.
- Add a manual Hari AI connection test in Settings, automatic fallback when a model is unavailable, and clearer API-key/quota error messages.
- Correct release-signing documentation and remove duplicate Hari AI documentation.

Teen Protection normalizes common punctuation/number substitutions and keeps common health, education and help-seeking queries accessible. It is still an app-level safeguard, not a complete web content filter or Android parental-control service. It cannot block every content source and can be bypassed if app data is cleared or the app is uninstalled.
