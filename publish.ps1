<#
    Uploads the jars in release-<version>\ to CurseForge.

    Prints what it would upload and stops, unless -Publish is given. Uploads are public and cannot
    be undone from here, so look at the dry run first. It does not check what is already on
    CurseForge: running it twice for one version uploads that version twice.

    The API token is read from the CURSEFORGE_TOKEN environment variable (set once with
    `setx CURSEFORGE_TOKEN "..."`); it is never written anywhere by this script.

    Usage:
        powershell -ExecutionPolicy Bypass -File .\publish.ps1                    # dry run, current version
        powershell -ExecutionPolicy Bypass -File .\publish.ps1 -Publish -Only '*forge-1.20.1*'
        powershell -ExecutionPolicy Bypass -File .\publish.ps1 -Publish           # upload them all
#>
param(
    [string]$Version,                    # default: mod_version in gradle.properties
    [string]$Only = '*',                 # wildcard on the jar name
    [ValidateSet('release', 'beta', 'alpha')][string]$ReleaseType = 'release',
    [switch]$Publish
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$projectId = 1699097                     # curseforge.com/minecraft/mc-mods/solo-economy
$api = 'https://minecraft.curseforge.com/api'

if (-not $Version) {
    $Version = (Select-String -Path (Join-Path $root 'gradle.properties') -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value.Trim()
}

# setx writes to the user environment, which a shell opened earlier has not seen yet.
$token = $env:CURSEFORGE_TOKEN
if (-not $token) { $token = [Environment]::GetEnvironmentVariable('CURSEFORGE_TOKEN', 'User') }
if (-not $token) { throw 'CURSEFORGE_TOKEN is not set.' }

$release = Join-Path $root "release-$Version"
if (-not (Test-Path $release)) { throw "No release-$Version folder." }

# The two builds: soloeconomy-<ver>.jar is NeoForge 1.21.1, soloeconomy-forge-1.20.1-<ver>.jar is Forge.
$builds = @(
    @{ Jar = "soloeconomy-$Version.jar"; Changelog = "CHANGELOG-$Version.md"; Loader = 'NeoForge'; Minecraft = '1.21.1'; Java = 'Java 21' },
    @{ Jar = "soloeconomy-forge-1.20.1-$Version.jar"; Changelog = "CHANGELOG-$Version-forge-1.20.1.md"; Loader = 'Forge'; Minecraft = '1.20.1'; Java = 'Java 17' }
) | Where-Object { $_.Jar -like $Only }
if (-not $builds) { throw "No jars for $Version matching '$Only'." }

$headers = @{ 'X-Api-Token' = $token }
$types = Invoke-RestMethod "$api/game/version-types" -Headers $headers
$versions = Invoke-RestMethod "$api/game/versions" -Headers $headers

# CurseForge reuses names across unrelated lists ("1.21.1" is also a Bukkit version), so every
# lookup names the list it wants.
function Get-VersionId([string]$name, [string]$typeSlugPattern) {
    $typeIds = @($types | Where-Object { $_.slug -match $typeSlugPattern } | ForEach-Object { $_.id })
    $hit = @($versions | Where-Object { $_.name -eq $name -and $typeIds -contains $_.gameVersionTypeID })
    if ($hit.Count -ne 1) { throw "Expected one CurseForge version named '$name' in /$typeSlugPattern/, found $($hit.Count)." }
    $hit[0].id
}

$plan = foreach ($build in $builds) {
    $jar = Get-Item (Join-Path $release $build.Jar)
    # The changelog file opens with a title and a "Minecraft x · Loader · Java" line; CurseForge
    # shows all of that itself, so the upload starts at the first section.
    $lines = @(Get-Content (Join-Path $release $build.Changelog) -Encoding UTF8)
    $first = 0
    while ($first -lt $lines.Count -and $lines[$first] -notmatch '^## ') { $first++ }
    $changelog = ($lines[$first..($lines.Count - 1)] -join "`n").Trim()
    if (-not $changelog) { throw "$($build.Changelog) has no '## ' section." }

    $ids = @(Get-VersionId $build.Minecraft '^minecraft-')
    $ids += Get-VersionId $build.Loader '^modloader$'
    $ids += Get-VersionId $build.Java '^java$'
    $ids += Get-VersionId 'Client' '^environment$'
    $ids += Get-VersionId 'Server' '^environment$'

    $metadata = [ordered]@{
        changelog     = $changelog
        changelogType = 'markdown'
        displayName   = "Solo Economy $Version ($($build.Loader) $($build.Minecraft))"
        gameVersions  = $ids
        releaseType   = $ReleaseType
    }
    [pscustomobject]@{ Jar = $jar; Name = $metadata.displayName; Tags = "$($build.Minecraft), $($build.Loader), $($build.Java), Client, Server"; Metadata = $metadata }
}

Write-Host "Project $projectId, version $Version, release type '$ReleaseType'" -ForegroundColor Cyan
foreach ($item in $plan) {
    Write-Host ("`n  {0}  ({1:N0} bytes)`n  {2}`n  [{3}]" -f $item.Jar.Name, $item.Jar.Length, $item.Name, $item.Tags)
    Write-Host "`n--- changelog ---`n$($item.Metadata.changelog)`n-----------------"
}

if (-not $Publish) {
    Write-Host "`nDry run: nothing uploaded. Add -Publish to upload." -ForegroundColor Yellow
    return
}

Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.DefaultRequestHeaders.Add('X-Api-Token', $token)
foreach ($item in $plan) {
    $form = New-Object System.Net.Http.MultipartFormDataContent
    $form.Add((New-Object System.Net.Http.StringContent(($item.Metadata | ConvertTo-Json -Depth 6 -Compress), [Text.Encoding]::UTF8)), 'metadata')
    $file = New-Object System.Net.Http.ByteArrayContent(, [IO.File]::ReadAllBytes($item.Jar.FullName))
    $file.Headers.ContentType = 'application/java-archive'
    $form.Add($file, 'file', $item.Jar.Name)

    $response = $client.PostAsync("$api/projects/$projectId/upload-file", $form).Result
    $body = $response.Content.ReadAsStringAsync().Result
    if (-not $response.IsSuccessStatusCode) {
        # Stop at the first failure, so a mistake is not repeated across every jar.
        throw "Upload of $($item.Jar.Name) failed: $([int]$response.StatusCode) $body"
    }
    Write-Host "Uploaded $($item.Jar.Name) -> $body" -ForegroundColor Green
}
