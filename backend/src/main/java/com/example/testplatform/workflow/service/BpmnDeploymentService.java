package com.example.testplatform.workflow.service;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BPMN 部署服务 — 核心入口
 * <p>负责 BPMN XML 部署到 Flowable 引擎及启动流程实例</p>
 *
 * @author TestPlatform
 */
@Slf4j
@Service
public class BpmnDeploymentService {

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    private static final String FLOWABLE_PREFIX = "flowable";
    private static final String DELEGATE_EXPRESSION = "${originalServiceDelegate}";
    private static final String HTTP_TYPE = "http";
    private static final String HTTP_REQUEST_METHOD = "httpRequestMethod";
    private static final String HTTP_REQUEST_URL = "httpRequestUrl";
    private static final String REQUEST_METHOD_FIELD = "requestMethod";
    private static final String REQUEST_URL_FIELD = "requestUrl";

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    /**
     * 部署 BPMN XML
     * <p>部署前自动：
     * <ol>
     *   <li>为 serviceTask 注入 OriginalServiceDelegate</li>
     *   <li>清理 process 级别的 extensionElements（bpmn-js 会在 bpmn:process 下放置 process 级
     *       监听器/候选人配置，严格 BPMN 2.0 schema 不允许；这些扩展会被合并到第一个 serviceTask 的
     *       extensionElements 或 definitions 级别中，保留功能的同时保证 schema 合法）</li>
     * </ol>
     * </p>
     *
     * @param flowName 流程名称
     * @param bpmnXml  BPMN XML 内容
     * @return Flowable 部署对象
     */
    public Deployment deploy(String flowName, String bpmnXml) {
        String processedXml = injectServiceTaskDelegate(bpmnXml);
        processedXml = relocateProcessLevelExtensionElements(processedXml);
        processedXml = normalizeExtensionElements(processedXml);
        return repositoryService.createDeployment()
                .addString(flowName + ".bpmn20.xml", processedXml)
                .deploy();
    }

    /**
     * 重新定位 bpmn:process 下的 extensionElements
     * <p>bpmn-js 会在 bpmn:process 直接子元素中放置 extensionElements（用于 process 级别的监听器等），
     * 但 BPMN 2.0 schema 的 tProcess 不允许此元素。处理方式：把 process 级 extensionElements 的子元素
     * 移到 definitions 级别（如果 definitions 也没有 extensionElements），或者直接移除（保留功能但避免 schema 错误）。</p>
     */
    private String relocateProcessLevelExtensionElements(String bpmnXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

            NodeList processes = doc.getElementsByTagNameNS(BPMN_NS, "process");
            boolean modified = false;
            for (int i = 0; i < processes.getLength(); i++) {
                Element process = (Element) processes.item(i);
                NodeList extElements = process.getChildNodes();
                for (int j = extElements.getLength() - 1; j >= 0; j--) {
                    Node node = extElements.item(j);
                    if (node instanceof Element element
                            && BPMN_NS.equals(element.getNamespaceURI())
                            && "extensionElements".equals(element.getLocalName())) {
                        // 把 process 级 extensionElements 的子元素移到第一个 serviceTask 的 extensionElements
                        Node firstServiceTask = findFirstServiceTask(process);
                        if (firstServiceTask instanceof Element svcElement) {
                            Element targetExt = getOrCreateExtensionElements(doc, svcElement);
                            // 把所有子节点移过去
                            while (element.getFirstChild() != null) {
                                targetExt.appendChild(element.getFirstChild());
                            }
                            log.info("已将 process 级 extensionElements 内容合并到 serviceTask[{}] 的 extensionElements",
                                    svcElement.getAttribute("id"));
                        } else {
                            // 找不到 serviceTask，直接移除 extensionElements
                            log.warn("未找到 serviceTask，移除 process 级 extensionElements");
                        }
                        process.removeChild(element);
                        modified = true;
                    }
                }
            }

            if (!modified) {
                return bpmnXml;
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty("omit-xml-declaration", "no");
            transformer.setOutputProperty("encoding", "UTF-8");
            transformer.setOutputProperty("indent", "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            log.error("清理 process 级 extensionElements 失败，使用原始 XML 部署", e);
            return bpmnXml;
        }
    }

    /**
     * 规范化所有 BPMN 元素的 extensionElements
     * <p>BPMN 2.0 schema 要求 extensionElements 在 documentation 之后、其他元素之前出现，
     * 且每个元素最多只能有一个 extensionElements。前端字符串操作或 bpmn-js 序列化可能产生：
     * <ul>
     *   <li>空的 extensionElements（自闭合标签）</li>
     *   <li>重复的 extensionElements（同一父元素下多个）</li>
     *   <li>位置错误的 extensionElements（出现在 incoming/outgoing 等元素之后）</li>
     * </ul>
     * 这些都会导致 SAXParseException: cvc-complex-type.2.4.a。</p>
     */
    private String normalizeExtensionElements(String bpmnXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

            boolean modified = false;
            NodeList allElements = doc.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element parent = (Element) allElements.item(i);

                // 收集此元素下所有 extensionElements 子元素
                List<Element> extList = new ArrayList<>();
                NodeList children = parent.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    Node node = children.item(j);
                    if (node instanceof Element element
                            && BPMN_NS.equals(element.getNamespaceURI())
                            && "extensionElements".equals(element.getLocalName())) {
                        extList.add(element);
                    }
                }

                if (extList.isEmpty()) {
                    continue;
                }

                // 移除空的 extensionElements（无元素子节点）
                for (int k = extList.size() - 1; k >= 0; k--) {
                    Element ext = extList.get(k);
                    if (!hasElementChild(ext)) {
                        parent.removeChild(ext);
                        extList.remove(k);
                        modified = true;
                        log.debug("移除空的 extensionElements 于元素[{}]", parent.getAttribute("id"));
                    }
                }

                if (extList.isEmpty()) {
                    continue;
                }

                // 合并多个 extensionElements 到第一个
                if (extList.size() > 1) {
                    Element target = extList.get(0);
                    for (int k = 1; k < extList.size(); k++) {
                        Element source = extList.get(k);
                        while (source.getFirstChild() != null) {
                            target.appendChild(source.getFirstChild());
                        }
                        parent.removeChild(source);
                        modified = true;
                        log.debug("合并重复的 extensionElements 于元素[{}]", parent.getAttribute("id"));
                    }
                }

                // 检查 extensionElements 是否已在正确位置（documentation 之后，其他元素之前）
                Element ext = extList.get(0);
                if (!isExtensionElementsInCorrectPosition(parent, ext)) {
                    Node insertionPoint = findExtensionElementsInsertionPoint(parent);
                    parent.insertBefore(ext, insertionPoint);
                    modified = true;
                    log.debug("重新定位 extensionElements 于元素[{}]", parent.getAttribute("id"));
                }
            }

            if (!modified) {
                return bpmnXml;
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty("omit-xml-declaration", "no");
            transformer.setOutputProperty("encoding", "UTF-8");
            transformer.setOutputProperty("indent", "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            log.error("规范化 extensionElements 失败，使用原始 XML 部署", e);
            return bpmnXml;
        }
    }

    /** 检查元素是否包含 Element 类型的子节点 */
    private boolean hasElementChild(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查 extensionElements 是否已在正确位置：
     * 其前面只允许 documentation 元素，不允许其他元素
     */
    private boolean isExtensionElementsInCorrectPosition(Element parent, Element ext) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            String localName = element.getLocalName();
            String nsUri = element.getNamespaceURI();
            if (BPMN_NS.equals(nsUri) && "extensionElements".equals(localName)) {
                // 找到 extensionElements，其前面没有非 documentation 元素 → 位置正确
                return true;
            }
            if (!BPMN_NS.equals(nsUri) || !"documentation".equals(localName)) {
                // 在 extensionElements 之前发现了非 documentation 元素 → 位置错误
                return false;
            }
        }
        return false;
    }

    /**
     * 找到 extensionElements 应插入的位置（返回 insertBefore 的参考节点）：
     * documentation 之后，其他元素之前
     */
    private Node findExtensionElementsInsertionPoint(Element parent) {
        NodeList children = parent.getChildNodes();
        Node lastDoc = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element element)) {
                continue;
            }
            String localName = element.getLocalName();
            String nsUri = element.getNamespaceURI();
            if (BPMN_NS.equals(nsUri) && "documentation".equals(localName)) {
                lastDoc = node;
            } else if (!BPMN_NS.equals(nsUri) || !"extensionElements".equals(localName)) {
                // 第一个非 documentation、非 extensionElements 的元素
                return node;
            }
        }
        // 没有 documentation 也没有其他元素，或只有 documentation
        // 返回 null 表示追加到末尾
        return null;
    }

    private Node findFirstServiceTask(Element process) {
        NodeList children = process.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element
                    && BPMN_NS.equals(element.getNamespaceURI())
                    && "serviceTask".equals(element.getLocalName())) {
                return node;
            }
        }
        return null;
    }

    /**
     * 启动流程实例
     *
     * @param procDefId 流程定义 ID
     * @param variables 流程变量
     * @return Flowable 流程实例
     */
    public ProcessInstance start(String procDefId, Map<String, Object> variables) {
        return runtimeService.startProcessInstanceById(procDefId, variables);
    }

    private String getFlowableAttribute(Element element, String name) {
        String value = element.getAttributeNS(FLOWABLE_NS, name);
        if (value == null || value.isBlank()) {
            value = element.getAttribute(FLOWABLE_PREFIX + ":" + name);
        }
        return value == null ? "" : value;
    }

    private void removeFlowableAttribute(Element element, String name) {
        element.removeAttributeNS(FLOWABLE_NS, name);
        element.removeAttribute(FLOWABLE_PREFIX + ":" + name);
    }
    private String getFlowableFieldValue(Element serviceTask, String fieldName) {
        NodeList fields = serviceTask.getElementsByTagNameNS(FLOWABLE_NS, "field");
        for (int i = 0; i < fields.getLength(); i++) {
            Element field = (Element) fields.item(i);
            if (!fieldName.equals(field.getAttribute("name"))) {
                continue;
            }
            NodeList strings = field.getElementsByTagNameNS(FLOWABLE_NS, "string");
            if (strings.getLength() > 0) {
                String value = strings.item(0).getTextContent();
                return value == null ? "" : value;
            }
            String stringValue = field.getAttribute("stringValue");
            if (stringValue == null || stringValue.isBlank()) {
                stringValue = field.getAttributeNS(FLOWABLE_NS, "stringValue");
            }
            return stringValue == null ? "" : stringValue;
        }
        return "";
    }

    private Element getOrCreateExtensionElements(Document doc, Element serviceTask) {
        NodeList children = serviceTask.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element
                    && BPMN_NS.equals(element.getNamespaceURI())
                    && "extensionElements".equals(element.getLocalName())) {
                return element;
            }
        }

        String bpmnPrefix = serviceTask.getPrefix();
        String qualifiedName = (bpmnPrefix == null || bpmnPrefix.isBlank())
                ? "extensionElements"
                : bpmnPrefix + ":extensionElements";
        Element extensionElements = doc.createElementNS(BPMN_NS, qualifiedName);
        Node firstElementChild = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                firstElementChild = node;
                break;
            }
        }
        serviceTask.insertBefore(extensionElements, firstElementChild);
        return extensionElements;
    }

    private void removeFlowableField(Element serviceTask, String fieldName) {
        NodeList fields = serviceTask.getElementsByTagNameNS(FLOWABLE_NS, "field");
        for (int i = fields.getLength() - 1; i >= 0; i--) {
            Element field = (Element) fields.item(i);
            if (fieldName.equals(field.getAttribute("name"))) {
                field.getParentNode().removeChild(field);
            }
        }
    }

    private void addFlowableStringField(Document doc, Element extensionElements, String fieldName, String value) {
        Element field = doc.createElementNS(FLOWABLE_NS, FLOWABLE_PREFIX + ":field");
        field.setAttribute("name", fieldName);
        Element string = doc.createElementNS(FLOWABLE_NS, FLOWABLE_PREFIX + ":string");
        string.setTextContent(value);
        field.appendChild(string);
        extensionElements.appendChild(field);
    }

    private void upsertFlowableHttpField(Document doc, Element serviceTask, String fieldName, String value) {
        removeFlowableField(serviceTask, fieldName);
        Element extensionElements = getOrCreateExtensionElements(doc, serviceTask);
        addFlowableStringField(doc, extensionElements, fieldName, value);
    }


    /**
     * 为所有 serviceTask 注入 OriginalServiceDelegate，强制覆盖其他执行方式
     * <p>前端 bpmn-js 生成的 serviceTask 可能带有 flowable:type="http" 或其他属性，
     * 后端统一加工，确保所有服务任务都使用自定义委托执行，支持原服务关联、重试、断言等高级功能</p>
     *
     * @param bpmnXml 原始 BPMN XML
     * @return 加工后的 BPMN XML
     */
    private String injectServiceTaskDelegate(String bpmnXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));

            // 确保根元素声明 flowable 命名空间
            Element root = doc.getDocumentElement();
            String flowableNsUri = root.getAttribute("xmlns:" + FLOWABLE_PREFIX);
            if (flowableNsUri.isBlank()) {
                root.setAttribute("xmlns:" + FLOWABLE_PREFIX, FLOWABLE_NS);
            }

            // 查找所有 serviceTask 元素（兼容有无前缀）
            NodeList serviceTasks = doc.getElementsByTagNameNS("http://www.omg.org/spec/BPMN/20100524/MODEL", "serviceTask");
            boolean modified = false;
            for (int i = 0; i < serviceTasks.getLength(); i++) {
                Element serviceTask = (Element) serviceTasks.item(i);

                // 强制设置自定义委托表达式，覆盖任何现有配置
                String existingDelegate = serviceTask.getAttributeNS(FLOWABLE_NS, "delegateExpression");
                if (!DELEGATE_EXPRESSION.equals(existingDelegate)) {
                    serviceTask.setAttributeNS(FLOWABLE_NS, FLOWABLE_PREFIX + ":delegateExpression", DELEGATE_EXPRESSION);
                    modified = true;
                }

                // 移除 flowable:type 属性（http、class等），避免使用Flowable内置任务处理器
                String flowableType = getFlowableAttribute(serviceTask, "type");
                if (!flowableType.isBlank()) {
                    removeFlowableAttribute(serviceTask, "type");
                    modified = true;
                }

                // 移除class属性，强制使用delegateExpression
                String flowableClass = getFlowableAttribute(serviceTask, "class");
                if (!flowableClass.isBlank()) {
                    removeFlowableAttribute(serviceTask, "class");
                    modified = true;
                }

                // 移除expression属性
                String flowableExpression = getFlowableAttribute(serviceTask, "expression");
                if (!flowableExpression.isBlank()) {
                    removeFlowableAttribute(serviceTask, "expression");
                    modified = true;
                }

                // 移除async属性，我们的委托内部会处理异步/重试逻辑，不需要Flowable的异步执行器
                String asyncAttr = getFlowableAttribute(serviceTask, "async");
                if (!asyncAttr.isBlank()) {
                    removeFlowableAttribute(serviceTask, "async");
                    modified = true;
                }

                // 移除旧的HTTP相关属性和字段（我们通过自定义委托处理HTTP调用，不需要Flowable内置HTTP任务）
                removeFlowableAttribute(serviceTask, HTTP_REQUEST_METHOD);
                removeFlowableAttribute(serviceTask, HTTP_REQUEST_URL);
                removeFlowableField(serviceTask, REQUEST_METHOD_FIELD);
                removeFlowableField(serviceTask, REQUEST_URL_FIELD);
            }

            if (!modified) {
                return bpmnXml;
            }

            // 序列化回字符串
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty("omit-xml-declaration", "no");
            transformer.setOutputProperty("encoding", "UTF-8");
            transformer.setOutputProperty("indent", "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            log.error("处理BPMN XML失败，使用原始XML部署", e);
            return bpmnXml;
        }
    }
}
