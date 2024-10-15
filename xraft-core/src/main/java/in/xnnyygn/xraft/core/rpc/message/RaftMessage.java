package in.xnnyygn.xraft.core.rpc.message;

import in.xnnyygn.xraft.core.node.NodeId;

public interface RaftMessage {
    public String toString();

    public NodeId getSourceNodeId();
 }
