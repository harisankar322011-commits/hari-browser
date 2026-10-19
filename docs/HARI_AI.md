# Hari AI — local chat for Hari Browser

Hari AI is an optional on-device assistant using Google's LiteRT-LM runtime. It does not call a paid AI API. The model is deliberately not bundled in the APK, keeping the browser APK smaller and allowing users to remove the model to recover storage.

## First-time setup

1. Open Hari Browser, open **Settings**, and choose **Hari AI (on-device)**.
2. Open **Model license & access**. Gemma model files are gated: sign in to Hugging Face and accept the Gemma model terms before downloading.
3. Return to Hari AI and tap **Download model (~584 MB)**. Wait for the .litertlm file to finish downloading.
4. Tap **Import model**, choose gemma3-1b-it-int4.litertlm from Downloads, and wait for the copy to finish. Importing temporarily needs room for a second copy; after success, the original Downloads copy may be deleted.
5. Tap **Load local AI**. Model initialization happens on a background thread and can take several seconds.

The model is stored in the app's private files directory, is not backed up by Android's app backup, and can be removed by clearing app data or uninstalling Hari Browser. The imported model is intentionally not loaded automatically so users can control memory use.

## Device and memory notes

The model is about 584 MB on disk and inference may use around 1 GB of RAM on supported devices, with actual memory and speed varying by device and other running apps. On a phone with 6 GB total RAM and about 2.4 GB free at the time of checking, close memory-heavy apps before loading the model. If the browser or phone becomes unstable, unload it by closing the Hari AI screen.

## Privacy behavior

- Prompts and generated answers are processed locally by the selected model.
- This activity does not upload chats or store the transcript for later sessions.
- The model download itself requires internet access and uses Hugging Face.
- A webpage is read only when the user explicitly opens Hari AI from a page and taps **Summarize current page**. A short text excerpt is then passed to the local model.
- Hari AI is not opened from private browsing, to preserve the separation between the private WebView process and the regular app process.
- The model can make mistakes. Verify important claims independently.

## Model

Default model: litert-community/Gemma3-1B-IT, file gemma3-1b-it-int4.litertlm. The model is subject to the Gemma terms and the model provider's conditions; accepting the license is separate from using Hari Browser. See the [model page](https://huggingface.co/litert-community/Gemma3-1B-IT).

Runtime: [LiteRT-LM for Android](https://developers.google.com/edge/litert-lm/android).
