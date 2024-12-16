import { SubscribeMqtt, PublishMqtt } from '../../src/Mqtt';

export const subscriptionConfig: SubscribeMqtt = {
  topic: 'hello',
  qos: 1,
  onEvent: (payload) => {
    console.log('Received message:', payload);
  },
  onSuccess: (ack) => {
    console.log('Subscription success:', ack);
  },
  onError: (error) => {
    console.log('Subscription error:', error);
  },
};

export const publishConfig: PublishMqtt = {
  topic: 'hello',
  payload: 'Hello from react native',
  qos: 1,
};
