package pbj.interop;

import grpc.testing.Empty;

public class TestServiceImpl implements TestService {
    @Override
    public Empty EmptyCall(Empty request) {
        return request;
    }
}
