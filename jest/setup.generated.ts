
import { jest } from '@jest/globals';
import { MqttModuleProxy } from '../src/Modules/mqttModule';

declare global {
  var __MqttModuleProxy: MqttModuleProxy;
}

const MockMqttModule = {
  createMqtt: jest.fn(),
  removeMqtt: jest.fn(),
  connectMqtt: jest.fn(),
  disconnectMqtt: jest.fn(),
  subscribeMqtt: jest.fn(),
  unsubscribeMqtt: jest.fn(),
  getConnectionStatusMqtt: jest.fn(() => 'connected'),
};

global.__MqttModuleProxy = {
  ...MockMqttModule,
};

export { MockMqttModule };

jest.mock('../src/Modules/mqttModule.ts', () => {
  return {
    __esModule: true,
    MqttJSIModule: {
      removeMqtt: jest.fn(),
      connectMqtt: jest.fn(),
      disconnectMqtt: jest.fn(),
      subscribeMqtt: jest.fn(),
      unsubscribeMqtt: jest.fn(),
      getConnectionStatusMqtt: jest.fn(() => {}),
    },
  };
});

const NativeModules = {
  MqttModule: {
    installJSIModule: jest.fn((shouldReturnTrue = true) => {
      return shouldReturnTrue;
    }),
    createMqtt: jest.fn(),
  },
};

const mockedMqttClass = jest.fn();

class NativeEventEmitter {
  constructor(name) {
    mockedMqttClass(name);
  }
}

export { NativeModules, NativeEventEmitter, mockedMqttClass };
