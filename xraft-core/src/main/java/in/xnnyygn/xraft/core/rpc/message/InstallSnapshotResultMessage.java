package in.xnnyygn.xraft.core.rpc.message;

import com.google.common.base.Preconditions;
import in.xnnyygn.xraft.core.node.NodeId;

import javax.annotation.Nonnull;

public class InstallSnapshotResultMessage implements RaftMessage {

    private final InstallSnapshotResult result;
    private final NodeId sourceNodeId;
    private final InstallSnapshotRpc rpc;

    public InstallSnapshotResultMessage(InstallSnapshotResult result, NodeId sourceNodeId, @Nonnull InstallSnapshotRpc rpc) {
        Preconditions.checkNotNull(rpc);
        this.result = result;
        this.sourceNodeId = sourceNodeId;
        this.rpc = rpc;
    }

    public InstallSnapshotResult get() {
        return result;
    }

    public NodeId getSourceNodeId() {
        return sourceNodeId;
    }

    public InstallSnapshotRpc getRpc() {
        return rpc;
    }

    public String toString() { return "InstallSnapshotResultMessage"; }
}
