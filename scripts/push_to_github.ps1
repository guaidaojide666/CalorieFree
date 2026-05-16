param(
    [string]$RepoUrl = "https://github.com/guaidaojide666/CalorieFree.git",
    [string]$Branch = "main",
    [string]$Message = "",
    [string]$GitUserName = "guaidaojide666",
    [string]$GitUserEmail = "guaidaojide666@users.noreply.github.com"
)

$ErrorActionPreference = "Stop"
$RepoPath = (Get-Location).Path.Replace("\", "/")

function Run-Git {
    param([string[]]$GitArgs)
    Write-Host "git $($GitArgs -join ' ')"
    git @GitArgs
    if ($LASTEXITCODE -ne 0) {
        throw "git $($GitArgs -join ' ') failed"
    }
}

if (-not (Test-Path ".git")) {
    Run-Git @("init")
    Run-Git @("branch", "-M", $Branch)
}

git config --global --add safe.directory $RepoPath

$configuredName = git config user.name
if ([string]::IsNullOrWhiteSpace($configuredName)) {
    Run-Git @("config", "user.name", $GitUserName)
}

$configuredEmail = git config user.email
if ([string]::IsNullOrWhiteSpace($configuredEmail)) {
    Run-Git @("config", "user.email", $GitUserEmail)
}

$remoteNames = git remote
if ($remoteNames -notcontains "origin") {
    Run-Git @("remote", "add", "origin", $RepoUrl)
} else {
    $remote = git remote get-url origin
    if ($remote -ne $RepoUrl) {
        Run-Git @("remote", "set-url", "origin", $RepoUrl)
    }
}

Run-Git @("add", "-A")

$status = git status --porcelain
if ([string]::IsNullOrWhiteSpace($status)) {
    Write-Host "No changes to commit."
} else {
    if ([string]::IsNullOrWhiteSpace($Message)) {
        $Message = "Update CalorieFree $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    }
    Run-Git @("commit", "-m", $Message)
}

Run-Git @("push", "-u", "origin", $Branch)
Write-Host "Pushed to $RepoUrl on branch $Branch."
