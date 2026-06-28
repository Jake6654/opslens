import base64
import httpx

from app.config import settings

class GitHubClient:
  def __init__(self):
    self.owner = settings.github_owner
    self.repo = settings.github_repo
    self.branch = settings.github_default_branch
    self.base_url = "https://api.github.com"

  def _headers(self) -> dict[str, str]:
        headers = {
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        }

        if settings.github_token:
            headers["Authorization"] = f"Bearer {settings.github_token}"

        return headers
  
  async def search_code(self, query: str, limit: int = 5) -> list[dict]:
        search_query = f"{query} repo:{self.owner}/{self.repo}"

        async with httpx.AsyncClient(timeout=20) as client:
            response = await client.get(
                f"{self.base_url}/search/code",
                headers=self._headers(),
                params={
                    "q": search_query,
                    "per_page": limit,
                },
            )

            response.raise_for_status()
            data = response.json()

        return data.get("items", [])

  async def fetch_file(self, path: str) -> str:
        async with httpx.AsyncClient(timeout=20) as client:
            response = await client.get(
                f"{self.base_url}/repos/{self.owner}/{self.repo}/contents/{path}",
                headers=self._headers(),
                params={"ref": self.branch},
            )

            response.raise_for_status()
            data = response.json()

        encoded_content = data.get("content", "")
        return base64.b64decode(encoded_content).decode("utf-8", errors="replace")