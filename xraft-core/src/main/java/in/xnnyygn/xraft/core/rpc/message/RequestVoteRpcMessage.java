package in.xnnyygn.xraft.core.rpc.message;

import in.xnnyygn.xraft.core.node.NodeId;
import in.xnnyygn.xraft.core.rpc.Channel;

public class RequestVoteRpcMessage extends AbstractRpcMessage<RequestVoteRpc> implements RaftMessage {

    public RequestVoteRpcMessage(RequestVoteRpc rpc, NodeId sourceNodeId, Channel channel) {
        super(rpc, sourceNodeId, channel);
    }

    public String toString() { return "RequestVoteRpcMessage"; }
}
