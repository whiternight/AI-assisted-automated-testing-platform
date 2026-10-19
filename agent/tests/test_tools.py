"""
test-backend 工具调用测试。
"""

import json
from unittest.mock import MagicMock, patch

import pytest

from src.tools import client as client_module
from src.tools import automation


class TestTools:
    """测试 test-backend 自动化测试工具。"""

    @pytest.fixture(autouse=True)
    def _setup_client(self, monkeypatch):
        """每个测试前重置客户端配置并清空 token。"""
        monkeypatch.setenv("BACKEND_BASE_URL", "http://localhost:8080")
        monkeypatch.setenv("BACKEND_AUTH_TYPE", "bearer")
        monkeypatch.setenv("BACKEND_TOKEN", "test-token")

        # 重新创建客户端实例，使其读取更新后的环境变量
        from src.tools.client import BackendClient

        self.client = BackendClient()
        client_module.client = self.client
        # automation.py 在导入时捕获了 client 引用，需同步更新
        automation.client = self.client

    @patch("src.tools.client.httpx.Client")
    def test_tool_list_api_cases_sends_correct_request(self, mock_client_cls):
        """list_api_cases 应发送正确的 GET 请求。"""
        mock_response = MagicMock()
        mock_response.json.return_value = {
            "code": 200,
            "data": {"list": [], "total": 0},
        }
        mock_response.raise_for_status.return_value = None

        mock_client = MagicMock()
        mock_client.request.return_value = mock_response
        mock_client_cls.return_value.__enter__.return_value = mock_client

        result = automation.list_api_cases.func(pageNum=1, pageSize=5)

        assert result["code"] == 200
        mock_client.request.assert_called_once()
        call_args = mock_client.request.call_args
        assert call_args.kwargs["method"] == "GET"
        assert call_args.kwargs["url"] == "http://localhost:8080/api-case/list"
        assert call_args.kwargs["params"]["pageNum"] == 1
        assert call_args.kwargs["params"]["pageSize"] == 5

    @patch("src.tools.client.httpx.Client")
    def test_tool_run_automation_test_sends_post_payload(self, mock_client_cls):
        """run_automation_test 应发送正确的 POST 请求体。"""
        mock_response = MagicMock()
        mock_response.json.return_value = {"code": 200, "data": {"taskId": "T001"}}
        mock_response.raise_for_status.return_value = None

        mock_client = MagicMock()
        mock_client.request.return_value = mock_response
        mock_client_cls.return_value.__enter__.return_value = mock_client

        result = automation.run_automation_test.func(
            caseIds=["C001"], executorId=1, testSuiteName="冒烟测试"
        )

        assert result["code"] == 200
        call_args = mock_client.request.call_args
        assert call_args.kwargs["method"] == "POST"
        assert call_args.kwargs["url"] == "http://localhost:8080/task/automation/run"
        payload = call_args.kwargs["json"]
        assert payload["caseIds"] == ["C001"]
        assert payload["testSuiteName"] == "冒烟测试"

    @patch("src.tools.client.httpx.Client")
    def test_client_adds_bearer_header(self, mock_client_cls):
        """bearer 认证时应在请求头中携带 Authorization。"""
        self.client.auth_type = "bearer"
        self.client.token = "test-token"

        mock_response = MagicMock()
        mock_response.json.return_value = {"code": 200, "data": {}}
        mock_response.raise_for_status.return_value = None

        mock_client = MagicMock()
        mock_client.request.return_value = mock_response
        mock_client_cls.return_value.__enter__.return_value = mock_client

        automation.get_automation_task.func(taskId="T001")

        headers = mock_client.request.call_args.kwargs["headers"]
        assert headers["Authorization"] == "Bearer test-token"

    @patch("src.tools.client.httpx.Client")
    def test_get_automation_task_sends_get_request(self, mock_client_cls):
        """get_automation_task 应向 /task/automation/{taskId} 发送 GET 请求。"""
        mock_response = MagicMock()
        mock_response.json.return_value = {"code": 200, "data": {"taskId": "T001"}}
        mock_response.raise_for_status.return_value = None

        mock_client = MagicMock()
        mock_client.request.return_value = mock_response
        mock_client_cls.return_value.__enter__.return_value = mock_client

        result = automation.get_automation_task.func(taskId="T001")

        assert result["code"] == 200
        call_args = mock_client.request.call_args
        assert call_args.kwargs["method"] == "GET"
        assert call_args.kwargs["url"] == "http://localhost:8080/task/automation/T001"

    @patch("src.tools.client.httpx.Client")
    def test_retry_automation_task_sends_post_request(self, mock_client_cls):
        """retry_automation_task 应向 /task/automation/{taskId}/retry 发送 POST 请求。"""
        mock_response = MagicMock()
        mock_response.json.return_value = {"code": 200, "data": {"taskId": "T001"}}
        mock_response.raise_for_status.return_value = None

        mock_client = MagicMock()
        mock_client.request.return_value = mock_response
        mock_client_cls.return_value.__enter__.return_value = mock_client

        result = automation.retry_automation_task.func(taskId="T001")

        assert result["code"] == 200
        call_args = mock_client.request.call_args
        assert call_args.kwargs["method"] == "POST"
        assert call_args.kwargs["url"] == "http://localhost:8080/task/automation/T001/retry"

    @patch("src.tools.client.httpx.Client")
    def test_delete_automation_task_sends_delete_request(self, mock_client_cls):
        """delete_automation_task 应向 /task/automation/{taskId} 发送 DELETE 请求。"""
        mock_response = MagicMock()
        mock_response.json.return_value = {"code": 200, "data": None}
        mock_response.raise_for_status.return_value = None

        mock_client = MagicMock()
        mock_client.request.return_value = mock_response
        mock_client_cls.return_value.__enter__.return_value = mock_client

        result = automation.delete_automation_task.func(taskId="T001")

        assert result["code"] == 200
        call_args = mock_client.request.call_args
        assert call_args.kwargs["method"] == "DELETE"
        assert call_args.kwargs["url"] == "http://localhost:8080/task/automation/T001"

    @patch("src.tools.client.httpx.Client")
    def test_list_automation_tasks_sends_get_request(self, mock_client_cls):
        """list_automation_tasks 应向 /task/automation/list 发送带分页参数的 GET 请求。"""
        mock_response = MagicMock()
        mock_response.json.return_value = {
            "code": 200,
            "data": {"list": [], "total": 0},
        }
        mock_response.raise_for_status.return_value = None

        mock_client = MagicMock()
        mock_client.request.return_value = mock_response
        mock_client_cls.return_value.__enter__.return_value = mock_client

        result = automation.list_automation_tasks.func(page=2, size=20)

        assert result["code"] == 200
        call_args = mock_client.request.call_args
        assert call_args.kwargs["method"] == "GET"
        assert call_args.kwargs["url"] == "http://localhost:8080/task/automation/list"
        assert call_args.kwargs["params"] == {"page": 2, "size": 20}

    def test_login_updates_client_token(self, monkeypatch):
        """登录成功后应更新客户端 token。"""
        monkeypatch.setenv("BACKEND_AUTH_TYPE", "none")

        with patch("src.tools.client.httpx.Client") as mock_client_cls:
            mock_response = MagicMock()
            mock_response.json.return_value = {
                "code": 200,
                "data": {"token": "new-token"},
            }
            mock_response.raise_for_status.return_value = None

            mock_client = MagicMock()
            mock_client.request.return_value = mock_response
            mock_client_cls.return_value.__enter__.return_value = mock_client

            result = automation.backend_login.func(username="admin", password="123456")

            assert result["data"]["token"] == "new-token"
            assert self.client.token == "new-token"

    @pytest.mark.integration
    def test_list_api_cases_real_backend(self):
        """集成测试：调用真实 后端服务（需服务已启动并配置有效 token）。"""
        result = automation.list_api_cases(pageNum=1, pageSize=5)

        assert isinstance(result, dict)
        assert "code" in result
