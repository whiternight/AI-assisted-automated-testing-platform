// d:\test\six_sunny_vue3\src\components\bpmn\flowable.js
// Flowable BPMN 扩展描述 - 用于 bpmn-js 支持 Flowable 自定义属性

export default {
  name: 'Flowable',
  uri: 'http://flowable.org/bpmn',
  prefix: 'flowable',
  xml: {
    tagAlias: 'lowerCase'
  },
  associations: [],
  enumerations: [],
  types: [
    {
      name: 'Process',
      extends: ['bpmn:Process'],
      properties: [
        {
          name: 'candidateStarterGroups',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'candidateStarterUsers',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'versionTag',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'historyTimeToLive',
          isAttr: true,
          type: 'Integer'
        },
        {
          name: 'isStartableInTasklist',
          isAttr: true,
          type: 'Boolean',
          default: true
        }
      ]
    },
    {
      name: 'ServiceTask',
      extends: ['bpmn:ServiceTask'],
      properties: [
        {
          name: 'type',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'expression',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'delegateExpression',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'class',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'resultVariable',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'resultVariablePrefix',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'async',
          isAttr: true,
          type: 'Boolean',
          default: false
        },
        {
          name: 'exclusive',
          isAttr: true,
          type: 'Boolean',
          default: true
        },
        {
          name: 'jobPriority',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'serviceId',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'serviceName',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'serviceAddress',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'inputMapping',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'outputMapping',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'assertions',
          isAttr: true,
          type: 'String'
        },
        // ✅✅✅ HTTP Task 关键属性（解决部署报错的核心！）
        {
          name: 'httpRequestMethod',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'httpRequestUrl',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'httpRequestHeaders',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'httpRequestBody',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'httpRequestVariable',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'httpResponseVariable',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'httpRequestTimeout',
          isAttr: true,
          type: 'Integer'
        },
        {
          name: 'httpFailStatusCodes',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'httpSuccessStatusCodes',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'httpDisableStatusCheck',
          isAttr: true,
          type: 'Boolean',
          default: false
        },
        {
          name: 'handleException',
          isAttr: true,
          type: 'String'
        }
      ]
    },
    {
      name: 'ScriptTask',
      extends: ['bpmn:ScriptTask'],
      properties: [
        {
          name: 'resultVariable',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'async',
          isAttr: true,
          type: 'Boolean',
          default: false
        },
        {
          name: 'autoStoreVariables',
          isAttr: true,
          type: 'Boolean',
          default: false
        }
      ]
    },
    {
      name: 'UserTask',
      extends: ['bpmn:UserTask'],
      properties: [
        {
          name: 'assignee',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'candidateUsers',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'candidateGroups',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'dueDate',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'followUpDate',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'priority',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'formKey',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'formHandlerClass',
          isAttr: true,
          type: 'String'
        }
      ]
    },
    {
      name: 'InclusiveGateway',
      extends: ['bpmn:InclusiveGateway'],
      properties: [
        {
          name: 'async',
          isAttr: true,
          type: 'Boolean',
          default: false
        },
        {
          name: 'exclusive',
          isAttr: true,
          type: 'Boolean',
          default: false
        }
      ]
    },
    {
      name: 'EventBasedGateway',
      extends: ['bpmn:EventBasedGateway'],
      properties: [
        {
          name: 'async',
          isAttr: true,
          type: 'Boolean',
          default: false
        }
      ]
    },
    {
      name: 'CallActivity',
      extends: ['bpmn:CallActivity'],
      properties: [
        {
          name: 'calledElement',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'inheritVariables',
          isAttr: true,
          type: 'Boolean',
          default: false
        },
        {
          name: 'inheritBusinessKey',
          isAttr: true,
          type: 'Boolean',
          default: false
        },
        {
          name: 'inheritVariablesForLocalScope',
          isAttr: true,
          type: 'Boolean',
          default: false
        },
        {
          name: 'sameDeployment',
          isAttr: true,
          type: 'Boolean',
          default: false
        },
        {
          name: 'businessKey',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'completeAsync',
          isAttr: true,
          type: 'Boolean',
          default: false
        },
        {
          name: 'fallbackToDefaultTenant',
          isAttr: true,
          type: 'Boolean',
          default: false
        }
      ]
    },
    {
      name: 'SignalEventDefinition',
      extends: ['bpmn:SignalEventDefinition'],
      properties: [
        {
          name: 'async',
          isAttr: true,
          type: 'Boolean',
          default: false
        }
      ]
    },
    {
      name: 'TimerEventDefinition',
      extends: ['bpmn:TimerEventDefinition'],
      properties: [
        {
          name: 'async',
          isAttr: true,
          type: 'Boolean',
          default: false
        }
      ]
    },
    {
      name: 'ErrorEventDefinition',
      extends: ['bpmn:ErrorEventDefinition'],
      properties: [
        {
          name: 'async',
          isAttr: true,
          type: 'Boolean',
          default: false
        }
      ]
    },
    {
      name: 'CompensateEventDefinition',
      extends: ['bpmn:CompensateEventDefinition'],
      properties: [
        {
          name: 'async',
          isAttr: true,
          type: 'Boolean',
          default: false
        }
      ]
    },
    {
      name: 'MultiInstanceLoopCharacteristics',
      extends: ['bpmn:MultiInstanceLoopCharacteristics'],
      properties: [
        {
          name: 'collection',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'elementVariable',
          isAttr: true,
          type: 'String'
        },
        {
          name: 'completionCondition',
          isBody: true,
          type: 'String'
        }
      ]
    }
  ]
}
