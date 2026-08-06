# CodeLeaf Changelog

Main user-facing changes per version. Internal commits such as `chore` (version bumps), `docs`, and `test` are generally omitted.

## 0.9.2 (2026-08-06)
### Fixed
- Markdown: tables written with the fullwidth vertical bar ｜ are now repaired to display as proper tables (when a Japanese IME commits the bar as fullwidth, the columns look aligned but the table falls back to a plain paragraph). A note above the repaired table explains that other Markdown viewers will still render it broken

## 0.9.1 (2026-07-14)
### Fixed
- Commit graph: fixed the merge line to a merge commit's second parent not being drawn when that parent already exists in another lane

## 0.9.0 (2026-07-07)
### New
- Localization: in addition to English and Japanese, added Korean, Simplified Chinese, Traditional Chinese, Spanish, and Vietnamese (follows the device language; can also be set explicitly in Settings)
- Added a rendered HTML preview in the viewer (toggle to Raw, just like Markdown)
- Sort the file list by "last modified" (last commit date; saved as a global setting)
- Edit a memo's comment after creating it
- View the release notes (changelog) from Settings
- Unified the language / memo destination / repository dropdowns with rounded corners
- Repo avatars support two full-width characters; short labels are 3 chars, with lower saturation and a darker selection ring

### Fixed
- In the collapsed 3-pane icon rail, show only the selected group and tone down avatar colors
- Use a proper message when diff retrieval fails
- Match dropdown text size to the surrounding buttons (14sp)

## 0.8.0 (2026-06-26)
### New
- Added Copy / Share actions to the add-memo sheet (also saves)
- Group memo copy/share by file, ordered by line number, quotes trimmed to 3 lines
- Added a menu to clear all entries while keeping the notebook
- Initialize the add-memo comment field with the selected lines' content (up to 3 lines)
- Long-press memo on rendered Markdown now captures the long-pressed line, not the whole block
- Compressed the add-memo sheet vertically so the action row is visible on open
- Made the save-to selector an explicit framed +▾ UI and added an icon to the confirm button
### Fixed
- Lazily format commit diffs and use LazyColumn to fix freezes/crashes on diffs with many files
- Added imePadding to the add-memo sheet to fix the IME (soft keyboard) overlap

## 0.7.0 (2026-06-23)
### New
- Show each file/folder's last commit (author and relative update time) on two lines under its name in the file list. Toggle with the "Show commit info" setting (default OFF)
- Long-press a folder to view the commit history that changed files under it
- Long-press a submodule to view its gitlink (pointer) move history
- Show submodule diffs as the old→new range of commits (message, author, time) instead of raw hashes
- Re-clone (rebuild the local clone) from the snackbar on sync failure; also added "Re-clone" to each row menu (⋮) on the repo edit screen
- Show sync errors with clearer wording such as "network error"
### Fixed
- Fixed status bar icons blending into the background when a theme different from the system was chosen (now follows the foreground screen's theme)

## 0.6.0 (2026-06-19)
### New
- Tap the title (repo name) at the top of the browser to open the repo list drawer
- Tap the file name/path at the top of the viewer to return to the browser (single pane closes / focus mode is released)
- Use the folder you were in as the default path filter in browser search
### Internal / cleanup (no behavior change)
- Unified the former name GitReader to CodeLeaf
- Consolidated to provider-neutral shared models / HTTP seam; made the OAuth provider an enum
- Split pure logic into separate files, tidied naming, minimized visibility, removed dead code

## 0.5.0 (2026-06-14)
### New
- Added a text selection mode to the viewer (toggle from the ⋮ menu / default in settings)
- Show a spinner while switching branches in the commit graph
### Fixed
- Keep scroll position when returning from the viewer/browser
- Right-align the diff "open" icon in the bar; submodules open without selection

## 0.4.0 (2026-06-11)
### New
- Restore the previous folder/file when reopening a repo (toggle in settings)
- Open-in-Viewer icon on each diff file (shows the version at that point if it is not in the working tree)
- In focus mode, Back from a link-navigated file returns to the previous file (focus kept)

## 0.3.1 (2026-06-09)
### New
- Long-press a commit in the commit graph to switch to a remote branch
- Reworked the add-memo line-range input with a slider + auto-repeat (hold to increment/decrement)
### Fixed
- Make Markdown directory / percent-encoded relative links navigable
- Send a restored backStack that points to a deleted repo (a "ghost screen") back to the list

## 0.3.0 (2026-06-08)
### New
- Strengthened submodule support: "SUB" badge on folders, fetch retries + visualization of unfetched ones, rewrite SSH URLs to HTTPS for fetching
- Added "History" to the file long-press menu
- Multi-pane Back now follows the operation order (goes up one level right after a folder move)
- Added collapse-all-files to the diff bottom bar
- Repo edit cards now show clone progress + toned-down color usage
### Fixed
- Fixed right-edge padding and file-name squashing in the multi-pane browser
- Fixed repo line colors in the browser (no color = gray / color changes apply immediately)
- Fixed the height of the diff single vertical pane
