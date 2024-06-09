jest.mock('../src/Modules/mqttModule.ts', () => {
  return {
    __esModule: true,
    MqttJSIModule: {
      createMqtt: jest.fn(),
      removeMqtt: jest.fn(),
      connectMqtt: jest.fn(),
      disconnectMqtt: jest.fn(),
      subscribeMqtt: jest.fn(),
      unsubscribeMqtt: jest.fn(),
      getConnectionStatusMqtt: jest.fn(() => {}),
    },
  };
});
