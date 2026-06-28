import base64
import httpx

from app.config import settings

class GitHubClient:
  # __init__ runs automatically when you create the object
  def __init__(self):
    self.owner = settings.github_owner
    self.repo = settings.github_repo
    self.branch = settings.github_default_branch
    self.base_url = "https://api.github.com"

  # This method builds HTTP headers for GitHub API requests
  # Headers are extra info sent with an HTTP request
  def _headers(self) -> dict[str, str]:
        headers = {
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        }

        # if a GitHub token exists, add it to the request headers
        if settings.github_token:
            headers["Authorization"] = f"Bearer {settings.github_token}"

        return headers
  
  async def search_code(self, query: str, limit: int = 5) -> list[dict]:
        search_query = f"{query} repo:{self.owner}/{self.repo}"

        # Creates an HTTP Client

        async with httpx.AsyncClient(timeout=20) as client:
            # this will be something like 
            # https://api.github.com/search/code?q=DiaryService+repo%3AJake6654%2Fsketch-my-day&per_page=3
            response = await client.get(
                f"{self.base_url}/search/code",
                headers=self._headers(),
                params={
                    "q": search_query,
                    "per_page": limit,
                },
            )

            # If github returned an error status, throw an exception
            response.raise_for_status()
            data = response.json()

        # returns only the list of matched files
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

        # This turns encoded Github content into normal source code text
        encoded_content = data.get("content", "")
        return base64.b64decode(encoded_content).decode("utf-8", errors="replace")