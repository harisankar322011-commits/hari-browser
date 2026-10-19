# Hari AI — Gemini cloud assistant for Hari Browser

Hari AI uses Google's Gemini Developer API instead of an on-device language model. It does not download a model or require LiteRT-LM. An internet connection and a Gemini API key supplied by each user are required.

## First-time setup

1. Open Hari Browser, open **Settings**, and choose **Hari AI (Gemini cloud; API key required)**.
2. Tap **Get API key** to open Google AI Studio, sign in if required, and create a Gemini API key: [Google AI Studio API keys](https://aistudio.google.com/apikey).
3. Return to Hari Browser, open **Gemini API settings**, choose **Enter API key**, and paste your key.
4. Send a question. Hari AI sends the prompt and a limited recent conversation history to Google's Gemini API over HTTPS.
5. To summarize an open HTTPS page, choose **Summarize current page** and explicitly confirm. Only then does the app send the page title, URL, and up to 4,000 characters of visible page text to Gemini.

## Cost and requirements

- The app contains no Gemini API key. Each person must supply their own key; never share it or commit it to source control.
- Google AI Studio/API access and free-tier quotas are controlled by Google and can change. Free usage is not unlimited, and requests may be rejected if a key, model, region, quota, or billing setup is not eligible. Review Google's current [Gemini API pricing](https://ai.google.dev/gemini-api/docs/pricing).
- This version uses the stable model identifier `gemini-3.8-flash`. Model availability and API terms may change.
- Prompts require internet access. The assistant cannot work offline.

## Privacy and key handling

- Hari Browser encrypts the user's API key using the existing `SecureStore`, backed by an AES-GCM key in Android Keystore. The key is not embedded in the app package or repository.
- Chats stay in memory for the current Hari AI screen only; the app does not save transcript history for later sessions.
- The current conversation context is sent to Google when a message is submitted. Page text is sent only after the user selects **Summarize current page** and confirms the action.
- Google's free Gemini API tier may use submitted content to improve Google products. Do not send passwords, financial information, private messages, confidential work, or sensitive personal information. Review Google's latest terms and data-use policy before use.
- Hari AI is not opened from private browsing.
- HTTPS protects the network connection, but this client-side design is intended for personal/family use with each person's own API key. For a public app with a developer-owned key, use a trusted backend proxy with rate limits instead of embedding a shared key in the Android app.
- Gemini can make mistakes. Verify important claims independently.

## Official references

- [Google AI Studio API keys](https://aistudio.google.com/apikey)
- [Gemini API models](https://ai.google.dev/gemini-api/docs/models)
- [Gemini API pricing](https://ai.google.dev/gemini-api/docs/pricing)
- [Google's API-key security guidance](https://ai.google.dev/gemini-api/docs/api-key)
