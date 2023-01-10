import grpc

import proto.hello_pb2_grpc as pb2_grpc
import proto.hello_pb2 as pb2

from library import utils

class Client(object):
    """
    Client to test gRPC functionality. Every symbol from `pb2` and `pb2_grpc` should resolve correctly, 
    and we should be able to run this binary from the IDE.
    """

    def __init__(self):
        self.host = 'localhost'
        self.server_port = 50051

        # instantiate a channel
        self.channel = grpc.insecure_channel(
            '{}:{}'.format(self.host, self.server_port))

        # bind the client and the server
        self.stub = pb2_grpc.HelloServiceStub(self.channel)

    def get_url(self, message):
        """
        Client function to call the rpc for GetServerResponse
        """
        message = pb2.MessageRequest(message=message)
        return self.stub.SaySomething(message)


if __name__ == '__main__':
    client = Client()
    message = utils.random_string()
    print(f'Request: "{message}"')

    result = client.get_url(message=message)
    print(f'Response: "{result.message}"')
