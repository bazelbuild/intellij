import time
from concurrent import futures

import grpc

import proto.hello_pb2_grpc as pb2_grpc
import proto.hello_pb2 as pb2

class Service(pb2_grpc.HelloServiceServicer):

    def __init__(self, *args, **kwargs):
        pass

    def SaySomething(self, request, context):
        # get the string from the incoming request
        message = request.message
        result = f'Hello I am up and running and received "{message}" message from you.'
        result = {'message': result}

        return pb2.MessageResponse(**result)


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    pb2_grpc.add_HelloServiceServicer_to_server(Service(), server)
    server.add_insecure_port('[::]:50051')
    server.start()
    server.wait_for_termination()


if __name__ == '__main__':
    serve()
