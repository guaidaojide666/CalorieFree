# Upload to GitHub

Run this from the project root:

```powershell
.\scripts\push_to_github.ps1
```

Custom commit message:

```powershell
.\scripts\push_to_github.ps1 -Message "Update app"
```

Custom repository URL:

```powershell
.\scripts\push_to_github.ps1 -RepoUrl "https://github.com/YOUR_NAME/CalorieFree.git"
```

The first push may ask you to sign in to GitHub. Use the browser login or a GitHub personal access token when Git asks for credentials.
