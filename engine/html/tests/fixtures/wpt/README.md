# Vendored WPT HTML tree-construction fixtures

These 50 selected tree-construction fixtures are copied from the Web Platform Tests repository:

- Source repository: `web-platform-tests/wpt`
- Pinned source revision: `b4cd3d853150fb8e704a56f0099d6fbff2b9550f`
- Upstream directory: `html/syntax/parsing/resources/`
- Upstream format description: `html/syntax/parsing/resources/README.md`
- Wrapper manifest used to select fixture names: `html/syntax/parsing/html5lib_url.html`
- License: BSD 3-Clause; see [LICENSE.md](LICENSE.md).

The pinned wrapper listed 61 fixture variants. This curated subset vendors 50 of them. Each bundle entry begins with `@@WPT-FIXTURE <upstream-name> <upstream-blob-sha>` and ends with `@@END-WPT-FIXTURE`. The bytes between those markers are the upstream `.dat` fixture content, retained without rewriting. The bundle markers and grouping are local packaging only.

## Included upstream fixtures

`adoption01`, `adoption02`, `blocks`, `comments01`, `doctype01`, `domjs-unsafe`, `entities01`, `entities02`, `html5test-com`, `inbody01`, `isindex`, `main-element`, `menuitem-element`, `namespace-sensitivity`, `pending-spec-changes`, `pending-spec-changes-plain-text-unsafe`, `quirks01`, `ruby`, `scriptdata01`, `search-element`, `tables01`, `tests1`, `tests10`, `tests11`, `tests12`, `tests14`, `tests15`, `tests16`, `tests17`, `tests18`, `tests19`, `tests2`, `tests20`, `tests21`, `tests22`, `tests23`, `tests24`, `tests25`, `tests26`, `tests3`, `tests4`, `tests5`, `tests6`, `tests7`, `tests8`, `tests9`, `tricky01`, `void-in-phrasing`, `webkit01`, and `webkit02`.

## Harness scope

The v1 harness compares exact trees when all authored `html`, `head`, and `body` wrappers are present. It uses a controlled body-content projection only when none of those wrappers was authored and the reference tree has empty implicit `head` content. It never removes or ignores authored wrappers.

Cases requiring fragment parsing, scripting modes, parse-error diagnostics, foreign namespaces, template-content nodes, processing instructions, public/system doctype identifiers, or unsupported wrapper insertion are reported as **unsupported**, not silently normalized. Supported-case mismatches fail the test. The harness does not change parser behavior.
