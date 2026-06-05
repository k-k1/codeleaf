# File icon attribution

このアプリのファイル一覧アイコンは、以下のオープンソースアイコンセットから取り込んでいる。
いずれも MIT License。各拡張子 → 種別キーの対応は `ui/FileIcons.kt` を参照。

| セット (設定名) | assets フォルダ | 出典 | License |
|---|---|---|---|
| Devicon | `devicon/` | https://github.com/devicons/devicon | MIT |
| Material | `material/` | https://github.com/material-extensions/vscode-material-icon-theme | MIT |
| VS Code | `vscode_icons/` | https://github.com/vscode-icons/vscode-icons | MIT |
| Seti | `seti/` | https://github.com/jesseweed/seti-ui | MIT |

Seti は単色グリフのため、`FileIcons.kt` の `SETI_COLOR`（seti-ui `styles/components/icons/mapping.less`
のパレットに準拠）でタイプ別に着色している。Seti は groovy / nodejs を収録しないため、
それらは汎用ファイルアイコンにフォールバックする。
