# JGit PoC 実行スクリプト (UTF-8 コンソール出力)
# 使い方:
#   公開リポ:   .\run.ps1
#   privateリポ: 先に環境変数を設定してから実行 (トークンはコミットしないこと)
#     $env:GIT_URL    = "https://github.com/you/private-repo.git"
#     $env:GIT_USER   = "you"            # GitHub=任意 / Bitbucket=Atlassianメール(必須)
#     $env:GIT_TOKEN  = "<PAT or API token>"
#     $env:GIT_BRANCH = "main"           # 省略可
#     .\run.ps1

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$base = $PSScriptRoot
$java = "C:\programs\java\jdk-21.0.9+10\bin\java.exe"
$javac = "C:\programs\java\jdk-21.0.9+10\bin\javac.exe"

& $javac -cp ".\libs\*" -d "$base\out" "$base\JgitPoc.java"
if (-not $?) { throw "compile failed" }

& $java "-Dfile.encoding=UTF-8" "-Dorg.slf4j.simpleLogger.defaultLogLevel=warn" `
    -cp "$base\out;$base\libs\*" JgitPoc
