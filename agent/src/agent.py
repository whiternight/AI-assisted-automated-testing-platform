"""
LangGraph ReAct Agent 构建器。

职责：
1. 按配置初始化 LLM（OpenAI 兼容接口，如 Qwen）。
2. 组合 Chroma 向量库检索工具与 test-backend 自动化测试工具。
3. 根据 allowed_tools / blocked_tools 过滤可用工具。
4. 使用 MemorySaver 提供多轮记忆能力。
5. 对外暴露 create_agent() 工厂函数。
"""

from typing import Callable

from langchain.agents import create_agent as create_langchain_agent
from langchain_core.tools import tool
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.memory import MemorySaver

from src.config import config
from src.tools import (
    backend_login,
    run_automation_test,
    get_automation_task,
    retry_automation_task,
    delete_automation_task,
    list_automation_tasks,
    direct_test,
    list_api_cases,
    debug_api_case,
    list_flow_definitions,
    get_flow_definition_detail,
    deploy_flow_definition,
    execute_flow,
    get_flow_execution_result,
    get_flow_execution_history,
    list_original_services,
    get_original_service_detail,
    create_original_service,
    delete_original_service,
    list_available_cases,
    add_cases_to_original_service,
)


@tool
def search_knowledge_base(query: str) -> str:
    """
    查询本地 Chroma 向量知识库，获取与问题相关的参考资料。

    当用户问题可能涉及项目文档、接口规范、测试用例说明时，优先调用此工具。
    """
    try:
        from src.rag import rag

        documents = rag.retrieve(query)
        if not documents:
            return "未检索到与问题相关的资料。"
        return rag.format_context(documents)
    except ValueError as exc:
        return f"知识库尚未配置，无法检索：{exc}"
    except Exception as exc:
        return f"检索知识库时出错：{exc}"


# 所有可用工具，name 用于 allowed_tools / blocked_tools 过滤
_ALL_TOOLS: list[Callable] = [
    search_knowledge_base,
    backend_login,
    run_automation_test,
    get_automation_task,
    retry_automation_task,
    delete_automation_task,
    list_automation_tasks,
    direct_test,
    list_api_cases,
    debug_api_case,
    list_flow_definitions,
    get_flow_definition_detail,
    deploy_flow_definition,
    execute_flow,
    get_flow_execution_result,
    get_flow_execution_history,
    list_original_services,
    get_original_service_detail,
    create_original_service,
    delete_original_service,
    list_available_cases,
    add_cases_to_original_service,
]


def _select_tools() -> list[Callable]:
    """根据配置过滤允许/禁止的工具。"""
    allowed = config.agent.allowed_tools
    blocked = config.agent.blocked_tools

    tools = _ALL_TOOLS
    if allowed:
        tools = [t for t in tools if getattr(t, "name", None) in allowed]
    if blocked:
        tools = [t for t in tools if getattr(t, "name", None) not in blocked]
    return tools


def create_agent(checkpointer=None):
    """
    创建并返回 LangGraph ReAct Agent 实例。

    Args:
        checkpointer: 可选的持久化记忆检查点，默认使用 MemorySaver。

    Returns:
        编译后的 LangGraph Agent 可调用对象。
    """
    model = ChatOpenAI(
        model=config.llm.model,
        openai_api_key=config.llm.api_key,
        openai_api_base=config.llm.base_url,
        temperature=config.llm.temperature,
        max_tokens=config.llm.max_tokens,
        streaming=True,
    )

    tools = _select_tools()
    if not checkpointer:
        checkpointer = MemorySaver()

    return create_langchain_agent(
        model=model,
        tools=tools,
        system_prompt=config.agent.system_prompt,
        checkpointer=checkpointer,
    )
