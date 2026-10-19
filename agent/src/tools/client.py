"""
后端服务 HTTP 客户端。

职责：
1. 从 config 读取 base_url、认证方式、超时、重试等参数。
2. 统一拼接 URL、注入认证头、处理通用错误。
3. 提供 login() 方法获取 JWT Token 并自动复用。
"""

from typing import Any, Optional

import httpx

from src.config import config


class BackendClient:
    """后端服务通用 HTTP 客户端。"""

    def __init__(self):
        self.base_url = (config.backend.base_url or "").rstrip("/")
        self.auth_type = (config.backend.auth_type or "none").lower()
        self.api_key = config.backend.api_key or ""
        self.token = config.backend.token or ""
        self.timeout = config.backend.timeout or 30
        self.max_retries = config.backend.max_retries or 0
        self.verify_ssl = config.backend.verify_ssl

    def _ensure_configured(self) -> None:
        """延迟校验：只有在真正发起请求时才检查 base_url。"""
        if not self.base_url:
            raise ValueError(
                "未配置 后端服务地址。请在 .env 中设置 BACKEND_BASE_URL。"
            )

    def _auth_headers(self) -> dict[str, str]:
        """根据认证方式生成请求头。"""
        headers: dict[str, str] = {}
        if self.auth_type == "bearer" and self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        elif self.auth_type == "api_key" and self.api_key:
            headers["X-API-Key"] = self.api_key
        return headers

    def request(
        self,
        method: str,
        path: str,
        *,
        params: Optional[dict[str, Any]] = None,
        json: Optional[dict[str, Any]] = None,
        headers: Optional[dict[str, str]] = None,
        **kwargs,
    ) -> dict[str, Any]:
        """
        发送 HTTP 请求并返回后端统一响应 Result 的 JSON 数据。

        Returns:
            后端返回的 JSON 字典，通常为 {"code": 200, "message": "...", "data": ..., "timestamp": ...}
        """
        self._ensure_configured()
        url = f"{self.base_url}{path}"
        merged_headers = self._auth_headers()
        if headers:
            merged_headers.update(headers)
        merged_headers.setdefault("Content-Type", "application/json")

        transport = httpx.HTTPTransport(retries=self.max_retries)
        with httpx.Client(
            timeout=self.timeout, verify=self.verify_ssl, transport=transport
        ) as client:
            response = client.request(
                method=method.upper(),
                url=url,
                params=params,
                json=json,
                headers=merged_headers,
                **kwargs,
            )
            response.raise_for_status()
            return response.json()

    def login(self, username: str, password: str) -> dict[str, Any]:
        """
        调用 /auth/login 获取 JWT Token，并将 token 写入客户端内存，供后续请求使用。

        Returns:
            后端登录接口返回的完整 Result 数据。
        """
        result = self.request(
            "POST",
            "/auth/login",
            json={"username": username, "password": password},
        )
        if result.get("code") == 200 and result.get("data"):
            self.token = result["data"].get("token", "")
        return result


# 模块级默认客户端实例
client = BackendClient()
