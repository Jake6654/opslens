import os

# This file gives the app one clean place to read env vars. Instead of calling os.getenv(...) everwhere, you can use settings.github_token or something like that
class Settings:
    github_token: str | None = os.getenv("GITHUB_TOKEN")
    github_owner: str = os.getenv("GITHUB_REPOSITORY_OWNER", "")
    github_repo: str = os.getenv("GITHUB_REPOSITORY_NAME", "")
    github_default_branch: str = os.getenv("GITHUB_DEFAULT_BRANCH", "main")
    code_search_mode: str = os.getenv("CODE_SEARCH_MODE", "github")
    local_repository_path: str = os.getenv("LOCAL_REPOSITORY_PATH", "")


settings = Settings()